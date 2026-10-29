package io.github.ningmeng123.modernite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.gear.GemBoxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Align the box's bottom with both real palms, including slim skins and crouching. @author NingMeng123 */
public final class GemBoxThirdPersonRendering {
    public static final float SCALE = .63F;
    public static final float MODEL_BOTTOM = .4F;
    public static final float FORWARD_SHIFT = 3.5F / 16F;
    public static final float GEM_SCALE = .45F;

    private GemBoxThirdPersonRendering() {}

    public static Vector3f palm(HumanoidModel<?> model, HumanoidArm arm) {
        PoseStack hand = new PoseStack();
        model.translateToHand(arm, hand);
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        return hand.last().pose().transformPosition(-side / 16F, 10.5F / 16F, 0F, new Vector3f());
    }

    public static void render(HumanoidModel<?> model, LivingEntity entity, ItemStack stack,
            PoseStack pose, MultiBufferSource buffers, int light) {
        Vector3f grip = GemBoxArmPose.supportAnchor(model);
        pose.pushPose();
        try {
            // Fingers support the rear underside; the open lid stays clear of the player's face.
            pose.translate(grip.x, grip.y, grip.z - FORWARD_SHIFT);
            // Model-space Y points down. Turning around Z keeps the chest's front facing forward.
            pose.mulPose(Axis.ZP.rotationDegrees(180F));
            pose.scale(SCALE, SCALE, SCALE);
            // ItemRenderer subtracts (8,8,8); lift the model so its bottom centre is the grip.
            pose.translate(0F, (8F - MODEL_BOTTOM) / 16F, 0F);
            GemBoxModelRendering.render(entity, stack, pose, buffers, light);
        } finally {
            pose.popPose();
        }
        Minecraft mc = Minecraft.getInstance();
        float tick = GemBoxOpeningMotion.elapsed(GemBoxItem.openingProgress(stack, entity.level(),
                mc.getTimer().getGameTimeDeltaPartialTick(true)));
        if (tick < GemBoxItem.REVEAL_TICK) return;
        ItemStack gem = GemBoxItem.revealedGem(stack);
        if (gem.isEmpty()) return;
        Vector3f hand = palm(model, HumanoidArm.RIGHT);
        pose.pushPose();
        try {
            pose.translate(hand.x, hand.y, hand.z);
            pose.mulPose(Axis.ZP.rotationDegrees(180F));
            pose.mulPose(Axis.YP.rotationDegrees(-15F));
            pose.scale(GEM_SCALE, GEM_SCALE, GEM_SCALE);
            mc.getItemRenderer().renderStatic(entity, gem, ItemDisplayContext.NONE, false, pose, buffers,
                    entity.level(), light, OverlayTexture.NO_OVERLAY, entity.getId());
        } finally {
            pose.popPose();
        }
    }
}
