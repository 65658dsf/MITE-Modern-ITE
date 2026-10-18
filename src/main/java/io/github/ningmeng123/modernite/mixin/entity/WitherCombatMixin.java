package io.github.ningmeng123.modernite.mixin.entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** The source 600-tick charge and its boss-bar denominator must agree. @author NingMeng123 */
@Mixin(WitherBoss.class)
public class WitherCombatMixin {
    @ModifyConstant(method="makeInvulnerable",constant=@Constant(intValue=220))
    private int modernIte$charge(int original){return 600;}
    @ModifyConstant(method="customServerAiStep",constant=@Constant(floatValue=220F))
    private float modernIte$chargeProgress(float original){return 600F;}
}
