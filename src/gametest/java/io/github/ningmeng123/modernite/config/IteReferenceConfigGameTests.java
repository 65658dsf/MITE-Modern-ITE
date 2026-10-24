package io.github.ningmeng123.modernite.config;

import io.github.ningmeng123.mite.config.GameplayConfigExport;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Editable ITE defaults, retired-preset migration, synchronization and exports. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IteReferenceConfigGameTests {
    private IteReferenceConfigGameTests() {}

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void editableDefaultsIgnoreTheRetiredPresetAndPreserveCustomValues(GameTestHelper helper) {
        helper.assertTrue(IteConfig.options().stream().noneMatch(value->value.key().equals("reference_spawning"))
                        &&!IteConfig.defaults().containsKey("reference_spawning"),
                "The retired preset is absent from generated settings and every editor");
        var defaults=IteConfig.defaults();
        helper.assertTrue(defaults.get("blood_moon_max_hostile_fraction").equals(3D)
                        &&defaults.get("ancient_bone_lord_spawn_limit_day").equals(33)
                        &&defaults.get("mob_max_spawn_count_day_minimum").equals(100)
                        &&defaults.get("blood_moon_hostile_spawn_groups").equals(4),
                "Generated values adopt the supplied 2026-10-24 factory preset");
        var old=Map.<String,Object>of("reference_spawning",false,"mob_max_spawn_count_chunk_base",37,
                "mob_max_spawn_count_day_minimum",123,"blood_moon_max_hostile_fraction",5D,
                "local_hostile_limit",19,"compressed_skeleton_expand_count",9);
        var validated=IteConfig.validated(old);
        helper.assertTrue(!validated.containsKey("reference_spawning"),"Legacy payload flags cannot select a second rule set");
        old.forEach((key,value)-> {if(!key.equals("reference_spawning"))helper.assertTrue(value.equals(validated.get(key)),
                "Explicit editable and splitting values remain exact: "+key);});
        helper.assertTrue(Integer.valueOf(3).equals(validated.get("extreme_mob_spawn_limit_day")),
                "Missing extreme start dates retain the day-three default");
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void retiredPresetFilesMigrateDefaultsOnceAndKeepCustomValues(GameTestHelper helper) throws Exception {
        for(boolean retired:new boolean[]{true,false}) {
            var file=Files.createTempFile("ite-retired-preset-",".toml");
            try {
                Files.writeString(file,"""
                        # User annotations and unrelated features must survive.
                        [GameMechanics.MobSpawning]
                        reference_spawning = %s
                        blood_moon_max_hostile_fraction = 3.0
                        ancient_bone_lord_spawn_limit_day = 33
                        mob_max_spawn_count_day_minimum = 100
                        blood_moon_hostile_spawn_groups = 4
                        mob_max_spawn_count_chunk_base = 73
                        extreme_mob_spawn_limit_day = 17
                        [Entities]
                        compressed_skeleton_expand_count = 9
                        """.formatted(retired));
                IteConfig.migrateLegacySpawningConfig(file);
                var parsed=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(file));
                String prefix="GameMechanics.MobSpawning.";
                helper.assertTrue(!parsed.contains(prefix+"reference_spawning")
                                &&((Number)parsed.get(prefix+"blood_moon_max_hostile_fraction")).doubleValue()==3D
                                &&((Number)parsed.get(prefix+"ancient_bone_lord_spawn_limit_day")).intValue()==33
                                &&((Number)parsed.get(prefix+"mob_max_spawn_count_day_minimum")).intValue()==100
                                &&((Number)parsed.get(prefix+"blood_moon_hostile_spawn_groups")).intValue()==4,
                        "Either retired preset migrates only its four obsolete defaults");
                helper.assertTrue(((Number)parsed.get(prefix+"mob_max_spawn_count_chunk_base")).intValue()==73
                                &&((Number)parsed.get(prefix+"extreme_mob_spawn_limit_day")).intValue()==17
                                &&((Number)parsed.get("Entities.compressed_skeleton_expand_count")).intValue()==9
                                &&Files.readString(file).contains("User annotations"),
                        "Custom values, splitting controls, unrelated sections and user comments survive migration");
                Files.writeString(file,"""
                        [GameMechanics.MobSpawning]
                        mob_max_spawn_count_day_minimum = 100
                        blood_moon_max_hostile_fraction = 3.0
                        """);
                String explicit=Files.readString(file);
                IteConfig.migrateLegacySpawningConfig(file);
                helper.assertTrue(Files.readString(file).equals(explicit),
                        "Removing the old key makes migration one-time; later explicit edits are never reset");
            } finally {Files.deleteIfExists(file);}
        }
        var custom=Files.createTempFile("ite-custom-preset-",".toml");
        try {
            Files.writeString(custom,"""
                    [GameMechanics.MobSpawning]
                    reference_spawning = true
                    blood_moon_max_hostile_fraction = 5.0
                    ancient_bone_lord_spawn_limit_day = 234
                    mob_max_spawn_count_day_minimum = 27
                    blood_moon_hostile_spawn_groups = 7
                    """);
            IteConfig.migrateLegacySpawningConfig(custom);
            var parsed=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(custom));
            helper.assertTrue(((Number)parsed.get("GameMechanics.MobSpawning.blood_moon_max_hostile_fraction")).doubleValue()==5D
                            &&((Number)parsed.get("GameMechanics.MobSpawning.ancient_bone_lord_spawn_limit_day")).intValue()==234
                            &&((Number)parsed.get("GameMechanics.MobSpawning.mob_max_spawn_count_day_minimum")).intValue()==27
                            &&((Number)parsed.get("GameMechanics.MobSpawning.blood_moon_hostile_spawn_groups")).intValue()==7,
                    "Non-default custom values remain editable and exact even when the retired reference preset hid them");
        } finally {Files.deleteIfExists(custom);}
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void extremeStartDayHasTypedBoundsAndPublishesImmediately(GameTestHelper helper) {
        String key="extreme_mob_spawn_limit_day";
        var option=IteConfig.options().stream().filter(value->value.key().equals(key)).findFirst().orElseThrow();
        helper.assertTrue(option.path().equals(List.of("GameMechanics","MobSpawning",key))
                        &&Integer.valueOf(3).equals(option.defaultValue())&&Integer.valueOf(3).equals(IteConfig.setting(key).getDefault())
                        &&option.minimum()==1D&&option.maximum()==1000000D,
                "The editable start day retains its public path, integer default and bounds");
        for(Object invalid:List.of(0,-1,1000001,3.5D,Double.NaN,"65"))
            helper.assertTrue(Integer.valueOf(3).equals(IteConfig.validated(Map.of(key,invalid)).get(key)),"Invalid start days use the supplied day-three default: "+invalid);
        var before=IteConfig.localSnapshot();
        try {
            for(int day:new int[]{1,65,173,1000000}) {
                IteConfig.setting(key).set(day);IteConfig.refresh();
                helper.assertTrue(IteConfig.localSnapshot().get(key).equals(day),"Refreshing publishes the edited date immediately: "+day);
            }
        } finally {IteConfig.setting(key).set(before.get(key));IteConfig.refresh();}
        helper.assertTrue(IteConfig.localSnapshot().equals(before),"Hot-refresh testing restores the complete local snapshot");
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void newCrowdAndPacingFieldsHaveTypedBoundsAndPublishImmediately(GameTestHelper helper) {
        var defaults=Map.<String,Object>of("natural_local_hostile_limit",5,"full_moon_natural_local_hostile_limit",10,
                "blood_moon_natural_local_hostile_limit",12,"deep_hostile_cap_multiplier",2D,
                "hostile_spawn_interval_ticks",5,"deep_spawn_chance_percent",10D,"surface_spawn_chance_percent",17D,
                "blood_moon_spawn_chance_multiplier",1D,"other_dimension_spawn_chance_multiplier",.25D);
        var before=IteConfig.localSnapshot();
        try {
            for(var entry:defaults.entrySet()) {
                String key=entry.getKey();Object fallback=entry.getValue();
                var option=IteConfig.options().stream().filter(value->value.key().equals(key)).findFirst().orElseThrow();
                helper.assertTrue(option.path().equals(List.of("GameMechanics","MobSpawning",key))
                                &&option.defaultValue().equals(fallback)&&IteConfig.setting(key).getDefault().equals(fallback),
                        "Each crowd and pacing field has its stable editable path and ITE default: "+key);
                for(Object invalid:List.of(-1,Double.NaN,Double.POSITIVE_INFINITY,"5",option.maximum()+1D))
                    helper.assertTrue(IteConfig.validated(Map.of(key,invalid)).get(key).equals(fallback),
                            "Invalid crowd/pacing values use the typed default: "+key);
                if(fallback instanceof Integer)helper.assertTrue(IteConfig.validated(Map.of(key,2.5D)).get(key).equals(fallback),
                        "Integer crowd/pacing fields reject fractional values: "+key);
                for(double boundary:new double[]{option.minimum(),option.maximum()}) {
                    Object expected=option.validated(boundary);
                    helper.assertTrue(IteConfig.validated(Map.of(key,boundary)).get(key).equals(expected),
                            "Both inclusive boundaries are accepted: "+key);
                }
                IteConfig.setting(key).set(option.validated(7));IteConfig.refresh();
                helper.assertTrue(IteConfig.localSnapshot().get(key).equals(option.validated(7)),
                        "Saving publishes each field without changing presets or reloading the world: "+key);
            }
        } finally {defaults.keySet().forEach(key->IteConfig.setting(key).set(before.get(key)));IteConfig.refresh();}
        helper.assertTrue(IteConfig.localSnapshot().equals(before),"Crowd/pacing validation restores the complete local snapshot");
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void editableSpawningValuesRoundTripAlongsideSplittingSettings(GameTestHelper helper) {
        var scalarBefore=IteConfig.effectiveSnapshot();boolean localScalar=scalarBefore==IteConfig.localSnapshot();
        var ratesBefore=IteSpawnConfig.effectiveSnapshot();var weightsBefore=IteSpawnConfig.effectiveWeights();
        boolean localSpawn=ratesBefore==IteSpawnConfig.localSnapshot()&&weightsBefore==IteSpawnConfig.localWeights();
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            var source=new HashMap<>(IteConfig.localSnapshot());
            source.put("reference_spawning",false);source.put("extreme_mob_spawn_limit_day",41);
            source.put("mob_max_spawn_count_day_minimum",173);source.put("local_hostile_limit",21);
            source.put("compressed_skeleton_expand_count",8);source.put("annihilation_skeleton_spawn_in_light",true);
            var pacing=Map.<String,Object>of("natural_local_hostile_limit",7,"full_moon_natural_local_hostile_limit",13,
                    "blood_moon_natural_local_hostile_limit",19,"deep_hostile_cap_multiplier",3.5D,
                    "hostile_spawn_interval_ticks",7,"deep_spawn_chance_percent",5D,"surface_spawn_chance_percent",23D,
                    "blood_moon_spawn_chance_multiplier",2D,"other_dimension_spawn_chance_multiplier",.5D);
            source.putAll(pacing);
            IteConfigSync.CODEC.encode(buffer,new IteConfigSync(source,Map.of("modern_ite:future_monster",.5D),Map.of("modern_ite:future_monster",72D)));
            var decoded=IteConfigSync.CODEC.decode(buffer);IteConfig.receiveServerSnapshot(decoded.values());
            pacing.forEach((key,value)->helper.assertTrue(value.equals(decoded.values().get(key))
                            &&value.equals(IteConfig.value(key)),"Crowd/pacing values retain schema types through synchronization: "+key));
            helper.assertTrue(IteConfig.integer("extreme_mob_spawn_limit_day")==41
                            &&IteConfig.integer("mob_max_spawn_count_day_minimum")==173&&IteConfig.integer("local_hostile_limit")==21
                            &&IteConfig.integer("compressed_skeleton_expand_count")==8&&IteConfig.bool("annihilation_skeleton_spawn_in_light"),
                    "All editable values and splitting controls are synchronized without a preset override");
            helper.assertTrue(!decoded.values().containsKey("reference_spawning")
                            &&decoded.values().get("extreme_mob_spawn_limit_day") instanceof Integer
                            &&decoded.values().get("annihilation_skeleton_spawn_in_light") instanceof Boolean
                            &&decoded.naturalSpawnWeights().get("modern_ite:future_monster")==72D,
                    "Decoding restores schema types, drops retired keys and preserves the complete weight payload");
            IteConfig.clearServerSnapshot();
            helper.assertTrue(IteConfig.effectiveSnapshot()==IteConfig.localSnapshot(),"Disconnect restores local editable defaults");
        } finally {
            buffer.release();IteConfig.clearServerSnapshot();
            if(!localScalar)IteConfig.receiveServerSnapshot(scalarBefore);
            if(!localSpawn)IteSpawnConfig.receiveServerSnapshot(ratesBefore,weightsBefore);
        }
        helper.assertTrue(IteConfig.effectiveSnapshot().equals(scalarBefore)&&IteSpawnConfig.effectiveSnapshot().equals(ratesBefore)
                        &&IteSpawnConfig.effectiveWeights().equals(weightsBefore),"Synchronization tests restore exact effective snapshots");
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void completeServerExportRetainsEditableValuesWithoutTheRetiredPreset(GameTestHelper helper) throws Exception {
        var before=IteConfig.localSnapshot();
        try {
            IteConfig.setting("mob_max_spawn_count_chunk_base").set(73);
            IteConfig.setting("extreme_mob_spawn_limit_day").set(113);IteConfig.refresh();
            var bundle=GameplayConfigExport.exportBundle(Files.createTempDirectory("ite-editable-config-export-"));
            var file=bundle.files().stream().filter(path->path.getFileName().toString().equals("modern_ite-common.toml")).findFirst().orElseThrow();
            var exported=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(file));
            helper.assertTrue(!exported.contains("GameMechanics.MobSpawning.reference_spawning")
                            &&((Number)exported.get(IteConfig.setting("mob_max_spawn_count_chunk_base").getPath())).intValue()==73
                            &&((Number)exported.get(IteConfig.setting("extreme_mob_spawn_limit_day").getPath())).intValue()==113,
                    "Server bundles retain effective editable values without the removed switch");
            helper.assertTrue(IteConfig.SPEC.isCorrect(exported),"Exported editable defaults pass the complete configuration specification");
        } finally {
            IteConfig.setting("mob_max_spawn_count_chunk_base").set(before.get("mob_max_spawn_count_chunk_base"));
            IteConfig.setting("extreme_mob_spawn_limit_day").set(before.get("extreme_mob_spawn_limit_day"));IteConfig.refresh();
        }
        helper.assertTrue(IteConfig.localSnapshot().equals(before),"Export testing restores the complete local snapshot without writing active config");
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void mainModProviderExposesAndSavesTheCompleteTypedSpawningCatalog(GameTestHelper helper) throws Exception {
        var policy=io.github.ningmeng123.mite.api.MiteExtensions.policy();
        var effectiveBefore=IteConfig.effectiveSnapshot();boolean hadRemote=effectiveBefore!=IteConfig.localSnapshot();
        var ratesBefore=IteSpawnConfig.effectiveSnapshot();var weightsBefore=IteSpawnConfig.effectiveWeights();
        boolean remoteSpawns=ratesBefore!=IteSpawnConfig.localSnapshot()||weightsBefore!=IteSpawnConfig.localWeights();
        IteConfig.clearServerSnapshot();
        var before=IteConfig.localSnapshot();var controlsBefore=policy.spawningControlValues();
        var level=helper.getLevel().getServer().overworld();
        boolean graceBefore=io.github.ningmeng123.modernite.world.ExtremeWorldState.graceEnded(level);
        try {
            var controls=policy.spawningControls();
            helper.assertTrue(!controls.isEmpty()&&controls.getFirst().key().equals("extreme_mob_spawn_limit_day"),
                    "The actual main-mod page starts with the global extreme monster date");
            var expected=new HashMap<String,IteConfig.Option>();
            var additional=java.util.Set.of("mob_spawner_speed_up_with_players","mob_spawner_extra_delay_reduction",
                    "giant_zombie_spawn_zombie_cooldown");
            for(var option:IteConfig.options())if(option.path().contains("MobSpawning")||additional.contains(option.key()))
                expected.put(option.key(),option);
            var actual=new java.util.HashSet<String>();
            for(var control:controls) {
                helper.assertTrue(actual.add(control.key())&&expected.containsKey(control.key()),
                        "Every host control is a unique implemented spawning field: "+control.key());
                var option=expected.get(control.key());
                helper.assertTrue(control.translationKey().equals("modern_ite.config."+control.key())
                                &&control.defaultValue().equals(option.defaultValue())
                                &&control.defaultValue().getClass()==option.defaultValue().getClass()
                                &&control.minimum()==option.minimum()&&control.maximum()==option.maximum(),
                        "The host page receives the typed schema default, translation and bounds: "+control.key());
                helper.assertTrue(controlsBefore.get(control.key()).equals(before.get(control.key()))
                                &&controlsBefore.get(control.key()).getClass()==option.defaultValue().getClass(),
                        "The host page snapshot matches the effective typed value: "+control.key());
            }
            helper.assertTrue(actual.equals(expected.keySet())&&actual.equals(controlsBefore.keySet())
                            &&!actual.contains("reference_spawning")&&!actual.contains("fishing_xp")&&!actual.contains("quartz_exp"),
                    "The catalog contains all spawning fields, no retired switch and no unrelated gameplay values");
            helper.assertFalse(policy.saveSpawningControls(null),"A missing host payload is rejected");
            var missing=new HashMap<>(controlsBefore);missing.remove("hostile_spawn_interval_ticks");
            helper.assertFalse(policy.saveSpawningControls(missing),"A payload missing one required field is rejected");
            var extra=new HashMap<>(controlsBefore);extra.put("fishing_xp",123);
            helper.assertFalse(policy.saveSpawningControls(extra),"The host editor cannot write unrelated gameplay values");
            var invalidValues=Map.<String,Object>of("hostile_spawn_interval_ticks",0,"mob_max_spawn_count_chunk_base",3.5D,
                    "deep_hostile_cap_multiplier",Double.NaN,"surface_spawn_chance_percent",101D,
                    "mob_spawner_speed_up_with_players","false");
            for(var invalid:invalidValues.entrySet()) {
                var values=new HashMap<>(controlsBefore);values.put(invalid.getKey(),invalid.getValue());
                helper.assertFalse(policy.saveSpawningControls(values),"Invalid host values are rejected before writing: "+invalid.getKey());
                helper.assertTrue(IteConfig.localSnapshot().equals(before),"Invalid host payloads leave the complete local snapshot intact");
            }
            helper.assertTrue(IteConfig.localSnapshot().equals(before),"Null, missing and unrelated fields cause no partial writes");
            int interval=IteConfig.integer("hostile_spawn_interval_ticks");interval=interval==72000?interval-1:interval+1;
            int cap=IteConfig.integer("mob_max_spawn_count_chunk_base");cap=cap==1000000?cap-1:cap+1;
            var edited=new HashMap<>(controlsBefore);edited.put("hostile_spawn_interval_ticks",interval);
            edited.put("mob_max_spawn_count_chunk_base",cap);
            helper.assertTrue(policy.saveSpawningControls(edited),"A complete valid payload is saved by the main-mod provider");
            helper.assertTrue(IteConfig.integer("hostile_spawn_interval_ticks")==interval
                            &&IteConfig.integer("mob_max_spawn_count_chunk_base")==cap
                            &&policy.spawningControlValues().equals(edited),"Saving immediately publishes the edited typed fields");
            var settings=policy.hostileSpawningSettings(io.github.ningmeng123.mite.config.MiteConfig.monsterSpawningSettings());
            helper.assertTrue(settings.value(io.github.ningmeng123.mite.rules.MonsterSpawningSettings.Option.HOSTILE_INTERVAL_TICKS)==interval,
                    "The actual scheduler reads the interval saved through the main-mod page");
            io.github.ningmeng123.modernite.world.ExtremeWorldState.restoreGraceEnded(level,true);
            int expectedCap=io.github.ningmeng123.modernite.ExtremeCalendar.configuredCap(
                    io.github.ningmeng123.modernite.ExtremeRules.currentDay(level),256,
                    io.github.ningmeng123.modernite.ExtremeRules.blood(level),false,
                    IteConfig.number("blood_moon_max_hostile_fraction"),IteConfig.number("mob_max_spawn_count_increase_per_day"),
                    cap,IteConfig.integer("mob_max_spawn_count_day_minimum"),IteConfig.integer("mob_max_spawn_count_day_maximum"),
                    IteConfig.number("deep_hostile_cap_multiplier"));
            helper.assertTrue(policy.monsterCap(level,false,256,-1)==expectedCap,
                    "The actual hostile budget reads the cap saved through the main-mod page");
            var file=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("modern_ite-common.toml");
            var parsed=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(file));
            helper.assertTrue(((Number)parsed.get(IteConfig.setting("hostile_spawn_interval_ticks").getPath())).intValue()==interval
                            &&((Number)parsed.get(IteConfig.setting("mob_max_spawn_count_chunk_base").getPath())).intValue()==cap,
                    "The host provider saves both edits to the isolated ITE configuration file");
        } finally {
            try {
                helper.assertTrue(policy.saveSpawningControls(controlsBefore),"Restoration saves the original values through the same provider");
            } finally {
                io.github.ningmeng123.modernite.world.ExtremeWorldState.restoreGraceEnded(level,graceBefore);
                if(hadRemote)IteConfig.receiveServerSnapshot(effectiveBefore);else IteConfig.clearServerSnapshot();
                if(remoteSpawns)IteSpawnConfig.receiveServerSnapshot(ratesBefore,weightsBefore);
            }
        }
        helper.assertTrue(IteConfig.localSnapshot().equals(before),"The provider test restores and saves the complete original local snapshot");
        helper.succeed();
    }
}
