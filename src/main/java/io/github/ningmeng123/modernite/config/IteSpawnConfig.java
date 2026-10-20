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
    private static volatile Map<String, Double> local = Map.of();
    private static volatile Map<String, Double> remote;

    private IteSpawnConfig() {}

    static void define(ModConfigSpec.Builder builder) {
        NATURAL_SPAWN_RATES = builder.translation("modern_ite.config.natural_spawn_rates")
                .comment("Natural spawn rate multipliers for registered ITE entity IDs, written as modern_ite:entity=multiplier (0..100). Omitted IDs preserve their existing rate; 0 disables natural spawning only. This does not create natural entries or bypass day, dimension, light, or population restrictions. Unknown ITE IDs are retained for future or temporarily removed entities.")
                .defineListAllowEmpty(List.of("GameMechanics", "MobSpawning", "natural_spawn_rates"),
                        List::of, () -> "modern_ite:zombie_chaos=1.0", IteSpawnConfig::validEntry);
    }

    public static boolean owns(String id) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        return key != null && ModernIte.MOD_ID.equals(key.getNamespace());
    }

    private static boolean validEntry(Object input) {
        if (!(input instanceof String text)) return false;
        int separator = text.lastIndexOf('=');
        if (separator < 1 || !owns(text.substring(0, separator).trim())) return false;
        try {
            double value = Double.parseDouble(text.substring(separator + 1).trim());
            return Double.isFinite(value) && value >= 0 && value <= 100;
        } catch (NumberFormatException invalid) { return false; }
    }

    public static Map<String, Double> validated(Map<String, ?> input) {
        var result = new LinkedHashMap<String, Double>();
        if (input != null) input.forEach((id, raw) -> {
            if (owns(id) && raw instanceof Number number) {
                double value = number.doubleValue();
                if (Double.isFinite(value) && value >= 0 && value <= 100) result.put(id, value);
            }
        });
        return Map.copyOf(result);
    }

    static void refresh() {
        var values = new LinkedHashMap<String, Double>();
        List<? extends String> entries = IteConfig.SPEC.isLoaded()
                ? NATURAL_SPAWN_RATES.get() : NATURAL_SPAWN_RATES.getDefault();
        for (String entry : entries) if (validEntry(entry)) {
            int separator = entry.lastIndexOf('=');
            values.put(entry.substring(0, separator).trim(), Double.parseDouble(entry.substring(separator + 1).trim()));
        }
        local = Map.copyOf(values);
    }

    public static Map<String, Double> localSnapshot() { return local; }
    public static Map<String, Double> effectiveSnapshot() { var server = remote; return server == null ? local : server; }
    public static double multiplier(String id) { return effectiveSnapshot().getOrDefault(id, 1D); }
    public static void receiveServerSnapshot(Map<String, ?> values) { remote = validated(values); }
    public static void clearServerSnapshot() { remote = null; }

    public static void save(Map<String, Double> values) {
        Map<String, Double> validated = validated(values);
        if (!validated.equals(values)) throw new IllegalArgumentException("Invalid ITE natural spawn rates");
        var entries = new ArrayList<String>();
        validated.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.add(entry.getKey() + "=" + entry.getValue()));
        NATURAL_SPAWN_RATES.set(entries);
        IteConfig.SPEC.save();
        refresh();
        IteConfigSync.broadcast();
    }
}
