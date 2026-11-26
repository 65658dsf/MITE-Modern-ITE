package io.github.ningmeng123.modernite.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.modernite.entity.EnderDragonPlusDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Identifies the end crystal explosions triggered by the dragon's shield skill. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.enderdragonplus.dragon.phase.ShieldAscensionPhase", remap = false)
public abstract class EnderDragonPlusShieldAscensionMixin {
    @WrapOperation(method = "explodeCrystals()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean modernIte$attributeShieldExplosion(EndCrystal crystal, DamageSource source, float damage,
            Operation<Boolean> original) {
        return EnderDragonPlusDamage.skillExplosion(crystal, () -> original.call(crystal, source, damage));
    }
}
