package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Wrist-anchored iron door, carried at the side and raised across the chest. @author NingMeng123 */
public final class DoorShieldLayer extends RenderLayer<Zombie,ZombieModel<Zombie>> {
    public DoorShieldLayer(RenderLayerParent<Zombie,ZombieModel<Zombie>> parent){super(parent);}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,Zombie entity,float limb,
            float amount,float partial,float age,float yaw,float pitch){
        if(entity.isInvisible()||!entity.getMainHandItem().is(Items.IRON_DOOR))return;
        pose.pushPose();
        if(getParentModel().young){pose.translate(0,.75,0);pose.scale(.5F,.5F,.5F);}
        boolean right=entity.getMainArm()==HumanoidArm.RIGHT;
        float side=right?1F:-1F;
        float guard=entity instanceof ExtremeMobs.ZombieVariant zombie?zombie.getDoorGuardProgress(partial):0F;
        var arm=right?getParentModel().rightArm:getParentModel().leftArm;
        // Use the actual animated wrist, including walking and melee motion. Undo the
        // arm's rotation at the grip so the heavy door stays upright as it is lifted.
        arm.translateAndRotate(pose);
        pose.translate(right?-.0625:.0625,.60,-.02);
        pose.mulPose(Axis.XP.rotation(-arm.xRot));
        pose.mulPose(Axis.YP.rotation(-arm.yRot));
        pose.mulPose(Axis.ZP.rotation(-arm.zRot));
        pose.mulPose(Axis.YP.rotation(side*Mth.lerp(guard,-.55F,.03F)));
        pose.mulPose(Axis.ZP.rotation(side*Mth.lerp(guard,.09F,.015F)));
        pose.mulPose(Axis.XP.rotation(Mth.lerp(guard,-.08F,0F)));
        pose.scale(.70F,-.70F,.70F);
        // NORTH's closed door occupies z=13/16..1. Put its inner face ahead
        // of the fingers, with the grip near the hinge edge and the panel seam.
        pose.translate(right?-.16:-.84,-.95,-1.10);
        var renderer=Minecraft.getInstance().getBlockRenderer();
        var lower=Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
        renderer.renderSingleBlock(lower,pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.translate(0,1,0);
        renderer.renderSingleBlock(lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
