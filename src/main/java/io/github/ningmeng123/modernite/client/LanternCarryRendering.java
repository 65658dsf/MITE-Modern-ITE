package io.github.ningmeng123.modernite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Vector3f;

/** A visible skin hand carries the lantern by its ring; the body swings below it. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class LanternCarryRendering {
    private LanternCarryRendering() {}

    @SubscribeEvent
    public static void durability(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR) || mc.options.hideGui
            || mc.player == null || mc.player.isSpectator()) return;
        ItemStack stack = mc.player.getOffhandItem();
        if (!(stack.getItem() instanceof DynamicCoreItem)) return;
        int maximum = stack.getMaxDamage();
        int remaining = Math.max(0, maximum - stack.getDamageValue());
        String text = remaining + "/" + maximum;
        int color = remaining == 0 ? 0xFFFF5555 : remaining <= maximum / 4 ? 0xFFFFCC55 : 0xFFE5F2DB;
        var graphics = event.getGuiGraphics();
        int side = mc.player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        // Keep the number just outside the health/food rows, even for the longest lifetime.
        int center = graphics.guiWidth() / 2 + side * 117;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(center, graphics.guiHeight() - 35, 0);
            graphics.pose().scale(0.75F, 0.75F, 1);
            graphics.drawString(mc.font, text, -mc.font.width(text) / 2, 0, color, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || !(event.getItemStack().getItem() instanceof DynamicCoreItem) || player.isScoping()) return;
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float partialTick = event.getPartialTick();
        float time = player.tickCount + partialTick;
        float walking = Mth.clamp(player.walkAnimation.speed(partialTick), 0, 1);
        float attack = Mth.sin(Mth.sqrt(event.getSwingProgress()) * Mth.PI);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(side * (0.48F + Mth.sin(time * 0.08F) * 0.003F),
                -0.08F - event.getEquipProgress() * 0.65F + attack * 0.07F
                    + Mth.sin(player.walkAnimation.position(partialTick) * 1.6F) * walking * 0.012F,
                -1.0F + attack * 0.04F);

            // Use the real player's arm and sleeve, including slim skins and native arm-render hooks.
            PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
            PoseStack hand = new PoseStack();
            // Bring the forearm in from the outside, leaving the hanging body visible below the fist.
            hand.mulPose(Axis.ZP.rotationDegrees(side * 55));
            hand.mulPose(Axis.XP.rotationDegrees(110 - attack * 12));
            hand.scale(1, -1, -1);
            hand.translate(side * 0.375F, -0.6875F, 0);
            pose.pushPose();
            try {
                pose.mulPose(hand.last().pose());
                if (!player.isInvisible()) {
                    if (arm == HumanoidArm.RIGHT) renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
                    else renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
                }
            } finally {
                pose.popPose();
            }
            // The native hand renderer sets the final arm pose. Resolve its actual palm, so the
            // ring stays attached even when skin width, attack movement or arm bob changes.
            if (!player.isInvisible()) {
                renderer.getModel().translateToHand(arm, hand);
                Vector3f grip = hand.last().pose().transformPosition(-side / 16F, 9F / 16F, 0, new Vector3f());
                pose.translate(grip.x, grip.y, grip.z);
            }
            swing(pose, player, partialTick, arm);
            renderLantern(player, event.getItemStack(), arm, pose, event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            pose.popPose();
        }
        event.setCanceled(true);
    }

    /** Apply pendulum motion about the grip, before drawing the hanging lantern. */
    public static void swing(PoseStack pose, LivingEntity entity, float partialTick, HumanoidArm arm) {
        float time = entity.tickCount + partialTick;
        float walking = Mth.clamp(entity.walkAnimation.speed(partialTick), 0, 1);
        float stride = entity.walkAnimation.position(partialTick) * 0.8F;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 0.08F) * 1.5F + Mth.sin(stride) * walking * 12));
        pose.mulPose(Axis.ZP.rotationDegrees(side * (Mth.sin(time * 0.065F) * 1.2F + Mth.cos(stride) * walking * 5)));
    }

    /** The origin is the center of the ring, in a coordinate system with positive Y upwards. */
    public static void renderLantern(LivingEntity entity, ItemStack stack, HumanoidArm arm, PoseStack pose,
                                     MultiBufferSource buffers, int light) {
        pose.pushPose();
        try {
            pose.mulPose(Axis.YP.rotationDegrees(arm == HumanoidArm.RIGHT ? -25 : 25));
            pose.scale(0.68F, 0.68F, 0.68F);
            // ItemRenderer centers model coordinates on (8,8,8); the ring grip is at (8,13,8).
            pose.translate(0, -5F / 16F, 0);
            Minecraft.getInstance().getItemRenderer().renderStatic(entity, stack, ItemDisplayContext.NONE,
                arm == HumanoidArm.LEFT, pose, buffers, entity.level(), light, OverlayTexture.NO_OVERLAY, entity.getId());
        } finally {
            pose.popPose();
        }
    }
}
