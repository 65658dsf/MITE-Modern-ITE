package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real zombie lord support admission, saved quotas and retained legacy rewards. @author NingMeng123 */
@GameTestHolder("modern_ite_zombie_lord")
@PrefixGameTestTemplate(false)
public final class ZombieLordGameTests {
    private static final ExtremeKind[] LORDS = {ExtremeKind.ZOMBIE_LORD, ExtremeKind.ZOMBIE_ELITE_LORD};
    private static final Set<String> LEGACY_TROOPS = Set.of("modern_ite:miner_zombie", "mite:ghoul", "mite:wight", "mite:revenant");

    private ZombieLordGameTests() {}

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void bothLordsSummonRealLegacyTroopsEveryFiftyOneCombatTicks(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            var player = fixture.site.player(8);
            for (var kind : LORDS) {
                Mob lord = fixture.lord(kind, MobSpawnType.SPAWN_EGG); lord.setTarget(player);
                MobMemory memory = memory(lord); memory.cooldown = 10000;
                ticks(lord, 50);
                h.assertTrue(fixture.support(lord).isEmpty() && memory.summons == 0,
                        "The first summon waits fifty-one combat ticks: " + kind);
                ticks(lord, 1);
                h.assertTrue(fixture.support(lord).size() == 1 && memory.summons == 1,
                        "The fifty-first combat tick really adds one troop to the server world: " + kind);
                assertTroop(h, fixture.support(lord).getFirst(), lord);
                h.assertTrue(memory.cooldown == 9949, "Support never resets or reuses another ability's cooldown: " + kind);
                ticks(lord, 50);
                h.assertTrue(fixture.support(lord).size() == 1, "A later summon cannot bypass the full interval: " + kind);
                ticks(lord, 1);
                h.assertTrue(fixture.support(lord).size() == 2 && memory.summons == 2,
                        "The next full interval admits another real troop: " + kind);
                fixture.removeSupport(lord);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void allFourLegacyTroopsHavePermanentStrengthAndInheritTheTarget(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            var player = fixture.site.player(8);
            for (var kind : LORDS) {
                Mob lord = fixture.lord(kind, MobSpawnType.SPAWN_EGG); lord.setTarget(player);
                Set<String> seen = new HashSet<>();
                for (int wave = 0; wave < 24 && seen.size() < 4; wave++) {
                    lord.getRandom().setSeed(137L + wave * 1000003L);
                    readyTick(lord);
                    var support = fixture.support(lord);
                    h.assertTrue(support.size() == 1, "Each successful wave admits exactly one troop: " + kind + "/" + wave);
                    Mob troop = support.getFirst(); seen.add(id(troop)); assertTroop(h, troop, lord);
                    fixture.removeSupport(lord);
                }
                h.assertTrue(seen.equals(LEGACY_TROOPS), "The actual summon selector includes all four legacy types: " + kind + " " + seen);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void lostCreativeAndSpectatorTargetsPauseCombatTimeAndSavedCooldownResumes(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            var player = fixture.site.player(8);
            for (var kind : LORDS) {
                Mob lord = fixture.lord(kind, MobSpawnType.SPAWN_EGG); lord.setTarget(player);
                ticks(lord, 20); int remaining = memory(lord).supportCooldown;
                lord.setTarget(null); ticks(lord, 70);
                h.assertTrue(memory(lord).supportCooldown == remaining, "Losing a target pauses the combat interval: " + kind);
                lord.setTarget(player);
                for (var mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                    player.setGameMode(mode); ticks(lord, 70);
                    h.assertTrue(memory(lord).supportCooldown == remaining && fixture.support(lord).isEmpty(),
                            "Observers cannot advance the interval or trigger support: " + kind + "/" + mode);
                }
                player.setGameMode(GameType.SURVIVAL);
                CompoundTag saved = new CompoundTag(); lord.saveWithoutId(saved); lord.discard();
                Mob loaded = fixture.fresh(kind); loaded.load(saved); loaded.setTarget(player);
                h.assertTrue(memory(loaded).supportCooldown == remaining, "NBT retains the partially spent interval: " + kind);
                ticks(loaded, 30);
                h.assertTrue(fixture.support(loaded).isEmpty(), "Reloading cannot shorten the remaining thirty-one ticks: " + kind);
                player.setGameMode(GameType.ADVENTURE); ticks(loaded, 1);
                h.assertTrue(fixture.support(loaded).size() == 1 && memory(loaded).summons == 1,
                        "Adventure combat resumes the saved interval and admits one troop: " + kind);
                fixture.removeSupport(loaded); player.setGameMode(GameType.SURVIVAL);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void twentyFourSuccessfulSummonsRemainSpentAfterSaveAndReload(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            var player = fixture.site.player(8);
            for (var kind : LORDS) {
                Mob lord = fixture.lord(kind, MobSpawnType.SPAWN_EGG); lord.setTarget(player);
                for (int wave = 0; wave < 24; wave++) {
                    readyTick(lord);
                    h.assertTrue(fixture.support(lord).size() == 1 && memory(lord).summons == wave + 1,
                            "Only a successfully admitted troop spends the cumulative quota: " + kind + "/" + wave);
                    fixture.removeSupport(lord);
                }
                ticks(lord, 102);
                h.assertTrue(fixture.support(lord).isEmpty() && memory(lord).summons == 24,
                        "Dead troops cannot replenish the lifetime twenty-four-summon quota: " + kind);
                CompoundTag saved = new CompoundTag(); lord.saveWithoutId(saved); lord.discard();
                Mob loaded = fixture.fresh(kind); loaded.load(saved); loaded.setTarget(player); ticks(loaded, 102);
                h.assertTrue(memory(loaded).summons == 24 && fixture.support(loaded).isEmpty(),
                        "A saved lord retains its spent quota instead of recreating the army: " + kind);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void canceledTroopsReleaseReservationsWithoutSpendingTheQuota(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            var player = fixture.site.player(8);
            for (var kind : LORDS) {
                Mob lord = fixture.lord(kind, MobSpawnType.SPAWN_EGG); lord.setTarget(player);
                Consumer<EntityJoinLevelEvent> cancel = event -> {
                    if (event.getEntity() instanceof Mob mob && ownedBy(mob, lord)) event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, cancel);
                try { readyTick(lord); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
                h.assertTrue(memory(lord).summons == 0 && fixture.support(lord).isEmpty() && SpawnPopulation.supportCount(lord) == 0,
                        "A canceled actual join releases its reservation and never spends a successful summon: " + kind);
                ticks(lord, 50);
                h.assertTrue(fixture.support(lord).isEmpty(), "A failed summon still waits the normal retry interval: " + kind);
                ticks(lord, 1);
                h.assertTrue(memory(lord).summons == 1 && fixture.support(lord).size() == 1,
                        "A later successful attempt can use the released slot: " + kind);
                fixture.removeSupport(lord);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void crowdedAreasRefuseSupportWithoutSpendingTheQuota(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true); fixture.site.legacyDates.set("local_hostile_limit", 1);
            var player = fixture.site.player(8);
            for (var kind : LORDS) {
                Mob lord = fixture.lord(kind, MobSpawnType.SPAWN_EGG); lord.setTarget(player);
                Mob blocker = fixture.site.mob(EntityType.ZOMBIE);
                h.assertTrue(SpawnPopulation.reserveSupport(blocker, null, false) && fixture.site.level.addFreshEntity(blocker),
                        "One real hostile fills the configured local allowance");
                readyTick(lord);
                h.assertTrue(memory(lord).summons == 0 && fixture.support(lord).isEmpty(),
                        "A full local area prevents a summon without consuming its quota: " + kind);
                blocker.discard(); SpawnPopulation.release(blocker);
                ticks(lord, 51);
                h.assertTrue(memory(lord).summons == 1 && fixture.support(lord).size() == 1,
                        "Freeing the local slot allows the next scheduled attempt: " + kind);
                fixture.removeSupport(lord);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void firstDayManualLordsAdmitOnlyTheirFourLegacyTroopsAndNaturalLordsStayProtected(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(1, 1, false);
            var player = fixture.site.player(8);
            for (var kind : LORDS) {
                Mob natural = fixture.fresh(kind); natural.setTarget(player); readyTick(natural);
                h.assertTrue(memory(natural).summons == 0 && fixture.support(natural).isEmpty(),
                        "A non-explicit lord retains ordinary first-day hostile protection: " + kind);
                for (var reason : new MobSpawnType[]{MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND}) {
                    Mob lord = fixture.lord(kind, reason); lord.setTarget(player);
                    Set<String> seen = new HashSet<>();
                    for (int wave = 0; wave < 24 && seen.size() < 4; wave++) {
                        lord.getRandom().setSeed(137L + wave * 1000003L); readyTick(lord);
                        var support = fixture.support(lord);
                        h.assertTrue(support.size() == 1, "Manual first-day combat completes real troop admission: " + kind + "/" + reason);
                        Mob troop = support.getFirst(); seen.add(id(troop)); assertTroop(h, troop, lord);
                        h.assertTrue(SpawnPopulation.explicit(troop), "The troop retains the manually requested encounter origin");
                        fixture.removeSupport(lord);
                    }
                    h.assertTrue(seen.equals(LEGACY_TROOPS), "All four legitimate legacy troops work during manual first-day combat: " + kind + "/" + reason);
                    h.assertTrue(MobAbilities.summonType(lord, EntityType.ZOMBIE, 1) == null,
                            "Manual lord support admission does not leak into unrelated monster types");
                    h.assertFalse(SpawnPopulation.automaticAllowed(fixture.site.level, EntityType.ZOMBIE),
                            "Ordinary first-day automatic hostile spawning remains protected");
                }
            }
            h.assertTrue(ExtremeWorldState.graceActive(fixture.site.level), "Manual lord encounters never end the world's initial grace period");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_rewards")
    public static void ordinaryAndEliteLordsKeepTheirSeparateLegacyRewardsAndPlayerKillCondition(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            var player = fixture.site.player(8);
            int[] days = {1, 31, 32, 63, 64, 96}; int[] amounts = {1, 1, 2, 2, 3, 3};
            for (var kind : LORDS) {
                Mob lord = fixture.lord(kind, MobSpawnType.SPAWN_EGG);
                for (int index = 0; index < days.length; index++) {
                    fixture.site.date(days[index], days[index], true);
                    List<ItemEntity> drops = new ArrayList<>();
                    ExtremeMobEvents.drops(new LivingDropsEvent(lord, fixture.site.level.damageSources().playerAttack(player), drops, true));
                    var expected = kind == ExtremeKind.ZOMBIE_LORD ? Items.DIAMOND : Items.EMERALD;
                    h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(expected) && drops.getFirst().getItem().getCount() == amounts[index],
                            "The lord retains its own capped legacy reward with no extra addon drops: " + kind + "/day " + days[index]);
                    drops.clear();
                    ExtremeMobEvents.drops(new LivingDropsEvent(lord, fixture.site.level.damageSources().generic(), drops, false));
                    h.assertTrue(drops.isEmpty(), "A death without recent player damage does not grant the legacy gem reward: " + kind);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void onlyOldManualEliteAttemptsDuringUnendedGraceAreRecoveredOnce(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(1, 1, false);
            var player = fixture.site.player(8);
            for (var reason : new MobSpawnType[]{MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND}) {
                Mob legacy = fixture.lord(ExtremeKind.ZOMBIE_ELITE_LORD, reason);
                memory(legacy).summons = 24; memory(legacy).supportCooldown = 7;
                legacy.getPersistentData().remove("modern_ite:zombie_lord_support_revision");
                CompoundTag saved = new CompoundTag(); legacy.saveWithoutId(saved); legacy.discard();
                Mob loaded = fixture.fresh(ExtremeKind.ZOMBIE_ELITE_LORD); loaded.load(saved);
                h.assertTrue(memory(loaded).initialized && memory(loaded).summons == 24 && SpawnPopulation.explicit(loaded),
                        "The fixture reproduces an initialized old manual elite with exhausted failed attempts: " + reason);
                MobAbilities.initialize(loaded, (ExtremeMob) loaded);
                h.assertTrue(memory(loaded).summons == 0 && memory(loaded).supportCooldown == 0
                                && loaded.getPersistentData().getInt("modern_ite:zombie_lord_support_revision") == 1,
                        "Only the old manual elite's impossible first-day attempts are recovered and marked migrated: " + reason);
                loaded.setTarget(player); ticks(loaded, 51);
                h.assertTrue(memory(loaded).summons == 1 && fixture.support(loaded).size() == 1,
                        "The recovered saved elite can really summon again during the same initial grace period: " + reason);
                fixture.removeSupport(loaded);
                memory(loaded).summons = 0; memory(loaded).supportCooldown = 19;
                MobAbilities.initialize(loaded, (ExtremeMob) loaded);
                h.assertTrue(memory(loaded).summons == 0 && memory(loaded).supportCooldown == 19,
                        "A migrated lord with zero successful summons does not reset its running interval");
                memory(loaded).summons = 24; memory(loaded).supportCooldown = 17;
                CompoundTag migrated = new CompoundTag(); loaded.saveWithoutId(migrated); loaded.discard();
                Mob reloaded = fixture.fresh(ExtremeKind.ZOMBIE_ELITE_LORD); reloaded.load(migrated);
                MobAbilities.initialize(reloaded, (ExtremeMob) reloaded); reloaded.setTarget(player); ticks(reloaded, 51);
                h.assertTrue(memory(reloaded).summons == 24 && memory(reloaded).supportCooldown == 17 && fixture.support(reloaded).isEmpty(),
                        "The migration revision survives another save and never refunds twenty-four successful summons");
            }
            for (var kind : LORDS) {
                Mob preserved = kind == ExtremeKind.ZOMBIE_LORD ? fixture.lord(kind, MobSpawnType.COMMAND) : fixture.fresh(kind);
                memory(preserved).summons = 24; memory(preserved).supportCooldown = 23;
                preserved.getPersistentData().remove("modern_ite:zombie_lord_support_revision");
                MobAbilities.initialize(preserved, (ExtremeMob) preserved);
                h.assertTrue(memory(preserved).summons == 24 && memory(preserved).supportCooldown == 23,
                        "Old ordinary lords and old non-explicit elites retain their recorded quota: " + kind);
            }
            fixture.site.date(128, 128, true);
            Mob late = fixture.lord(ExtremeKind.ZOMBIE_ELITE_LORD, MobSpawnType.COMMAND);
            memory(late).summons = 24; memory(late).supportCooldown = 29;
            late.getPersistentData().remove("modern_ite:zombie_lord_support_revision");
            MobAbilities.initialize(late, (ExtremeMob) late);
            h.assertTrue(memory(late).summons == 24 && memory(late).supportCooldown == 29,
                    "Once initial grace has ended, an old manual elite's possibly successful quota is preserved");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_support")
    public static void firstDaySavedManualArmiesRetainTheirOriginAndEightLivingTroopLimit(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(1, 1, false);
            var player = fixture.site.player(8);
            for (var kind : LORDS) for (var reason : new MobSpawnType[]{MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND}) {
                Mob lord = fixture.lord(kind, reason); lord.setTarget(player);
                for (int wave = 0; wave < 8; wave++) readyTick(lord);
                var troops = fixture.support(lord);
                h.assertTrue(troops.size() == 8 && memory(lord).summons == 8,
                        "Eight living legacy troops occupy their owner's actual support allowance: " + kind + "/" + reason);
                var troopTypes = new ArrayList<EntityType<?>>(); var troopSaves = new ArrayList<CompoundTag>();
                for (var troop : troops) {
                    troopTypes.add(troop.getType()); CompoundTag saved = new CompoundTag(); troop.saveWithoutId(saved); troopSaves.add(saved);
                    troop.discard(); SpawnPopulation.release(troop);
                }
                CompoundTag savedLord = new CompoundTag(); lord.saveWithoutId(savedLord); lord.discard();
                SpawnPopulation.clearReservations(fixture.site.level);
                Mob loaded = fixture.fresh(kind); loaded.load(savedLord); MobAbilities.initialize(loaded, (ExtremeMob) loaded); loaded.setTarget(player);
                h.assertTrue(SpawnPopulation.explicit(loaded) && memory(loaded).summons == 8,
                        "Saving the manual root retains its original admission and successful quota: " + kind + "/" + reason);
                for (int index = 0; index < troopSaves.size(); index++) {
                    Mob troop = fixture.site.mob(troopTypes.get(index)); troop.load(troopSaves.get(index));
                    h.assertTrue(SpawnPopulation.explicit(troop) && ownedBy(troop, loaded),
                            "Saved troops retain the restricted manual support marker and the same saved owner UUID");
                    h.assertTrue(fixture.site.level.addFreshEntity(troop), "A saved first-day troop actually rejoins the server world");
                    var strength = troop.getEffect(MobEffects.DAMAGE_BOOST);
                    h.assertTrue(strength != null && strength.getAmplifier() == 0 && strength.getDuration() >= Integer.MAX_VALUE - 2,
                            "The saved troop retains permanent Strength I without requiring another summon");
                }
                SpawnPopulation.clearReservations(fixture.site.level);
                readyTick(loaded);
                h.assertTrue(fixture.support(loaded).size() == 8 && SpawnPopulation.supportCount(loaded) == 8 && memory(loaded).summons == 8,
                        "A rebuilt ledger refuses a ninth living troop without consuming a successful summon");
                Mob removed = fixture.support(loaded).getFirst(); removed.discard(); SpawnPopulation.release(removed);
                readyTick(loaded);
                h.assertTrue(fixture.support(loaded).size() == 8 && memory(loaded).summons == 9,
                        "Removing one living troop permits exactly one replacement after the saved root resumes combat");
                fixture.removeSupport(loaded);
            }
            h.assertTrue(ExtremeWorldState.graceActive(fixture.site.level), "Saving and reloading a manual army never ends world grace");
        }
        h.succeed();
    }

    private static void assertTroop(GameTestHelper h, Mob troop, Mob lord) {
        h.assertTrue(LEGACY_TROOPS.contains(id(troop)), "The actual troop belongs to the four legacy summon types: " + id(troop));
        h.assertTrue(troop.isAddedToLevel() && troop.getSpawnType() == MobSpawnType.MOB_SUMMONED && ownedBy(troop, lord),
                "A troop finishes ordinary spawn initialization, joins the server and retains its summoner UUID");
        h.assertTrue(troop.getTarget() == lord.getTarget(), "A troop inherits the lord's actual player target");
        var strength = troop.getEffect(MobEffects.DAMAGE_BOOST);
        h.assertTrue(strength != null && strength.getAmplifier() == 0 && strength.getDuration() >= Integer.MAX_VALUE - 2,
                "Every summon receives the legacy permanent Strength I effect");
    }

    private static MobMemory memory(Mob lord) { return ((ExtremeMob) lord).extremeMemory(); }
    private static void ticks(Mob lord, int count) { for (int tick = 0; tick < count; tick++) MobAbilities.tick(lord, (ExtremeMob) lord); }
    private static void readyTick(Mob lord) { memory(lord).supportCooldown = 1; ticks(lord, 1); }
    private static String id(Mob mob) { return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(); }
    private static boolean ownedBy(Mob mob, Mob lord) {
        return mob.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                && mob.getPersistentData().getUUID(SpawnPopulation.SUMMONER).equals(lord.getUUID());
    }

    private static final class Fixture implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final List<Mob> lords = new ArrayList<>();
        Fixture(GameTestHelper h) { site = new SpawnBalanceGameTests.Site(h); }
        Mob fresh(ExtremeKind kind) {
            Mob lord = site.mob(ExtremeMobs.type(kind)); lords.add(lord); MobAbilities.initialize(lord, (ExtremeMob) lord); return lord;
        }
        Mob lord(ExtremeKind kind, MobSpawnType reason) {
            Mob lord = fresh(kind);
            if (reason == MobSpawnType.COMMAND) lord.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true);
            EventHooks.finalizeMobSpawn(lord, site.level, site.level.getCurrentDifficultyAt(lord.blockPosition()), reason, null);
            return lord;
        }
        List<Mob> support(Mob lord) {
            var result = new ArrayList<Mob>();
            for (var entity : site.level.getAllEntities()) if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved() && ownedBy(mob, lord)) result.add(mob);
            return result;
        }
        void removeSupport(Mob lord) { for (var troop : support(lord)) { troop.discard(); SpawnPopulation.release(troop); } }
        @Override public void close() { for (var lord : lords) removeSupport(lord); site.close(); }
    }
}
