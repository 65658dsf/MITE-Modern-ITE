package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.BossSummonedMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Marks Bone Crown's skeletons before they enter the world. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.witherplus.wither.skill.BoneCrownSkill", remap = false)
public abstract class WitherPlusBoneCrownMixin {
    @Redirect(method = "summon(Lnet/minecraft/world/phys/Vec3;)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/EntityType;spawn(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;"))
    private Entity modernIte$markBoneCrownSkeleton(EntityType<? extends Mob> type, ServerLevel level,
            BlockPos pos, MobSpawnType spawnType) {
        return type.spawn(level, BossSummonedMobs::mark, pos, spawnType, false, false);
    }
}
