package io.github.ningmeng123.modernite.entity;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Opt-in thick-ground benchmark; excluded from the ordinary namespace set. @author NingMeng123 */
@GameTestHolder("modern_ite_infernal_benchmark")
@PrefixGameTestTemplate(false)
public final class InfernalExplosionBenchmarkGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private InfernalExplosionBenchmarkGameTests() {}

    @GameTest(template = "gear_empty", batch = "infernal_benchmark", timeoutTicks = 1200)
    public static void fortyBlockStoneHemisphereUsesNoDropsAndReportsBudgetedTicks(GameTestHelper h) {
        var level = h.getLevel();
        var terrain = new LinkedHashMap<BlockPos, BlockState>();
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean blockDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        boolean dropDecay = level.getGameRules().getBoolean(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY);
        var site = new SpawnBalanceGameTests.Site(h);
        AABB cleanupBounds = new AABB(site.origin, site.origin).inflate(44);
        Runnable releaseChunks = InfernalExplosionGameTestSupport.holdChunks(level, cleanupBounds);
        var maxServiceNanos = new AtomicLong();
        Consumer<ServerTickEvent.Post> timing = event -> {
            if (event.getServer() == level.getServer())
                maxServiceNanos.accumulateAndGet(InfernalExplosionDestruction.stats(event.getServer()).lastTickNanos(), Math::max);
        };
        Runnable cleanup = () -> {
            NeoForge.EVENT_BUS.unregister(timing);
            level.getEntitiesOfClass(ItemEntity.class, cleanupBounds).forEach(ItemEntity::discard);
            level.getEntitiesOfClass(ExperienceOrb.class, cleanupBounds).forEach(ExperienceOrb::discard);
            terrain.forEach((pos, state) -> level.setBlock(pos, state, 2));
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(blockDrops, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(dropDecay, level.getServer());
            site.close();
            releaseChunks.run();
        };
        try {
            site.date(256, 256, true);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(true, level.getServer());
            Creeper creeper = (Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
            var center = creeper.position().add(0, creeper.getBbHeight() / 4, 0);
            var floor = BlockPos.containing(site.origin).below();
            long setupStarted = System.nanoTime();
            for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) for (int depth = 0; depth < 40; depth++) {
                var pos = floor.offset(x, -depth, z);
                if (pos.distToCenterSqr(center) > 40D * 40D) continue;
                terrain.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            }
            int stoneCount = terrain.size();
            var outside = floor.offset(41, 0, 0);
            terrain.put(outside, level.getBlockState(outside));
            level.setBlock(outside, Blocks.STONE.defaultBlockState(), 2);
            h.assertTrue(stoneCount >= 120_000, "The benchmark contains a real thick hemisphere, stones=" + stoneCount);
            LOGGER.info("Infernal benchmark terrain ready: stones={}, setupMs={}", stoneCount, (System.nanoTime() - setupStarted) / 1_000_000D);

            var selected = new AtomicInteger();
            var selectionFinished = new AtomicLong();
            Consumer<ExplosionEvent.Detonate> observe = event -> {
                if (event.getExplosion().getDirectSourceEntity() == creeper) {
                    selected.set(event.getAffectedBlocks().size());
                    selectionFinished.set(System.nanoTime());
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExplosionEvent.Detonate.class, observe);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, timing);
            var before = InfernalExplosionDestruction.stats(level.getServer());
            long started = System.nanoTime();
            try {
                level.explode(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), 3F, Level.ExplosionInteraction.MOB);
            } finally {
                long finished = System.nanoTime();
                NeoForge.EVENT_BUS.unregister(observe);
                LOGGER.info("Infernal benchmark immediate explosion: stones={}, selectedBlocks={}, initialMs={}, selectionMs={}, enqueueMs={}, mobGriefing=true, doTileDrops=true, mobExplosionDropDecay=true, dedicatedLoot=false",
                        stoneCount, selected.get(), (finished - started) / 1_000_000D,
                        selectionFinished.get() == 0 ? -1D : (selectionFinished.get() - started) / 1_000_000D,
                        selectionFinished.get() == 0 ? -1D : (finished - selectionFinished.get()) / 1_000_000D);
            }
            h.assertTrue(selected.get() >= stoneCount, "The real explosion selected the entire stone hemisphere");
            h.assertTrue(InfernalExplosionDestruction.remainingJobs(level) > 0, "The thick hemisphere is processed over bounded server ticks");
            InfernalExplosionGameTestSupport.finishAfterJobs(h, level, () -> {
                for (var inside : new BlockPos[]{floor, floor.offset(39, 0, 0), floor.offset(0, -37, 0), floor.offset(20, -20, 20)})
                    h.assertTrue(level.getBlockState(inside).isAir(), "A representative interior stone is destroyed: " + inside.subtract(floor)+", state="+level.getBlockState(inside)+", loaded="+level.hasChunkAt(inside));
                h.assertTrue(level.getBlockState(outside).is(Blocks.STONE), "Stone at forty-one blocks survives the thick-ground explosion");
                h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, cleanupBounds).isEmpty(), "The entire thick crater produces zero item entities with normal drop rules enabled");
                h.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, cleanupBounds).isEmpty(), "The entire thick crater produces zero XP");
                var stats = InfernalExplosionDestruction.stats(level.getServer());
                LOGGER.info("Infernal benchmark queue complete: selectedBlocks={}, totalWallMs={}, serviceTicks={}, maxServiceTickMs={}, processedBlocks={}, itemEntities=0, experienceOrbs=0",
                        selected.get(), (System.nanoTime() - started) / 1_000_000D, stats.serviceTicks() - before.serviceTicks(), maxServiceNanos.get() / 1_000_000D, stats.processedBlocks() - before.processedBlocks());
            }, cleanup);
        } catch (Throwable failure) { cleanup.run(); throw failure; }
    }
}
