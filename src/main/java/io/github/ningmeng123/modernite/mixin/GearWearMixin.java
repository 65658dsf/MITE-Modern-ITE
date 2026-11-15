package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;

/** Gem-only equipment enters the same single-roll path as ordinary and growth Unbreaking. @author NingMeng123 */
@Mixin(EnchantmentHelper.class)
public abstract class GearWearMixin {
    @WrapMethod(method = "processDurabilityChange")
    private static int modernite$socketWear(ServerLevel level, ItemStack stack, int amount, Operation<Integer> original) {
        boolean gemOnly = ExtremeGear.activeRank(stack, GemType.DURABILITY) > 0
                && !EnchantmentSources.levels(stack, level, "minecraft:unbreaking").present();
        int remaining = original.call(level, stack, amount);
        return gemOnly ? R196Enchantments.unbreakingDamage(stack, level, remaining, 0) : remaining;
    }
}
