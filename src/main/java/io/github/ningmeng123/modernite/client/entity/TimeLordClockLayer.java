package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Time Lord's existing clock hangs from its animated chest instead of its offhand. @author NingMeng123 */
public final class TimeLordClockLayer extends ItemInHandLayer<AbstractSkeleton, SkeletonModel<AbstractSkeleton>> {
    private static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public TimeLordClockLayer(RenderLayerParent<AbstractSkeleton, SkeletonModel<AbstractSkeleton>> parent,
                              ItemInHandRenderer items) {
        super(parent, items);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractSkeleton lord,
                                 float limb, float amount, float partial, float age, float yaw, float pitch) {
        // Retain the ordinary weapon pipeline, including the base mod's animated weapon grip.
        super.render(pose, buffers, light, lord, limb, amount, partial, age, yaw, pitch);
        if (lord.isInvisible() || !lord.getOffhandItem().is(Items.CLOCK) || !getParentModel().body.visible) return;
        pose.pushPose();
        try {
            if (getParentModel().young) { pose.translate(0, .75, 0); pose.scale(.5F, .5F, .5F); }
            getParentModel().body.translateAndRotate(pose);
            ItemStack armor = lord.getItemBySlot(EquipmentSlot.CHEST);
            boolean armored = armor.getItem() instanceof ArmorItem piece && piece.getEquipmentSlot() == EquipmentSlot.CHEST;
            pose.translate(0, 0, -(armored ? 3.5F : 2.5F) / 16F);
            VertexConsumer chain = buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN));
            cord(pose.last(), chain, -.125F, .025F, -.025F, .18F, light);
            cord(pose.last(), chain, .125F, .025F, .025F, .18F, light);
            pose.translate(0, .29F, -.006F);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(.32F, .32F, .32F);
            Minecraft.getInstance().getItemRenderer().renderStatic(lord, lord.getOffhandItem(), ItemDisplayContext.FIXED,
                    false, pose, buffers, lord.level(), light, OverlayTexture.NO_OVERLAY, lord.getId());
        } finally { pose.popPose(); }
    }

    @Override protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                               HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light) {
        if (arm != entity.getMainArm() && stack.is(Items.CLOCK)) return;
        super.renderArmWithItem(entity, stack, context, arm, pose, buffers, light);
    }

    private static void cord(PoseStack.Pose pose, VertexConsumer vertices, float ax, float ay, float bx, float by, int light) {
        float length = (float)Math.hypot(bx - ax, by - ay);
        float dx = -(by - ay) / length * .007F, dy = (bx - ax) / length * .007F;
        vertex(pose, vertices, ax + dx, ay + dy, light);
        vertex(pose, vertices, bx + dx, by + dy, light);
        vertex(pose, vertices, bx - dx, by - dy, light);
        vertex(pose, vertices, ax - dx, ay - dy, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, int light) {
        vertices.addVertex(pose, x, y, 0).setColor(222, 181, 70, 255).setUv(.5F, .5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
    }
}
