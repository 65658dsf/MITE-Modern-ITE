package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.DefenseGuardFocus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply defensive attention after pursuit controls; both sides use the same bounded body turn. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class DefenseFacingMixin {
    @Unique private float modernite$previousGuardBodyYaw;
    @Unique private float modernite$guardHeadYaw;
    @Unique private float modernite$beforeLookHeadYaw;

    @Inject(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/LookControl;tick()V"))
    private void modernite$defensiveLook(CallbackInfo ci) {
        modernite$beforeLookHeadYaw = ((Mob)(Object)this).yHeadRot;
        DefenseGuardFocus.beforeLook((Mob)(Object)this);
    }

    @Inject(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/LookControl;tick()V", shift = At.Shift.AFTER))
    private void modernite$limitGuardHead(CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        if (DefenseGuardFocus.guarding(mob))
            mob.yHeadRot = Mth.approachDegrees(modernite$beforeLookHeadYaw, mob.yHeadRot, 25F);
    }

    @Inject(method = "tickHeadTurn", at = @At("HEAD"))
    private void modernite$rememberBodyYaw(float yaw, float distance, CallbackInfoReturnable<Float> cir) {
        modernite$previousGuardBodyYaw = ((Mob)(Object)this).yBodyRot;
        modernite$guardHeadYaw = ((Mob)(Object)this).yHeadRot;
    }

    @Inject(method = "tickHeadTurn", at = @At("RETURN"))
    private void modernite$turnGuardBody(float yaw, float distance, CallbackInfoReturnable<Float> cir) {
        Mob mob = (Mob)(Object)this;
        // Native moving-body clamping must not undo a bounded defensive look, including on the client.
        if (DefenseGuardFocus.guarding(mob)) mob.yHeadRot = modernite$guardHeadYaw;
        DefenseGuardFocus.afterBodyTurn(mob, modernite$previousGuardBodyYaw);
    }
}
