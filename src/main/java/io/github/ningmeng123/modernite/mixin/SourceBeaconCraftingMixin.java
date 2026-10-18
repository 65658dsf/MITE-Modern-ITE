package io.github.ningmeng123.modernite.mixin;
import io.github.ningmeng123.modernite.survival.SourceUtilityRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Attach assistance to the real active-beacon pulse and clear it on removal. @author NingMeng123 */
@Mixin(BeaconBlockEntity.class)
public abstract class SourceBeaconCraftingMixin {
    @Inject(method="applyEffects",at=@At("HEAD"))
    private static void modernite$assist(Level level,BlockPos pos,int rank,Holder<MobEffect> primary,Holder<MobEffect> secondary,CallbackInfo ci) {
        if(level instanceof ServerLevel server&&primary!=null)SourceUtilityRules.beaconPulse(server,pos,rank);
    }
    @Inject(method="setRemoved",at=@At("HEAD"))
    private void modernite$clear(CallbackInfo ci) {
        BlockEntity beacon=(BlockEntity)(Object)this;SourceUtilityRules.removeBeacon(beacon.getLevel(),beacon.getBlockPos());
    }
}
