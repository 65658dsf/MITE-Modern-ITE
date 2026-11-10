package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.GemInspectionSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolve a render-only gem placeholder without moving or replacing inventory items. @author NingMeng123 */
@Pseudo
@Mixin(targets = "punchy.client.animation.PunchyAnimationManager", remap = false)
public abstract class GemInspectionPunchyCompatibilityMixin {
    @Shadow private static HumanoidArm activeSourceArm;

    @Inject(method = "updateItemOverrides(Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"),
            remap = false, require = 0)
    private static void modernite$captureInspectionSource(Minecraft minecraft, CallbackInfo callback) {
        GemInspectionSource.beforeOverrideUpdate(minecraft, activeSourceArm);
    }

    @Inject(method = "resolveItemStack(Ljava/lang/String;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private static void modernite$resolveInspectionSource(String itemId, CallbackInfoReturnable<ItemStack> callback) {
        ItemStack copy = GemInspectionSource.resolve(itemId);
        if (copy != null) callback.setReturnValue(copy);
    }
}
