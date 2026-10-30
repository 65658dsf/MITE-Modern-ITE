package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.GemBoxEmfPoseCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve ISMAH's selected supporting arm through optional EMF callbacks. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.github.razorplay01.ismah.util.FirstPersonArmorRenderer", remap = false)
public abstract class GemBoxArmoredHandMixin {
    @Inject(method = "applyArmOnlyPose(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/model/geom/PartPose;)V",
            at = @At("TAIL"), require = 0)
    private static void modernite$preserveSupportingArmor(HumanoidModel<?> model, HumanoidArm arm, PartPose pose, CallbackInfo ci) {
        GemBoxEmfPoseCompat.captureFirstPersonArmor(model, arm);
    }
}
