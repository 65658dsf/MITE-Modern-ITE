package io.github.ningmeng123.modernite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.entity.VibraniumCosmicAccelerationCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Only cosmic wearers bypass AR batching, with stack restoration even on a failed draw. @author NingMeng123 */
@Mixin(LivingEntityRenderer.class)
public abstract class VibraniumCosmicAccelerationMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void modernite$orderedCosmicWearer(LivingEntity entity, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        boolean scoped = VibraniumCosmicAccelerationCompat.begin(entity);
        try {
            original.call(entity, yaw, partialTick, pose, buffers, light);
        } finally {
            if (scoped) VibraniumCosmicAccelerationCompat.end();
        }
    }
}
