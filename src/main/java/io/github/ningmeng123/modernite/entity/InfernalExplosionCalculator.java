package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.R196ExplosionCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;

/** A physical spherical crater, with an independent R196 damage curve. @author NingMeng123 */
public final class InfernalExplosionCalculator extends EntityBasedExplosionDamageCalculator {
    public static final float RADIUS = 40F;
    private final float radius;
    private final R196ExplosionCalculator damage;

    public InfernalExplosionCalculator(Entity source, float radius) {
        super(source);
        this.radius = radius;
        this.damage = new R196ExplosionCalculator(source, radius / 4F);
    }

    public float radius() { return radius; }

    @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return entity.distanceToSqr(explosion.center()) < radius * radius && damage.shouldDamageEntity(explosion, entity);
    }

    @Override public float getEntityDamageAmount(Explosion explosion, Entity entity) {
        return shouldDamageEntity(explosion, entity) ? damage.getEntityDamageAmount(explosion, entity) : 0F;
    }

    @Override public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return pos.distToCenterSqr(explosion.center()) <= radius * radius
                && super.shouldBlockExplode(explosion, level, pos, state, power);
    }

    /** Called before the public Detonate event so protection mods may still remove any selected blocks. */
    public void prepareTerrain(ServerLevel level, Explosion explosion, boolean fire) {
        explosion.getToBlow().clear();
        if (!explosion.interactsWithBlocks()) return;
        var center = explosion.center();
        var cursor = new BlockPos.MutableBlockPos();
        double radiusSquared = (double)radius * radius;
        int minX = Mth.floor(center.x - radius), maxX = Mth.floor(center.x + radius);
        int minZ = Mth.floor(center.z - radius), maxZ = Mth.floor(center.z + radius);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(center.y - radius));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(center.y + radius));
        var chunkSource = level.getChunkSource();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                // A single non-loading lookup replaces per-block chunk availability and world lookups.
                var chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                int firstX = Math.max(minX, chunkX << 4), lastX = Math.min(maxX, (chunkX << 4) + 15);
                int firstZ = Math.max(minZ, chunkZ << 4), lastZ = Math.min(maxZ, (chunkZ << 4) + 15);
                for (int x = firstX; x <= lastX; x++) {
                    double dx = x + .5D - center.x;
                    double xSquared = dx * dx;
                    for (int z = firstZ; z <= lastZ; z++) {
                        double dz = z + .5D - center.z;
                        double horizontalSquared = xSquared + dz * dz;
                        if (horizontalSquared > radiusSquared) continue;
                        double halfHeight = Math.sqrt(radiusSquared - horizontalSquared);
                        // Keep a one-block numerical guard, then use the original exact center-distance test.
                        int firstY = Math.max(minY, Mth.ceil(center.y - halfHeight - .5D) - 1);
                        int lastY = Math.min(maxY, Mth.floor(center.y + halfHeight - .5D) + 1);
                        for (int y = firstY; y <= lastY; y++) {
                            cursor.set(x, y, z);
                            if (cursor.distToCenterSqr(center) > radiusSquared) continue;
                            var state = chunk.getBlockState(cursor);
                            if (state.isAir()) {
                                // Air over surviving terrain is also an affected location in the old blast.
                                // Expose it before Detonate so protection mods can exclude ignition too.
                                if (fire && cursor.getY() > level.getMinBuildHeight()) {
                                    var below = cursor.below();
                                    if (chunk.getBlockState(below).isSolidRender(level, below)
                                            && shouldBlockExplode(explosion, level, cursor, state, radius))
                                        explosion.getToBlow().add(cursor.immutable());
                                }
                                continue;
                            }
                            var fluid = state.getFluidState();
                            if (!fluid.isEmpty() || state.getDestroySpeed(level, cursor) < 0F) continue;
                            // Evaluate position-dependent mod hooks for every block; no resistance or hardness cache.
                            float resistance = getBlockExplosionResistance(explosion, level, cursor, state, fluid).orElse(0F);
                            if (resistance >= 1200F || !shouldBlockExplode(explosion, level, cursor, state, radius)) continue;
                            explosion.getToBlow().add(cursor.immutable());
                        }
                    }
                }
            }
        }
    }
}
