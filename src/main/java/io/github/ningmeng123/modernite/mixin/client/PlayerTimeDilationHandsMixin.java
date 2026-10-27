package io.github.ningmeng123.modernite.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.PlayerTimeDilationRendering;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person equip, swing, eating and bow poses use the slow player's render time. @author NingMeng123 */
@Mixin(ItemInHandRenderer.class)
public abstract class PlayerTimeDilationHandsMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void modernite$equipStep(CallbackInfo ci) {
        if (!TimeDilation.playerStep(Minecraft.getInstance().player)) ci.cancel();
    }

    @ModifyVariable(method = "renderHandsWithItems", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modernite$partial(float partial, float original, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light) {
        return PlayerTimeDilationRendering.partial(player, partial);
    }
}
