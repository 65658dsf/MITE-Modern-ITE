package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.world.SourceExplosionBlocks;
import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Main-thread, globally budgeted, fair destruction of already approved crater blocks. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class InfernalExplosionDestruction {
    private static final int MAX_BLOCKS_PER_TICK = 2048;
    private static final long MAX_NANOS_PER_TICK = 4_000_000L;
    private static final Map<MinecraftServer, State> SERVERS = new WeakHashMap<>();
    private InfernalExplosionDestruction() {}

    private record Target(BlockPos pos, BlockState state, BlockEntity inventory) {}
    private static final class Job {
        final ServerLevel level; final Explosion explosion; final List<Target> targets;
        final List<BlockPos> fireCandidates; final boolean fire; final RandomSource random = RandomSource.create();
        int next, nextFire;
        Job(ServerLevel level, Explosion explosion, List<Target> targets, List<BlockPos> fireCandidates, boolean fire) {
            this.level = level; this.explosion = explosion; this.targets = targets;
            this.fireCandidates = fireCandidates; this.fire = fire;
        }
        boolean pending() { return next < targets.size() || nextFire < fireCandidates.size(); }
    }
    private static final class State {
        final ArrayDeque<Job> jobs = new ArrayDeque<>();
        long lastTickNanos, maxTickNanos, processedBlocks, serviceTicks;
    }
    public record Stats(int jobs, long processedBlocks, long serviceTicks, long lastTickNanos, long maxTickNanos) {}

    public static Stats stats(MinecraftServer server) {
        State state = SERVERS.get(server);
        return state == null ? new Stats(0, 0, 0, 0, 0)
                : new Stats(state.jobs.size(), state.processedBlocks, state.serviceTicks, state.lastTickNanos, state.maxTickNanos);
    }

    public static int remainingJobs(ServerLevel level) {
        State state = SERVERS.get(level.getServer());
        return state == null ? 0 : (int)state.jobs.stream().filter(job -> job.level == level).count();
    }

    public static void enqueue(ServerLevel level, Explosion explosion, float radius, boolean fire) {
        if (explosion.getToBlow().isEmpty() || !explosion.interactsWithBlocks()) return;
        List<List<Target>> shells = new ArrayList<>((int)radius + 1);
        List<BlockPos> fireCandidates = new ArrayList<>();
        for (int i = 0; i <= radius; i++) shells.add(new ArrayList<>());
        for (var pos : explosion.getToBlow()) {
            double distance = pos.distToCenterSqr(explosion.center());
            if (distance > radius * radius || !level.hasChunkAt(pos)) continue;
            BlockState state=level.getBlockState(pos);
            if (state.isAir()) {
                if (fire) fireCandidates.add(pos.immutable());
                continue;
            }
            var blockEntity=state.hasBlockEntity()?level.getBlockEntity(pos):null;
            shells.get(Math.min((int)radius, (int)Math.sqrt(distance))).add(new Target(pos.immutable(), state,
                    blockEntity instanceof Container?blockEntity:null));
        }
        List<Target> targets = new ArrayList<>(explosion.getToBlow().size());
        shells.forEach(targets::addAll);
        if (!targets.isEmpty() || !fireCandidates.isEmpty()) SERVERS.computeIfAbsent(level.getServer(), ignored -> new State())
                .jobs.addLast(new Job(level, explosion, targets, fireCandidates, fire));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        State state = SERVERS.get(event.getServer());
        if (state == null || state.jobs.isEmpty()) return;
        long started = System.nanoTime(); int processed = 0, visitedTargets = 0;
        while (!state.jobs.isEmpty() && processed < MAX_BLOCKS_PER_TICK && System.nanoTime() - started < MAX_NANOS_PER_TICK) {
            Job job = state.jobs.removeFirst();
            if (!job.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) continue;
            for (int slice = 0; slice < 32 && job.pending() && processed < MAX_BLOCKS_PER_TICK; slice++) {
                if (System.nanoTime() - started >= MAX_NANOS_PER_TICK) break;
                processed++;
                if (job.next >= job.targets.size()) {
                    ignite(job, job.fireCandidates.get(job.nextFire++));
                    continue;
                }
                Target target = job.targets.get(job.next++); visitedTargets++;
                if (!job.level.hasChunkAt(target.pos())) continue;
                BlockState current=job.level.getBlockState(target.pos());
                if (target.inventory()!=null) {
                    // Double-chest connections and furnace lighting can change while the same inventory remains.
                    if (!current.is(target.state().getBlock()) || job.level.getBlockEntity(target.pos())!=target.inventory()) continue;
                } else if (current!=target.state()) continue;
                destroy(job, target, current);
                // Never ignite until every approved destruction callback in this crater has finished.
                if (job.fire && job.level.getBlockState(target.pos()).isAir()) job.fireCandidates.add(target.pos());
            }
            if (job.pending()) state.jobs.addLast(job);
        }
        state.lastTickNanos = System.nanoTime() - started;
        state.maxTickNanos = Math.max(state.maxTickNanos, state.lastTickNanos);
        state.processedBlocks += visitedTargets; state.serviceTicks++;
    }

    private static void ignite(Job job, BlockPos pos) {
        if (!job.level.hasChunkAt(pos) || !job.level.getBlockState(pos).isAir()
                || pos.getY() <= job.level.getMinBuildHeight()) return;
        var below = pos.below();
        if (job.level.getBlockState(below).isSolidRender(job.level, below) && job.random.nextInt(3) == 0)
            job.level.setBlockAndUpdate(pos, BaseFireBlock.getState(job.level, pos));
    }

    private static void destroy(Job job, Target target, BlockState state) {
        var blockEntity=job.level.getBlockEntity(target.pos());
        if (!(blockEntity instanceof Container container)) {
            InfernalExplosionLoot.withoutBlockDrops(job.explosion,
                    () -> SourceExplosionBlocks.destroy(job.level, target.pos(), state, job.explosion));
            return;
        }
        // Only this physical block's slots: combined or remote item handlers may expose other containers.
        boolean dropInventory=state.is(Blocks.DECORATED_POT)
                || !TrialBlockDrops.suppresses(job.level, target.pos());
        List<ItemStack> contents=new ArrayList<>(container.getContainerSize());
        try {
            for (int slot=0; slot<container.getContainerSize(); slot++) contents.add(container.removeItemNoUpdate(slot));
            // The empty inventory prevents native removal callbacks from duplicating the saved contents.
            InfernalExplosionLoot.withoutBlockDrops(job.explosion,
                    () -> SourceExplosionBlocks.destroy(job.level, target.pos(), state, job.explosion));
        } finally {
            if (job.level.getBlockEntity(target.pos())==blockEntity) {
                // A custom block may reject its removal callback after the public protection event.
                for (int slot=0; slot<contents.size(); slot++) container.setItem(slot, contents.get(slot));
                container.setChanged();
            } else if (dropInventory) {
                // Outside suppression and explosion decay; shulker-box contents also survive as loose items.
                for (ItemStack stack:contents) if (!stack.isEmpty())
                    Containers.dropItemStack(job.level, target.pos().getX(), target.pos().getY(), target.pos().getZ(), stack);
            }
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = SERVERS.get(level.getServer());
        if (state != null) state.jobs.removeIf(job -> job.level == level);
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
}
