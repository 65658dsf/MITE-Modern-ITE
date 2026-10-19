package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingContent;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingEntity;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingMenu;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingRecipe;
import io.github.ningmeng123.modernite.integration.jei.MetalUpgradingJeiPlugin;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in real server menus and JEI GPU frames in a newly created, isolated world. @author NingMeng123 */
public final class MetalUpgradingClientAudit {
    private static boolean baked, started, finished, languageRequested;
    private static int waiting, readyTicks, firstServerTick = -1;
    private static String worldName;
    private static JsonObject report;
    private static WaitingScreen waitingScreen;
    private static Steps steps;
    private MetalUpgradingClientAudit() {}

    @EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event) {
            if (Boolean.getBoolean("modern_ite.metalAudit") && ModList.get().isLoaded("jei")) baked = true;
        }
    }
    /** FML may reflect every subscriber method even when the opt-in flag is false.
     * Keep this entry class free of optional types and load the implementation only
     * after both guards pass. @author NingMeng123 */
    @EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
    public static final class Trigger {
        private Trigger() {}
        private static boolean active() { return Boolean.getBoolean("modern_ite.metalAudit") && ModList.get().isLoaded("jei"); }
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) { if (active()) MetalUpgradingClientAudit.tick(event); }
        @SubscribeEvent public static void frame(RenderFrameEvent.Post event) { if (active()) MetalUpgradingClientAudit.frame(event); }
    }
    private static boolean enabled() { return Boolean.getBoolean("modern_ite.metalAudit"); }

    private static void tick(ClientTickEvent.Post event) {
        if (!enabled() || finished || !baked) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (report == null) report = initial();
            if (!started) {
                // Fail before touching any world, connection, option or language in another launch.
                check(isolated(mc),
                        "Metal audit requires the separate run-metal-audit game directory");
                check(mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null,
                        "Refused an existing world or server connection");
                if (mc.getOverlay() != null) return;
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                check(ModList.get().isLoaded("jei"), "The isolated metal audit requires the real optional JEI runtime");
                if (!languageRequested && !mc.getLanguageManager().getSelected().equals("zh_cn")) {
                    languageRequested = true; mc.getLanguageManager().setSelected("zh_cn"); mc.options.languageCode = "zh_cn";
                    mc.reloadResourcePacks(); return;
                }
                check(mc.getLanguageManager().getSelected().equals("zh_cn"), "Chinese resources must finish loading before world creation");
                check(Component.translatable("block.modern_ite.metal_upgrading_furnace").getString().equals("金属升级炉"),
                        "The production Chinese metal-furnace title is translated");
                mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5);
                mc.options.guiScale().set(1); mc.resizeDisplay();
                worldName = "modern-ite-metal-audit-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
                report.addProperty("disposable_world", worldName); report.addProperty("language", "zh_cn");
                started = true;
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(562761234L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++waiting < 4800, "Timed out waiting for the new isolated world, menu synchronization or JEI");
            if (mc.getOverlay() != null || mc.level == null || mc.player == null || mc.getConnection() == null || mc.getSingleplayerServer() == null) return;
            check(owned(mc), "Only the newly created metal audit world may be inspected or changed");
            if (firstServerTick < 0) firstServerTick = mc.getSingleplayerServer().getTickCount();
            readyTicks++;
            if (waitingScreen == null) { waitingScreen = new WaitingScreen(); mc.setScreen(waitingScreen); }
            if (readyTicks < 100 || mc.getSingleplayerServer().getTickCount() - firstServerTick < 100) return;
            if (steps == null) {
                Object optional = Class.forName("mezz.jei.common.Internal").getMethod("getOptionalJeiRuntime").invoke(null);
                if (!(optional instanceof Optional<?> runtime) || runtime.isEmpty()) return;
                report.addProperty("server_ready_ticks_before_audit", mc.getSingleplayerServer().getTickCount() - firstServerTick);
                steps = new Steps(mc, runtime.get());
            }
            steps.tick(mc);
        } catch (Throwable failure) { fail("Metal client lifecycle", failure); finish(mc); }
    }

    private static void frame(RenderFrameEvent.Post event) {
        if (!enabled() || finished || steps == null) return;
        try { steps.frame(Minecraft.getInstance()); }
        catch (Throwable failure) { fail("Metal native rendered frame", failure); finish(Minecraft.getInstance()); }
    }

    /** JEI classes are reached only when the opt-in isolated launch has the actual runtime. */
    private static final class Steps {
        private final IJeiRuntime runtime;
        private final RecipeType<MetalUpgradingJeiPlugin.Entry> type;
        private final IRecipeCategory<MetalUpgradingJeiPlugin.Entry> category;
        private final List<MetalUpgradingJeiPlugin.Entry> entries;
        private CompletableFuture<ServerFacts> prepared, later;
        private ServerFacts current;
        private MetalUpgradingScreen metalScreen;
        private Screen expectedScreen;
        private int phase, stageIndex, syncTicks, firstClientProgress, frames;
        private JsonObject currentFacts;

        @SuppressWarnings("unchecked") Steps(Minecraft mc, Object actualRuntime) {
            runtime = (IJeiRuntime)actualRuntime;
            var manager = runtime.getRecipeManager();
            type = (RecipeType<MetalUpgradingJeiPlugin.Entry>)manager.getRecipeType(ResourceLocation.parse("modern_ite:metal_upgrading")).orElseThrow();
            category = manager.getRecipeCategory(type);
            check(category != null && category.getRecipeType().equals(type), "The real JEI registry has the independent metal-upgrading category");
            entries = manager.createRecipeLookup(type).includeHidden().get().sorted(java.util.Comparator.comparing(entry -> entry.id().toString())).toList();
            check(entries.size() == 2, "The real category contains exactly the two independent metal upgrade recipes");
            Set<ResourceLocation> loaded = new HashSet<>();
            mc.level.getRecipeManager().getAllRecipesFor(MetalUpgradingContent.RECIPE_TYPE.get()).forEach(holder -> loaded.add(holder.id()));
            check(loaded.equals(entries.stream().map(MetalUpgradingJeiPlugin.Entry::id).collect(java.util.stream.Collectors.toSet())),
                    "JEI indexes exactly the two real synchronized server recipe holders");
            var catalysts = manager.createRecipeCatalystLookup(type).includeHidden().getItemStack().toList();
            check(catalysts.size() == 1 && catalysts.getFirst().is(MetalUpgradingContent.FURNACE_ITEM.get()),
                    "The dedicated metal furnace is the only registered category catalyst");
            JsonArray recipes = new JsonArray(); report.add("jei_recipes", recipes);
            for (var entry : entries) {
                var layout = manager.createRecipeLayoutDrawable(category, entry, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                var slots = layout.getRecipeSlotsView();
                check(slots.getSlotViews(RecipeIngredientRole.INPUT).stream().anyMatch(slot -> contains(slot, entry.recipe().input())),
                        "The real JEI layout includes the configured complete metal-block input");
                check(slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream().anyMatch(slot -> contains(slot, entry.recipe().output())),
                        "The real JEI layout includes the actual next-metal output");
                check(slots.getSlotViews(RecipeIngredientRole.INPUT).stream().anyMatch(slot ->
                                entry.recipe().fuelOptions().stream().allMatch(fuel -> contains(slot, fuel))),
                        "The real JEI fuel slot contains every executable counted fuel alternative");
                var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, entry.recipe().output());
                var found = manager.createRecipeLookup(type).includeHidden().limitFocus(List.of(focus)).get().toList();
                check(found.size() == 1 && found.getFirst().id().equals(entry.id()), "Output R lookup finds the exact real independent conversion");
                JsonObject row = new JsonObject(); row.addProperty("id", entry.id().toString());
                row.addProperty("input", itemId(entry.recipe().input())); row.addProperty("input_count", entry.recipe().inputCount());
                row.addProperty("output", itemId(entry.recipe().output())); row.addProperty("cooking_ticks", entry.recipe().cookingTicks());
                row.addProperty("required_heat", entry.recipe().requiredHeat()); row.addProperty("fuel_alternatives", entry.recipe().fuelOptions().size());
                row.addProperty("native_layout_and_output_lookup_verified", true); recipes.add(row);
            }
            var use = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK,
                    new ItemStack(MetalUpgradingContent.FURNACE_ITEM.get()));
            check(manager.createRecipeLookup(type).includeHidden().limitFocus(List.of(use)).get().count() == 2,
                    "Actual furnace U lookup returns both upgrades");
            report.addProperty("registered_jei_recipes", 2); report.addProperty("registered_jei_catalysts", 1);
            report.addProperty("native_jei_recipe_layouts_verified", 2); report.addProperty("catalyst_uses_lookup_verified", true);
            prepared = prepare(mc, entries.getFirst().recipe());
        }

        void tick(Minecraft mc) {
            if (phase != 0 || !prepared.isDone()) return;
            if (current == null) current = prepared.join();
            if (!(mc.screen instanceof MetalUpgradingScreen actual) || actual.getMenu().containerId != current.containerId()) return;
            MetalUpgradingMenu menu = actual.getMenu();
            if (menu.total() != current.total() || menu.progress() < current.seedProgress() || menu.data.get(10) != current.inputCount()) return;
            check(mc.player.containerMenu == menu, "The displayed menu is the client's actual packet-opened player container");
            check(menu.getSlot(0).getItem().is(entries.get(stageIndex).recipe().input().getItem())
                            && menu.getSlot(0).getItem().getCount() == current.inputCount(), "The real server metal input is synchronized to the native screen");
            check(menu.getSlot(1).getItem().is(ExtremeContent.BLAZE_COAL.get()) && menu.getSlot(1).getItem().getCount() == 1,
                    "The actual remaining fuel stack is synchronized after one fuel was ignited");
            check(menu.getSlot(2).getItem().isEmpty() && menu.data.get(8) == 5 && menu.data.get(9) == current.requiredHeat(),
                    "Output, current fuel heat and stage-specific required heat reach the real menu");
            if (syncTicks++ == 0) { firstClientProgress = menu.progress(); metalScreen = actual; }
            if (syncTicks < 12 || menu.progress() <= firstClientProgress + 2) return;
            if (later == null) { later = snapshot(mc, current.pos()); return; }
            if (!later.isDone()) return;
            ServerFacts server = later.join();
            check(server.progress() > current.seedProgress() && menu.progress() > firstClientProgress,
                    "Natural server ticks advance the real container after the explicitly seeded screenshot starting point");
            check(Math.abs(server.progress() - menu.progress()) <= 12, "Client progress stays within network latency of an independent server-thread snapshot");
            currentFacts = new JsonObject(); currentFacts.addProperty("stage", entries.get(stageIndex).recipe().stage().getSerializedName());
            currentFacts.addProperty("container_id", menu.containerId); currentFacts.addProperty("server_progress_snapshot", server.progress());
            currentFacts.addProperty("client_progress", menu.progress()); currentFacts.addProperty("client_first_progress", firstClientProgress);
            currentFacts.addProperty("configured_total_ticks", menu.total()); currentFacts.addProperty("input_count", current.inputCount());
            currentFacts.addProperty("heat", menu.data.get(8)); currentFacts.addProperty("required_heat", menu.data.get(9));
            currentFacts.addProperty("seeded_screenshot_progress", current.seedProgress()); currentFacts.addProperty("native_packet_menu_and_progress_verified", true);
            report.getAsJsonArray("native_menus").add(currentFacts);
            expectedScreen = metalScreen; frames = 0; phase = 1; movePointerAway(mc);
        }

        void frame(Minecraft mc) throws Exception {
            if (phase == 0 || mc.getOverlay() != null || mc.screen != expectedScreen || ++frames < 3) return;
            if (phase == 1) {
                Path image = screenshot(mc, "metal-" + entries.get(stageIndex).recipe().stage().getSerializedName() + "-zh_cn.png", true);
                currentFacts.addProperty("screenshot", image.toString());
                if (stageIndex == 0) {
                    stageIndex = 1; phase = 0; syncTicks = 0; current = null; later = null;
                    prepared = prepare(mc, entries.get(1).recipe()); return;
                }
                var areas = runtime.getScreenHelper().getGuiClickableArea(metalScreen, 93, 44).toList();
                check(areas.size() == 1, "The real screen helper exposes exactly one metal-upgrade arrow action");
                var rect = areas.getFirst().getArea();
                check(rect.getX() == 78 && rect.getY() == 36 && rect.getWidth() == 30 && rect.getHeight() == 16,
                        "The real registered click rectangle covers the production progress arrow");
                check(runtime.getScreenHelper().getGuiClickableArea(metalScreen, 55, 28).findAny().isEmpty(),
                        "The input slot is not an accidental recipe click area");
                areas.getFirst().onClick(runtime.getJeiHelpers().getFocusFactory(), runtime.getRecipesGui());
                check(mc.screen == runtime.getRecipesGui() && runtime.getRecipesGui().getParentScreen().orElse(null) == metalScreen,
                        "The actual registered arrow action opens real JEI and preserves the native furnace parent");
                report.addProperty("native_arrow_action_opened_jei", true); arm(mc, 2); return;
            }
            var visible = visibleLayouts();
            check(!visible.isEmpty() && visible.stream().allMatch(layout -> layout.getRecipeCategory() == category
                            && layout.getRecipe() instanceof MetalUpgradingJeiPlugin.Entry), "Actual JEI rendered rows belong to the independent metal category");
            if (phase == 2) {
                report.addProperty("arrow_opened_visible_native_recipe_rows", visible.size());
                screenshot(mc, "metal-jei-arrow-zh_cn.png", false);
                runtime.getRecipesGui().showRecipes(category, List.of(entries.getFirst()), List.of()); arm(mc, 3); return;
            }
            int row = phase - 3;
            check(visible.size() == 1 && ((MetalUpgradingJeiPlugin.Entry)visible.getFirst().getRecipe()).id().equals(entries.get(row).id()),
                    "The real native JEI screen draws the requested conversion and its configured costs");
            Path image = screenshot(mc, "metal-jei-" + entries.get(row).recipe().stage().getSerializedName() + "-zh_cn.png", false);
            report.getAsJsonArray("jei_recipes").get(row).getAsJsonObject().addProperty("screenshot", image.toString());
            if (phase == 3) { runtime.getRecipesGui().showRecipes(category, List.of(entries.get(1)), List.of()); arm(mc, 4); return; }
            report.addProperty("native_jei_conversion_screens_verified", 2); report.addProperty("natural_menu_progress_synchronization_verified", true);
            finish(mc);
        }

        private void arm(Minecraft mc, int nextPhase) {
            check(mc.screen == runtime.getRecipesGui(), "JEI scenes use the actual recipes screen");
            phase = nextPhase; expectedScreen = mc.screen; frames = 0; movePointerAway(mc);
        }
        private List<IRecipeLayoutDrawable<?>> visibleLayouts() throws Exception {
            Object logic = field(runtime.getRecipesGui(), "logic"), state = field(logic, "state");
            Object cache = field(logic, "cachedRecipeLayoutsWithButtons");
            check(cache != null, "JEI has created its actual visible-layout cache");
            List<?> rows = (List<?>)Class.forName("mezz.jei.gui.recipes.lookups.ILookupState")
                    .getMethod("getVisible", Class.forName("mezz.jei.gui.recipes.layouts.IRecipeLayoutList")).invoke(state, cache);
            var result = new ArrayList<IRecipeLayoutDrawable<?>>();
            for (Object row : rows) {
                Object drawable = Class.forName("mezz.jei.gui.recipes.IRecipeLayoutWithButtons").getMethod("getRecipeLayout").invoke(row);
                check(drawable instanceof IRecipeLayoutDrawable<?>, "The visible native JEI row owns a real recipe drawable");
                result.add((IRecipeLayoutDrawable<?>)drawable);
            }
            return result;
        }
    }

    private record ServerFacts(BlockPos pos, int containerId, int progress, int total, int inputCount, int requiredHeat, int seedProgress) {}
    private static CompletableFuture<ServerFacts> prepare(Minecraft mc, MetalUpgradingRecipe recipe) {
        UUID playerId = mc.player.getUUID();
        return mc.getSingleplayerServer().submit(() -> {
            check(owned(mc), "Server preparation is restricted to the just-created isolated world");
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            check(player != null, "The real integrated-server player has joined");
            var level = player.serverLevel(); BlockPos pos = player.blockPosition().offset(2, 0, 0);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, MetalUpgradingContent.FURNACE.get().defaultBlockState());
            var furnace = (MetalUpgradingEntity)level.getBlockEntity(pos);
            furnace.setItem(0, recipe.input()); furnace.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get(), 2));
            check(recipe.cookingTicks() >= 100, "The isolated default batch duration must allow visible natural menu progress");
            // Seed only this disposable machine's saved progress so the real screenshot
            // has a visible arrow without waiting several minutes for the default batch.
            MetalUpgradingEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
            var saved = furnace.saveWithoutMetadata(level.registryAccess());
            int seed = recipe.cookingTicks() * 2 / 5; saved.putInt("progress", seed);
            furnace.loadWithComponents(saved, level.registryAccess());
            player.teleportTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() + 2.5);
            player.openMenu(furnace);
            check(player.containerMenu instanceof MetalUpgradingMenu, "The real server opened the dedicated production menu");
            player.containerMenu.broadcastChanges();
            return facts(furnace, player.containerMenu.containerId, seed);
        });
    }
    private static CompletableFuture<ServerFacts> snapshot(Minecraft mc, BlockPos pos) {
        return mc.getSingleplayerServer().submit(() -> {
            check(owned(mc), "Server snapshots are restricted to the new isolated world");
            var entity = (MetalUpgradingEntity)mc.getSingleplayerServer().overworld().getBlockEntity(pos);
            return facts(entity, -1, 0);
        });
    }
    private static ServerFacts facts(MetalUpgradingEntity entity, int containerId, int seed) {
        int progress = (entity.data.get(0) & 65535) | ((entity.data.get(1) & 65535) << 16);
        int total = (entity.data.get(2) & 65535) | ((entity.data.get(3) & 65535) << 16);
        return new ServerFacts(entity.getBlockPos(), containerId, progress, total, entity.data.get(10), entity.data.get(9), seed);
    }
    private static Path screenshot(Minecraft mc, String filename, boolean metalPanel) throws Exception {
        // Called only after RenderFrameEvent.Post: this is the framebuffer the user
        // actually saw, including the native container or JEI screen and its text.
        Path path = output().getParent().resolve(filename); Files.createDirectories(path.getParent());
        int panelPixels = 0;
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(path);
            if (metalPanel) {
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    if ((pixel & 255) == 207 && (pixel >>> 8 & 255) == 212 && (pixel >>> 16 & 255) == 216) panelPixels++;
                }
                check(panelPixels > 2000, "The real GPU screenshot must contain the opaque production panel, not only its background");
            }
        }
        var shot = new JsonObject(); shot.addProperty("screenshot", path.toString()); shot.addProperty("screen", mc.screen.getClass().getName());
        shot.addProperty("capture", "RenderFrameEvent.Post actual main framebuffer"); shot.addProperty("production_panel_pixels", panelPixels);
        report.getAsJsonArray("screenshots").add(shot); return path;
    }
    private static boolean contains(IRecipeSlotView slot, ItemStack stack) { return slot.getItemStacks().anyMatch(actual -> ItemStack.matches(actual, stack)); }
    private static String itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void movePointerAway(Minecraft mc) { GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 4, 4); }
    private static JsonObject initial() {
        var value = new JsonObject(); value.addProperty("author", "NingMeng123"); value.addProperty("timestamp", Instant.now().toString());
        value.addProperty("scope", "Real isolated integrated-server blocks, packet-opened native menus and natural progress synchronization; real JEI two-recipe layouts, catalyst/uses/output lookup and arrow action; Chinese GPU frames");
        value.addProperty("existing_world_loaded", false); value.addProperty("multiplayer_connected", false);
        value.addProperty("test_fixture_progress_seeded_for_visible_screenshots", true);
        value.add("failures", new JsonArray()); value.add("native_menus", new JsonArray()); value.add("screenshots", new JsonArray()); return value;
    }
    private static boolean owned(Minecraft mc) {
        return started && worldName != null && mc.getSingleplayerServer() != null
                && worldName.equals(mc.getSingleplayerServer().getWorldData().getLevelName());
    }
    private static boolean isolated(Minecraft mc) {
        return mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-metal-audit");
    }
    private static void fail(String label, Throwable failure) {
        if (report == null) report = initial(); report.getAsJsonArray("failures").add(label + ": " + failure);
        LogUtils.getLogger().error("MODERN_ITE_METAL_AUDIT {}", label, failure);
    }
    private static void finish(Minecraft mc) {
        if (finished) return; finished = true;
        boolean safe = owned(mc) || isolated(mc) && mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null;
        if (report == null) report = initial(); report.addProperty("status", report.getAsJsonArray("failures").isEmpty() ? "passed" : "failed");
        report.addProperty("safe_client_shutdown_requested", safe);
        try {
            Path path = output(); Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            LogUtils.getLogger().info("MODERN_ITE_METAL_AUDIT {} report={}", report.get("status").getAsString(), path);
        } catch (Exception failure) { LogUtils.getLogger().error("Cannot write metal audit report", failure); }
        if (safe) mc.stop();
    }
    private static Path output() {
        return Path.of(System.getProperty("modern_ite.metalAuditOutput", "build/verification/metal/client-audit.json")).toAbsolutePath().normalize();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static final class WaitingScreen extends Screen {
        WaitingScreen() { super(Component.literal("金属升级炉 · 隔离客户端审计")); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF101820); graphics.drawCenteredString(font, title, width / 2, height / 2, 0xFFFFFFFF);
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }
    }
}
