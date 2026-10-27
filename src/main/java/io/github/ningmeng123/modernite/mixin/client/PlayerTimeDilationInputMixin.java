package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.PlayerTimeDilationActions;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native attack, place, use, drop, swap and hotbar input share the player clock. @author NingMeng123 */
@Mixin(Minecraft.class)
public abstract class PlayerTimeDilationInputMixin {
    @Shadow public LocalPlayer player;
    @Shadow private int rightClickDelay;
    @Shadow private int missTime;

    @Inject(method = "tick", at = @At("HEAD"))
    private void modernite$beginPlayerInput(CallbackInfo ci) { PlayerTimeDilationActions.beforeTick(); }

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void modernite$playerInputStep(CallbackInfo ci) {
        if (!TimeDilation.playerStep(player)) ci.cancel();
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    private boolean modernite$oneBufferedClick(KeyMapping mapping) { return PlayerTimeDilationActions.consumeClick(mapping); }

    @Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I", opcode = Opcodes.PUTFIELD))
    private void modernite$useDelay(Minecraft mc, int value) {
        if (TimeDilation.playerStep(player)) rightClickDelay = value;
    }

    @Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;missTime:I", opcode = Opcodes.PUTFIELD))
    private void modernite$missDelay(Minecraft mc, int value) {
        if (value >= missTime || TimeDilation.playerStep(player)) missTime = value;
    }
}
