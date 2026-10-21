package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** A temporary bucket over the player's head, independent of equipped armor. @author NingMeng123 */
public final class BucketHeadLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    // A solid metal surface keeps the bucket closed around any helmet underneath.
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
    private final BucketHelmetModel bucket = new BucketHelmetModel(BucketHelmetModel.layer().bakeRoot());

    public BucketHeadLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                 float walk, float amount, float partialTick, float age, float yaw, float pitch) {
        if (!player.hasEffect(ExtremeEffects.VALUES.get("bucket_head"))) return;
        // Copy the final animated head, including crouch position and big-head scaling.
        bucket.head.copyFrom(getParentModel().head);
        bucket.head.render(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
    }
}
