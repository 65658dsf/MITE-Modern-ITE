package io.github.ningmeng123.modernite.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;

/** One persistent resurrection position per player. @author NingMeng123 */
public final class ResurrectionBlock extends Block {
    public ResurrectionBlock(Properties properties) { super(properties); }
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
        CompoundTag anchor=new CompoundTag(); anchor.putString("dimension",level.dimension().location().toString()); anchor.putLong("position",pos.asLong()); data.put("modern_ite:resurrection",anchor);
        player.displayClientMessage(Component.translatable("message.modern_ite.array_bound"),true);
    }
}
