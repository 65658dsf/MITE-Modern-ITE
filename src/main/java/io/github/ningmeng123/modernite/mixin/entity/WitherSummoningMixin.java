package io.github.ningmeng123.modernite.mixin.entity;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.modernite.content.wither.WitherEncounters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture all three consumed skulls before they disappear, in either orientation. @author NingMeng123 */
@Mixin(WitherSkullBlock.class)
public abstract class WitherSummoningMixin {
    @Inject(method = "checkSpawn(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/SkullBlockEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/CarvedPumpkinBlock;clearPatternBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/pattern/BlockPattern$BlockPatternMatch;)V"))
    private static void modernIte$identifySummon(Level level, BlockPos pos, SkullBlockEntity skull, CallbackInfo ci,
            @Local BlockPattern.BlockPatternMatch pattern, @Local WitherBoss wither) {
        boolean special = false;
        for (int x = 0; x < 3; x++) {
            if (pattern.getBlock(x, 0, 0).getEntity() instanceof SkullBlockEntity head
                    && head.getPersistentData().getBoolean("WitherPlusSpecial")) special = true;
        }
        WitherEncounters.markSummon(wither, special);
    }
}
