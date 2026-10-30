package io.github.ningmeng123.modernite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemBoxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Vector3f;

/** Both real skin hands support one solid chest, with a gentle lifting and carrying motion. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class GemBoxCarryRendering {
    private GemBoxCarryRendering() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        if (GemBoxArmPose.hiddenForMovement(player)) {
            if (event.getItemStack().is(ExtremeGear.GEM_BOX.get())) event.setCanceled(true);
            return;
        }
        if (!GemBoxArmPose.active(player)) return;
        InteractionHand carrying = GemBoxArmPose.carryingHand(player);
        // Vanilla posts each hand separately. The other hand must not draw another item or chest.
        event.setCanceled(true);
        if (event.getHand() != carrying) return;
        float partial = event.getPartialTick();
        float age = player.tickCount + partial;
        float walking = Mth.clamp(player.walkAnimation.speed(partial), 0, 1);
        float stride = player.walkAnimation.position(partial);
        float lift = Mth.sin(Mth.sqrt(Mth.clamp(event.getSwingProgress(), 0, 1)) * Mth.PI);
        float openingLift = Mth.clamp(GemBoxItem.openingProgress(player.getItemInHand(carrying), player.level(), partial)
                * GemBoxItem.OPEN_TICKS / 4F, 0F, 1F);
        float elapsed = GemBoxItem.openingProgress(player.getItemInHand(carrying), player.level(), partial) * GemBoxItem.OPEN_TICKS;
        float reach = ease(elapsed, 16, 20);
        float extraction = ease(elapsed, 36, 48);
        Vector3f rightGrip = rightGrip(elapsed);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            // Spread and lift the supporting hands beside the chest at its original size and distance.
            pose.translate(Mth.sin(stride * .8F) * walking * .012F,
                    -.32F - event.getEquipProgress() * .7F + lift * .055F + openingLift * .035F
                            + Mth.sin(age * .09F) * .006F + Mth.cos(stride * 1.6F) * walking * .012F,
                    -1.18F + event.getEquipProgress() * .10F - lift * .035F - openingLift * .02F);
            pose.mulPose(Axis.XP.rotationDegrees(6F + Mth.sin(age * .07F) * .65F));
            pose.mulPose(Axis.YP.rotationDegrees(-9F + Mth.sin(stride * .8F) * walking * 1.2F));
            if (!player.isInvisible()) {
                renderSupportHand(mc, event, HumanoidArm.RIGHT, rightGrip, reach * (1 - extraction), extraction);
                renderSupportHand(mc, event, HumanoidArm.LEFT, new Vector3f(-.43F, -.18F, .22F), 0, 0);
            }
            pose.pushPose();
            try {
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.scale(.72F, .72F, .72F);
                GemBoxModelRendering.render(player, player.getItemInHand(carrying), pose,
                        event.getMultiBufferSource(), event.getPackedLight());
            } finally {
                pose.popPose();
            }
            var gem = GemBoxItem.revealedGem(player.getItemInHand(carrying));
            if (elapsed >= GemBoxItem.REVEAL_TICK && !gem.isEmpty()) {
                pose.pushPose();
                try {
                    pose.translate(rightGrip.x, rightGrip.y + .13F, rightGrip.z - .015F);
                    pose.mulPose(Axis.YP.rotationDegrees(-12));
                    pose.mulPose(Axis.ZP.rotationDegrees(-9));
                    pose.scale(.45F, .45F, .45F);
                    mc.getItemRenderer().renderStatic(player, gem, ItemDisplayContext.NONE, false, pose,
                            event.getMultiBufferSource(), player.level(), event.getPackedLight(), OverlayTexture.NO_OVERLAY, player.getId());
                } finally { pose.popPose(); }
            }
        } finally {
            pose.popPose();
        }
    }

    private static float ease(float elapsed, float from, float to) {
        float t = Mth.clamp((elapsed - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static Vector3f rightGrip(float elapsed) {
        if (elapsed < 16) return new Vector3f(.43F, -.18F, .22F);
        if (elapsed < 20) {
            float t = ease(elapsed, 16, 20);
            return new Vector3f(Mth.lerp(t, .43F, .055F), Mth.lerp(t, -.18F, .15F), Mth.lerp(t, .22F, -.025F));
        }
        if (elapsed < 24) return new Vector3f(.055F, Mth.lerp(ease(elapsed, 20, 24), .15F, -.065F), -.025F);
        if (elapsed < 36) {
            float wave = (elapsed - 24) * .8F;
            return new Vector3f(.055F + Mth.sin(wave) * .055F, -.065F + Mth.sin(wave * 1.5F) * .018F,
                    -.025F + Mth.cos(wave) * .035F);
        }
        if (elapsed < 40) return new Vector3f(Mth.lerp(ease(elapsed, 36, 40), .055F, .12F),
                Mth.lerp(ease(elapsed, 36, 40), -.065F, .18F), Mth.lerp(ease(elapsed, 36, 40), -.025F, .06F));
        float t = ease(elapsed, 40, 48);
        return new Vector3f(Mth.lerp(t, .12F, .32F), Mth.lerp(t, .18F, .38F), Mth.lerp(t, .06F, .32F));
    }

    private static void renderSupportHand(Minecraft mc, RenderHandEvent event, HumanoidArm arm, Vector3f target,
            float searching, float extraction) {
        var player = mc.player;
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        var model = renderer.getModel();
        try (GemBoxRenderState saved = ((GemBoxRenderState.Owner)renderer).modernite$captureGemBoxRenderState()) {
            GemBoxEmfPoseCompat.clear(model);
            // Match PlayerRenderer.renderHand's setup before resolving the actual slim/normal palm.
            model.attackTime = 0;
            model.crouching = false;
            model.swimAmount = 0;
            model.setupAnim(player, 0, 0, 0, 0, 0);
            var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            part.xRot = 0;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            PoseStack orientation = new PoseStack();
            orientation.mulPose(Axis.ZP.rotationDegrees(side * 25F - searching * 60F));
            orientation.mulPose(Axis.XP.rotationDegrees(130F - searching * 95F - extraction * 20F));
            orientation.scale(1, -1, -1);
            PoseStack palm = new PoseStack();
            palm.mulPose(orientation.last().pose());
            model.translateToHand(arm, palm);
            Vector3f grip = palm.last().pose().transformPosition(-side / 16F, 9F / 16F, 0, new Vector3f());
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            try {
                pose.translate(target.x - grip.x, target.y - grip.y, target.z - grip.z);
                pose.mulPose(orientation.last().pose());
                if (arm == HumanoidArm.RIGHT)
                    renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
                else renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
            } finally {
                pose.popPose();
            }
        }
    }
}
