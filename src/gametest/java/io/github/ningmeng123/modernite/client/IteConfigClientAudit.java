package io.github.ningmeng123.modernite.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.client.config.ConfigSearchIndex;
import io.github.ningmeng123.mite.client.config.MiteSettingsScreen;
import io.github.ningmeng123.mite.client.config.MonsterSpawnSettingsScreen;
import io.github.ningmeng123.mite.client.config.OreGenerationSettingsScreen;
import io.github.ningmeng123.mite.client.config.SingleConfigValueScreen;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.config.client.IteConfigValuesScreen;
import io.github.ningmeng123.modernite.config.client.IteConfigOptionScreen;
import io.github.ningmeng123.mite.client.config.MonsterSpawningRulesScreen;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.joml.Matrix4f;

/** Explicit title-only GUI audit in the isolated config-audit launch. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class IteConfigClientAudit {
    private static final String SAMPLE = "conqueror_damage_boost_per_lvl";
    private static boolean finished, languageReloadRequested, started;
    private static int ticks, saveTicks, auditStep, enchantmentIndex;
    private static Screen title;
    private static JsonObject report;
    private static Map<String, Object> originalValues;
    private static List<? extends String> originalSpawnRates, originalSpawnWeights;
    private static Path configFile;
    private static byte[] originalFile;
    private static String originalLanguage;
    private static final Map<IteConfig.Option, Object> savedEnchantments = new LinkedHashMap<>();
    private static final Map<ModConfigSpec.ConfigValue<?>, Object> originalBaseValues = new LinkedHashMap<>();
    private static MiteConfig.Snapshot originalBaseSnapshot, originalBaseEffective;
    private static Path baseConfigFile;
    private static byte[] originalBaseFile;

    private IteConfigClientAudit() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("modern_ite.configAudit") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null || mc.getConnection() != null || mc.getSingleplayerServer() != null) {
            fail("Refused to audit a client attached to any world or server", null);
            finish(mc, false);
            return;
        }
        try {
            if (++ticks > 2400) throw new IllegalStateException("Timed out waiting for the isolated config audit");
            if (mc.getOverlay() != null) return;
            if (started) {
                if (++saveTicks < 12) return;
                saveTicks = 0;
                advanceAudit(mc);
                return;
            }
            if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
                onboarding.onClose();
                return;
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            if (!languageReloadRequested && !mc.getLanguageManager().getSelected().equals("zh_cn")) {
                originalLanguage = mc.getLanguageManager().getSelected();
                languageReloadRequested = true;
                mc.getLanguageManager().setSelected("zh_cn");
                mc.options.languageCode = "zh_cn";
                mc.reloadResourcePacks();
                return;
            }
            started = true;
            report = initialReport();
            originalValues = IteConfig.localSnapshot();
            originalSpawnRates = List.copyOf(IteSpawnConfig.NATURAL_SPAWN_RATES.get());
            originalSpawnWeights = List.copyOf(IteSpawnConfig.NATURAL_SPAWN_WEIGHTS.get());
            configFile = FMLPaths.CONFIGDIR.get().resolve("modern_ite-common.toml").toAbsolutePath().normalize();
            check(configFile.startsWith(mc.gameDirectory.toPath().toAbsolutePath().normalize()),
                    "Config audit may only write its isolated game directory");
            originalFile = Files.exists(configFile) ? Files.readAllBytes(configFile) : null;
            check(IteConfig.SPEC.isLoaded(), "The actual COMMON config must be loaded");
            check(MiteConfig.SPEC.isLoaded(), "The actual MITE COMMON config must be loaded");
            originalBaseSnapshot = MiteConfig.localSnapshot();
            originalBaseEffective = MiteConfig.effectiveSnapshot();
            baseConfigFile = FMLPaths.CONFIGDIR.get().resolve("mite-common.toml").toAbsolutePath().normalize();
            check(baseConfigFile.startsWith(mc.gameDirectory.toPath().toAbsolutePath().normalize()),
                    "MITE config audit may only write its isolated game directory");
            originalBaseFile = Files.exists(baseConfigFile) ? Files.readAllBytes(baseConfigFile) : null;
            collectBaseValues(MiteConfig.SPEC.getValues());
            title = mc.screen;
            var ite = ModList.get().getModContainerById(ModernIte.MOD_ID).orElseThrow();
            check(IConfigScreenFactory.getForMod(ite.getModInfo()).isEmpty(), "ITE has no separate native config factory");
            report.addProperty("ite_uses_only_the_mite_config_factory", true);
            auditScalarTypes();
            advanceAudit(mc);
        } catch (Throwable failure) {
            fail("Config GUI audit", failure);
            finish(mc, true);
        }
    }

    private static void advanceAudit(Minecraft mc) throws Exception {
        // Allow the file watcher to process each completed edit before the next group writes the same TOML.
        checkSavedEnchantments();
        switch (auditStep++) {
            case 0 -> auditSurvivalSettings(mc, configHome(mc), 320, 240);
            case 1 -> auditSurvivalSettings(mc, configHome(mc), 854, 480);
            case 2 -> auditAllScalarDrafts(mc);
            case 3 -> auditNextEnchantment(mc);
            case 4 -> auditRedDiamondExperience(mc, configHome(mc));
            case 5 -> auditBaseSpawningEntry(mc, 320, 240);
            case 6 -> auditCreatures(mc, 320, 240);
            case 7 -> auditCombatSettings(mc, mc.screen, 320, 240);
            case 8 -> auditBaseSpawningEntry(mc, 854, 480);
            case 9 -> auditCreatures(mc, 854, 480);
            case 10 -> auditCombatSettings(mc, mc.screen, 854, 480);
            case 11 -> auditOres(mc, title);
            default -> {
                report.addProperty("native_edit_saved_to_toml", true);
                report.addProperty("native_edit_published_to_gameplay", true);
                report.addProperty("minimum_and_large_widget_bounds_verified", true);
                checkOreSave();
                finish(mc, true);
            }
        }
    }

    private static Screen configHome(Minecraft mc) {
        var mite = ModList.get().getModContainerById("mite").orElseThrow();
        Screen main = IConfigScreenFactory.getForMod(mite.getModInfo()).orElseThrow().createScreen(mite, title);
        check(main instanceof MiteSettingsScreen, "The original MITE factory must return its actual settings screen");
        mc.setScreen(main);
        return main;
    }

    private static void auditCreatures(Minecraft mc, int width, int height) throws Exception {
        Screen main = configHome(mc);
        capture(mc, main, width, height, "mite-settings", -1, -1);
        bounds(main);
        if (width == 320) {
            auditSpawningRulesSearch(mc, main);
            auditSpawnOverrideSearch(mc, main);
        }
        press(main, "mite.config.category.creatures");
        Screen home = mc.screen;
        capture(mc, home, width, height, "mite-creature-settings", -1, -1);
        bounds(home);
        var legacyRates = new LinkedHashMap<>(IteSpawnConfig.localSnapshot());
        legacyRates.put("modern_ite:zombie_chaos",.25D);
        IteSpawnConfig.save(legacyRates,IteSpawnConfig.localWeights());
        press(home, "modern_ite.config.monster_spawns");
        check(mc.screen instanceof MonsterSpawnSettingsScreen, "ITE's spawn button must open the shared registry browser");
        Screen spawns = mc.screen;
        capture(mc, spawns, width, height, "ite-natural-spawns", -1, -1);
        bounds(spawns);
        check(widgets(spawns).stream().noneMatch(widget -> widget.getMessage().equals(Component.translatable("mite.config.monster_spawn_rate_tab"))
                        || widget.getMessage().equals(Component.translatable("mite.config.monster_spawn_weight_tab"))),
                "The actual ITE browser must expose a single weight editor without multiplier tabs");
        var entriesField = MonsterSpawnSettingsScreen.class.getDeclaredField("entries");
        entriesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var spawnEntries = (List<io.github.ningmeng123.mite.config.MonsterSpawnCatalog.Entry>)entriesField.get(spawns);
        for(var kind:ExtremeKind.values()) {
            var type = ExtremeMobs.type(kind);
            check(spawnEntries.stream().anyMatch(entry -> entry.type() == type
                            && entry.baseWeights().equals(List.of(ExtremeMobs.spawnWeight(kind)))),
                    "The ITE browser must show each registered monster's real current raw weight: " + kind.id());
        }
        check(spawnEntries.stream().allMatch(entry -> entry.id().startsWith("modern_ite:")),
                "ITE's browser must not duplicate the base mod's registered monsters");
        check(spawnEntries.stream().anyMatch(entry -> entry.type() == io.github.ningmeng123.modernite.entity.RabidDogContent.RABID_DOG.get()
                        && entry.baseWeights().equals(List.of(5))), "The registered rabid dog must expose its real weight 5");
        findEdit(spawns,"mite.config.monster_spawn_search").setValue("modern_ite:zombie_chaos");
        findEdit(spawns,"mite.config.monster_spawn_weight").setValue("2.5");
        var invalidWeights = MonsterSpawnSettingsScreen.class.getDeclaredField("invalidWeights");
        invalidWeights.setAccessible(true);
        check(((java.util.Set<?>)invalidWeights.get(spawns)).isEmpty(),"A finite2.5 weight must not be marked invalid or rendered as a rejected draft");
        check(findButton(spawns,"mite.config.save").active,"A finite decimal weight must leave native saving enabled");
        capture(mc,spawns,width,height,"ite-natural-spawn-weight-edited",-1,-1);bounds(spawns);
        press(spawns,"mite.config.save");
        check(mc.screen == home,"Saving the single weight editor returns to the shared Creatures category");
        check(IteSpawnConfig.localWeights().get("modern_ite:zombie_chaos")==2.5D
                        && !IteSpawnConfig.localSnapshot().containsKey("modern_ite:zombie_chaos"),
                "Writing weight2.5 must publish exactly2.5 and remove that entity's legacy multiplier");
        check(io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns.weight(ExtremeMobs.type(ExtremeKind.ZOMBIE_CHAOS),25D)==2.5D
                        && io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns.multiplier(ExtremeMobs.type(ExtremeKind.ZOMBIE_CHAOS))==1D,
                "The actual runtime must read the written weight directly with no hidden legacy multiplier");
        press(home,"modern_ite.config.monster_spawns");
        Screen reopened = mc.screen;
        findEdit(reopened,"mite.config.monster_spawn_search").setValue("modern_ite:zombie_chaos");
        check(findEdit(reopened,"mite.config.monster_spawn_weight").getValue().equals("2.5"),
                "Reopening the actual native editor must display the saved decimal2.5 without rounding");
        check(((java.util.Set<?>)invalidWeights.get(reopened)).isEmpty() && findButton(reopened,"mite.config.save").active,
                "The saved decimal remains a valid editable draft after a complete screen round trip");
        reopened.onClose();
        check(mc.screen == home,"Closing the reopened weight editor restores the shared Creatures category");
        auditSpawningRules(mc, home, width, height);
        home.onClose();
        check(mc.screen == main, "The shared Creatures category returns to MITE settings");
        report.addProperty("ite_editors_open_from_mite_categories", true);
        report.addProperty("effective_values_search_verified", true);
        report.addProperty("ite_registry_spawn_browser_and_native_weights_verified", true);
        report.addProperty("ite_single_weight_editor_clears_edited_legacy_multiplier",true);
        report.addProperty("ite_fractional_spawn_weight_editor_accepts_saves_and_reopens_exact_value",true);
        report.addProperty("ite_spawning_rules_paging_validation_drafts_and_save_verified", true);
        report.addProperty("mite_spawning_rules_entry_paging_validation_drafts_and_save_verified", true);
    }

    private static void auditBaseSpawningEntry(Minecraft mc, int width, int height) throws Exception {
        Screen home = configHome(mc);
        home.init(mc, width, height);
        press(home, "mite.config.category.creatures");
        auditBaseSpawningRules(mc, mc.screen, width, height);
    }

    private static void auditScalarTypes() {
        var options = IteConfig.options();
        for (var option : options) {
            Class<?> editorType = switch (option.defaultValue()) {
                case Boolean ignored -> ModConfigSpec.BooleanValue.class;
                case Integer ignored -> ModConfigSpec.IntValue.class;
                case Double ignored -> ModConfigSpec.DoubleValue.class;
                default -> throw new IllegalStateException("Unsupported scalar type for " + option.key());
            };
            Object configValue = IteConfig.SPEC.getValues().get(option.path());
            check(configValue == IteConfig.setting(option.key()) && editorType.isInstance(configValue),
                    "The native editor supports the registered type of " + option.key() + ": " + editorType.getSimpleName());
        }
        report.addProperty("ite_scalars_with_native_editor_types", options.size());
    }

    private static void auditSurvivalSettings(Minecraft mc, Screen main, int width, int height) throws Exception {
        main.init(mc, width, height);
        press(main, "mite.config.category.survival");
        Screen survival = mc.screen;
        press(survival, "modern_ite.config.settings.survival");
        check(mc.screen instanceof IteConfigValuesScreen, "Survival opens the shared Survival and Resources list");
        Screen resources = mc.screen;
        capture(mc, resources, width, height, "survival-resources", -1, -1);
        bounds(resources);

        String daylight = "first_day_longer_day_time";
        Screen editor = openIteValue(mc, resources, daylight, width, height);
        capture(mc, editor, width, height, "survival-daylight-before", -1, -1);
        var toggle = firstCycleButton(editor);
        boolean before = IteConfig.bool(daylight);
        boolean doubleLength = !before;
        click(editor, toggle);
        check(toggle.getValue().equals(doubleLength) && IteConfig.setting(daylight).get().equals(before),
                "Clicking the daylight switch changes only the visible draft");
        capture(mc, editor, width, height, "survival-daylight-switch", -1, -1);
        click(editor, findButton(editor, "mite.config.save"));
        check(mc.screen == resources && IteConfig.bool(daylight) == doubleLength,
                "Saving the switch returns to Survival and Resources and publishes its value");
        var disk = new TomlParser().parse(Files.readString(configFile));
        check(Boolean.valueOf(doubleLength).equals(disk.get(List.of("GameMechanics", daylight))),
                "The real TOML contains the saved daylight switch value");
        editor = openIteValue(mc, resources, daylight, width, height);
        check(firstCycleButton(editor).getValue().equals(doubleLength), "The reopened switch shows the saved value");
        editor.onClose();

        for (String key : List.of("longer_day_time_end_day", "in_rain_debuff_time", "in_wall_damage_for_player")) {
            double originalScroll = valuesList(resources).getScrollAmount();
            editor = openIteValue(mc, resources, key, width, height);
            String translation = "modern_ite.config." + key;
            EditBox input = findEdit(editor, translation);
            double value = Double.parseDouble(input.getValue());
            check(input.active && value == IteConfig.number(key), "Clicking opens the matching active number box: " + key);
            if (height == 240 && key.equals("in_wall_damage_for_player")) {
                check(valuesList(resources).getScrollAmount() > originalScroll,
                        "The off-screen decimal setting requires scrolling in the compact list");
            }
            editor.onClose();
            check(mc.screen == resources, "The number box returns to the shared Survival and Resources list");
        }
        resources.onClose();
        check(mc.screen == survival, "The resource list returns to the Survival category");
        survival.onClose();
        check(mc.screen == main, "The Survival category returns to the MITE homepage");
        report.addProperty("survival_switch_mouse_edit_save_and_reopen", true);
        report.addProperty("survival_number_editors_open_by_click_after_scrolling", true);
    }

    private static Screen openIteValue(Minecraft mc, Screen resources, String key, int width, int height) throws Exception {
        resources.init(mc, width, height);
        var list = valuesList(resources);
        String name = Component.translatable("modern_ite.config." + key).getString();
        List<?> rows = list.children();
        var row = rows.stream().filter(entry -> ((ObjectSelectionList.Entry<?>) entry).getNarration().getString().startsWith(name))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing resource row " + key));
        int index = rows.indexOf(row);
        var rowTop = AbstractSelectionList.class.getDeclaredMethod("getRowTop", int.class);
        rowTop.setAccessible(true);
        int y = (int) rowTop.invoke(list, index) + 8;
        double x = list.getRowRight() - 12D;
        while (y < list.getY() || y >= list.getBottom()) {
            double before = list.getScrollAmount();
            check(resources.mouseScrolled(x, list.getY() + 8, 0, y < list.getY() ? 1D : -1D)
                            && list.getScrollAmount() != before,
                    "The mouse wheel can reach the resource row: " + key);
            y = (int) rowTop.invoke(list, index) + 8;
        }
        check(resources.mouseClicked(x, y, 0) && mc.screen instanceof IteConfigOptionScreen,
                "Clicking the visible ITE value opens its editor: " + key);
        mc.screen.init(mc, width, height);
        return mc.screen;
    }

    private static void auditCombatSettings(Minecraft mc, Screen main, int width, int height) throws Exception {
        EditBox globalSearch = findEdit(main, "mite.config.search_all");
        globalSearch.setValue("stepped_mob_damage_progress_increase_day");
        var matches = MiteSettingsScreen.class.getDeclaredField("matches");
        matches.setAccessible(true);
        check(((List<?>) matches.get(main)).isEmpty(),
                "The retired date-damage field is absent from search while retaining its TOML compatibility value");
        for (String key : List.of("stepped_mob_damage_factor", "stepped_mob_damage_progress_max")) {
            globalSearch.setValue(key);
            press(main, "modern_ite.config." + key);
            check(mc.screen instanceof IteConfigOptionScreen && findEdit(mc.screen, "modern_ite.config." + key).active,
                    "The static mob combo factor remains editable: " + key);
            mc.screen.onClose();
            check(mc.screen == main, "Closing the native editor returns to the global search");
            globalSearch = findEdit(main, "mite.config.search_all");
        }
        globalSearch.setValue("");
        press(main, "mite.config.category.combat");
        Screen combat = mc.screen;
        capture(mc, combat, width, height, "mite-combat-settings", -1, -1);
        bounds(combat);
        press(combat, "modern_ite.config.settings.enchantment_effects");
        check(mc.screen instanceof IteConfigValuesScreen, "Combat opens the ITE enchantment settings browser");
        Screen values = mc.screen;
        values.init(mc, width, height);
        EditBox search = findEdit(values, "modern_ite.config.search");
        var count = IteConfigValuesScreen.class.getDeclaredField("matchCount");
        count.setAccessible(true);
        search.setValue("stepped_mob_damage_factor");
        check(count.getInt(values) == 0, "The enchantment browser is scoped to its category");
        search.setValue("征服者");
        check(count.getInt(values) == 1, "Searching 征服者 finds the only matching enchantment setting");
        capture(mc, values, width, height, "conqueror-search", -1, -1);
        bounds(values);
        var list = valuesList(values);
        var rowTop = AbstractSelectionList.class.getDeclaredMethod("getRowTop", int.class);
        rowTop.setAccessible(true);
        int rowY = (int) rowTop.invoke(list, list.children().size() - 1) + 8;
        capture(mc, values, width, height, "conqueror-tooltip", width / 2, rowY);
        values.mouseClicked(width / 2D, rowY, 0);
        check(mc.screen instanceof IteConfigOptionScreen, "Clicking an ITE value opens its editor");
        Screen editor = mc.screen;
        capture(mc, editor, width, height, "ite-enchantment-editor", -1, -1);
        check(findEdit(editor, "modern_ite.config." + SAMPLE).active, "The enchantment editor shows its number box");
        click(editor, findButton(editor, "gui.cancel"));
        check(mc.screen == values && findEdit(values, "modern_ite.config.search").getValue().equals("征服者"),
                "Returning to the scoped enchantment settings preserves the search");
        values.onClose();
        check(mc.screen == combat, "Closing the enchantment browser restores the shared Combat category");
        combat.onClose();
        check(mc.screen == main, "Closing the Combat category restores the MITE homepage");
        report.addProperty("effective_values_search_verified", true);
    }

    private static void checkSavedEnchantments() throws Exception {
        if (savedEnchantments.isEmpty()) return;
        var disk = new TomlParser().parse(Files.readString(configFile));
        savedEnchantments.forEach((option, expected) -> {
            Object saved = disk.get(option.path());
            Object effective = IteConfig.value(option.key());
            Object configured = IteConfig.setting(option.key()).get();
            check(sameValue(saved, expected) && sameValue(effective, expected) && sameValue(configured, expected),
                    option.key() + " must retain its saved value: expected=" + expected + ", TOML=" + saved
                            + ", effective=" + effective + ", config value=" + configured);
        });
    }

    private static void auditAllScalarDrafts(Minecraft mc) throws Exception {
        Set<String> sharedSpawning = MiteExtensions.policy().spawningControls().stream()
                .map(MiteExtensions.SpawningControl::key).collect(Collectors.toSet());
        Screen home = configHome(mc);
        int checked = 0, checkedShared = 0;
        for (var option : IteConfig.options()) {
            if (option.key().equals("stepped_mob_damage_progress_increase_day")) continue;
            boolean shared = sharedSpawning.contains(option.key());
            Screen editor = openGlobalOption(mc, home, option, shared);
            draftOption(editor, option);
            click(editor, findButton(editor, "gui.cancel"));
            check(mc.screen == home, "Cancel returns to the results for " + option.key());
            if (shared) checkedShared++; else checked++;
        }
        check(checkedShared == sharedSpawning.size(), "Every shared spawning control has an editable config option");
        report.addProperty("ite_options_opened_and_edited_from_global_search", checked);
        report.addProperty("ite_shared_spawning_options_opened_and_edited_from_global_search", checkedShared);
    }

    private static Screen openGlobalOption(Minecraft mc, Screen home, IteConfig.Option option, boolean shared) throws Exception {
        String id = ModernIte.MOD_ID + ":" + String.join(".", option.path());
        home.init(mc, 320, 240);
        findEdit(home, "mite.config.search_all").setValue(id);
        var matches = MiteSettingsScreen.class.getDeclaredField("matches");
        matches.setAccessible(true);
        List<?> matching = (List<?>) matches.get(home);
        var exact = matching.stream().map(ConfigSearchIndex.Entry.class::cast).filter(entry -> entry.id().equals(id)).toList();
        check(exact.size() == 1, "The expected option has one exact search result: " + id);
        var results = MiteSettingsScreen.class.getDeclaredField("results");
        results.setAccessible(true);
        List<?> buttons = (List<?>) results.get(home);
        int perPage = buttons.size() - 2;
        int resultIndex = matching.indexOf(exact.getFirst());
        while (resultIndex >= perPage) {
            click(home, findButton(home, ">"));
            resultIndex -= perPage;
        }
        click(home, (Button) buttons.get(resultIndex));
        check(shared ? mc.screen instanceof MonsterSpawningRulesScreen : mc.screen instanceof IteConfigOptionScreen,
                "Global search opens the proper ITE editor for " + id);
        // Shared editor selection is resolved when the screen first initializes at the real window size.
        if (!shared) mc.screen.init(mc, 320, 240);
        return mc.screen;
    }

    private static void auditNextEnchantment(Minecraft mc) throws Exception {
        var enchantments = IteConfig.options().stream()
                .filter(option -> option.path().subList(0, option.path().size() - 1).equals(List.of("Item", "Enchantment")))
                .toList();
        var option = enchantments.get(enchantmentIndex++);
        Screen home = configHome(mc);
        home.init(mc, 320, 240);
        press(home, "mite.config.category.combat");
        press(mc.screen, "modern_ite.config.settings.enchantment_effects");
        Screen values = mc.screen;
        Screen editor = openIteValue(mc, values, option.key(), 320, 240);
        var input = findEdit(editor, "modern_ite.config." + option.key());
        String invalid = configNumber(option.maximum() + 1D);
        typeNumber(editor, input, invalid);
        editor.init(mc, 854, 480);
        check(findEdit(editor, "modern_ite.config." + option.key()).getValue().equals(invalid)
                        && !findButton(editor, "mite.config.save").active,
                "Resizing retains the invalid value and prevents saving for " + option.key());
        Object expected = draftOption(editor, option);
        boolean scientific = option.key().equals("crit_enchantment_damage_boost_per_lvl");
        if (scientific) {
            input = findEdit(editor, "modern_ite.config." + option.key());
            typeNumber(editor, input, "1e-80");
            check(input.getValue().equals("1e-80") && findButton(editor, "mite.config.save").active,
                    "The enchantment input accepts a finite value in scientific notation");
            expected = 1e-80D;
        }
        if (SAMPLE.equals(option.key())) capture(mc, editor, 854, 480, "enchantment-draft", -1, -1);
        click(editor, findButton(editor, "mite.config.save"));
        check(mc.screen == values, "Saving returns to the Enchantment Effects list for " + option.key());
        savedEnchantments.put(option, expected);
        checkSavedEnchantments();
        editor = openIteValue(mc, values, option.key(), 854, 480);
        check(sameValue(Double.parseDouble(findEdit(editor, "modern_ite.config." + option.key()).getValue()), expected),
                "The reopened enchantment shows its saved value for " + option.key());
        if (scientific) report.addProperty("scientific_notation_saved_and_reopened", true);
        click(editor, findButton(editor, "gui.cancel"));
        if (enchantmentIndex < enchantments.size()) auditStep--;
        report.addProperty("ite_enchantment_values_saved_and_reopened", enchantmentIndex);
    }

    private static Object draftOption(Screen editor, IteConfig.Option option) throws Exception {
        Object original = IteConfig.value(option.key());
        Object candidate = switch (original) {
            case Boolean flag -> !flag;
            case Integer integer -> integer > option.minimum() ? integer - 1 : integer + 1;
            case Double decimal -> decimal == option.minimum() ? (option.minimum() + option.maximum()) / 2D : option.minimum();
            default -> throw new IllegalStateException("Unsupported ITE draft type: " + option.key());
        };
        if (candidate instanceof Number number) {
            EditBox input = findEdit(editor, "modern_ite.config." + option.key());
            String invalid = configNumber(option.maximum() + 1D);
            typeNumber(editor, input, invalid);
            check(input.getValue().equals(invalid) && !findButton(editor, "mite.config.save").active,
                    "An out-of-range draft stays visible without saving for " + option.key());
            typeNumber(editor, input, configNumber(number.doubleValue()));
            check(sameValue(Double.parseDouble(input.getValue()), candidate), "The number box keeps the draft for " + option.key());
        } else if (editor instanceof MonsterSpawningRulesScreen) {
            var toggle = spawningToggle(editor, option.key());
            click(editor, toggle);
            var label = Component.translatable((Boolean) candidate ? "options.on" : "options.off");
            check(toggle.getMessage().equals(label), "The shared spawning toggle displays the new draft for " + option.key());
        } else {
            var toggle = firstCycleButton(editor);
            click(editor, toggle);
            check(toggle.getValue().equals(candidate), "The toggle displays the new draft for " + option.key());
        }
        check(findButton(editor, "mite.config.save").active && sameValue(IteConfig.value(option.key()), original)
                        && sameValue(IteConfig.setting(option.key()).get(), original),
                "Only the draft changes before pressing Save for " + option.key());
        return candidate;
    }

    private static Button spawningToggle(Screen editor, String key) throws Exception {
        var visible = MonsterSpawningRulesScreen.class.getDeclaredField("visible");
        visible.setAccessible(true);
        for (Object row : (List<?>) visible.get(editor)) {
            var control = row.getClass().getDeclaredMethod("control");
            control.setAccessible(true);
            Object details = control.invoke(row);
            var name = details.getClass().getDeclaredMethod("key");
            name.setAccessible(true);
            if (!key.equals(name.invoke(details))) continue;
            var y = row.getClass().getDeclaredMethod("y");
            y.setAccessible(true);
            int rowY = (int) y.invoke(row);
            return widgets(editor).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getY() <= rowY && rowY < button.getBottom()).findFirst().orElseThrow();
        }
        throw new IllegalStateException("The searched spawning row is missing: " + key);
    }

    private static void typeNumber(Screen editor, EditBox input, String text) {
        click(editor, input);
        input.moveCursorToEnd(false);
        input.setHighlightPos(0);
        for (char digit : text.toCharArray()) check(editor.charTyped(digit, 0), "The number box accepts input " + text);
    }

    private static String configNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static boolean sameValue(Object left, Object right) {
        return left instanceof Number a && right instanceof Number b
                ? Double.compare(a.doubleValue(), b.doubleValue()) == 0 : right.equals(left);
    }

    private static void auditRedDiamondExperience(Minecraft mc, Screen main) {
        String translation = "modern_ite.config.red_diamond_exp";
        var setting = IteConfig.setting("red_diamond_exp");
        Object original = setting.get();
        check(setting.getDefault().equals(250), "Red diamond experience defaults to 250");
        try {
            setting.set(250);
            IteConfig.refresh();
            findEdit(main, "mite.config.search_all").setValue("红钻献祭");
            press(main, translation);
            check(mc.screen instanceof IteConfigOptionScreen, "MITE's search opens the editable red diamond experience setting");
            EditBox field = findEdit(mc.screen, translation);
            check(field.active && field.getValue().equals("250"), "The real red diamond number box displays the effective 250");
            var stack = new ItemStack(ExtremeContent.FANCY_RED.get());
            var tooltip = Component.translatable("tooltip.mite.gem_sacrifice", 250).withStyle(ChatFormatting.AQUA);
            check(tooltip.getString().equals("250 点经验") && stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL)
                            .stream().filter(tooltip::equals).count() == 1,
                    "The real red diamond tooltip renders the same translated experience line as other gems");
            typeNumber(mc.screen, field, "123");
            check(setting.get().equals(250), "An unsaved red diamond experience draft leaves the current value intact");
            click(mc.screen, findButton(mc.screen, "mite.config.save"));
            check(mc.screen == main && setting.get().equals(123) && IteConfig.integer("red_diamond_exp") == 123,
                    "Saving the red diamond editor updates the value and returns to MITE settings");
            findEdit(main, "mite.config.search_all").setValue("");
            report.addProperty("red_diamond_experience_search_editor_and_tooltip_verified", true);
        } finally {
            setting.set(original);
            IteConfig.SPEC.save();
            IteConfig.refresh();
        }
    }

    private static void auditSpawningRulesSearch(Minecraft mc, Screen main) {
        String key = "extreme_mob_spawn_limit_day";
        findEdit(main, "mite.config.search_all").setValue(key);
        press(main, "modern_ite.config." + key);
        check(mc.screen instanceof MonsterSpawningRulesScreen
                        && findEdit(mc.screen, "modern_ite.config." + key).active,
                "Searching for an ITE spawn date focuses that value in the shared rules editor");
        mc.screen.onClose();
        check(mc.screen == main, "Closing the shared rules editor preserves the originating global search");
        findEdit(main, "mite.config.search_all").setValue("");
        report.addProperty("ite_spawn_date_search_opens_shared_rules", true);
    }

    private static void auditSpawnOverrideSearch(Minecraft mc, Screen main) {
        for (String key : List.of("natural_spawn_rates", "natural_spawn_weights")) {
            findEdit(main, "mite.config.search_all").setValue("modern_ite:GameMechanics.MobSpawning." + key);
            press(main, "modern_ite.config." + key);
            check(mc.screen instanceof SingleConfigValueScreen, "Searching a raw ITE override opens its native list editor: " + key);
            mc.screen.onClose();
            check(mc.screen == main, "Closing an override editor returns to the shared search");
        }
        findEdit(main, "mite.config.search_all").setValue("");
        report.addProperty("raw_ite_spawn_override_lists_open_from_shared_search", true);
    }

    private static void auditOres(Minecraft mc, Screen title) throws Exception {
        var mite = ModList.get().getModContainerById("mite").orElseThrow();
        Screen parent = IConfigScreenFactory.getForMod(mite.getModInfo()).orElseThrow().createScreen(mite, title);
        mc.setScreen(parent);
        auditRedDiamondOreChance(mc, parent);
        press(parent, "mite.config.category.world");
        Screen world = mc.screen;
        press(world, "mite.config.ore_generation");
        check(mc.screen instanceof OreGenerationSettingsScreen, "The actual MITE ore button must open its multiplier page");
        Screen ores = mc.screen;
        findEdit(ores, "mite.config.ore_search").setValue("minecraft:copper_ore");
        check(widgets(ores).stream().filter(EditBox.class::isInstance).count() == 3,
                "A filtered ore row exposes search, multiplier and absolute height range inputs");
        selectOreDimension(ores, R196OreRules.Dimension.UNDERWORLD);
        findEdit(ores, "mite.config.ore_multiplier").setValue("0");
        selectOreDimension(ores, R196OreRules.Dimension.OVERWORLD);
        checkDouble(Double.parseDouble(findEdit(ores, "mite.config.ore_multiplier").getValue()),
                originalBaseSnapshot.overworldOres().get("COPPER"), "Surface and deep drafts start independently");
        findEdit(ores, "mite.config.ore_multiplier").setValue("0.5");
        selectOreDimension(ores, R196OreRules.Dimension.UNDERWORLD);
        check(findEdit(ores, "mite.config.ore_multiplier").getValue().equals("0"), "A disabled deep draft survives switching dimension");
        findEdit(ores, "mite.config.ore_multiplier").setValue("2");
        selectOreDimension(ores, R196OreRules.Dimension.OVERWORLD);
        check(findEdit(ores, "mite.config.ore_multiplier").getValue().equals("0.5"), "A half-rate surface draft survives switching dimension");
        press(ores, "mite.config.reset");
        checkDouble(Double.parseDouble(findEdit(ores,"mite.config.ore_multiplier").getValue()),
                MiteConfig.factoryOreMultiplier(R196OreRules.Kind.COPPER,R196OreRules.Dimension.OVERWORLD,false),
                "The ore reset button restores the current bundled surface copper multiplier");
        findEdit(ores, "mite.config.ore_multiplier").setValue("NaN");
        selectOreDimension(ores, R196OreRules.Dimension.UNDERWORLD);
        check(!findButton(ores, "mite.config.save").active, "An invalid hidden dimension must disable the joint save button");
        check(findEdit(ores, "mite.config.ore_multiplier").getValue().equals("2"), "Deep double-rate draft survives invalid surface input");
        findEdit(ores, "mite.config.ore_multiplier").setValue("0.5");
        selectOreDimension(ores, R196OreRules.Dimension.OVERWORLD);
        check(findEdit(ores, "mite.config.ore_multiplier").getValue().equals("NaN"), "Invalid text is preserved when returning to its dimension");
        findEdit(ores, "mite.config.ore_multiplier").setValue("2");
        check(findButton(ores, "mite.config.save").active, "Fixing all dimension drafts re-enables saving");
        findEdit(ores, "mite.config.ore_height_range").setValue("[100,-100]");
        selectOreDimension(ores, R196OreRules.Dimension.UNDERWORLD);
        check(!findButton(ores, "mite.config.save").active, "A reversed hidden height range blocks saving");
        findEdit(ores, "mite.config.ore_height_range").setValue("[-50,200]");
        selectOreDimension(ores, R196OreRules.Dimension.OVERWORLD);
        check(findEdit(ores, "mite.config.ore_height_range").getValue().equals("[100,-100]"), "Invalid height text survives dimension switches");
        findEdit(ores, "mite.config.ore_height_range").setValue("[-100,100]");
        check(findButton(ores, "mite.config.save").active, "Valid negative absolute heights re-enable saving");
        check(MiteConfig.localSnapshot().equals(originalBaseSnapshot), "Draft changes cannot reach gameplay before saving");
        for (var dimension : List.of(R196OreRules.Dimension.OVERWORLD, R196OreRules.Dimension.UNDERWORLD)) {
            selectOreDimension(ores, dimension);
            for (int[] size : new int[][] {{320, 240}, {854, 480}}) {
                capture(mc, ores, size[0], size[1], "ore-" + dimension.name().toLowerCase(java.util.Locale.ROOT) + "-multiplier", -1, -1);
                bounds(ores);
                checkDouble(Double.parseDouble(findEdit(ores, "mite.config.ore_multiplier").getValue()),
                        dimension == R196OreRules.Dimension.OVERWORLD ? 2D : .5D, "Draft survives resizing and remains in the correct dimension");
            }
        }
        editRedDiamondOre(ores);
        press(ores, "mite.config.save");
        check(mc.screen == world, "Saving both dimension multipliers returns to the shared World category");
        check(MiteConfig.localSnapshot().oreCandidates().equals(originalBaseSnapshot.oreCandidates())
                        && MiteConfig.localSnapshot().oreChances().equals(originalBaseSnapshot.oreChances())
                        && MiteConfig.localSnapshot().overworldOreCandidates().equals(originalBaseSnapshot.overworldOreCandidates())
                        && MiteConfig.localSnapshot().overworldOreChances().equals(originalBaseSnapshot.overworldOreChances()),
                "Simple multiplier editing must preserve all existing advanced candidate and probability settings");
        report.addProperty("ore_multiplier_ui_clicked", true);
        report.addProperty("ore_zero_half_double_drafts_survived_dimension_switches", true);
        report.addProperty("ore_invalid_hidden_draft_blocked_save", true);
        report.addProperty("ore_reset_restores_bundled_default", true);
        report.addProperty("ore_both_dimension_drafts_saved", true);
        report.addProperty("ore_advanced_settings_preserved", true);
        report.addProperty("ore_height_drafts_validated_and_saved", true);
    }

    private static void auditRedDiamondOreChance(Minecraft mc, Screen parent) {
        var redDiamond = R196OreRules.Kind.valueOf("RED_DIAMOND");
        var chance = MiteConfig.UNDERWORLD_ORE_CHANCES.get(redDiamond);
        double original = chance.get();
        checkDouble(chance.getDefault(), 100D, "The red diamond ore chance defaults to one hundred percent");
        try {
            findEdit(parent, "mite.config.search_all").setValue("underworld_generation.red_diamond.chance_percent");
            press(parent, "红钻矿石 · 通过率（%）");
            check(mc.screen instanceof SingleConfigValueScreen, "MITE's search opens the red diamond ore pass chance editor");
            var field = findEdit(mc.screen, "mite.config.ore_chance");
            check(field.active, "The red diamond ore pass chance is editable");
            field.setValue("25");
            mc.screen.onClose();
            checkDouble(chance.get(), 25D, "Editing the native chance field updates the red diamond setting");
            check(mc.screen == parent, "Closing the chance editor returns to MITE settings");
            findEdit(parent, "mite.config.search_all").setValue("");
            report.addProperty("red_diamond_ore_chance_search_and_edit_verified", true);
        } finally {
            chance.set(original);
            MiteConfig.SPEC.save();
            MiteConfig.refreshEnchantmentRules();
        }
    }

    private static void editRedDiamondOre(Screen ores) {
        selectOreDimension(ores, R196OreRules.Dimension.OVERWORLD);
        findEdit(ores, "mite.config.ore_search").setValue("红钻矿石");
        check(widgets(ores).stream().filter(EditBox.class::isInstance).count() == 1,
                "Searching for red diamond ore in the Overworld leaves only the search field");
        selectOreDimension(ores, R196OreRules.Dimension.UNDERWORLD);
        check(widgets(ores).stream().filter(EditBox.class::isInstance).count() == 3,
                "Searching for red diamond ore in the Underworld shows one editable ore row");
        findEdit(ores, "mite.config.ore_search").setValue("modern_ite:fancy_red_ore");
        press(ores, "mite.config.reset");
        EditBox multiplier = findEdit(ores, "mite.config.ore_multiplier");
        EditBox height = findEdit(ores, "mite.config.ore_height_range");
        check(multiplier.getValue().equals("1") && height.getValue().equals("[131,274]"),
                "The native red diamond ore row resets to its bundled multiplier and height defaults");
        multiplier.setValue("1.5");
        height.setValue("[140,260]");
        check(findButton(ores, "mite.config.save").active, "Valid red diamond ore settings can be saved");
    }

    private static void checkOreSave() throws Exception {
        checkDouble(MiteConfig.oreMultiplier(R196OreRules.Kind.COPPER, R196OreRules.Dimension.OVERWORLD), 2D,
                "Saved surface multiplier is published to gameplay");
        checkDouble(MiteConfig.underworldOreMultiplier(R196OreRules.Kind.COPPER), .5D, "Saved deep multiplier is published to gameplay");
        var parsed = new TomlParser().parse(Files.readString(baseConfigFile));
        checkDouble(((Number)parsed.get(List.of("overworld_ores", "copper"))).doubleValue(), 2D, "Native TOML keeps the surface multiplier");
        checkDouble(((Number)parsed.get(List.of("underworld_ores", "copper"))).doubleValue(), .5D, "Native TOML keeps the separate deep multiplier");
        check(parsed.<String>get(List.of("overworld_generation", "copper", "height_range")).equals("[-100,100]"), "Native TOML stores absolute surface heights");
        check(parsed.<String>get(List.of("underworld_generation", "copper", "height_range")).equals("[-50,200]"), "Native TOML stores independent underworld heights");
        var redDiamond = R196OreRules.Kind.valueOf("RED_DIAMOND");
        checkDouble(MiteConfig.underworldOreMultiplier(redDiamond), 1.5D, "Saved red diamond ore multiplier reaches gameplay");
        check(MiteConfig.oreHeightRange(redDiamond, R196OreRules.Dimension.UNDERWORLD, false).toString().equals("[140,260]"),
                "Saved red diamond ore heights reach gameplay");
        checkDouble(((Number)parsed.get(List.of("underworld_ores", "red_diamond"))).doubleValue(), 1.5D,
                "Native TOML stores the red diamond ore multiplier under the Underworld");
        check("[140,260]".equals(parsed.get(List.of("underworld_generation", "red_diamond", "height_range"))),
                "Native TOML stores the red diamond ore height override under the Underworld");
        report.addProperty("ore_multiplier_saved_to_toml", true);
        report.addProperty("ore_multiplier_published_to_gameplay", true);
        report.addProperty("red_diamond_ore_search_defaults_and_save_verified", true);
    }

    private static void collectBaseValues(UnmodifiableConfig config) {
        for (var entry : config.entrySet()) {
            if (entry.getValue() instanceof UnmodifiableConfig nested) collectBaseValues(nested);
            else if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                Object original = value.get();
                originalBaseValues.put(value, original instanceof List<?> list ? List.copyOf(list) : original);
            }
        }
    }

    private static void selectOreDimension(Screen screen, R196OreRules.Dimension dimension) {
        check(screen instanceof OreGenerationSettingsScreen && (dimension == R196OreRules.Dimension.OVERWORLD
                || dimension == R196OreRules.Dimension.UNDERWORLD),"The ore selector targets an actual supported dimension tab");
        boolean overworld = dimension == R196OreRules.Dimension.OVERWORLD;
        Button button = oreDimensionTab(screen, overworld ? "overworld" : "underworld");
        Button other = oreDimensionTab(screen, overworld ? "underworld" : "overworld");
        if (button.active) button.onPress();
        check(!button.active && other.active,"The real button click marks the requested ore dimension selected and keeps the opposite tab available");
    }

    private static Button oreDimensionTab(Screen screen, String dimension) {
        var label = Component.translatable("mite.config.ore_dimension." + dimension);
        String plain = label.getString();
        String selected = Component.translatable("mite.config.ore_dimension.selected", label).getString();
        return widgets(screen).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(plain) || button.getMessage().getString().equals(selected))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing ore dimension tab " + dimension));
    }

    private static void auditSpawningRules(Minecraft mc, Screen home, int width, int height) throws Exception {
        String key="mob_max_spawn_count_chunk_base";
        String dayKey="extreme_mob_spawn_limit_day";
        String mobDayKey="dragon_zombie_spawn_limit_day";
        int original=IteConfig.integer(key);
        int originalDay=IteConfig.integer(dayKey);
        int originalMobDay=IteConfig.integer(mobDayKey);
        int edited=original==1000000?original-1:original+1;
        int editedDay=originalDay==1000000?originalDay-1:originalDay+1;
        int editedMobDay=originalMobDay==15?16:15;
        try {
            check(!IteConfig.defaults().containsKey("reference_spawning"),"The removed spawning preset has no schema entry");
            press(home,"mite.config.spawning_rules.button");
            check(mc.screen instanceof MonsterSpawningRulesScreen,"The shared Creatures category opens the rules editor with ITE controls");
            Screen rules=mc.screen;
            capture(mc,rules,width,height,"ite-spawning-rules",-1,-1);bounds(rules);
            var dayField=findEdit(rules,"modern_ite.config."+dayKey);
            check(dayField.active&&widgets(rules).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).noneMatch(field->field.getY()<dayField.getY()),"The main-mod page begins with the editable extreme start day without a preset switch");
            dayField.setValue("0");
            check(!findButton(rules,"mite.config.save").active,"Day zero is invalid");
            dayField.setValue(Integer.toString(editedDay));
            check(findButton(rules,"mite.config.save").active,"Correcting the date restores saving");
            showIteSpawningField(rules,mobDayKey);
            var mobDayField=findEdit(rules,"modern_ite.config."+mobDayKey);
            check(mobDayField.active,"The individual dragon zombie date is editable in the main spawning page");
            mobDayField.setValue("5.5");
            check(!findButton(rules,"mite.config.save").active,"Fractional individual dates block saving");
            mobDayField.setValue("0");
            check(!findButton(rules,"mite.config.save").active,"Individual dates require at least day one");
            mobDayField.setValue(Integer.toString(editedMobDay));
            check(findButton(rules,"mite.config.save").active,"Correcting the individual date restores saving");
            showIteSpawningField(rules,"local_hostile_limit");
            check(findEdit(rules,"modern_ite.config.local_hostile_limit").active,"Splitting and support density remains editable");
            showIteSpawningField(rules,key);
            check(findEdit(rules,"modern_ite.config."+key).active,"The ITE default natural cap is directly editable");
            findEdit(rules,"modern_ite.config."+key).setValue("-1");
            check(!findButton(rules,"mite.config.save").active,"Negative cap blocks saving");
            auditSpawningDraftNavigation(rules,width,height,"ite-cap");
            check(!findButton(rules,"mite.config.save").active,"Invalid drafts remain blocking after the actual available page navigation");
            showIteSpawningField(rules,key);
            check(findEdit(rules,"modern_ite.config."+key).getValue().equals("-1"),"Paging preserves the invalid cap draft");
            findEdit(rules,"modern_ite.config."+key).setValue(Integer.toString(edited));
            check(findButton(rules,"mite.config.save").active,"Correcting the cap restores saving");
            firstSpawningPage(rules);
            int pages=0;
            do {
                for(var widget:widgets(rules)) {
                    if(widget instanceof EditBox field)check(field.active,"Every exposed numeric spawning value is editable");
                    if(widget instanceof Button button&&(button.getMessage().getString().equals(Component.translatable("options.on").getString())
                            ||button.getMessage().getString().equals(Component.translatable("options.off").getString())))
                        check(button.active,"Every exposed spawning boolean is editable");
                }
                bounds(rules);
                if(!findButton(rules,">").active)break;
                press(rules,">");
                if(++pages>40)throw new IllegalStateException("Unbounded spawning page list");
            } while(true);
            capture(mc,rules,width,height,"ite-spawning-rules-last-page",-1,-1);
            showIteSpawningField(rules,key);
            check(findEdit(rules,"modern_ite.config."+key).getValue().equals(Integer.toString(edited)),"Every page retains the edited cap");
            showIteSpawningField(rules,mobDayKey);
            check(findEdit(rules,"modern_ite.config."+mobDayKey).getValue().equals(Integer.toString(editedMobDay)),
                    "Paging retains the individual species date draft");
            press(rules,"mite.config.save");
            check(mc.screen==home&&IteConfig.integer(key)==edited&&IteConfig.integer(dayKey)==editedDay
                            &&IteConfig.integer(mobDayKey)==editedMobDay
                            &&IteConfig.extremeMobFirstDay("modern_ite:dragon_zombie",true)==Math.max(editedDay,editedMobDay),
                    "Saving publishes the cap, global date and individual date together and restores the parent");
            var parsed=new TomlParser().parse(Files.readString(configFile));
            check(((Number)parsed.get(List.of("GameMechanics","MobSpawning",key))).intValue()==edited
                            &&!parsed.contains(List.of("GameMechanics","MobSpawning","reference_spawning")),
                    "The editor persists the editable cap without a preset key");
            check(((Number)parsed.get(List.of("GameMechanics","MobSpawning",dayKey))).intValue()==editedDay,
                    "The editor persists the edited extreme start date");
            check(((Number)parsed.get(List.of("GameMechanics","MobSpawning",mobDayKey))).intValue()==editedMobDay,
                    "The editor persists the individual species start date");
            report.addProperty("ite_spawning_all_values_editable_without_preset",true);
            report.addProperty("ite_spawning_paging_preserves_and_validates_drafts",true);
            report.addProperty("ite_extreme_start_day_first_page_validation_and_save",true);
            report.addProperty("ite_individual_spawn_day_validation_paging_and_save",true);
        } finally {
            IteConfig.setting(key).set(original);IteConfig.setting(dayKey).set(originalDay);IteConfig.setting(mobDayKey).set(originalMobDay);
            IteConfig.SPEC.save();IteConfig.refresh();
        }
    }

    private static void firstSpawningPage(Screen rules) {
        while(findButton(rules,"<").active)press(rules,"<");
    }

    private static void showIteSpawningField(Screen rules,String key) {
        firstSpawningPage(rules);
        String label=Component.translatable("modern_ite.config."+key).getString();
        int pages=0;
        while(widgets(rules).stream().noneMatch(widget->widget instanceof EditBox&&widget.getMessage().getString().equals(label))) {
            check(pages++<40&&findButton(rules,">").active,"The ITE setting must be reachable by paging: "+key);
            press(rules,">");bounds(rules);
        }
    }

    private static void auditSpawningDraftNavigation(Screen rules,int width,int height,String context) throws ReflectiveOperationException {
        var pageField = MonsterSpawningRulesScreen.class.getDeclaredField("page");
        var countField = MonsterSpawningRulesScreen.class.getDeclaredField("pageCount");
        pageField.setAccessible(true); countField.setAccessible(true);
        int before = pageField.getInt(rules), count = countField.getInt(rules);
        var next = findButton(rules,">"); var previous = findButton(rules,"<");
        check(count >= 1 && before >= 0 && before < count,"The real spawning page and page count are valid");
        check(next.active == (before + 1 < count) && previous.active == (before > 0),
                "The native paging arrows exactly reflect the current page boundaries");
        if (height <= 240) check(count > 1,"The small-window audit must exercise real pagination rather than skip its draft-preservation check");
        String navigation;
        int expected;
        if (next.active) {
            press(rules,">"); expected = before + 1; navigation = "next";
        } else if (previous.active) {
            press(rules,"<"); expected = before - 1; navigation = "previous_from_last_page";
        } else {
            check(count == 1 && before == 0,"Both arrows are disabled only when the whole editor fits on one page");
            expected = before; navigation = "single_page_no_navigation";
        }
        check(pageField.getInt(rules) == expected,"The actual screen moves by exactly one page, or stays put for a verified single-page layout");
        bounds(rules);
        if (!report.has("spawning_draft_navigation")) report.add("spawning_draft_navigation",new JsonArray());
        JsonObject result = new JsonObject(); result.addProperty("context",context);
        result.addProperty("width",width); result.addProperty("height",height);
        result.addProperty("page_count",count); result.addProperty("from_page",before); result.addProperty("to_page",expected);
        result.addProperty("navigation",navigation); report.getAsJsonArray("spawning_draft_navigation").add(result);
    }

    private static void auditBaseSpawningRules(Minecraft mc, Screen home, int width, int height) throws Exception {
        check(originalBaseValues.keySet().containsAll(MiteConfig.SPAWNING_CONTROLS.values()),
                "The final restoration must include every MITE spawning setting");
        press(home,"mite.config.monster_spawning");
        check(mc.screen instanceof MonsterSpawnSettingsScreen,"MITE's refresh-rate entry opens its registry browser");
        Screen rates=mc.screen;
        capture(mc,rates,width,height,"mite-natural-spawns",-1,-1);bounds(rates);
        press(rates,"mite.config.spawning_rules.button");
        check(mc.screen instanceof MonsterSpawningRulesScreen,"MITE's spawning rules entry opens the same main-mod editor with ITE controls");
        Screen rules=mc.screen;
        capture(mc,rules,width,height,"mite-spawning-rules",-1,-1);bounds(rules);
        String key="hostile_spawn_interval_ticks";
        int original=IteConfig.integer(key);
        int edited=original==72000?original-1:original+1;
        try {
            showIteSpawningField(rules,key);
            var interval=findEdit(rules,"modern_ite.config."+key);
            check(interval.active,"The unified ITE interval is directly editable from the MITE entry");
            interval.setValue("0");
            check(!findButton(rules,"mite.config.save").active,"A zero interval disables saving");
            auditSpawningDraftNavigation(rules,width,height,"mite-entry-ite-interval");
            check(!findButton(rules,"mite.config.save").active,"The invalid interval remains blocking after the actual available page navigation");
            showIteSpawningField(rules,key);
            check(findEdit(rules,"modern_ite.config."+key).getValue().equals("0"),"Pagination preserves the invalid interval draft");
            findEdit(rules,"modern_ite.config."+key).setValue(Integer.toString(edited));
            check(findButton(rules,"mite.config.save").active,"Correcting the interval enables saving");
            capture(mc,rules,width,height,"mite-spawning-rules-interval",-1,-1);bounds(rules);
            press(rules,"mite.config.save");
            check(mc.screen==rates,"Saving from the MITE entry returns to its refresh-rate parent");
            check(IteConfig.integer(key)==edited,"The unified interval is immediately published to gameplay");
            var parsed=new TomlParser().parse(Files.readString(configFile));
            check(((Number)parsed.get(List.of("GameMechanics","MobSpawning",key))).intValue()==edited,
                    "The unified spawning editor persists the interval to ITE's TOML");
            rates.onClose();
            check(mc.screen==home,"Closing the MITE refresh-rate browser restores its home");
            report.addProperty("mite_spawning_entry_uses_unified_ite_editor_and_interval",true);
        } finally {IteConfig.setting(key).set(original);IteConfig.SPEC.save();IteConfig.refresh();}
    }

    private static EditBox findEdit(Screen screen, String key) {
        String name = Component.translatable(key).getString();
        return widgets(screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing edit box " + key));
    }

    private static Button findButton(Screen screen, String key) {
        String name = Component.translatable(key).getString();
        return widgets(screen).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing button " + key));
    }

    private static AbstractSelectionList<?> valuesList(Screen screen) {
        return (AbstractSelectionList<?>) widgets(screen).stream().filter(AbstractSelectionList.class::isInstance)
                .findFirst().orElseThrow();
    }

    private static CycleButton<?> firstCycleButton(Screen screen) {
        return (CycleButton<?>) widgets(screen).stream().filter(widget -> widget instanceof CycleButton<?> && widget.active)
                .findFirst().orElseThrow();
    }

    private static void click(Screen screen, AbstractWidget widget) {
        check(widget.active && screen.mouseClicked(widget.getX() + widget.getWidth() / 2D,
                        widget.getY() + widget.getHeight() / 2D, 0),
                "The actual widget responds to a mouse click: " + widget.getMessage().getString());
    }

    private static void checkDouble(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1E-9, message + ": " + actual + " != " + expected);
    }

    private static List<AbstractWidget> widgets(ContainerEventHandler container) {
        var result = new ArrayList<AbstractWidget>();
        for (GuiEventListener child : container.children()) {
            if (child instanceof AbstractWidget widget) result.add(widget);
            if (child instanceof ContainerEventHandler nested) result.addAll(widgets(nested));
        }
        return result;
    }

    private static void press(Screen screen, String key) {
        String text = Component.translatable(key).getString();
        widgets(screen).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.active && button.getMessage().getString().equals(text)).findFirst().orElseThrow(
                        () -> new IllegalStateException("Missing active button " + key)).onPress();
    }

    private static void bounds(Screen screen) {
        var buttons = new ArrayList<Button>();
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                    "Widget escapes " + screen.width + "x" + screen.height + ": " + widget.getMessage().getString());
            if (widget instanceof Button button) buttons.add(button);
        }
        for (int first = 0; first < buttons.size(); first++) for (int second = first + 1; second < buttons.size(); second++) {
            var a = buttons.get(first); var b = buttons.get(second);
            check(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                    "Buttons overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
        }
    }

    private static void capture(Minecraft mc, Screen screen, int width, int height, String name, int mouseX, int mouseY) throws Exception {
        TextureTarget target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        var originalTarget = mc.getMainRenderTarget();
        var targetField = Minecraft.class.getDeclaredField("mainRenderTarget");
        targetField.setAccessible(true);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var window = mc.getWindow();
        int oldWidth = window.getWidth(), oldHeight = window.getHeight();
        double scale = window.getGuiScale();
        int originalBlur = mc.options.menuBackgroundBlurriness().get();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            // Screen.renderBlurredBackground binds Minecraft's main target again after
            // the panorama. Keep that native binding pointed at this audit framebuffer;
            // the cached blur chain still belongs to the original target, so skip blur.
            targetField.set(mc, target);
            mc.options.menuBackgroundBlurriness().set(0);
            window.setWidth(width); window.setHeight(height); window.setGuiScale(1);
            screen.init(mc, width, height);
            target.setClearColor(.06F, .09F, .12F, 1F); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            float farPlane = ClientHooks.getGuiFarPlane();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, 1000, farPlane), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.translation(0, 0, 10000F - farPlane);
            RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1);
            Lighting.setupFor3DItems();
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            screen.render(graphics, mouseX, mouseY, 0); graphics.flush(); mc.renderBuffers().bufferSource().endBatch();
            Path path = output().getParent().resolve(name + "-" + width + "x" + height + ".png");
            Files.createDirectories(path.getParent());
            int titlePixels = 0;
            try (var image = Screenshot.takeScreenshot(target)) {
                image.writeToFile(path);
                for (int y = 10; y < Math.min(31, height); y++) for (int x = 16; x < width - 16; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    if ((pixel & 255) > 220 && (pixel >>> 8 & 255) > 220 && (pixel >>> 16 & 255) > 220) titlePixels++;
                }
            }
            check(titlePixels >= 20, "Native title text must be present in the captured framebuffer: " + name);
            JsonObject shot = new JsonObject(); shot.addProperty("screen", screen.getClass().getName());
            shot.addProperty("width", width); shot.addProperty("height", height); shot.addProperty("screenshot", path.toString());
            shot.addProperty("title_bright_pixels", titlePixels);
            report.getAsJsonArray("screenshots").add(shot);
        } finally {
            mc.renderBuffers().bufferSource().endBatch(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); RenderSystem.enableDepthTest(); Lighting.setupFor3DItems();
            window.setWidth(oldWidth); window.setHeight(oldHeight); window.setGuiScale(scale);
            mc.options.menuBackgroundBlurriness().set(originalBlur);
            targetField.set(mc, originalTarget);
            originalTarget.bindWrite(true); target.destroyBuffers();
        }
    }

    private static JsonObject initialReport() {
        var result = new JsonObject(); result.addProperty("author", "NingMeng123"); result.addProperty("timestamp", Instant.now().toString());
        result.addProperty("scope", "Unified MITE config factory and categories, ITE config and spawn editing, separate ore multipliers with cross-dimension drafts and native saves, GPU screenshots at 320x240 and 854x480, and byte-exact restoration of both COMMON files");
        result.addProperty("world_created", false); result.addProperty("server_connected", false);
        result.add("failures", new JsonArray()); result.add("screenshots", new JsonArray()); return result;
    }

    private static void fail(String label, Throwable failure) {
        if (report == null) report = initialReport();
        report.getAsJsonArray("failures").add(label + (failure == null ? "" : ": " + failure));
        LogUtils.getLogger().error("MODERN_ITE_CONFIG_AUDIT {}", label, failure);
    }

    private static void finish(Minecraft mc, boolean stop) {
        if (finished) return;
        finished = true;
        if (report == null) report = initialReport();
        try {
            if (originalValues != null) {
                originalValues.forEach((key, value) -> IteConfig.setting(key).set(value));
                IteSpawnConfig.NATURAL_SPAWN_RATES.set(originalSpawnRates);
                IteSpawnConfig.NATURAL_SPAWN_WEIGHTS.set(originalSpawnWeights);
                IteConfig.SPEC.save();
                if (originalFile != null) Files.write(configFile, originalFile);
                else Files.deleteIfExists(configFile);
                IteConfig.refresh();
                check(IteConfig.localSnapshot().equals(originalValues)
                                && IteSpawnConfig.NATURAL_SPAWN_RATES.get().equals(originalSpawnRates)
                                && IteSpawnConfig.NATURAL_SPAWN_WEIGHTS.get().equals(originalSpawnWeights)
                                && (originalFile == null ? !Files.exists(configFile) : Arrays.equals(originalFile, Files.readAllBytes(configFile))),
                        "The original config values and file must be fully restored");
                report.addProperty("original_config_restored", true);
            }
        } catch (Throwable failure) { fail("Restore isolated config", failure); }
        finally {
            try { restoreBaseConfig(); }
            catch (Throwable failure) { fail("Restore isolated MITE config", failure); }
            if (originalLanguage != null) {
                mc.getLanguageManager().setSelected(originalLanguage);
                mc.options.languageCode = originalLanguage;
            }
        }
        report.addProperty("status", report.getAsJsonArray("failures").isEmpty() ? "passed" : "failed");
        report.addProperty("safe_client_shutdown_requested", stop);
        try {
            Path path = output(); Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            LogUtils.getLogger().info("MODERN_ITE_CONFIG_AUDIT {} report={}", report.get("status").getAsString(), path);
        } catch (Exception failure) { LogUtils.getLogger().error("Cannot write config audit report", failure); }
        if (stop) mc.stop();
    }

    private static void restoreBaseConfig() throws Exception {
        if (originalBaseSnapshot == null || originalBaseValues.isEmpty()) return;
        originalBaseValues.forEach(IteConfigClientAudit::restoreBaseValue);
        MiteConfig.clearServerSnapshot();
        MiteConfig.refreshEnchantmentRules();
        MiteConfig.SPEC.save();
        if (originalBaseFile != null) Files.write(baseConfigFile, originalBaseFile);
        else Files.deleteIfExists(baseConfigFile);
        MiteConfig.refreshEnchantmentRules();
        check(MiteConfig.localSnapshot().equals(originalBaseSnapshot)
                        && (originalBaseFile == null ? !Files.exists(baseConfigFile) : Arrays.equals(originalBaseFile, Files.readAllBytes(baseConfigFile))),
                "The original complete MITE snapshot and raw mite-common.toml must both be restored");
        if (originalBaseEffective != originalBaseSnapshot) MiteConfig.receiveServerSnapshot(originalBaseEffective);
        report.addProperty("original_mite_config_restored", true);
        report.addProperty("original_mite_snapshot_restored", true);
    }

    @SuppressWarnings("unchecked")
    private static void restoreBaseValue(ModConfigSpec.ConfigValue<?> setting, Object value) {
        ((ModConfigSpec.ConfigValue<Object>)setting).set(value);
    }

    private static Path output() {
        return Path.of(System.getProperty("modern_ite.configAuditOutput", "build/verification/config/client-audit.json")).toAbsolutePath().normalize();
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
