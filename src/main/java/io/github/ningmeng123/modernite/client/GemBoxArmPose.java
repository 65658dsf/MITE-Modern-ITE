package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemBoxItem;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import org.joml.Vector3f;

/** Two palms support a single box while native movement and item-use poses retain priority. @author NingMeng123 */
public final class GemBoxArmPose {
    private static final double MAX_CARRY_SPEED_SQUARED = .24D * .24D;
    // Player models are reused serially on the render thread; weak keys do not retain reloaded renderers.
    private static final Map<HumanoidModel<?>, Vector3f> SUPPORT_ANCHORS = new WeakHashMap<>();

    private GemBoxArmPose() {}

    /** Main hand wins when both slots contain a box; this never changes either actual slot. */
    public static InteractionHand carryingHand(LivingEntity entity) {
        if (entity.getMainHandItem().is(ExtremeGear.GEM_BOX.get())) return InteractionHand.MAIN_HAND;
        if (entity.getOffhandItem().is(ExtremeGear.GEM_BOX.get())) return InteractionHand.OFF_HAND;
        return null;
    }

    public static boolean active(LivingEntity entity) {
        return entity instanceof Player player && !player.isSpectator() && carryingHand(entity) != null
                && !hiddenForMovement(entity)
                && !entity.isSleeping() && !entity.isVisuallySwimming() && !entity.isFallFlying()
                && !entity.isAutoSpinAttack() && !entity.onClimbable() && !entity.isUsingItem()
                && !CrossbowItem.isCharged(entity.getMainHandItem()) && !CrossbowItem.isCharged(entity.getOffhandItem())
                && !entity.swinging;
    }

    /** Hide hand-held boxes during travel that cannot use the relaxed two-handed carry. */
    public static boolean hiddenForMovement(LivingEntity entity) {
        if (!(entity instanceof Player) || carryingHand(entity) == null) return false;
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        return entity.isSprinting() || !entity.onGround() || entity.isPassenger()
                || entity.isVisuallySwimming() || entity.isFallFlying() || entity.isAutoSpinAttack()
                || entity.onClimbable() || dx * dx + dz * dz > MAX_CARRY_SPEED_SQUARED
                || entity.getDeltaMovement().horizontalDistanceSqr() > MAX_CARRY_SPEED_SQUARED;
    }

    public static void apply(HumanoidModel<?> model, LivingEntity entity, float walk, float amount, float age) {
        if (!active(entity)) {
            SUPPORT_ANCHORS.remove(model);
            return;
        }
        float walking = Mth.clamp(amount, 0F, 1F);
        float breath = Mth.sin(age * .085F) * .018F;
        float step = Mth.cos(walk * 1.3324F) * walking * .035F;
        float lift = -1.22F + breath + step + (model.crouching ? .15F : 0F);
        model.rightArm.xRot = lift;
        model.leftArm.xRot = lift;
        model.rightArm.yRot = -.20F;
        model.leftArm.yRot = .20F;
        model.rightArm.zRot = -.12F - breath * .35F;
        model.leftArm.zRot = .12F + breath * .35F;
        model.rightArm.z = 0F;
        model.leftArm.z = 0F;

        // Freeze the two-handed support point before the right hand leaves it to search inside.
        SUPPORT_ANCHORS.put(model, GemBoxThirdPersonRendering.palm(model, HumanoidArm.RIGHT)
                .add(GemBoxThirdPersonRendering.palm(model, HumanoidArm.LEFT)).mul(.5F));
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float tick = GemBoxOpeningMotion.elapsed(GemBoxItem.openingProgress(
                entity.getItemInHand(carryingHand(entity)), entity.level(), partialTick));
        float reaching = GemBoxOpeningMotion.reach(tick);
        float searching = GemBoxOpeningMotion.rummage(tick);
        float withdrawing = GemBoxOpeningMotion.extraction(tick);
        float pitch = Mth.lerp(reaching, lift, -1.60F)
                + Mth.sin((tick - 16F) * .85F) * searching * .07F;
        float yaw = Mth.lerp(reaching, -.20F, -.47F)
                + Mth.sin((tick - 16F) * .55F) * searching * .11F;
        float roll = Mth.lerp(reaching, -.12F - breath * .35F, -.08F)
                + Mth.sin((tick - 16F) * .67F) * searching * .025F;
        model.rightArm.xRot = Mth.lerp(withdrawing, pitch, -1.95F + breath);
        model.rightArm.yRot = Mth.lerp(withdrawing, yaw, .08F);
        model.rightArm.zRot = Mth.lerp(withdrawing, roll, -.12F - breath * .35F);
        // Reach past the rear rim instead of searching in the air between the box and torso.
        model.rightArm.z = -reaching * (1F - withdrawing) * 2F;
    }

    public static Vector3f supportAnchor(HumanoidModel<?> model) {
        Vector3f saved = SUPPORT_ANCHORS.get(model);
        return saved != null ? new Vector3f(saved)
                : GemBoxThirdPersonRendering.palm(model, HumanoidArm.RIGHT)
                        .add(GemBoxThirdPersonRendering.palm(model, HumanoidArm.LEFT)).mul(.5F);
    }
}
