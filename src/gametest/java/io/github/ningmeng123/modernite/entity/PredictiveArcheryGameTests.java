package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Seeded native arrow collisions against a registered survival player's real hitbox. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class PredictiveArcheryGameTests {
    private static final int SHOTS = 100;
    private static final float TARGET_HEALTH = 1000F;
    private static final Vec3 WALKING = new Vec3(0D, 0D, .215D);
    private static final double[][] STATIONARY_SCENARIOS = {{6D, 0D}, {15D, 0D}, {30D, 0D}, {15D, 5D}, {15D, -5D}};
    private static final Map<GameTestHelper, Vec3> ARENAS = new WeakHashMap<>();
    private static int nextArena;

    private PredictiveArcheryGameTests() {}

    private static Vec3 origin(GameTestHelper h) {
        // Separate altitude lanes prevent simultaneously waiting targets from intercepting another test's arrows.
        return ARENAS.computeIfAbsent(h, helper -> {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            int lane = nextArena++ % 12;
            return new Vec3(origin.getX() + 1.5D, Math.min(100D + lane * 12D, helper.getLevel().getMaxBuildHeight() - 24D), origin.getZ() + 1.5D);
        });
    }

    private static Player target(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(origin(h).add(15D, 0D, 0D));
        player.setNoGravity(true);
        player.setOnGround(true);
        player.getInventory().clearContent();
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
        player.setHealth(TARGET_HEALTH);
        // Loading the full flight corridor makes entity collision independent of the template's tiny size.
        BlockPos base = BlockPos.containing(origin(h));
        for (int x = base.getX() >> 4; x <= (base.getX() + 40) >> 4; x++)
            for (int z = (base.getZ() - 16) >> 4; z <= (base.getZ() + 32) >> 4; z++) h.getLevel().getChunk(x, z);
        h.assertTrue(h.getLevel().addFreshEntity(player), "The survival target enters the real entity manager");
        return player;
    }

    private static List<EntityType<? extends AbstractSkeleton>> archers() {
        List<EntityType<? extends AbstractSkeleton>> types = new ArrayList<>();
        types.add(EntityType.SKELETON);
        types.add((EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.LONGDEAD));
        for (ExtremeKind kind : ExtremeKind.values()) if (kind.family == ExtremeKind.Family.SKELETON)
            types.add((EntityType<? extends AbstractSkeleton>)(EntityType<?>)ExtremeMobs.type(kind));
        return types;
    }

    private static AbstractSkeleton archer(GameTestHelper h, EntityType<? extends AbstractSkeleton> type) {
        AbstractSkeleton skeleton = type.create(h.getLevel());
        h.assertTrue(skeleton != null, "The registered archer creates: " + type);
        prepareShooter(h, skeleton);
        return skeleton;
    }

    private static void prepareShooter(GameTestHelper h, Mob shooter) {
        shooter.moveTo(origin(h));
        shooter.setNoAi(true);
        shooter.setNoGravity(true);
        // Direct performRangedAttack exercises the real arrow creation without random encounter bonuses.
        shooter.getPersistentData().putBoolean("modern_ite:legacy_rolls", true);
        shooter.getPersistentData().putBoolean("modern_ite:triple", false);
        shooter.getPersistentData().putBoolean("modern_ite:fire_arrows", false);
        shooter.getPersistentData().putBoolean("modern_ite:compressed", false);
        shooter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        shooter.clearFire();
    }

    private static void reset(Player target, Vec3 position, Vec3 movement) {
        target.moveTo(position);
        target.setPose(Pose.STANDING);
        target.setSwimming(false);
        target.setOnGround(true);
        target.fallDistance = 0F;
        target.removeAllEffects();
        target.clearFire();
        target.setHealth(TARGET_HEALTH);
        target.setAbsorptionAmount(0F);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        target.setDeltaMovement(movement);
    }

    private static long seed(int shot) { return 0x4E696E674D656E67L + shot * 0x9E3779B97F4A7C15L; }

    private static void tickFlight(AbstractArrow arrow, Player target, Vec3 start, Vec3 movement) {
        for (int tick = 0; tick < 64 && !arrow.isRemoved(); tick++) {
            target.moveTo(start.add(movement.scale(tick + 1D)));
            target.setDeltaMovement(movement);
            arrow.tick();
            if (target.getHealth() < TARGET_HEALTH) break;
        }
    }

    private static AbstractArrow oneArrow(GameTestHelper h, CapturedShot captured, String context) {
        List<AbstractArrow> arrows = captured.arrows();
        h.assertValueEqual(arrows.size(), 1, "Exactly one actual arrow is released: " + context);
        return arrows.getFirst();
    }

    private static int nativeHits(GameTestHelper h, AbstractSkeleton skeleton, Player player, Vec3 position, Vec3 movement,
            boolean predict) {
        int hits = 0;
        for (int shot = 0; shot < SHOTS; shot++) {
            reset(player, position, predict ? movement : Vec3.ZERO);
            skeleton.getRandom().setSeed(seed(shot));
            try (CapturedShot captured = new CapturedShot(h.getLevel(), skeleton, seed(shot))) {
                skeleton.performRangedAttack(player, 1F);
                AbstractArrow arrow = oneArrow(h, captured, skeleton.getType().toString());
                tickFlight(arrow, player, position, movement);
                if (player.getHealth() < TARGET_HEALTH) hits++;
            }
        }
        return hits;
    }

    private static void stationaryMatrix(GameTestHelper h, Difficulty difficulty) {
        Player player = target(h);
        // A world tick is required before native projectile entity queries can see the mock player.
        h.runAfterDelay(1, () -> {
            Difficulty previous = h.getLevel().getDifficulty();
            int minimum = SHOTS;
            int arrows = 0;
            try {
                // Each entire matrix runs synchronously, restoring difficulty before another test can tick.
                h.getLevel().getServer().setDifficulty(difficulty, true);
                for (var type : archers()) {
                    AbstractSkeleton skeleton = archer(h, type);
                    try {
                        for (double[] scenario : STATIONARY_SCENARIOS) {
                            Vec3 position = origin(h).add(scenario[0], scenario[1], 0D);
                            int hits = nativeHits(h, skeleton, player, position, Vec3.ZERO, true);
                            minimum = Math.min(minimum, hits);
                            arrows += SHOTS;
                            h.assertTrue(hits >= 95, "At least 95/100 stationary player hits: " + type + " difficulty=" + difficulty
                                    + " range=" + scenario[0] + " height=" + scenario[1] + " actual=" + hits);
                        }
                    } finally { skeleton.discard(); }
                }
                System.out.println("[ModernITE predictive archery] " + difficulty + " stationary arrows=" + arrows + " minimum=" + minimum + "/100");
            } finally {
                h.getLevel().getServer().setDifficulty(previous, true);
                player.discard();
            }
            h.succeed();
        });
    }

    @GameTest(template = "gear_empty")
    public static void everyNativeArcherHitsStationaryPlayersAtLeast95PercentOnEasy(GameTestHelper h) {
        stationaryMatrix(h, Difficulty.EASY);
    }

    @GameTest(template = "gear_empty")
    public static void everyNativeArcherHitsStationaryPlayersAtLeast95PercentOnNormal(GameTestHelper h) {
        stationaryMatrix(h, Difficulty.NORMAL);
    }

    @GameTest(template = "gear_empty")
    public static void everyNativeArcherHitsStationaryPlayersAtLeast95PercentOnHard(GameTestHelper h) {
        stationaryMatrix(h, Difficulty.HARD);
    }

    @GameTest(template = "gear_empty")
    public static void leadingLateralWalkingBeatsTheSameNativeShotsWithoutMovementInformation(GameTestHelper h) {
        // Distinct targets give both controls their own first motion sample during this synchronous batch.
        Player ledPlayer = target(h);
        Player unledPlayer = target(h);
        h.runAfterDelay(1, () -> {
            try {
                for (var type : archers()) {
                    AbstractSkeleton skeleton = archer(h, type);
                    try {
                        Vec3 position = origin(h).add(15D, 0D, 0D);
                        unledPlayer.moveTo(origin(h).add(-10D, 10D, 20D));
                        int led = nativeHits(h, skeleton, ledPlayer, position, WALKING, true);
                        ledPlayer.moveTo(origin(h).add(-10D, 10D, 20D));
                        int unled = nativeHits(h, skeleton, unledPlayer, position, WALKING, false);
                        h.assertTrue(led >= 85 && led >= unled + 60,
                                "Actual native arrows lead a walking player: " + type + " led=" + led + "/100 without velocity=" + unled + "/100");
                    } finally { skeleton.discard(); }
                }
            } finally { ledPlayer.discard(); unledPlayer.discard(); }
            h.succeed();
        });
    }

    @GameTest(template = "gear_empty")
    public static void stoneCoverRejectsTheNativeShotAndTheSharedPredictiveHelper(GameTestHelper h) {
        Player player = target(h);
        h.runAfterDelay(1, () -> {
            BlockPos from = BlockPos.containing(origin(h).add(7D, -6D, -3D));
            BlockPos to = BlockPos.containing(origin(h).add(7D, 8D, 3D));
            try {
                for (BlockPos pos : BlockPos.betweenClosed(from, to)) h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                reset(player, origin(h).add(15D, 0D, 0D), WALKING);
                for (var type : archers()) {
                    AbstractSkeleton skeleton = archer(h, type);
                    try (CapturedShot captured = new CapturedShot(h.getLevel(), skeleton)) {
                        skeleton.performRangedAttack(player, 1F);
                        h.assertValueEqual(captured.arrows().size(), 0, "A completely stone-covered player receives no native shot: " + type);
                        AbstractArrow arrow = EntityType.ARROW.create(h.getLevel());
                        try {
                            arrow.setOwner(skeleton);
                            arrow.setPos(skeleton.getX(), skeleton.getEyeY() - .1D, skeleton.getZ());
                            h.assertFalse(PredictiveBowAiming.shoot(skeleton, arrow, player, 1.6F), "The common predictor refuses hidden targets");
                            h.assertTrue(arrow.getDeltaMovement().equals(Vec3.ZERO), "A refused shot retains zero launch velocity");
                        } finally { arrow.discard(); }
                    } finally { skeleton.discard(); }
                }
            } finally {
                for (BlockPos pos : BlockPos.betweenClosed(from, to)) h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                player.discard();
            }
            h.succeed();
        });
    }

    @GameTest(template = "gear_empty")
    public static void releasedArrowsFollowNativeGravityAndNeverTurnAfterAPlayerChangesDirection(GameTestHelper h) {
        Player player = target(h);
        h.runAfterDelay(1, () -> {
            AbstractSkeleton skeleton = archer(h, EntityType.SKELETON);
            try (CapturedShot captured = new CapturedShot(h.getLevel(), skeleton)) {
                reset(player, origin(h).add(15D, 0D, 0D), WALKING);
                skeleton.performRangedAttack(player, 1F);
                AbstractArrow arrow = oneArrow(h, captured, "one release before a direction change");
                player.moveTo(origin(h).add(-10D, 10D, -10D));
                player.setDeltaMovement(WALKING.scale(-1D));
                for (int tick = 0; tick < 5; tick++) {
                    Vec3 before = arrow.getDeltaMovement();
                    arrow.tick();
                    Vec3 expected = before.scale((double).99F).add(0D, -.05D, 0D);
                    h.assertTrue(arrow.getDeltaMovement().distanceToSqr(expected) < 1e-12D,
                            "Released arrow tick " + tick + " applies only native air drag and gravity");
                }
            } finally { skeleton.discard(); player.discard(); }
            h.succeed();
        });
    }

    @GameTest(template = "gear_empty")
    public static void pigmanBowGoalSharesTheSameAccurateReleaseAndWalkingPrediction(GameTestHelper h) {
        Player stationary = target(h);
        Player walking = target(h);
        h.runAfterDelay(1, () -> {
            ZombifiedPiglin pig = EntityType.ZOMBIFIED_PIGLIN.create(h.getLevel());
            prepareShooter(h, pig);
            try {
                for (Vec3 movement : List.of(Vec3.ZERO, WALKING)) {
                    Player player = movement.equals(Vec3.ZERO) ? stationary : walking;
                    Player inactive = player == stationary ? walking : stationary;
                    inactive.moveTo(origin(h).add(-10D, 10D, 20D));
                    pig.setTarget(player);
                    int hits = 0;
                    for (int shot = 0; shot < SHOTS; shot++) {
                        Vec3 position = origin(h).add(15D, 0D, 0D);
                        reset(player, position, movement);
                        pig.getRandom().setSeed(seed(shot));
                        try (CapturedShot captured = new CapturedShot(h.getLevel(), pig, seed(shot))) {
                            PigmanBowGoal goal = new PigmanBowGoal(pig);
                            h.assertTrue(goal.canUse(), "The real pigman goal accepts its survival-player target");
                            goal.tick();
                            AbstractArrow arrow = oneArrow(h, captured, "pigman goal");
                            tickFlight(arrow, player, position, movement);
                            if (player.getHealth() < TARGET_HEALTH) hits++;
                        }
                    }
                    h.assertTrue(hits >= (movement.equals(Vec3.ZERO) ? 95 : 85),
                            "Pigman goal's native projectiles hit the player: movement=" + movement + " actual=" + hits + "/100");
                }
            } finally { pig.discard(); stationary.discard(); walking.discard(); }
            h.succeed();
        });
    }

    /** Capture joins directly: newly spawned arrows need not wait for chunk tracking to be queryable. */
    private static final class CapturedShot implements AutoCloseable {
        private final List<Entity> spawned = new ArrayList<>();
        private final Consumer<EntityJoinLevelEvent> listener;
        private final Consumer<EntityEvent.EntityConstructing> constructing;

        private CapturedShot(ServerLevel level, Mob shooter) {
            this(level, shooter, seed(0));
        }

        private CapturedShot(ServerLevel level, Mob shooter, long seed) {
            // Projectile.shoot reads the arrow's RNG, so seed it at construction, before native release.
            constructing = event -> {
                if (event.getEntity() instanceof AbstractArrow arrow && arrow.level() == level) arrow.getRandom().setSeed(seed);
            };
            listener = event -> {
                if (event.isCanceled() || event.getLevel() != level) return;
                Entity entity = event.getEntity();
                if (entity instanceof AbstractArrow arrow && arrow.getOwner() == shooter
                        || entity instanceof LightningBolt && entity.distanceToSqr(shooter) < 4096D) spawned.add(entity);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityEvent.EntityConstructing.class, constructing);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, listener);
        }

        private List<AbstractArrow> arrows() {
            return spawned.stream().filter(e -> !e.isRemoved() && e instanceof AbstractArrow).map(e -> (AbstractArrow)e).toList();
        }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(listener);
            NeoForge.EVENT_BUS.unregister(constructing);
            spawned.forEach(Entity::discard);
        }
    }
}
