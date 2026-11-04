package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.content.wither.WitherEncounters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional WitherPlus 0.1.1 bridge; its combat configuration still applies to special summons. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.witherplus.wither.WitherBrain", remap = false)
public abstract class WitherPlusBrainMixin {
    @Shadow @Final private WitherBoss wither;
    @Shadow private boolean specialSummon;

    @Inject(method = "active", at = @At("HEAD"), cancellable = true)
    private void modernIte$specialOnly(CallbackInfoReturnable<Boolean> cir) {
        specialSummon = WitherEncounters.syncSpecialSummon(wither, specialSummon);
        if (!specialSummon) cir.setReturnValue(false);
    }

    @Inject(method = "setSpecialSummon", at = @At("TAIL"))
    private void modernIte$syncSummon(boolean special, CallbackInfo ci) {
        specialSummon = WitherEncounters.syncSpecialSummon(wither, specialSummon);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void modernIte$loadSummon(CompoundTag tag, CallbackInfo ci) {
        WitherEncounters.loadSpecialSummon(wither, specialSummon);
    }
}
