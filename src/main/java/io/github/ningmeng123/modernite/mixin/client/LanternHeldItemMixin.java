package io.github.ningmeng123.modernite.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.client.LanternCarryRendering;
import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attach the lantern ring to the palm while its body hangs below the moving hand. @author NingMeng123 */
@Mixin(ItemInHandLayer.class)
public abstract class LanternHeldItemMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void modernite$hangingLantern(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                          HumanoidArm arm, PoseStack pose, MultiBufferSource buffers,
                                          int light, CallbackInfo ci) {
        if (!(entity instanceof Player) || !(stack.getItem() instanceof DynamicCoreItem)) return;
        var parent = ((ItemInHandLayer<?, ?>) (Object) this).getParentModel();
        if (!(parent instanceof HumanoidModel<?> model)) return;
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.pushPose();
        try {
            model.translateToHand(arm, pose);
            pose.translate(-side / 16F, 9F / 16F, 0);
            // Keep the grip on the arm, then undo its orientation so gravity stays vertical.
            pose.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).conjugate());
            pose.mulPose(Axis.XP.rotationDegrees(180));
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            LanternCarryRendering.swing(pose, entity, partialTick, arm);
            LanternCarryRendering.renderLantern(entity, stack, arm, pose, buffers, light);
        } finally {
            pose.popPose();
        }
        ci.cancel();
    }
}
