package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Deterministic protection conversion, preserving the actual baseline growth identity. @author NingMeng123 */
public final class ProtectionTomeRecipe extends CustomRecipe {
    private static final ResourceLocation PROTECTION = ResourceLocation.withDefaultNamespace("protection");
    private static final List<ResourceLocation> REPLACED = List.of("fire_protection", "blast_protection", "projectile_protection", "thorns")
            .stream().map(ResourceLocation::withDefaultNamespace).toList();
    public ProtectionTomeRecipe(CraftingBookCategory category) { super(category); }
    private ItemStack armor(CraftingInput input) {
        ItemStack armor = ItemStack.EMPTY; int books = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.is(ExtremeGear.PROTECTION_TOME.get())) books++;
            else if (GearProgression.isArmor(stack) && armor.isEmpty() && stack.getCount() == 1) armor = stack;
            else return ItemStack.EMPTY;
        }
        if (books != 1 || armor.isEmpty()) return ItemStack.EMPTY;
        GearProgress state = GearProgression.state(armor);
        if (state.affixes().containsKey(PROTECTION)) return ItemStack.EMPTY;
        boolean replaces = REPLACED.stream().anyMatch(state.affixes()::containsKey);
        return replaces || state.level() < GearProgression.maxLevel(armor) ? armor : ItemStack.EMPTY;
    }
    @Override public boolean matches(CraftingInput input, Level level) { return !armor(input).isEmpty(); }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack source = armor(input);
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = source.copy();
        GearProgression.ensureId(result);
        GearProgress state = GearProgression.state(result);
        var affixes = new HashMap<>(state.affixes());
        int protection = 1; boolean replaces = false;
        for (ResourceLocation key : REPLACED) {
            Integer previous = affixes.remove(key);
            if (previous != null) { replaces = true; protection = Math.max(protection, previous); }
        }
        affixes.put(PROTECTION, Math.min(protection, 5));
        result.set(GearProgression.PROGRESS.get(), new GearProgress(state.id(), state.level() + (replaces ? 0 : 1),
                replaces ? state.experienceMillis() : 0L, affixes, state.version()));
        return result;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ExtremeGear.TOME_RECIPE.get(); }
}
