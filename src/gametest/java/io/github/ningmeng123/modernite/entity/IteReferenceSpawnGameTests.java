package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static io.github.ningmeng123.modernite.entity.ExtremeKind.*;

/** Real reference biome additions, positioned exclusions and retaliation summons. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IteReferenceSpawnGameTests {
    private IteReferenceSpawnGameTests() {}

    @GameTest(template="gear_empty",batch="reference_ite_spawns")
    public static void constructorAdditionsRetainUnderworldDuplicatesAndHellClear(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            var site=f.site;site.date(224,224,true);
            for(ServerLevel level:site.level.getServer().getAllLevels()) {
                List<R196MobSpawning.Entry> entries=new ArrayList<>();
                IteReferenceSpawns.addEntries(context(level),net.minecraft.core.BlockPos.containing(site.origin),entries);
                if(level.dimension()==Level.OVERWORLD) {
                    h.assertTrue(entries.size()==6,"The base biome gains exactly the six old ITE entries");
                    assertEntry(h,entries,EntityType.GIANT,10,1,1,4);
                    assertEntry(h,entries,EntityType.GHAST,10,1,1,1);
                    assertEntry(h,entries,MiteMobs.type(MobKind.ANCIENT_BONE_LORD),10,1,1,1);
                    assertEntry(h,entries,ExtremeMobs.type(ZOMBIE_LORD),2,1,1,1);
                }else if(MobAbilities.underground(level)) {
                    h.assertTrue(entries.size()==10,"Underworld retains the six parent additions and four child additions");
                    for(var kind:List.of(ZOMBIE_LORD,ANNIHILATION_SKELETON,WANDERING_WITCH))
                        h.assertTrue(entries.stream().filter(entry->entry.type()==ExtremeMobs.type(kind)).count()==2,"Both constructor additions survive: "+kind);
                    assertEntry(h,entries,MiteMobs.type(MobKind.HELLHOUND),15,1,2,8);
                }else if(level.dimension()==Level.NETHER) {
                    h.assertTrue(entries.size()==3,"Hell clears the superclass list before its three additions");
                    assertEntry(h,entries,MiteMobs.type(MobKind.HELLHOUND),20,1,2,8);
                    h.assertTrue(entries.stream().noneMatch(entry->entry.type()==ExtremeMobs.type(ZOMBIE_LORD)),"Hell does not inherit the overworld lord entry");
                }else if(level.dimension()==Level.END)h.assertTrue(entries.isEmpty(),"End clears the parent additions");
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="reference_ite_spawns")
    public static void speciesKeepTheirOwnDatesAndExtraExtremeRemainsEventOnly(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            var site=f.site;var pos=net.minecraft.core.BlockPos.containing(site.origin);
            site.date(32,32,true);
            h.assertTrue(IteReferenceSpawns.naturalAllowed(site.level,ExtremeMobs.type(ZOMBIE_LORD),pos),"ITE zombie lord has no invented shared day-33 gate");
            h.assertFalse(IteReferenceSpawns.naturalAllowed(site.level,ExtremeMobs.type(ANNIHILATION_SKELETON),pos),"Overworld annihilation skeleton waits until after day 32");
            site.date(33,33,true);
            h.assertTrue(IteReferenceSpawns.naturalAllowed(site.level,ExtremeMobs.type(ANNIHILATION_SKELETON),pos),"Annihilation skeleton unlocks on day 33");
            site.date(64,64,true);
            h.assertFalse(IteReferenceSpawns.naturalAllowed(site.level,ExtremeMobs.type(WANDERING_WITCH),pos),"Wandering witch's strict >64 comparison is preserved");
            site.date(65,65,true);
            h.assertTrue(IteReferenceSpawns.naturalAllowed(site.level,ExtremeMobs.type(WANDERING_WITCH),pos),"Wandering witch unlocks on day 65");
            site.date(160,160,true);site.level.setDayTime(159*24_000L+18_000L);
            h.assertTrue(IteReferenceSpawns.naturalAllowed(site.level,EntityType.GHAST,pos),"A late blood night permits the overworld ghast");
            h.assertFalse(IteReferenceSpawns.naturalAllowed(site.level,MiteMobs.type(MobKind.ANCIENT_BONE_LORD),pos),"Ancient bone lord still waits for day 192");
            site.date(192,192,true);
            h.assertTrue(IteReferenceSpawns.naturalAllowed(site.level,MiteMobs.type(MobKind.ANCIENT_BONE_LORD),pos),"Ancient bone lord permits the full blood date from day 192");
            for(var kind:List.of(ZOMBIE_IRON_BUCKET,RUN_CREEPER,WITCH_LORD,UNDYING_ZOMBIE_EMPEROR)) {
                h.assertFalse(IteReferenceSpawns.naturalAllowed(site.level,ExtremeMobs.type(kind),pos),"Extra Extreme identity has no reference natural entry: "+kind);
                h.assertTrue(IteReferenceSpawns.automaticAllowed(site.level,ExtremeMobs.type(kind)),"Scripted event remains available: "+kind);
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="reference_ite_spawns")
    public static void giantAndAncientKeepTheirPositionedExclusionRadii(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            var site=f.site;site.date(224,224,true);var pos=net.minecraft.core.BlockPos.containing(site.origin);site.player(0);
            for(var type:List.of(EntityType.GIANT,MiteMobs.type(MobKind.ANCIENT_BONE_LORD))) {
                h.assertTrue(IteReferenceSpawns.naturalAllowed(site.level,type,pos),"The unoccupied blood-date candidate is available: "+type);
                Mob existing=site.mob(type);existing.moveTo(site.origin.add(type==EntityType.GIANT?95:63,0,0));
                h.assertTrue(site.level.addFreshEntity(existing),"Existing exclusion control joins");
                h.assertFalse(IteReferenceSpawns.naturalAllowed(site.level,type,pos),"Same species inside its original 96/64 radius blocks a candidate");
                existing.discard();SpawnPopulation.clearReservations(site.level);
                Mob pending=site.mob(type);
                h.assertTrue(SpawnPopulation.reserveNatural(pending),"A rare candidate reserves before it becomes section-visible");
                h.assertTrue(IteReferenceSpawns.naturalAllowed(site.level,pending),"Rechecking the reserved candidate excludes its own transaction");
                h.assertFalse(IteReferenceSpawns.naturalAllowed(site.level,type,pos),"The pool sees the pending rare candidate in the same tick");
                h.assertFalse(SpawnPopulation.reserveNatural(site.mob(type)),"A second pending rare candidate is rejected in the same tick");
                SpawnPopulation.release(pending);
                h.assertTrue(IteReferenceSpawns.naturalAllowed(site.level,type,pos),"A canceled or failed reservation releases the rare slot");
                pending.discard();SpawnPopulation.clearReservations(site.level);
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="reference_ite_spawns")
    public static void passiveWitchesDoNotMakeTroopsAndWanderingRetaliationWaits160Ticks(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            var site=f.site;site.date(224,224,true);var player=site.player(20);
            Mob ordinary=site.mob(EntityType.WITCH);ordinary.setTarget(player);
            h.assertTrue(site.level.addFreshEntity(ordinary),"Ordinary witch control joins");
            for(int tick=0;tick<200;tick++)MobAbilities.witchSupportTick(ordinary);
            h.assertTrue(SpawnPopulation.supportCount(ordinary)==0,"A target alone never starts the old modern eight-second mixed troop loop");
            ordinary.setLastHurtByMob(player);MobAbilities.witchSupportTick(ordinary);
            h.assertTrue(ordinary.getPersistentData().getInt("modern_ite:reference_wolf_countdown")==60,"Ordinary retaliation schedules the R196 sixty-tick wolf call");
            Mob wandering=site.mob(ExtremeMobs.type(WANDERING_WITCH));var state=(ExtremeMob)wandering;
            wandering.setTarget(player);state.extremeMemory().cursed=true;
            h.assertTrue(site.level.addFreshEntity(wandering),"Wandering witch control joins");
            for(int tick=0;tick<200;tick++)MobAbilities.tick(wandering,state);
            h.assertTrue(SpawnPopulation.supportCount(wandering)==0,"A distant target without player injury cannot summon bats");
            wandering.setLastHurtByMob(player);MobAbilities.tick(wandering,state);
            for(int tick=0;tick<159;tick++)MobAbilities.tick(wandering,state);
            h.assertTrue(SpawnPopulation.supportCount(wandering)==0,"Retaliation does not fire before its 160-tick countdown finishes");
            MobAbilities.tick(wandering,state);
            var bats=site.level.getEntitiesOfClass(Mob.class,wandering.getBoundingBox().inflate(48),mob->mob.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                    &&mob.getPersistentData().getUUID(SpawnPopulation.SUMMONER).equals(wandering.getUUID()));
            site.mobs.addAll(bats);
            h.assertTrue(bats.size()==8,"Successful old ITE retaliation fills eight living bat slots: "+bats.size());
            h.assertTrue(bats.stream().allMatch(bat->bat.getType()==MiteMobs.type(MobKind.GIANT_VAMPIRE_BAT)||bat.getType()==MiteMobs.type(MobKind.NIGHTWING)),"Wandering witch summons only its original large bats/nightwings");
            h.assertTrue(bats.stream().allMatch(bat->bat.getTarget()==player),"All summoned bats inherit the valid retaliation target");
        }
        h.succeed();
    }

    private static MiteExtensions.SpawnContext context(ServerLevel level) {return new MiteExtensions.SpawnContext(level,R196SpawnRules.Category.MONSTER,null);}
    private static void assertEntry(GameTestHelper h,List<R196MobSpawning.Entry> entries,EntityType<?> type,int weight,int min,int max,int pack) {
        h.assertTrue(entries.stream().anyMatch(entry->entry.type()==type&&entry.weight()==weight&&entry.minimum()==min&&entry.maximum()==max&&entry.pack()==pack),"Old entry preserves weight/group/pack: "+type);
    }
    private static final class Fixture implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;final Preset preset;
        Fixture(GameTestHelper h) {
            preset=new Preset(true);
            site=new SpawnBalanceGameTests.Site(h);
        }
        @Override public void close() {site.close();preset.close();}
    }
    /** Test snapshots must override a previously received server snapshot, not just the local config cache. */
    static final class Preset implements AutoCloseable {
        private final java.util.Map<String,Object> previous=IteConfig.effectiveSnapshot();
        private final boolean hadRemote=previous!=IteConfig.localSnapshot();
        Preset(boolean reference) {set("reference_spawning",reference);}
        void set(String key,Object value) {
            var values=new java.util.LinkedHashMap<>(IteConfig.effectiveSnapshot());
            values.put(key.substring(key.lastIndexOf('.')+1),value);IteConfig.receiveServerSnapshot(values);
        }
        @Override public void close() {if(hadRemote)IteConfig.receiveServerSnapshot(previous);else IteConfig.clearServerSnapshot();}
    }
}
