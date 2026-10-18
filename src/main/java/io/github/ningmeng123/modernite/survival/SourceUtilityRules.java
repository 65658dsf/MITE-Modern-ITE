package io.github.ningmeng123.modernite.survival;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Short lived source beacon assistance; normal craft validation and spawner activation remain authoritative. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class SourceUtilityRules {
    private record Source(ResourceKey<Level> dimension,BlockPos position) {}
    private record Pulse(long expires,float factor) {}
    private static final Map<UUID,Map<Source,Pulse>> BEACONS=new HashMap<>();
    private SourceUtilityRules() {}

    public static float beaconFactor(int level) {
        return switch(level){case 1->.25F;case 2->.4F;case 3->.6F;case 4->.85F;default->0F;};
    }
    /** Invoked only when the real beacon applies its selected native power to players. */
    public static void beaconPulse(ServerLevel level,BlockPos pos,int rank) {
        float factor=beaconFactor(rank);if(factor<=0)return;
        double range=rank*10+10;
        AABB bounds=new AABB(pos).inflate(range).expandTowards(0,level.getHeight(),0);
        Source source=new Source(level.dimension(),pos.immutable());long now=level.getGameTime();
        for(ServerPlayer player:level.getEntitiesOfClass(ServerPlayer.class,bounds,player->!player.isSpectator()&&player.isAlive())) {
            Map<Source,Pulse> pulses=BEACONS.computeIfAbsent(player.getUUID(),ignored->new HashMap<>());
            pulses.entrySet().removeIf(entry->!entry.getKey().dimension().equals(level.dimension())||entry.getValue().expires()<=now);
            pulses.put(source,new Pulse(now+160,factor));
        }
    }
    public static float craftingBoost(ServerPlayer player) {
        Map<Source,Pulse> pulses=BEACONS.get(player.getUUID());if(pulses==null)return 0F;
        ServerLevel level=player.serverLevel();long now=level.getGameTime();float strongest=0F;
        var iterator=pulses.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();Source source=entry.getKey();Pulse pulse=entry.getValue();
            if(!source.dimension().equals(level.dimension())||pulse.expires()<=now
                    ||!level.hasChunkAt(source.position())||!level.getBlockState(source.position()).is(Blocks.BEACON))iterator.remove();
            else strongest=Math.max(strongest,pulse.factor());
        }
        if(pulses.isEmpty())BEACONS.remove(player.getUUID());return strongest;
    }
    public static int craftingPeriod(ServerPlayer player,int original) {
        float boost=craftingBoost(player);return boost<=0?original:Math.max(25,Math.round(original/(1F+boost)));
    }
    public static void removeBeacon(Level level,BlockPos pos) {
        if(level==null||level.isClientSide)return;
        Source source=new Source(level.dimension(),pos);
        BEACONS.values().forEach(pulses->pulses.remove(source));BEACONS.values().removeIf(Map::isEmpty);
    }
    public static boolean closeToSpawner(ServerLevel level,BlockPos pos,int spawnRange) {
        var bounds=new AABB(pos).inflate(spawnRange*2D,spawnRange,spawnRange*2D);
        return !level.getEntitiesOfClass(ServerPlayer.class,bounds,player->!player.isSpectator()&&player.isAlive()).isEmpty();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void wolves(EntityJoinLevelEvent event) {
        if(event.isCanceled()||event.getLevel().isClientSide||!(event.getEntity() instanceof Wolf wolf))return;
        if(wolf.targetSelector.getAvailableGoals().stream().noneMatch(goal->goal.getGoal() instanceof MeatPlayerTarget))wolf.targetSelector.addGoal(3,new MeatPlayerTarget(wolf));
    }
    /** Only the source's reachable held-meat branch is used; carrying meat elsewhere does not qualify. */
    public static final class MeatPlayerTarget extends Goal {
        private final Wolf wolf;private Player target;
        public MeatPlayerTarget(Wolf wolf){this.wolf=wolf;setFlags(EnumSet.of(Flag.TARGET));}
        private boolean wild(){return !wolf.isTame()&&!wolf.isOrderedToSit()&&!wolf.getPersistentData().getBoolean("mite_wolf_witch_ally_032");}
        @Override public boolean canUse() {
            if(!wild())return false;
            target=wolf.level().getNearestPlayer(wolf.getX(),wolf.getY(),wolf.getZ(),64,
                    entity->entity instanceof Player player&&player.isAlive()&&!player.isCreative()&&!player.isSpectator()
                            &&io.github.ningmeng123.mite.entity.R196NpcNeeds.meat(player.getMainHandItem()));
            return target!=null&&wolf.getSensing().hasLineOfSight(target);
        }
        @Override public boolean canContinueToUse(){return wild()&&target!=null&&target.isAlive()&&!target.isCreative()&&!target.isSpectator()&&wolf.getTarget()==target&&wolf.distanceToSqr(target)<=4096;}
        @Override public void start(){wolf.setTarget(target);}
        @Override public void stop(){target=null;}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){BEACONS.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){BEACONS.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){BEACONS.clear();}
}
