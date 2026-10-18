package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.mite.entity.MobShields;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** ITE halves accepted mob shield wear, rounding down before native enchantments. @author NingMeng123 */
@Mixin(value = MobShields.class, remap = false)
public abstract class MobShieldWearMixin {
    @ModifyArg(method = "hurtShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"), index = 0)
    private static int modernite$halveShieldWear(int amount) {
        return amount / 2;
    }
}
