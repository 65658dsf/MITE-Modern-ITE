package io.github.ningmeng123.modernite.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Packaged factory preset; never reads or overwrites the player's live configuration. @author NingMeng123 */
final class BundledGameplayDefaults {
    private static final Config VALUES = load();
    private BundledGameplayDefaults() {}
    private static Config load() {
        try (var stream = BundledGameplayDefaults.class.getResourceAsStream("/modern_ite/gameplay-defaults.toml")) {
            if (stream == null) throw new IllegalStateException("Missing bundled gameplay defaults");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new TomlParser().parse(reader);
            }
        } catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
    static double number(String path, double fallback) {
        Object value = VALUES.get(path);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
    static String text(String path, String fallback) {
        Object value = VALUES.get(path);
        return value instanceof String text ? text : fallback;
    }
    static List<String> list(String path) {
        List<String> values = VALUES.get(path);
        return values == null ? List.of() : List.copyOf(values);
    }
    static Object value(String path, Object fallback) {
        Object value = VALUES.get(path);
        if (value == null) return fallback;
        if (fallback instanceof Integer && value instanceof Number number) return number.intValue();
        if (fallback instanceof Double && value instanceof Number number) return number.doubleValue();
        return value;
    }
}
