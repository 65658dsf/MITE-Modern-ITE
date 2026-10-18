package io.github.ningmeng123.modernite.mixin.entity;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Reuses the current source pathing, doors, target selection and blood-moon frenzy. @author NingMeng123 */
@Mixin(R196MonsterAi.class)
public class SourceMobAiMixin {
    @Inject(method="managed",at=@At("HEAD"),cancellable=true)
    private static void modernIte$managed(PathfinderMob mob,CallbackInfoReturnable<Boolean> cir){
        if(mob.getType()==EntityType.ZOMBIFIED_PIGLIN)cir.setReturnValue(true);
        if(mob instanceof ExtremeMob e)switch(e.extremeKind().family){case ZOMBIE,SKELETON,CREEPER,WITCH,PIGMAN -> cir.setReturnValue(true);default -> {}}
    }
    @Inject(method="monster",at=@At("HEAD"),cancellable=true)
    private static void modernIte$monster(Mob mob,CallbackInfoReturnable<Boolean> cir){
        if(mob instanceof ExtremeMob e)switch(e.extremeKind().family){case ZOMBIE,SKELETON,CREEPER,WITCH,PIGMAN -> cir.setReturnValue(true);default -> {}}
    }
}
