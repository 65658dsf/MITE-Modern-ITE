package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.PlayerTimeDilationActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse-wheel item changes are input actions, not an independent real-time bypass. @author NingMeng123 */
@Mixin(MouseHandler.class)
public abstract class PlayerTimeDilationMouseMixin {
    @Shadow private void onScroll(long window, double x, double y) {}

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void modernite$slowWheel(long window, double x, double y, CallbackInfo ci) {
        if (Minecraft.getInstance().screen == null
                && PlayerTimeDilationActions.defer(null, true, () -> onScroll(window, x, y))) ci.cancel();
    }
}
