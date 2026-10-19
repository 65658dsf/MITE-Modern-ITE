package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.enchantment.Enchantments;

/** Only a correctly worn protection-V vibranium set receives the starlight overlay. @author NingMeng123 */
public final class ArmorAuraRules {
    private static final ResourceLocation PROTECTION = ResourceLocation.withDefaultNamespace("protection");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private ArmorAuraRules() {}

    public static boolean fullProtectionFive(LivingEntity entity) {
        var protection = entity.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        for (var slot : ARMOR) {
            var stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)
                    || armor.getEquipmentSlot() != slot) return false;
            var definition = MiteArmorContent.definition(stack.getItem());
            if (definition == null || definition.horse() || !"vibranium".equals(definition.material())
                    || definition.type().getSlot() != slot) return false;
            if (stack.getEnchantmentLevel(protection) < 5 && GearProgression.affixLevel(stack, PROTECTION) < 5) return false;
        }
        return true;
    }
}
