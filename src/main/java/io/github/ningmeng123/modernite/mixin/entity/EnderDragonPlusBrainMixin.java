package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.EnderDragonPlusDamage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies ITE damage scaling at the shared Ender Dragon Plus skill damage entry point. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.enderdragonplus.dragon.DragonBrain", remap = false)
public abstract class EnderDragonPlusBrainMixin {
    @ModifyVariable(method = "damage(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"),
            argsOnly = true, ordinal = 0)
    private double modernIte$scaleSkillDamage(double multiplier) {
        return multiplier * EnderDragonPlusDamage.SKILL_MULTIPLIER;
    }
}
