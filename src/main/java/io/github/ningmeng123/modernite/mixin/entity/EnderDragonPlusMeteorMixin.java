package io.github.ningmeng123.modernite.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.modernite.entity.EnderDragonPlusDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Marks Ender Dragon Plus's optional meteor terrain explosion as part of the skill. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.enderdragonplus.dragon.hazard.MeteorHazard", remap = false)
public abstract class EnderDragonPlusMeteorMixin {
    @WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lcom/enderdragonplus/dragon/DragonBrain;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;explode("
                    + "Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)"
                    + "Lnet/minecraft/world/level/Explosion;"))
    private Explosion modernIte$markMeteorExplosion(ServerLevel level, Entity source, double x, double y, double z,
            float power, Level.ExplosionInteraction interaction, Operation<Explosion> original) {
        return EnderDragonPlusDamage.skillExplosion(source,
                () -> original.call(level, source, x, y, z, power, interaction));
    }
}
