package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real extension spawn geometry at the reported negative height. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class NaturalSpawnPlacementGameTests {
    private NaturalSpawnPlacementGameTests() {}

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void everyGroundIdentityRejectsVoidAndAcceptsAStoneFloor(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            for (ExtremeKind kind : ExtremeKind.values()) {
                helper.assertTrue(SpawnPlacements.hasPlacement(ExtremeMobs.type(kind)),
                        "Every extension identity has an explicit placement registration: " + kind);
                if (kind.family == ExtremeKind.Family.GHAST) continue;
                helper.assertFalse(SpawnPlacements.isSpawnPositionOk(ExtremeMobs.type(kind), site.level, site.pos),
                        "No registered ground identity may use an unsupported air block: " + kind);
            }
            site.set(site.pos.below(), Blocks.STONE.defaultBlockState());
            for (ExtremeKind kind : ExtremeKind.values()) {
                if (kind.family == ExtremeKind.Family.GHAST) continue;
                helper.assertTrue(SpawnPlacements.isSpawnPositionOk(ExtremeMobs.type(kind), site.level, site.pos),
                        "A real stone floor remains valid for ground identity " + kind);
                helper.assertFalse(SpawnPlacements.isSpawnPositionOk(ExtremeMobs.type(kind), site.level, site.pos.above()),
                        "A floor two blocks below cannot support a spawn in midair: " + kind);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void actualNaturalQualificationRejectsDarkVoidButAllowsSupportedMonsters(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            helper.assertTrue(site.pos.getY() == -49 && site.level.getMaxLocalRawBrightness(site.pos) <= 7,
                    "The regression uses dark air at the reported negative height");
            for (ExtremeKind kind : List.of(ExtremeKind.ZOMBIE_IRON_BUCKET, ExtremeKind.MIRROR_SKELETON,
                    ExtremeKind.RUN_CREEPER, ExtremeKind.RED_GHOUL)) {
                Mob mob = site.mob(kind);
                helper.assertTrue(site.level.noCollision(mob) && mob.checkSpawnRules(site.level, MobSpawnType.NATURAL),
                        "The original collision and species predicates alone admit the void candidate " + kind);
                helper.assertFalse(qualifies(mob, site), "The actual scheduler qualification rejects dark void: " + kind);
                site.set(site.pos.below(), Blocks.STONE.defaultBlockState());
                helper.assertTrue(qualifies(mob, site), "The same entity qualifies after adding a real floor: " + kind);
                site.set(site.pos.below(), Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void naturalQualificationStillRejectsBlockedHeadroomAndWater(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            Mob mob = site.mob(ExtremeKind.ZOMBIE_IRON_BUCKET);
            site.set(site.pos.below(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(qualifies(mob, site), "The clear supported control is eligible");
            site.set(site.pos.above(), Blocks.STONE.defaultBlockState());
            helper.assertFalse(qualifies(mob, site), "The real upper body cannot intersect a solid block");
            site.set(site.pos.above(), Blocks.AIR.defaultBlockState());
            site.set(site.pos, Blocks.WATER.defaultBlockState());
            helper.assertFalse(qualifies(mob, site), "Ground monsters cannot use a water-filled spawn cell");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_glowstone", timeoutTicks = 100)
    public static void glowstonePlatformStillRejectsNaturalMonstersAfterRealLightPropagation(GameTestHelper helper) {
        Site site = new Site(helper, false);
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { site.close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { site.close(); }
        });
        site.set(site.pos.below(), Blocks.GLOWSTONE.defaultBlockState());
        Mob mob = site.mob(ExtremeKind.ZOMBIE_IRON_BUCKET);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(site.level.getMaxLocalRawBrightness(site.pos) > 7,
                "Wait for the actual glowstone block light to reach the spawn cell"))
                .thenExecute(() -> helper.assertFalse(qualifies(mob, site),
                        "A luminous platform does not become an eligible natural spawn floor"))
                .thenSucceed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void summonedGroundMobsKeepTheirSpawnReasons(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            Mob zombie = site.mob(ExtremeKind.ZOMBIE_IRON_BUCKET);
            for (MobSpawnType reason : List.of(MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND,
                    MobSpawnType.MOB_SUMMONED, MobSpawnType.CONVERSION)) {
                helper.assertTrue(zombie.checkSpawnRules(site.level, reason),
                        "The existing explicit-spawn behavior is preserved: " + reason);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_light_boundaries", timeoutTicks = 150)
    public static void oneAndSevenBlockLightRejectButCompleteDarknessAllows(GameTestHelper helper) {
        Site site=new Site(helper);
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original,GameTestInfo rerun,GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info,GameTestRunner runner) {site.close();}
            @Override public void testFailed(GameTestInfo info,GameTestRunner runner) {site.close();}
        });
        site.set(site.pos.below(),Blocks.STONE.defaultBlockState());
        Mob mob=site.mob(ExtremeKind.ZOMBIE_IRON_BUCKET);BlockPos lamp=site.pos.offset(2,0,0);
        helper.startSequence()
                .thenWaitUntil(()->helper.assertTrue(site.level.getBrightness(LightLayer.BLOCK,site.pos)==0,"Control cell is fully dark"))
                .thenExecute(()->{
                    helper.assertTrue(qualifies(mob,site),"A supported dark cave candidate remains eligible");
                    site.set(lamp,Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,3));
                })
                .thenWaitUntil(()->helper.assertTrue(site.level.getBrightness(LightLayer.BLOCK,site.pos)==1,"Light engine reaches the one-light boundary"))
                .thenExecute(()->{
                    helper.assertFalse(qualifies(mob,site),"Even one block-light level suppresses an extreme natural monster");
                    site.set(lamp,Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,9));
                })
                .thenWaitUntil(()->helper.assertTrue(site.level.getBrightness(LightLayer.BLOCK,site.pos)==7,"Light engine reaches the old seven-light boundary"))
                .thenExecute(()->helper.assertFalse(qualifies(mob,site),"The former level-seven threshold no longer permits a spawn"))
                .thenSucceed();
    }

    private static boolean qualifies(Mob mob, Site site) {
        return R196MobSpawning.qualifiesWithEvent(mob, site.level, MobSpawnType.NATURAL, true, true);
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final long time;
        final BloodMoonInvasionData progress;
        final long highestDay;
        final boolean progressDirty;
        final boolean graceEnded;
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final List<Mob> mobs = new ArrayList<>();
        boolean closed;

        Site(GameTestHelper helper) {
            this(helper, true);
        }

        Site(GameTestHelper helper, boolean night) {
            level = helper.getLevel();
            pos = helper.absolutePos(new BlockPos(3, 0, 3)).atY(-49);
            time = level.getDayTime();
            progress = BloodMoonInvasionData.get(level);
            highestDay = progress.highestDay();
            progressDirty = progress.isDirty();
            graceEnded = ExtremeWorldState.graceEnded(level);
            ExtremeWorldState.restoreGraceEnded(level,true);
            if (night) {
                level.setDayTime(32*24_000L+18000L);
                progress.restoreProgress(33);
                level.updateSkyBrightness();
            }
            for (BlockPos point : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 5, 3)))
                set(point, Blocks.AIR.defaultBlockState());
        }

        void set(BlockPos point, BlockState state) {
            previous.putIfAbsent(point.immutable(), level.getBlockState(point));
            level.setBlock(point, state, 2);
        }

        Mob mob(ExtremeKind kind) {
            Mob mob = ExtremeMobs.type(kind).create(level);
            if (mob == null) throw new AssertionError("Registered extension entity creates: " + kind);
            mob.moveTo(pos.getX() + .5, pos.getY() + .005, pos.getZ() + .5, 0, 0);
            mobs.add(mob);
            return mob;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            mobs.forEach(Mob::discard);
            previous.forEach((point, state) -> level.setBlock(point, state, 2));
            level.setDayTime(time);
            level.updateSkyBrightness();
            progress.restoreProgress(highestDay);
            progress.setDirty(progressDirty);
            ExtremeWorldState.restoreGraceEnded(level,graceEnded);
        }
    }
}
