package io.github.ningmeng123.modernite.content;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
/** Single owner-bound vibranium strongbox. @author NingMeng123 */
public final class VibraniumChestBlock extends ChestBlock {
    public VibraniumChestBlock(Properties p) { super(p, () -> ExtremeContent.CHEST_ENTITY.get()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new VibraniumChestEntity(pos,state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return super.getStateForPlacement(context).setValue(TYPE,ChestType.SINGLE); }
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other) { return super.updateShape(state,side,neighbor,level,pos,other).setValue(TYPE,ChestType.SINGLE); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack) { super.setPlacedBy(level,pos,state,entity,stack); if(entity instanceof Player player && level.getBlockEntity(pos) instanceof VibraniumChestEntity chest) chest.setOwner(player); }
}
