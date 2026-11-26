package io.github.ningmeng123.modernite.mixin.entity;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.ningmeng123.modernite.entity.BossSummonedMobs;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Prevents Wither Plus's scorch from starting unattributed fire damage on boss summons. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.witherplus.wither.hazard.ScorchHazard", remap = false)
public abstract class WitherPlusScorchHazardMixin {
    @WrapWithCondition(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lcom/witherplus/wither/WitherBrain;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;igniteForSeconds(F)V"))
    private boolean modernIte$shouldIgnite(LivingEntity victim, float seconds) {
        return !BossSummonedMobs.isSummoned(victim);
    }
}
