package io.github.ningmeng123.modernite.content;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/** Persistent source heat-five furnace. @author NingMeng123 */
public final class VibraniumFurnaceEntity extends AbstractFurnaceBlockEntity {
    public VibraniumFurnaceEntity(BlockPos pos,BlockState state) { super(ExtremeContent.FURNACE_ENTITY.get(),pos,state,RecipeType.SMELTING); }
    @Override protected Component getDefaultName() { return Component.translatable("block.modern_ite.vibranium_furnace"); }
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory) { return new FurnaceMenu(id,inventory,this,dataAccess); }
}
