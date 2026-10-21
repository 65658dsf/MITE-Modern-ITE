package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Reject foreign mining before removal, drops or tool wear, including creative mode. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class ResurrectionProtectionEvents {
    private ResurrectionProtectionEvents() {}

    @SubscribeEvent public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT) return;
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ResurrectionBlock
                && !ResurrectionBlock.canBreak(event.getLevel(), event.getPos(), event.getEntity())) {
            event.setCanceled(true);
            if (!event.getLevel().isClientSide && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)
                event.getEntity().displayClientMessage(Component.translatable("message.modern_ite.array_break_denied"), true);
        }
    }

    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof ResurrectionBlock
                && !ResurrectionBlock.canBreak(event.getLevel(), event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.modern_ite.array_break_denied"), true);
        }
    }
}
