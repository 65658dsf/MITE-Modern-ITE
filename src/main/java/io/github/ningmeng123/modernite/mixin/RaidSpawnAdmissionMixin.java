package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Failed world admission must release the raid's preceding wave registration. @author NingMeng123 */
@Mixin(Raid.class)
public abstract class RaidSpawnAdmissionMixin {
    @Shadow private float totalHealth;

    @WrapOperation(method = "joinRaid", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/raid/Raid;addWaveMob(ILnet/minecraft/world/entity/raid/Raider;)Z"))
    private boolean modernIte$captureWaveHealth(Raid raid, int wave, Raider raider, Operation<Boolean> original,
            @Share("modernIte$waveHealth") LocalFloatRef contribution) {
        float previous = totalHealth;
        boolean added = original.call(raid, wave, raider);
        contribution.set(totalHealth - previous);
        return added;
    }

    @WrapOperation(method = "joinRaid", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private void modernIte$releaseRejectedRaider(ServerLevel level, Entity entity, Operation<Void> original,
            @Share("modernIte$waveHealth") LocalFloatRef contribution) {
        original.call(level, entity);
        if (entity.isAddedToLevel() && !entity.isRemoved()) return;

        Raider raider = (Raider) entity;
        Raid raid = (Raid) (Object) this;
        if (raid.getLeader(raider.getWave()) == raider) raid.removeLeader(raider.getWave());
        // Finalization can change this candidate's health or admit other raid members reentrantly.
        totalHealth -= contribution.get();
        raid.removeFromRaid(raider, false);
        raider.discard();
        SpawnPopulation.release(raider);
    }
}
