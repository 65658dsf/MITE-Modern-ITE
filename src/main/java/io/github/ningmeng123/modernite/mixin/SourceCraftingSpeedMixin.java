package io.github.ningmeng123.modernite.mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.modernite.survival.SourceUtilityRules;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Apply source beacon speed to the authoritative modern manufacturing period. @author NingMeng123 */
@Mixin(targets="io.github.ningmeng123.mite.crafting.TimedCraftingController",remap=false)
public abstract class SourceCraftingSpeedMixin {
    @ModifyReturnValue(method="period(Lnet/minecraft/server/level/ServerPlayer;Lio/github/ningmeng123/mite/crafting/TimedCraftingController$Prepared;)I",at=@At("RETURN"))
    private static int modernite$beacon(int original,@Local(argsOnly=true)ServerPlayer player) {
        return SourceUtilityRules.craftingPeriod(player,original);
    }
}
