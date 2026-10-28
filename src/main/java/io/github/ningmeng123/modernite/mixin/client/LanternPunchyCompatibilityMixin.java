package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Let the lantern own its carrying hand through Punchy's per-hand fallback. @author NingMeng123 */
@Pseudo
@Mixin(targets = "punchy.config.PunchyConfig", remap = false)
public abstract class LanternPunchyCompatibilityMixin {
    @Inject(method = "isItemBlacklisted(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
        cancellable = true, remap = false, require = 0)
    private static void modernite$lanternOwnsHand(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.getItem() instanceof DynamicCoreItem) cir.setReturnValue(true);
    }
}
