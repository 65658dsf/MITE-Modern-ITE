package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.SpecialMobEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;

/** Use the native lord generator: fixed starting material is a floor, never a date-based downgrade. @author NingMeng123 */
public final class TimeLordEquipment {
    private TimeLordEquipment() {}

    static void initialize(Mob lord) {
        equip(lord, EquipmentSlot.MAINHAND, "mite:mithril_sword");
        equip(lord, EquipmentSlot.HEAD, "mite:mithril_helmet");
        equip(lord, EquipmentSlot.CHEST, "mite:mithril_chestplate");
        equip(lord, EquipmentSlot.LEGS, "mite:mithril_leggings");
        equip(lord, EquipmentSlot.FEET, "mite:mithril_boots");
    }

    private static void equip(Mob lord, EquipmentSlot slot, String initialItem) {
        lord.setItemSlot(slot, SpecialMobEquipment.generated(lord, initialItem, true));
    }
}
