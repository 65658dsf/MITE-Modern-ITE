package io.github.ningmeng123.modernite.entity;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** Wait on real server ticks, keeping fixture cleanup on both completion and timeout. @author NingMeng123 */
final class InfernalExplosionGameTestSupport {
    private InfernalExplosionGameTestSupport() {}
    static Runnable holdChunks(ServerLevel level, AABB bounds) {
        var held = new java.util.ArrayList<ChunkPos>();
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++)
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (level.getForcedChunks().contains(ChunkPos.asLong(x, z))) continue;
                level.setChunkForced(x, z, true);
                held.add(new ChunkPos(x, z));
            }
        return () -> held.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
    }
    static void finishAfterJobs(GameTestHelper h, ServerLevel level, Runnable assertions, Runnable cleanup) {
        await(h, level, 1000, assertions, cleanup);
    }
    static void whenEntityVisible(GameTestHelper h, ServerLevel level, net.minecraft.world.entity.Entity entity,
                                  AABB bounds, Runnable action, Runnable cleanup) {
        awaitEntity(h, level, entity, bounds, 100, action, cleanup);
    }
    static void whenPositionsEntityTicking(GameTestHelper h, ServerLevel level, java.util.List<net.minecraft.core.BlockPos> positions,
                                          Runnable action, Runnable cleanup) {
        awaitPositions(h, level, positions, 100, action, cleanup);
    }
    private static void awaitPositions(GameTestHelper h, ServerLevel level, java.util.List<net.minecraft.core.BlockPos> positions,
                                       int remaining, Runnable action, Runnable cleanup) {
        h.runAfterDelay(1, () -> {
            // A synchronous FULL chunk is not sufficient: entity sections are indexed only after
            // the forced ticket has promoted the chunk's real entity-ticking status.
            boolean ready=positions.stream().allMatch(level::isPositionEntityTicking);
            if (!ready && remaining > 0) {
                awaitPositions(h, level, positions, remaining - 1, action, cleanup);
                return;
            }
            try {
                h.assertTrue(ready, "Both real target positions are entity-ticking before entity admission");
                action.run();
            } catch (Throwable failure) { cleanup.run(); throw failure; }
        });
    }
    private static void awaitEntity(GameTestHelper h, ServerLevel level, net.minecraft.world.entity.Entity entity,
                                    AABB bounds, int remaining, Runnable action, Runnable cleanup) {
        h.runAfterDelay(1, () -> {
            boolean visible=level.getEntitiesOfClass(entity.getClass(), bounds).contains(entity);
            if (!visible && remaining > 0) {
                awaitEntity(h, level, entity, bounds, remaining - 1, action, cleanup);
                return;
            }
            try {
                h.assertTrue(visible, "The real entity is query-visible before the explosion selects its targets");
                action.run();
            } catch (Throwable failure) { cleanup.run(); throw failure; }
        });
    }
    private static void await(GameTestHelper h, ServerLevel level, int remaining, Runnable assertions, Runnable cleanup) {
        h.runAfterDelay(1, () -> {
            if (InfernalExplosionDestruction.remainingJobs(level) > 0 && remaining > 0) {
                await(h, level, remaining - 1, assertions, cleanup);
                return;
            }
            try {
                h.assertTrue(InfernalExplosionDestruction.remainingJobs(level) == 0, "The bounded destruction queue finishes on real server ticks");
                assertions.run();
                h.succeed();
            } finally { cleanup.run(); }
        });
    }
}
