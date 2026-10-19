package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.mite.entity.ShieldBlockGoal;
import io.github.ningmeng123.modernite.entity.IntentShieldGuard;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the parent's installed goal identity while replacing only its guard decisions. @author NingMeng123 */
@Mixin(value = ShieldBlockGoal.class, remap = false)
public abstract class IntentShieldGoalMixin {
    @Shadow @Final private Mob mob;
    @Unique private IntentShieldGuard modernite$guard;

    @Unique private IntentShieldGuard modernite$guard() {
        if (modernite$guard == null) modernite$guard = new IntentShieldGuard(mob);
        return modernite$guard;
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void modernite$canUse(CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(modernite$guard().canUse()); }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void modernite$canContinue(CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(modernite$guard().canContinue()); }

    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void modernite$start(CallbackInfo ci) { modernite$guard().start(); ci.cancel(); }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void modernite$tick(CallbackInfo ci) { modernite$guard().tick(); ci.cancel(); }

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true)
    private void modernite$stop(CallbackInfo ci) { modernite$guard().stop(); ci.cancel(); }
}
