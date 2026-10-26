package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.world.SourceExplosionBlocks;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
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

    private record Target(BlockPos pos, BlockState state) {}
    private static final class Job {
        final ServerLevel level; final Explosion explosion; final List<Target> targets; int next;
        Job(ServerLevel level, Explosion explosion, List<Target> targets) { this.level = level; this.explosion = explosion; this.targets = targets; }
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

    public static void enqueue(ServerLevel level, Explosion explosion, float radius) {
        if (explosion.getToBlow().isEmpty() || !explosion.interactsWithBlocks()) return;
        List<List<Target>> shells = new ArrayList<>((int)radius + 1);
        for (int i = 0; i <= radius; i++) shells.add(new ArrayList<>());
        for (var pos : explosion.getToBlow()) {
            double distance = pos.distToCenterSqr(explosion.center());
            if (distance > radius * radius || !level.hasChunkAt(pos)) continue;
            shells.get(Math.min((int)radius, (int)Math.sqrt(distance))).add(new Target(pos.immutable(), level.getBlockState(pos)));
        }
        List<Target> targets = new ArrayList<>(explosion.getToBlow().size());
        shells.forEach(targets::addAll);
        if (!targets.isEmpty()) SERVERS.computeIfAbsent(level.getServer(), ignored -> new State()).jobs.addLast(new Job(level, explosion, targets));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        State state = SERVERS.get(event.getServer());
        if (state == null || state.jobs.isEmpty()) return;
        long started = System.nanoTime(); int processed = 0;
        while (!state.jobs.isEmpty() && processed < MAX_BLOCKS_PER_TICK && System.nanoTime() - started < MAX_NANOS_PER_TICK) {
            Job job = state.jobs.removeFirst();
            if (!job.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) continue;
            for (int slice = 0; slice < 32 && job.next < job.targets.size() && processed < MAX_BLOCKS_PER_TICK; slice++) {
                if (System.nanoTime() - started >= MAX_NANOS_PER_TICK) break;
                Target target = job.targets.get(job.next++); processed++;
                if (!job.level.hasChunkAt(target.pos()) || job.level.getBlockState(target.pos()) != target.state()) continue;
                InfernalExplosionLoot.withoutBlockDrops(job.explosion, () -> {
                    if (job.level.getBlockEntity(target.pos()) instanceof Container container) container.clearContent();
                    SourceExplosionBlocks.destroy(job.level, target.pos(), target.state(), job.explosion);
                });
            }
            if (job.next < job.targets.size()) state.jobs.addLast(job);
        }
        state.lastTickNanos = System.nanoTime() - started;
        state.maxTickNanos = Math.max(state.maxTickNanos, state.lastTickNanos);
        state.processedBlocks += processed; state.serviceTicks++;
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = SERVERS.get(level.getServer());
        if (state != null) state.jobs.removeIf(job -> job.level == level);
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
}
