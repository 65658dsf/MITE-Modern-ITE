package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.modernite.content.wither.WitherEncounters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WitherWallSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** WitherPlus marks standing skulls; wall skulls invoke their own summon check. @author NingMeng123 */
@Mixin(WitherWallSkullBlock.class)
public abstract class WitherWallSkullPlacementMixin {
    @Unique private static final ResourceLocation modernIte$specialSkull =
            ResourceLocation.fromNamespaceAndPath("witherplus", "special_wither_skeleton_skull");

    @Inject(method = "setPlacedBy", at = @At("HEAD"))
    private void modernIte$markWallSkull(Level level, BlockPos pos, BlockState state, LivingEntity placer,
            ItemStack stack, CallbackInfo ci) {
        if (modernIte$specialSkull.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                && level.getBlockEntity(pos) instanceof SkullBlockEntity skull) {
            skull.getPersistentData().putBoolean(WitherEncounters.SPECIAL_SKULL_TAG, true);
            skull.setChanged();
            if (!level.isClientSide) level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }
}
