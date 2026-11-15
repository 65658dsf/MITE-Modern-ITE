package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemEffectRules;
import io.github.ningmeng123.modernite.gear.GemType;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Combine gem ranks with ordinary Efficiency before native environment and survival scaling. @author NingMeng123 */
@Mixin(Player.class)
public abstract class GearGemMiningMixin {
    @WrapOperation(method = "getDigSpeed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double modernite$combinedEfficiency(Player player, Holder<Attribute> attribute, Operation<Double> original) {
        double value = original.call(player, attribute);
        if (!attribute.equals(Attributes.MINING_EFFICIENCY)) return value;
        var stack = player.getMainHandItem();
        int rank = ExtremeGear.activeRank(stack, GemType.EFFICIENCY);
        if (rank == 0) return value;
        int ordinary = EnchantmentSources.levels(stack, player.level(), "minecraft:efficiency").normal();
        // Growth Efficiency already contributes its own squared bonus to this attribute.
        return value + GemEffectRules.efficiencyAddition(ordinary, rank);
    }
}
