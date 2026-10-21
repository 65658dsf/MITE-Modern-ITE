package io.github.ningmeng123.modernite.mixin.client;

import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enlarge the native head and its shared emissive-eye geometry. @author NingMeng123 */
@Mixin(EndermanModel.class)
public abstract class EndermanHeadModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void modernite$largeHead(LivingEntity entity, float walk, float amount, float age,
                                    float yaw, float pitch, CallbackInfo ci) {
        var model = (HumanoidModel<?>) (Object) this;
        // Assign instead of multiplying: this model is reused by every enderman and eye pass.
        model.head.xScale = model.head.yScale = model.head.zScale = 2F;
        model.hat.xScale = model.hat.yScale = model.hat.zScale = 2F;
    }
}
