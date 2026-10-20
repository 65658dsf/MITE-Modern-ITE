package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.progression.GearProgression;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Legacy grinding blocks remain placeable; forging now controls growth extensions. @author NingMeng123 */
public final class GrinderBlock extends Block {
    public GrinderBlock(Properties properties) { super(properties); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ItemStack stack = player.getMainHandItem();
        if (!ExtremeGear.socketable(stack) || GearProgression.maxLevel(stack) <= 0) return InteractionResult.PASS;
        if (!level.isClientSide)
            player.displayClientMessage(Component.translatable("message.modern_ite.grinder_forging_cap"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
