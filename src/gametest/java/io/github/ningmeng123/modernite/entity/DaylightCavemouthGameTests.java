package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ITE cannot bypass the shared daylight cave-mouth gate. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class DaylightCavemouthGameTests {
    private DaylightCavemouthGameTests() {}

    @GameTest(template="gear_empty",batch="daylight_cavemouth_ite")
    public static void allIteIdentitiesRejectDaytimeCavemouthIncludingBloodDates(GameTestHelper h) {
        try (Cave cave=new Cave(h)) {
            h.assertFalse(R196OutdoorChecks.surface(cave.level,cave.pos),"The candidate has an opaque roof above its own column");
            h.assertFalse(R196OutdoorChecks.outdoors(cave.level,cave.pos),"The eleven-block cave entrance is beyond the old short outdoor probe");
            for (ExtremeKind kind:ExtremeKind.values()) {
                Mob mob=cave.mob(ExtremeMobs.type(kind));
                cave.night();
                h.assertTrue(R196MobSpawning.naturalDaylightAllows(mob,cave.level),"Night retains the species' independent natural rules: "+kind);
                cave.day();
                h.assertFalse(R196MobSpawning.naturalDaylightAllows(mob,cave.level),"Every ITE identity shares the daytime surface-cave exclusion: "+kind);
                h.assertFalse(R196MobSpawning.qualifiesWithEvent(mob,cave.level,MobSpawnType.NATURAL,true,true),"Actual modern qualification cannot bypass daylight: "+kind);
            }
            Mob dog=cave.mob(RabidDogContent.RABID_DOG.get());
            h.assertFalse(R196MobSpawning.naturalDaylightAllows(dog,cave.level),"The separate Extra rabid-dog identity also obeys the cave-mouth exclusion");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="daylight_cavemouth_ite")
    public static void iteNativeSuccessAndFinalJoinCannotBypassBloodDayCavemouth(GameTestHelper h) {
        try (Cave cave=new Cave(h)) {
            cave.site.player(120);
            for (var type:List.of(EntityType.GIANT,EntityType.GHAST,MiteMobs.type(MobKind.ANCIENT_BONE_LORD))) {
                Mob mob=cave.mob(type);
                // Ghasts intentionally have a blood-night-only rule. First prove this is a
                // real eligible native override, then change only the time before admission.
                cave.night();
                var nativeEvent=new MobSpawnEvent.PositionCheck(mob,cave.level,MobSpawnType.NATURAL,null);
                IteReferenceSpawns.nativeQualification(nativeEvent);
                h.assertTrue(nativeEvent.getResult()==MobSpawnEvent.PositionCheck.Result.SUCCEED,"The original ITE native override is active: "+type);
                cave.day();
                if (type!=EntityType.GHAST) {
                    var daylightEvent=new MobSpawnEvent.PositionCheck(mob,cave.level,MobSpawnType.NATURAL,null);
                    IteReferenceSpawns.nativeQualification(daylightEvent);
                    h.assertTrue(daylightEvent.getResult()==MobSpawnEvent.PositionCheck.Result.SUCCEED,"Native blood-date success alone still does not establish daylight admission: "+type);
                }
                h.assertFalse(R196MobSpawning.qualifiesWithEvent(mob,cave.level,MobSpawnType.NATURAL,true,false),"A successful native override cannot bypass the shared daylight qualification: "+type);
                h.assertTrue(R196MobSpawning.finalizeNaturalMonster(cave.level,mob,null,8).monsters()==0&&!mob.isAddedToLevel(),"The final natural transaction cannot insert a blood-day cave-mouth candidate: "+type);

                Mob direct=cave.mob(type);
                ((MobSpawnTypeAccessor)direct).mite$setSpawnType(MobSpawnType.NATURAL);
                var join=new EntityJoinLevelEvent(direct,cave.level,false);
                NeoForge.EVENT_BUS.post(join);
                h.assertTrue(join.isCanceled(),"Direct natural joins cannot bypass the same daytime exclusion: "+type);
                h.assertTrue(SpawnPopulation.localCount(cave.level,direct.getBoundingBox().inflate(2))==0,"A rejected natural join releases the ITE pending reservation: "+type);
            }
            for (var kind:List.of(ExtremeKind.ZOMBIE_IRON_BUCKET,ExtremeKind.RUN_CREEPER,ExtremeKind.ZOMBIE_LORD,ExtremeKind.WANDERING_WITCH,ExtremeKind.ANNIHILATION_SKELETON)) {
                Mob direct=cave.mob(ExtremeMobs.type(kind));
                ((MobSpawnTypeAccessor)direct).mite$setSpawnType(MobSpawnType.NATURAL);
                var join=new EntityJoinLevelEvent(direct,cave.level,false);
                NeoForge.EVENT_BUS.post(join);
                h.assertTrue(join.isCanceled(),"The shared final-join gate covers Extra and old ITE identities: "+kind);
                h.assertTrue(SpawnPopulation.localCount(cave.level,direct.getBoundingBox().inflate(2))==0,"Failed Extra admissions leave no pending population: "+kind);
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="daylight_cavemouth_ite")
    public static void existingNaturalsAndDeathMimicsRemainAdmittedInDaytimeCavemouth(GameTestHelper h) {
        try (Cave cave=new Cave(h)) {
            cave.site.player(120);
            cave.night();
            Mob existing=cave.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_IRON_BUCKET));
            EventHooks.finalizeMobSpawn(existing,cave.level,cave.level.getCurrentDifficultyAt(cave.pos),MobSpawnType.NATURAL,null);
            h.assertTrue(!existing.isSpawnCancelled()&&cave.level.addFreshEntity(existing),"An unlocked night-time natural becomes an admitted inhabitant");
            cave.day();
            h.assertFalse(R196MobSpawning.naturalDaylightAllows(existing,cave.level),"The same habitat is excluded for fresh daytime naturals");
            var transfer=new EntityJoinLevelEvent(existing,cave.level,false);
            NeoForge.EVENT_BUS.post(transfer);
            h.assertFalse(transfer.isCanceled(),"An existing admitted natural survives a later daytime transfer or rejoin");
            cave.site.date(1,1,false);cave.level.updateSkyBrightness();
            Mob saved=cave.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_IRON_BUCKET));
            ((MobSpawnTypeAccessor)saved).mite$setSpawnType(MobSpawnType.NATURAL);
            var disk=new EntityJoinLevelEvent(saved,cave.level,true);
            NeoForge.EVENT_BUS.post(disk);
            h.assertFalse(disk.isCanceled(),"A saved natural still loads before the date floor and during opening grace");
            SpawnPopulation.release(saved);
            Mob mimic=cave.mob(ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE));
            SpawnPopulation.markPlayerDeathMimic(mimic);
            h.assertTrue(mimic.getSpawnType()!=MobSpawnType.NATURAL&&cave.level.addFreshEntity(mimic),"The actual player-death admission marker retains its daytime, day-one and opening-grace exemption");
        }
        h.succeed();
    }

    private static final class Cave implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final IteReferenceSpawnGameTests.Settings settings=new IteReferenceSpawnGameTests.Settings();
        final Map<String,Double> rates=IteSpawnConfig.effectiveSnapshot();
        final Map<String,Double> weights=IteSpawnConfig.effectiveWeights();
        final boolean localSpawns=rates==IteSpawnConfig.localSnapshot()&&weights==IteSpawnConfig.localWeights();
        final ServerLevel level;
        final BlockPos pos;
        final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
        Cave(GameTestHelper h) {
            site=new SpawnBalanceGameTests.Site(h);level=site.level;pos=BlockPos.containing(site.origin);
            settings.set("extreme_mob_spawn_limit_day",65);
            // Eleven horizontal air steps lead to a genuinely open column. The sixteen-
            // block chamber height also gives the native giant its full spawn volume.
            for (BlockPos cell:BlockPos.betweenClosed(pos.offset(-5,-1,-6),pos.offset(10,16,6))) {
                boolean shell=cell.getY()==pos.getY()-1||cell.getY()==pos.getY()+16
                        ||cell.getX()==pos.getX()-5||Math.abs(cell.getZ()-pos.getZ())==6;
                set(cell,shell?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            }
            for (int y=pos.getY();y<level.getMaxBuildHeight();y++)set(pos.offset(11,y-pos.getY(),0),Blocks.AIR.defaultBlockState());
            day();
        }
        void set(BlockPos cell,BlockState state) {
            previous.putIfAbsent(cell.immutable(),level.getBlockState(cell));level.setBlock(cell,state,2);
        }
        void day() {site.date(224,224,true);level.updateSkyBrightness();}
        void night() {site.date(224,224,true);level.setDayTime(223*24_000L+18_000L);level.updateSkyBrightness();}
        Mob mob(EntityType<?> type) {return site.mob(type);}
        @Override public void close() {
            try {site.close();previous.forEach((cell,state)->level.setBlock(cell,state,2));}
            finally {settings.close();if (!localSpawns)IteSpawnConfig.receiveServerSnapshot(rates,weights);}
        }
    }
}
