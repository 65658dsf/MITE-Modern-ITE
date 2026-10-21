package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.modernite.content.DwarfCurse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Death/login/dimension recovery and the base curse's two legitimate cures. @author NingMeng123 */
@Mixin(value = PlayerCurses.class, remap = false)
public abstract class DwarfCurseLifecycleMixin {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private static void modernite$oneCurse(ServerPlayer player, Witch witch, R196Curse type, int delay, CallbackInfoReturnable<Boolean> cir) {
        if (DwarfCurse.pendingOrActive(player)) cir.setReturnValue(false);
    }

    @Inject(method = "reconcile(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("RETURN"))
    private static void modernite$restoreCurse(ServerPlayer player, boolean forceSync, CallbackInfo ci) {
        DwarfCurse.reconcile(player);
    }

    @Inject(method = "cure", at = @At("RETURN"), cancellable = true)
    private static void modernite$cure(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (DwarfCurse.cure(player)) cir.setReturnValue(true);
    }

    @Inject(method = "removeForWitch", at = @At("RETURN"))
    private static void modernite$witchDies(Witch witch, CallbackInfo ci) {
        DwarfCurse.removeForWitch(witch);
    }
}
