package io.github.ningmeng123.modernite.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The source gotcha light absorbs landings. @author NingMeng123 */
public final class CushionLightBlock extends Block {
    public CushionLightBlock(Properties properties) { super(properties); }
    @Override public void fallOn(Level level, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, Entity entity, float distance) { entity.causeFallDamage(distance, 0F, level.damageSources().fall()); }
}
