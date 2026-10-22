package io.github.ningmeng123.modernite.item;

import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class WholeFlint {
    private WholeFlint() {}

    public static ItemStack replace(ItemStack stack) {
        return stack.is(MiteItems.FLINT_CHIP.get()) ? stack.transmuteCopy(Items.FLINT) : stack;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(BlockDropsEvent event) {
        event.getDrops().forEach(drop -> drop.setItem(replace(drop.getItem())));
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity drop)
            drop.setItem(replace(drop.getItem()));
    }

    @SubscribeEvent
    public static void inventory(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack before = player.getInventory().getItem(slot);
            ItemStack after = replace(before);
            if (after != before) {
                player.getInventory().setItem(slot, after);
                changed = true;
            }
        }
        for (var slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) continue;
            ItemStack before = slot.getItem();
            ItemStack after = replace(before);
            if (after != before) {
                slot.set(after);
                changed = true;
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack after = replace(carried);
        if (after != carried) {
            player.containerMenu.setCarried(after);
            changed = true;
        }
        if (changed) player.containerMenu.broadcastChanges();
    }

    public static void creative(BuildCreativeModeTabContentsEvent event) {
        Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream())
                .filter(stack -> stack.is(MiteItems.FLINT_CHIP.get())).toList()
                .forEach(stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }
}
