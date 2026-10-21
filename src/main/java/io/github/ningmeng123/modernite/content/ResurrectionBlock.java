package io.github.ningmeng123.modernite.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One persistent resurrection position per player. @author NingMeng123 */
public final class ResurrectionBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(0, 0, 0, 16, 5, 16), Block.box(0.5, 5, 0.5, 15.5, 16, 15.5));
    public ResurrectionBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ResurrectionBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    static boolean canBreak(BlockGetter level, BlockPos pos, Player player) {
        // A legacy save may only have the owner's player-side anchor until their first interaction.
        if (level.getBlockEntity(pos) instanceof ResurrectionBlockEntity array && !array.hasOwner()
                && player instanceof ServerPlayer serverPlayer) ExtremeLifeEvents.resolveAnchor(serverPlayer);
        return level.getBlockEntity(pos) instanceof ResurrectionBlockEntity array && array.isOwner(player);
    }
    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return canBreak(level, pos, player) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                                boolean willHarvest, FluidState fluid) {
        return canBreak(level, pos, player) && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ResurrectionBlockEntity array) {
            player.displayClientMessage(Component.translatable(array.hasOwner() ? "message.modern_ite.array_owner" : "message.modern_ite.array_unbound", array.ownerName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player&&ExtremeLifeEvents.resolveAnchor(player).isPresent()) {
            player.displayClientMessage(Component.translatable("message.modern_ite.array_exists"),true);return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack) {
        super.setPlacedBy(level,pos,state,entity,stack);
        if(level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        var data=player.getPersistentData();
        var previous=ExtremeLifeEvents.resolveAnchor(player);
        if(previous.isPresent()&&!(previous.get().level()==level&&previous.get().position().equals(pos))) { level.destroyBlock(pos,!player.isCreative()); player.displayClientMessage(Component.translatable("message.modern_ite.array_exists"),true); return; }
        if (level.getBlockEntity(pos) instanceof ResurrectionBlockEntity array) array.setOwner(player);
        CompoundTag anchor=new CompoundTag(); anchor.putString("dimension",level.dimension().location().toString()); anchor.putLong("position",pos.asLong()); data.put(ExtremeLifeEvents.ANCHOR,anchor);
        player.displayClientMessage(Component.translatable("message.modern_ite.array_bound"),true);
    }
}
