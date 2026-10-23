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

/** Reference-preset compatibility across schema, synchronization and complete exports. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IteReferenceConfigGameTests {
    private IteReferenceConfigGameTests() {}

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void oldConfigurationsSelectReferenceWithoutOverwritingCustomValues(GameTestHelper helper) {
        var option=IteConfig.options().stream().filter(value->value.key().equals("reference_spawning")).findFirst().orElseThrow();
        helper.assertTrue(option.path().equals(List.of("GameMechanics","MobSpawning","reference_spawning"))
                        &&Boolean.TRUE.equals(option.defaultValue())&&Boolean.TRUE.equals(IteConfig.setting("reference_spawning").getDefault()),
                "Generated configurations select the reference preset at the public MobSpawning path");
        var old=Map.<String,Object>of("mob_max_spawn_count_chunk_base",37,"mob_max_spawn_count_day_minimum",123,
                "blood_moon_max_hostile_fraction",5D,"local_hostile_limit",19,"compressed_skeleton_expand_count",9);
        var migrated=IteConfig.validated(old);
        helper.assertTrue(Boolean.TRUE.equals(migrated.get("reference_spawning")),"A missing legacy preset selects reference spawning");
        old.forEach((key,value)->helper.assertTrue(value.equals(migrated.get(key)),"Existing custom and splitting values remain exact: "+key));
        helper.assertTrue(Boolean.TRUE.equals(IteConfig.validated(Map.of("reference_spawning","false")).get("reference_spawning"))
                        &&Boolean.FALSE.equals(IteConfig.validated(Map.of("reference_spawning",false)).get("reference_spawning")),
                "Only a typed false value opts into the existing custom behavior");
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void bothPresetValuesRoundTripAlongsideDormantCustomAndSplittingSettings(GameTestHelper helper) {
        var scalarBefore=IteConfig.effectiveSnapshot();
        boolean localScalar=scalarBefore==IteConfig.localSnapshot();
        var ratesBefore=IteSpawnConfig.effectiveSnapshot();
        var weightsBefore=IteSpawnConfig.effectiveWeights();
        boolean localSpawn=ratesBefore==IteSpawnConfig.localSnapshot()&&weightsBefore==IteSpawnConfig.localWeights();
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            for(boolean reference:new boolean[]{true,false}) {
                var source=new HashMap<>(IteConfig.localSnapshot());
                source.put("reference_spawning",reference);
                source.put("mob_max_spawn_count_day_minimum",173);
                source.put("local_hostile_limit",21);
                source.put("compressed_skeleton_expand_count",8);
                buffer.clear();
                IteConfigSync.CODEC.encode(buffer,new IteConfigSync(source,Map.of("modern_ite:future_monster",.5D),
                        Map.of("modern_ite:future_monster",72D)));
                var decoded=IteConfigSync.CODEC.decode(buffer);
                IteConfig.receiveServerSnapshot(decoded.values());
                helper.assertTrue(IteConfig.bool("reference_spawning")==reference
                                &&IteConfig.integer("mob_max_spawn_count_day_minimum")==173
                                &&IteConfig.integer("local_hostile_limit")==21
                                &&IteConfig.integer("compressed_skeleton_expand_count")==8,
                        "Both modes synchronize their dormant legacy values and live splitting controls");
                helper.assertTrue(decoded.values().get("reference_spawning") instanceof Boolean
                                &&decoded.naturalSpawnWeights().get("modern_ite:future_monster")==72D,
                        "The preset retains its boolean type while the full weight payload remains compatible");
            }
            IteConfig.clearServerSnapshot();
            helper.assertTrue(IteConfig.effectiveSnapshot()==IteConfig.localSnapshot(),"Disconnect restores the local preset together with local values");
        } finally {
            buffer.release();
            IteConfig.clearServerSnapshot();
            if(!localScalar)IteConfig.receiveServerSnapshot(scalarBefore);
            if(!localSpawn)IteSpawnConfig.receiveServerSnapshot(ratesBefore,weightsBefore);
        }
        helper.assertTrue(IteConfig.effectiveSnapshot().equals(scalarBefore)
                        &&IteSpawnConfig.effectiveSnapshot().equals(ratesBefore)&&IteSpawnConfig.effectiveWeights().equals(weightsBefore),
                "The synchronous payload test restores the exact effective snapshots before another test can start");
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_reference_config")
    public static void completeServerExportRetainsEachPresetAndItsDormantCustomCap(GameTestHelper helper) throws Exception {
        var localBefore=IteConfig.localSnapshot();
        Object referenceBefore=IteConfig.setting("reference_spawning").get();
        Object capBefore=IteConfig.setting("mob_max_spawn_count_chunk_base").get();
        try {
            for(boolean reference:new boolean[]{true,false}) {
                IteConfig.setting("reference_spawning").set(reference);
                IteConfig.setting("mob_max_spawn_count_chunk_base").set(73);
                IteConfig.refresh();
                var bundle=GameplayConfigExport.exportBundle(Files.createTempDirectory("ite-reference-config-export-"));
                var file=bundle.files().stream().filter(path->path.getFileName().toString().equals("modern_ite-common.toml")).findFirst().orElseThrow();
                var exported=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(file));
                helper.assertTrue(Boolean.valueOf(reference).equals(exported.get(IteConfig.setting("reference_spawning").getPath()))
                                &&((Number)exported.get(IteConfig.setting("mob_max_spawn_count_chunk_base").getPath())).intValue()==73,
                        "The complete server bundle preserves the selected mode and inactive custom cap without rewriting either");
                helper.assertTrue(IteConfig.SPEC.isCorrect(exported),"The exported reference preset passes the complete existing configuration specification");
            }
        } finally {
            IteConfig.setting("reference_spawning").set(referenceBefore);
            IteConfig.setting("mob_max_spawn_count_chunk_base").set(capBefore);
            IteConfig.refresh();
        }
        helper.assertTrue(IteConfig.localSnapshot().equals(localBefore),
                "Export testing restores the complete local scalar snapshot synchronously and never writes the active config file");
        helper.succeed();
    }
}
