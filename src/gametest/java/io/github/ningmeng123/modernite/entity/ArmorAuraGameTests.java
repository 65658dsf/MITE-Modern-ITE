package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Validate the vibranium identity, worn slots and both real protection sources. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ArmorAuraGameTests {
    private static final ResourceLocation PROTECTION = ResourceLocation.withDefaultNamespace("protection");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @GameTest(template = "gear_empty")
    public static void fullProtectionFiveVibraniumWorksForPlayersAndZombies(GameTestHelper h) {
        for (var entity : wearers(h)) {
            equip(entity, "vibranium", false);
            h.assertTrue(ArmorAuraRules.fullProtectionFive(entity), "Players and zombies wearing four protection-V vibranium pieces receive gold decoration");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void nonVibraniumAndMixedMaterialsNeverActivateDecoration(GameTestHelper h) {
        for (var entity : wearers(h)) {
            equip(entity, "iron", false);
            h.assertFalse(ArmorAuraRules.fullProtectionFive(entity), "A complete protection-V iron set receives no vibranium decoration");
            for (var material : new String[]{"iron", "mithril"}) {
                for (var slot : ARMOR) {
                    equip(entity, "vibranium", false);
                    entity.setItemSlot(slot, enchantedPiece(entity, material, slot, 5));
                    h.assertFalse(ArmorAuraRules.fullProtectionFive(entity), "One " + material + " piece in " + slot + " disables the vibranium decoration");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void protectionFiveVibraniumInTheWrongSlotsDoesNotQualify(GameTestHelper h) {
        for (var entity : wearers(h)) {
            equip(entity, "vibranium", false);
            var helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
            var boots = entity.getItemBySlot(EquipmentSlot.FEET);
            entity.setItemSlot(EquipmentSlot.HEAD, boots);
            entity.setItemSlot(EquipmentSlot.FEET, helmet);
            h.assertFalse(ArmorAuraRules.fullProtectionFive(entity), "Four vibranium protection-V items must occupy their actual armor slots");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void eachVibraniumPieceNeedsProtectionFiveFromOneSource(GameTestHelper h) {
        for (var entity : wearers(h)) {
            for (var slot : ARMOR) {
                equip(entity, "vibranium", false);
                entity.setItemSlot(slot, enchantedPiece(entity, "vibranium", slot, 4));
                h.assertFalse(ArmorAuraRules.fullProtectionFive(entity), "A protection-IV piece in " + slot + " disables decoration");
                growthProtection(entity.getItemBySlot(slot), 4);
                h.assertFalse(ArmorAuraRules.fullProtectionFive(entity), "Ordinary IV and growth IV on one item do not combine into protection V");
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void growthProtectionFiveAndMixedSourcesQualify(GameTestHelper h) {
        for (var entity : wearers(h)) {
            equip(entity, "vibranium", true);
            h.assertTrue(ArmorAuraRules.fullProtectionFive(entity), "Four MITE growth protection-V affixes qualify without ordinary enchantments");
            entity.setItemSlot(EquipmentSlot.HEAD, enchantedPiece(entity, "vibranium", EquipmentSlot.HEAD, 5));
            entity.setItemSlot(EquipmentSlot.LEGS, enchantedPiece(entity, "vibranium", EquipmentSlot.LEGS, 6));
            h.assertTrue(ArmorAuraRules.fullProtectionFive(entity), "Ordinary protection V or higher and MITE growth protection V can qualify different pieces of the same set");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void removingAnyVibraniumPieceImmediatelyDisablesDecoration(GameTestHelper h) {
        for (var entity : wearers(h)) {
            for (var slot : ARMOR) {
                equip(entity, "vibranium", false);
                entity.setItemSlot(slot, ItemStack.EMPTY);
                h.assertFalse(ArmorAuraRules.fullProtectionFive(entity), "Removing the piece in " + slot + " immediately disables decoration");
            }
        }
        h.succeed();
    }

    private static LivingEntity[] wearers(GameTestHelper h) {
        return new LivingEntity[]{h.makeMockPlayer(GameType.SURVIVAL), new Zombie(h.getLevel())};
    }

    private static void equip(LivingEntity entity, String material, boolean growth) {
        for (var slot : ARMOR) {
            var stack = growth ? piece(material, slot) : enchantedPiece(entity, material, slot, 5);
            if (growth) growthProtection(stack, 5);
            entity.setItemSlot(slot, stack);
        }
    }

    private static ItemStack enchantedPiece(LivingEntity entity, String material, EquipmentSlot slot, int level) {
        var stack = piece(material, slot);
        var protection = entity.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        stack.enchant(protection, level);
        return stack;
    }

    private static ItemStack piece(String material, EquipmentSlot slot) {
        String suffix = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> throw new IllegalArgumentException("Not a humanoid armor slot: " + slot);
        };
        return new ItemStack(MiteArmorContent.get(("iron".equals(material) ? "minecraft:" : "mite:") + material + "_" + suffix));
    }

    private static void growthProtection(ItemStack stack, int level) {
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 5, 0L, Map.of(PROTECTION, level), 1));
    }
}
