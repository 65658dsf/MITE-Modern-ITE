package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196SpawnQualification;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import static io.github.ningmeng123.modernite.entity.ExtremeKind.*;

/** ITE 2.0.28 biome constructor additions and positioned predicates. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class IteReferenceSpawns {
    private IteReferenceSpawns() {}
    public static boolean enabled() {return IteConfig.bool("reference_spawning");}

    /** Automatic scripted encounters keep their identities; natural-only restrictions are below. */
    public static boolean automaticAllowed(ServerLevel level,EntityType<?> type) {
        boolean over=level.dimension()==Level.OVERWORLD;
        int day=MobAbilities.day(level);
        if(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(ModernIte.MOD_ID)
                &&day<ExtremeSpawnProgression.FIRST_ITE_DAY)return false;
        if(type==MiteMobs.type(MobKind.ANCIENT_BONE_LORD))return !over||day>=192;
        if(type==EntityType.GHAST)return !over||day>=128;
        if(type==ExtremeMobs.type(WANDERING_WITCH))return day>64;
        return type!=ExtremeMobs.type(ANNIHILATION_SKELETON)||!over||day>32;
    }

    public static boolean naturalAllowed(ServerLevel level,EntityType<?> type,BlockPos pos) {
        return naturalAllowed(level,type,pos,null);
    }
    /** Finalize and join can check one candidate twice; it must not reject its own reservation. */
    public static boolean naturalAllowed(ServerLevel level,Mob candidate) {
        return naturalAllowed(level,candidate.getType(),candidate.blockPosition(),candidate);
    }
    private static boolean naturalAllowed(ServerLevel level,EntityType<?> type,BlockPos pos,Mob candidate) {
        if(!automaticAllowed(level,type))return false;
        boolean over=level.dimension()==Level.OVERWORLD,under=MobAbilities.underground(level),nether=level.dimension()==Level.NETHER;
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if(id.getNamespace().equals(ModernIte.MOD_ID)) {
            if(type==ExtremeMobs.type(ZOMBIE_LORD))return over||under;
            if(type==ExtremeMobs.type(ZOMBIE_PIGMAN_LORD))return nether;
            if(type==ExtremeMobs.type(ANNIHILATION_SKELETON))return over||under||nether;
            if(type==ExtremeMobs.type(WANDERING_WITCH))return over||under;
            return false;
        }
        if(type==EntityType.GIANT)return over&&blood(level,false)&&noneWithin(level,type,pos,96,false,candidate);
        if(type==EntityType.GHAST&&over)return blood(level,true)&&noneWithin(level,type,pos,64,true,candidate);
        if(type==MiteMobs.type(MobKind.ANCIENT_BONE_LORD)&&over)
            return blood(level,false)&&noneWithin(level,type,pos,64,false,candidate);
        return true;
    }

    private static boolean blood(ServerLevel level,boolean night) {
        var moon=LunarCalendar.state(level.getDayTime(),level.dimension()==Level.OVERWORLD);
        return night?moon.bloodNight():moon.bloodPeriod();
    }
    private static boolean noneWithin(ServerLevel level,EntityType<?> type,BlockPos pos,double radius,boolean box,Mob candidate) {
        double x=candidate==null?pos.getX()+.5:candidate.getX(),y=candidate==null?pos.getY():candidate.getY(),z=candidate==null?pos.getZ()+.5:candidate.getZ();
        AABB volume=(candidate==null?new AABB(pos):candidate.getBoundingBox()).inflate(radius);
        return !SpawnPopulation.anyAdmitted(level,candidate,mob->mob.getType()==type
                &&(box?volume.intersects(mob.getBoundingBox()):mob.distanceToSqr(x,y,z)<radius*radius));
    }

    public static List<R196MobSpawning.Entry> pool(MiteExtensions.SpawnContext context,BlockPos pos,List<R196MobSpawning.Entry> original) {
        if(context.category()!=R196SpawnRules.Category.MONSTER)return original;
        var result=new ArrayList<>(original);
        addEntries(context,pos,result);
        result.removeIf(entry->!naturalAllowed(context.level(),entry.type(),pos));
        return List.copyOf(result);
    }

    public static void addEntries(MiteExtensions.SpawnContext context,BlockPos pos,List<R196MobSpawning.Entry> entries) {
        if(context.category()!=R196SpawnRules.Category.MONSTER)return;
        var level=context.level();
        if(level.dimension()==Level.OVERWORLD){
            var hut=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.SWAMP_HUT);
            if(hut!=null&&level.structureManager().getStructureWithPieceAt(pos,hut).isValid())return;
        }
        var surface=level.getBiome(new BlockPos(pos.getX(),level.getHeight(Heightmap.Types.WORLD_SURFACE,pos.getX(),pos.getZ())-1,pos.getZ()));
        int biome=R196MobSpawning.biomeId(surface,level);
        // Hell, End and mushroom constructors clear the superclass monster list.
        if(biome==8) {
            var fortress=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.FORTRESS);
            if(fortress!=null&&(level.structureManager().getStructureWithPieceAt(pos,fortress).isValid()
                    ||level.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS)&&level.structureManager().getStructureAt(pos,fortress).isValid()))return;
            add(entries,ExtremeMobs.type(ZOMBIE_PIGMAN_LORD),null,frequency(ZOMBIE_PIGMAN_LORD),1,1,1);
            add(entries,ExtremeMobs.type(ANNIHILATION_SKELETON),null,frequency(ANNIHILATION_SKELETON),1,1,1);
            add(entries,MiteMobs.type(MobKind.HELLHOUND),MobKind.HELLHOUND.sourceClass(),20,1,2,8);
            return;
        }
        if(biome==9||biome==14||biome==15||surface.unwrapKey().map(key->key.location().getPath().equals("mushroom_fields")).orElse(false))return;
        add(entries,EntityType.GIANT,"tk",10,1,1,4);
        add(entries,EntityType.GHAST,"tj",10,1,1,1);
        add(entries,MiteMobs.type(MobKind.ANCIENT_BONE_LORD),MobKind.ANCIENT_BONE_LORD.sourceClass(),10,1,1,1);
        addIteBase(entries);
        // Underworld removes selected original classes, but does not clear the ITE additions.
        if(biome==26) {
            addIteBase(entries);
            add(entries,MiteMobs.type(MobKind.HELLHOUND),MobKind.HELLHOUND.sourceClass(),15,1,2,8);
        }
    }
    private static void addIteBase(List<R196MobSpawning.Entry> entries) {
        for(var kind:List.of(ZOMBIE_LORD,ANNIHILATION_SKELETON,WANDERING_WITCH))
            add(entries,ExtremeMobs.type(kind),null,frequency(kind),1,1,1);
    }
    private static int frequency(ExtremeKind kind) {
        return IteConfig.integer("GameMechanics.MobSpawning."+kind.id()+"_spawn_frequency");
    }
    private static void add(List<R196MobSpawning.Entry> entries,EntityType<? extends Mob> type,String source,int weight,int min,int max,int pack) {
        if(weight>0)entries.add(new R196MobSpawning.Entry(type,source,R196SpawnRules.Category.MONSTER,weight,min,max,pack,source==null));
    }

    /** These ITE native overrides intentionally do not inherit an ordinary monster light check. */
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void nativeQualification(MobSpawnEvent.PositionCheck event) {
        if(!enabled()||event.getSpawnType()!=MobSpawnType.NATURAL||event.getResult()!=MobSpawnEvent.PositionCheck.Result.DEFAULT)return;
        Mob mob=event.getEntity();var level=event.getLevel().getLevel();
        boolean giant=mob.getType()==EntityType.GIANT;
        boolean ghast=mob instanceof Ghast&&mob.getType()==EntityType.GHAST&&level.dimension()==Level.OVERWORLD;
        boolean ancient=mob.getType()==MiteMobs.type(MobKind.ANCIENT_BONE_LORD)&&level.dimension()==Level.OVERWORLD;
        if(!giant&&!ghast&&!ancient)return;
        AABB volume=mob.getBoundingBox().expandTowards(0,giant?1:ghast?3:0,0);
        boolean allowed=level.getDifficulty()!=Difficulty.PEACEFUL&&naturalAllowed(level,mob)
                &&!level.getFluidState(mob.blockPosition()).is(FluidTags.WATER)&&level.noCollision(mob,volume);
        event.setResult(allowed?MobSpawnEvent.PositionCheck.Result.SUCCEED:MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    public static boolean variantQualification(Mob mob,ExtremeKind kind,ServerLevelAccessor level,MobSpawnType reason) {
        var server=level.getLevel();boolean natural=reason==MobSpawnType.NATURAL||reason==MobSpawnType.CHUNK_GENERATION;
        if(natural&&!naturalAllowed(server,mob)||server.getDifficulty()==Difficulty.PEACEFUL)return false;
        if(!level.getFluidState(mob.blockPosition()).isEmpty())return false;
        if(kind==ANNIHILATION_SKELETON) {
            if(server.dimension()!=Level.OVERWORLD)return true;
            return MobAbilities.day(server)>32&&mob.getRandom().nextInt(4)==0&&server.getNearestPlayer(mob,24)==null
                    &&R196SpawnQualification.mobLight(mob,level,reason==MobSpawnType.SPAWNER);
        }
        if(kind==WANDERING_WITCH&&mob.getRandom().nextInt(100)>=(MobAbilities.underground(server)?50:33))return false;
        return R196SpawnQualification.mobLight(mob,level,reason==MobSpawnType.SPAWNER);
    }
}
