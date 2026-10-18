package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Legacy glow masks use alpha, including white RGB in fully transparent pixels. @author NingMeng123 */
public final class SourceGlowLayer<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M> {
    private final Function<T,ResourceLocation> texture;

    public SourceGlowLayer(RenderLayerParent<T,M> renderer,Function<T,ResourceLocation> texture){
        super(renderer);
        this.texture=texture;
    }

    @Override
    public void render(PoseStack pose,MultiBufferSource buffers,int packedLight,T entity,float limbSwing,
            float limbSwingAmount,float partialTick,float ageInTicks,float netHeadYaw,float headPitch){
        if(entity.isInvisible())return;
        // Unlike the additive eyes shader, this pass discards alpha-zero pixels before blending.
        var vertices=buffers.getBuffer(RenderType.entityTranslucentEmissive(texture.apply(entity),false));
        getParentModel().renderToBuffer(pose,vertices,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
    }
}
