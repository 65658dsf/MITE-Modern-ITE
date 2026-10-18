package io.github.ningmeng123.modernite.mixin.entity;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.entity.*;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Maps inherited source behavior, without aliasing any registered entity identity. @author NingMeng123 */
@Mixin(R196MobSpawning.class)
public class SourceMobIdentityMixin {
    @Inject(method="sourceClass(Lnet/minecraft/world/entity/Mob;)Ljava/lang/String;",at=@At("HEAD"),cancellable=true)
    private static void modernIte$source(Mob mob,CallbackInfoReturnable<String> cir){
        if(mob instanceof ExtremeMob e)cir.setReturnValue(e.extremeKind()==ExtremeKind.RED_GHOUL?"EntityGhoul":switch(e.extremeKind().family){
            case ZOMBIE -> "tw";case SKELETON -> "tr";case CREEPER -> "tf";case WITCH -> "tv";
            case GHAST -> "tj";case GOLEM -> "sd";case PIGMAN -> "tn";default -> null;
        });
    }
}
