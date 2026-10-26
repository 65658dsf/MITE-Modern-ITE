package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.config.IteConfigSync;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static io.github.ningmeng123.modernite.entity.ExtremeKind.*;

/** Species dates remain subordinate to the shared gate at real automatic entry points. @author NingMeng123 */
@GameTestHolder("modern_ite_spawn_days")
@PrefixGameTestTemplate(false)
public final class ExtremeMobSpawnDayGameTests {
    private ExtremeMobSpawnDayGameTests() {}

    @GameTest(template="gear_empty",batch="extreme_mob_spawn_days")
    public static void everySpeciesHasAnEditableIntegerDateExceptThePlayerMimic(GameTestHelper h) {
        var keys=new ArrayList<String>();
        for(var kind:ExtremeKind.values()) {
            if(kind==MIMIC_ZOMBIE||kind==WANDERING_WITCH)continue;
            String key=kind.id()+"_spawn_limit_day";keys.add(key);
            int expected=kind==ANNIHILATION_SKELETON?33:Math.max(1,kind.speciesFirstDay);
            h.assertTrue(IteConfig.defaults().get(key).equals(expected),"The original species date becomes its editable default: "+kind);
        }
        keys.addAll(List.of("rabid_dog_spawn_limit_day","wandering_witch_spawn_limit_day_overworld","wandering_witch_spawn_limit_day_other"));
        h.assertTrue(keys.size()==46&&!IteConfig.defaults().containsKey("mimic_zombie_spawn_limit_day")
                &&!IteConfig.defaults().containsKey("wandering_witch_spawn_limit_day"),
                "All 45 non-player species are configurable, with two existing witch dimension dates and no duplicate or player-mimic field");
        var controls=MiteExtensions.policy().spawningControls();
        for(String key:keys) {
            var option=IteConfig.options().stream().filter(value->value.key().equals(key)).findFirst().orElseThrow();
            h.assertTrue(option.path().equals(List.of("GameMechanics","MobSpawning",key))
                    &&option.defaultValue() instanceof Integer&&option.minimum()==1D&&option.maximum()==1000000D,
                    "Each date is a typed, bounded public setting: "+key);
            h.assertTrue(controls.stream().anyMatch(control->control.key().equals(key)&&control.defaultValue().equals(option.defaultValue())),
                    "The main-mod editor exposes every species date: "+key);
            for(Object invalid:List.of(0,-1,1000001,5.5D,Double.NaN,"15"))
                h.assertTrue(IteConfig.validated(Map.of(key,invalid)).get(key).equals(option.defaultValue()),"Invalid species dates fall back safely: "+key);
            for(int boundary:new int[]{1,1000000})
                h.assertTrue(IteConfig.validated(Map.of(key,boundary)).get(key).equals(boundary),"Date bounds remain valid: "+key);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_mob_spawn_days")
    public static void everySpeciesUsesTheLaterOfGlobalAndIndividualDates(GameTestHelper h) {
        try(var site=new SpawnBalanceGameTests.Site(h);var settings=new IteReferenceSpawnGameTests.Settings()) {
            settings.set("extreme_mob_spawn_limit_day",10);
            var types=new ArrayList<EntityType<?>>();
            for(var kind:ExtremeKind.values())if(kind!=MIMIC_ZOMBIE)types.add(ExtremeMobs.type(kind));
            types.add(RabidDogContent.RABID_DOG.get());
            for(int individual:new int[]{5,15}) {
                setAllDates(settings,individual);
                int expected=Math.max(10,individual);
                for(int day:new int[]{expected-1,expected}) {
                    site.date(day,day,true);
                    for(var level:site.level.getServer().getAllLevels())for(var type:types) {
                        String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                        h.assertTrue(IteConfig.extremeMobFirstDay(id,level.dimension()==Level.OVERWORLD)==expected,
                                "Global 10 and individual "+individual+" combine to "+expected+": "+id);
                        h.assertTrue(SpawnPopulation.automaticAllowed(level,type)==(day>=expected),
                                "Every automatic entry uses the inclusive combined date: "+id+" day="+day);
                    }
                }
            }
            h.assertTrue(IteConfig.extremeMobFirstDay("modern_ite:future_monster",true)==10,
                    "A future ITE identity still inherits the shared date");
            h.assertTrue(IteConfig.extremeMobFirstDay("minecraft:zombie",true)==0,
                    "Unrelated namespaces do not inherit ITE dates");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_mob_spawn_days")
    public static void liveIndividualDatesControlPoolsAndAutomaticFinalizeJoinAndSupport(GameTestHelper h) {
        try(var site=new SpawnBalanceGameTests.Site(h);var settings=new IteReferenceSpawnGameTests.Settings()) {
            settings.set("extreme_mob_spawn_limit_day",10);setAllDates(settings,5);
            var pos=BlockPos.containing(site.origin);
            var context=new MiteExtensions.SpawnContext(site.level,R196SpawnRules.Category.MONSTER,null);
            var types=List.of(ExtremeMobs.type(DRAGON_ZOMBIE),ExtremeMobs.type(SKELETON_ELITE_LORD),
                    ExtremeMobs.type(ANNIHILATION_SKELETON),ExtremeMobs.type(UNDYING_ZOMBIE_EMPEROR),RabidDogContent.RABID_DOG.get());
            site.date(10,10,true);
            var entries=MiteExtensions.policy().spawnPool(context,pos,List.of());
            for(var type:types)h.assertTrue(entries.stream().anyMatch(entry->entry.type()==type),
                    "Lowering a species date removes its old hard-coded minimum from the real pool: "+type);
            h.assertFalse(entries.stream().anyMatch(entry->entry.type()==ExtremeMobs.type(ZOMBIE_BOSS)||entry.type()==ExtremeMobs.type(MIMIC_ZOMBIE)),
                    "Date settings do not create event-only natural candidates");
            setAllDates(settings,15);site.date(14,14,true);
            var owner=site.mob(EntityType.ENDER_DRAGON);
            for(var type:types) {
                h.assertFalse(MiteExtensions.policy().spawnPool(context,pos,List.of()).stream().anyMatch(entry->entry.type()==type),
                        "A later species date removes the live natural candidate: "+type);
                for(var reason:List.of(MobSpawnType.NATURAL,MobSpawnType.SPAWNER,MobSpawnType.MOB_SUMMONED,
                        MobSpawnType.JOCKEY,MobSpawnType.REINFORCEMENT,MobSpawnType.EVENT,MobSpawnType.CONVERSION)) {
                    Mob candidate=site.mob(type);
                    EventHooks.finalizeMobSpawn(candidate,site.level,site.level.getCurrentDifficultyAt(pos),reason,null);
                    h.assertTrue(candidate.isSpawnCancelled(),"The actual finalize event blocks a late species: "+type+" "+reason);
                }
                h.assertFalse(site.level.addFreshEntity(site.mob(type)),"Reasonless automatic joins also check the species date: "+type);
                h.assertFalse(SpawnPopulation.reserveSupport(site.mob(type),owner,true),"Encounter support cannot bypass its own species date: "+type);
            }
            site.date(15,15,true);
            for(var type:types) {
                Mob candidate=site.mob(type);
                EventHooks.finalizeMobSpawn(candidate,site.level,site.level.getCurrentDifficultyAt(pos),MobSpawnType.CONVERSION,null);
                h.assertTrue(!candidate.isSpawnCancelled()&&site.level.addFreshEntity(candidate),"The configured species day unlocks finalize and join inclusively: "+type);
            }
            settings.set("dragon_zombie_spawn_limit_day",30);
            h.assertTrue(site.mobs.stream().anyMatch(mob->mob.getType()==ExtremeMobs.type(DRAGON_ZOMBIE)&&mob.isAddedToLevel()&&mob.isAlive()),
                    "Raising an individual date preserves existing admitted inhabitants");
            h.assertFalse(SpawnPopulation.automaticAllowed(site.level,ExtremeMobs.type(DRAGON_ZOMBIE)),"Raising the same date immediately blocks the next automatic spawn");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_mob_spawn_days")
    public static void wanderingWitchKeepsIndependentDimensionDatesUnderTheSharedGate(GameTestHelper h) {
        try(var site=new SpawnBalanceGameTests.Site(h);var settings=new IteReferenceSpawnGameTests.Settings()) {
            settings.set("extreme_mob_spawn_limit_day",10);
            settings.set("wandering_witch_spawn_limit_day_overworld",5);
            settings.set("wandering_witch_spawn_limit_day_other",15);
            h.assertTrue(IteConfig.extremeMobFirstDay("modern_ite:wandering_witch",true)==10
                    &&IteConfig.extremeMobFirstDay("modern_ite:wandering_witch",false)==15,
                    "The existing overworld and other-dimension fields combine independently with the shared date");
            site.date(10,10,true);
            h.assertTrue(SpawnPopulation.automaticAllowed(site.level,ExtremeMobs.type(WANDERING_WITCH)),"Overworld witch unlocks on the shared day 10");
            var nether=site.level.getServer().getLevel(Level.NETHER);
            h.assertTrue(nether!=null&&!SpawnPopulation.automaticAllowed(nether,ExtremeMobs.type(WANDERING_WITCH)),"Other-dimension automatic admission waits for its later date");
            site.date(15,15,true);
            h.assertTrue(SpawnPopulation.automaticAllowed(nether,ExtremeMobs.type(WANDERING_WITCH)),"Other-dimension automatic admission unlocks on day 15");
            h.assertFalse(MobAbilities.maySpawn(WANDERING_WITCH,nether,BlockPos.containing(site.origin)),"Unlocking dates keeps natural habitat restrictions intact");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_mob_spawn_days")
    public static void speciesDatesRoundTripThroughServerSynchronization(GameTestHelper h) {
        var before=IteConfig.effectiveSnapshot();boolean remote=before!=IteConfig.localSnapshot();
        var rates=IteSpawnConfig.effectiveSnapshot();var weights=IteSpawnConfig.effectiveWeights();
        boolean remoteSpawns=rates!=IteSpawnConfig.localSnapshot()||weights!=IteSpawnConfig.localWeights();
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var source=new HashMap<>(before);source.put("extreme_mob_spawn_limit_day",10);
            source.put("dragon_zombie_spawn_limit_day",5);source.put("rabid_dog_spawn_limit_day",15);
            IteConfigSync.CODEC.encode(buffer,new IteConfigSync(source));
            var decoded=IteConfigSync.CODEC.decode(buffer);IteConfig.receiveServerSnapshot(decoded.values());
            h.assertTrue(IteConfig.value("dragon_zombie_spawn_limit_day").equals(5)&&IteConfig.value("rabid_dog_spawn_limit_day").equals(15),
                    "The wire codec retains each new integer date");
            h.assertTrue(IteConfig.extremeMobFirstDay("modern_ite:dragon_zombie",true)==10
                    &&IteConfig.extremeMobFirstDay("modern_ite:rabid_dog",true)==15,
                    "Received server values drive the same combined-date calculation");
        } finally {
            buffer.release();IteConfig.clearServerSnapshot();
            if(remote)IteConfig.receiveServerSnapshot(before);
            if(remoteSpawns)IteSpawnConfig.receiveServerSnapshot(rates,weights);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_mob_spawn_days")
    public static void mainModEditorSavesAndImmediatelyPublishesIndividualDates(GameTestHelper h) throws Exception {
        try(var site=new SpawnBalanceGameTests.Site(h)) {
            var previous=IteConfig.effectiveSnapshot();
            boolean remote=previous!=IteConfig.localSnapshot();
            var rates=IteSpawnConfig.effectiveSnapshot();var weights=IteSpawnConfig.effectiveWeights();
            boolean remoteSpawns=rates!=IteSpawnConfig.localSnapshot()||weights!=IteSpawnConfig.localWeights();
            IteConfig.clearServerSnapshot();
            var policy=MiteExtensions.policy();var before=policy.spawningControlValues();
            try {
                var edited=new HashMap<>(before);edited.put("extreme_mob_spawn_limit_day",10);
                edited.put("dragon_zombie_spawn_limit_day",5);edited.put("rabid_dog_spawn_limit_day",15);
                h.assertTrue(policy.saveSpawningControls(edited),"The actual editor provider saves new species date fields");
                site.date(10,10,true);
                h.assertTrue(SpawnPopulation.automaticAllowed(site.level,ExtremeMobs.type(DRAGON_ZOMBIE))
                        &&!SpawnPopulation.automaticAllowed(site.level,RabidDogContent.RABID_DOG.get()),
                        "Editor saves immediately update actual automatic admission");
                var file=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("modern_ite-common.toml");
                var config=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(file));
                h.assertTrue(((Number)config.get(IteConfig.setting("dragon_zombie_spawn_limit_day").getPath())).intValue()==5
                        &&((Number)config.get(IteConfig.setting("rabid_dog_spawn_limit_day").getPath())).intValue()==15,
                        "Both independent dates persist in the isolated configuration file");
            } finally {
                try {h.assertTrue(policy.saveSpawningControls(before),"The editor provider restores its prior values");}
                finally {
                    IteConfig.clearServerSnapshot();
                    if(remote)IteConfig.receiveServerSnapshot(previous);
                    if(remoteSpawns)IteSpawnConfig.receiveServerSnapshot(rates,weights);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="extreme_mob_spawn_days")
    public static void playerDeathMimicRetainsItsRealDeathSourceExemption(GameTestHelper h) {
        ExtremeDeathAndExplosionGameTests.playerDeathMimicIgnoresConfiguredUnlockAndOpeningGrace(h);
    }

    private static void setAllDates(IteReferenceSpawnGameTests.Settings settings,int day) {
        for(var kind:ExtremeKind.values())if(kind!=MIMIC_ZOMBIE&&kind!=WANDERING_WITCH)settings.set(kind.id()+"_spawn_limit_day",day);
        settings.set("rabid_dog_spawn_limit_day",day);
        settings.set("wandering_witch_spawn_limit_day_overworld",day);settings.set("wandering_witch_spawn_limit_day_other",day);
    }
}
