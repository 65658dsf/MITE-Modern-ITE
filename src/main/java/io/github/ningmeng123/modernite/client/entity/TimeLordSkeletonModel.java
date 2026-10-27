package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.mite.client.FreshAnimationsModelDriver;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.AbstractSkeleton;

/** Use the installed skeleton CEM root and the same explicit FA driver as other addon skeletons. @author NingMeng123 */
public final class TimeLordSkeletonModel extends SkeletonModel<AbstractSkeleton> {
    private final ModelPart root;
    private boolean freshAnimationsApplied;

    public TimeLordSkeletonModel(ModelPart root) {
        super(root);
        this.root = root;
    }

    @Override public void setupAnim(AbstractSkeleton entity, float limb, float amount, float age, float yaw, float pitch) {
        // Clear the previous CEM pose before vanilla setup, including nested pack-specific bones.
        if (freshAnimationsApplied) root.getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity, limb, amount, age, yaw, pitch);
        freshAnimationsApplied = FreshAnimationsModelDriver.animate(entity, this);
    }

    public boolean freshAnimationsApplied() { return freshAnimationsApplied; }
}
