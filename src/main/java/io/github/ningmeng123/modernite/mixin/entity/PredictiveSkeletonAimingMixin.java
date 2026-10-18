package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.mite.entity.SkeletonAiming;
import io.github.ningmeng123.modernite.entity.PredictiveBowAiming;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuse native ammo, damage and firing goals with a more accurate release. @author NingMeng123 */
@Mixin(value = SkeletonAiming.class, remap = false)
public abstract class PredictiveSkeletonAimingMixin {
    @Inject(method = "shoot", at = @At("HEAD"), cancellable = true)
    private static void modernite$predictRelease(AbstractSkeleton shooter, AbstractArrow arrow,
            LivingEntity target, float speed, CallbackInfo ci) {
        PredictiveBowAiming.shoot(shooter, arrow, target, speed);
        ci.cancel();
    }
}
