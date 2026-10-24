package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static io.github.ningmeng123.modernite.entity.ExtremeKind.*;

/** Runtime unlock configuration at the real pool, qualification, finalize and join boundaries. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ExtremeUnlockDayGameTests {
    private ExtremeUnlockDayGameTests() {}

    @GameTest(template="gear_empty",batch="extreme_unlock_day")
    public static void unifiedRulesUnlockExtraCandidatesOnTheConfiguredDate(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.preset.set("extreme_mob_spawn_limit_day",65);
            f.site.date(64,64,true);
            for(var kind:List.of(ZOMBIE_IRON_BUCKET,RUN_CREEPER)) {
                h.assertFalse(SpawnPopulation.automaticAllowed(f.site.level,ExtremeMobs.type(kind)),"The preceding date keeps automatic admission locked: "+kind);
                h.assertFalse(has(pool(f.site.level,f.pos),ExtremeMobs.type(kind)),"The preceding date excludes the actual candidate: "+kind);
            }
            h.assertFalse(has(pool(f.site.level,f.pos),RabidDogContent.RABID_DOG.get()),"Rabid dogs share the configurable unlock date");
            f.site.date(65,65,true);
            for(var type:List.of(ExtremeMobs.type(ZOMBIE_IRON_BUCKET),ExtremeMobs.type(RUN_CREEPER),RabidDogContent.RABID_DOG.get())) {
                h.assertTrue(SpawnPopulation.automaticAllowed(f.site.level,type),"The configured date admits automatic spawning: "+type);
                h.assertTrue(has(pool(f.site.level,f.pos),type),"The configured date adds a real natural candidate: "+type);
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_unlock_day")
    public static void loweringRaisingAndRewindingUseLiveSettingsAndHistoricalProgress(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            var type=ExtremeMobs.type(ZOMBIE_IRON_BUCKET);
            f.site.date(40,40,true);f.preset.set("extreme_mob_spawn_limit_day",65);
            h.assertFalse(has(pool(f.site.level,f.pos),type),"An early world starts below the default date");
            f.preset.set("extreme_mob_spawn_limit_day",33);
            h.assertTrue(has(pool(f.site.level,f.pos),type),"Lowering the setting takes effect without restarting or retaining a hard-coded day 65");
            f.preset.set("extreme_mob_spawn_limit_day",80);
            h.assertFalse(has(pool(f.site.level,f.pos),type),"Raising the setting removes new candidates immediately");
            f.preset.set("extreme_mob_spawn_limit_day",40);
            h.assertTrue(has(pool(f.site.level,f.pos),type),"A live setting equal to historical progress is inclusive");
            f.site.date(1,40,true);
            h.assertTrue(has(pool(f.site.level,f.pos),type),"Rewinding displayed time preserves the historical unlock");
            f.site.date(1,1,true);
            h.assertFalse(has(pool(f.site.level,f.pos),type),"A genuinely new world's history cannot borrow that unlock");
            f.preset.set("extreme_mob_spawn_limit_day",1);f.site.date(1,1,false);
            h.assertFalse(has(pool(f.site.level,f.pos),type),"An early configured date still respects the opening grace period");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_unlock_day")
    public static void baseSpeciesKeepTheirOwnProgressionWhileExtraSpeciesKeepLaterDatesAndHabitats(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.preset.set("extreme_mob_spawn_limit_day",500);f.site.date(33,33,true);
            h.assertTrue(SpawnPopulation.automaticAllowed(f.site.level,EntityType.ZOMBIE),"Base zombies do not inherit the Extra unlock date");
            h.assertTrue(SpawnPopulation.automaticAllowed(f.site.level,MiteMobs.type(MobKind.BONE_LORD)),"A base day-33 species keeps its own progression");
            h.assertFalse(SpawnPopulation.automaticAllowed(f.site.level,MiteMobs.type(MobKind.ANCIENT_BONE_LORD)),"The source ancient bone lord retains its separate day-192 default");
            var base=new R196MobSpawning.Entry(EntityType.ZOMBIE,"EntityZombie",R196SpawnRules.Category.MONSTER,100,1,4,4,false);
            h.assertTrue(has(MiteExtensions.policy().spawnPool(new MiteExtensions.SpawnContext(f.site.level,R196SpawnRules.Category.MONSTER,null),f.pos,List.of(base)),EntityType.ZOMBIE),"The real pool retains a base candidate before Extra species unlock");
            f.preset.set("extreme_mob_spawn_limit_day",33);
            h.assertTrue(MobAbilities.maySpawn(ZOMBIE_IRON_BUCKET,f.site.level,f.pos),"Ordinary Extra species can unlock before day 65");
            h.assertFalse(MobAbilities.maySpawn(WANDERING_WITCH,f.site.level,f.pos),"The original wandering witch still waits until at least day 65");
            f.site.date(65,65,true);
            h.assertTrue(MobAbilities.maySpawn(WANDERING_WITCH,f.site.level,f.pos),"The original wandering-witch date is inclusive");
            f.site.date(127,127,true);
            h.assertFalse(MobAbilities.maySpawn(UNDYING_ZOMBIE_EMPEROR,f.site.level,f.pos),"An early global date does not advance the emperor's day 128");
            f.site.date(128,128,true);
            h.assertTrue(MobAbilities.maySpawn(UNDYING_ZOMBIE_EMPEROR,f.site.level,f.pos),"The emperor unlocks on its later species date");
            ServerLevel nether=f.site.level.getServer().getLevel(Level.NETHER),end=f.site.level.getServer().getLevel(Level.END);
            h.assertTrue(nether!=null&&end!=null,"Standard dimensions exist for habitat controls");
            f.site.date(95,95,true);
            h.assertFalse(MobAbilities.maySpawn(SKELETON_LIGHTNING,nether,f.pos),"Lightning skeleton retains its day-96 minimum");
            f.site.date(96,96,true);
            h.assertTrue(MobAbilities.maySpawn(SKELETON_LIGHTNING,nether,f.pos),"Lightning skeleton can appear in its later Nether habitat");
            h.assertFalse(MobAbilities.maySpawn(SKELETON_LIGHTNING,f.site.level,f.pos),"Unlocking the date does not add an overworld lightning-skeleton habitat");
            h.assertFalse(MobAbilities.maySpawn(UNDYING_ZOMBIE_EMPEROR,nether,f.pos),"The emperor does not gain a Nether habitat");
            h.assertFalse(MobAbilities.maySpawn(RUN_CREEPER,end,f.pos),"The End remains outside ordinary Extra habitats");
            h.assertFalse(RabidDogContent.maySpawn(nether)||RabidDogContent.maySpawn(end),"Rabid dogs retain only overworld/Underworld habitats");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_unlock_day")
    public static void referencePoolAddsEachExtraOnceAndPreservesOnlyTheOriginalUnderworldDuplicates(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.preset.set("extreme_mob_spawn_limit_day",33);f.site.date(224,224,true);
            for(ServerLevel level:f.site.level.getServer().getAllLevels()) {
                var entries=pool(level,f.pos);
                for(var kind:ExtremeKind.values())if(!IteReferenceSpawns.referenceIdentity(kind))
                    h.assertTrue(count(entries,ExtremeMobs.type(kind))<=1,"Appending Extra candidates never duplicates identity "+kind+" in "+level.dimension().location());
                h.assertTrue(count(entries,RabidDogContent.RABID_DOG.get())<=1,"Rabid dogs are appended once");
                if(level.dimension()==Level.OVERWORLD||MobAbilities.underground(level)) {
                    h.assertTrue(count(entries,ExtremeMobs.type(ZOMBIE_IRON_BUCKET))==1,"Eligible overworld and Underworld pools contain an Extra zombie");
                    h.assertTrue(count(entries,RabidDogContent.RABID_DOG.get())==1,"Eligible overworld and Underworld pools contain a rabid dog");
                    for(var kind:List.of(ZOMBIE_LORD,ANNIHILATION_SKELETON,WANDERING_WITCH))
                        h.assertTrue(count(entries,ExtremeMobs.type(kind))==(MobAbilities.underground(level)?2:1),"The old ITE constructor multiplicity is preserved: "+kind);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_unlock_day")
    public static void zeroWeightsAndEventOnlyIdentitiesCannotGainNaturalEntries(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.preset.set("extreme_mob_spawn_limit_day",1);f.site.date(224,224,true);
            String id="modern_ite:zombie_iron_bucket";
            var entries=pool(f.site.level,f.pos);
            var iron=entries.stream().filter(entry->entry.type()==ExtremeMobs.type(ZOMBIE_IRON_BUCKET)).findFirst().orElseThrow();
            IteSpawnConfig.receiveServerSnapshot(Map.of(id,0D),Map.of());
            h.assertTrue(ConfiguredMonsterSpawns.choose(f.site.level,f.pos,R196SpawnRules.Category.MONSTER,List.of(iron),RandomSource.create(65),true)==null,"A zero natural rate still removes the newly unlocked candidate");
            IteSpawnConfig.receiveServerSnapshot(Map.of(),Map.of(id,0D));
            h.assertTrue(ConfiguredMonsterSpawns.choose(f.site.level,f.pos,R196SpawnRules.Category.MONSTER,List.of(iron),RandomSource.create(65),true)==null,"A zero absolute weight still removes the newly unlocked candidate");
            IteSpawnConfig.receiveServerSnapshot(Map.of(),Map.of("modern_ite:mimic_zombie",1000D,"modern_ite:zombie_boss",1000D));
            entries=pool(f.site.level,f.pos);
            for(var kind:List.of(MIMIC_ZOMBIE,ZOMBIE_BOSS)) {
                h.assertFalse(has(entries,ExtremeMobs.type(kind)),"An absolute weight cannot create an event-only natural entry: "+kind);
                h.assertFalse(MobAbilities.maySpawn(kind,f.site.level,f.pos),"The species itself rejects natural admission: "+kind);
            }
            f.preset.set("zombie_lord_spawn_frequency",0);
            h.assertFalse(has(pool(f.site.level,f.pos),ExtremeMobs.type(ZOMBIE_LORD)),"The legacy zero spawn-frequency setting continues to disable its own pool entry");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_unlock_day")
    public static void referenceSpecialPoolsExcludeExtraCandidatesAfterUnlock(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.preset.set("extreme_mob_spawn_limit_day",1);f.site.date(224,224,true);
            h.assertTrue(has(pool(f.site.level,f.pos),ExtremeMobs.type(ZOMBIE_IRON_BUCKET)),"The ordinary reference-biome control contains Extra candidates");
            try(SpecialStructure hut=new SpecialStructure(f.site.level,f.pos,BuiltinStructures.SWAMP_HUT)) {
                h.assertTrue(f.site.level.structureManager().getStructureWithPieceAt(f.pos,hut.structure).isValid(),"The hut fixture is visible through the real structure-manager query");
                h.assertTrue(pool(f.site.level,f.pos).isEmpty(),"A swamp-hut pool does not gain Extra or ordinary ITE additions");
            }
            try(MushroomBiome ignored=new MushroomBiome(f.site.level,f.pos)) {
                BlockPos surface=new BlockPos(f.pos.getX(),f.site.level.getHeight(Heightmap.Types.WORLD_SURFACE,f.pos.getX(),f.pos.getZ())-1,f.pos.getZ());
                h.assertTrue(f.site.level.getBiome(surface).is(Biomes.MUSHROOM_FIELDS),
                        "The real surface-biome query samples mushroom fields even when the randomized test site lies on a chunk boundary");
                h.assertTrue(pool(f.site.level,f.pos).isEmpty(),"A mushroom constructor remains free of Extra additions");
            }
            ServerLevel nether=f.site.level.getServer().getLevel(Level.NETHER),end=f.site.level.getServer().getLevel(Level.END);
            h.assertTrue(nether!=null&&end!=null,"Nether and End special-pool controls exist");
            h.assertTrue(has(pool(nether,f.pos),ExtremeMobs.type(RUN_CREEPER)),"An ordinary Nether pool can contain an eligible Extra species");
            try(SpecialStructure fortress=new SpecialStructure(nether,f.pos,BuiltinStructures.FORTRESS)) {
                h.assertTrue(nether.structureManager().getStructureWithPieceAt(f.pos,fortress.structure).isValid(),"The fortress fixture has a real occupied structure piece");
                h.assertTrue(pool(nether,f.pos).isEmpty(),"The fortress override pool does not receive Extra additions");
            }
            h.assertTrue(pool(end,f.pos).isEmpty(),"The End constructor remains free of Extra additions");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_unlock_day")
    public static void actualNaturalQualificationFinalizeAndJoinRespectTheLiveUnlock(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.preset.set("extreme_mob_spawn_limit_day",33);f.site.date(32,32,true);f.site.player(40);
            BlockPos pos=f.pos.atY(-49);
            try(DarkFloor floor=new DarkFloor(f.site.level,pos)) {
                Mob before=f.mob(ZOMBIE_IRON_BUCKET,pos);
                h.assertFalse(R196MobSpawning.qualifiesWithEvent(before,f.site.level,MobSpawnType.NATURAL,true,true),"The actual scheduler qualification rejects the preceding date");
                EventHooks.finalizeMobSpawn(before,f.site.level,f.site.level.getCurrentDifficultyAt(pos),MobSpawnType.NATURAL,null);
                h.assertTrue(before.isSpawnCancelled(),"The real natural-finalize event rejects the preceding date");
                h.assertFalse(f.site.level.addFreshEntity(f.mob(ZOMBIE_IRON_BUCKET,pos)),"A direct unadmitted join cannot bypass the unlock date");
                f.site.date(33,33,true);f.site.level.setDayTime(32*24_000L+18_000L);f.site.level.updateSkyBrightness();
                Mob after=f.mob(ZOMBIE_IRON_BUCKET,pos);
                h.assertTrue(R196MobSpawning.qualifiesWithEvent(after,f.site.level,MobSpawnType.NATURAL,true,true),"A real supported dark candidate qualifies on the configured day, including dates before 65");
                EventHooks.finalizeMobSpawn(after,f.site.level,f.site.level.getCurrentDifficultyAt(pos),MobSpawnType.NATURAL,null);
                h.assertFalse(after.isSpawnCancelled(),"The real natural-finalize event accepts the configured day");
                h.assertTrue(f.site.level.addFreshEntity(after),"The finalized candidate survives the real entity-join event");
                f.preset.set("extreme_mob_spawn_limit_day",80);
                Mob lockedAgain=f.mob(ZOMBIE_IRON_BUCKET,pos);
                EventHooks.finalizeMobSpawn(lockedAgain,f.site.level,f.site.level.getCurrentDifficultyAt(pos),MobSpawnType.NATURAL,null);
                h.assertTrue(lockedAgain.isSpawnCancelled(),"Raising the live day immediately rejects the next natural-finalize attempt");
                h.assertTrue(after.isAlive()&&!after.isRemoved(),"Raising the date preserves an already admitted inhabitant");
            }
        }
        h.succeed();
    }

    private static List<R196MobSpawning.Entry> pool(ServerLevel level,BlockPos pos) {
        return MiteExtensions.policy().spawnPool(new MiteExtensions.SpawnContext(level,R196SpawnRules.Category.MONSTER,null),pos,List.of());
    }
    private static long count(List<R196MobSpawning.Entry> entries,EntityType<?> type) {return entries.stream().filter(entry->entry.type()==type).count();}
    private static boolean has(List<R196MobSpawning.Entry> entries,EntityType<?> type) {return count(entries,type)>0;}

    private static final class Fixture implements AutoCloseable {
        final Map<String,Double> rates=IteSpawnConfig.effectiveSnapshot(),weights=IteSpawnConfig.effectiveWeights();
        final boolean remoteRates=rates!=IteSpawnConfig.localSnapshot()||weights!=IteSpawnConfig.localWeights();
        final IteReferenceSpawnGameTests.Settings preset;
        final SpawnBalanceGameTests.Site site;
        final BlockPos pos;
        Fixture(GameTestHelper h) {
            preset=new IteReferenceSpawnGameTests.Settings();
            preset.set("wandering_witch_spawn_limit_day_overworld",65);preset.set("wandering_witch_spawn_limit_day_other",65);
            site=new SpawnBalanceGameTests.Site(h);pos=BlockPos.containing(site.origin);
            IteSpawnConfig.receiveServerSnapshot(Map.of(),Map.of());
        }
        Mob mob(ExtremeKind kind,BlockPos pos) {Mob mob=site.mob(ExtremeMobs.type(kind));mob.moveTo(pos.getX()+.5,pos.getY()+.005,pos.getZ()+.5,0,0);return mob;}
        @Override public void close() {
            site.close();preset.close();
            if(remoteRates)IteSpawnConfig.receiveServerSnapshot(rates,weights);else IteSpawnConfig.clearServerSnapshot();
        }
    }

    private static final class DarkFloor implements AutoCloseable {
        final ServerLevel level;final Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        DarkFloor(ServerLevel level,BlockPos pos) {
            this.level=level;
            for(BlockPos point:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,4,2)))set(point,Blocks.AIR.defaultBlockState());
            set(pos.below(),Blocks.STONE.defaultBlockState());
        }
        void set(BlockPos pos,BlockState state) {before.putIfAbsent(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,state,2);}
        @Override public void close() {before.forEach((pos,state)->level.setBlock(pos,state,2));}
    }

    /** Register a real bounded piece without generating or placing a whole world structure. */
    private static final class SpecialStructure implements AutoCloseable {
        final LevelChunk chunk;final Structure structure;
        final Map<Structure,StructureStart> starts;final Map<Structure,LongSet> references=new LinkedHashMap<>();
        SpecialStructure(ServerLevel level,BlockPos pos,net.minecraft.resources.ResourceKey<Structure> key) {
            chunk=level.getChunkAt(pos);structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(key);
            starts=new LinkedHashMap<>(chunk.getAllStarts());chunk.getAllReferences().forEach((type,values)->references.put(type,new LongOpenHashSet(values)));
            var piece=new SwampHutPiece(RandomSource.create(65),pos.getX(),pos.getZ());var center=piece.getBoundingBox().getCenter();
            piece.move(pos.getX()-center.getX(),pos.getY()-center.getY(),pos.getZ()-center.getZ());
            chunk.setStartForStructure(structure,new StructureStart(structure,chunk.getPos(),0,new PiecesContainer(List.of(piece))));
            chunk.addReferenceForStructure(structure,chunk.getPos().toLong());
        }
        @Override public void close() {chunk.setAllStarts(starts);chunk.setAllReferences(references);}
    }

    private static final class MushroomBiome implements AutoCloseable {
        final Map<LevelChunk,Map<BlockPos,Holder<Biome>>> before=new LinkedHashMap<>();
        MushroomBiome(ServerLevel level,BlockPos pos) {
            var mushroom=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.MUSHROOM_FIELDS);
            int centerX=pos.getX()>>4,centerZ=pos.getZ()>>4;
            // BiomeManager's Voronoi sampling can choose a neighboring chunk at the random test-grid boundary.
            for(int cx=centerX-1;cx<=centerX+1;cx++)for(int cz=centerZ-1;cz<=centerZ+1;cz++) {
                LevelChunk chunk=level.getChunk(cx,cz);var samples=new LinkedHashMap<BlockPos,Holder<Biome>>();
                int x=chunk.getPos().getMinBlockX()>>2,z=chunk.getPos().getMinBlockZ()>>2;
                for(int qx=x;qx<x+4;qx++)for(int qz=z;qz<z+4;qz++)for(int qy=level.getMinBuildHeight()>>2;qy<(level.getMaxBuildHeight()>>2);qy++)
                    samples.put(new BlockPos(qx,qy,qz),chunk.getNoiseBiome(qx,qy,qz));
                before.put(chunk,samples);chunk.fillBiomesFromNoise((qx,qy,qz,sampler)->mushroom,null);
            }
        }
        @Override public void close() {before.forEach((chunk,samples)->chunk.fillBiomesFromNoise((qx,qy,qz,sampler)->samples.get(new BlockPos(qx,qy,qz)),null));}
    }
}
