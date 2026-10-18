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

/** Consumed at the clicked position; expands the existing growth system by one level. @author NingMeng123 */
public final class GrinderBlock extends Block {
    public GrinderBlock(Properties properties) { super(properties); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ItemStack stack = player.getMainHandItem();
        if (!ExtremeGear.socketable(stack) || GearProgression.maxLevel(stack) <= 0) return InteractionResult.PASS;
        int uses = stack.getOrDefault(ExtremeGear.GRINDINGS.get(), 0);
        if (uses >= 3) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.modern_ite.grinder_full"), true);
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            stack.set(ExtremeGear.GRINDINGS.get(), uses + 1);
            GearProgression.ensureId(stack);
            level.removeBlock(pos, false);
            player.getInventory().setChanged();
            player.displayClientMessage(Component.translatable("message.modern_ite.grinder_used", uses + 1), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
