package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import java.util.Optional;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The shrieker and other native callers need the result of actual world admission. @author NingMeng123 */
@Mixin(SpawnUtil.class)
public abstract class SpawnUtilAdmissionMixin {
    @Inject(method = "trySpawnMob", at = @At("RETURN"), cancellable = true)
    private static void modernIte$requireJoinedMob(CallbackInfoReturnable<Optional<Mob>> result) {
        Mob mob = result.getReturnValue().orElse(null);
        if (mob == null || mob.isAddedToLevel() && !mob.isRemoved()) return;

        mob.discard();
        SpawnPopulation.release(mob);
        result.setReturnValue(Optional.empty());
    }
}
