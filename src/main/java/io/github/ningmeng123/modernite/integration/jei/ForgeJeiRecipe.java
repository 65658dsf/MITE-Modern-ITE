package io.github.ningmeng123.modernite.integration.jei;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A successful strengthening example; normal and universal payments have separate pages. @author NingMeng123 */
public record ForgeJeiRecipe(int minimumTier, ResourceLocation itemId, ItemStack input, ItemStack output,
        List<ItemStack> materials, boolean universal, ForgeJeiRecipes.ToolStats stats) {
    public ForgeJeiRecipe {
        input = input.copy();
        output = output.copy();
        materials = materials.stream().map(ItemStack::copy).toList();
    }

    @Override public ItemStack input() { return input.copy(); }
    @Override public ItemStack output() { return output.copy(); }
    @Override public List<ItemStack> materials() { return materials.stream().map(ItemStack::copy).toList(); }

    public int inputGrade() { return ExtremeGear.grade(input); }
    public int outputGrade() { return ExtremeGear.grade(output); }
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "jei_forging/" + itemId.getNamespace()
                + "/" + itemId.getPath() + "/" + inputGrade() + (universal ? "/universal" : "/normal"));
    }
}
