package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.SpecialMobEquipment;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual lord generation, support admission, breeze brain, damage and save lifecycle. @author NingMeng123 */
@GameTestHolder("modern_ite_time_lord")
@PrefixGameTestTemplate(false)
public final class TimeLordSupportGameTests {
    private TimeLordSupportGameTests() {}

    @GameTest(template = "gear_empty", batch = "time_lord_support")
    public static void eggAndCommandLordsSummonActualBreezesDuringFirstDayGrace(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(1, 1, false);
            var player = fixture.site.player(8);
            for (boolean command : new boolean[]{false, true}) {
                Mob lord = command ? fixture.fresh() : fixture.lord();
                if (command) lord.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true);
                lord.setTarget(player);
                MobAbilities.tick(lord, (ExtremeMob) lord);
                var troops = fixture.support(lord);
                h.assertTrue(troops.size() == 1 && ((ExtremeMob) lord).extremeMemory().summons == 1,
                        "A manually requested Time Lord admits an actual breeze even on day one: " + command);
                h.assertTrue(troops.getFirst().getSpawnType() == MobSpawnType.MOB_SUMMONED && troops.getFirst().getTarget() == player,
                        "The troop completes real spawn finalization and joins with its breeze brain target");
                h.assertTrue(SpawnPopulation.explicit(troops.getFirst()), "The troop retains the explicit encounter admission through finalization and join");
                CompoundTag saved = new CompoundTag(); lord.saveWithoutId(saved);
                Mob loaded = fixture.fresh(); loaded.load(saved); loaded.setTarget(player);
                ((ExtremeMob) loaded).extremeMemory().supportCooldown = 0;
                MobAbilities.tick(loaded, (ExtremeMob) loaded);
                h.assertTrue(((ExtremeMob) loaded).extremeMemory().summons == 2,
                        "The boss's saved explicit origin also works after reloading");
            }
            h.assertTrue(ExtremeWorldState.graceActive(fixture.site.level), "Manual boss support does not end the world's grace period");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_support")
    public static void naturalLordsAndOtherTroopsStillRespectFirstDayGrace(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(1, 1, false);
            Mob lord = fixture.fresh(); lord.setTarget(fixture.site.player(8));
            MobAbilities.tick(lord, (ExtremeMob) lord);
            h.assertTrue(fixture.support(lord).isEmpty() && ((ExtremeMob) lord).extremeMemory().summons == 0,
                    "A non-explicit Time Lord cannot bypass ordinary automatic hostile protection");
            Mob manual = fixture.lord();
            h.assertTrue(MobAbilities.summonType(manual, EntityType.ZOMBIE, 1) == null,
                    "The exception does not leak into other summoned monster types");
            h.assertFalse(SpawnPopulation.automaticAllowed(fixture.site.level, EntityType.BREEZE),
                    "Natural breeze spawning remains protected on day one");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_support")
    public static void manualFirstDayTroopsRetainCombatAndCrowdingChecks(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(1, 1, false);
            fixture.site.legacyDates.set("local_hostile_limit", 1);
            Mob lord = fixture.lord(); var state = (ExtremeMob) lord;
            var player = fixture.site.player(8); lord.setTarget(player);
            player.setGameMode(GameType.CREATIVE); MobAbilities.tick(lord, state);
            h.assertTrue(fixture.support(lord).isEmpty(), "A creative observer does not trigger combat summons");
            player.setGameMode(GameType.SURVIVAL); MobAbilities.tick(lord, state);
            h.assertTrue(fixture.support(lord).size() == 1, "A survival target starts the manual encounter");
            state.extremeMemory().supportCooldown = 0; MobAbilities.tick(lord, state);
            h.assertTrue(fixture.support(lord).size() == 1 && state.extremeMemory().summons == 1,
                    "Explicit origin still obeys local crowding and failed attempts do not spend summon quota");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_equipment")
    public static void nativeLordGeneratorKeepsMithrilAsTheFloorAndRollsOnlyHigherMaterials(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            for (long day : new long[]{1, 16, 64, 96}) {
                fixture.site.date(day, day, true);
                Mob lord = fixture.lord();
                h.assertTrue(SpecialMobEquipment.handles(lord), "The real native special-equipment generator recognizes the Time Lord");
                for (var slot : kitSlots()) {
                    String id = id(lord.getItemBySlot(slot));
                    h.assertTrue(id.startsWith("mite:mithril_") || id.startsWith("mite:adamantium_") || id.startsWith("mite:vibranium_"),
                            "An earlier date can never downgrade the initial mithril family: " + day + "/" + slot + "/" + id);
                    h.assertTrue(EquipmentDurability.quality(lord.getItemBySlot(slot)).ordinal() >= Quality.AVERAGE.ordinal(),
                            "Native quality is never below the initial item's average quality");
                }
                h.assertTrue(lord.getOffhandItem().is(Items.CLOCK), "Material progression retains the separate clock");
                if (day == 1) for (var slot : kitSlots()) h.assertTrue(id(lord.getItemBySlot(slot)).startsWith("mite:mithril_"),
                        "Before date progression, every initial equipment slot is exactly mithril");
                lord.discard();
            }
            fixture.site.date(320, 320, true);
            boolean higher = false;
            for (int seed = 0; seed < 24; seed++) {
                var lord = fixture.fresh(); lord.getRandom().setSeed(seed);
                EventHooks.finalizeMobSpawn(lord, fixture.site.level, fixture.site.level.getCurrentDifficultyAt(lord.blockPosition()), MobSpawnType.SPAWN_EGG, null);
                MobAbilities.initialize(lord, (ExtremeMob) lord);
                for (var slot : kitSlots()) higher |= !id(lord.getItemBySlot(slot)).startsWith("mite:mithril_");
                h.assertTrue(lord.getPersistentData().getCompound(SpecialMobEquipment.DATA_KEY).getLong("highest_day") == 320,
                        "The entire kit uses the existing highest-date spawn snapshot");
                lord.discard();
            }
            h.assertTrue(higher, "Later actual generated Time Lords receive adamantium or vibranium upgrades through the native generator");
            h.assertFalse(SpecialMobEquipment.handles(fixture.site.mob(ExtremeMobs.type(ExtremeKind.SKELETON_ELITE_LORD))),
                    "The new integration does not alter unrelated addon lord equipment policies");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_equipment")
    public static void dateRewindsAndNbtReloadNeverRerollOrDegradeAnExistingKit(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(320, 320, true);
            Mob lord = fixture.lord();
            ItemStack custom = MobAbilities.item("mite:vibranium_sword");
            EquipmentDurability.setQuality(custom, Quality.EXCELLENT);
            custom.setDamageValue(17);
            lord.setItemSlot(EquipmentSlot.MAINHAND, custom);
            var memory = ((ExtremeMob) lord).extremeMemory(); memory.summons = 4; memory.supportCooldown = 73;
            List<ItemStack> before = equipment(lord);
            CompoundTag saved = new CompoundTag(); lord.saveWithoutId(saved); lord.discard();
            fixture.site.date(1, 1, true);
            Mob restored = fixture.fresh(); restored.load(saved);
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(restored, fixture.site.level, true));
            MobAbilities.initialize(restored, (ExtremeMob) restored);
            for (var slot : EquipmentSlot.values()) h.assertTrue(ItemStack.matches(before.get(slot.ordinal()), restored.getItemBySlot(slot)),
                    "Reload and date rewind retain all existing equipment components: " + slot);
            var read = ((ExtremeMob) restored).extremeMemory();
            h.assertTrue(read.summons == 4 && read.supportCooldown == 73, "Summon count and its independent cooldown survive NBT");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_support")
    public static void actualCombatSummonsBoostedBreezesWithTheCorrectBrainTargetAndSeparateCooldown(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            Mob lord = fixture.lord(); var state = (ExtremeMob) lord;
            var player = fixture.site.player(8); lord.setTarget(player);
            state.extremeMemory().cooldown = 70;
            MobAbilities.tick(lord, state);
            List<Breeze> support = fixture.support(lord);
            h.assertTrue(support.size() == 1 && state.extremeMemory().summons == 1, "The actual combat tick admits one breeze through the normal support pipeline");
            Breeze breeze = support.getFirst();
            h.assertTrue(breeze.getTarget() == player, "The actual breeze brain targets the lord's player");
            h.assertTrue(breeze.getPersistentData().getBoolean(TimeLordMinions.MARKER), "Only summoned troops carry the enhancement marker");
            h.assertTrue(state.extremeMemory().cooldown == 69 && state.extremeMemory().supportCooldown == 100,
                    "Support has its own five-second cooldown and does not reset the time spell");
            var natural = (Breeze) fixture.site.mob(EntityType.BREEZE);
            h.assertTrue(breeze.getAttribute(Attributes.MAX_HEALTH).getModifier(boost()).amount() == .5,
                    "The summoned troop has the dedicated fifty-percent health modifier");
            h.assertTrue(Math.abs(breeze.getAttributeValue(Attributes.ATTACK_DAMAGE) / natural.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.25) < 1E-8,
                    "The actual attack attribute is twenty-five percent stronger");
            h.assertTrue(Math.abs(breeze.getAttributeValue(Attributes.MOVEMENT_SPEED) / natural.getAttributeValue(Attributes.MOVEMENT_SPEED) - 1.1) < 1E-8,
                    "The actual movement attribute is ten percent faster");
            MobAbilities.tick(lord, state);
            h.assertTrue(fixture.support(lord).size() == 1, "The next tick cannot bypass the summon cooldown");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_support")
    public static void cumulativeSixTroopLimitAndSaveReloadPreventUnlimitedReinforcements(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            Mob lord = fixture.lord(); var state = (ExtremeMob) lord; lord.setTarget(fixture.site.player(8));
            for (int wave = 0; wave < 6; wave++) {
                state.extremeMemory().supportCooldown = 0;
                MobAbilities.tick(lord, state);
            }
            h.assertTrue(fixture.support(lord).size() == 6 && state.extremeMemory().summons == 6, "Six real admitted troops reach the same lifetime limit as the skeleton lord");
            for (int wave = 0; wave < 4; wave++) { state.extremeMemory().supportCooldown = 0; MobAbilities.tick(lord, state); }
            h.assertTrue(fixture.support(lord).size() == 6, "Further ready ticks cannot exceed the six-troop quota");
            CompoundTag saved = new CompoundTag(); lord.saveWithoutId(saved); lord.discard();
            Mob copy = fixture.fresh(); copy.load(saved); copy.setTarget(fixture.site.player(9));
            var loaded = (ExtremeMob) copy; loaded.extremeMemory().supportCooldown = 0;
            MobAbilities.tick(copy, loaded);
            h.assertTrue(loaded.extremeMemory().summons == 6 && fixture.support(copy).size() == 6,
                    "A reloaded lord retains its spent quota instead of recreating its army");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_support")
    public static void canceledTroopsReleaseReservationsAndDoNotSpendTheSummonQuota(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            Mob lord = fixture.lord(); var state = (ExtremeMob) lord; lord.setTarget(fixture.site.player(8));
            Consumer<EntityJoinLevelEvent> cancel = event -> { if (event.getEntity() instanceof Breeze && event.getEntity().getPersistentData().getBoolean(TimeLordMinions.MARKER)) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, cancel);
            try { MobAbilities.tick(lord, state); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(state.extremeMemory().summons == 0 && SpawnPopulation.supportCount(lord) == 0,
                    "Canceled joins release the normal reservation and do not consume a successful summon");
            state.extremeMemory().supportCooldown = 0; MobAbilities.tick(lord, state);
            h.assertTrue(state.extremeMemory().summons == 1 && SpawnPopulation.supportCount(lord) == 1,
                    "A later normal attempt can still spend that admission slot");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_support")
    public static void savedBreezeBoostDoesNotStackOrHealAndRealWindDamageIsStronger(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            fixture.site.date(128, 128, true);
            var ordinary = (Breeze) fixture.site.mob(EntityType.BREEZE);
            var enhanced = (Breeze) fixture.site.mob(EntityType.BREEZE); TimeLordMinions.prepare(enhanced);
            enhanced.setHealth(enhanced.getMaxHealth() * .4F);
            float maximum = enhanced.getMaxHealth();
            CompoundTag saved = new CompoundTag(); enhanced.saveWithoutId(saved);
            var copy = (Breeze) fixture.site.mob(EntityType.BREEZE); copy.load(saved);
            for (int repeat = 0; repeat < 3; repeat++) TimeLordMinions.join(new EntityJoinLevelEvent(copy, fixture.site.level, true));
            h.assertTrue(Math.abs(copy.getMaxHealth() - maximum) < 1E-5 && Math.abs(copy.getHealth() / copy.getMaxHealth() - .4F) < 1E-5,
                    "Repeated load reconciliation retains the modifier once and preserves injuries");
            var first = fixture.site.mob(EntityType.COW); var second = fixture.site.mob(EntityType.COW);
            for (var cow : List.of(first, second)) { cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); cow.setHealth(200); }
            var naturalShot = EntityType.BREEZE_WIND_CHARGE.create(fixture.site.level); naturalShot.setOwner(ordinary);
            var enhancedShot = EntityType.BREEZE_WIND_CHARGE.create(fixture.site.level); enhancedShot.setOwner(copy);
            first.hurt(fixture.site.level.damageSources().windCharge(naturalShot, ordinary), .5F);
            second.hurt(fixture.site.level.damageSources().windCharge(enhancedShot, copy), .5F);
            float normalDamage = 200 - first.getHealth(), boostedDamage = 200 - second.getHealth();
            h.assertTrue(normalDamage > 0 && Math.abs(boostedDamage / normalDamage - 1.25) < .001,
                    "The actual attributed wind-charge damage path is twenty-five percent stronger, rather than an unused panel stat");
            h.assertFalse(ordinary.getPersistentData().getBoolean(TimeLordMinions.MARKER), "Natural breezes remain unmodified");
        }
        h.succeed();
    }

    private static net.minecraft.resources.ResourceLocation boost() { return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("modern_ite", "time_lord_minion"); }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static List<EquipmentSlot> kitSlots() { return List.of(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET); }
    private static List<ItemStack> equipment(Mob mob) { var result = new ArrayList<ItemStack>(); for (var slot : EquipmentSlot.values()) result.add(mob.getItemBySlot(slot).copy()); return result; }

    private static final class Fixture implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final List<Mob> lords = new ArrayList<>();
        Fixture(GameTestHelper h) { site = new SpawnBalanceGameTests.Site(h); }
        Mob fresh() { Mob lord = site.mob(ExtremeMobs.type(ExtremeKind.TIME_LORD)); lords.add(lord); return lord; }
        Mob lord() { Mob lord = fresh(); EventHooks.finalizeMobSpawn(lord, site.level, site.level.getCurrentDifficultyAt(lord.blockPosition()), MobSpawnType.SPAWN_EGG, null); MobAbilities.initialize(lord, (ExtremeMob) lord); return lord; }
        List<Breeze> support(Mob lord) {
            var result = new ArrayList<Breeze>();
            for (var entity : site.level.getAllEntities()) if (entity instanceof Breeze breeze
                    && breeze.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                    && breeze.getPersistentData().getUUID(SpawnPopulation.SUMMONER).equals(lord.getUUID())) result.add(breeze);
            return result;
        }
        @Override public void close() {
            for (var lord : lords) support(lord).forEach(Mob::discard);
            site.close();
        }
    }
}
