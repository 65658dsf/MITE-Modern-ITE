package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.InfernalExplosionLoot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bypass source/native equipment, loot and XP only for a direct infernal explosion kill. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class InfernalExplosionDeathLootMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void modernIte$noInfernalDeathLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (InfernalExplosionLoot.isDirectKill(source)) ci.cancel();
    }
}
