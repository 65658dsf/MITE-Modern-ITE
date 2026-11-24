package io.github.ningmeng123.modernite.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs.CreeperVariant;
import net.minecraft.world.entity.monster.Creeper;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies the run creeper's shorter fuse to detonation, fall acceleration and swelling. @author NingMeng123 */
@Mixin(Creeper.class)
public abstract class RunCreeperFuseMixin {
    @ModifyExpressionValue(method = {"tick", "causeFallDamage", "getSwelling"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/monster/Creeper;maxSwell:I", opcode = Opcodes.GETFIELD))
    private int modernite$shortenRunCreeperFuse(int baseFuse) {
        // Keep the saved Fuse as the base duration. Swelling divides by the effective duration minus two.
        if (baseFuse > 3 && (Object)this instanceof CreeperVariant variant && variant.extremeKind() == ExtremeKind.RUN_CREEPER) {
            return baseFuse - baseFuse / 3;
        }
        return baseFuse;
    }
}
