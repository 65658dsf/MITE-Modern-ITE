package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.ExtremeRules;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import io.github.ningmeng123.modernite.world.DeferredStructureSpawns;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Shared admission accounting, including entities not yet visible in a server section. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class SpawnPopulation {
    public static final String SUMMONER="modern_ite:summoner", ENCOUNTER="modern_ite:spawn_encounter", EXPLICIT_COMMAND="modern_ite:explicit_command_spawn";
    private static final String EXPLICIT_EGG="modern_ite:explicit_egg_spawn", ADMITTED="modern_ite:spawn_admitted", PLAYER_DEATH_MIMIC="modern_ite:player_death_mimic";
    private static final Set<String> OTHER_HOSTILES=Set.of("mite:dire_wolf","mite:vampire_bat","mite:giant_vampire_bat","mite:nightwing");
    private static final Map<ServerLevel,Ledger> LEDGERS=new WeakHashMap<>();
    private SpawnPopulation() {}

    public static boolean hostile(EntityType<?> type) {
        return type.getCategory()==MobCategory.MONSTER||OTHER_HOSTILES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }
    private static boolean counted(Mob mob) {return hostile(mob.getType())||mob.getPersistentData().hasUUID(SUMMONER);}
    private static boolean living(Mob mob) {return mob.isAlive()&&!mob.isRemoved()&&!mob.isSpawnCancelled();}
    public static boolean explicit(Mob mob) {
        return mob.getPersistentData().getBoolean(EXPLICIT_COMMAND)||mob.getPersistentData().getBoolean(EXPLICIT_EGG)||mob.getSpawnType()==MobSpawnType.SPAWN_EGG
                ||TimeLordMinions.explicitSupport(mob)||ZombieLordAbilities.explicitSupport(mob);
    }
    public static boolean encounter(Mob mob) {return mob!=null&&mob.getPersistentData().getBoolean(ENCOUNTER);}
    /** A real player-death substitute can appear before automatic hostile spawning unlocks. */
    static void markPlayerDeathMimic(Mob mob) {
        if(mob.getType()!=ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE))throw new IllegalArgumentException("Only player mimics can use death admission");
        mob.getPersistentData().putBoolean(PLAYER_DEATH_MIMIC,true);
    }
    private static boolean playerDeathMimic(Mob mob) {
        return mob.getType()==ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE)&&mob.getPersistentData().getBoolean(PLAYER_DEATH_MIMIC);
    }
    public static boolean automaticAllowed(ServerLevel level,EntityType<?> type) {
        if(!hostile(type))return true;
        if(ExtremeWorldState.graceActive(level))return false;
        if(IteReferenceSpawns.enabled())return IteReferenceSpawns.automaticAllowed(level,type);
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        long day=BloodMoonInvasion.highestDay(level);
        if(id.startsWith("modern_ite:"))return day>=IteConfig.extremeMobFirstDay(id,level.dimension()==Level.OVERWORLD);
        return ExtremeSpawnProgression.unlocked(id,day,IteConfig.integer("ancient_bone_lord_spawn_limit_day"),
                IteConfig.integer("extreme_mob_spawn_limit_day"));
    }
    public static int localCount(ServerLevel level,AABB box) {return ledger(level).count(null,box,null,false);}
    /** Includes pending joins whose native entity section is not queryable yet. */
    public static boolean anyAdmitted(ServerLevel level,Mob excluded,java.util.function.Predicate<Mob> predicate) {
        Ledger ledger=ledger(level);
        for(Mob mob:ledger.existing.values())if(!ledger.pending.containsKey(mob.getUUID())
                &&admittedMatch(mob,excluded,ledger,predicate))return true;
        for(Mob mob:ledger.pending.values())if(admittedMatch(mob,excluded,ledger,predicate))return true;
        return false;
    }
    private static boolean admittedMatch(Mob mob,Mob excluded,Ledger ledger,java.util.function.Predicate<Mob> predicate) {
        return (excluded==null||!mob.getUUID().equals(excluded.getUUID()))&&!ledger.excluded.contains(mob.getUUID())
                &&living(mob)&&predicate.test(mob);
    }
    public static int supportCount(Mob owner) {return owner.level() instanceof ServerLevel level?ledger(level).count(null,null,owner.getUUID(),false):0;}
    /** Test isolation only; normal accounting resets itself once per game tick. */
    public static void clearReservations(ServerLevel level) {LEDGERS.remove(level);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)LEDGERS.remove(level);
    }
    public static void release(Mob mob) {
        if(mob.level() instanceof ServerLevel level&&level.getServer().isSameThread())ledger(level).pending.remove(mob.getUUID());
    }
    /** A replacement spends the original's reservation, rather than briefly spending two. */
    public static void transfer(Mob from,Mob to) {
        if(!(from.level() instanceof ServerLevel level))return;
        Ledger ledger=ledger(level);ledger.pending.remove(from.getUUID());ledger.excluded.add(from.getUUID());
        ledger.excluded.remove(to.getUUID());ledger.pending.put(to.getUUID(),to);
    }
    public static boolean reserveNatural(Mob mob) {
        if(!(mob.level() instanceof ServerLevel level)||!automaticAllowed(level,mob.getType()))return false;
        Ledger ledger=ledger(level);
        boolean reference=IteReferenceSpawns.enabled();
        if(reference?!IteReferenceSpawns.naturalAllowed(level,mob):!localRoom(level,mob,ledger))return false;
        if(R196MobSpawning.category(mob)==io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER) {
            if(ledger.chunks<0)ledger.chunks=R196MobSpawning.eligibleChunks(level,true).size();
            int chunks=ledger.chunks;
            boolean deep=deep(mob,level);
            int cap=ExtremeRules.hostileCap(level,chunks,reference&&deep);
            if(ledger.count(mob,null,null,true,reference?deep:null)>=cap)return false;
        }
        ledger.pending.put(mob.getUUID(),mob);return true;
    }
    public static boolean reserveSupport(Mob candidate,Mob owner,boolean encounter) {
        if(!(candidate.level() instanceof ServerLevel level))return false;
        if(owner!=null)candidate.getPersistentData().putUUID(SUMMONER,owner.getUUID());
        if(!TimeLordMinions.explicitSupport(candidate)&&!ZombieLordAbilities.explicitSupport(candidate)
                &&(ExtremeWorldState.graceActive(level)||!automaticAllowed(level,candidate.getType())))return false;
        boolean scripted=encounter||encounter(owner);candidate.getPersistentData().putBoolean(ENCOUNTER,scripted);
        Ledger ledger=ledger(level);
        if(!scripted&&(!localRoom(level,candidate,ledger)||owner!=null&&ledger.count(candidate,null,owner.getUUID(),false)>=8))return false;
        ledger.pending.put(candidate.getUUID(),candidate);return true;
    }
    private static boolean localRoom(ServerLevel level,Mob candidate,Ledger ledger) {
        int cap=ExtremeRules.localHostileLimit(level);
        for(var player:level.players()) {
            if(!player.isAlive()||player.isSpectator())continue;
            AABB box=player.getBoundingBox().inflate(32,8,32);
            if(box.intersects(candidate.getBoundingBox())&&ledger.count(candidate,box,null,false)>=cap)return false;
        }
        return true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeFinalize(FinalizeSpawnEvent event) {
        if(event.isCanceled()||event.isSpawnCancelled()||!(event.getLevel().getLevel() instanceof ServerLevel level))return;
        Mob mob=event.getEntity();
        if(event.getSpawnType()==MobSpawnType.SPAWN_EGG)mob.getPersistentData().putBoolean(EXPLICIT_EGG,true);
        if(!counted(mob)||explicit(mob))return;
        boolean allowed=automaticAllowed(level,mob.getType());
        if(allowed&&level.getServer().isSameThread()) {
            if(event.getSpawnType()==MobSpawnType.NATURAL)allowed=reserveNatural(mob);
            else if(!encounter(mob)) {
                Ledger ledger=ledger(level);
                allowed=localRoom(level,mob,ledger)&&(!mob.getPersistentData().hasUUID(SUMMONER)
                        ||ledger.count(mob,null,mob.getPersistentData().getUUID(SUMMONER),false)<8);
                if(allowed)ledger.pending.put(mob.getUUID(),mob);
            }
        }
        if(!allowed){
            if(event.getSpawnType()==MobSpawnType.STRUCTURE)DeferredStructureSpawns.defer(mob,true);
            event.setSpawnCancelled(true);event.setCanceled(true);release(mob);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeJoin(EntityJoinLevelEvent event) {
        if(event.isCanceled()||!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof Mob mob)||!counted(mob))return;
        boolean existing=event.loadedFromDisk()||mob.getPersistentData().getBoolean(ADMITTED);
        if(!existing&&!explicit(mob)) {
            boolean allowed=playerDeathMimic(mob)||automaticAllowed(level,mob.getType());
            if(allowed&&mob.getSpawnType()==MobSpawnType.NATURAL)allowed=reserveNatural(mob);
            if(allowed&&!encounter(mob)&&(!IteReferenceSpawns.enabled()||mob.getSpawnType()!=MobSpawnType.NATURAL)) {
                Ledger ledger=ledger(level);
                allowed=localRoom(level,mob,ledger)&&(!mob.getPersistentData().hasUUID(SUMMONER)
                        ||ledger.count(mob,null,mob.getPersistentData().getUUID(SUMMONER),false)<8);
            }
            if(!allowed){
                if(mob.getSpawnType()==MobSpawnType.STRUCTURE)DeferredStructureSpawns.defer(mob,false);
                event.setCanceled(true);release(mob);return;
            }
        }
        // Existing saved inhabitants also carry this bit across later dimension transfers.
        mob.getPersistentData().putBoolean(ADMITTED,true);ledger(level).pending.put(mob.getUUID(),mob);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void canceledJoin(EntityJoinLevelEvent event) {
        if(event.isCanceled()&&event.getLevel() instanceof ServerLevel&&event.getEntity() instanceof Mob mob) {
            release(mob);
            if(!event.loadedFromDisk()&&!mob.isAddedToLevel())mob.getPersistentData().remove(ADMITTED);
        }
    }
    private static Ledger ledger(ServerLevel level) {
        Ledger ledger=LEDGERS.computeIfAbsent(level,ignored->new Ledger());
        if(ledger.tick!=level.getGameTime()) {
            ledger.tick=level.getGameTime();ledger.chunks=-1;ledger.pending.clear();ledger.existing.clear();ledger.excluded.clear();
            for(Entity entity:level.getAllEntities())if(entity instanceof Mob mob&&counted(mob)&&living(mob))ledger.existing.put(mob.getUUID(),mob);
        }
        return ledger;
    }
    private static boolean deep(Mob mob,ServerLevel level) {
        return io.github.ningmeng123.mite.rules.R196SpawnRules.sourceY(mob.getBlockY(),R196MobSpawning.underworld(level))<60;
    }
    private static final class Ledger {
        long tick=Long.MIN_VALUE;
        int chunks=-1;
        final Map<UUID,Mob> existing=new HashMap<>(),pending=new HashMap<>();
        final Set<UUID> excluded=new HashSet<>();
        int count(Mob candidate,AABB box,UUID owner,boolean monstersOnly) {
            return count(candidate,box,owner,monstersOnly,null);
        }
        int count(Mob candidate,AABB box,UUID owner,boolean monstersOnly,Boolean deepOnly) {
            int count=0;
            for(Mob mob:existing.values())if(!pending.containsKey(mob.getUUID())&&matches(mob,candidate,box,owner,monstersOnly,deepOnly))count++;
            for(Mob mob:pending.values())if(matches(mob,candidate,box,owner,monstersOnly,deepOnly))count++;
            return count;
        }
        private boolean matches(Mob mob,Mob candidate,AABB box,UUID owner,boolean monstersOnly,Boolean deepOnly) {
            return (candidate==null||!mob.getUUID().equals(candidate.getUUID()))&&!excluded.contains(mob.getUUID())&&living(mob)
                    &&(!monstersOnly||R196MobSpawning.category(mob)==io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER)
                    &&(deepOnly==null||deep(mob,(ServerLevel)mob.level())==deepOnly)
                    &&(box==null||box.intersects(mob.getBoundingBox()))
                    &&(owner==null||mob.getPersistentData().hasUUID(SUMMONER)&&owner.equals(mob.getPersistentData().getUUID(SUMMONER)));
        }
    }
}
