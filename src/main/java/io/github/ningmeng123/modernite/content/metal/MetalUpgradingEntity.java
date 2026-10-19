package io.github.ningmeng123.modernite.content.metal;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Counted, heat-gated upgrades persist across chunk reloads without borrowing gem-smelting recipes. @author NingMeng123 */
public final class MetalUpgradingEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int DATA_COUNT = 11;
    private static final int[] TOP = {0}, SIDE = {1}, BOTTOM = {2, 1};
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int progress, total, burnRemaining, burnDuration, heat, batchCount, requiredHeat;
    private String activeRecipe = "";
    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress & 65535; case 1 -> progress >>> 16;
                case 2 -> total & 65535; case 3 -> total >>> 16;
                case 4 -> burnRemaining & 65535; case 5 -> burnRemaining >>> 16;
                case 6 -> burnDuration & 65535; case 7 -> burnDuration >>> 16;
                case 8 -> heat; case 9 -> requiredHeat; case 10 -> batchCount; default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };
    public MetalUpgradingEntity(BlockPos pos, BlockState state) { super(MetalUpgradingContent.ENTITY.get(), pos, state); }
    @Override public int getContainerSize() { return 3; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> values) { items = values; }
    @Override protected Component getDefaultName() { return Component.translatable("block.modern_ite.metal_upgrading_furnace"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new MetalUpgradingMenu(id, inventory, this, data); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot == 0 && !ItemStack.isSameItemSameComponents(getItem(0), stack)) progress = 0;
        super.setItem(slot, stack);
    }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = super.removeItem(slot, count);
        if (slot == 0 && !removed.isEmpty()) { progress = 0; setChanged(); }
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot == 0) progress = 0;
        return super.removeItemNoUpdate(slot);
    }
    @Override public void clearContent() { super.clearContent(); progress = 0; setChanged(); }
    public static boolean isFuel(ItemStack stack) {
        var fuel = FurnaceThermalController.fuel(stack);
        return fuel.ticks() > 0 && fuel.heat() >= 3 && fuel.heat() <= 5;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 ? MetalUpgradingRecipe.isInput(stack) : slot == 1 && isFuel(stack);
    }
    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDE; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == 2 || slot == 1 && !isFuel(stack);
    }
    private RecipeHolder<MetalUpgradingRecipe> recipe() {
        if (level == null || getItem(0).isEmpty()) return null;
        // Retain the recipe's cost/time in the menu even when the player has inserted too few blocks.
        return level.getRecipeManager().getAllRecipesFor(MetalUpgradingContent.RECIPE_TYPE.get()).stream()
                .filter(holder -> holder.value().accepts(getItem(0))).findFirst().orElse(null);
    }
    private boolean roomFor(ItemStack result) {
        ItemStack output = getItem(2);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= Math.min(getMaxStackSize(), result.getMaxStackSize());
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, MetalUpgradingEntity furnace) {
        if (level.isClientSide) return;
        boolean changed = false;
        if (furnace.burnRemaining > 0) { furnace.burnRemaining--; changed = true; }
        if (furnace.burnRemaining == 0) furnace.heat = 0;
        var holder = furnace.recipe();
        var recipe = holder == null ? null : holder.value();
        String id = holder == null ? "" : holder.id().toString();
        int ticks = recipe == null ? 0 : recipe.cookingTicks(), count = recipe == null ? 0 : recipe.inputCount();
        int required = recipe == null ? 0 : recipe.requiredHeat();
        if (!id.equals(furnace.activeRecipe) || ticks != furnace.total || count != furnace.batchCount || required != furnace.requiredHeat) {
            furnace.activeRecipe = id; furnace.total = ticks; furnace.batchCount = count;
            furnace.requiredHeat = required; furnace.progress = 0; changed = true;
        }
        boolean ready = recipe != null && furnace.getItem(0).getCount() >= count && furnace.roomFor(recipe.output());
        ItemStack fuelStack = furnace.getItem(1);
        var fuel = FurnaceThermalController.fuel(fuelStack);
        if (furnace.burnRemaining == 0 && ready && isFuel(fuelStack) && fuel.heat() >= required) {
            furnace.burnRemaining = furnace.burnDuration = fuel.ticks(); furnace.heat = fuel.heat();
            ItemStack remainder = fuelStack.hasCraftingRemainingItem() ? fuelStack.getCraftingRemainingItem() : ItemStack.EMPTY;
            fuelStack.shrink(1);
            if (fuelStack.isEmpty()) furnace.setItem(1, remainder);
            else if (!remainder.isEmpty()) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, remainder);
            changed = true;
        }
        if (ready && furnace.burnRemaining > 0 && furnace.heat >= required) {
            furnace.progress++;
            if (furnace.progress >= ticks) {
                ItemStack result = recipe.output(), output = furnace.getItem(2);
                if (output.isEmpty()) furnace.setItem(2, result); else output.grow(result.getCount());
                furnace.getItem(0).shrink(count); furnace.progress = 0;
            }
            changed = true;
        } else if (furnace.progress != 0) { furnace.progress = 0; changed = true; }
        boolean lit = furnace.burnRemaining > 0;
        if (state.getValue(MetalUpgradingBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(MetalUpgradingBlock.LIT, lit), 3); changed = true;
        }
        if (changed) furnace.setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("progress", progress); tag.putInt("total", total); tag.putInt("burn_remaining", burnRemaining);
        tag.putInt("burn_duration", burnDuration); tag.putInt("heat", heat); tag.putInt("batch_count", batchCount);
        tag.putInt("required_heat", requiredHeat); tag.putString("active_recipe", activeRecipe);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); items = NonNullList.withSize(3, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, registries);
        total = Math.clamp(tag.getInt("total"), 0, 100000000); progress = Math.clamp(tag.getInt("progress"), 0, Math.max(0, total - 1));
        burnDuration = Math.max(0, tag.getInt("burn_duration")); burnRemaining = Math.clamp(tag.getInt("burn_remaining"), 0, burnDuration);
        heat = burnRemaining > 0 ? Math.clamp(tag.getInt("heat"), 0, 5) : 0;
        batchCount = Math.clamp(tag.getInt("batch_count"), 0, 64); requiredHeat = Math.clamp(tag.getInt("required_heat"), 0, 5);
        activeRecipe = tag.getString("active_recipe");
    }
}
