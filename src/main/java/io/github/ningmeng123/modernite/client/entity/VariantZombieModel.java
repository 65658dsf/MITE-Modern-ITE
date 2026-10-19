package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;

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
        if(kind==ExtremeKind.ZOMBIE_DOOR&&entity.getMainHandItem().is(Items.IRON_DOOR)){
            boolean right=entity.getMainArm()==HumanoidArm.RIGHT;
            float side=right?1F:-1F;
            float partial=Mth.clamp(age-entity.tickCount,0F,1F);
            float guard=entity instanceof ExtremeMobs.ZombieVariant zombie?zombie.getDoorGuardProgress(partial):0F;
            ModelPart shieldArm=right?rightArm:leftArm;
            ModelPart freeArm=right?leftArm:rightArm;
            float walk=Mth.cos(limb*.6662F)*.12F*amount;
            float swing=Mth.sin(Mth.sqrt(attackTime)*Mth.PI);
            shieldArm.xRot=Mth.lerp(guard,-.55F+walk-swing*.65F,-1.10F);
            shieldArm.yRot=side*Mth.lerp(guard,.08F,-.45F);
            shieldArm.zRot=side*Mth.lerp(guard,.10F,.025F);
            // Keep the spare hand behind the panel while bracing it; outside
            // the guard retain the native zombie's walking and attack animation.
            freeArm.xRot=Mth.lerp(guard,freeArm.xRot,-.95F);
            freeArm.yRot=Mth.lerp(guard,freeArm.yRot,side*.45F);
            freeArm.zRot=Mth.lerp(guard,freeArm.zRot,-side*.05F);
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
