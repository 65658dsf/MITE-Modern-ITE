package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.content.ExtremeContent;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native pillagers keep using the addon crossbow after variant removal. @author NingMeng123 */
@Mixin(Pillager.class)
public abstract class PillagerCrossbowMixin {
    @Inject(method = "canFireProjectileWeapon", at = @At("HEAD"), cancellable = true)
    private void modernite$crossbow(ProjectileWeaponItem weapon, CallbackInfoReturnable<Boolean> cir) {
        if (weapon == ExtremeContent.CROSSBOW.get()) cir.setReturnValue(true);
    }
}
