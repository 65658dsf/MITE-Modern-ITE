package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.mite.client.config.ConfigSearchIndex;
import io.github.ningmeng123.mite.client.config.MonsterSpawnSettingsScreen;
import io.github.ningmeng123.mite.client.config.MonsterSpawningRulesScreen;
import io.github.ningmeng123.mite.client.config.SingleConfigValueScreen;
import io.github.ningmeng123.mite.config.MonsterSpawnCatalog;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.RabidDogContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** Contributes native ITE settings and per-entity spawn controls to MITE's homepage search. @author NingMeng123 */
final class IteConfigSearch {
    private IteConfigSearch() {}

    static List<ConfigSearchIndex.Entry> entries(Screen parent) {
        var sharedSpawning = IteConfigClient.sharedSpawningKeys();
        var options = IteConfig.options().stream().collect(Collectors.toMap(IteConfig.Option::key, Function.identity()));
        var definitions = ConfigSearchIndex.configurationEntries(ModernIte.MOD_ID, true, (config, path) -> {
            String key = path.getLast();
            if (sharedSpawning.contains(key)) return new MonsterSpawningRulesScreen(parent, key);
            if (IteConfigClient.editable()) {
                var option = options.get(key);
                if (option != null) return new IteConfigOptionScreen(parent, option);
                return SingleConfigValueScreen.create(parent, config, path, Component.translatable("modern_ite.config." + key));
            }
            return spawnList(key) ? spawns(parent, "") : new IteConfigValuesScreen(parent, key);
        });
        var entries = new ArrayList<ConfigSearchIndex.Entry>();
        for (var entry : definitions) {
            String id = entry.id();
            String key = id.substring(Math.max(id.lastIndexOf('.'), id.lastIndexOf(':')) + 1);
            if (key.equals("stepped_mob_damage_progress_increase_day")) continue;
            var option = options.get(key);
            Component category = entry.category();
            if (spawnList(key)) {
                category = breadcrumb("creatures", Component.translatable("modern_ite.config.monster_spawns"), category);
            } else if (sharedSpawning.contains(key)) {
                category = breadcrumb("creatures", Component.translatable("mite.config.spawning_rules.button"), category);
            } else if (option != null) {
                var group = IteConfigValuesScreen.Group.forOption(option);
                if (group != null) category = breadcrumb(group.category(), group.title(), category);
            }
            entries.add(new ConfigSearchIndex.Entry(entry.id(), entry.label(), category, entry.description(),
                    entry.available(), entry.open()));
        }

        var minecraft = Minecraft.getInstance();
        var registry = minecraft.level == null ? RegistryAccess.EMPTY : minecraft.level.registryAccess();
        var entities = new LinkedHashMap<String, Component>();
        for (var entity : MonsterSpawnCatalog.entries(ModernIte.MOD_ID, registry)) entities.put(entity.id(), entity.name());
        IteSpawnConfig.effectiveSnapshot().keySet().stream().filter(IteSpawnConfig::owns)
                .forEach(id -> entities.putIfAbsent(id, Component.literal(id)));
        IteSpawnConfig.effectiveWeights().keySet().stream().filter(IteSpawnConfig::owns)
                .forEach(id -> entities.putIfAbsent(id, Component.literal(id)));
        var spawnWeights = Component.translatable("modern_ite.config.monster_spawns");
        var category = breadcrumb("creatures", spawnWeights, spawnWeights);
        entities.forEach((id, name) -> {
            for (String setting : List.of("natural_spawn_rates", "natural_spawn_weights")) {
                String translation = "modern_ite.config." + setting;
                entries.add(new ConfigSearchIndex.Entry(ModernIte.MOD_ID + "." + setting + "." + id,
                        name.copy().append(" · ").append(Component.translatable(translation)), category,
                        Component.translatable(translation + ".tooltip"), true, () -> spawns(parent, id)));
            }
        });
        return List.copyOf(entries);
    }

    private static Component breadcrumb(String category, Component section, Component previous) {
        String key = "mite.config.category." + category;
        if (!Language.getInstance().has(key)) return previous;
        var result = Component.translatable(key).append(" / ").append(section);
        if (!section.equals(previous)) result.append(" / ").append(previous);
        return result;
    }

    private static boolean spawnList(String key) {
        return key.equals("natural_spawn_rates") || key.equals("natural_spawn_weights");
    }

    static MonsterSpawnSettingsScreen spawns(Screen parent, String query) {
        var screen = new MonsterSpawnSettingsScreen(parent, ModernIte.MOD_ID,
                IteSpawnConfig::effectiveSnapshot, IteSpawnConfig::effectiveWeights, (rates, weights) -> {
                    if (IteConfigClient.editable()) IteSpawnConfig.save(rates, weights);
                }, IteConfigSearch::nativeSpawnWeights, IteSpawnConfig::defaultRates, IteSpawnConfig::defaultWeights);
        screen.setSearchQuery(query);
        return screen;
    }

    private static List<Integer> nativeSpawnWeights(EntityType<?> type) {
        if (type == RabidDogContent.RABID_DOG.get()) return List.of(5);
        for (var kind : ExtremeKind.values())
            if (ExtremeMobs.type(kind) == type) return List.of(ExtremeMobs.spawnWeight(kind));
        return List.of();
    }
}
