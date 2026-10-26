package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.mite.world.SourceExplosionBlocks;
import io.github.ningmeng123.modernite.entity.InfernalExplosionCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The spherical infernal crater uses the normal block callbacks after protection events. @author NingMeng123 */
@Mixin(SourceExplosionBlocks.class)
public abstract class InfernalExplosionPreparationMixin {
    @Inject(method = "prepare(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/ExplosionDamageCalculator;)V", at = @At("HEAD"), cancellable = true)
    private static void modernIte$skipInfernalPrepass(Level level, Explosion explosion, ExplosionDamageCalculator calculator, CallbackInfo ci) {
        if (calculator instanceof InfernalExplosionCalculator) ci.cancel();
    }
}
