package io.github.ningmeng123.modernite.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.ILookupState;
import net.minecraft.world.item.ItemStack;

/** One category with a native sidebar limited to tables usable for the visible page. @author NingMeng123 */
public final class ForgeJeiSidebar {
    private ForgeJeiSidebar() {}

    public static Stream<Consumer<IIngredientAcceptor<?>>> groups(ILookupState state,
            IRecipeCategory<?> category, IRecipeCategory<?> cachedCategory, IRecipeLayoutList cachedLayouts) {
        var rows = visibleRecipes(state, category, cachedCategory, cachedLayouts);
        if (rows.isEmpty()) return Stream.empty();
        var tables = new ArrayList<>(ForgeJeiRecipes.catalysts(rows.getFirst().minimumTier()));
        for (int i = 1; i < rows.size(); i++) {
            var allowed = ForgeJeiRecipes.catalysts(rows.get(i).minimumTier());
            tables.removeIf(table -> allowed.stream().noneMatch(other -> ItemStack.isSameItemSameComponents(table, other)));
        }
        // Each compatible table gets its own standard JEI catalyst slot, including click/tooltip behavior.
        return tables.stream().map(table -> acceptor -> acceptor.addItemStack(table.copy()));
    }

    private static List<ForgeJeiRecipe> visibleRecipes(ILookupState state, IRecipeCategory<?> category,
            IRecipeCategory<?> cachedCategory, IRecipeLayoutList cachedLayouts) {
        if (state == null) return List.of();
        if (cachedLayouts != null && cachedCategory == category) {
            try {
                var layouts = state.getVisible(cachedLayouts);
                List<ForgeJeiRecipe> rows = new ArrayList<>();
                for (var layout : layouts) {
                    var drawable = layout.getRecipeLayout();
                    if (drawable.getRecipeCategory() != category || !(drawable.getRecipe() instanceof ForgeJeiRecipe recipe)) {
                        rows.clear();
                        break;
                    }
                    rows.add(recipe);
                }
                if (!rows.isEmpty()) return List.copyOf(rows);
            } catch (IndexOutOfBoundsException staleLayout) {
                // JEI can ask for catalysts while a changed layout list is being rebuilt.
            }
        }
        var focused = state.getFocusedRecipes().getRecipes();
        int start = Math.clamp(state.getRecipeIndex(), 0, focused.size());
        int count = Math.min(Math.max(1, state.getRecipesPerPage()), focused.size() - start);
        List<ForgeJeiRecipe> rows = new ArrayList<>();
        for (Object recipe : focused.subList(start, start + count)) {
            if (!(recipe instanceof ForgeJeiRecipe forging)) return List.of();
            rows.add(forging);
        }
        return List.copyOf(rows);
    }
}
