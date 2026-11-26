package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.BossSummonedMobs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.DragonFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps dragon breath attributable after the fireball's owner unloads. @author NingMeng123 */
@Mixin(DragonFireball.class)
public abstract class BossSummonedMobDragonBreathMixin {
    @ModifyArg(method = "onHit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity modernIte$markBreathCloud(Entity cloud) {
        if (BossSummonedMobs.isBossHazard((Entity) (Object) this)) BossSummonedMobs.markBossHazard(cloud);
        return cloud;
    }
}
