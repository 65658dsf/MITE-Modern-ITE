package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.mite.entity.SpecialMobEquipment;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend the existing native lord material-floor policy to the addon Time Lord. @author NingMeng123 */
@Mixin(value = SpecialMobEquipment.class, remap = false)
public abstract class TimeLordEquipmentProgressionMixin {
    @Inject(method = "handles", at = @At("HEAD"), cancellable = true)
    private static void modernite$timeLord(Mob mob, CallbackInfoReturnable<Boolean> cir) {
        if (mob instanceof ExtremeMob extreme && extreme.extremeKind() == ExtremeKind.TIME_LORD) cir.setReturnValue(true);
    }
}
