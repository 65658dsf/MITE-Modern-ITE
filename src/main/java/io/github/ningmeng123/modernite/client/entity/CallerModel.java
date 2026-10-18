package io.github.ningmeng123.modernite.client.entity;

import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Zombie;

/** Source ModelZombieCaller geometry and 64 by 80 UVs. @author NingMeng123 */
public final class CallerModel extends ZombieModel<Zombie> {
    public CallerModel(ModelPart root){super(root);}
    public static LayerDefinition layer(){
        var mesh=new MeshDefinition();var p=mesh.getRoot();
        p.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-9,-4,8,8,8,new CubeDeformation(1)),PartPose.offset(0,-8,.25F));
        p.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        p.addOrReplaceChild("body",CubeListBuilder.create().texOffs(4,20).addBox(-8,-17,-6,16,14,12,new CubeDeformation(3))
            .texOffs(15,46).addBox(-7,-12.5F,-8,14,9,2,new CubeDeformation(3)),PartPose.offset(0,12,0));
        p.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(33,48).addBox(0,-3,-3,6,19,6),PartPose.offset(11,-5,1));
        p.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(33,48).addBox(-6,-3,-3,6,19,6),PartPose.offset(-11,-5,1));
        p.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,62).addBox(-3.35F,0,-3,7,12,6),PartPose.offset(5.1F,12,0));
        p.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,62).addBox(-3.65F,0,-3,7,12,6),PartPose.offset(-5.1F,12,0));
        return LayerDefinition.create(mesh,64,80);
    }
    @Override public void setupAnim(Zombie entity,float limb,float amount,float age,float yaw,float pitch){
        head.xRot=pitch*Mth.DEG_TO_RAD;head.yRot=yaw*Mth.DEG_TO_RAD;
        rightArm.xRot=Mth.cos(limb*.6662F+Mth.PI)*amount;leftArm.xRot=Mth.cos(limb*.6662F)*amount;
        rightLeg.xRot=Mth.cos(limb*.6662F)*1.4F*amount;leftLeg.xRot=Mth.cos(limb*.6662F+Mth.PI)*1.4F*amount;
        if(entity.hurtTime>0)rightArm.xRot=leftArm.xRot=-2.9F*entity.hurtTime/10F;
        if(attackTime>0)rightArm.xRot-=Mth.sin(attackTime*Mth.PI)*1.8F;
        hat.copyFrom(head);
    }
}
