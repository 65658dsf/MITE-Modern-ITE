package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.modernite.entity.ExtremeKind;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;

/** Distinct sprinting, ender-like and orbiting-chaos silhouettes. @author NingMeng123 */
public final class VariantCreeperModel extends CreeperModel<Creeper> {
    private final ExtremeKind kind;
    public VariantCreeperModel(ModelPart root,ExtremeKind kind){super(root);this.kind=kind;}

    public static LayerDefinition layer(ExtremeKind kind){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        boolean phase=kind==ExtremeKind.PHASE_CREEPER,run=kind==ExtremeKind.RUN_CREEPER;
        root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8),
                PartPose.offset(0,phase?0:run?5:6,run?-3:0));
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(16,16)
                .addBox(phase?-2:-4,0,phase?-1.5F:-2,phase?4:8,phase?14:12,phase?3:4),
                PartPose.offset(0,phase?0:6,0));
        for(int i=0;i<4;i++){
            String name=switch(i){case 0->"right_hind_leg";case 1->"left_hind_leg";case 2->"right_front_leg";default->"left_front_leg";};
            float width=phase?2:run?3:4,length=phase?10:run?8:6;
            root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(0,16).addBox(-width/2,0,-width/2,width,length,width),
                    PartPose.offset(i%2==0?-2:2,24-length,i<2?3:-3));
        }
        if(kind==ExtremeKind.CHAOS_CREEPER){
            var ring=root.addOrReplaceChild("chaos_arrow",CubeListBuilder.create(),PartPose.offset(0,-5.5F,0));
            // A broken circular arrow floats above the head; its tip clearly marks rotation.
            ring.addOrReplaceChild("north",CubeListBuilder.create().texOffs(0,0).addBox(-5,-.4F,-6,10,.8F,1),PartPose.ZERO);
            ring.addOrReplaceChild("east",CubeListBuilder.create().texOffs(0,0).addBox(5,-.4F,-5,1,.8F,10),PartPose.ZERO);
            ring.addOrReplaceChild("south",CubeListBuilder.create().texOffs(0,0).addBox(-5,-.4F,5,10,.8F,1),PartPose.ZERO);
            ring.addOrReplaceChild("west",CubeListBuilder.create().texOffs(0,0).addBox(-6,-.4F,-1,1,.8F,6),PartPose.ZERO);
            ring.addOrReplaceChild("tip_left",CubeListBuilder.create().texOffs(0,0).addBox(0,-.5F,-.5F,4,1,1),PartPose.offsetAndRotation(-5.5F,-.1F,-2,0,-.75F,0));
            ring.addOrReplaceChild("tip_right",CubeListBuilder.create().texOffs(0,0).addBox(-4,-.5F,-.5F,4,1,1),PartPose.offsetAndRotation(-5.5F,-.1F,-2,0,.75F,0));
        }
        return LayerDefinition.create(mesh,64,32);
    }

    public ModelPart chaosArrow(){return root().getChild("chaos_arrow");}

    @Override public void setupAnim(Creeper entity,float limb,float amount,float age,float yaw,float pitch){
        root().getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity,limb,amount,age,yaw,pitch);
        if(kind==ExtremeKind.RUN_CREEPER){
            root().getChild("body").xRot=.25F+amount*.2F;
            root().getChild("head").xRot-=.15F+amount*.12F;
            String[] legs={"right_hind_leg","left_hind_leg","right_front_leg","left_front_leg"};
            for(int i=0;i<legs.length;i++)root().getChild(legs[i]).xRot=Mth.cos(limb*1.05F+(i==0||i==3?0:Mth.PI))*2.1F*amount;
        }
        if(kind==ExtremeKind.PHASE_CREEPER){
            root().y=Mth.sin(age*.09F)*.7F;
            root().getChild("body").zRot=Mth.sin(age*.08F)*.025F;
        }
        if(kind==ExtremeKind.CHAOS_CREEPER){
            ModelPart arrow=chaosArrow();arrow.visible=false;
            arrow.xRot=.45F;arrow.yRot=age*.14F;arrow.zRot=Mth.sin(age*.11F)*.15F;
            root().getChild("head").zRot=Mth.sin(age*.15F)*.09F;
        }
    }
}
