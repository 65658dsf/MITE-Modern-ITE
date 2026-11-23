package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.item.GemSacrifice;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Includes red diamonds in the shared gem cooldown and preserves creative use. @author NingMeng123 */
@Mixin(value = GemSacrifice.class, remap = false)
public abstract class GemSacrificeMixin {
    @Inject(method = "onUse", at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$RightClickItem;getLevel()Lnet/minecraft/world/level/Level;"))
    private static void modernite$coolDownRedDiamond(PlayerInteractEvent.RightClickItem event, CallbackInfo callback) {
        event.getEntity().getCooldowns().addCooldown(ExtremeContent.FANCY_RED.get(), GemSacrifice.COOLDOWN_TICKS);
    }

    @WrapOperation(method = "onUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private static void modernite$preserveCreativeRedDiamond(ItemStack stack, int count, Operation<Void> shrink,
            @Local(argsOnly = true) PlayerInteractEvent.RightClickItem event) {
        if (!stack.is(ExtremeContent.FANCY_RED.get()) || !event.getEntity().isCreative()) {
            shrink.call(stack, count);
        }
    }
}
