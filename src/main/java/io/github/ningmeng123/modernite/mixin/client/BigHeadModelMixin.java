package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.content.ExtremeEffects;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply after vanilla copies the head pose, and reset shared player models. @author NingMeng123 */
@Mixin(HumanoidModel.class)
public abstract class BigHeadModelMixin {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void modernite$head(LivingEntity entity, float walk, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player)) return;
        float scale = entity.hasEffect(ExtremeEffects.VALUES.get("big_head")) ? 2F : 1F;
        head.xScale = head.yScale = head.zScale = scale;
        hat.xScale = hat.yScale = hat.zScale = scale;
    }
}
