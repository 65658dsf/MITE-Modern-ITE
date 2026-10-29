package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.InfernalExplosionCalculator;
import io.github.ningmeng123.modernite.entity.InfernalExplosionDestruction;
import io.github.ningmeng123.modernite.entity.InfernalExplosionLoot;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.List;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Select the bounded crater before other mods receive the normal detonation event. @author NingMeng123 */
@Mixin(Explosion.class)
public abstract class InfernalExplosionTerrainMixin implements InfernalExplosionLoot.Access {
    @Shadow @Final private ExplosionDamageCalculator damageCalculator;
    @Shadow @Final private Level level;
    @Shadow @Final private boolean fire;

    @Override public boolean modernIte$isInfernalExplosion() { return damageCalculator instanceof InfernalExplosionCalculator; }

    @ModifyArg(method = "explode", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onExplosionDetonate(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/Explosion;Ljava/util/List;D)V"), index = 2)
    private List<Entity> modernIte$boundInfernalEntities(List<Entity> entities) {
        if (damageCalculator instanceof InfernalExplosionCalculator infernal) {
            var center = ((Explosion)(Object)this).center();
            double radiusSquared = (double)infernal.radius() * infernal.radius();
            // Source item handlers run during Detonate, before vanilla's later spherical range check.
            entities.removeIf(entity -> entity.distanceToSqr(center) > radiusSquared);
        }
        return entities;
    }

    @Inject(method = "explode", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onExplosionDetonate(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/Explosion;Ljava/util/List;D)V"))
    private void modernIte$infernalCrater(CallbackInfo ci) {
        if (damageCalculator instanceof InfernalExplosionCalculator infernal && level instanceof ServerLevel server)
            infernal.prepareTerrain(server, (Explosion)(Object)this, fire);
    }

    @WrapMethod(method = "finalizeExplosion")
    private void modernIte$budgetInfernalCrater(boolean particles, Operation<Void> original) {
        if (damageCalculator instanceof InfernalExplosionCalculator infernal && level instanceof ServerLevel server) {
            var explosion = (Explosion)(Object)this;
            InfernalExplosionDestruction.enqueue(server, explosion, infernal.radius(), fire);
            // The native packet must not erase the entire crater on clients before queued server work finishes.
            explosion.clearToBlow();
        }
        original.call(particles);
    }
}
