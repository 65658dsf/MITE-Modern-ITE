package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Non-quality lantern repairs use half the source core's explicit lowest difficulty of one. @author NingMeng123 */
@Mixin(value = CraftingQuality.class, remap = false)
public abstract class SourceLanternCraftingMixin {
    @ModifyReturnValue(method = "rule(Lnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/CraftingInput;)Lio/github/ningmeng123/mite/crafting/TimedCraftingRecipes$Rule;",
            at = @At("RETURN"))
    private static TimedCraftingRecipes.Rule modernite$lanternRepair(TimedCraftingRecipes.Rule original,
            RecipeHolder<CraftingRecipe> recipe, ItemStack output, CraftingInput input) {
        return original == null && recipe.value() instanceof RepairItemRecipe && output.getItem() instanceof DynamicCoreItem
                ? new TimedCraftingRecipes.Rule(.5F, null, -1) : original;
    }

    @ModifyReturnValue(method = "resolve", at = @At("RETURN"))
    private static CraftingQuality.Result modernite$allowWornLanternInputs(CraftingQuality.Result original,
            Player player, AbstractContainerMenu menu, CraftingContainer grid, RecipeHolder<CraftingRecipe> recipe, ItemStack raw) {
        if (original == null || !(recipe.value() instanceof RepairItemRecipe) || !(raw.getItem() instanceof DynamicCoreItem))
            return original;
        return new CraftingQuality.Result(original.rule(), original.stack(), original.quality(), original.range(),
                original.fixed(), true, original.exempt(), original.cost(), original.consumption());
    }
}
