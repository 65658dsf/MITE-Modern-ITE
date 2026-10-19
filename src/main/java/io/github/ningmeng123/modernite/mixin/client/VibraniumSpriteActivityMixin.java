package io.github.ningmeng123.modernite.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.compat.VibraniumSpriteActivity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before cached item draws can skip Sodium's ordinary per-quad activity marker. @author NingMeng123 */
@Mixin(ItemRenderer.class)
public abstract class VibraniumSpriteActivityMixin {
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"))
    private void modernite$markRenderedVibranium(ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        VibraniumSpriteActivity.markRendered(stack, model);
    }
}
