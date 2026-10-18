package io.github.ningmeng123.modernite.mixin.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explicit siege riders remain babies despite the source mod's ordinary-zombie age policy. @author NingMeng123 */
@Mixin(Zombie.class)
public abstract class SiegeRiderAgeMixin {
    @Shadow @Final private static EntityDataAccessor<Boolean> DATA_BABY_ID;
    @Shadow @Final private static AttributeModifier SPEED_MODIFIER_BABY;

    @Inject(method="setBaby",at=@At("RETURN"))
    private void modernIte$keepSiegeRiderSmall(boolean baby,CallbackInfo ci){
        Zombie zombie=(Zombie)(Object)this;
        if(!zombie.getPersistentData().getBoolean("modern_ite:siege_rider"))return;
        zombie.getEntityData().set(DATA_BABY_ID,true);
        if(!zombie.level().isClientSide){
            var speed=zombie.getAttribute(Attributes.MOVEMENT_SPEED);
            if(speed!=null){speed.removeModifier(SPEED_MODIFIER_BABY.id());speed.addTransientModifier(SPEED_MODIFIER_BABY);}
        }
    }
}
