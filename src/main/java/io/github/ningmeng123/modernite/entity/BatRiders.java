package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.ModernIte;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Extreme's one-in-five bat mounts share their rider's spawn admission. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class BatRiders {
    static final String PENDING_MOUNT = "modern_ite:pending_bat_mount";
    private record Ride(Mob rider, Mob bat) {}
    private static final Map<FinalizeSpawnEvent, Ride> FINALIZING = new WeakHashMap<>();
    private static final Map<Mob, WeakReference<Mob>> FRESH_MOUNTS = new WeakHashMap<>();

    private BatRiders() {}

    static void finalizeRider(FinalizeSpawnEvent event) {
        Mob rider = event.getEntity();
        if (event.isCanceled() || event.isSpawnCancelled() || rider.isPassenger()
                || !(rider.level() instanceof ServerLevel level)) return;
        EntityType<? extends Mob> mountType = mountType(rider);
        if (mountType == null || rider.getRandom().nextInt(5) != 0) return;
        if (!level.getServer().isSameThread()) {
            rider.getPersistentData().putString(PENDING_MOUNT, BuiltInRegistries.ENTITY_TYPE.getKey(mountType).toString());
            return;
        }
        Mob bat = createMount(rider, mountType, event.getSpawnType());
        if (bat != null) FINALIZING.put(event, new Ride(rider, bat));
    }

    private static Mob createMount(Mob rider, EntityType<? extends Mob> mountType, MobSpawnType reason) {
        ServerLevel level = (ServerLevel)rider.level();
        Mob bat = mountType.create(level);
        if (bat == null) return null;
        bat.moveTo(rider.getX(), rider.getY(), rider.getZ(), rider.getYRot(), 0F);
        bat.getPersistentData().putUUID(SpawnPopulation.SUMMONER, rider.getUUID());
        boolean enlisted = R196MobSpawning.enlistNaturalSpawn(rider, bat);
        boolean mounted = false;
        try {
            boolean explicit = SpawnPopulation.explicit(rider);
            if (explicit) bat.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true);
            if (!explicit && !SpawnPopulation.reserveSupport(bat, rider, false)) return null;
            EventHooks.finalizeMobSpawn(bat, level, level.getCurrentDifficultyAt(bat.blockPosition()), reason, null);
            if (bat.isRemoved() || bat.isSpawnCancelled() || rider.isRemoved() || rider.isSpawnCancelled()) return null;
            bat.setTarget(rider.getTarget());
            if (bat instanceof Bat flying) flying.setResting(false);
            if (!rider.startRiding(bat, true)) return null;
            if (!enlisted && !level.addFreshEntity(bat)) return null;
            mounted = true;
            FRESH_MOUNTS.put(bat, new WeakReference<>(rider));
            return bat;
        } finally {
            if (!mounted) discard(rider, bat);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void deferredJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob rider) || !(event.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return;
        var data = rider.getPersistentData();
        if (!data.contains(PENDING_MOUNT)) return;
        ResourceLocation id = ResourceLocation.tryParse(data.getString(PENDING_MOUNT));
        data.remove(PENDING_MOUNT);
        if (event.isCanceled() || rider.isPassenger() || rider.isRemoved() || rider.isSpawnCancelled()) return;
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type != EntityType.BAT && type != MiteMobs.type(MobKind.VAMPIRE_BAT)) return;
        @SuppressWarnings("unchecked") EntityType<? extends Mob> mountType = (EntityType<? extends Mob>)type;
        createMount(rider, mountType, rider.getSpawnType() == null ? MobSpawnType.STRUCTURE : rider.getSpawnType());
    }

    static EntityType<? extends Mob> mountType(Mob rider) {
        if (rider instanceof Creeper) {
            if (rider.getType() == MiteMobs.type(MobKind.INFERNAL_CREEPER)
                    || rider instanceof ExtremeMob extreme && extreme.extremeKind() == ExtremeKind.BLACK_INFERNAL_CREEPER) return null;
            return MiteMobs.type(MobKind.VAMPIRE_BAT);
        }
        if (!(rider instanceof AbstractSkeleton)) return null;
        boolean vampire = rider instanceof WitherSkeleton
                || rider instanceof R196Skeleton sourceSkeleton && sourceSkeleton.skeletonType() > 0
                || rider instanceof OrdinarySkeletonState ordinarySkeleton && ordinarySkeleton.mite$sourceSkeletonType() > 0;
        return vampire ? MiteMobs.type(MobKind.VAMPIRE_BAT) : EntityType.BAT;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void finishFinalization(FinalizeSpawnEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        Ride ride = FINALIZING.remove(event);
        if (ride != null && (event.isCanceled() || event.isSpawnCancelled()
                || ride.rider().isRemoved() || ride.rider().isSpawnCancelled())) discard(ride.rider(), ride.bat());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void finishJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !event.isCanceled() || !(event.getEntity() instanceof Mob rider) || rider.isAddedToLevel()
                || !(rider.getVehicle() instanceof Bat bat)) return;
        WeakReference<Mob> freshOwner = FRESH_MOUNTS.get(bat);
        if (freshOwner != null && freshOwner.get() == rider) discard(rider, bat);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void settleMount(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Bat bat) || !(bat.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return;
        WeakReference<Mob> freshOwner = FRESH_MOUNTS.remove(bat);
        if (freshOwner == null) return;
        Mob rider = freshOwner.get();
        if (rider == null || rider.isRemoved() || !rider.isAddedToLevel()) discard(rider, bat);
    }

    private static void discard(Mob rider, Mob bat) {
        FRESH_MOUNTS.remove(bat);
        if (rider != null && rider.getVehicle() == bat) rider.stopRiding();
        bat.discard();
        SpawnPopulation.release(bat);
    }
}
