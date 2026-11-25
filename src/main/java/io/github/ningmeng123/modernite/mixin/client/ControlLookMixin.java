package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.github.ningmeng123.modernite.content.Rabies;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Synchronized control effects modify the native mouse turn. @author NingMeng123 */
@Mixin(MouseHandler.class)
public abstract class ControlLookMixin {
    @ModifyArgs(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void modernite$adjustLook(Args args, double frameSeconds) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;
        if (Rabies.active(player)) {
            args.set(0, 0D);
            args.set(1, 0D);
            return;
        }
        if (player.hasEffect(ExtremeEffects.VALUES.get("fast_sensitivity"))) {
            args.set(0, (double)args.get(0) * 12D);
            args.set(1, (double)args.get(1) * 12D);
        }
        if (TimeDilation.active(player)) {
            args.set(0, (double)args.get(0) / TimeDilation.TICK_DIVISOR);
            args.set(1, (double)args.get(1) / TimeDilation.TICK_DIVISOR);
        }
        if (minecraft.isPaused() || !player.isAlive() || player.isSpectator() || minecraft.getCameraEntity() != player) return;
        var lookUp = player.getEffect(ExtremeEffects.VALUES.get("look_up"));
        if (lookUp == null || lookUp.endsWithin(0)) return;

        // Cap the pull at the pitch limit so downward input works even when already looking straight up.
        double pullDegrees = Math.min(Math.max(0D, player.getXRot() + 90D), 45D * Mth.clamp(frameSeconds, 0D, .1D));
        double pullInput = pullDegrees / .15D; // Entity.turn converts input units to degrees.
        double pitchInput = args.get(1);
        // Even a small downward movement remains responsive while resisting the pull.
        if (pitchInput > 0D) pullInput = Math.min(pullInput, pitchInput * .5D);
        args.set(1, pitchInput - pullInput);
    }
}
