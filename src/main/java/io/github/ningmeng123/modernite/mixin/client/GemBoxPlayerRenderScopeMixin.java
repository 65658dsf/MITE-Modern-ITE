package io.github.ningmeng123.modernite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.GemBoxArmPose;
import io.github.ningmeng123.modernite.client.GemBoxEmfPoseCompat;
import io.github.ningmeng123.modernite.client.GemBoxRenderState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Shared player and armor poses belong to one box wearer, including failed draws. @author NingMeng123 */
@Mixin(PlayerRenderer.class)
public abstract class GemBoxPlayerRenderScopeMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
        implements GemBoxRenderState.Owner {
    protected GemBoxPlayerRenderScopeMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadow) {
        super(context, model, shadow);
    }

    @Override public GemBoxRenderState modernite$captureGemBoxRenderState() {
        return GemBoxRenderState.capture(getModel(), layers);
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void modernite$scopeGemBoxPose(AbstractClientPlayer entity, float yaw, float partial, PoseStack pose,
            MultiBufferSource buffers, int light, Operation<Void> original) {
        var playerModel = getModel();
        if (!GemBoxArmPose.active(entity) && !GemBoxRenderState.borrowed(playerModel)) {
            original.call(entity, yaw, partial, pose, buffers, light);
            return;
        }
        try (GemBoxRenderState saved = GemBoxRenderState.capture(playerModel, layers);
                GemBoxEmfPoseCompat.PoseScope outerPose = GemBoxEmfPoseCompat.preserve(playerModel)) {
            original.call(entity, yaw, partial, pose, buffers, light);
        }
    }
}
