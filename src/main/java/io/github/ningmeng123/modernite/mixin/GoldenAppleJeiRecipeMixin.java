package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.integration.jei.EnchantingJeiRecipe;
import io.github.ningmeng123.modernite.item.IteItemRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Use the apple menu's point price on the core mod's JEI conversion page. @author NingMeng123 */
@Mixin(value = EnchantingJeiRecipe.class, remap = false)
public abstract class GoldenAppleJeiRecipeMixin {
    @ModifyExpressionValue(method = "goldenApple", at = @At(value = "INVOKE",
            target = "Lio/github/ningmeng123/mite/enchantment/R196EnchantmentTable;experienceCost(I)I"))
    private static int modernite$applePrice(int original) {
        return IteItemRules.GOLDEN_APPLE_ENCHANTING_COST;
    }
}
