package io.github.ningmeng123.modernite.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.PlayerTimeDilationRendering;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Limb motion and swing poses interpolate over the player's logical tick. @author NingMeng123 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerTimeDilationModelMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float modernite$partial(float partial, AbstractClientPlayer player, float yaw, float original, PoseStack pose, MultiBufferSource buffers, int light) {
        return PlayerTimeDilationRendering.partial(player, partial);
    }
}
