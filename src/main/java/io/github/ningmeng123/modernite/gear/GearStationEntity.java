package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Gem slots are projections of the equipment component, never a second copy of its gems. @author NingMeng123 */
public final class GearStationEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    private int progress, total, failAt, status;
    private boolean doomed;
    public final ContainerData data = new ContainerData() {
        public int get(int index) { return switch (index) {
            case 0 -> tier(); case 1 -> progress; case 2 -> total; case 3 -> status; case 4 -> failure(); default -> 0;
        }; }
        public void set(int index, int value) {}
        public int getCount() { return 5; }
    };
    public GearStationEntity(BlockPos pos, BlockState state) { super(ExtremeGear.STATION_ENTITY.get(), pos, state); }
    public int tier() { return getBlockState().getBlock() instanceof GearStationBlock block ? block.tier : -1; }
    public boolean isGemStation() { return tier() < 0; }
    public boolean running() { return status == 1; }
    @Override public int getContainerSize() { return 9; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> values) { items = values; }
    @Override protected Component getDefaultName() { return Component.translatable(isGemStation() ? "container.modern_ite.gem_setting" : "container.modern_ite.forging"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new GearStationMenu(id, inventory, this, data, isGemStation()); }
    @Override public ItemStack getItem(int slot) {
        if (isGemStation() && slot > 0) return ExtremeGear.fromCode(ExtremeGear.sockets(items.get(0)).values().get(slot - 1));
        return items.get(slot);
    }
    @Override public boolean isEmpty() {
        return isGemStation() ? items.get(0).isEmpty() : items.stream().allMatch(ItemStack::isEmpty);
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (isGemStation() && slot > 0) {
            ItemStack gear = items.get(0);
            if (gear.isEmpty()) return;
            if (!stack.isEmpty() && !(stack.getItem() instanceof EnhanceGemItem)) return;
            int code = stack.isEmpty() ? 0 : ((EnhanceGemItem)stack.getItem()).code();
            gear.set(ExtremeGear.SOCKETS.get(), ExtremeGear.sockets(gear).with(slot - 1, code));
        } else {
            items.set(slot, stack);
            if (slot >= 5 || isGemStation()) stack.setCount(Math.min(stack.getCount(), 1));
        }
        setChanged();
    }
    @Override public ItemStack removeItem(int slot, int count) {
        if (running() || count <= 0) return ItemStack.EMPTY;
        if (isGemStation() && slot > 0) {
            ItemStack gem = getItem(slot); if (!gem.isEmpty()) setItem(slot, ItemStack.EMPTY); return gem;
        }
        ItemStack result = ContainerHelper.removeItem(items, slot, count); if (!result.isEmpty()) setChanged(); return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (running()) return ItemStack.EMPTY;
        if (isGemStation() && slot > 0) return removeItem(slot, 1);
        return ContainerHelper.takeItem(items, slot);
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (running()) return false;
        if (isGemStation()) return slot == 0 ? ExtremeGear.socketable(stack) : !getItem(0).isEmpty() && stack.getItem() instanceof EnhanceGemItem;
        if (slot == 8) return false;
        if (slot == 5) return ExtremeGear.socketable(stack) && GearMath.maximumGrade(GearProgression.material(stack)) > 0;
        if (slot == 6) return R196EnchantmentCatalog.family(stack) == ToolFamily.AXE;
        if (slot == 7) return R196EnchantmentCatalog.family(stack) == ToolFamily.WAR_HAMMER;
        return slot >= 0 && slot < 5;
    }
    private int toolTier(int slot) { return GearMath.materialTier(GearProgression.material(items.get(slot))); }
    private boolean universal() {
        return ForgeRecipes.usesUniversal(this);
    }
    private int failure() {
        int result = GearMath.failurePercent(ExtremeGear.grade(items.get(5)), toolTier(6), toolTier(7));
        if (universal()) result += 50 - (ExtremeGear.grade(items.get(5)) < 4 ? 35 : ExtremeGear.grade(items.get(5)) < 7 ? 55 : 65);
        return Math.clamp(result, 0, 100);
    }
    public boolean canStart() {
        ItemStack target = items.get(5); String material = GearProgression.material(target);
        int grade = ExtremeGear.grade(target);
        return !isGemStation() && !running() && !target.isEmpty() && items.get(8).isEmpty()
                && grade < GearMath.maximumGrade(material) && tier() >= GearMath.tableTier(material)
                && R196EnchantmentCatalog.family(items.get(6)) == ToolFamily.AXE
                && R196EnchantmentCatalog.family(items.get(7)) == ToolFamily.WAR_HAMMER
                && items.get(6).isDamageableItem() && items.get(7).isDamageableItem() && ForgeRecipes.has(this);
    }
    public void start() {
        if (level == null || level.isClientSide || running()) return;
        if (!canStart()) { status = 4; setChanged(); return; }
        progress = 0; total = GearMath.forgeTicks(ExtremeGear.grade(items.get(5)), toolTier(6), toolTier(7));
        failAt = 1 + level.random.nextInt(total); doomed = level.random.nextInt(100) < failure();
        status = 1; setChanged();
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, GearStationEntity station) {
        if (level.isClientSide || station.isGemStation() || !station.running()) return;
        if (++station.progress >= (station.doomed ? station.failAt : station.total)) station.finish(!station.doomed);
        else if (ForgingAnimation.isImpactTick(station.progress)) {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, .45F,
                    .95F + (station.progress / ForgingAnimation.CYCLE_TICKS % 3) * .04F);
        }
        station.setChanged();
    }
    private void finish(boolean success) {
        if (level == null || level.isClientSide) return;
        ItemStack target = items.get(5); int oldGrade = ExtremeGear.grade(target);
        // No reroll after chunk reload: the initial result and failure check tick are saved.
        if (target.isEmpty() || !items.get(8).isEmpty() || !ForgeRecipes.consume(this, true)) { status = 4; total = progress = 0; return; }
        rawWear(items.get(6), GearMath.axeCost(oldGrade) / (success ? 1 : 2));
        rawWear(items.get(7), GearMath.hammerCost(oldGrade) / (success ? 1 : 2));
        if (success) {
            target.set(ExtremeGear.FORGE_GRADE.get(), oldGrade + 1);
            var quality = ForgeRecipes.reward(oldGrade);
            if (quality != null && quality.ordinal() > EquipmentDurability.quality(target).ordinal()) EquipmentDurability.setQuality(target, quality);
            items.set(8, target); items.set(5, ItemStack.EMPTY); status = 2;
        } else {
            int remaining = Math.max(0, target.getMaxDamage() - target.getDamageValue());
            int retained = Math.round(remaining * (oldGrade < 7 ? .95F : .9F));
            target.setDamageValue(target.getMaxDamage() - retained);
            // Extreme explicitly removes ITE's grade downgrade feedback.
            status = 3;
        }
        level.playSound(null, worldPosition, success ? SoundEvents.ANVIL_USE : SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 1, 1);
        progress = total = 0; setChanged();
    }
    private static void rawWear(ItemStack stack, int amount) {
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        long next = (long)stack.getDamageValue() + amount;
        if (next >= stack.getMaxDamage()) stack.shrink(1); else stack.setDamageValue((int)next);
    }
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        // Virtual gem slots must never also be dropped beside the socketed equipment.
        int count = isGemStation() ? 1 : 9;
        for (int i = 0; i < count; i++) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, items.get(i));
        items.clear(); status = 0; setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("progress", progress); tag.putInt("total", total); tag.putInt("fail_at", failAt);
        tag.putInt("status", status); tag.putBoolean("doomed", doomed);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); items = NonNullList.withSize(9, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, registries);
        progress = Math.max(0, tag.getInt("progress")); total = Math.clamp(tag.getInt("total"), 0, 10000);
        failAt = Math.clamp(tag.getInt("fail_at"), 1, Math.max(1, total)); status = Math.clamp(tag.getInt("status"), 0, 4); doomed = tag.getBoolean("doomed");
        if (status == 1 && (total == 0 || progress > total)) { status = 0; progress = total = 0; }
    }
}
