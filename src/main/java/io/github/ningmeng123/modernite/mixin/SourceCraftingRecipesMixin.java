package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.modernite.crafting.SourceCraftingRecipes;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Register extension recipe timing at the base mod's common lookup. @author NingMeng123 */
@Mixin(value = TimedCraftingRecipes.class, remap = false)
public abstract class SourceCraftingRecipesMixin {
    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private static TimedCraftingRecipes.Rule modernite$sourceRule(TimedCraftingRecipes.Rule original, ResourceLocation recipe) {
        return original == null ? SourceCraftingRecipes.get(recipe) : original;
    }
}
