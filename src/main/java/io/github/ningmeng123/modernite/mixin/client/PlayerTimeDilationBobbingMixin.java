package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.client.PlayerTimeDilationRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Walking camera bob follows the same continuous slow-player phase. @author NingMeng123 */
@Mixin(GameRenderer.class)
public abstract class PlayerTimeDilationBobbingMixin {
    @ModifyVariable(method = "bobView", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modernite$bob(float partial) {
        return PlayerTimeDilationRendering.partial(Minecraft.getInstance().player, partial);
    }
}
