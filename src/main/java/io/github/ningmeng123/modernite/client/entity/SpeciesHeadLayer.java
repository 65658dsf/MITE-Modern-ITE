package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.dragon.DragonHeadModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Zombie;

/** Real ghast and dragon textures accompany their own head geometry. @author NingMeng123 */
public final class SpeciesHeadLayer extends RenderLayer<Zombie,ZombieModel<Zombie>> {
    private static final ResourceLocation GHAST=ResourceLocation.withDefaultNamespace("textures/entity/ghast/ghast.png");
    private static final ResourceLocation DRAGON=ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private final ExtremeKind kind;
    private final ModelPart ghast;
    private final DragonHeadModel dragon;

    public SpeciesHeadLayer(RenderLayerParent<Zombie,ZombieModel<Zombie>> parent,ExtremeKind kind,ModelPart root){
        super(parent);this.kind=kind;ghast=kind==ExtremeKind.ZOMBIE_GHAST?root:null;
        dragon=kind==ExtremeKind.DRAGON_ZOMBIE?new DragonHeadModel(root):null;
    }

    public static LayerDefinition ghastLayer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-8,-8,-8,16,16,16),PartPose.ZERO);
        for(int i=0;i<9;i++){
            int length=3+i%3;
            root.addOrReplaceChild("tentacle_"+i,CubeListBuilder.create().texOffs(0,0).addBox(-1,0,-1,2,length,2),
                    PartPose.offset((i%3-1)*5F,7,(i/3-1)*5F));
        }
        return LayerDefinition.create(mesh,64,32);
    }

    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,Zombie entity,float limb,
            float amount,float partial,float age,float yaw,float pitch){
        if(entity.isInvisible())return;
        pose.pushPose();
        if(getParentModel().young){pose.translate(0,.75,0);pose.scale(.5F,.5F,.5F);}
        getParentModel().head.translateAndRotate(pose);
        if(ghast!=null){
            pose.translate(0,-.32,0);pose.scale(.64F,.64F,.64F);
            for(int i=0;i<9;i++)ghast.getChild("tentacle_"+i).xRot=.18F+.13F*Mth.sin(age*.13F+i);
            ghast.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(GHAST)),light,OverlayTexture.NO_OVERLAY);
        }else{
            pose.translate(0,-.12,0);pose.scale(.72F,.72F,.72F);
            // Head orientation already comes from the humanoid attachment point.
            dragon.setupAnim(age,0,0);
            dragon.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(DRAGON)),light,OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }
}
