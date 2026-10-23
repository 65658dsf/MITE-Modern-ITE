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
import io.github.ningmeng123.mite.client.config.MiteSettingsScreen;
import io.github.ningmeng123.mite.client.config.MonsterSpawnSettingsScreen;
import io.github.ningmeng123.mite.client.config.OreGenerationSettingsScreen;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.config.client.IteConfigValuesScreen;
import io.github.ningmeng123.modernite.config.client.IteSettingsScreen;
import io.github.ningmeng123.mite.client.config.MonsterSpawningRulesScreen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.joml.Matrix4f;

/** Explicit title-only GUI audit in the isolated config-audit launch. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class IteConfigClientAudit {
    private static final String SAMPLE = "conqueror_damage_boost_per_lvl";
    private static boolean finished, languageReloadRequested, started;
    private static int ticks, saveTicks;
    private static JsonObject report;
    private static Map<String, Object> originalValues;
    private static List<? extends String> originalSpawnRates, originalSpawnWeights;
    private static Path configFile;
    private static byte[] originalFile;
    private static String originalLanguage;
    private static double expected;
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
                if (++saveTicks < 10) return;
                check(Math.abs(IteConfig.number(SAMPLE) - expected) < 1E-9,
                        "Native settings save must publish the new effective value");
                String written = Files.readString(configFile);
                check(written.contains(SAMPLE + " = " + expected), "Saved TOML must contain the edited value");
                report.addProperty("native_edit_saved_to_toml", true);
                report.addProperty("native_edit_published_to_gameplay", true);
                checkOreSave();
                finish(mc, true);
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
            audit(mc);
        } catch (Throwable failure) {
            fail("Config GUI audit", failure);
            finish(mc, true);
        }
    }

    private static void audit(Minecraft mc) throws Exception {
        Screen title = mc.screen;
        var mite = ModList.get().getModContainerById("mite").orElseThrow();
        var ite = ModList.get().getModContainerById(ModernIte.MOD_ID).orElseThrow();
        check(IConfigScreenFactory.getForMod(ite.getModInfo()).orElseThrow()
                .createScreen(ite, title) instanceof IteSettingsScreen, "ITE's native config factory must be registered");
        report.addProperty("native_config_factory_registered", true);
        for (int[] size : new int[][] {{320, 240}, {854, 480}}) {
            int width = size[0], height = size[1];
            Screen main = IConfigScreenFactory.getForMod(mite.getModInfo()).orElseThrow().createScreen(mite, title);
            check(main instanceof MiteSettingsScreen, "The original MITE factory must return its actual settings screen");
            mc.setScreen(main);
            capture(mc, main, width, height, "mite-settings", -1, -1);
            bounds(main);
            auditBaseSpawningRules(mc,main,width,height);
            press(main, "modern_ite.config.open");
            check(mc.screen instanceof IteSettingsScreen, "The injected button must open ITE settings");
            Screen home = mc.screen;
            capture(mc, home, width, height, "ite-settings", -1, -1);
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
            findEdit(spawns,"mite.config.monster_spawn_weight").setValue("80");
            capture(mc,spawns,width,height,"ite-natural-spawn-weight-edited",-1,-1);bounds(spawns);
            press(spawns,"mite.config.save");
            check(mc.screen == home,"Saving the single weight editor must restore ITE settings");
            check(IteSpawnConfig.localWeights().get("modern_ite:zombie_chaos")==80D
                            && !IteSpawnConfig.localSnapshot().containsKey("modern_ite:zombie_chaos"),
                    "Writing weight 80 must publish 80 and remove that entity's legacy multiplier");
            check(io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns.weight(ExtremeMobs.type(ExtremeKind.ZOMBIE_CHAOS),25D)==80D
                            && io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns.multiplier(ExtremeMobs.type(ExtremeKind.ZOMBIE_CHAOS))==1D,
                    "The actual runtime must read the written weight directly with no hidden legacy multiplier");
            auditSpawningRules(mc, home, width, height);
            press(home, "modern_ite.config.values");
            check(mc.screen instanceof IteConfigValuesScreen, "The values button must open the effective settings browser");
            Screen values = mc.screen;
            values.init(mc, width, height);
            EditBox search = widgets(values).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            search.setValue("征服者");
            var count = IteConfigValuesScreen.class.getDeclaredField("matchCount");
            count.setAccessible(true);
            check(count.getInt(values) == 1, "Searching 征服者 must find exactly the implemented conqueror setting");
            capture(mc, values, width, height, "conqueror-search", -1, -1);
            capture(mc, values, width, height, "conqueror-tooltip", width / 2, 112);
            bounds(values);
            values.onClose();
            check(mc.screen == home, "Closing the browser must restore its actual parent");
        }
        report.addProperty("mite_entry_button_clicked", true);
        report.addProperty("effective_values_search_verified", true);
        report.addProperty("ite_registry_spawn_browser_and_native_weights_verified", true);
        report.addProperty("ite_single_weight_editor_clears_edited_legacy_multiplier",true);
        report.addProperty("ite_spawning_rules_paging_validation_drafts_and_save_verified", true);
        report.addProperty("mite_spawning_rules_entry_paging_validation_drafts_and_save_verified", true);
        report.addProperty("minimum_and_large_widget_bounds_verified", true);
        auditOres(mc, title);
        var home = new IteSettingsScreen(ite, title);
        mc.setScreen(home);
        press(home, "modern_ite.config.edit");
        check(mc.screen instanceof ConfigurationScreen || mc.screen instanceof ConfigurationScreen.ConfigurationSectionScreen,
                "Edit must open NeoForge's actual configuration screen");
        if (mc.screen instanceof ConfigurationScreen) {
            Button common = widgets(mc.screen).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.active && button.getWidth() == ConfigurationScreen.BIG_BUTTON_WIDTH).findFirst().orElseThrow();
            common.onPress();
        }
        check(mc.screen instanceof ConfigurationScreen.ConfigurationSectionScreen, "COMMON settings must be editable");
        Screen commonRoot=mc.screen;
        section(mc.screen,"GameMechanics").onPress();
        section(mc.screen,"MobSpawning").onPress();
        String startDayKey="extreme_mob_spawn_limit_day";
        EditBox startDay=findEdit(mc.screen,"modern_ite.config."+startDayKey);
        int startDayBefore=IteConfig.integer(startDayKey);
        int startDayEdited=startDayBefore==1000000?startDayBefore-1:startDayBefore+1;
        check(startDay.active,"The native common-config editor exposes the extreme monster start day");
        startDay.setValue(Integer.toString(startDayEdited));
        check(((Number)IteConfig.setting(startDayKey).get()).intValue()==startDayEdited,
                "Editing the native start-day box updates the actual ConfigValue");
        startDay.setValue(Integer.toString(startDayBefore));
        mc.screen.onClose();mc.screen.onClose();
        check(mc.screen==commonRoot,"Closing the nested spawning sections returns to COMMON settings");
        report.addProperty("native_extreme_start_day_number_box_edited",true);
        section(mc.screen, "Item").onPress();
        section(mc.screen, "Enchantment").onPress();
        EditBox conqueror = widgets(mc.screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(Component.translatable("modern_ite.config." + SAMPLE).getString()))
                .findFirst().orElseThrow(() -> new IllegalStateException("The native conqueror number box is missing"));
        expected = ((Number)originalValues.get(SAMPLE)).doubleValue() == 2D ? 3D : 2D;
        conqueror.setValue(Double.toString(expected));
        check(((Number)IteConfig.setting(SAMPLE).get()).doubleValue() == expected, "The native number box must update the real ConfigValue");
        capture(mc, mc.screen, 320, 240, "native-enchantments-edited", -1, -1);
        bounds(mc.screen);
        capture(mc, mc.screen, 854, 480, "native-enchantments-edited", -1, -1);
        bounds(mc.screen);
        for (int index = 0; index < 8 && mc.screen != home; index++) mc.screen.onClose();
        check(mc.screen == home, "Closing native sections must save COMMON and return to ITE settings");
        report.addProperty("native_enchantment_number_box_edited", true);
        report.addProperty("edited_setting", SAMPLE);
        report.addProperty("edited_value", expected);
    }

    private static void auditOres(Minecraft mc, Screen title) throws Exception {
        var mite = ModList.get().getModContainerById("mite").orElseThrow();
        Screen parent = IConfigScreenFactory.getForMod(mite.getModInfo()).orElseThrow().createScreen(mite, title);
        mc.setScreen(parent);
        press(parent, "mite.config.ore_generation");
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
        check(findEdit(ores, "mite.config.ore_multiplier").getValue().equals("1"), "The ore reset button restores the multiplier to one");
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
        check(MiteConfig.localSnapshot() == originalBaseSnapshot, "Draft changes cannot reach gameplay before saving");
        for (var dimension : List.of(R196OreRules.Dimension.OVERWORLD, R196OreRules.Dimension.UNDERWORLD)) {
            selectOreDimension(ores, dimension);
            for (int[] size : new int[][] {{320, 240}, {854, 480}}) {
                capture(mc, ores, size[0], size[1], "ore-" + dimension.name().toLowerCase(java.util.Locale.ROOT) + "-multiplier", -1, -1);
                bounds(ores);
                checkDouble(Double.parseDouble(findEdit(ores, "mite.config.ore_multiplier").getValue()),
                        dimension == R196OreRules.Dimension.OVERWORLD ? 2D : .5D, "Draft survives resizing and remains in the correct dimension");
            }
        }
        press(ores, "mite.config.save");
        check(mc.screen == parent, "Saving both dimension multipliers returns to the actual MITE settings parent");
        check(MiteConfig.localSnapshot().oreCandidates().equals(originalBaseSnapshot.oreCandidates())
                        && MiteConfig.localSnapshot().oreChances().equals(originalBaseSnapshot.oreChances())
                        && MiteConfig.localSnapshot().overworldOreCandidates().equals(originalBaseSnapshot.overworldOreCandidates())
                        && MiteConfig.localSnapshot().overworldOreChances().equals(originalBaseSnapshot.overworldOreChances()),
                "Simple multiplier editing must preserve all existing advanced candidate and probability settings");
        report.addProperty("ore_multiplier_ui_clicked", true);
        report.addProperty("ore_zero_half_double_drafts_survived_dimension_switches", true);
        report.addProperty("ore_invalid_hidden_draft_blocked_save", true);
        report.addProperty("ore_reset_restores_one", true);
        report.addProperty("ore_both_dimension_drafts_saved", true);
        report.addProperty("ore_advanced_settings_preserved", true);
        report.addProperty("ore_height_drafts_validated_and_saved", true);
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
        report.addProperty("ore_multiplier_saved_to_toml", true);
        report.addProperty("ore_multiplier_published_to_gameplay", true);
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
        Button button = findButton(screen, "mite.config.ore_dimension." + dimension.name().toLowerCase(java.util.Locale.ROOT));
        if (button.active) button.onPress();
        check(!button.active, "The selected ore dimension must be visibly marked");
    }

    private static void auditSpawningRules(Minecraft mc, Screen home, int width, int height) throws Exception {
        String key="mob_max_spawn_count_chunk_base";
        String dayKey="extreme_mob_spawn_limit_day";
        int original=IteConfig.integer(key);
        int originalDay=IteConfig.integer(dayKey);
        int edited=original==1000000?original-1:original+1;
        int editedDay=originalDay==1000000?originalDay-1:originalDay+1;
        try {
            check(!IteConfig.defaults().containsKey("reference_spawning"),"The removed spawning preset has no schema entry");
            press(home,"modern_ite.config.spawning_rules");
            check(mc.screen instanceof MonsterSpawningRulesScreen,"The ITE spawning shortcut opens the actual main-mod rules editor");
            Screen rules=mc.screen;
            capture(mc,rules,width,height,"ite-spawning-rules",-1,-1);bounds(rules);
            var dayField=findEdit(rules,"modern_ite.config."+dayKey);
            check(dayField.active&&widgets(rules).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).noneMatch(field->field.getY()<dayField.getY()),"The main-mod page begins with the editable extreme start day without a preset switch");
            dayField.setValue("0");
            check(!findButton(rules,"mite.config.save").active,"Day zero is invalid");
            dayField.setValue(Integer.toString(editedDay));
            check(findButton(rules,"mite.config.save").active,"Correcting the date restores saving");
            showIteSpawningField(rules,"local_hostile_limit");
            check(findEdit(rules,"modern_ite.config.local_hostile_limit").active,"Splitting and support density remains editable");
            showIteSpawningField(rules,key);
            check(findEdit(rules,"modern_ite.config."+key).active,"The ITE default natural cap is directly editable");
            findEdit(rules,"modern_ite.config."+key).setValue("-1");
            check(!findButton(rules,"mite.config.save").active,"Negative cap blocks saving");
            press(rules,">");bounds(rules);
            check(!findButton(rules,"mite.config.save").active,"Invalid drafts remain blocking on other pages");
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
            press(rules,"mite.config.save");
            check(mc.screen==home&&IteConfig.integer(key)==edited&&IteConfig.integer(dayKey)==editedDay,
                    "Saving publishes the edited cap and extreme start date together and restores the parent");
            var parsed=new TomlParser().parse(Files.readString(configFile));
            check(((Number)parsed.get(List.of("GameMechanics","MobSpawning",key))).intValue()==edited
                            &&!parsed.contains(List.of("GameMechanics","MobSpawning","reference_spawning")),
                    "The editor persists the editable cap without a preset key");
            check(((Number)parsed.get(List.of("GameMechanics","MobSpawning",dayKey))).intValue()==editedDay,
                    "The editor persists the edited extreme start date");
            report.addProperty("ite_spawning_all_values_editable_without_preset",true);
            report.addProperty("ite_spawning_paging_preserves_and_validates_drafts",true);
            report.addProperty("ite_extreme_start_day_first_page_validation_and_save",true);
        } finally {
            IteConfig.setting(key).set(original);IteConfig.setting(dayKey).set(originalDay);
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
            press(rules,">");bounds(rules);
            check(!findButton(rules,"mite.config.save").active,"The invalid interval remains blocking on another page");
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

    private static Button section(ContainerEventHandler container, String category) {
        String title = Component.translatable("modern_ite.configuration." + category).getString();
        boolean label = container.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .anyMatch(widget -> widget.getMessage().getString().contains(title));
        if (label) for (var child : container.children()) if (child instanceof Button button && button.active) return button;
        for (var child : container.children()) if (child instanceof ContainerEventHandler nested) {
            Button found = sectionOrNull(nested, category);
            if (found != null) return found;
        }
        throw new IllegalStateException("Missing native category " + category);
    }

    private static Button sectionOrNull(ContainerEventHandler container, String category) {
        try { return section(container, category); } catch (IllegalStateException ignored) { return null; }
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
        result.addProperty("scope", "Actual config factories, MITE/ITE navigation, separate ore multipliers with cross-dimension drafts and native saves, conqueror search, GPU screenshots at 320x240 and 854x480, and byte-exact restoration of both COMMON files");
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
