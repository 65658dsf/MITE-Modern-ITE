package io.github.ningmeng123.modernite.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.GemBoxArmPose;
import io.github.ningmeng123.modernite.client.GemBoxThirdPersonRendering;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One central chest replaces both hand items while the two-handed carry pose is active. @author NingMeng123 */
@Mixin(value = ItemInHandLayer.class, priority = 1100)
public abstract class GemBoxHeldItemMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void modernite$twoHandedGemBox(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (GemBoxArmPose.hiddenForMovement(entity)) {
            if (stack.is(ExtremeGear.GEM_BOX.get())) ci.cancel();
            return;
        }
        if (!GemBoxArmPose.active(entity)) return;
        var parent = ((ItemInHandLayer<?, ?>)(Object)this).getParentModel();
        if (!(parent instanceof HumanoidModel<?> model)) return;
        InteractionHand hand = GemBoxArmPose.carryingHand(entity);
        HumanoidArm owner = hand == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        if (arm == owner) GemBoxThirdPersonRendering.render(model, entity, entity.getItemInHand(hand), pose, buffers, light);
        ci.cancel();
    }
}
