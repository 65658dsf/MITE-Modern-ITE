package io.github.ningmeng123.modernite.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;

/** An open-bottom inverted bucket with a rolled lip and carrying handle. @author NingMeng123 */
public final class BucketHelmetModel extends HumanoidModel<LivingEntity> {
    public BucketHelmetModel(ModelPart root){super(root);}
    public static LayerDefinition layer(){
        var mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);var root=mesh.getRoot();
        var bucket=CubeListBuilder.create().texOffs(0,0)
                .addBox(-6,-11,-6,12,1,12)
                .addBox(-6,-10,-6,12,9,1).addBox(-6,-10,5,12,9,1)
                .addBox(-6,-10,-5,1,9,10).addBox(5,-10,-5,1,9,10)
                .texOffs(0,0).addBox(-6.5F,-1.5F,-6.5F,13,1.5F,1)
                .addBox(-6.5F,-1.5F,5.5F,13,1.5F,1)
                .addBox(-6.5F,-1.5F,-5.5F,1,1.5F,11).addBox(5.5F,-1.5F,-5.5F,1,1.5F,11)
                .texOffs(0,0).addBox(-7,-8,-1,1,3,2).addBox(6,-8,-1,1,3,2)
                .addBox(-7.5F,-14,-.5F,1,7,1).addBox(6.5F,-14,-.5F,1,7,1)
                .addBox(-7.5F,-14.5F,-.5F,15,1,1);
        root.addOrReplaceChild("head",bucket,PartPose.ZERO);
        for(String name:new String[]{"hat","body","right_arm","left_arm","right_leg","left_leg"})root.addOrReplaceChild(name,CubeListBuilder.create(),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,32);
    }
}
