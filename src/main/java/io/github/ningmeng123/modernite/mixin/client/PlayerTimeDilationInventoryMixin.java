package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.PlayerTimeDilationActions;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retain complete inventory input sequences until their player step, including drag/release. @author NingMeng123 */
@Mixin(AbstractContainerScreen.class)
public abstract class PlayerTimeDilationInventoryMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void modernite$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        if (PlayerTimeDilationActions.defer(screen, true, () -> screen.mouseClicked(x, y, button))) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void modernite$release(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        if (PlayerTimeDilationActions.defer(screen, false, () -> screen.mouseReleased(x, y, button))) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void modernite$drag(double x, double y, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        if (PlayerTimeDilationActions.defer(screen, false, () -> screen.mouseDragged(x, y, button, dx, dy))) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void modernite$key(int key, int scan, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return;
        var screen = (AbstractContainerScreen<?>) (Object) this;
        Slot slot = hoveredSlot;
        if (PlayerTimeDilationActions.defer(screen, true, () -> {
            Slot previous = hoveredSlot;
            hoveredSlot = slot;
            try { screen.keyPressed(key, scan, modifiers); } finally { hoveredSlot = previous; }
        })) cir.setReturnValue(true);
    }

    @ModifyConstant(method = "mouseClicked", constant = @Constant(longValue = 250L))
    private long modernite$doubleClickClock(long original) {
        return TimeDilation.active(Minecraft.getInstance().player) ? original * TimeDilation.TICK_DIVISOR : original;
    }
}
