package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;

/** Keeps the species bucket visible when spawn rules leave its armor slot empty. @author NingMeng123 */
public final class BucketZombieLayer extends RenderLayer<Zombie, ZombieModel<Zombie>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
    private final BucketHelmetModel bucket = new BucketHelmetModel(BucketHelmetModel.layer().bakeRoot());

    public BucketZombieLayer(RenderLayerParent<Zombie, ZombieModel<Zombie>> parent) {
        super(parent);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Zombie zombie,
                                 float walk, float amount, float partialTick, float age, float yaw, float pitch) {
        if (zombie.isInvisible() || !zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        // This is appearance only: early births keep their empty slot, armor value and drops.
        // Copy the final animation and render as a humanoid so baby head transforms match armor.
        bucket.young = getParentModel().young;
        bucket.head.copyFrom(getParentModel().head);
        bucket.setAllVisible(false);
        bucket.head.visible = true;
        bucket.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
    }
}
