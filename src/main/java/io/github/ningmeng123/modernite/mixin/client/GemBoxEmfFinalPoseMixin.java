package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.GemBoxEmfPoseCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run after FA, EMF Compat and Touch'n Motion finish their draw-time poses. @author NingMeng123 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
public abstract class GemBoxEmfFinalPoseMixin {
    @Inject(method = "animate()V", at = @At("TAIL"), require = 0)
    private void modernite$restoreCarryingArms(CallbackInfo ci) {
        GemBoxEmfPoseCompat.restore(this);
    }
}
