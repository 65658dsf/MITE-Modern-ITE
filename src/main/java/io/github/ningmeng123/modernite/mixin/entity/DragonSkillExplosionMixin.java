package io.github.ningmeng123.modernite.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.modernite.entity.EnderDragonPlusDamage;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scales the dragon skill's raw impact damage where the explosion applies it to each entity. @author NingMeng123 */
@Mixin(Explosion.class)
public abstract class DragonSkillExplosionMixin {
    @ModifyExpressionValue(method = "explode", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/ExplosionDamageCalculator;getEntityDamageAmount("
                    + "Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/entity/Entity;)F"))
    private float modernIte$scaleSkillExplosionDamage(float amount) {
        return EnderDragonPlusDamage.scaleExplosionDamage(((Explosion) (Object) this).getDirectSourceEntity(), amount);
    }
}
