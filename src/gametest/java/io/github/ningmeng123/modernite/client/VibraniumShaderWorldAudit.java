package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.client.entity.VibraniumCosmicShaders;
import io.github.ningmeng123.modernite.client.entity.VibraniumGildingLayer;
import io.github.ningmeng123.modernite.entity.ArmorAuraRules;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/** Actual shader-pack world frames; visible star quality still requires pixel review. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class VibraniumShaderWorldAudit {
    private enum Phase { TITLE, LOADING, PREPARING, TRANSITIONING, SYNCHRONIZING, WARMING, DONE }
    private enum Outfit {
        UNENCHANTED("vibranium-unenchanted", 0, false, false),
        PROTECTION_IV("vibranium-protection-iv", 4, false, false),
        PROTECTION_V("vibranium-protection-v", 5, false, true),
        BASELINE_V("vibranium-protection-v-layer-disabled", 5, false, true),
        FIXED_V("vibranium-protection-v-fixed-160", 5, false, true),
        ANIMATED_V("vibranium-protection-v-later", 5, false, true),
        REAR_V("vibranium-protection-v-rear", 5, false, true),
        WALL_BASELINE_V("vibranium-protection-v-low-wall-layer-disabled", 5, false, true),
        WALL_V("vibranium-protection-v-low-wall", 5, false, true),
        INVENTORY_V("vibranium-protection-v-inventory", 5, false, true),
        MISSING_CHEST("vibranium-protection-v-missing-chest", 5, false, false),
        IRON_V("iron-protection-v", 5, true, false),
        LIFECYCLE_ON_V("lifecycle-bsl-enabled-first", 5, false, true),
        LIFECYCLE_OFF_V("lifecycle-shaders-disabled-first", 5, false, true),
        LIFECYCLE_ON_AGAIN_V("lifecycle-bsl-enabled-again", 5, false, true),
        LIFECYCLE_OFF_RELOAD_V("lifecycle-shaders-disabled-resource-reloaded", 5, false, true);
        final String id;
        final int protection;
        final boolean iron, active;
        Outfit(String id, int protection, boolean iron, boolean active) {
            this.id = id; this.protection = protection; this.iron = iron; this.active = active;
        }
        boolean disabled() { return this == BASELINE_V || this == WALL_BASELINE_V; }
        boolean wall() { return this == WALL_V || this == WALL_BASELINE_V; }
        boolean isolateGlint() { return active && this != PROTECTION_V && this != INVENTORY_V; }
        int cosmicTick() { return this == ANIMATED_V ? 200 : 160; }
        boolean lifecycle() { return ordinal() >= LIFECYCLE_ON_V.ordinal(); }
        boolean lifecycleShadersEnabled() { return this == LIFECYCLE_ON_V || this == LIFECYCLE_ON_AGAIN_V; }
    }
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final String[] PIECES = { "helmet", "chestplate", "leggings", "boots" };
    private static final Item[] IRON = { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS };
    private static final Vec3 HOME = new Vec3(.5D, 128D, .5D);
    private static final float YAW = -20F, PITCH = 8F;
    private static final String COMPAT = "io.github.ningmeng123.modernite.client.entity.VibraniumCosmicIrisCompat";
    private static final String LIFECYCLE_PACK = "BSL_v10.1.1.zip";
    private static final JsonObject REPORT = new JsonObject();
    private static Phase phase = Phase.TITLE;
    private static CompletableFuture<JsonObject> pending;
    private static CompletableFuture<?> lifecyclePending;
    private static String worldName;
    private static UUID owner;
    private static int ticks, caseIndex, synchronizationTicks, warmTicks, normalWorldFrames;
    private static long passesBefore;
    private static JsonObject current;
    private static NativeImage previous;
    private static boolean isolated;
    private static List<Object> controlledLayers;
    private static int controlledIndex = -1;
    private static Object originalLayer;
    private static final Set<String> CALLER_BUFFER_CLASSES = new TreeSet<>();

    private VibraniumShaderWorldAudit() {}
    private static boolean enabled() { return Boolean.getBoolean("modern_ite.cosmicShaderWorldAudit"); }
    private static boolean lifecycleEnabled() { return Boolean.getBoolean("modern_ite.cosmicLifecycleAudit"); }
    private static boolean nativeLayerAudit() { return Boolean.getBoolean("modern_ite.cosmicNativeLayerAudit"); }
    private static int caseCount() { return nativeLayerAudit() ? 2 : lifecycleEnabled() ? Outfit.values().length : Outfit.LIFECYCLE_ON_V.ordinal(); }
    private static Outfit currentOutfit() {
        return nativeLayerAudit() ? caseIndex == 0 ? Outfit.PROTECTION_V : Outfit.INVENTORY_V : Outfit.values()[caseIndex];
    }
    private static boolean expectedShaderPack() {
        Outfit outfit = currentOutfit();
        return outfit.lifecycle() ? outfit.lifecycleShadersEnabled()
                : Boolean.getBoolean("modern_ite.cosmicExpectedShaderPack");
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!enabled() || phase == Phase.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == Phase.TITLE) {
                check(mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null,
                        "Refused an existing world, server or connection");
                if (mc.getOverlay() != null) return;
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                isolated = directory.toString().replace('\\', '/').contains("/build/verification/");
                check(isolated, "The world audit requires a separate game directory under build/verification");
                check(output().toString().replace('\\', '/').contains("/build/verification/"),
                        "Audit output must stay under build/verification");
                REPORT.addProperty("author", "NingMeng123");
                REPORT.addProperty("timestamp", Instant.now().toString());
                REPORT.addProperty("status", "running");
                REPORT.addProperty("game_directory", directory.toString());
                REPORT.addProperty("expected_shader_pack", expectedShaderPack());
                REPORT.addProperty("lifecycle_audit_enabled", lifecycleEnabled());
                REPORT.addProperty("native_layer_audit_enabled", nativeLayerAudit());
                check(!(nativeLayerAudit() && lifecycleEnabled()), "Native-layer and controlled lifecycle audits are separate modes");
                REPORT.addProperty("planned_cases", caseCount());
                if (lifecycleEnabled()) {
                    check(Files.isRegularFile(directory.resolve("shaderpacks").resolve(LIFECYCLE_PACK)),
                            "The optional lifecycle audit requires a copied BSL_v10.1.1.zip in the isolated client's shaderpacks");
                    REPORT.addProperty("lifecycle_shader_pack", LIFECYCLE_PACK);
                }
                REPORT.addProperty("existing_worlds_opened", false);
                REPORT.addProperty("manual_pixel_review_required", true);
                REPORT.addProperty("pixel_difference_is_star_visibility_proof", false);
                REPORT.addProperty("scope", nativeLayerAudit()
                        ? "Actual integrated-server protection V armor, unmodified production cosmic layer, natural animation clock and ordinary enchantment glint, world and inventory"
                        : "Actual integrated-server armor, synchronized third-person player, native world framebuffer, post-pack draw instrumentation, test-only fixed-clock layer controls, low-wall occlusion and inventory");
                REPORT.add("cases", new JsonArray());
                // Iris's public API describes the current world pipeline, which may not exist at the title.
                // Require the requested on/off state only after normal world frames have actually rendered.
                REPORT.addProperty("shader_state_check_stage", "after_normal_world_frames");
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.options.fov().set(60);
                mc.options.renderDistance().set(3);
                mc.options.simulationDistance().set(5);
                worldName = "cosmic-shader-audit-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
                REPORT.addProperty("disposable_world", worldName);
                phase = Phase.LOADING;
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261024L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++ticks < 6000, "Timed out creating or rendering the dedicated shader world");
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
            check(owned(mc), "Only the newly created disposable world may be changed");
            mc.options.hideGui = true;
            mc.getToasts().clear();
            mc.options.setCameraType(currentOutfit() == Outfit.REAR_V ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
            mc.player.setYRot(YAW); mc.player.setXRot(PITCH);
            mc.player.yRotO = YAW; mc.player.xRotO = PITCH;
            mc.player.yHeadRot = YAW; mc.player.yHeadRotO = YAW;
            mc.player.yBodyRot = YAW; mc.player.yBodyRotO = YAW;
            if (phase == Phase.LOADING) {
                if (mc.screen != null) return;
                owner = mc.player.getUUID();
                pending = onServer(mc, player -> { prepareWorld(player); return equipment(player); });
                phase = Phase.PREPARING;
            } else if (phase == Phase.PREPARING && pending.isDone()) {
                pending.join();
                if (mc.screen != null || mc.player.position().distanceToSqr(HOME) > .01D) return;
                REPORT.addProperty("world_created", true);
                REPORT.addProperty("camera", "third_person_front");
                REPORT.addProperty("camera_yaw", YAW); REPORT.addProperty("camera_pitch", PITCH);
                installLayerControl(mc);
                beginCase(mc);
            } else if (phase == Phase.TRANSITIONING) {
                if (!lifecyclePending.isDone()) return;
                lifecyclePending.join();
                current.addProperty("lifecycle_operation_completed", true);
                current.addProperty("resource_reload_future_completed", currentOutfit() == Outfit.LIFECYCLE_OFF_RELOAD_V);
                // Resource reload rebuilds entity renderers; control the new layer rather than a stale list.
                installLayerControl(mc);
                beginEquipmentCase(mc, currentOutfit());
            } else if (phase == Phase.SYNCHRONIZING) {
                check(++synchronizationTicks < 400, "The actual server equipment did not synchronize to the client");
                if (!pending.isDone()) return;
                JsonObject server = pending.join();
                if (!matches(mc, currentOutfit())) return;
                if (currentOutfit() == Outfit.INVENTORY_V && mc.gameMode.hasInfiniteItems()) return;
                current.add("server_equipment", server);
                current.add("client_equipment", equipment(mc.player));
                current.addProperty("client_gate", ArmorAuraRules.fullProtectionFive(mc.player));
                current.addProperty("client_tick_before_warmup", mc.player.tickCount);
                passesBefore = compatLong("renderedPasses");
                normalWorldFrames = 0; warmTicks = 0;
                if (currentOutfit() == Outfit.INVENTORY_V) mc.setScreen(new InventoryScreen(mc.player));
                phase = Phase.WARMING;
            } else if (phase == Phase.WARMING) {
                ++warmTicks;
                check(matches(mc, currentOutfit()), "The synchronized equipment changed during the capture case");
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void worldRendered(RenderLevelStageEvent event) {
        if (!enabled() || phase != Phase.WARMING || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        try {
            if (irisShadowPass()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && captureScreenReady(mc) && mc.getOverlay() == null) ++normalWorldFrames;
        } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
    }

    @SubscribeEvent public static void frameRendered(RenderFrameEvent.Post ignored) {
        if (!enabled() || phase != Phase.WARMING || normalWorldFrames < 40 || warmTicks < 40) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !captureScreenReady(mc) || mc.getOverlay() != null) return;
        try {
            checkShaderState();
            check(!irisShadowPass(), "A shadow render cannot count as the displayed world frame");
            Outfit outfit = currentOutfit();
            long passes = compatLong("renderedPasses") - passesBefore;
            current.addProperty("normal_world_frames_after_equipment_sync", normalWorldFrames);
            current.addProperty("warmup_client_ticks", warmTicks);
            current.addProperty("client_tick_at_capture", mc.player.tickCount);
            current.addProperty("compatibility_rendered_passes_during_case", passes);
            long pendingPasses = compatLong("pendingPasses");
            current.addProperty("pending_passes_after_frame", pendingPasses);
            check(pendingPasses == 0, "Deferred world geometry must not survive the displayed frame or leak into GUI rendering");
            current.addProperty("actual_shader_pack_active", compatBoolean("activeShaderPack"));
            current.addProperty("actual_compatibility_shader_class", (String)compat("lastShaderClass"));
            current.addProperty("original_glsl_ready", VibraniumCosmicShaders.isReady());
            captureAccelerationState();
            if (nativeLayerAudit()) current.add("native_layer_state", inspectNativeLayer(mc));
            JsonArray callerClasses = new JsonArray();
            CALLER_BUFFER_CLASSES.forEach(callerClasses::add);
            current.add("actual_caller_buffer_classes", callerClasses);
            if (expectedShaderPack() && outfit.active && !outfit.disabled()) {
                check(passes > 0, "Full protection V must execute the real Iris-compatible world render path");
            } else check(passes == 0, "Negative controls and shaders-off must not execute the Iris-compatible star pass");
            check(VibraniumCosmicShaders.isReady(), "Both paths retain the registered original cosmic GLSL program");
            capture(mc, outfit.id + ".png");
            current.addProperty("instrumentation_status", "passed");
            write();
            if (++caseIndex == caseCount()) finish(mc, null);
            else beginCase(mc);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void beginCase(Minecraft mc) throws Exception {
        Outfit outfit = currentOutfit();
        if (mc.screen != null) mc.setScreen(null);
        current = new JsonObject(); current.addProperty("outfit", outfit.id);
        CALLER_BUFFER_CLASSES.clear();
        current.addProperty("expected_cosmic_gate", outfit.active);
        current.addProperty("test_only_layer_disabled", outfit.disabled());
        if (nativeLayerAudit()) current.addProperty("cosmic_clock", "unmodified_entity_tick_and_partial_tick");
        else current.addProperty("fixed_cosmic_tick", outfit.cosmicTick());
        current.addProperty("native_glint_isolated", outfit.isolateGlint());
        current.addProperty("comparison_limit", nativeLayerAudit()
                ? "Natural production clock, ordinary glint and resource-pack animation; world and inventory captures are independent frames"
                : "Same server-synchronized equipment and fixed cosmic clock; independent world frames still include shader temporal history and native breathing");
        current.addProperty("camera", outfit == Outfit.REAR_V ? "third_person_back" : "third_person_front");
        current.addProperty("low_wall_occlusion", outfit.wall());
        current.addProperty("inventory_screen", outfit == Outfit.INVENTORY_V);
        current.addProperty("expected_shader_pack", expectedShaderPack());
        if (outfit == Outfit.ANIMATED_V) current.addProperty("same_equipment_as_previous_capture", true);
        REPORT.getAsJsonArray("cases").add(current);
        if (outfit.lifecycle()) {
            beginLifecycleTransition(mc, outfit);
            return;
        }
        beginEquipmentCase(mc, outfit);
    }

    private static void beginEquipmentCase(Minecraft mc, Outfit outfit) {
        pending = onServer(mc, player -> {
            player.setGameMode(outfit == Outfit.INVENTORY_V ? GameType.SURVIVAL : GameType.CREATIVE);
            equip(player, outfit);
            for (BlockPos pos : BlockPos.betweenClosed(-1, 128, 2, 2, 128, 2))
                player.serverLevel().setBlock(pos, (outfit.wall() ? Blocks.STONE_BRICKS : Blocks.AIR).defaultBlockState(), 3);
            check(ArmorAuraRules.fullProtectionFive(player) == outfit.active, "The server control must use the real full-set gate");
            return equipment(player);
        });
        synchronizationTicks = 0;
        phase = Phase.SYNCHRONIZING;
    }

    private static void beginLifecycleTransition(Minecraft mc, Outfit outfit) throws Exception {
        check(isolated && owned(mc), "Lifecycle mutations are restricted to the disposable audit client and world");
        phase = Phase.TRANSITIONING;
        normalWorldFrames = 0; warmTicks = 0;
        current.addProperty("lifecycle_operation", outfit == Outfit.LIFECYCLE_OFF_RELOAD_V
                ? "disable_shaders_then_reload_resources" : outfit.lifecycleShadersEnabled() ? "enable_bsl" : "disable_shaders");
        current.addProperty("shader_pack_before_transition", compatBoolean("activeShaderPack"));
        current.addProperty("pending_passes_before_transition", compatLong("pendingPasses"));
        current.addProperty("lifecycle_operation_completed", false);
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
        Object config = iris.getMethod("getIrisConfig").invoke(null);
        // Pack selection has no v0 public API. Only this gametest uses the installed Iris config by name.
        config.getClass().getMethod("setShaderPackName", String.class).invoke(config, LIFECYCLE_PACK);
        Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        Object instance = api.getMethod("getInstance").invoke(null);
        Object apiConfig = api.getMethod("getConfig").invoke(instance);
        Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig")
                .getMethod("setShadersEnabledAndApply", boolean.class).invoke(apiConfig, outfit.lifecycleShadersEnabled());
        // Iris apply is synchronous; a Minecraft resource reload is asynchronous and must finish first.
        lifecyclePending = outfit == Outfit.LIFECYCLE_OFF_RELOAD_V
                ? mc.reloadResourcePacks() : CompletableFuture.completedFuture(null);
        write();
    }

    private static void prepareWorld(ServerPlayer player) {
        var level = player.serverLevel(); var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(6000L); level.setWeatherParameters(0, 0, false, false);
        for (BlockPos pos : BlockPos.betweenClosed(-12, 127, -12, 12, 127, 12)) level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (BlockPos pos : BlockPos.betweenClosed(-6, 128, -6, 6, 135, 6)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        player.setGameMode(GameType.CREATIVE); player.setNoGravity(true); player.setInvulnerable(true);
        player.removeAllEffects(); player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) player.setItemSlot(slot, ItemStack.EMPTY);
        player.teleportTo(level, HOME.x, HOME.y, HOME.z, YAW, PITCH);
        player.setDeltaMovement(Vec3.ZERO); player.hurtMarked = true;
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
    }

    private static void equip(ServerPlayer player, Outfit outfit) {
        var protection = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        for (int i = 0; i < ARMOR.length; ++i) {
            Item item = outfit.iron ? IRON[i] : vibranium(i);
            ItemStack stack = new ItemStack(item);
            if (outfit.protection > 0) stack.enchant(protection, outfit.protection);
            if (outfit.isolateGlint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            if (outfit == Outfit.MISSING_CHEST && ARMOR[i] == EquipmentSlot.CHEST) stack = ItemStack.EMPTY;
            player.setItemSlot(ARMOR[i], stack);
        }
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
    }

    private static Item vibranium(int index) {
        var id = ResourceLocation.fromNamespaceAndPath("mite", "vibranium_" + PIECES[index]);
        Item item = BuiltInRegistries.ITEM.get(id);
        check(item != Items.AIR, "The actual vibranium armor item is registered: " + id);
        return item;
    }

    private static boolean matches(Minecraft mc, Outfit outfit) {
        if (ArmorAuraRules.fullProtectionFive(mc.player) != outfit.active) return false;
        var protection = mc.player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        for (int i = 0; i < ARMOR.length; ++i) {
            ItemStack stack = mc.player.getItemBySlot(ARMOR[i]);
            if (outfit == Outfit.MISSING_CHEST && ARMOR[i] == EquipmentSlot.CHEST) { if (!stack.isEmpty()) return false; continue; }
            if (!stack.is(outfit.iron ? IRON[i] : vibranium(i)) || stack.getEnchantmentLevel(protection) != outfit.protection) return false;
            if (outfit.isolateGlint() && !Boolean.FALSE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) return false;
        }
        return true;
    }

    private static JsonObject equipment(net.minecraft.world.entity.LivingEntity living) {
        JsonObject facts = new JsonObject();
        var protection = living.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = living.getItemBySlot(slot); JsonObject piece = new JsonObject();
            piece.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            piece.addProperty("protection", stack.getEnchantmentLevel(protection));
            if (stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
                piece.addProperty("glint_override", stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
            facts.add(slot.getName(), piece);
        }
        facts.addProperty("full_protection_v_gate", ArmorAuraRules.fullProtectionFive(living));
        facts.addProperty("entity_id", living.getId()); return facts;
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        Files.createDirectories(output());
        int beforeCapture = GL11.glGetError();
        current.addProperty("opengl_error_before_capture", beforeCapture);
        check(beforeCapture == GL11.GL_NO_ERROR, "World/GUI rendering left an OpenGL error: " + beforeCapture);
        NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        try {
            int afterReadback = GL11.glGetError();
            current.addProperty("opengl_error_after_readback", afterReadback);
            check(afterReadback == GL11.GL_NO_ERROR, "Native framebuffer readback failed with OpenGL error: " + afterReadback);
            pixels.writeToFile(output().resolve(name));
            current.addProperty("screenshot", output().resolve(name).toString());
            current.addProperty("width", pixels.getWidth()); current.addProperty("height", pixels.getHeight());
            if (previous != null && previous.getWidth() == pixels.getWidth() && previous.getHeight() == pixels.getHeight()) {
                int changed = 0, total = 0;
                for (int y = pixels.getHeight() / 4; y < pixels.getHeight() * 7 / 8; ++y)
                    for (int x = pixels.getWidth() * 7 / 20; x < pixels.getWidth() * 13 / 20; ++x) {
                        int before = previous.getPixelRGBA(x, y), after = pixels.getPixelRGBA(x, y);
                        int delta = Math.abs((before & 255) - (after & 255))
                                + Math.abs((before >>> 8 & 255) - (after >>> 8 & 255))
                                + Math.abs((before >>> 16 & 255) - (after >>> 16 & 255));
                        if (delta > 45) ++changed; ++total;
                    }
                current.addProperty("center_armor_region_changed_pixels_from_previous_case", changed);
                current.addProperty("center_armor_region_sampled_pixels", total);
                current.addProperty("difference_limit", "Includes armor, ordinary glint, breathing, shader history and world lighting; not an isolated star-visibility assertion");
            }
            if (previous != null) previous.close();
            previous = pixels; pixels = null;
            REPORT.addProperty("native_world_framebuffer_captured", true);
            REPORT.addProperty("opengl_version", GL11.glGetString(GL11.GL_VERSION));
            REPORT.addProperty("opengl_renderer", GL11.glGetString(GL11.GL_RENDERER));
        } finally { if (pixels != null) pixels.close(); }
    }

    /** Observe the public acceleration stack only after all world and GUI rendering has finished. */
    private static void captureAccelerationState() throws Exception {
        JsonObject state = new JsonObject();
        try {
            Class<?> type = Class.forName("com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature");
            Object setting = type.getMethod("getPipelineSetting").invoke(null);
            Object defaultSetting = type.getMethod("getDefaultPipelineSetting").invoke(null);
            boolean accelerated = (boolean) type.getMethod("shouldUseAcceleratedPipeline").invoke(null);
            state.addProperty("installed", true);
            state.addProperty("feature_enabled", (boolean) type.getMethod("isEnabled").invoke(null));
            state.addProperty("should_use_accelerated_pipeline", accelerated);
            state.addProperty("pipeline_setting", setting.toString());
            state.addProperty("default_pipeline_setting", defaultSetting.toString());
            state.addProperty("pipeline_restored_to_default", setting.equals(defaultSetting));
            current.add("acceleration_state_after_frame", state);
            check(setting.equals(defaultSetting), "Cosmic rendering must restore the user's default accelerated pipeline after the frame");
        } catch (ClassNotFoundException absent) {
            state.addProperty("installed", false);
            current.add("acceleration_state_after_frame", state);
        }
    }

    private static boolean captureScreenReady(Minecraft mc) {
        return currentOutfit() == Outfit.INVENTORY_V
                ? mc.screen instanceof InventoryScreen : mc.screen == null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void installLayerControl(Minecraft mc) throws Exception {
        restoreLayerControl();
        if (nativeLayerAudit()) {
            REPORT.add("native_layer_state_at_install", inspectNativeLayer(mc));
            return;
        }
        var renderer = (LivingEntityRenderer<?, ?>) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        Field field = LivingEntityRenderer.class.getDeclaredField("layers"); field.setAccessible(true);
        controlledLayers = (List<Object>) field.get(renderer);
        for (int index = 0; index < controlledLayers.size(); ++index) {
            if (!(controlledLayers.get(index) instanceof VibraniumGildingLayer<?, ?>)) continue;
            controlledIndex = index;
            originalLayer = controlledLayers.get(index);
            controlledLayers.set(index, new ControlledGildingLayer((RenderLayerParent) renderer, (RenderLayer) originalLayer));
            REPORT.addProperty("fixed_clock_control", "gametest-only layer delegate; production shader and armor gate unchanged");
            return;
        }
        throw new IllegalStateException("The real player renderer has no cosmic armor layer");
    }

    /** Read the live renderer without replacing, wrapping or reordering any production layer. */
    private static JsonObject inspectNativeLayer(Minecraft mc) throws Exception {
        var renderer = (LivingEntityRenderer<?, ?>) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        Field field = LivingEntityRenderer.class.getDeclaredField("layers"); field.setAccessible(true);
        List<?> layers = (List<?>) field.get(renderer);
        JsonObject state = new JsonObject();
        JsonArray names = new JsonArray();
        int realCount = 0, controlledCount = 0;
        for (Object layer : layers) {
            names.add(layer.getClass().getName());
            if (layer.getClass() == VibraniumGildingLayer.class) ++realCount;
            if (layer instanceof ControlledGildingLayer) ++controlledCount;
        }
        state.add("actual_renderer_layer_classes", names);
        state.addProperty("production_cosmic_layer_count", realCount);
        state.addProperty("controlled_layer_count", controlledCount);
        state.addProperty("production_layer_unwrapped", realCount == 1 && controlledCount == 0);
        check(realCount == 1 && controlledCount == 0, "Native audit must retain exactly one real, unwrapped production cosmic layer");
        return state;
    }

    private static void restoreLayerControl() {
        if (controlledLayers != null && controlledIndex >= 0) {
            controlledLayers.set(controlledIndex, originalLayer);
            controlledLayers = null; originalLayer = null; controlledIndex = -1;
        }
    }

    /** Test-only clock/gate adapter, also used by the existing native armor portrait audits. */
    private static final class ControlledGildingLayer extends RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> {
        private final RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> delegate;
        ControlledGildingLayer(RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> parent,
                RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> delegate) {
            super(parent); this.delegate = delegate;
        }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                float limbSwing, float amount, float partialTick, float age, float headYaw, float headPitch) {
            if (phase == Phase.DONE) return;
            CALLER_BUFFER_CLASSES.add(buffers.getClass().getName());
            Outfit outfit = currentOutfit();
            if (outfit.disabled()) return;
            int savedTick = entity.tickCount;
            try {
                entity.tickCount = outfit.cosmicTick();
                delegate.render(pose, buffers, light, entity, limbSwing, amount, 0F, age, headYaw, headPitch);
            } finally { entity.tickCount = savedTick; }
        }
    }

    private static void checkShaderState() throws Exception {
        REPORT.addProperty("shader_state_checked_after_world_render", true);
        boolean actual = compatBoolean("activeShaderPack");
        REPORT.addProperty("actual_shader_pack_active", actual);
        REPORT.addProperty("iris_api_shader_pack_active", irisFlag("isShaderPackInUse"));
        check(actual == expectedShaderPack(), "Actual shader-pack state does not match the explicitly requested on/off run");
        check(irisFlag("isShaderPackInUse") == expectedShaderPack(), "The real Iris API must confirm the expected shader-pack state");
        try {
            String packName = (String) Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
            current.addProperty("actual_iris_pack_name", packName);
            String expectedPack = currentOutfit().lifecycle() ? LIFECYCLE_PACK : System.getProperty("modern_ite.cosmicExpectedPack", "");
            if (expectedShaderPack() && !expectedPack.isEmpty())
                check(expectedPack.equals(packName), "The displayed world must use the explicitly selected shader pack: " + expectedPack);
        } catch (ClassNotFoundException absent) { current.addProperty("actual_iris_pack_name", "iris_not_installed"); }
    }

    private static boolean irisShadowPass() throws Exception { return irisFlag("isRenderingShadowPass"); }
    private static boolean irisFlag(String name) throws Exception {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod(name).invoke(instance);
        } catch (ClassNotFoundException absent) { return false; }
    }
    private static Object compat(String name) throws Exception {
        Method method = Class.forName(COMPAT).getDeclaredMethod(name); method.setAccessible(true);
        return method.invoke(null);
    }
    private static long compatLong(String name) throws Exception { return ((Number) compat(name)).longValue(); }
    private static boolean compatBoolean(String name) throws Exception { return (boolean) compat(name); }
    private static CompletableFuture<JsonObject> onServer(Minecraft mc, java.util.function.Function<ServerPlayer, JsonObject> action) {
        var server = mc.getSingleplayerServer();
        return CompletableFuture.supplyAsync(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            check(player != null, "The actual integrated-server player remains online");
            return action.apply(player);
        }, server);
    }
    private static boolean owned(Minecraft mc) {
        return worldName != null && mc.getSingleplayerServer() != null
                && worldName.equals(mc.getSingleplayerServer().getWorldData().getLevelName());
    }
    private static Path output() {
        return Path.of(System.getProperty("modern_ite.cosmicShaderWorldAuditOutput", "build/verification/cosmic-shader-world"))
                .toAbsolutePath().normalize();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void write() throws Exception {
        Files.createDirectories(output());
        Files.writeString(output().resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n", StandardCharsets.UTF_8);
    }
    private static void finish(Minecraft mc, Throwable failure) {
        boolean safe = owned(mc) || (isolated && mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null);
        phase = Phase.DONE;
        restoreLayerControl();
        REPORT.addProperty("status", failure == null ? "captured_real_world_frames_pending_visual_review" : "failed");
        REPORT.addProperty("safe_client_shutdown_requested", safe);
        if (failure != null) { REPORT.addProperty("failure", failure.toString()); LogUtils.getLogger().error("MODERN_ITE_COSMIC_SHADER_WORLD_AUDIT failed", failure); }
        if (previous != null) { previous.close(); previous = null; }
        try { write(); } catch (Exception writing) { LogUtils.getLogger().error("Cannot write cosmic shader world report", writing); }
        if (safe) mc.stop();
    }
}
