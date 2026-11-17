package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exact lord scope, vanilla reward pool and native saved death transactions. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class LordTrimDropsGameTests {
    private static final Set<ExtremeKind> ADDON_LORDS = Set.of(
            ExtremeKind.ZOMBIE_LORD, ExtremeKind.ZOMBIE_ELITE_LORD, ExtremeKind.ZOMBIE_PIGMAN_LORD,
            ExtremeKind.SKELETON_ELITE_LORD, ExtremeKind.WITCH_LORD, ExtremeKind.TIME_LORD);

    private LordTrimDropsGameTests() {}

    @GameTest(template = "gear_empty", batch = "lord_trim_drops")
    public static void onlyTheEightLordIdentitiesReceiveOneRandomVanillaTrimTemplate(GameTestHelper h) {
        Set<Item> templates = vanillaTemplates();
        h.assertValueEqual(templates.size(), 18, "The reward pool covers all eighteen vanilla armor trim templates");
        for (var kind : ExtremeKind.values()) {
            Mob mob = ExtremeMobs.type(kind).create(h.getLevel());
            h.assertTrue(LordTrimDrops.isLord(mob) == ADDON_LORDS.contains(kind),
                    "The shared reward distinguishes each lord from other boss and ordinary identities: " + kind);
        }
        for (var kind : new MobKind[]{MobKind.BONE_LORD, MobKind.ANCIENT_BONE_LORD, MobKind.LONGDEAD_GUARDIAN, MobKind.LONGDEAD}) {
            Mob mob = MiteMobs.type(kind).create(h.getLevel());
            boolean lord = kind == MobKind.BONE_LORD || kind == MobKind.ANCIENT_BONE_LORD;
            h.assertTrue(LordTrimDrops.isLord(mob) == lord, "The native lord predicate retains its exact identities: " + kind);
        }
        for (var type : lordTypes()) {
            Mob mob = type.create(h.getLevel());
            for (boolean recentlyHit : new boolean[]{false, true}) {
                seedRoll(mob, true);
                List<ItemEntity> drops = new ArrayList<>();
                LordTrimDrops.drops(new LivingDropsEvent(mob, mob.damageSources().generic(), drops, recentlyHit));
                h.assertTrue(drops.size() == 1 && templates.contains(drops.getFirst().getItem().getItem())
                                && drops.getFirst().getItem().getCount() == 1,
                        "A successful roll gives one vanilla trim after either player or environmental damage: " + type);
            }
        }
        Mob lord = ExtremeMobs.type(ExtremeKind.TIME_LORD).create(h.getLevel());
        Set<Item> seen = new HashSet<>();
        for (long seed = 0; seed < 4096; seed++) {
            lord.getRandom().setSeed(seed);
            List<ItemEntity> drops = new ArrayList<>();
            LordTrimDrops.drops(new LivingDropsEvent(lord, lord.damageSources().generic(), drops, false));
            h.assertTrue(drops.size() <= 1, "A death roll can add at most one template");
            if (!drops.isEmpty()) seen.add(drops.getFirst().getItem().getItem());
        }
        h.assertTrue(seen.equals(templates), "Every vanilla template is reachable in the random selection pool");
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "lord_trim_drops")
    public static void failedRollCanceledDropsAndDisabledMobLootPreserveTheExistingDrops(GameTestHelper h) {
        Mob lord = ExtremeMobs.type(ExtremeKind.ZOMBIE_LORD).create(h.getLevel());
        var original = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.DIAMOND, 2));
        List<ItemEntity> drops = new ArrayList<>(List.of(original));
        seedRoll(lord, false);
        LordTrimDrops.drops(new LivingDropsEvent(lord, lord.damageSources().generic(), drops, false));
        h.assertTrue(drops.size() == 1 && drops.getFirst() == original, "A failed ten-percent roll preserves existing rewards");
        seedRoll(lord, true);
        var canceled = new LivingDropsEvent(lord, lord.damageSources().generic(), drops, true);
        canceled.setCanceled(true);
        LordTrimDrops.drops(canceled);
        h.assertTrue(drops.size() == 1 && drops.getFirst() == original, "A canceled loot transaction adds no reward");
        var rules = h.getLevel().getGameRules();
        boolean previous = rules.getBoolean(GameRules.RULE_DOMOBLOOT);
        rules.getRule(GameRules.RULE_DOMOBLOOT).set(false, h.getLevel().getServer());
        try {
            seedRoll(lord, true);
            LordTrimDrops.drops(new LivingDropsEvent(lord, lord.damageSources().generic(), drops, true));
            h.assertTrue(drops.size() == 1 && drops.getFirst() == original, "Disabled mob loot suppresses the optional reward");
        } finally { rules.getRule(GameRules.RULE_DOMOBLOOT).set(previous, h.getLevel().getServer()); }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "lord_trim_drops")
    public static void naturalAndSummonedLordsKeepTheRewardThroughSaveLoadAndNativeDeath(GameTestHelper h) {
        var level = h.getLevel();
        var overworld = level.getServer().overworld();
        long previousTime = overworld.getDayTime();
        var history = BloodMoonInvasionData.get(level);
        long previousHighestDay = history.highestDay();
        boolean previousDirty = history.isDirty();
        boolean previousGrace = ExtremeWorldState.graceEnded(level);
        var position = h.absolutePos(new BlockPos(2, 152, 2)).getCenter();
        List<Mob> mobs = new ArrayList<>();
        try {
            overworld.setDayTime(299 * 24000L + 1000);
            history.restoreProgress(300);
            ExtremeWorldState.restoreGraceEnded(level, true);
            SpawnPopulation.clearReservations(level);
            List<EntityType<? extends Mob>> types = List.of(MiteMobs.type(MobKind.BONE_LORD),
                    ExtremeMobs.type(ExtremeKind.ZOMBIE_LORD), ExtremeMobs.type(ExtremeKind.TIME_LORD));
            Set<Item> templates = vanillaTemplates();
            for (var type : types) for (var origin : new MobSpawnType[]{MobSpawnType.NATURAL, MobSpawnType.MOB_SUMMONED}) {
                Mob original = type.create(level);
                mobs.add(original);
                original.moveTo(position);
                original.setNoAi(true);
                original.setNoGravity(true);
                EventHooks.finalizeMobSpawn(original, level, level.getCurrentDifficultyAt(original.blockPosition()), origin, null);
                CompoundTag saved = new CompoundTag();
                original.saveWithoutId(saved);
                // Exercise the native stored origins even when a custom finalize hook skips Mob.finalizeSpawn.
                saved.putString("neoforge:spawn_type", origin.name());
                Mob loaded = type.create(level);
                mobs.add(loaded);
                loaded.load(saved);
                h.assertTrue(loaded.getSpawnType() == origin, "The real saved lord retains its natural or summoned origin");
                level.addLegacyChunkEntities(Stream.of(loaded));
                h.assertTrue(level.getEntity(loaded.getUUID()) == loaded, "The native chunk loader restores the saved lord");
                List<ItemStack> rewards = new ArrayList<>();
                Consumer<LivingDropsEvent> seed = event -> { if (event.getEntity() == loaded) seedRoll(loaded, true); };
                Consumer<LivingDropsEvent> observe = event -> {
                    if (event.getEntity() != loaded) return;
                    for (ItemEntity item : event.getDrops())
                        if (templates.contains(item.getItem().getItem())) rewards.add(item.getItem().copy());
                    event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingDropsEvent.class, seed);
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDropsEvent.class, observe);
                try {
                    loaded.setHealth(0);
                    loaded.die(loaded.damageSources().generic());
                    h.assertTrue(rewards.size() == 1 && rewards.getFirst().getCount() == 1,
                            "One saved natural or summoned lord death produces one template through the native loot event: " + type + "/" + origin);
                } finally {
                    NeoForge.EVENT_BUS.unregister(seed);
                    NeoForge.EVENT_BUS.unregister(observe);
                    loaded.discard();
                }
            }
        } finally {
            mobs.forEach(Mob::discard);
            SpawnPopulation.clearReservations(level);
            overworld.setDayTime(previousTime);
            history.restoreProgress(previousHighestDay);
            history.setDirty(previousDirty);
            ExtremeWorldState.restoreGraceEnded(level, previousGrace);
        }
        h.succeed();
    }

    private static List<EntityType<? extends Mob>> lordTypes() {
        List<EntityType<? extends Mob>> types = new ArrayList<>(List.of(
                MiteMobs.type(MobKind.BONE_LORD), MiteMobs.type(MobKind.ANCIENT_BONE_LORD)));
        for (var kind : ADDON_LORDS) types.add(ExtremeMobs.type(kind));
        return types;
    }

    private static Set<Item> vanillaTemplates() {
        return BuiltInRegistries.ITEM.stream().filter(item -> {
            var id = BuiltInRegistries.ITEM.getKey(item);
            return id.getNamespace().equals("minecraft") && id.getPath().endsWith("_armor_trim_smithing_template");
        }).collect(Collectors.toSet());
    }

    private static void seedRoll(Mob mob, boolean success) {
        int expected = success ? 9 : 10;
        for (long seed = 0; seed < 10000; seed++) {
            mob.getRandom().setSeed(seed);
            if (mob.getRandom().nextInt(100) == expected) {
                mob.getRandom().setSeed(seed);
                return;
            }
        }
        throw new IllegalStateException("Missing deterministic lord reward roll");
    }
}
