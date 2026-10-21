package io.github.ningmeng123.modernite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.content.ResurrectionBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;

/** A glowing, slowly turning likeness of the array's owner, without spawning an entity. @author NingMeng123 */
public final class ResurrectionArrayRenderer implements BlockEntityRenderer<ResurrectionBlockEntity> {
    // Leave room above the head and beneath the closed, one-block-high glass cover.
    private static final float SCALE = 0.27F;
    private static final float TURN_TICKS = 1200F;
    private final ModelPart wideRoot;
    private final ModelPart slimRoot;
    private final PlayerModel<LivingEntity> wideModel;
    private final PlayerModel<LivingEntity> slimModel;

    public ResurrectionArrayRenderer(BlockEntityRendererProvider.Context context) {
        wideRoot = context.bakeLayer(ModelLayers.PLAYER);
        slimRoot = context.bakeLayer(ModelLayers.PLAYER_SLIM);
        wideModel = new PlayerModel<>(wideRoot, false);
        slimModel = new PlayerModel<>(slimRoot, true);
    }

    @Override
    public void render(ResurrectionBlockEntity array, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int overlay) {
        ResolvableProfile owner = array.getOwnerProfile();
        if (owner == null) return;
        Minecraft mc = Minecraft.getInstance();
        PlayerSkin skin;
        // Prefer the live player so changes of skin and client skin integrations remain visible.
        if (mc.level != null && mc.level.getPlayerByUUID(owner.gameProfile().getId()) instanceof AbstractClientPlayer player) {
            skin = player.getSkin();
        } else {
            var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(owner.gameProfile().getId());
            skin = info != null ? info.getSkin() : mc.getSkinManager().getInsecureSkin(owner.gameProfile());
        }
        float ticks = array.getLevel() == null ? 0 : array.getLevel().getGameTime() % (long)TURN_TICKS + partialTick;
        renderPortrait(skin, ticks, pose, buffers, packedLight, overlay);
    }

    void renderPortrait(PlayerSkin skin, float ticks, PoseStack pose, MultiBufferSource buffers,
                        int packedLight, int overlay) {
        boolean slim = skin.model() == PlayerSkin.Model.SLIM;
        ModelPart root = slim ? slimRoot : wideRoot;
        PlayerModel<LivingEntity> model = slim ? slimModel : wideModel;
        root.getAllParts().forEach(ModelPart::resetPose);
        model.young = false;
        model.setAllVisible(true);
        float breath = Mth.sin(ticks * Mth.TWO_PI / 120F);
        model.head.xRot = -0.035F;
        model.rightArm.zRot = 0.07F + breath * 0.015F;
        model.leftArm.zRot = -model.rightArm.zRot;
        model.hat.copyFrom(model.head);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        model.jacket.copyFrom(model.body);

        pose.pushPose();
        try {
            pose.translate(0.5F, 0.40F + breath * 0.015F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees((ticks % TURN_TICKS) * 360F / TURN_TICKS));
            // Native player meshes have their feet at y=24/16 and use a downward Y axis.
            pose.scale(-SCALE, -SCALE, SCALE);
            pose.translate(0, -1.5F, 0);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture())),
                LightTexture.FULL_BRIGHT, overlay);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(ResurrectionBlockEntity array) {
        var pos = array.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1.25, pos.getZ() + 1);
    }
}
