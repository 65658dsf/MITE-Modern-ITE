package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.PlayerTimeDilationActions;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Buffered shift/ctrl clicks keep the modifier state from when the input occurred. @author NingMeng123 */
@Mixin(Screen.class)
public abstract class PlayerTimeDilationModifiersMixin {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void modernite$shift(CallbackInfoReturnable<Boolean> cir) { Boolean value = PlayerTimeDilationActions.modifier(1); if (value != null) cir.setReturnValue(value); }
    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void modernite$control(CallbackInfoReturnable<Boolean> cir) { Boolean value = PlayerTimeDilationActions.modifier(2); if (value != null) cir.setReturnValue(value); }
    @Inject(method = "hasAltDown", at = @At("HEAD"), cancellable = true)
    private static void modernite$alt(CallbackInfoReturnable<Boolean> cir) { Boolean value = PlayerTimeDilationActions.modifier(4); if (value != null) cir.setReturnValue(value); }
}
