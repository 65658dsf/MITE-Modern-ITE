package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.entity.ArmorAuraRules;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Transparent starlight over the wearer's original armor material. @author NingMeng123 */
public final class VibraniumGildingLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation OUTER = ResourceLocation.fromNamespaceAndPath("mite", "textures/models/armor/vibranium_layer_1.png");
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath("mite", "textures/models/armor/vibranium_layer_2.png");
    private static final Field RENDER_LAYERS = renderLayersField();
    private final VibraniumCosmicModel<T> outer;
    private final VibraniumCosmicModel<T> inner;

    public VibraniumGildingLayer(RenderLayerParent<T, M> parent, ModelPart outer, ModelPart inner) {
        super(parent);
        this.outer = new VibraniumCosmicModel<>(outer);
        this.inner = new VibraniumCosmicModel<>(inner);
    }

    private static Field renderLayersField() {
        try {
            Field field = LivingEntityRenderer.class.getDeclaredField("layers");
            return field.trySetAccessible() ? field : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    /** Only defer from an actual armor layer that this renderer will follow with our own layer. */
    static boolean followsArmorLayer(LivingEntity entity, HumanoidArmorLayer<?, ?, ?> armorLayer) {
        if (RENDER_LAYERS == null) return false;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)
                || armorLayer.getParentModel() != living.getModel()) return false;
        try {
            // A cached registration flag would become stale when a renderer removes our layer.
            // Read the live list without retaining the renderer, model, layer, or entity globally.
            if (!(RENDER_LAYERS.get(living) instanceof List<?> layers)) return false;
            int armorIndex = layers.indexOf(armorLayer);
            if (armorIndex < 0) return false;
            for (int index = armorIndex + 1; index < layers.size(); index++) {
                if (layers.get(index) instanceof VibraniumGildingLayer<?, ?> gilding
                        && gilding.getParentModel() == living.getModel()) return true;
            }
        } catch (IllegalAccessException | RuntimeException unavailable) {
            // An inaccessible or unsupported renderer keeps its complete native glint path.
        }
        return false;
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
        VibraniumCosmicShaders.renderDeferredGlint(entity, buffers);
    }
}
