package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.modernite.gear.NetheriteTrimProtection;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Adamantium and vibranium equipment relinquish their inherited material fire protection. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class EquipmentFireDamageMixin {
    @WrapMethod(method = "canBeHurtBy")
    private boolean modernite$equipmentFireDamage(DamageSource source, Operation<Boolean> original) {
        ItemStack stack = (ItemStack)(Object)this;
        if (source.is(DamageTypeTags.IS_FIRE) && NetheriteTrimProtection.removesInnateProtection(stack))
            return stack.getItem().canBeHurtBy(stack, source);
        return original.call(source);
    }
}
