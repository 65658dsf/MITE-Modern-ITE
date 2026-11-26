package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.DragonFinalNovaPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Final Nova's approach and charge available until the detonation has completed. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.enderdragonplus.dragon.phase.FinalNovaPhase", remap = false)
public abstract class EnderDragonPlusFinalNovaMixin implements DragonFinalNovaPhase {
    @Unique private boolean modernIte$finalNovaDetonated;

    @Inject(method = "start()Z", at = @At("HEAD"))
    private void modernIte$beginFinalNova(CallbackInfoReturnable<Boolean> cir) {
        modernIte$finalNovaDetonated = false;
    }

    @Inject(method = "detonate()V", at = @At("RETURN"))
    private void modernIte$endFinalNova(CallbackInfo ci) {
        modernIte$finalNovaDetonated = true;
    }

    @Override
    public boolean modernIte$isPreparingFinalNova() {
        return !modernIte$finalNovaDetonated;
    }
}
