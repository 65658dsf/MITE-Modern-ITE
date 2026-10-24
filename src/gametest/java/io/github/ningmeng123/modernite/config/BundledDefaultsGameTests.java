package io.github.ningmeng123.modernite.config;

import io.github.ningmeng123.modernite.config.IteConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The complete user preset becomes schema defaults without touching live values. @author NingMeng123 */
@GameTestHolder("modern_ite_defaults")
@PrefixGameTestTemplate(false)
public final class BundledDefaultsGameTests {
    private BundledDefaultsGameTests() {}
    @GameTest(template="gear_empty")
    public static void everyExportedSettingIsTheActualFactoryDefault(GameTestHelper helper) throws Exception {
        try (var stream = BundledDefaultsGameTests.class.getResourceAsStream("/modern_ite/gameplay-defaults.toml");
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            compare(helper, new TomlParser().parse(reader), List.of());
        }
        helper.assertValueEqual(IteConfig.localSnapshot(), IteConfig.defaults(), "Loaded ITE scalar snapshot matches the factory preset, including shared day three");
        helper.assertValueEqual(IteSpawnConfig.localSnapshot(), IteSpawnConfig.defaultRates(), "Loaded rate snapshot matches the unchanged exported empty list");
        helper.assertValueEqual(IteSpawnConfig.localWeights(), IteSpawnConfig.defaultWeights(), "Loaded weight snapshot matches the exported integral weights");
        helper.succeed();
    }
    private static void compare(GameTestHelper helper, UnmodifiableConfig expected, List<String> parent) {
        for (var entry : expected.entrySet()) {
            var path = new ArrayList<>(parent); path.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig child) compare(helper, child, path);
            else {
                Object raw = IteConfig.SPEC.getValues().get(path);
                helper.assertTrue(raw instanceof ModConfigSpec.ConfigValue<?>, "Exported setting exists: " + path);
                Object actual = ((ModConfigSpec.ConfigValue<?>)raw).getDefault(), wanted = entry.getValue();
                boolean equal = actual instanceof Number a && wanted instanceof Number b
                        ? Double.compare(a.doubleValue(), b.doubleValue()) == 0 : java.util.Objects.equals(actual, wanted);
                helper.assertTrue(equal, "Factory default " + path + ": " + actual + " vs " + wanted);
                Object live = ((ModConfigSpec.ConfigValue<?>)raw).get();
                boolean loadedEqual = live instanceof Number a && wanted instanceof Number b
                        ? Double.compare(a.doubleValue(), b.doubleValue()) == 0 : java.util.Objects.equals(live, wanted);
                helper.assertTrue(loadedEqual, "Fresh config loads bundled value " + path + ": " + live + " vs " + wanted);
            }
        }
    }
}
