package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native skeleton joins, calendar growth and one-time saved-profile migration. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class NativeSkeletonHealthGameTests {
    private static final String PROFILE = "modern_ite:source_profile";
    private static final String MIGRATED = "modern_ite:skeleton_health_v2";
    private static final ResourceLocation EXTRA_HEALTH = ResourceLocation.fromNamespaceAndPath("modern_ite_test", "skeleton_extra_health");

    private NativeSkeletonHealthGameTests() {}

    @GameTest(template = "gear_empty", batch = "native_skeleton_health")
    public static void actualFreshJoinsStartAtSixAndUseTwentyReferenceGrowth(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            long[] days = {1L, 2L, 33L, 65L};
            double[] maxima = {6D, 6.1D, 9.2D, 10.672D};
            for (int index = 0; index < days.length; index++) {
                fixture.day(days[index]);
                Mob skeleton = fixture.spawn(EntityType.SKELETON);
                health(helper, skeleton, 6D, maxima[index], maxima[index]);
                helper.assertTrue(skeleton.getPersistentData().getBoolean(PROFILE)
                                && skeleton.getPersistentData().getBoolean(MIGRATED),
                        "The actual new skeleton join records its corrected native profile");
                close(helper, skeleton.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), 8D, "The addon skeleton attack base is retained");
                close(helper, skeleton.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), .3D, "The addon skeleton movement base is retained");
                close(helper, skeleton.getAttributeBaseValue(Attributes.FOLLOW_RANGE), 64D, "The addon skeleton follow range is retained");
            }
            fixture.day(1L);
            Mob wither = fixture.spawn(EntityType.WITHER_SKELETON);
            health(helper, wither, 45D, 45D, 45D);
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "native_skeleton_health")
    public static void injuredNativeSkeletonRecalculatesGrowthAcrossSavedAndRepeatedJoins(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.day(1L);
            Mob skeleton = fixture.spawn(EntityType.SKELETON);
            skeleton.setHealth(3F);
            fixture.day(33L);
            MonsterDayScaling.refresh(skeleton);
            health(helper, skeleton, 6D, 9.2D, 4.6D);
            fixture.day(65L);
            MonsterDayScaling.refresh(skeleton);
            health(helper, skeleton, 6D, 10.672D, 5.336D);
            Mob restored = fixture.reload(skeleton);
            fixture.day(1L);
            for (int repeat = 0; repeat < 20; repeat++) {
                fixture.loadedJoin(restored);
                health(helper, restored, 6D, 6D, 3D);
            }
            helper.assertTrue(restored.getPersistentData().getCompound(MonsterDayScaling.DATA_KEY).getLong("day") == 1L,
                    "Loading after a rollback updates the saved skeleton to the current day");
            fixture.day(65L);
            tickGrowth(restored);
            helper.assertTrue(restored.getAttribute(Attributes.MAX_HEALTH).getModifiers().size() == 1,
                    "The new date reinstalls exactly one calendar modifier");
            Mob secondReload = fixture.reload(restored);
            fixture.loadedJoin(secondReload);
            health(helper, secondReload, 6D, 10.672D, 5.336D);
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "native_skeleton_health")
    public static void legacyFifteenHealthProfilesMigrateOnceWithoutHealingOrCompounding(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            long[] days = {1L, 33L, 65L};
            double[] maxima = {6D, 9.2D, 10.672D};
            for (int index = 0; index < days.length; index++) {
                fixture.day(days[index]);
                Mob legacy = fixture.legacy(15D);
                MonsterDayScaling.refresh(legacy);
                legacy.setHealth(legacy.getMaxHealth() / 4F);
                Mob restored = fixture.reload(legacy);
                close(helper, restored.getAttributeBaseValue(Attributes.MAX_HEALTH), 15D,
                        "The real saved entity still contains the old addon fifteen-health base before joining");
                for (int repeat = 0; repeat < 20; repeat++) {
                    fixture.loadedJoin(restored);
                    health(helper, restored, 6D, maxima[index], maxima[index] / 4D);
                }
                helper.assertTrue(restored.getPersistentData().getBoolean(PROFILE)
                                && restored.getPersistentData().getBoolean(MIGRATED),
                        "The legacy native profile gains the one-time skeleton health migration marker");
                Mob secondReload = fixture.reload(restored);
                fixture.loadedJoin(secondReload);
                health(helper, secondReload, 6D, maxima[index], maxima[index] / 4D);
                helper.assertTrue(secondReload.getPersistentData().getBoolean(MIGRATED),
                        "The completed migration marker persists through the next real entity save and load");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "native_skeleton_health")
    public static void legacyMigrationRetainsIndependentHealthModifiersAndUsesTheirCorrectedBasis(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.day(65L);
            Mob legacy = fixture.legacy(15D);
            legacy.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(EXTRA_HEALTH, 4D,
                    AttributeModifier.Operation.ADD_VALUE));
            MonsterDayScaling.refresh(legacy);
            close(helper, legacy.getMaxHealth(), 25.752D, "The saved old base and independent modifier have their original grown maximum");
            legacy.setHealth(legacy.getMaxHealth() / 4F);
            Mob restored = fixture.reload(legacy);
            fixture.day(1L);
            for (int repeat = 0; repeat < 20; repeat++) {
                fixture.loadedJoin(restored);
                health(helper, restored, 6D, 10D, 2.5D);
                AttributeModifier extra = restored.getAttribute(Attributes.MAX_HEALTH).getModifier(EXTRA_HEALTH);
                helper.assertTrue(extra != null && extra.amount() == 4D
                                && extra.operation() == AttributeModifier.Operation.ADD_VALUE,
                        "The migration preserves the saved independent native health modifier");
                helper.assertTrue(restored.getAttribute(Attributes.MAX_HEALTH).getModifiers().size() == 1,
                        "Only the independent modifier remains after rolling back to the first day");
            }
            fixture.day(65L);
            Mob secondReload = fixture.reload(restored);
            fixture.loadedJoin(secondReload);
            health(helper, secondReload, 6D, 15.312D, 3.828D);
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "native_skeleton_health")
    public static void customNonFifteenProfilesKeepTheirHealthAndAreMarkedWithoutMigration(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            double[] bases = {12D, 20D, 30D};
            double[] maxima = {17.632D, 26.912D, 38.512D};
            for (int index = 0; index < bases.length; index++) {
                fixture.day(65L);
                Mob custom = fixture.legacy(bases[index]);
                MonsterDayScaling.refresh(custom);
                custom.setHealth(custom.getMaxHealth() / 2F);
                Mob restored = fixture.reload(custom);
                for (int repeat = 0; repeat < 3; repeat++) {
                    fixture.loadedJoin(restored);
                    health(helper, restored, bases[index], maxima[index], maxima[index] / 2D);
                }
                helper.assertTrue(restored.getPersistentData().getBoolean(MIGRATED),
                        "A custom saved health base is marked as checked without overwriting its value");
                restored.getAttribute(Attributes.MAX_HEALTH).setBaseValue(15D);
                MonsterDayScaling.refresh(restored);
                restored.setHealth(restored.getMaxHealth() / 2F);
                fixture.loadedJoin(restored);
                health(helper, restored, 15D, 21.112D, 10.556D);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "native_skeleton_health")
    public static void iteZombiesAndSkeletonsGrowAndRollBackWithTheCurrentWorldDay(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, MonsterGrowthRules.DEFAULTS)) {
            var kinds = List.of(ExtremeKind.ZOMBIE_CHAOS, ExtremeKind.ZOMBIE_FLY_AWAY, ExtremeKind.ZOMBIE_GHAST,
                    ExtremeKind.ZOMBIE_KNOCKBACK, ExtremeKind.ZOMBIE_INVISIBLE_LOOK_UP, ExtremeKind.MIRROR_ZOMBIE,
                    ExtremeKind.MIMIC_ZOMBIE, ExtremeKind.SKELETON_ZOMBIE, ExtremeKind.FISHING_ROD_ZOMBIE,
                    ExtremeKind.DRAGON_ZOMBIE, ExtremeKind.RANDOM_ZOMBIE, ExtremeKind.MIRROR_SKELETON,
                    ExtremeKind.SKELETON_FAST_ARROW, ExtremeKind.SKELETON_KNOCKBACK, ExtremeKind.SKELETON_LIGHTNING,
                    ExtremeKind.SKELETON_FLOATING, ExtremeKind.SKELETON_BLAZE, ExtremeKind.WITHER_SKELETON_ARROW);
            List<EntityType<?>> types = new ArrayList<>();
            kinds.forEach(kind -> types.add(ExtremeMobs.type(kind)));
            types.add(MiteMobs.type(MobKind.LONGDEAD));
            for (EntityType<?> type : types) {
                fixture.day(1L);
                Mob mob = fixture.spawn(type);
                double base = type == ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE) ? 10D : 15D;
                float initialMaximum = mob.getMaxHealth();
                health(helper, mob, base, initialMaximum, initialMaximum);
                mob.setHealth(initialMaximum / 2F);
                fixture.day(65L);
                tickGrowth(mob);
                double grown = MonsterGrowthRules.health(65L, initialMaximum, false, MonsterGrowthRules.DEFAULTS, 1D);
                health(helper, mob, base, grown, grown / 2D);
                fixture.day(1L);
                tickGrowth(mob);
                health(helper, mob, base, initialMaximum, initialMaximum / 2F);
                fixture.day(65L);
                tickGrowth(mob);
                Mob restored = fixture.reload(mob);
                fixture.day(1L);
                fixture.loadedJoin(restored);
                health(helper, restored, base, initialMaximum, initialMaximum / 2F);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "native_skeleton_health")
    public static void speciesHealthCurveRecalculatesBeforeSharedGrowthAndPreservesOtherHealthModifiers(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.day(1L);
            Mob mob = fixture.spawn(ExtremeMobs.type(ExtremeKind.EXCHANGER));
            double original = (.5D - 7D / 27D) * 50D;
            var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
            attribute.addPermanentModifier(new AttributeModifier(EXTRA_HEALTH, 4D, AttributeModifier.Operation.ADD_VALUE));
            mob.setHealth(mob.getMaxHealth() / 2F);
            fixture.day(65L);
            tickGrowth(mob);
            double next = (.5D + 2D / 22D) * 50D + 20D;
            double grown = (next + 4D + 3.2D) * 1.16D;
            health(helper, mob, next, grown, grown / 2D);
            Mob old = fixture.reload(mob);
            old.getPersistentData().remove(MobAbilities.SPECIES_HEALTH_BASE);
            fixture.day(1L);
            tickGrowth(mob);
            health(helper, mob, original, original + 4D, (original + 4D) / 2D);
            fixture.loadedJoin(old);
            health(helper, old, original, original + 4D, (original + 4D) / 2D);
            old.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            old.setHealth(52F);
            fixture.day(65L);
            tickGrowth(old);
            double custom = (100D + 4D + 3.2D) * 1.16D;
            health(helper, old, 100D, custom, custom / 2D);
        }
        helper.succeed();
    }

    private static void tickGrowth(Mob mob) {
        mob.tickCount = Math.floorMod(-mob.getId(), 20);
        MonsterDayScaling.tick(new EntityTickEvent.Post(mob));
    }

    private static void health(GameTestHelper helper, Mob mob, double base, double maximum, double current) {
        close(helper, mob.getAttributeBaseValue(Attributes.MAX_HEALTH), base, "Native maximum-health base");
        close(helper, mob.getMaxHealth(), maximum, "Actual grown maximum health");
        close(helper, mob.getHealth(), current, "The saved injured-health fraction is preserved");
    }

    private static void close(GameTestHelper helper, double actual, double expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-4D, description + " (" + actual + " vs " + expected + ")");
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<Mob> entities = new ArrayList<>();
        private final long time;
        private final long highestDay;
        private final boolean historyDirty;
        private final MiteConfig.Snapshot previous = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot()
                ? null : MiteConfig.effectiveSnapshot();

        private Fixture(GameTestHelper helper) {
            this(helper, new MonsterGrowthRules.Settings(20D, 0D, 20D, .005D, .005D,
                    MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA));
        }

        private Fixture(GameTestHelper helper, MonsterGrowthRules.Settings growth) {
            this.helper = helper;
            level = helper.getLevel();
            time = level.getServer().overworld().getDayTime();
            var history = BloodMoonInvasionData.get(level);
            highestDay = history.highestDay();
            historyDirty = history.isDirty();
            var source = MiteConfig.effectiveSnapshot();
            MiteConfig.receiveServerSnapshot(new MiteConfig.Snapshot(source.ores(), 1D, 1D, 1D,
                    source.reachMultiplier(), source.reachReduction(), source.heightPenalty(), source.underworldTrials(), source.overworldTrials(),
                    source.caps(), source.disabled(), growth, source.oreCandidates(), source.oreChances(),
                    source.overworldOreCandidates(), source.overworldOreChances(), source.overworldOres(), source.enchantmentChances(),
                    source.monsterSpawnRates(), source.oreHeights(), source.overworldOreHeights(), source.spawningSettings(),
                    source.monsterSpawnWeights(), source.betterCombatMobs()));
        }

        private void day(long day) {
            level.getServer().overworld().setDayTime((day - 1L) * 24_000L + 6_000L);
            BloodMoonInvasionData.get(level).observeDay(day);
        }

        private Mob create(EntityType<?> type) {
            Mob mob = (Mob) type.create(level);
            if (mob == null) throw new AssertionError("The registered native skeleton fixture creates");
            mob.moveTo(helper.absolutePos(new BlockPos(1, 180, 1)).getCenter());
            mob.setNoAi(true);
            mob.setNoGravity(true);
            if (type == EntityType.SKELETON && mob instanceof R196Skeleton skeleton) skeleton.forceSkeletonType(0);
            entities.add(mob);
            return mob;
        }

        private Mob spawn(EntityType<?> type) {
            Mob mob = create(type);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWN_EGG, null);
            helper.assertTrue(!mob.isSpawnCancelled() && level.addFreshEntity(mob),
                    "The real finalized native entity passes the installed join event pipeline");
            return mob;
        }

        private Mob legacy(double base) {
            Mob mob = create(EntityType.SKELETON);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(base);
            mob.setHealth((float) base);
            mob.getPersistentData().putBoolean(PROFILE, true);
            mob.getPersistentData().remove(MIGRATED);
            return mob;
        }

        private Mob reload(Mob original) {
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            Mob restored = create(original.getType());
            restored.load(saved);
            restored.setUUID(UUID.randomUUID());
            return restored;
        }

        private void loadedJoin(Mob mob) {
            EntityJoinLevelEvent event = new EntityJoinLevelEvent(mob, level, true);
            NeoForge.EVENT_BUS.post(event);
            helper.assertFalse(event.isCanceled(), "The real saved-entity join pipeline admits the existing skeleton");
        }

        @Override public void close() {
            entities.forEach(Mob::discard);
            level.getServer().overworld().setDayTime(time);
            var history = BloodMoonInvasionData.get(level);
            history.restoreProgress(highestDay);
            history.setDirty(historyDirty);
            MiteConfig.clearServerSnapshot();
            if (previous != null) MiteConfig.receiveServerSnapshot(previous);
        }
    }
}
