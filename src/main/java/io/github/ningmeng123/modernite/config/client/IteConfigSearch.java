package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.mite.client.config.ConfigSearchIndex;
import io.github.ningmeng123.mite.client.config.MonsterSpawnSettingsScreen;
import io.github.ningmeng123.mite.client.config.SingleConfigValueScreen;
import io.github.ningmeng123.mite.config.MonsterSpawnCatalog;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

/** Contributes native ITE settings and per-entity spawn controls to MITE's homepage search. @author NingMeng123 */
final class IteConfigSearch {
    private IteConfigSearch() {}

    static List<ConfigSearchIndex.Entry> entries(Screen parent) {
        var entries = new ArrayList<>(ConfigSearchIndex.configurationEntries(ModernIte.MOD_ID, true, (config, path) -> {
            String key = path.getLast();
            if (key.equals("natural_spawn_rates") || key.equals("natural_spawn_weights")) return spawns(parent, "");
            return IteConfigClient.editable()
                    ? SingleConfigValueScreen.create(parent, config, path, Component.translatable("modern_ite.config." + key))
                    : new IteConfigValuesScreen(parent, key);
        }));
        entries.removeIf(entry -> entry.id().endsWith(".stepped_mob_damage_progress_increase_day"));

        var minecraft = Minecraft.getInstance();
        var registry = minecraft.level == null ? RegistryAccess.EMPTY : minecraft.level.registryAccess();
        var entities = new LinkedHashMap<String, Component>();
        for (var entity : MonsterSpawnCatalog.entries(ModernIte.MOD_ID, registry)) entities.put(entity.id(), entity.name());
        IteSpawnConfig.effectiveSnapshot().keySet().stream().filter(IteSpawnConfig::owns)
                .forEach(id -> entities.putIfAbsent(id, Component.literal(id)));
        IteSpawnConfig.effectiveWeights().keySet().stream().filter(IteSpawnConfig::owns)
                .forEach(id -> entities.putIfAbsent(id, Component.literal(id)));
        var category = Component.translatable("modern_ite.config.monster_spawns");
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

    private static MonsterSpawnSettingsScreen spawns(Screen parent, String query) {
        var screen = new MonsterSpawnSettingsScreen(parent, ModernIte.MOD_ID,
                IteSpawnConfig::effectiveSnapshot, IteSpawnConfig::effectiveWeights, (rates, weights) -> {
                    if (IteConfigClient.editable()) IteSpawnConfig.save(rates, weights);
                }, IteSettingsScreen::nativeSpawnWeights, IteSpawnConfig::defaultRates, IteSpawnConfig::defaultWeights);
        screen.setSearchQuery(query);
        return screen;
    }
}
