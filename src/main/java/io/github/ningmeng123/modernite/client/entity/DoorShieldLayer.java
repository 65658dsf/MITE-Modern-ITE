package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** A solid two-panel iron door held in front of the zombie as a large shield. @author NingMeng123 */
public final class DoorShieldLayer extends RenderLayer<Zombie,ZombieModel<Zombie>> {
    public DoorShieldLayer(RenderLayerParent<Zombie,ZombieModel<Zombie>> parent){super(parent);}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,Zombie entity,float limb,
            float amount,float partial,float age,float yaw,float pitch){
        if(entity.isInvisible()||!entity.getMainHandItem().is(Items.IRON_DOOR))return;
        pose.pushPose();
        if(getParentModel().young){pose.translate(0,.75,0);pose.scale(.5F,.5F,.5F);}
        getParentModel().body.translateAndRotate(pose);
        pose.translate(entity.getMainArm()==HumanoidArm.RIGHT?-.68:.03,1.18,-1.05);
        pose.scale(.65F,-.65F,.65F);
        var renderer=Minecraft.getInstance().getBlockRenderer();
        var lower=Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
        renderer.renderSingleBlock(lower,pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.translate(0,1,0);
        renderer.renderSingleBlock(lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
