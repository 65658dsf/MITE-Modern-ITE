package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.PlayerTimeDilationRendering;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Camera interpolation spans all five world ticks instead of moving once and freezing four times. @author NingMeng123 */
@Mixin(Camera.class)
public abstract class PlayerTimeDilationCameraMixin {
    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void modernite$position(Args args, BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial) {
        if (!(entity instanceof Player player) || !TimeDilation.active(player)) return;
        var position = PlayerTimeDilationRendering.position(player, partial);
        double eye = (double) args.get(1) - Mth.lerp(partial, player.yo, player.getY());
        args.set(0, position.x); args.set(1, position.y + eye); args.set(2, position.z);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void modernite$eyeHeight(CallbackInfo ci) {
        if (!TimeDilation.playerStep(Minecraft.getInstance().player)) ci.cancel();
    }
}
