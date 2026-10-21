package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.MobSplitting;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Self-detonation discards the parent without the normal living death callback. @author NingMeng123 */
@Mixin(Creeper.class)
public abstract class CreeperSplittingMixin {
    @Inject(method="explodeCreeper",at=@At("TAIL"))
    private void modernite$splitAfterExplosion(CallbackInfo ci){
        // The parent blast and lingering cloud have completed before the offspring appear.
        MobSplitting.trySplit((Creeper)(Object)this);
    }
}
