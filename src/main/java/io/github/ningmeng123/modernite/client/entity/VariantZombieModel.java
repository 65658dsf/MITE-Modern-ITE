package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.modernite.entity.ExtremeKind;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.Zombie;

/** Species silhouettes and the strong zombie's two-arm uppercut. @author NingMeng123 */
public final class VariantZombieModel extends ZombieModel<Zombie> {
    private final ExtremeKind kind;

    public VariantZombieModel(ModelPart root,ExtremeKind kind){super(root);this.kind=kind;}

    public static LayerDefinition headlessLayer(){
        var mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);
        mesh.getRoot().addOrReplaceChild("head",CubeListBuilder.create(),PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64);
    }

    @Override public void setupAnim(Zombie entity,float limb,float amount,float age,float yaw,float pitch){
        super.setupAnim(entity,limb,amount,age,yaw,pitch);
        // Models are shared by every entity of this species; restore this before applying its pose.
        head.xScale=head.yScale=head.zScale=kind==ExtremeKind.FISHING_ROD_ZOMBIE?2F:1F;
        hat.xScale=hat.yScale=hat.zScale=head.xScale;
        if(kind==ExtremeKind.ZOMBIE_DOOR){
            ModelPart shieldArm=entity.getMainArm()==HumanoidArm.RIGHT?rightArm:leftArm;
            shieldArm.xRot=-1.1F;shieldArm.yRot=entity.getMainArm()==HumanoidArm.RIGHT?-.45F:.45F;
            shieldArm.zRot=0;
        }
        if(kind==ExtremeKind.ZOMBIE_FLY_AWAY&&attackTime>0){
            // Both arms swing together from low guard to overhead, like an iron golem.
            float uppercut=-2F+1.5F*Mth.triangleWave((1F-attackTime)*10F,10F);
            rightArm.xRot=leftArm.xRot=uppercut;
            rightArm.yRot=leftArm.yRot=0;rightArm.zRot=leftArm.zRot=0;
            body.yRot=0;rightArm.x=-5;leftArm.x=5;rightArm.z=leftArm.z=0;
        }
    }
}
