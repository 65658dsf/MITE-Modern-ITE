package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Socket durability is an independent source, preserving baseline enchantment arithmetic. @author NingMeng123 */
@Mixin(value = EquipmentDurability.class, remap = false)
public abstract class GearWearMixin {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int modernite$socketWear(int amount, ItemStack stack, int ignored, LivingEntity owner, EquipmentSlot slot) {
        int rank = ExtremeGear.rank(stack, GemType.DURABILITY);
        if (rank == 0 || amount <= 0 || owner.level().isClientSide) return amount;
        int remaining = 0;
        for (int i = 0; i < amount; i++) if (owner.getRandom().nextFloat() >= rank * .05F) remaining++;
        return remaining;
    }
}
