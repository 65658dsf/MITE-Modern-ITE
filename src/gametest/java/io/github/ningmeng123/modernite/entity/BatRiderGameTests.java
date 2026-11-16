package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ambient.Bat;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual Extreme bat-rider lotteries, mounted spawn commits and rollback. @author NingMeng123 */
@GameTestHolder("modern_ite_bat_riders")
@PrefixGameTestTemplate(false)
public final class BatRiderGameTests {
    private BatRiderGameTests() {}

    @GameTest(template = "gear_empty")
    public static void sourceSkeletonTypesAndCreeperFamiliesChooseTheirMatchingBat(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            Mob skeleton = site.mob(EntityType.SKELETON);
            helper.assertTrue(BatRiders.mountType(skeleton) == EntityType.BAT, "Ordinary source type zero uses the ordinary bat");
            ((OrdinarySkeletonState)skeleton).mite$setSourceSkeletonType(2);
            helper.assertTrue(BatRiders.mountType(skeleton) == MiteMobs.type(MobKind.VAMPIRE_BAT), "Melee source skeleton metadata uses a vampire bat");
            Mob wither = site.mob(EntityType.WITHER_SKELETON);
            helper.assertTrue(BatRiders.mountType(wither) == MiteMobs.type(MobKind.VAMPIRE_BAT), "The native wither identity uses a vampire bat");
            R196Skeleton longdead = (R196Skeleton)site.mob(MiteMobs.type(MobKind.LONGDEAD));
            longdead.setSkeletonType(2);
            helper.assertTrue(BatRiders.mountType(longdead) == MiteMobs.type(MobKind.VAMPIRE_BAT), "The source longdead metadata shares the skeleton mount rule");
            helper.assertTrue(BatRiders.mountType(site.mob(EntityType.CREEPER)) == MiteMobs.type(MobKind.VAMPIRE_BAT), "A regular creeper uses a vampire bat");
            helper.assertTrue(BatRiders.mountType(site.mob(MiteMobs.type(MobKind.INFERNAL_CREEPER))) == null, "The source infernal creeper is outside this lottery");
            helper.assertTrue(BatRiders.mountType(site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER))) == null,
                    "The inherited black infernal identity is outside this lottery");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void actualFinalizeRollCreatesTheMountAndTheOtherFourOutcomesStayGrounded(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            for (EntityType<?> type : List.of(EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.CREEPER)) {
                Mob rider = site.mob(type);
                EntityType<? extends Mob> expected = BatRiders.mountType(rider);
                roll(rider, 0, () -> finalizeEgg(site, rider));
                helper.assertTrue(rider.getVehicle() instanceof Bat bat && bat.getType() == expected && bat.isAddedToLevel(),
                        "The successful native finalization roll creates and joins the expected bat: " + type);
                helper.assertTrue(site.base.level.addFreshEntity(rider), "The normal spawn caller adds its actual rider after finalization");
                helper.assertTrue(rider.isPassenger() && rider.getVehicle().getFirstPassenger() == rider, "The added pair keeps its riding relationship");
                BatRiders.settleMount(new EntityTickEvent.Pre(rider.getVehicle()));
                var laterRejectedJoin = new EntityJoinLevelEvent(rider, site.base.level, false);
                laterRejectedJoin.setCanceled(true);
                BatRiders.finishJoin(laterRejectedJoin);
                helper.assertTrue(rider.getVehicle() instanceof Bat bat && bat.isAddedToLevel() && !bat.isRemoved(),
                        "A later canceled rejoin preserves the already admitted mount");
            }
            for (int outcome = 1; outcome < 5; outcome++) {
                Mob rider = site.mob(EntityType.SKELETON);
                int before = site.bats.size();
                roll(rider, outcome, () -> finalizeEgg(site, rider));
                helper.assertFalse(rider.isPassenger(), "Each unsuccessful one-in-five outcome leaves the rider grounded");
                helper.assertValueEqual(site.bats.size(), before, "An unsuccessful outcome creates no extra bat");
            }
            Mob mounted = site.mob(EntityType.CREEPER);
            Mob existingBat = site.mob(EntityType.BAT);
            helper.assertTrue(mounted.startRiding(existingBat, true), "The fixture starts with an existing mount");
            roll(mounted, 0, () -> finalizeEgg(site, mounted));
            helper.assertTrue(mounted.getVehicle() == existingBat, "A supplied riding relationship remains the active mount");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void actualNaturalTransactionAddsItsFreshVehicleAndRiderAsOneCommittedTree(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            site.base.player(40);
            Mob rider = site.mob(EntityType.CREEPER);
            var result = roll(rider, 0, () -> R196MobSpawning.finalizeNaturalMonster(site.base.level, rider, null, 1));
            helper.assertValueEqual(result.monsters(), 1, "The monster allowance charges the actual creeper identity once");
            helper.assertTrue(rider.isAddedToLevel() && rider.getVehicle() instanceof Bat bat && bat.isAddedToLevel(),
                    "The real natural commit inserts its fresh bat vehicle and passenger");
            helper.assertValueEqual(site.bats.size(), 1, "A single natural candidate owns exactly one new bat");
            helper.assertTrue(site.bats.getFirst().getSpawnType() == MobSpawnType.NATURAL && rider.getSpawnType() == MobSpawnType.NATURAL,
                    "Both members retain their genuine natural spawn reason");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void naturalAllowanceParentCancellationAndExceptionsReclaimTheDetachedFreshBat(GameTestHelper helper) {
        for (int mode = 0; mode < 3; mode++) {
            try (Site site = new Site(helper)) {
                site.base.player(40);
                Mob rider = site.mob(EntityType.CREEPER);
                int failure = mode;
                boolean[] invoked = {false};
                Consumer<FinalizeSpawnEvent> interrupt = event -> {
                    if (event.getEntity() != rider || failure == 0) return;
                    invoked[0] = true;
                    if (failure == 2) throw new IllegalStateException("bat-rider-finalization-audit");
                    event.setSpawnCancelled(true);
                    event.setCanceled(true);
                    rider.discard();
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FinalizeSpawnEvent.class, interrupt);
                try {
                    try {
                        var result = roll(rider, 0, () -> R196MobSpawning.finalizeNaturalMonster(site.base.level, rider, null, failure == 0 ? 0 : 1));
                        helper.assertTrue(failure != 2 && result.monsters() == 0, "A rejected natural tree contributes no monster");
                    } catch (IllegalStateException exception) {
                        helper.assertTrue(failure == 2 && invoked[0], "The deliberate parent finalization failure reaches transactional cleanup");
                    }
                } finally {
                    NeoForge.EVENT_BUS.unregister(interrupt);
                }
                helper.assertValueEqual(site.bats.size(), 1, "The rollback fixture really constructed its bat before rejecting the rider");
                helper.assertTrue(rider.isRemoved() && site.bats.getFirst().isRemoved() && !site.bats.getFirst().isAddedToLevel(),
                        "Allowance, cancellation and exceptions each reclaim the fresh bat after riding links detach");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void normalSpawnFailuresReclaimOnlyTheirFreshMounts(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            Mob canceled = site.mob(EntityType.CREEPER);
            Consumer<FinalizeSpawnEvent> cancelFinalize = event -> {
                if (event.getEntity() == canceled) {
                    event.setSpawnCancelled(true);
                    event.setCanceled(true);
                    canceled.discard();
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOW, FinalizeSpawnEvent.class, cancelFinalize);
            try { roll(canceled, 0, () -> finalizeEgg(site, canceled)); }
            finally { NeoForge.EVENT_BUS.unregister(cancelFinalize); }
            helper.assertValueEqual(site.bats.size(), 1, "The low-priority cancellation follows the real bat creation");
            helper.assertTrue(site.bats.getFirst().isRemoved(), "Canceled normal finalization reclaims its pre-added mount");

            Mob rejected = site.mob(EntityType.CREEPER);
            roll(rejected, 0, () -> finalizeEgg(site, rejected));
            Mob bat = (Mob)rejected.getVehicle();
            helper.assertTrue(bat != null && bat.isAddedToLevel(), "The normal caller has a real pre-added mount before its rider joins");
            Consumer<EntityJoinLevelEvent> cancelJoin = event -> { if (event.getEntity() == rejected) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOW, EntityJoinLevelEvent.class, cancelJoin);
            try { helper.assertFalse(site.base.level.addFreshEntity(rejected), "The real rider join can be rejected"); }
            finally { NeoForge.EVENT_BUS.unregister(cancelJoin); }
            helper.assertTrue(bat.isRemoved() && !rejected.isPassenger(), "The rejected rider join releases its mount and riding link");

            Mob late = site.mob(EntityType.CREEPER);
            roll(late, 0, () -> finalizeEgg(site, late));
            Mob lateBat = (Mob)late.getVehicle();
            Consumer<EntityJoinLevelEvent> cancelLate = event -> {
                if (event.getEntity() == late) {
                    event.setCanceled(true);
                    late.discard();
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, cancelLate);
            try { helper.assertFalse(site.base.level.addFreshEntity(late), "A listener registered later at LOWEST can still reject the rider"); }
            finally { NeoForge.EVENT_BUS.unregister(cancelLate); }
            BatRiders.settleMount(new EntityTickEvent.Pre(lateBat));
            helper.assertTrue(lateBat.isRemoved(), "The fresh mount's first tick reclaims a detached rider rejected after the join cleanup listener");

            Mob abandoned = site.mob(EntityType.CREEPER);
            roll(abandoned, 0, () -> finalizeEgg(site, abandoned));
            Mob abandonedBat = (Mob)abandoned.getVehicle();
            var canceledTick = new EntityTickEvent.Pre(abandonedBat);
            canceledTick.setCanceled(true);
            NeoForge.EVENT_BUS.post(canceledTick);
            helper.assertTrue(abandonedBat.isRemoved() && !abandoned.isPassenger(), "A caller that abandons its finalized rider leaves no bat after the mount's first tick");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void canceledNaturalVehicleOrRiderJoinRollsBackTheEntireMountedTree(GameTestHelper helper) {
        for (boolean vehicle : List.of(false, true)) {
            try (Site site = new Site(helper)) {
                site.base.player(40);
                Mob rider = site.mob(EntityType.CREEPER);
                Consumer<EntityJoinLevelEvent> reject = event -> {
                    boolean rejectRider = event.getEntity() == rider && !vehicle;
                    boolean rejectBat = event.getEntity() instanceof Bat && vehicle
                            && event.getEntity().getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                            && rider.getUUID().equals(event.getEntity().getPersistentData().getUUID(SpawnPopulation.SUMMONER));
                    if (rejectRider || rejectBat) event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOW, EntityJoinLevelEvent.class, reject);
                try {
                    var result = roll(rider, 0, () -> R196MobSpawning.finalizeNaturalMonster(site.base.level, rider, null, 1));
                    helper.assertValueEqual(result.monsters(), 0, "Rejecting either natural tree member aborts the commit");
                } finally {
                    NeoForge.EVENT_BUS.unregister(reject);
                }
                helper.assertValueEqual(site.bats.size(), 1, "The admission test creates a real owned bat");
                helper.assertTrue(rider.isRemoved() && site.bats.getFirst().isRemoved(), "The failed natural join reclaims both members");
                helper.assertFalse(rider.isAddedToLevel() || site.bats.getFirst().isAddedToLevel(), "Neither aborted tree member remains registered");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void workerMountRollSurvivesNbtAndCreatesOnlyOnceWhenItsRiderJoinsTheMainThread(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            Mob original = site.mob(EntityType.SKELETON);
            ((OrdinarySkeletonState)original).mite$setSourceSkeletonType(2);
            queueOffThread(site, original);
            helper.assertTrue(original.getPersistentData().getString(BatRiders.PENDING_MOUNT).equals("mite:vampire_bat"),
                    "The worker records the successful roll's incoming skeleton type");
            helper.assertTrue(site.bats.isEmpty() && !original.isPassenger(), "The worker records its roll before creating or admitting any bat");
            CompoundTag saved = new CompoundTag();
            original.saveWithoutId(saved);
            Mob loaded = site.mob(EntityType.SKELETON);
            loaded.load(saved);
            ((OrdinarySkeletonState)loaded).mite$setSourceSkeletonType(0);
            helper.assertTrue(site.base.level.addFreshEntity(loaded), "The saved structure rider reaches the normal main-thread join");
            helper.assertTrue(loaded.getVehicle() instanceof Bat bat && bat.getType() == MiteMobs.type(MobKind.VAMPIRE_BAT) && bat.isAddedToLevel(),
                    "Main-thread admission uses the saved incoming type without another probability or metadata roll");
            helper.assertFalse(loaded.getPersistentData().contains(BatRiders.PENDING_MOUNT), "Successful main-thread consumption clears the pending marker");
            Mob mount = (Mob)loaded.getVehicle();
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(loaded, site.base.level, false));
            helper.assertTrue(site.bats.size() == 1 && loaded.getVehicle() == mount, "A repeated join consumes no second roll and creates no second mount");

            Mob canceled = site.mob(EntityType.SKELETON);
            queueOffThread(site, canceled);
            Consumer<EntityJoinLevelEvent> reject = event -> { if (event.getEntity() == canceled) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityJoinLevelEvent.class, reject);
            try { helper.assertFalse(site.base.level.addFreshEntity(canceled), "An earlier join listener can reject the deferred structure rider"); }
            finally { NeoForge.EVENT_BUS.unregister(reject); }
            helper.assertTrue(site.bats.size() == 1 && !canceled.isPassenger(), "Rejected deferred riders create no extra bat");
            helper.assertFalse(canceled.getPersistentData().contains(BatRiders.PENDING_MOUNT), "Rejected main-thread consumption also clears its pending marker");
        }
        helper.succeed();
    }

    private static void queueOffThread(Site site, Mob rider) {
        ((MobSpawnTypeAccessor)(Object)rider).mite$setSpawnType(MobSpawnType.STRUCTURE);
        rider.getRandom().setSeed(seedForOutcome(0));
        var event = new FinalizeSpawnEvent(rider, site.base.level, rider.getX(), rider.getY(), rider.getZ(),
                site.base.level.getCurrentDifficultyAt(rider.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        CompletableFuture.runAsync(() -> {
            BatRiders.finalizeRider(event);
            BatRiders.finishFinalization(event);
        }).join();
    }

    private static Void finalizeEgg(Site site, Mob rider) {
        EventHooks.finalizeMobSpawn(rider, site.base.level, site.base.level.getCurrentDifficultyAt(rider.blockPosition()), MobSpawnType.SPAWN_EGG, null);
        return null;
    }

    private static <T> T roll(Mob rider, int outcome, Supplier<T> action) {
        long selected = seedForOutcome(outcome);
        Consumer<FinalizeSpawnEvent> seedRoll = event -> { if (event.getEntity() == rider) rider.getRandom().setSeed(selected); };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, FinalizeSpawnEvent.class, seedRoll);
        try { return action.get(); }
        finally { NeoForge.EVENT_BUS.unregister(seedRoll); }
    }

    private static long seedForOutcome(int outcome) {
        long seed = 0L;
        while (RandomSource.create(seed).nextInt(5) != outcome) seed++;
        return seed;
    }

    private static final class Site implements AutoCloseable {
        final SpawnBalanceGameTests.Site base;
        final List<Mob> bats = new ArrayList<>();
        final Consumer<FinalizeSpawnEvent> capture;

        Site(GameTestHelper helper) {
            base = new SpawnBalanceGameTests.Site(helper);
            base.date(200, 200, true);
            base.level.getServer().overworld().setDayTime(199L * 24000L + 18000L);
            capture = event -> {
                Mob mob = event.getEntity();
                if (mob instanceof Bat && mob.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                        && base.mobs.stream().anyMatch(owner -> owner.getUUID().equals(mob.getPersistentData().getUUID(SpawnPopulation.SUMMONER)))) bats.add(mob);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, FinalizeSpawnEvent.class, capture);
        }

        Mob mob(EntityType<?> type) { return base.mob(type); }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(capture);
            bats.forEach(Mob::discard);
            base.close();
        }
    }
}
