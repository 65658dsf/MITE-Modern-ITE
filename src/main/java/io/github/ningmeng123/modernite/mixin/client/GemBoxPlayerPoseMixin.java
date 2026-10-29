package io.github.ningmeng123.modernite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.modernite.client.GemBoxArmPose;
import io.github.ningmeng123.modernite.client.GemBoxEmfPoseCompat;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply after the complete player animation chain, including NEA/playerAnimator. @author NingMeng123 */
@Mixin(LivingEntityRenderer.class)
public abstract class GemBoxPlayerPoseMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void modernite$carryGemBox(EntityModel<?> model, Entity entity, float walk, float amount, float age,
            float yaw, float pitch, Operation<Void> original) {
        original.call(model, entity, walk, amount, age, yaw, pitch);
        if (model instanceof HumanoidModel<?> humanoid && entity instanceof LivingEntity living) {
            GemBoxArmPose.apply(humanoid, living, walk, amount, age);
            if (GemBoxArmPose.active(living) && model instanceof PlayerModel<?> player) {
                player.rightSleeve.copyFrom(player.rightArm);
                player.leftSleeve.copyFrom(player.leftArm);
            }
            GemBoxEmfPoseCompat.capture(humanoid, living);
        }
    }
}
