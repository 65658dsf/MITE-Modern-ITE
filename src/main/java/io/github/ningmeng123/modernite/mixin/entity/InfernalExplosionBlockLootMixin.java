package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.entity.InfernalExplosionLoot;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoid loot-table work while retaining the block's actual explosion/removal callbacks. @author NingMeng123 */
@Mixin(BlockBehaviour.class)
public abstract class InfernalExplosionBlockLootMixin {
    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    private void modernIte$noInfernalBlockLoot(BlockState state, Level level, BlockPos pos, Explosion explosion,
                                              BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        if (InfernalExplosionLoot.isDestroying(explosion)) {
            if (!state.isAir() && explosion.getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK)
                state.onBlockExploded(level, pos, explosion);
            ci.cancel();
        }
    }
}
