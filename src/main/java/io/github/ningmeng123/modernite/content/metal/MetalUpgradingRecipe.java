package io.github.ningmeng123.modernite.content.metal;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Counted conversion recipes read the same effective settings on the server and in JEI. @author NingMeng123 */
public record MetalUpgradingRecipe(Stage stage) implements Recipe<SingleRecipeInput> {
    public enum Stage implements StringRepresentable {
        IRON_TO_MITHRIL("iron_to_mithril", "minecraft:iron_block", "mite:mithril_ingot", "iron_block_count_to_mithril"),
        MITHRIL_TO_ADAMANTIUM("mithril_to_adamantium", "mite:mithril_block", "mite:adamantium_ingot", "mithril_block_count_to_adamantium");
        private final String name, input, output, countKey;
        Stage(String name, String input, String output, String countKey) {
            this.name = name; this.input = input; this.output = output; this.countKey = countKey;
        }
        @Override public String getSerializedName() { return name; }
    }
    public int inputCount() { return IteConfig.integer(stage.countKey); }
    public int cookingTicks() { return IteConfig.integer(stage.name + "_cook_time"); }
    public ItemStack input() { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(stage.input)), inputCount()); }
    public ItemStack output() { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(stage.output))); }
    public int requiredHeat() { return FurnaceThermalController.requiredHeat(input()); }
    /** Exact fuel batches shared by JEI and the headless executable-recipe audit. */
    public List<ItemStack> fuelOptions() {
        return BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(stack -> {
            var fuel = FurnaceThermalController.fuel(stack);
            return fuel.ticks() > 0 && fuel.heat() >= requiredHeat() && fuel.heat() <= 5;
        }).map(stack -> {
            int ticks = FurnaceThermalController.fuel(stack).ticks();
            stack.setCount((int)(((long)cookingTicks() + ticks - 1) / ticks)); return stack;
        }).toList();
    }
    public boolean accepts(ItemStack input) { return !input.isEmpty() && input.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(stage.input))); }
    public static boolean isInput(ItemStack stack) {
        for (Stage stage : Stage.values()) if (new MetalUpgradingRecipe(stage).accepts(stack)) return true;
        return false;
    }
    @Override public boolean matches(SingleRecipeInput input, Level level) {
        return accepts(input.item()) && input.item().getCount() >= inputCount();
    }
    @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) { return output(); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return output(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 1; }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, Ingredient.of(input().getItem())); }
    @Override public RecipeSerializer<?> getSerializer() { return MetalUpgradingContent.SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return MetalUpgradingContent.RECIPE_TYPE.get(); }
    @Override public ItemStack getToastSymbol() { return new ItemStack(MetalUpgradingContent.FURNACE_ITEM.get()); }
    public static final class Serializer implements RecipeSerializer<MetalUpgradingRecipe> {
        private static final MapCodec<MetalUpgradingRecipe> CODEC = StringRepresentable.fromEnum(Stage::values)
                .fieldOf("stage").xmap(MetalUpgradingRecipe::new, MetalUpgradingRecipe::stage);
        private static final StreamCodec<RegistryFriendlyByteBuf, MetalUpgradingRecipe> STREAM = StreamCodec.of(
                (buffer, recipe) -> buffer.writeEnum(recipe.stage()), buffer -> new MetalUpgradingRecipe(buffer.readEnum(Stage.class)));
        @Override public MapCodec<MetalUpgradingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MetalUpgradingRecipe> streamCodec() { return STREAM; }
    }
}
