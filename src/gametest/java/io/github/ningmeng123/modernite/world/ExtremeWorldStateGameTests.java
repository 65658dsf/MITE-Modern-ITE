package io.github.ningmeng123.modernite.world;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.EventHooks;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Persistent opening protection and NeoForge daylight integration. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ExtremeWorldStateGameTests {
    private ExtremeWorldStateGameTests() {}

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void openingPeaceEndsExactlyAtSecondNightAndSurvivesSaving(GameTestHelper h) {
        var state=new ExtremeWorldState.State();
        state.observe(36_999L,2);
        h.assertFalse(state.save(new CompoundTag(),h.getLevel().registryAccess()).getBoolean("grace_ended"),
                "Second-day daylight still belongs to the opening protection");
        state.observe(37_000L,2);
        var saved=state.save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(saved.getBoolean("grace_ended"),"Second-day night ends opening protection exactly at tick 37000");
        var loaded=ExtremeWorldState.State.load(saved,h.getLevel().registryAccess());
        loaded.observe(0,1);
        h.assertTrue(loaded.save(new CompoundTag(),h.getLevel().registryAccess()).getBoolean("grace_ended"),
                "Disk round trip and time rewind cannot reopen protection");
        var migrated=new ExtremeWorldState.State();migrated.observe(0,33);
        h.assertTrue(migrated.save(new CompoundTag(),h.getLevel().registryAccess()).getBoolean("grace_ended"),
                "Existing worlds with historical progress do not gain a new opening period");
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void openingPeaceUsesTheOverworldAcrossEveryLoadedDimension(GameTestHelper h) {
        var level=h.getLevel().getServer().overworld();var history=BloodMoonInvasionData.get(level);
        long time=level.getDayTime(),highest=history.highestDay();boolean dirty=history.isDirty();
        boolean grace=ExtremeWorldState.graceEnded(level);
        try {
            level.setDayTime(36_999L);history.restoreProgress(2);ExtremeWorldState.restoreGraceEnded(level,false);
            for(var other:level.getServer().getAllLevels())
                h.assertTrue(ExtremeWorldState.graceActive(other),"Every loaded dimension shares the same opening protection: "+other.dimension().location());
            level.setDayTime(37_000L);ExtremeWorldState.observe(level);
            level.setDayTime(0);history.restoreProgress(1);
            for(var other:level.getServer().getAllLevels())
                h.assertFalse(ExtremeWorldState.graceActive(other),"No dimension reopens protection after a rewind: "+other.dimension().location());
        } finally {
            level.setDayTime(time);history.restoreProgress(highest);history.setDirty(dirty);
            ExtremeWorldState.restoreGraceEnded(level,grace);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void earlyDaylightUsesTheBaseCalendarBoundaries(GameTestHelper h) {
        for(long day:new long[]{1,2,31}) {
            long start=(day-1)*24_000L;
            for(int tick:new int[]{0,6_000,12_999,23_001,23_999})
                h.assertTrue(ExtremeWorldState.dayTimePerTick(start+tick,day)==.5F,
                        "Daylight advances half a calendar tick before the first blood moon: "+day+"/"+tick);
            for(int tick:new int[]{13_000,18_000,23_000})
                h.assertTrue(ExtremeWorldState.dayTimePerTick(start+tick,day)==-1F,
                        "Night and the exact base-calendar boundaries retain normal time: "+day+"/"+tick);
        }
        h.assertTrue(ExtremeWorldState.dayTimePerTick(31*24_000L,32)==-1F,
                "The first blood-moon day restores native day length");
        h.assertTrue(ExtremeWorldState.dayTimePerTick(0,33)==-1F,
                "Historical progress preserves normal day length after a rewind");
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void levelTickUpdatesTheNativeRateWithoutEnablingStoppedTime(GameTestHelper h) {
        var level=h.getLevel().getServer().overworld();var history=BloodMoonInvasionData.get(level);
        long time=level.getDayTime(),highest=history.highestDay();boolean dirty=history.isDirty();
        boolean grace=ExtremeWorldState.graceEnded(level),cycle=level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        float rate=level.getDayTimePerTick();
        try {
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
            level.setDayTime(24_000L);history.restoreProgress(2);ExtremeWorldState.restoreGraceEnded(level,false);
            ExtremeWorldState.beforeLevelTick(new LevelTickEvent.Pre(()->true,level));
            h.assertTrue(level.getDayTimePerTick()==.5F&&level.getDayTime()==24_000L,
                    "The integration configures NeoForge's fractional rate without advancing calendar time directly");
            h.assertFalse(level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT),"Stopped time remains stopped");
            level.setDayTime(37_000L);ExtremeWorldState.beforeLevelTick(new LevelTickEvent.Pre(()->true,level));
            h.assertTrue(level.getDayTimePerTick()==-1F,"Night restores the native advancement rate");
            level.setDayTime(31*24_000L);history.restoreProgress(32);
            ExtremeWorldState.beforeLevelTick(new LevelTickEvent.Pre(()->true,level));
            h.assertTrue(level.getDayTimePerTick()==-1F,"Day 32 permanently restores native day length");
            var nether=level.getServer().getLevel(Level.NETHER);float netherRate=nether.getDayTimePerTick();
            ExtremeWorldState.beforeLevelTick(new LevelTickEvent.Pre(()->true,nether));
            h.assertTrue(nether.getDayTimePerTick()==netherRate,"Only the overworld owns the shared daylight clock");
        } finally {
            level.setDayTime(time);history.restoreProgress(highest);history.setDirty(dirty);
            ExtremeWorldState.restoreGraceEnded(level,grace);level.setDayTimePerTick(rate);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(cycle,level.getServer());
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void blockedStructureInhabitantsSurviveSavingAndReplayExactlyOnce(GameTestHelper h) {
        var level=h.getLevel().getServer().overworld();var history=BloodMoonInvasionData.get(level);
        long time=level.getDayTime(),highest=history.highestDay();boolean dirty=history.isDirty();
        boolean grace=ExtremeWorldState.graceEnded(level);
        var originalQueue=DeferredStructureSpawns.get(level);
        CompoundTag originalTag=originalQueue.save(new CompoundTag(),level.registryAccess());boolean queueDirty=originalQueue.isDirty();
        Mob guardian=EntityType.ELDER_GUARDIAN.create(level),natural=EntityType.ZOMBIE.create(level);Entity replayed=null;
        try {
            level.setDayTime(1000);history.restoreProgress(1);ExtremeWorldState.restoreGraceEnded(level,false);
            guardian.moveTo(h.absolutePos(new BlockPos(0,2,0)).getCenter());guardian.setNoAi(true);
            natural.moveTo(guardian.position());
            EventHooks.finalizeMobSpawn(guardian,level,level.getCurrentDifficultyAt(guardian.blockPosition()),MobSpawnType.STRUCTURE,null);
            EventHooks.finalizeMobSpawn(natural,level,level.getCurrentDifficultyAt(natural.blockPosition()),MobSpawnType.NATURAL,null);
            h.assertTrue(guardian.isSpawnCancelled()&&!guardian.isAddedToLevel(),"A one-time structure hostile remains absent during opening peace");
            DeferredStructureSpawns.defer(guardian,true);
            CompoundTag snapshot=DeferredStructureSpawns.get(level).save(new CompoundTag(),level.registryAccess());
            long guardianEntries=snapshot.getList("spawns",Tag.TAG_COMPOUND).stream().map(tag->((CompoundTag)tag).getCompound("entity"))
                    .filter(tag->tag.hasUUID("UUID")&&tag.getUUID("UUID").equals(guardian.getUUID())).count();
            h.assertTrue(guardianEntries==1,"Repeated structure cancellation stores one original UUID");
            h.assertTrue(snapshot.getList("spawns",Tag.TAG_COMPOUND).stream().map(tag->((CompoundTag)tag).getCompound("entity"))
                    .noneMatch(tag->tag.hasUUID("UUID")&&tag.getUUID("UUID").equals(natural.getUUID())),"Recurring natural candidates do not enter the saved structure queue");
            var loaded=DeferredStructureSpawns.load(snapshot,level.registryAccess());
            level.getDataStorage().set("modern_ite_deferred_structure_spawns",loaded);
            DeferredStructureSpawns.replay(level);
            h.assertTrue(level.getEntity(guardian.getUUID())==null,"Loading the saved queue does not replay before opening peace ends");
            level.setDayTime(37_000);history.restoreProgress(2);ExtremeWorldState.restoreGraceEnded(level,true);
            h.assertTrue(level.hasChunkAt(guardian.blockPosition()),"The structure's chunk is actually loaded for replay");
            DeferredStructureSpawns.replay(level);replayed=level.getEntity(guardian.getUUID());
            h.assertTrue(replayed!=null&&replayed.getType()==EntityType.ELDER_GUARDIAN,"The saved structure guardian rejoins with its original identity and UUID");
            DeferredStructureSpawns.replay(level);
            h.assertTrue(level.getEntity(guardian.getUUID())==replayed,"A second replay does not duplicate the structure inhabitant");
            h.assertTrue(loaded.save(new CompoundTag(),level.registryAccess()).getList("spawns",Tag.TAG_COMPOUND).stream()
                    .map(tag->((CompoundTag)tag).getCompound("entity"))
                    .noneMatch(tag->tag.hasUUID("UUID")&&tag.getUUID("UUID").equals(guardian.getUUID())),"Successful replay consumes the saved queue entry");
        } finally {
            guardian.discard();natural.discard();if(replayed!=null)replayed.discard();SpawnPopulation.clearReservations(level);
            var restoredQueue=DeferredStructureSpawns.load(originalTag,level.registryAccess());restoredQueue.setDirty(queueDirty);
            level.getDataStorage().set("modern_ite_deferred_structure_spawns",restoredQueue);
            level.setDayTime(time);history.restoreProgress(highest);history.setDirty(dirty);ExtremeWorldState.restoreGraceEnded(level,grace);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void nativeClockDoublesOnlyEarlyDaylightAndPreservesRealGameTicks(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel().getServer().overworld();var history=BloodMoonInvasionData.get(level);
        long time=level.getDayTime(),gameTime=level.getGameTime(),highest=history.highestDay();boolean dirty=history.isDirty();
        boolean grace=ExtremeWorldState.graceEnded(level),cycle=level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        float rate=level.getDayTimePerTick(),fraction=level.getDayTimeFraction();
        var tick=ServerLevel.class.getDeclaredMethod("tickTime");tick.setAccessible(true);
        try {
            level.setDayTime(30_000);level.setDayTimeFraction(0);history.restoreProgress(2);ExtremeWorldState.restoreGraceEnded(level,true);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,level.getServer());
            ExtremeWorldState.beforeLevelTick(new LevelTickEvent.Pre(()->true,level));
            for(int i=0;i<20;i++)tick.invoke(level);
            h.assertTrue(level.getDayTime()==30_010&&level.getGameTime()==gameTime+20,
                    "Twenty actual native clock ticks advance early daylight by ten calendar ticks and twenty real game ticks");
            level.setDayTime(37_000);ExtremeWorldState.beforeLevelTick(new LevelTickEvent.Pre(()->true,level));
            for(int i=0;i<20;i++)tick.invoke(level);
            h.assertTrue(level.getDayTime()==37_020&&level.getGameTime()==gameTime+40,
                    "Twenty native night ticks advance both clocks by twenty");
            level.setDayTime(30_000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
            ExtremeWorldState.beforeLevelTick(new LevelTickEvent.Pre(()->true,level));
            for(int i=0;i<20;i++)tick.invoke(level);
            h.assertTrue(level.getDayTime()==30_000&&level.getGameTime()==gameTime+60,
                    "Stopped daylight keeps its calendar while native game time continues normally");
            level.setDayTime(31*24_000L+6000);history.restoreProgress(32);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,level.getServer());
            ExtremeWorldState.beforeLevelTick(new LevelTickEvent.Pre(()->true,level));
            for(int i=0;i<20;i++)tick.invoke(level);
            h.assertTrue(level.getDayTime()==31*24_000L+6020&&level.getGameTime()==gameTime+80,
                    "Day 32 daylight returns to twenty native calendar ticks per twenty game ticks");
        } finally {
            ((ServerLevelData)level.getLevelData()).setGameTime(gameTime);level.setDayTime(time);
            history.restoreProgress(highest);history.setDirty(dirty);ExtremeWorldState.restoreGraceEnded(level,grace);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(cycle,level.getServer());
            level.setDayTimePerTick(rate);level.setDayTimeFraction(fraction);SpawnPopulation.clearReservations(level);
        }
        h.succeed();
    }
}
