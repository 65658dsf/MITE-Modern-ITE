package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;

/** Independently colored, continuously rotating arrow over the chaos creeper. @author NingMeng123 */
public final class ChaosArrowLayer extends RenderLayer<Creeper,CreeperModel<Creeper>> {
    private static final ResourceLocation TEXTURE=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    public ChaosArrowLayer(RenderLayerParent<Creeper,CreeperModel<Creeper>> parent){super(parent);}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,Creeper entity,float limb,
            float amount,float partial,float age,float yaw,float pitch){
        if(entity.isInvisible()||!(getParentModel() instanceof VariantCreeperModel model))return;
        var arrow=model.chaosArrow();arrow.visible=true;
        try{arrow.render(pose,buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE,false)),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0xFFCE6FFF);}
        finally{arrow.visible=false;}
    }
}
