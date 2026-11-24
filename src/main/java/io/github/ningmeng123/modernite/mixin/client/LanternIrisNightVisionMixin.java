package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.content.ExtremeLifeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies the lantern's night vision to Iris shader packs. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class LanternIrisNightVisionMixin {
    @Inject(method = "getNightVision()F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void modernite$includeLanternNightVision(CallbackInfoReturnable<Float> cir) {
        if (Minecraft.getInstance().getCameraEntity() instanceof LocalPlayer player
                && ExtremeLifeEvents.coreLevel(player) > 0) {
            cir.setReturnValue(1.0F);
        }
    }
}
