package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mark only the command's explicit root and NBT passengers before spawn callbacks. @author NingMeng123 */
@Mixin(SummonCommand.class)
public abstract class ExplicitSummonMixin {
    @ModifyExpressionValue(method="createEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityType;loadEntityRecursive(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/Level;Ljava/util/function/Function;)Lnet/minecraft/world/entity/Entity;"))
    private static Entity modernite$explicitTree(Entity root) {
        if(root!=null)root.getSelfAndPassengers().forEach(entity->entity.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND,true));
        return root;
    }
}
