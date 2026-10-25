package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.content.ExtremeEffects;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse vanilla upside-down rendering for the player and all equipment layers. @author NingMeng123 */
@Mixin(LivingEntityRenderer.class)
public abstract class ReversedPlayerModelMixin {
    @Inject(method = "isEntityUpsideDown", at = @At("HEAD"), cancellable = true)
    private static void modernite$reversedPlayer(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player && entity.hasEffect(ExtremeEffects.VALUES.get("reversed"))) {
            cir.setReturnValue(true);
        }
    }
}
