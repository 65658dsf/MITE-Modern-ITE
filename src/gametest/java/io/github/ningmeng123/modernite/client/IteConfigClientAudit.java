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
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.config.client.IteConfigValuesScreen;
import io.github.ningmeng123.modernite.config.client.IteSettingsScreen;
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
            press(main, "modern_ite.config.open");
            check(mc.screen instanceof IteSettingsScreen, "The injected button must open ITE settings");
            Screen home = mc.screen;
            capture(mc, home, width, height, "ite-settings", -1, -1);
            bounds(home);
            press(home, "modern_ite.config.monster_spawns");
            check(mc.screen instanceof MonsterSpawnSettingsScreen, "ITE's spawn button must open the shared registry browser");
            Screen spawns = mc.screen;
            capture(mc, spawns, width, height, "ite-natural-spawns", -1, -1);
            bounds(spawns);
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
            spawns.onClose();
            check(mc.screen == home, "Closing the spawn browser must restore ITE settings");
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
        check(widgets(ores).stream().filter(EditBox.class::isInstance).count() == 2,
                "A filtered ore row must expose only search and one multiplier input");
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
    }

    private static void checkOreSave() throws Exception {
        checkDouble(MiteConfig.oreMultiplier(R196OreRules.Kind.COPPER, R196OreRules.Dimension.OVERWORLD), 2D,
                "Saved surface multiplier is published to gameplay");
        checkDouble(MiteConfig.underworldOreMultiplier(R196OreRules.Kind.COPPER), .5D, "Saved deep multiplier is published to gameplay");
        var parsed = new TomlParser().parse(Files.readString(baseConfigFile));
        checkDouble(((Number)parsed.get(List.of("overworld_ores", "copper"))).doubleValue(), 2D, "Native TOML keeps the surface multiplier");
        checkDouble(((Number)parsed.get(List.of("underworld_ores", "copper"))).doubleValue(), .5D, "Native TOML keeps the separate deep multiplier");
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
                IteConfig.SPEC.save();
                if (originalFile != null) Files.write(configFile, originalFile);
                else Files.deleteIfExists(configFile);
                IteConfig.refresh();
                check(IteConfig.localSnapshot().equals(originalValues)
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
