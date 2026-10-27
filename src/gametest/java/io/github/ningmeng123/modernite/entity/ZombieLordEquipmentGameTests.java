package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual lord equipment generation, saved kits and conservative old-sword conversion. @author NingMeng123 */
@GameTestHolder("modern_ite_zombie_lord")
@PrefixGameTestTemplate(false)
public final class ZombieLordEquipmentGameTests {
    private static final String REVISION = "modern_ite:zombie_lord_equipment_revision";
    private static final ExtremeKind[] LORDS = {ExtremeKind.ZOMBIE_LORD, ExtremeKind.ZOMBIE_ELITE_LORD};
    private static final EquipmentSlot[] KIT = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ITEMS = {"vibranium_war_hammer", "vibranium_helmet", "vibranium_chestplate", "vibranium_leggings", "vibranium_boots"};

    private ZombieLordEquipmentGameTests() {}

    @GameTest(template = "gear_empty", batch = "zombie_lord_equipment")
    public static void realNewbornLordsReceiveHammersFullVibraniumAndNoIssuedShield(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            for (int day : new int[]{1, 64, 65, 128}) for (var kind : LORDS) for (int seed = 0; seed < 8; seed++) {
                site.date(day, day, true);
                Mob lord = spawned(h, site, kind, 137L + seed * 1000003L);
                for (int index = 0; index < KIT.length; index++) {
                    ItemStack stack = lord.getItemBySlot(KIT[index]);
                    h.assertTrue(id(stack).equals("mite:" + ITEMS[index]), "The actual newborn retains the exact requested source family: " + kind + "/" + day + "/" + KIT[index]);
                    h.assertTrue(EquipmentDurability.quality(stack) == Quality.AVERAGE, "Vibranium retains the source's ordinary quality semantics");
                    h.assertTrue(stack.getDamageValue() > 0 && stack.getDamageValue() < stack.getMaxDamage(), "Each source-generated piece receives real wear without breaking");
                }
                h.assertTrue(lord.getOffhandItem().isEmpty(), "Ordinary spawn and sword/shield callbacks cannot add an issued shield");
                for (var effect : List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_BOOST)) {
                    var active = lord.getEffect(effect);
                    h.assertTrue(active != null && active.getAmplifier() == 0 && active.getDuration() >= Integer.MAX_VALUE - 2, "The legacy permanent speed and strength remain level one");
                }
                remove(lord);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_equipment")
    public static void sourceWearAndRandomEnchantmentsReplacePlainAndForcedHighLevelKits(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            boolean enchanted = false, plain = false;
            for (int day : new int[]{1, 320}) for (var kind : LORDS) for (int seed = 0; seed < 16; seed++) {
                site.date(day, day, true);
                Mob lord = spawned(h, site, kind, 827L + seed * 1000003L);
                for (var slot : KIT) {
                    var stack = lord.getItemBySlot(slot);
                    if (day == 1) h.assertFalse(stack.isEnchanted(), "The first-day source loadout has no forced Protection V or Sharpness V");
                    else { enchanted |= stack.isEnchanted(); plain |= !stack.isEnchanted(); }
                    double fraction = (double)stack.getDamageValue() / stack.getMaxDamage();
                    double lower = stack.isEnchanted() ? .5 : .7, upper = stack.isEnchanted() ? .8 : 1;
                    h.assertTrue(fraction >= lower - 1D / stack.getMaxDamage() && fraction < upper,
                            "Wear follows the existing source generator's enchanted or unenchanted range: " + kind + "/" + day + "/" + slot + "/" + fraction);
                }
                remove(lord);
            }
            h.assertTrue(enchanted && plain, "Later source generation actually rolls enchantment presence rather than fixing every piece");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_equipment")
    public static void existingKitsKeepComponentsAndSummonStateAcrossDateRewindsAndNbt(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            for (var kind : LORDS) {
                site.date(320, 320, true);
                Mob lord = spawned(h, site, kind, 913L);
                var held = lord.getMainHandItem(); EquipmentDurability.setQuality(held, Quality.EXCELLENT); held.setDamageValue(17);
                held.set(DataComponents.CUSTOM_NAME, Component.literal("Retained lord hammer"));
                var memory = ((ExtremeMob)lord).extremeMemory(); memory.summons = 12; memory.supportCooldown = 17;
                List<ItemStack> before = equipment(lord);
                CompoundTag saved = new CompoundTag(); lord.saveWithoutId(saved); remove(lord);
                site.date(1, 1, true);
                Mob loaded = site.mob(ExtremeMobs.type(kind)); loaded.load(saved);
                h.assertTrue(site.level.addFreshEntity(loaded), "The saved manual lord rejoins after the calendar rewinds");
                MobAbilities.initialize(loaded, (ExtremeMob)loaded);
                assertEquipment(h, before, loaded, "NBT and repeated initialization never reroll existing equipment");
                h.assertTrue(((ExtremeMob)loaded).extremeMemory().summons == 12 && ((ExtremeMob)loaded).extremeMemory().supportCooldown == 17,
                        "Equipment initialization cannot reset the saved reinforcement quota or interval");
                remove(loaded);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_equipment")
    public static void oldDefaultSwordConvertsOnceWithoutLosingQualityEnchantmentsOrWear(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(128, 128, true);
            Mob old = spawned(h, site, ExtremeKind.ZOMBIE_LORD, 453L);
            ItemStack sword = MobAbilities.item("mite:vibranium_sword");
            EquipmentDurability.setQuality(sword, Quality.EXCELLENT);
            sword.enchant(site.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS), 3);
            sword.setDamageValue(sword.getMaxDamage() / 3);
            double wear = (double)sword.getDamageValue() / sword.getMaxDamage();
            old.setItemSlot(EquipmentSlot.MAINHAND, sword); old.setDropChance(EquipmentSlot.MAINHAND, .085F);
            List<ItemStack> before = equipment(old);
            old.getPersistentData().remove(REVISION);
            CompoundTag saved = new CompoundTag(); old.saveWithoutId(saved); remove(old);
            Mob loaded = site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_LORD)); loaded.load(saved);
            MobAbilities.initialize(loaded, (ExtremeMob)loaded);
            var hammer = loaded.getMainHandItem();
            h.assertTrue(id(hammer).equals("mite:vibranium_war_hammer"), "An old default ordinary-lord sword upgrades to the requested hammer");
            h.assertTrue(EquipmentDurability.quality(hammer) == EquipmentDurability.quality(sword)
                            && Objects.equals(hammer.get(DataComponents.ENCHANTMENTS), sword.get(DataComponents.ENCHANTMENTS)),
                    "The narrow conversion retains quality and enchantment components");
            h.assertTrue(Math.abs((double)hammer.getDamageValue() / hammer.getMaxDamage() - wear) <= 1D / hammer.getMaxDamage(), "The conversion preserves relative remaining durability");
            for (var slot : EquipmentSlot.values()) if (slot != EquipmentSlot.MAINHAND)
                h.assertTrue(ItemStack.matches(before.get(slot.ordinal()), loaded.getItemBySlot(slot)), "The conversion does not regenerate an armor or offhand slot");
            h.assertTrue(loaded.getPersistentData().getInt(REVISION) == 1, "The saved conversion records completion");
            List<ItemStack> migrated = equipment(loaded); MobAbilities.initialize(loaded, (ExtremeMob)loaded);
            assertEquipment(h, migrated, loaded, "A completed conversion never rerolls on a later initialization");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_equipment")
    public static void oldCustomPickedUpAndOtherWeaponsRemainUntouched(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(128, 128, true);
            for (int scenario = 0; scenario < 7; scenario++) {
                Mob old = spawned(h, site, ExtremeKind.ZOMBIE_LORD, 31L + scenario);
                ItemStack stack = scenario == 5 ? new ItemStack(Items.IRON_SWORD) : scenario == 6 ? ItemStack.EMPTY : MobAbilities.item("mite:vibranium_sword");
                if (scenario == 0) stack.set(DataComponents.CUSTOM_NAME, Component.literal("A player's named sword"));
                if (scenario == 1) { CompoundTag custom = new CompoundTag(); custom.putInt("retained", 1); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom)); }
                if (scenario == 2) stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Retain this history"))));
                if (scenario == 3) stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
                old.setItemSlot(EquipmentSlot.MAINHAND, stack); old.setDropChance(EquipmentSlot.MAINHAND, scenario == 4 ? 2F : .085F);
                old.getPersistentData().remove(REVISION);
                CompoundTag saved = new CompoundTag(); old.saveWithoutId(saved); remove(old);
                Mob loaded = site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_LORD)); loaded.load(saved);
                MobAbilities.initialize(loaded, (ExtremeMob)loaded);
                h.assertTrue(ItemStack.matches(stack, loaded.getMainHandItem()), "Old custom, picked-up, unrelated or empty hands are preserved exactly: " + scenario);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_equipment")
    public static void underworldLordsRollIndependentSourceBonusesAndNeverRerollSavedGear(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(1, 1, true);
            ServerLevel underworld = site.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            h.assertTrue(underworld != null, "The real registered Underworld is available for equipment generation");
            var sharpness = underworld.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
            var protection = underworld.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
            var observed = new HashSet<Integer>(); boolean differentSlots = false;
            for (var kind : LORDS) for (int seed = 0; seed < 16; seed++) {
                Mob lord = ExtremeMobs.type(kind).create(underworld); site.mobs.add(lord);
                lord.moveTo(0.5, 200, 0.5); lord.setNoAi(true); lord.getRandom().setSeed(137L + seed * 1000003L);
                MobAbilities.initialize(lord, (ExtremeMob)lord);
                int weapon = lord.getMainHandItem().getEnchantmentLevel(sharpness); observed.add(weapon);
                h.assertTrue(weapon >= 0 && weapon <= 5, "The source Underworld weapon bonus stays between zero and five");
                for (int index = 1; index < KIT.length; index++) {
                    int level = lord.getItemBySlot(KIT[index]).getEnchantmentLevel(protection); observed.add(level); differentSlots |= level != weapon;
                    h.assertTrue(level >= 0 && level <= 5, "Each armor piece receives its own bounded source bonus");
                }
                List<ItemStack> before = equipment(lord); CompoundTag saved = new CompoundTag(); lord.saveWithoutId(saved); remove(lord);
                Mob loaded = ExtremeMobs.type(kind).create(underworld); site.mobs.add(loaded); loaded.load(saved);
                MobAbilities.initialize(loaded, (ExtremeMob)loaded);
                assertEquipment(h, before, loaded, "Reading an Underworld lord never grants another round of bonuses");
            }
            h.assertTrue(observed.size() > 1 && differentSlots, "The five slots use independent variable rolls rather than a fixed Protection V kit");
        }
        h.succeed();
    }

    private static Mob spawned(GameTestHelper h, SpawnBalanceGameTests.Site site, ExtremeKind kind, long seed) {
        Mob lord = site.mob(ExtremeMobs.type(kind)); lord.getRandom().setSeed(seed);
        EventHooks.finalizeMobSpawn(lord, site.level, site.level.getCurrentDifficultyAt(lord.blockPosition()), MobSpawnType.SPAWN_EGG, null);
        h.assertTrue(site.level.addFreshEntity(lord), "The lord completes actual spawn finalization and enters the server world");
        return lord;
    }

    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static List<ItemStack> equipment(Mob lord) {
        var result = new ArrayList<ItemStack>(); for (var slot : EquipmentSlot.values()) result.add(lord.getItemBySlot(slot).copy()); return result;
    }
    private static void assertEquipment(GameTestHelper h, List<ItemStack> expected, Mob lord, String message) {
        for (var slot : EquipmentSlot.values()) h.assertTrue(ItemStack.matches(expected.get(slot.ordinal()), lord.getItemBySlot(slot)), message + ": " + slot);
    }
    private static void remove(Mob mob) { mob.discard(); SpawnPopulation.release(mob); }
}
