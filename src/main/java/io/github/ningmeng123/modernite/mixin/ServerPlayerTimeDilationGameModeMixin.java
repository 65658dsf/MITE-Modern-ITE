package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mining advances on the player's clock without adding a mining-fatigue penalty. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerTimeDilationGameModeMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void modernite$miningClock(CallbackInfo ci) {
        if (!TimeDilation.playerStep(player)) ci.cancel();
    }
}
