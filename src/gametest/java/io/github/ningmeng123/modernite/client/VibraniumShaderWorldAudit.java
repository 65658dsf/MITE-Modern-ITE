package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.client.entity.VibraniumCosmicShaders;
import io.github.ningmeng123.modernite.entity.ArmorAuraRules;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
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
import org.lwjgl.opengl.GL13;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Actual shader-pack world frames; visible star quality still requires pixel review. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class VibraniumShaderWorldAudit {
    private enum Phase { TITLE, LOADING, PREPARING, SYNCHRONIZING, WARMING, DONE }
    private enum Outfit {
        UNENCHANTED("vibranium-unenchanted", 0, false, false),
        PROTECTION_IV("vibranium-protection-iv", 4, false, false),
        PROTECTION_V("vibranium-protection-v", 5, false, true),
        ANIMATED_V("vibranium-protection-v-later", 5, false, true),
        MISSING_CHEST("vibranium-protection-v-missing-chest", 5, false, false),
        IRON_V("iron-protection-v", 5, true, false);
        final String id;
        final int protection;
        final boolean iron, active;
        Outfit(String id, int protection, boolean iron, boolean active) {
            this.id = id; this.protection = protection; this.iron = iron; this.active = active;
        }
    }
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final String[] PIECES = { "helmet", "chestplate", "leggings", "boots" };
    private static final Item[] IRON = { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS };
    private static final Vec3 HOME = new Vec3(.5D, 128D, .5D);
    private static final float YAW = -20F, PITCH = 8F;
    private static final String COMPAT = "io.github.ningmeng123.modernite.client.entity.VibraniumCosmicIrisCompat";
    private static final JsonObject REPORT = new JsonObject();
    private static Phase phase = Phase.TITLE;
    private static CompletableFuture<JsonObject> pending;
    private static String worldName;
    private static UUID owner;
    private static int ticks, caseIndex, synchronizationTicks, warmTicks, normalWorldFrames;
    private static long passesBefore;
    private static JsonObject current;
    private static NativeImage previous;
    private static boolean isolated;

    private VibraniumShaderWorldAudit() {}
    private static boolean enabled() { return Boolean.getBoolean("modern_ite.cosmicShaderWorldAudit"); }
    private static boolean expectedShaderPack() { return Boolean.getBoolean("modern_ite.cosmicExpectedShaderPack"); }

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
                REPORT.addProperty("existing_worlds_opened", false);
                REPORT.addProperty("manual_pixel_review_required", true);
                REPORT.addProperty("pixel_difference_is_star_visibility_proof", false);
                REPORT.addProperty("scope", "Actual integrated-server armor, synchronized third-person player, native world framebuffer and Iris path instrumentation; no portrait or private render target");
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
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
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
                beginCase(mc);
            } else if (phase == Phase.SYNCHRONIZING) {
                check(++synchronizationTicks < 400, "The actual server equipment did not synchronize to the client");
                if (!pending.isDone()) return;
                JsonObject server = pending.join();
                if (!matches(mc, Outfit.values()[caseIndex])) return;
                current.add("server_equipment", server);
                current.add("client_equipment", equipment(mc.player));
                current.addProperty("client_gate", ArmorAuraRules.fullProtectionFive(mc.player));
                current.addProperty("client_tick_before_warmup", mc.player.tickCount);
                passesBefore = compatLong("renderedPasses");
                normalWorldFrames = 0; warmTicks = 0;
                phase = Phase.WARMING;
            } else if (phase == Phase.WARMING) {
                ++warmTicks;
                check(matches(mc, Outfit.values()[caseIndex]), "The synchronized equipment changed during the capture case");
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void worldRendered(RenderLevelStageEvent event) {
        if (!enabled() || phase != Phase.WARMING || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        try {
            if (irisShadowPass()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null && mc.getOverlay() == null) ++normalWorldFrames;
        } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
    }

    @SubscribeEvent public static void frameRendered(RenderFrameEvent.Post ignored) {
        if (!enabled() || phase != Phase.WARMING || normalWorldFrames < 40 || warmTicks < 40) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.getOverlay() != null) return;
        try {
            checkShaderState();
            check(!irisShadowPass(), "A shadow render cannot count as the displayed world frame");
            Outfit outfit = Outfit.values()[caseIndex];
            long passes = compatLong("renderedPasses") - passesBefore;
            current.addProperty("normal_world_frames_after_equipment_sync", normalWorldFrames);
            current.addProperty("warmup_client_ticks", warmTicks);
            current.addProperty("client_tick_at_capture", mc.player.tickCount);
            current.addProperty("compatibility_rendered_passes_during_case", passes);
            current.addProperty("compatibility_registered_textures", compatLong("registeredTextures"));
            current.addProperty("compatibility_animation_frame", compatLong("currentFrame"));
            current.addProperty("actual_shader_pack_active", compatBoolean("activeShaderPack"));
            current.addProperty("actual_compatibility_shader_class", (String)compat("lastShaderClass"));
            current.addProperty("original_glsl_ready", VibraniumCosmicShaders.isReady());
            if (expectedShaderPack() && outfit.active) {
                check(passes > 0, "Full protection V must execute the real Iris-compatible world render path");
                check(compatLong("registeredTextures") > 0, "The compatible path must register its real star textures");
                captureStarTextures(outfit.id);
            } else check(passes == 0, "Negative controls and shaders-off must not execute the Iris-compatible star pass");
            if (!expectedShaderPack()) check(VibraniumCosmicShaders.isReady(), "Shaders-off keeps the registered original cosmic GLSL path");
            capture(mc, outfit.id + ".png");
            current.addProperty("instrumentation_status", "passed");
            write();
            if (++caseIndex == Outfit.values().length) finish(mc, null);
            else beginCase(mc);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void beginCase(Minecraft mc) {
        Outfit outfit = Outfit.values()[caseIndex];
        current = new JsonObject(); current.addProperty("outfit", outfit.id);
        current.addProperty("expected_cosmic_gate", outfit.active);
        if (outfit == Outfit.ANIMATED_V) current.addProperty("same_equipment_as_previous_capture", true);
        REPORT.getAsJsonArray("cases").add(current);
        pending = onServer(mc, player -> {
            if (outfit != Outfit.ANIMATED_V) equip(player, outfit);
            check(ArmorAuraRules.fullProtectionFive(player) == outfit.active, "The server control must use the real full-set gate");
            return equipment(player);
        });
        synchronizationTicks = 0;
        phase = Phase.SYNCHRONIZING;
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
            facts.add(slot.getName(), piece);
        }
        facts.addProperty("full_protection_v_gate", ArmorAuraRules.fullProtectionFive(living));
        facts.addProperty("entity_id", living.getId()); return facts;
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        Files.createDirectories(output());
        NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        try {
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

    /** Read the actual generated mask and GPU texture; submitting a pass alone proves no visible output. */
    private static void captureStarTextures(String prefix) throws Exception {
        Class<?> type = Class.forName(COMPAT);
        Field field = type.getDeclaredField("TEXTURES"); field.setAccessible(true);
        JsonArray textures = new JsonArray();
        for (var entry : ((Map<?, ?>)field.get(null)).entrySet()) {
            Object value = entry.getValue();
            Field textureField = value.getClass().getDeclaredField("texture"); textureField.setAccessible(true);
            DynamicTexture texture = (DynamicTexture)textureField.get(value);
            NativeImage cpu = texture.getPixels(); check(cpu != null, "The generated texture remains live");
            String name = prefix + "-" + ((ResourceLocation)entry.getKey()).getPath().replace('/', '-');
            Files.createDirectories(output()); cpu.writeToFile(output().resolve(name + "-cpu.png"));
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try (NativeImage gpu = new NativeImage(cpu.getWidth(), cpu.getHeight(), false)) {
                texture.bind(); gpu.downloadTexture(0, false);
                gpu.writeToFile(output().resolve(name + "-gpu.png"));
                int visible = 0, mismatch = 0;
                for (int y = 0; y < cpu.getHeight(); y++) for (int x = 0; x < cpu.getWidth(); x++) {
                    if ((gpu.getPixelRGBA(x, y) >>> 24) >= 26) visible++;
                    if (cpu.getPixelRGBA(x, y) != gpu.getPixelRGBA(x, y)) mismatch++;
                }
                JsonObject facts = new JsonObject(); facts.addProperty("source_mask", entry.getKey().toString());
                facts.addProperty("gpu_visible_alpha_pixels", visible); facts.addProperty("cpu_gpu_mismatched_pixels", mismatch);
                facts.addProperty("cpu_image", output().resolve(name + "-cpu.png").toString());
                facts.addProperty("gpu_image", output().resolve(name + "-gpu.png").toString());
                textures.add(facts);
                check(visible > 0 && mismatch == 0, "The actual GPU star texture must retain visible pixels and match its generated mask");
            } finally {
                RenderSystem.bindTexture(previousTexture); RenderSystem.activeTexture(active);
            }
        }
        current.add("actual_generated_gpu_textures", textures);
    }

    private static void checkShaderState() throws Exception {
        REPORT.addProperty("shader_state_checked_after_world_render", true);
        boolean actual = compatBoolean("activeShaderPack");
        REPORT.addProperty("actual_shader_pack_active", actual);
        REPORT.addProperty("iris_api_shader_pack_active", irisFlag("isShaderPackInUse"));
        check(actual == expectedShaderPack(), "Actual shader-pack state does not match the explicitly requested on/off run");
        check(irisFlag("isShaderPackInUse") == expectedShaderPack(), "The real Iris API must confirm the expected shader-pack state");
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
        REPORT.addProperty("status", failure == null ? "captured_real_world_frames_pending_visual_review" : "failed");
        REPORT.addProperty("safe_client_shutdown_requested", safe);
        if (failure != null) { REPORT.addProperty("failure", failure.toString()); LogUtils.getLogger().error("MODERN_ITE_COSMIC_SHADER_WORLD_AUDIT failed", failure); }
        if (previous != null) { previous.close(); previous = null; }
        try { write(); } catch (Exception writing) { LogUtils.getLogger().error("Cannot write cosmic shader world report", writing); }
        if (safe) mc.stop();
    }
}
