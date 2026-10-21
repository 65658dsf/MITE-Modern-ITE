package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.modernite.entity.MobSplitting;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Miniatures retain one quarter of adult health even with configured growth formulas. @author NingMeng123 */
@Mixin(value=MonsterDayScaling.class,remap=false)
public abstract class SplitHealthGrowthMixin {
    @Inject(method="refresh",at=@At("HEAD"))
    private static void modernite$adultGrowthInputs(Mob mob,CallbackInfo ci){MobSplitting.beforeHealthGrowth(mob);}
    @Inject(method="refresh",at=@At("RETURN"))
    private static void modernite$miniatureGrowthResult(Mob mob,CallbackInfo ci){MobSplitting.applyHealthScale(mob);}
}
