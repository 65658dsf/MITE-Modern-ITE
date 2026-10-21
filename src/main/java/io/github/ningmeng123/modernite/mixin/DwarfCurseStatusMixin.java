package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.mite.command.CurseCommands;
import io.github.ningmeng123.modernite.content.DwarfCurseCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Include the extra world record in the base operator status command. @author NingMeng123 */
@Mixin(value = CurseCommands.class, remap = false)
public abstract class DwarfCurseStatusMixin {
    @Inject(method = "status", at = @At("HEAD"), cancellable = true)
    private static void modernite$dwarfStatus(CommandSourceStack source, ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        int result = DwarfCurseCommands.status(source, player);
        if (result != 0) cir.setReturnValue(result);
    }
}
