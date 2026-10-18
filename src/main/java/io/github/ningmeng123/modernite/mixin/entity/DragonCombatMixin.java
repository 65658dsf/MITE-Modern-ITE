package io.github.ningmeng123.modernite.mixin.entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Retains modern dragon phases while changing the source collision and crystal constants. @author NingMeng123 */
@Mixin(EnderDragon.class)
public class DragonCombatMixin {
    @ModifyConstant(method="hurt(Ljava/util/List;)V",constant=@Constant(floatValue=10F))
    private float modernIte$contact(float old){var d=(EnderDragon)(Object)this;return d.getHealth()<d.getMaxHealth()/2?85:60;}
    @ModifyConstant(method="knockBack",constant=@Constant(doubleValue=4D))
    private double modernIte$knock(double old){return old*1.5;}
    @ModifyArg(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),index=1)
    private Vec3 modernIte$flight(Vec3 move){return move.scale(1.3);}
    @ModifyConstant(method="checkCrystals",constant=@Constant(doubleValue=32D))
    private double modernIte$crystalRange(double old){return 48;}
    @ModifyConstant(method="checkCrystals",constant=@Constant(floatValue=1F))
    private float modernIte$crystalHeal(float old){return 2;}
    @ModifyConstant(method="onCrystalDestroyed",constant=@Constant(floatValue=10F))
    private float modernIte$crystalDamage(float old){return 5;}
    @ModifyConstant(method="hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",constant=@Constant(floatValue=4F))
    private float modernIte$bodyDivisor(float old){return 5F;}
    @ModifyConstant(method="hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",constant=@Constant(floatValue=1F,ordinal=0))
    private float modernIte$bodyFloor(float old){return .5F;}
}
