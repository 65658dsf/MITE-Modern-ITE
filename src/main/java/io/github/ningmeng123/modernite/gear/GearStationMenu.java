package io.github.ningmeng123.modernite.gear;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server revalidates every slot operation and start button. @author NingMeng123 */
public final class GearStationMenu extends AbstractContainerMenu {
    public final boolean gemMode;
    public final ContainerData data;
    private final Container station;
    public static GearStationMenu gemClient(int id, Inventory inventory) { return new GearStationMenu(id, inventory, new SimpleContainer(9), new SimpleContainerData(5), true); }
    public static GearStationMenu forgeClient(int id, Inventory inventory) { return new GearStationMenu(id, inventory, new SimpleContainer(9), new SimpleContainerData(5), false); }
    public GearStationMenu(int id, Inventory inventory, Container station, ContainerData data, boolean gemMode) {
        super(gemMode ? ExtremeGear.STATION_MENU.get() : ExtremeGear.FORGE_MENU.get(), id);
        this.gemMode = gemMode; this.data = data; this.station = station;
        checkContainerSize(station, 9); checkContainerDataCount(data, 5);
        int[][] gemPositions = {{26, 35}, {62, 25}, {80, 25}, {98, 25}, {116, 25}, {62, 47}, {80, 47}, {98, 47}, {116, 47}};
        int[][] forgePositions = {{17, 22}, {35, 22}, {53, 22}, {71, 22}, {89, 22}, {53, 51}, {17, 78}, {89, 78}, {143, 51}};
        for (int i = 0; i < 9; i++) {
            int index = i; int[] pos = (gemMode ? gemPositions : forgePositions)[i];
            addSlot(new Slot(station, index, pos[0], pos[1]) {
                @Override public boolean mayPlace(ItemStack stack) {
                    if (locked()) return false;
                    if (station instanceof GearStationEntity actual) return actual.canPlaceItem(index, stack);
                    return gemMode ? index == 0 ? ExtremeGear.socketable(stack) : !station.getItem(0).isEmpty() && stack.getItem() instanceof EnhanceGemItem : index != 8;
                }
                @Override public boolean mayPickup(Player player) { return !locked() && super.mayPickup(player); }
                @Override public int getMaxStackSize() { return gemMode || index >= 5 ? 1 : 64; }
            });
        }
        int inventoryY = gemMode ? 91 : 126;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, inventoryY + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, inventoryY + 58));
        addDataSlots(data);
    }
    public boolean locked() { return !gemMode && data.get(3) == 1; }
    @Override public boolean stillValid(Player player) { return station.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != 0 || gemMode || !stillValid(player) || !(station instanceof GearStationEntity entity)) return false;
        entity.start(); return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || index < 9 && locked()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy(); ItemStack moving = original.copy();
        if (index < 9) { if (!moveItemStackTo(moving, 9, slots.size(), true)) return ItemStack.EMPTY; }
        else if (gemMode) {
            if (ExtremeGear.socketable(moving)) { if (!moveItemStackTo(moving, 0, 1, false)) return ItemStack.EMPTY; }
            else if (moving.getItem() instanceof EnhanceGemItem) { if (!moveItemStackTo(moving, 1, 9, false)) return ItemStack.EMPTY; }
            else return ItemStack.EMPTY;
        } else {
            if (ExtremeGear.socketable(moving) && slots.get(5).mayPlace(moving) && !slots.get(5).hasItem()) {
                if (!moveItemStackTo(moving, 5, 6, false)) return ItemStack.EMPTY;
            } else if (slots.get(6).mayPlace(moving) && !slots.get(6).hasItem()) {
                if (!moveItemStackTo(moving, 6, 7, false)) return ItemStack.EMPTY;
            } else if (slots.get(7).mayPlace(moving) && !slots.get(7).hasItem()) {
                if (!moveItemStackTo(moving, 7, 8, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(moving, 0, 5, false)) return ItemStack.EMPTY;
        }
        if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.setByPlayer(moving.isEmpty() ? ItemStack.EMPTY : moving); slot.onTake(player, original);
        return original;
    }
}
