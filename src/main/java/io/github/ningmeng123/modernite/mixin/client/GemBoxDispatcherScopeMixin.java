package io.github.ningmeng123.modernite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.GemBoxArmPose;
import io.github.ningmeng123.modernite.client.GemBoxEmfPoseCompat;
import io.github.ningmeng123.modernite.client.GemBoxRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Preserve shared models before ETF/EMF activates and resets the next entity. @author NingMeng123 */
@Mixin(EntityRenderDispatcher.class)
public abstract class GemBoxDispatcherScopeMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void modernite$scopeDispatchedGemBoxPose(Entity entity, double x, double y, double z,
            float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            original.call(entity, x, y, z, yaw, partial, pose, buffers, light);
            return;
        }
        EntityRenderer<?> renderer = ((EntityRenderDispatcher)(Object)this).getRenderer(entity);
        if (!(renderer instanceof PlayerRenderer playerRenderer)
                || (!GemBoxArmPose.active(player) && !GemBoxRenderState.borrowed(playerRenderer.getModel()))) {
            original.call(entity, x, y, z, yaw, partial, pose, buffers, light);
            return;
        }
        var owner = (GemBoxRenderState.Owner)playerRenderer;
        try (GemBoxRenderState saved = owner.modernite$captureGemBoxRenderState();
                GemBoxEmfPoseCompat.PoseScope outerPose = GemBoxEmfPoseCompat.preserve(playerRenderer.getModel())) {
            original.call(entity, x, y, z, yaw, partial, pose, buffers, light);
        }
    }
}
