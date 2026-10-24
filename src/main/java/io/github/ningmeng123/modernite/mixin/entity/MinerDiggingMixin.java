package io.github.ningmeng123.modernite.mixin.entity;
import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.modernite.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Preserves actual source digging, including the miner's 4.8-fold stage speed. @author NingMeng123 */
@Mixin(R196DiggerGoal.class)
public class MinerDiggingMixin {
    @Shadow @Final private PathfinderMob mob;
    @Inject(method="canDestroyMaterial",at=@At("HEAD"),cancellable=true)
    private void modernIte$protectPumpkinTorches(BlockPos pos,CallbackInfoReturnable<Boolean> cir){
        if(mob instanceof ExtremeMob e&&e.extremeKind()==ExtremeKind.ZOMBIE_PUMPKIN
                &&mob.level().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BaseTorchBlock)
            cir.setReturnValue(false);
    }
    @Inject(method="watcher",at=@At("HEAD"),cancellable=true)
    private static void modernIte$pigman(PathfinderMob mob,CallbackInfoReturnable<Boolean> cir){if(mob instanceof net.minecraft.world.entity.monster.ZombifiedPiglin)cir.setReturnValue(true);}
    @Inject(method="stageDelay",at=@At("RETURN"),cancellable=true)
    private void modernIte$miner(BlockPos pos,CallbackInfoReturnable<Integer> cir){
        if(mob instanceof ExtremeMob e&&e.extremeKind()==ExtremeKind.MINER_ZOMBIE)cir.setReturnValue(Math.max(1,(int)(cir.getReturnValue()/4.8)));
    }
}
