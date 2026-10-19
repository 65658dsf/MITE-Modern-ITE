package io.github.ningmeng123.modernite.world;

import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One persistent grace period and a shared early-game daylight clock. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class ExtremeWorldState {
    private static final String DATA_NAME = "modern_ite_world_state";
    private static final long GRACE_END_TIME = 37_000L;
    private static final long FIRST_BLOOD_DAY = 32L;
    private static final Map<MinecraftServer, Snapshot> SNAPSHOTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Snapshot INITIAL = new Snapshot(false);

    private record Snapshot(boolean graceEnded) {}

    private ExtremeWorldState() {}

    /** Worker queries use only the last immutable server snapshot, never world saved data. */
    public static boolean graceActive(ServerLevel level) {
        return !graceEnded(level);
    }

    public static boolean graceEnded(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Snapshot known = SNAPSHOTS.get(server);
        if (known != null && known.graceEnded()) return true;
        if (server.isSameThread()) observe(level);
        return SNAPSHOTS.getOrDefault(server, INITIAL).graceEnded();
    }

    /** Publish on the main thread; the grace period cannot reopen after ordinary time changes. */
    public static void observe(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        State state = data(overworld);
        state.observe(overworld.getDayTime(), BloodMoonInvasion.highestDay(overworld));
        SNAPSHOTS.put(server, new Snapshot(state.graceEnded));
    }

    /** Exact restoration for isolated tests; normal gameplay only ends the grace period. */
    public static void restoreGraceEnded(ServerLevel level, boolean ended) {
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Grace state must be restored on the server thread");
        ServerLevel overworld = server.overworld();
        if (overworld == null) throw new IllegalStateException("The overworld is not loaded");
        State state = data(overworld);
        if (state.graceEnded != ended) {
            state.graceEnded = ended;
            state.setDirty();
        }
        SNAPSHOTS.put(server, new Snapshot(ended));
    }

    private static State data(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(State.FACTORY, DATA_NAME);
    }

    static boolean shouldEndGrace(boolean ended, long dayTime, long highestDay) {
        return ended || dayTime >= GRACE_END_TIME || highestDay >= 3L;
    }

    static float dayTimePerTick(long dayTime, long highestDay) {
        return io.github.ningmeng123.modernite.config.IteConfig.bool("first_day_longer_day_time") && highestDay < FIRST_BLOOD_DAY
                && LunarCalendar.daytime((int) Math.floorMod(dayTime, 24_000L)) ? .5F : -1F;
    }

    @SubscribeEvent
    public static void load(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) observe(level);
    }

    @SubscribeEvent
    public static void beforeLevelTick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        observe(level);
        float desired = dayTimePerTick(level.getDayTime(), BloodMoonInvasion.highestDay(level));
        // NeoForge owns fractional accumulation, persistence, client synchronization and stopped time.
        if (Float.compare(level.getDayTimePerTick(), desired) != 0) level.setDayTimePerTick(desired);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SNAPSHOTS.remove(event.getServer());
    }

    static final class State extends SavedData {
        static final Factory<State> FACTORY = new Factory<>(State::new, State::load);
        private boolean graceEnded;

        void observe(long dayTime, long highestDay) {
            if (!graceEnded && shouldEndGrace(false, dayTime, highestDay)) {
                graceEnded = true;
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("version", 1);
            tag.putBoolean("grace_ended", graceEnded);
            return tag;
        }

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.graceEnded = tag.getBoolean("grace_ended");
            return state;
        }
    }
}
