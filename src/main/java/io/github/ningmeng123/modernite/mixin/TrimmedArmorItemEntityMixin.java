package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.modernite.gear.NetheriteTrimProtection;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The current trim controls dropped armor immunity without persistent derived components. @author NingMeng123 */
@Mixin(ItemEntity.class)
public abstract class TrimmedArmorItemEntityMixin {
    @WrapMethod(method = "fireImmune")
    private boolean modernite$trimFireImmunity(Operation<Boolean> original) {
        ItemStack stack = ((ItemEntity)(Object)this).getItem();
        if (NetheriteTrimProtection.trimmedArmor(stack)) return true;
        return !NetheriteTrimProtection.removesInnateProtection(stack) && original.call();
    }

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;canBeHurtBy(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean modernite$trimmedDropDamage(ItemStack stack, DamageSource source, Operation<Boolean> original) {
        if (source.is(DamageTypeTags.IS_FIRE) && NetheriteTrimProtection.trimmedArmor(stack)) return false;
        return original.call(stack, source);
    }
}
