package io.github.ningmeng123.modernite.config;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Immutable, registry-identity-based natural spawn settings. @author NingMeng123 */
public final class IteSpawnConfig {
    public static ModConfigSpec.ConfigValue<List<? extends String>> NATURAL_SPAWN_RATES;
    public static ModConfigSpec.ConfigValue<List<? extends String>> NATURAL_SPAWN_WEIGHTS;
    private record Snapshot(Map<String, Double> rates, Map<String, Double> weights) {}
    private static volatile Snapshot local = new Snapshot(Map.of(), Map.of());
    private static volatile Snapshot remote;

    private IteSpawnConfig() {}

    static void define(ModConfigSpec.Builder builder) {
        NATURAL_SPAWN_RATES = builder.translation("modern_ite.config.natural_spawn_rates")
                .comment("Natural spawn rate multipliers for registered ITE entity IDs, written as modern_ite:entity=multiplier (0..100). Omitted IDs preserve their existing rate; 0 disables natural spawning only. This does not create natural entries or bypass day, dimension, light, or population restrictions. Unknown ITE IDs are retained for future or temporarily removed entities.")
                .defineListAllowEmpty(List.of("GameMechanics", "MobSpawning", "natural_spawn_rates"),
                        () -> BundledGameplayDefaults.list("GameMechanics.MobSpawning.natural_spawn_rates"), () -> "modern_ite:zombie_chaos=1.0", IteSpawnConfig::validEntry);
        NATURAL_SPAWN_WEIGHTS = builder.translation("modern_ite.config.natural_spawn_weights")
                .comment("Absolute natural candidate weights: modern_ite:entity=weight (finite decimals allowed, 0..1000000). Omitted IDs retain each pool's native weight. The selected weight is multiplied by natural_spawn_rates. 0 removes the whole candidate family, including its conditional replacements. Does not create natural entries or bypass legacy zero spawn_frequency, dates, dimensions or population limits. Unknown ITE IDs are retained.")
                .defineListAllowEmpty(List.of("GameMechanics", "MobSpawning", "natural_spawn_weights"),
                        () -> BundledGameplayDefaults.list("GameMechanics.MobSpawning.natural_spawn_weights"), () -> "modern_ite:zombie_chaos=50", IteSpawnConfig::validWeightEntry);
        local = new Snapshot(defaultRates(), defaultWeights());
    }

    public static boolean owns(String id) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        return key != null && ModernIte.MOD_ID.equals(key.getNamespace());
    }

    private static boolean validEntry(Object input) {
        return validEntry(input, false);
    }

    private static boolean validWeightEntry(Object input) { return validEntry(input, true); }

    private static boolean validEntry(Object input, boolean weight) {
        if (!(input instanceof String text)) return false;
        int separator = text.lastIndexOf('=');
        if (separator < 1 || !owns(text.substring(0, separator).trim())) return false;
        try {
            double value = Double.parseDouble(text.substring(separator + 1).trim());
            return validValue(value, weight);
        } catch (NumberFormatException invalid) { return false; }
    }

    public static Map<String, Double> validated(Map<String, ?> input) {
        return validated(input, false);
    }

    public static Map<String, Double> validatedWeights(Map<String, ?> input) { return validated(input, true); }

    private static boolean validValue(double value, boolean weight) {
        return Double.isFinite(value) && value >= 0D && value <= (weight ? 1000000D : 100D);
    }

    private static Map<String, Double> validated(Map<String, ?> input, boolean weight) {
        var result = new LinkedHashMap<String, Double>();
        if (input != null) input.forEach((id, raw) -> {
            if (owns(id) && raw instanceof Number number) {
                double value = number.doubleValue();
                if (validValue(value, weight)) result.put(id, value);
            }
        });
        return Map.copyOf(result);
    }

    static void refresh() {
        local = new Snapshot(read(NATURAL_SPAWN_RATES, false), read(NATURAL_SPAWN_WEIGHTS, true));
    }

    private static Map<String, Double> read(ModConfigSpec.ConfigValue<List<? extends String>> setting, boolean weight) {
        var values = new LinkedHashMap<String, Double>();
        List<? extends String> entries = IteConfig.SPEC.isLoaded()
                ? setting.get() : setting.getDefault();
        for (String entry : entries) if (validEntry(entry, weight)) {
            int separator = entry.lastIndexOf('=');
            values.put(entry.substring(0, separator).trim(), Double.parseDouble(entry.substring(separator + 1).trim()));
        }
        return Map.copyOf(values);
    }

    private static Snapshot effective() { var server = remote; return server == null ? local : server; }
    private static Map<String, Double> defaults(ModConfigSpec.ConfigValue<List<? extends String>> setting) {
        var result = new LinkedHashMap<String, Double>();
        for (String entry : setting.getDefault()) {
            int split = entry.lastIndexOf('=');
            result.put(entry.substring(0, split), Double.parseDouble(entry.substring(split + 1)));
        }
        return Map.copyOf(result);
    }
    public static Map<String, Double> defaultRates() { return defaults(NATURAL_SPAWN_RATES); }
    public static Map<String, Double> defaultWeights() { return defaults(NATURAL_SPAWN_WEIGHTS); }
    public static Map<String, Double> localSnapshot() { return local.rates(); }
    public static Map<String, Double> localWeights() { return local.weights(); }
    public static Map<String, Double> effectiveSnapshot() { return effective().rates(); }
    public static Map<String, Double> effectiveWeights() { return effective().weights(); }
    public static double multiplier(String id) { return effectiveSnapshot().getOrDefault(id, 1D); }
    public static double weight(String id, double fallback) { return effectiveWeights().getOrDefault(id, fallback); }
    public static void receiveServerSnapshot(Map<String, ?> values) { receiveServerSnapshot(values, Map.of()); }
    public static void receiveServerSnapshot(Map<String, ?> values, Map<String, ?> weights) {
        remote = new Snapshot(validated(values), validatedWeights(weights));
    }
    public static void clearServerSnapshot() { remote = null; }

    public static void save(Map<String, Double> values) {
        save(values, localWeights());
    }

    public static void save(Map<String, Double> values, Map<String, Double> weights) {
        Map<String, Double> validated = validated(values);
        if (!validated.equals(values)) throw new IllegalArgumentException("Invalid ITE natural spawn rates");
        Map<String, Double> validatedWeights = validatedWeights(weights);
        if (!validatedWeights.equals(weights)) throw new IllegalArgumentException("Invalid ITE natural spawn weights");
        NATURAL_SPAWN_RATES.set(entries(validated));
        NATURAL_SPAWN_WEIGHTS.set(entries(validatedWeights));
        IteConfig.SPEC.save();
        refresh();
        IteConfigSync.broadcast();
    }

    private static List<String> entries(Map<String, Double> values) {
        var entries = new ArrayList<String>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.add(entry.getKey() + "=" + entry.getValue()));
        return entries;
    }
}
