package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.mite.client.mob.MobModelLayers;
import io.github.ningmeng123.mite.client.mob.SourceCreeperModel;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;

/** Keeps charged source creepers on their own 64x32 mesh and armour atlas. @author NingMeng123 */
final class SourceCreeperPowerLayer extends EnergySwirlLayer<Creeper, CreeperModel<Creeper>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("mite", "textures/entity/creeper/creeper_armor.png");
    private final SourceCreeperModel model;

    SourceCreeperPowerLayer(RenderLayerParent<Creeper, CreeperModel<Creeper>> parent, EntityRendererProvider.Context context) {
        super(parent);
        model = new SourceCreeperModel(context.bakeLayer(MobModelLayers.SOURCE_CREEPER_ARMOR),
                () -> context.bakeLayer(ModelLayers.CREEPER));
    }

    @Override protected float xOffset(float age) { return age * .01F; }
    @Override protected ResourceLocation getTextureLocation() { return TEXTURE; }
    @Override protected EntityModel<Creeper> model() { return model; }
}
