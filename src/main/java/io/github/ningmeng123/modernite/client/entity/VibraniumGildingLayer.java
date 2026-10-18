package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.entity.ArmorAuraRules;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Complete cosmic armor surfaces, retaining only the native armor alpha mask. @author NingMeng123 */
public final class VibraniumGildingLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation OUTER = ResourceLocation.fromNamespaceAndPath("mite", "textures/models/armor/vibranium_layer_1.png");
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath("mite", "textures/models/armor/vibranium_layer_2.png");
    private final VibraniumCosmicModel<T> outer;
    private final VibraniumCosmicModel<T> inner;

    public VibraniumGildingLayer(RenderLayerParent<T, M> parent, ModelPart outer, ModelPart inner) {
        super(parent);
        this.outer = new VibraniumCosmicModel<>(outer);
        this.inner = new VibraniumCosmicModel<>(inner);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
            float limbSwing, float limbSwingAmount, float partialTick, float age,
            float headYaw, float headPitch) {
        if (!ArmorAuraRules.fullProtectionFive(entity)) return;
        M parent = getParentModel();
        float ticks = entity.tickCount % VibraniumCosmicShaders.PERIOD_TICKS + partialTick;
        inner.copyPose(parent, true);
        VibraniumCosmicShaders.render(pose, buffers, inner, INNER, light, ticks);
        outer.copyPose(parent, false);
        VibraniumCosmicShaders.render(pose, buffers, outer, OUTER, light, ticks);
        VibraniumLightWings.render(pose, buffers, parent, ticks, light);
    }
}
