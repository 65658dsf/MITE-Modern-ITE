package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.LanternArmPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carry lanterns with a slightly raised arm, including the matching skin sleeve. @author NingMeng123 */
@Mixin(HumanoidModel.class)
public abstract class LanternPlayerPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void modernite$carryLantern(LivingEntity entity, float walk, float amount, float age,
                                        float yaw, float pitch, CallbackInfo ci) {
        LanternArmPose.apply((HumanoidModel<?>) (Object) this, entity, walk, amount, age);
    }
}
