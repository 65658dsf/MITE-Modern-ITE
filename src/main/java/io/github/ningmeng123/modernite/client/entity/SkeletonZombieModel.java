package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.mite.client.FreshAnimationsModelDriver;
import java.util.function.Supplier;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.AbstractSkeleton;

/** Keeps the source zombie geometry and UVs while borrowing skeleton animation poses. @author NingMeng123 */
public final class SkeletonZombieModel extends SkeletonModel<AbstractSkeleton> {
    private final SkeletonModel<AbstractSkeleton> animationModel;
    private boolean freshAnimationsApplied;

    public SkeletonZombieModel(ModelPart nativeRoot, Supplier<ModelPart> animationRoot) {
        super(nativeRoot);
        // Bake after super: EMF associates the most recently baked root with Model's constructor.
        this.animationModel = new SkeletonModel<>(animationRoot.get());
    }

    @Override public void setupAnim(AbstractSkeleton entity, float limb, float amount, float age, float yaw, float pitch) {
        // Clear only a previously borrowed pose; an instance without FA keeps vanilla interpolation.
        if (freshAnimationsApplied) {
            head.resetPose();
            hat.resetPose();
            body.resetPose();
            rightArm.resetPose();
            leftArm.resetPose();
            rightLeg.resetPose();
            leftLeg.resetPose();
        }
        super.setupAnim(entity, limb, amount, age, yaw, pitch);
        copyPropertiesTo(animationModel);
        animationModel.swimAmount = swimAmount;
        animationModel.setupAnim(entity, limb, amount, age, yaw, pitch);
        freshAnimationsApplied = FreshAnimationsModelDriver.animate(entity, animationModel);
        if (freshAnimationsApplied) {
            // copyPropertiesTo only transfers pose/state, never skeleton cuboids or their 64x32 UVs.
            animationModel.copyPropertiesTo(this);
        }
        // The base mod applies Better Combat after setupAnim, so its attack still has final priority.
    }
}
