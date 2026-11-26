package io.github.ningmeng123.modernite.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.modernite.content.wither.WitherDamage;
import io.github.ningmeng123.modernite.entity.BossSummonedMobs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.WitherSkull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The skull's ownerless impact reports anonymous magic after the Wither is gone. @author NingMeng123 */
@Mixin(WitherSkull.class)
public abstract class BossSummonedMobSkullMixin {
    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean modernIte$bossSkullImpact(Entity victim, DamageSource source, float damage, Operation<Boolean> original) {
        if (!BossSummonedMobs.isBossHazard((Entity) (Object) this)) return original.call(victim, source, damage);
        return !BossSummonedMobs.isSummoned(victim)
                && original.call(victim, source,
                        damage * WitherDamage.NATIVE_DAMAGE_MULTIPLIER * WitherDamage.OUTGOING_DAMAGE_MULTIPLIER);
    }
}
