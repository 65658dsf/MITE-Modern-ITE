package io.github.ningmeng123.modernite.content;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
/** Native furnace interaction with the shared MITE thermal controller. @author NingMeng123 */
public final class VibraniumFurnaceBlock extends FurnaceBlock {
    public VibraniumFurnaceBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new VibraniumFurnaceEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createFurnaceTicker(level,type,ExtremeContent.FURNACE_ENTITY.get()); }
    @Override protected void openContainer(Level level,BlockPos pos,Player player) {
        if (!FurnaceThermalController.frontAccessible(level,pos,level.getBlockState(pos))) return;
        // FurnaceBlock only opens vanilla FurnaceBlockEntity instances.
        if (level.getBlockEntity(pos) instanceof VibraniumFurnaceEntity furnace) {
            player.openMenu(furnace);
            player.awardStat(Stats.INTERACT_WITH_FURNACE);
        }
    }
}
