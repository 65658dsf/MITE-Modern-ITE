package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.modernite.world.StructureResidents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Unfinalized inhabitants retain their native state inside an actual structure placement. @author NingMeng123 */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateResidentMixin {
    @WrapOperation(method = "lambda$addEntitiesToWorld$5", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private static void modernIte$preserveResident(ServerLevelAccessor level, Entity entity, Operation<Void> original,
            @Local(argsOnly = true) StructurePlaceSettings settings) {
        if (!settings.shouldFinalizeEntities() && StructureResidents.active(level)) {
            if (StructureResidents.deferUnfinalized(level, entity)) return;
            original.call(level, entity);
            StructureResidents.retainRejected(entity);
        } else original.call(level, entity);
    }
}
