package io.github.ningmeng123.modernite.world;

import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;

/** Preserve native structure residents whose placement deliberately skips finalization. @author NingMeng123 */
public final class StructureResidents {
    private static final ThreadLocal<WorldGenLevel> GENERATING = new ThreadLocal<>();

    private StructureResidents() {}

    @FunctionalInterface public interface Scope extends AutoCloseable { @Override void close(); }

    public static Scope generation(WorldGenLevel level) {
        WorldGenLevel previous = GENERATING.get();
        GENERATING.set(level);
        return () -> {
            if (previous == null) GENERATING.remove();
            else GENERATING.set(previous);
        };
    }

    public static boolean active(ServerLevelAccessor level) { return GENERATING.get() == level; }

    public static boolean deferUnfinalized(ServerLevelAccessor access, Entity entity) {
        if (!(entity instanceof Mob mob) || !SpawnPopulation.hostile(mob.getType()) || mob.getSpawnType() != null) return false;
        ((MobSpawnTypeAccessor)(Object)mob).mite$setSpawnType(MobSpawnType.STRUCTURE);
        if (!(access instanceof WorldGenRegion) && access.getLevel().getServer().isSameThread()) return false;

        // Proto-chunk inhabitants load as existing entities. Admit fresh worker residents on the main thread.
        deferFresh(mob);
        return true;
    }

    public static void retainRejected(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.getSpawnType() != MobSpawnType.STRUCTURE
                || !SpawnPopulation.hostile(mob.getType()) || mob.isAddedToLevel() || mob.isRemoved()) return;
        deferFresh(mob);
    }

    public static void deferFresh(Mob mob) {
        if (mob.isAddedToLevel() || mob.isRemoved()) return;
        // A late join cancellation can run after the population listener provisionally admits this fresh entity.
        mob.getPersistentData().remove("modern_ite:spawn_admitted");
        DeferredStructureSpawns.defer(mob, false);
        mob.discard();
        SpawnPopulation.release(mob);
    }
}
