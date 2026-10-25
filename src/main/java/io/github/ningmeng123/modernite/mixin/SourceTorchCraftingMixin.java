package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.modernite.crafting.SourceCraftingRecipes;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Restore manufacturing for bundle recipes previously exempted by the base mod. @author NingMeng123 */
@Mixin(value = CraftingQuality.class, remap = false)
public abstract class SourceTorchCraftingMixin {
    @ModifyReturnValue(method = "instantTorchBundle", at = @At("RETURN"))
    private static boolean modernite$requireManufacturing(boolean original, ResourceLocation recipe) {
        return original && SourceCraftingRecipes.get(recipe) == null;
    }
}
