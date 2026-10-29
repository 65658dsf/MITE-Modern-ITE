package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.modernite.gear.GemBoxItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Settle a revealed gem before a native inventory operation moves or splits its paying box. @author NingMeng123 */
@Mixin(value = AbstractContainerMenu.class, priority = 1200)
public abstract class GemBoxInventoryInteractionMixin {
    @Inject(method = "clicked", at = @At("HEAD"))
    private void modernite$settleOpeningBeforeMovingItems(int slot, int button, ClickType type, Player player, CallbackInfo callback) {
        GemBoxItem.interrupt(player);
    }
}
