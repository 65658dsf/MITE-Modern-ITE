package io.github.ningmeng123.modernite.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native raid and triggered-spawn callers observe actual admission results. @author NingMeng123 */
@GameTestHolder("modern_ite_modern_spawning")
@PrefixGameTestTemplate(false)
public final class ModernSpawnAdmissionGameTests {
    private ModernSpawnAdmissionGameTests() {}

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void rejectedRaidLeaderReleasesItsWaveAndExistingRaiderStillTransfers(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(1, 1, false);
            BlockPos pos = BlockPos.containing(site.origin);
            Raid blocked = new Raid(71, site.level, pos);
            Raider leader = (Raider) site.mob(EntityType.PILLAGER);
            blocked.setLeader(1, leader);
            blocked.joinRaid(1, leader, pos, false);
            assertRejectedRaider(h, blocked, leader);
            h.assertTrue(leader.isSpawnCancelled(), "Opening protection still rejects the actual raid finalization");

            site.date(4, 4, true);
            Raid active = new Raid(72, site.level, pos);
            Raider admitted = (Raider) site.mob(EntityType.PILLAGER);
            active.joinRaid(1, admitted, pos, false);
            h.assertTrue(admitted.isAddedToLevel() && active.getTotalRaidersAlive() == 1,
                    "A real raid member joins after opening protection ends");
            active.removeFromRaid(admitted, true);

            site.date(1, 1, false);
            Raid existing = new Raid(73, site.level, pos);
            existing.joinRaid(2, admitted, null, true);
            h.assertTrue(admitted.isAddedToLevel() && !admitted.isRemoved() && existing.getTotalRaidersAlive() == 1,
                    "An already admitted raider remains available to the existing-entity join branch");
            existing.removeFromRaid(admitted, true);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void fullLocalPopulationCannotLeaveAnInvisibleRaidMember(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(4, 4, true);
            var player = site.player(0);
            var volume = player.getBoundingBox().inflate(32, 8, 32);
            int baseline = SpawnPopulation.localCount(site.level, volume);
            site.legacyDates.set("local_hostile_limit", baseline + 1);
            Mob occupant = explicitOccupant(h, site);
            Raid raid = new Raid(74, site.level, BlockPos.containing(site.origin));
            Raider rejected = (Raider) site.mob(EntityType.PILLAGER);
            raid.setLeader(1, rejected);
            raid.joinRaid(1, rejected, raid.getCenter(), false);
            assertRejectedRaider(h, raid, rejected);
            h.assertTrue(occupant.isAddedToLevel() && !occupant.isRemoved(), "The existing population remains admitted");

            occupant.discard();
            SpawnPopulation.clearReservations(site.level);
            int releasedCount = SpawnPopulation.localCount(site.level, volume);
            h.assertTrue(releasedCount == baseline,
                    "Discarding the fixture occupant releases its one slot: baseline=" + baseline + ", count=" + releasedCount);
            Raider retry = (Raider) site.mob(EntityType.PILLAGER);
            raid.joinRaid(1, retry, raid.getCenter(), false);
            h.assertTrue(retry.isAddedToLevel() && raid.getTotalRaidersAlive() == 1,
                    "A later real member occupies the released raid slot: added=" + retry.isAddedToLevel()
                            + ", removed=" + retry.isRemoved() + ", canceled=" + retry.isSpawnCancelled()
                            + ", raidCount=" + raid.getTotalRaidersAlive() + ", localCount=" + SpawnPopulation.localCount(site.level, volume)
                            + ", limit=" + io.github.ningmeng123.modernite.ExtremeRules.localHostileLimit(site.level));
            raid.removeFromRaid(retry, true);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void lateJoinCancellationAlsoRemovesRaidMembershipAndLeader(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(4, 4, true);
            Raid raid = new Raid(75, site.level, BlockPos.containing(site.origin));
            Raider rejected = (Raider) site.mob(EntityType.PILLAGER);
            raid.setLeader(1, rejected);
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getEntity() == rejected) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, cancel);
            try { raid.joinRaid(1, rejected, raid.getCenter(), false); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            assertRejectedRaider(h, raid, rejected);
            h.assertFalse(rejected.isSpawnCancelled(), "The failure happened at actual world join after successful finalization");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void canceledFinalizationReversesOnlyItsOriginalWaveHealthContribution(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(4, 4, true);
            Raid raid = new Raid(76, site.level, BlockPos.containing(site.origin));
            Raider existing = (Raider) site.mob(EntityType.PILLAGER);
            raid.joinRaid(1, existing, raid.getCenter(), false);
            h.assertTrue(existing.isAddedToLevel(), "An actual existing wave member establishes the raid health baseline");
            float baseline = raid.getTotalHealth();
            Raider rejected = (Raider) site.mob(EntityType.PILLAGER);
            Raider nested = (Raider) site.mob(EntityType.PILLAGER);
            float[] nestedContribution = {0};
            Consumer<FinalizeSpawnEvent> cancel = event -> {
                if (event.getEntity() != rejected) return;
                rejected.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
                rejected.setHealth(400);
                float beforeNested = raid.getTotalHealth();
                raid.joinRaid(1, nested, raid.getCenter(), false);
                nestedContribution[0] = raid.getTotalHealth() - beforeNested;
                event.setSpawnCancelled(true);
                event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FinalizeSpawnEvent.class, cancel);
            try { raid.joinRaid(1, rejected, raid.getCenter(), false); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(rejected.isRemoved() && rejected.getCurrentRaid() == null,
                    "The candidate with changed finalization health is cleaned after rejection");
            h.assertTrue(existing.isAddedToLevel() && nested.isAddedToLevel() && raid.getTotalRaidersAlive() == 2,
                    "Both the existing member and reentrantly admitted member retain actual wave membership");
            h.assertTrue(raid.getTotalHealth() == baseline + nestedContribution[0],
                    "Rejected finalization subtracts its recorded contribution while preserving both other members' total health");
            raid.removeFromRaid(existing, true);
            raid.removeFromRaid(nested, true);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void nativeWardenSpawnReportsProtectionFailureAndLaterSuccessfulAdmission(GameTestHelper h) {
        try (var fixture = new WardenSite(h)) {
            fixture.site.date(1, 1, false);
            h.assertTrue(fixture.spawn().isEmpty(), "Native SpawnUtil returns failure while opening protection rejects the warden");
            h.assertTrue(fixture.candidates.size() == 1 && fixture.candidates.getFirst().isRemoved(),
                    "The native routine found a valid position and cleaned its rejected candidate");

            fixture.site.date(4, 4, true);
            var admitted = fixture.spawn();
            h.assertTrue(admitted.isPresent() && admitted.get().isAddedToLevel() && !admitted.get().isRemoved(),
                    "The same native trigger succeeds after protection ends");
            h.assertTrue(admitted.get().getSpawnType() == MobSpawnType.TRIGGERED,
                    "The real warden keeps the native triggered spawn reason");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void nativeWardenSpawnReportsCrowdingAndLateJoinCancellationAsFailure(GameTestHelper h) {
        try (var fixture = new WardenSite(h)) {
            var site = fixture.site;
            site.date(4, 4, true);
            var player = site.player(4);
            var volume = player.getBoundingBox().inflate(32, 8, 32);
            int baseline = SpawnPopulation.localCount(site.level, volume);
            site.legacyDates.set("local_hostile_limit", baseline + 1);
            Mob occupant = explicitOccupant(h, site);
            h.assertTrue(fixture.spawn().isEmpty(), "The actual triggered spawn reports a full local population");
            h.assertTrue(fixture.candidates.size() == 1 && fixture.candidates.getFirst().isRemoved(),
                    "The crowding-rejected native candidate is discarded");
            occupant.discard();
            SpawnPopulation.clearReservations(site.level);

            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getEntity() instanceof Warden && event.getEntity().blockPosition().equals(fixture.pos)) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, cancel);
            try { h.assertTrue(fixture.spawn().isEmpty(), "A late world-join cancellation cannot be reported as a successful warden spawn"); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(fixture.candidates.size() == 2 && fixture.candidates.getLast().isRemoved(),
                    "The late-canceled candidate is also cleaned");
            h.assertTrue(fixture.spawn().filter(Mob::isAddedToLevel).isPresent(),
                    "Canceled candidates release the local reservation for a later actual spawn");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void nativeSpawnUtilityStillAdmitsAnOrdinaryIronGolemDuringProtection(GameTestHelper h) {
        try (var fixture = new WardenSite(h)) {
            fixture.site.date(1, 1, false);
            var admitted = SpawnUtil.trySpawnMob(EntityType.IRON_GOLEM, MobSpawnType.MOB_SUMMONED, fixture.site.level,
                    fixture.pos, 1, 0, 0, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER);
            admitted.ifPresent(fixture.site.mobs::add);
            h.assertTrue(admitted.filter(IronGolem::isAddedToLevel).isPresent() && !admitted.get().isRemoved(),
                    "Native non-hostile callers retain a successful committed spawn during opening protection");
        }
        h.succeed();
    }

    private static Mob explicitOccupant(GameTestHelper h, SpawnBalanceGameTests.Site site) {
        Mob occupant = site.mob(EntityType.ZOMBIE);
        occupant.moveTo(site.origin.add(3, 0, 0));
        occupant.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true);
        h.assertTrue(site.level.addFreshEntity(occupant), "The real existing hostile occupies the local population slot");
        return occupant;
    }

    private static void assertRejectedRaider(GameTestHelper h, Raid raid, Raider rejected) {
        h.assertTrue(rejected.isRemoved() && !rejected.isAddedToLevel(), "A failed fresh raider never remains alive outside the world");
        h.assertTrue(raid.getTotalRaidersAlive() == 0 && raid.getHealthOfLivingRaiders() == 0 && raid.getTotalHealth() == 0,
                "Failed admission releases wave membership and living health immediately");
        h.assertTrue(raid.getLeader(rejected.getWave()) == null && rejected.getCurrentRaid() == null,
                "The rejected leader leaves both leader and raider-side references");
    }

    private static final class WardenSite implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final BlockPos pos;
        final BlockState floor;
        final List<Warden> candidates = new ArrayList<>();
        final Consumer<FinalizeSpawnEvent> capture;

        WardenSite(GameTestHelper h) {
            site = new SpawnBalanceGameTests.Site(h);
            pos = BlockPos.containing(site.origin);
            floor = site.level.getBlockState(pos.below());
            site.level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            capture = event -> {
                if (event.getSpawnType() == MobSpawnType.TRIGGERED && event.getEntity() instanceof Warden warden
                        && warden.blockPosition().equals(pos) && !candidates.contains(warden)) {
                    candidates.add(warden);
                    site.mobs.add(warden);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, FinalizeSpawnEvent.class, capture);
        }

        java.util.Optional<Warden> spawn() {
            return SpawnUtil.trySpawnMob(EntityType.WARDEN, MobSpawnType.TRIGGERED, site.level, pos, 1, 0, 0,
                    SpawnUtil.Strategy.ON_TOP_OF_COLLIDER);
        }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(capture);
            site.level.setBlock(pos.below(), floor, 3);
            site.close();
        }
    }
}
