package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.GemBoxArmPose;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Let the shared chest carry own both hands before Punchy's per-hand rendering. @author NingMeng123 */
@Pseudo
@Mixin(targets = "punchy.config.PunchyConfig", remap = false)
public abstract class GemBoxPunchyCompatibilityMixin {
    @Inject(method = "isItemBlacklisted(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private static void modernite$gemBoxOwnsBothHands(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && (GemBoxArmPose.active(player)
                || (GemBoxArmPose.hiddenForMovement(player) && stack != null && stack.is(ExtremeGear.GEM_BOX.get())))) {
            callback.setReturnValue(true);
        }
    }
}
