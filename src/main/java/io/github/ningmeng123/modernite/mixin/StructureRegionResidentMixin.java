package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import io.github.ningmeng123.modernite.world.StructureResidents;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Completed structure finalization precedes main-thread population admission. @author NingMeng123 */
@Mixin(WorldGenRegion.class)
public abstract class StructureRegionResidentMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void modernIte$deferFreshResident(Entity entity, CallbackInfoReturnable<Boolean> result) {
        WorldGenRegion region = (WorldGenRegion)(Object)this;
        if (!StructureResidents.active(region) || !(entity instanceof Mob mob) || mob.isRemoved() || mob.isSpawnCancelled()
                || mob.getSpawnType() != MobSpawnType.STRUCTURE || !SpawnPopulation.hostile(mob.getType())) return;

        StructureResidents.deferFresh(mob);
        result.setReturnValue(false);
    }
}
