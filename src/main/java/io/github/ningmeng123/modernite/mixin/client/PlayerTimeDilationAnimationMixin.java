package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.content.PlayerTimeDilationState;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Idle animation age advances only when the player's simulation advances. @author NingMeng123 */
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerTimeDilationAnimationMixin {
    @Inject(method = "getBob", at = @At("HEAD"), cancellable = true)
    private void modernite$age(LivingEntity entity, float partial, CallbackInfoReturnable<Float> cir) {
        if (entity instanceof Player player && TimeDilation.active(player))
            cir.setReturnValue(((PlayerTimeDilationState) player).modernite$timeVisualAge() + partial);
    }
}
