package io.github.ningmeng123.modernite.mixin.client;
import io.github.ningmeng123.modernite.content.ExtremeLifeEvents;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Partial cores preserve the stronger effect of ordinary night-vision potions. @author NingMeng123 */
@Mixin(GameRenderer.class)
public abstract class CoreNightVisionMixin {
    @Inject(method="getNightVisionScale",at=@At("RETURN"),cancellable=true)
    private static void modernite$core(LivingEntity entity,float partialTick,CallbackInfoReturnable<Float> cir){if(entity instanceof Player player){var effect=player.getEffect(MobEffects.NIGHT_VISION);int level=ExtremeLifeEvents.coreLevel(player);if(effect!=null&&effect.getDuration()<=220&&level>0&&level<5)cir.setReturnValue(cir.getReturnValue()*level/5F);}}
}
