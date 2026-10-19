package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/** A relaxed carrying arm, applied before player sleeves copy their native arm pose. @author NingMeng123 */
public final class LanternArmPose {
    private LanternArmPose() {}

    public static void apply(HumanoidModel<?> model, LivingEntity entity, float walk, float amount, float age) {
        if (!(entity instanceof Player player) || player.isSpectator()) return;
        for (HumanoidArm arm : HumanoidArm.values()) {
            if (!canCarry(entity, arm)) continue;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            float stride = Mth.cos(walk * 0.6662F + (side == 1 ? Mth.PI : 0)) * amount;
            part.xRot = -0.70F + stride * 0.12F + (model.crouching ? 0.25F : 0);
            part.yRot = -side * 0.08F;
            part.zRot = -side * (0.10F + Mth.sin(age * 0.07F) * 0.015F);
        }
    }

    public static boolean canCarry(LivingEntity entity, HumanoidArm arm) {
        InteractionHand hand = arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (!(entity.getItemInHand(hand).getItem() instanceof DynamicCoreItem)
            || entity.isVisuallySwimming() || entity.isFallFlying() || entity.isAutoSpinAttack()) return false;
        if (entity.swinging && entity.swingingArm == hand) return false;
        if (entity.isUsingItem()) {
            UseAnim animation = entity.getUseItem().getUseAnimation();
            if (animation == UseAnim.BOW || animation == UseAnim.CROSSBOW || animation == UseAnim.BLOCK
                || animation == UseAnim.SPEAR || animation == UseAnim.SPYGLASS) return false;
        }
        return !chargedCrossbow(entity.getMainHandItem()) && !chargedCrossbow(entity.getOffhandItem());
    }

    private static boolean chargedCrossbow(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack);
    }
}
