package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.entity.ShaderPackSurfaceRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separates world surfaces from GUI previews. @author NingMeng123 */
@Mixin(GameRenderer.class)
public abstract class ShaderPackSurfaceFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void modernite$beginSurfaceFrame(CallbackInfo ci) {
        ShaderPackSurfaceRenderer.beginWorld();
    }
}
