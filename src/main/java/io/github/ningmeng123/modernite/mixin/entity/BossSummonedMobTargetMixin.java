package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.BossSummonedMobs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filter attack candidates before they can take up the target goal. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class BossSummonedMobTargetMixin {
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void modernIte$bossSummonTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Mob mob && !mob.level().isClientSide && BossSummonedMobs.excludesTarget(mob, target)) {
            cir.setReturnValue(false);
        }
    }
}
