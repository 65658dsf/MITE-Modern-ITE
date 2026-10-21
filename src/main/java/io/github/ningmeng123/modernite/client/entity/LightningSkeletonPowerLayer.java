package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;

/** Charged-creeper energy following the skeleton's animated bones. @author NingMeng123 */
public final class LightningSkeletonPowerLayer extends RenderLayer<AbstractSkeleton, SkeletonModel<AbstractSkeleton>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private final SkeletonModel<AbstractSkeleton> model;

    public LightningSkeletonPowerLayer(RenderLayerParent<AbstractSkeleton, SkeletonModel<AbstractSkeleton>> parent, ModelPart root) {
        super(parent);
        model = new SkeletonModel<>(root);
    }

    public static LayerDefinition layer() {
        var inflation = new CubeDeformation(2F);
        var mesh = HumanoidModel.createMesh(inflation, 0);
        var root = mesh.getRoot();
        // Preserve the thin skeleton limbs under the same inflation as creeper power armor.
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16)
                .addBox(-1, -2, -1, 2, 12, 2, inflation), PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror()
                .addBox(-1, -2, -1, 2, 12, 2, inflation), PartPose.offset(5, 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-1, 0, -1, 2, 12, 2, inflation), PartPose.offset(-2, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-1, 0, -1, 2, 12, 2, inflation), PartPose.offset(2, 12, 0));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractSkeleton skeleton,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (skeleton.isInvisible()) return;
        // Copy the final pose so bow aiming, walking and other model adjustments stay aligned.
        getParentModel().copyPropertiesTo(model);
        float offset = (skeleton.tickCount + partialTick) * .01F % 1F;
        var vertices = buffers.getBuffer(RenderType.energySwirl(TEXTURE, offset, offset));
        model.renderToBuffer(pose, vertices, packedLight, OverlayTexture.NO_OVERLAY, 0xFF808080);
    }
}
