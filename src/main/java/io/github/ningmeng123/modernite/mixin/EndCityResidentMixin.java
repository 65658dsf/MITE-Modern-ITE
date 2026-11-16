package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.modernite.world.StructureResidents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** End-city sentries have a structure origin without a native finalization callback. @author NingMeng123 */
@Mixin(EndCityPieces.EndCityPiece.class)
public abstract class EndCityResidentMixin {
    @WrapOperation(method = "handleDataMarker", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean modernIte$preserveSentry(ServerLevelAccessor level, Entity entity, Operation<Boolean> original) {
        if (!(entity instanceof Shulker)) return original.call(level, entity);
        if (StructureResidents.deferUnfinalized(level, entity)) return false;
        boolean added = original.call(level, entity);
        StructureResidents.retainRejected(entity);
        return added;
    }
}
