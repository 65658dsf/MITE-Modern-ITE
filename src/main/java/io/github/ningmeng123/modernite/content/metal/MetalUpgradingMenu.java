package io.github.ningmeng123.modernite.content.metal;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Distinct three-slot upgrade menu with full-width progress synchronization. @author NingMeng123 */
public final class MetalUpgradingMenu extends AbstractContainerMenu {
    public final ContainerData data;
    private final Container furnace;
    public static MetalUpgradingMenu client(int id, Inventory inventory) {
        return new MetalUpgradingMenu(id, inventory, new SimpleContainer(3), new SimpleContainerData(MetalUpgradingEntity.DATA_COUNT));
    }
    public MetalUpgradingMenu(int id, Inventory inventory, Container furnace, ContainerData data) {
        super(MetalUpgradingContent.MENU.get(), id); this.furnace = furnace; this.data = data;
        checkContainerSize(furnace, 3); checkContainerDataCount(data, MetalUpgradingEntity.DATA_COUNT);
        addSlot(new Slot(furnace, 0, 56, 28) {
            @Override public boolean mayPlace(ItemStack stack) { return MetalUpgradingRecipe.isInput(stack); }
        });
        addSlot(new Slot(furnace, 1, 56, 60) {
            @Override public boolean mayPlace(ItemStack stack) { return MetalUpgradingEntity.isFuel(stack); }
        });
        addSlot(new Slot(furnace, 2, 116, 36) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 111 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 169));
        addDataSlots(data);
    }
    /** Container data packets carry signed shorts, so long configured durations use two words. */
    public int integer(int lowIndex) { return (data.get(lowIndex) & 65535) | ((data.get(lowIndex + 1) & 65535) << 16); }
    public int progress() { return integer(0); }
    public int total() { return integer(2); }
    public int burnRemaining() { return integer(4); }
    public int burnDuration() { return integer(6); }
    @Override public boolean stillValid(Player player) { return furnace.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack moving = slot.getItem(), original = moving.copy();
        if (index < 3) {
            if (!moveItemStackTo(moving, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else if (MetalUpgradingRecipe.isInput(moving)) {
            if (!moveItemStackTo(moving, 0, 1, false)) return ItemStack.EMPTY;
        } else if (MetalUpgradingEntity.isFuel(moving)) {
            if (!moveItemStackTo(moving, 1, 2, false)) return ItemStack.EMPTY;
        } else if (index < 30) {
            if (!moveItemStackTo(moving, 30, 39, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(moving, 3, 30, false)) return ItemStack.EMPTY;
        if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.setByPlayer(moving.isEmpty() ? ItemStack.EMPTY : moving); slot.onTake(player, original);
        return original;
    }
}
