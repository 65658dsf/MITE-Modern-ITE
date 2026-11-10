package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** Actual inspect-key playback and native first-person GPU captures in an owned disposable world. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class GemInspectionClientAudit {
    private static final String CLIP = "modern_ite_gem_inspect";
    private static final String[] CASES = {"right-main", "left-main", "right-offhand"};
    private static final JsonObject REPORT = initial();
    private static boolean baked, started, finished, hidden, triggerObserved, transferObserved, returnObserved;
    private static int elapsed, ready, phase, phaseTicks, caseIndex, frameIndex;
    private static long lastCaptureNs, caseStartNs;
    private static float clipLength, maximumClipTime;
    private static String worldName;
    private static JsonObject current;
    private static Object handler;
    private static Class<?> handlerType;
    private static CompletableFuture<Void> pending;
    private static Method nativeFrame, stageRenderer;
    private static Object noHiddenHand;
    private static Class<?> managerType;
    private static Field itemOverrides;
    private static List<ItemStack> inventoryBefore;
    private static ItemStack inspectedSource;
    private static Object previewClip;
    private static JsonObject preview;
    private static int previewFrame, previewLastFrame;
    private static Method previewPlayAtTime, previewSourceHand, previewOverrides;
    private GemInspectionClientAudit() {}
    private static boolean enabled() { return Boolean.getBoolean("modern_ite.gemInspectionAudit"); }

    @EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event) { if (enabled()) baked = true; }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!enabled() || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!started && (mc.level != null || mc.getConnection() != null || mc.getSingleplayerServer() != null)) {
                REPORT.getAsJsonArray("failures").add("Refused a preexisting world/connection");
                finished = true; REPORT.addProperty("status", "aborted_existing_world"); write(); return;
            }
            if (!hidden) { GLFW.glfwHideWindow(mc.getWindow().getWindow()); hidden = true; }
            check(++elapsed < 3000, "Timed out during isolated inspect playback");
            if (!baked || mc.getOverlay() != null) return;
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                Path allowed = Path.of(System.getProperty("modern_ite.gemInspectionAuditAllowedRoot")).toAbsolutePath().normalize();
                check(directory.startsWith(allowed) && !directory.equals(allowed), "The inspect audit requires its own directory beneath build/verification");
                initializeReflection();
                Files.createDirectories(folder());
                started = true; mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                worldName = "modern-ite-gem-inspect-audit-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
                REPORT.addProperty("disposable_world", worldName); REPORT.addProperty("game_directory", directory.toString());
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(183994862L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                return;
            }
            if (mc.level == null || mc.player == null || mc.getConnection() == null || mc.getSingleplayerServer() == null) return;
            check(owned(mc), "Refused a world that this audit did not create");
            if (++ready < 100) return;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.setXRot(0); mc.player.xRotO = 0;
            mc.player.xBob = mc.player.xBobO = mc.player.yBob = mc.player.yBobO = 0;
            mc.player.attackAnim = mc.player.oAttackAnim = 0; mc.player.swinging = false;
            mc.player.setMainArm(caseIndex == 1 ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
            if (phase == 0) {
                if (caseIndex == CASES.length) { finish(mc); return; }
                current = new JsonObject(); current.addProperty("case", CASES[caseIndex]); current.addProperty("manual_pose_clock_advance", false); current.add("frames", new JsonArray());
                REPORT.getAsJsonArray("cases").add(current);
                Files.createDirectories(folder().resolve(CASES[caseIndex]));
                pending = prepareServer(mc, caseIndex == 2 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                phase = 1; phaseTicks = 0; return;
            }
            if (phase == 1) {
                if (!pending.isDone()) return; pending.join();
                if (++phaseTicks < 60) return;
                mc.setScreen(null);
                GemBoxNativeHandAudit.syncMovementEquipment(mc);
                inventoryBefore = inventorySnapshot(mc.player.getInventory());
                inspectedSource = (caseIndex == 2 ? mc.player.getOffhandItem() : mc.player.getMainHandItem()).copy();
                check(inspectedSource.getCount() == 7 && inspectedSource.has(DataComponents.CUSTOM_NAME), "The synchronized source fixture must retain count and components");
                current.addProperty("source_stack", inspectedSource.saveOptional(mc.level.registryAccess()).toString());
                Field keyField = Class.forName("punchy.client.input.AnimationKeybinds").getDeclaredField("inspectKey"); keyField.setAccessible(true);
                KeyMapping inspect = (KeyMapping) keyField.get(null);
                check(inspect != null, "Punchy inspect key is registered");
                KeyMapping.click(inspect.getKey());
                current.addProperty("trigger", "Native KeyMapping.click -> Punchy inspect key callback -> InspectStateMachine");
                current.addProperty("key", inspect.getKey().getName());
                phase = 2; phaseTicks = frameIndex = 0; maximumClipTime = 0; triggerObserved = false;
                transferObserved = returnObserved = false;
                lastCaptureNs = 0; caseStartNs = System.nanoTime(); return;
            }
            if (phase == 2) {
                ++phaseTicks;
                checkInventory(mc.player.getInventory());
                Object clip = handlerType.getMethod("getCurrentClip").invoke(handler);
                boolean active = inspectionClip(clip);
                if (active) {
                    triggerObserved = true;
                    clipLength = ((Number) clip.getClass().getMethod("getLength").invoke(clip)).floatValue();
                    maximumClipTime = Math.max(maximumClipTime, ((Number) handlerType.getMethod("getCurrentTime").invoke(handler)).floatValue());
                    current.addProperty("clip_duration_seconds", clipLength);
                    current.addProperty("resolved_clip_name", clip.getClass().getMethod("getName").invoke(clip).toString());
                }
                check(triggerObserved || phaseTicks < 80, "Inspect key failed to start the custom gem clip");
                boolean ended = triggerObserved && (!active || !(Boolean) handlerType.getMethod("isPlaying").invoke(handler));
                if (ended && phaseTicks > 10 || triggerObserved && phaseTicks > Math.ceil(clipLength * 20) + 40) {
                    check(frameIndex >= 20, "Too few native first-person samples");
                    check(maximumClipTime >= clipLength - .15F, "Native inspect playback ended before the expected final pose");
                    check(transferObserved && returnObserved, "Native item overrides must transfer the exact source gem to the other hand and return it");
                    current.addProperty("maximum_observed_clip_time", maximumClipTime); current.addProperty("captured_frames", frameIndex);
                    current.addProperty("source_components_preserved_in_transfer", true);
                    current.addProperty("client_inventory_unchanged", true);
                    pending = verifyServerInventory(mc); phase = 3;
                }
            }
            if (phase == 3 && pending.isDone()) {
                pending.join(); current.addProperty("server_inventory_unchanged", true);
                current.addProperty("status", "passed_native_key_and_render"); write();
                if (caseIndex == 0 && Boolean.getBoolean("modern_ite.gemInspectionSmoothPreview")) {
                    beginSmoothPreview(mc); phase = 4;
                } else { caseIndex++; phase = 0; }
            }
            if (phase == 5 && pending.isDone()) {
                pending.join(); preview.addProperty("client_and_server_inventory_unchanged", true);
                preview.addProperty("status", "passed_deterministic_preview"); write(); caseIndex++; phase = 0;
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post ignored) {
        if (!enabled() || finished) return;
        if (phase == 4) {
            Minecraft mc = Minecraft.getInstance(); if (!owned(mc) || mc.player == null) return;
            try { smoothPreviewFrame(mc); } catch (Throwable failure) { fail(mc, failure); }
            return;
        }
        if (phase != 2 || !triggerObserved) return;
        Minecraft mc = Minecraft.getInstance(); if (!owned(mc) || mc.player == null) return;
        long now = System.nanoTime(); if (now - lastCaptureNs < 33_000_000L) return;
        try {
            Object clip = handlerType.getMethod("getCurrentClip").invoke(handler);
            if (!inspectionClip(clip)) return;
            JsonObject facts = new JsonObject();
            float time = ((Number) handlerType.getMethod("getCurrentTime").invoke(handler)).floatValue();
            facts.addProperty("frame", frameIndex); facts.addProperty("clip_time", time);
            facts.addProperty("wall_seconds", (now - caseStartNs) / 1_000_000_000D);
            facts.addProperty("dual_hand_pose_active", (Boolean) handlerType.getMethod("isDualHandedPoseActive", float.class).invoke(handler, 1F));
            facts.addProperty("resolved_clip_name", clip.getClass().getMethod("getName").invoke(clip).toString());
            captureOverrides(mc, facts);
            stageRenderer.invoke(null, mc);
            try (NativeImage image = (NativeImage) nativeFrame.invoke(null, mc, facts, -1, false, noHiddenHand, true)) {
                if (time >= .65F && time <= 4.5F)
                    check(facts.get("foreground_pixels").getAsInt() > 1000, "Native first-person presentation contains no visible held scene");
                image.writeToFile(folder().resolve(CASES[caseIndex]).resolve(String.format(java.util.Locale.ROOT, "frame-%04d.png", frameIndex)));
            }
            // Keep direct draw evidence while avoiding duplicated full Punchy settings in every frame.
            facts.remove("runtime_before"); facts.remove("runtime_after"); facts.remove("native_hand_events");
            current.getAsJsonArray("frames").add(facts); frameIndex++; lastCaptureNs = now;
        } catch (Throwable failure) { fail(mc, failure); }
    }

    /** Separate visual artifact after the real key/clock proof; never counted as a natural playback case. */
    private static void beginSmoothPreview(Minecraft mc) throws Exception {
        previewClip = managerType.getMethod("resolveNamedClip", String.class, Minecraft.class, InteractionHand.class)
                .invoke(null, CLIP, mc, InteractionHand.MAIN_HAND);
        check(inspectionClip(previewClip), "Smooth preview requires the loaded gemstone inspection clip");
        float duration = ((Number) previewClip.getClass().getMethod("getLength").invoke(previewClip)).floatValue();
        previewPlayAtTime = handlerType.getMethod("playAtTime", previewClip.getClass(), float.class, boolean.class, boolean.class, float.class);
        previewSourceHand = managerType.getMethod("setSourceHand", Minecraft.class, InteractionHand.class);
        previewOverrides = managerType.getMethod("updateItemOverrides", Minecraft.class);
        previewFrame = 0; previewLastFrame = Math.round(duration * 30);
        preview = new JsonObject(); preview.addProperty("manual_pose_clock_advance", true);
        preview.addProperty("native_key_proof", "cases[0]; recorded before this deterministic sampling pass");
        preview.addProperty("method", "Native PoseHandler.playAtTime at frame/30, native timeline item overrides, complete native first-person render");
        preview.addProperty("fps", 30); preview.addProperty("clip_duration_seconds", duration);
        preview.addProperty("texture_atlas_clock", "natural; only the hand-pose clock is sampled");
        preview.add("frames", new JsonArray()); REPORT.add("deterministic_preview", preview);
        REPORT.addProperty("manual_pose_clock_advance", true);
        Files.createDirectories(folder().resolve("smooth-preview")); write();
    }

    private static void smoothPreviewFrame(Minecraft mc) throws Exception {
        float time = previewFrame / 30F;
        // Restart the same loaded clip at an exact sample. Skip crossfade so each
        // image represents that pose, independent of expensive GPU readback time.
        handlerType.getMethod("setActiveArm", HumanoidArm.class).invoke(handler, (Object) null);
        handlerType.getMethod("setLooping", boolean.class).invoke(handler, false);
        previewPlayAtTime.invoke(handler, previewClip, 1F, true, false, time);
        previewSourceHand.invoke(null, mc, InteractionHand.MAIN_HAND);
        previewOverrides.invoke(null, mc);
        checkInventory(mc.player.getInventory());
        JsonObject facts = new JsonObject(); facts.addProperty("frame", previewFrame);
        facts.addProperty("clip_time", time); facts.addProperty("manual_pose_clock_advance", true);
        facts.addProperty("dual_hand_pose_active", (Boolean) handlerType.getMethod("isDualHandedPoseActive", float.class).invoke(handler, 1F));
        captureOverrides(mc, facts);
        stageRenderer.invoke(null, mc);
        try (NativeImage image = (NativeImage) nativeFrame.invoke(null, mc, facts, -1, false, noHiddenHand, true)) {
            if (time >= .65F && time <= 4.5F)
                check(facts.get("foreground_pixels").getAsInt() > 1000, "Sampled presentation contains no visible held scene");
            image.writeToFile(folder().resolve("smooth-preview").resolve(String.format(java.util.Locale.ROOT, "frame-%04d.png", previewFrame)));
        }
        facts.remove("runtime_before"); facts.remove("runtime_after"); facts.remove("native_hand_events");
        preview.getAsJsonArray("frames").add(facts);
        if (previewFrame++ >= previewLastFrame) {
            preview.addProperty("captured_frames", previewFrame);
            handlerType.getMethod("stopImmediateWithBlendOut").invoke(handler);
            managerType.getMethod("clearSourceHand").invoke(null); previewOverrides.invoke(null, mc);
            pending = verifyServerInventory(mc); phase = 5;
        }
    }

    private static CompletableFuture<Void> prepareServer(Minecraft mc, InteractionHand hand) {
        var result = new CompletableFuture<Void>(); var server = mc.getSingleplayerServer(); UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid); check(player != null, "Native integrated-server player must be present");
                var level = player.serverLevel();
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setBlockAndUpdate(new BlockPos(x, 127, z), Blocks.STONE.defaultBlockState());
                level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
                player.teleportTo(.5, 128, .5); player.setYRot(0); player.setXRot(0);
                player.getInventory().clearContent(); player.getInventory().selected = 0;
                ItemStack gem = ExtremeGear.gem(GemType.RECOVER, 6); gem.setCount(7);
                gem.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 inspection fixture"));
                player.setItemInHand(hand, gem);
                player.setItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges(); result.complete(null);
            } catch (Throwable failure) { result.completeExceptionally(failure); }
        }); return result;
    }

    private static void initializeReflection() throws Exception {
        managerType = Class.forName("punchy.client.animation.PunchyAnimationManager");
        handler = managerType.getField("POSE_HANDLER").get(null); handlerType = handler.getClass();
        itemOverrides = managerType.getDeclaredField("ITEM_OVERRIDES"); itemOverrides.setAccessible(true);
        Class<?> hiddenType = Class.forName("io.github.ningmeng123.modernite.client.GemBoxNativeHandAudit$HiddenHand");
        noHiddenHand = hiddenType.getEnumConstants()[0];
        nativeFrame = GemBoxNativeHandAudit.class.getDeclaredMethod("frame", Minecraft.class, JsonObject.class, int.class, boolean.class, hiddenType, boolean.class);
        nativeFrame.setAccessible(true);
        stageRenderer = GemBoxNativeHandAudit.class.getDeclaredMethod("stageRenderer", Minecraft.class); stageRenderer.setAccessible(true);
    }
    private static boolean inspectionClip(Object clip) throws Exception {
        if (clip == null) return false;
        String name = clip.getClass().getMethod("getName").invoke(clip).toString();
        return name.equals(CLIP) || name.equals(CLIP + "_right") || name.equals(CLIP + "_left");
    }
    private static List<ItemStack> inventorySnapshot(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) result.add(inventory.getItem(slot).copy()); return result;
    }
    private static void checkInventory(Inventory inventory) {
        check(inventory.getContainerSize() == inventoryBefore.size(), "Inventory size changed during visual inspection");
        for (int slot = 0; slot < inventoryBefore.size(); slot++) check(ItemStack.matches(inventoryBefore.get(slot), inventory.getItem(slot)), "Visual inspection mutated inventory slot " + slot);
    }
    private static CompletableFuture<Void> verifyServerInventory(Minecraft mc) {
        var result = new CompletableFuture<Void>(); var server = mc.getSingleplayerServer(); UUID uuid = mc.player.getUUID();
        server.execute(() -> { try { ServerPlayer player = server.getPlayerList().getPlayer(uuid); check(player != null, "Server player still present"); checkInventory(player.getInventory()); result.complete(null); } catch (Throwable failure) { result.completeExceptionally(failure); } }); return result;
    }
    private static void captureOverrides(Minecraft mc, JsonObject facts) throws Exception {
        JsonObject overrides = new JsonObject(); facts.add("native_item_overrides", overrides);
        var map = (java.util.Map<?, ?>) itemOverrides.get(null);
        ItemStack sourceRender = ItemStack.EMPTY, oppositeRender = ItemStack.EMPTY;
        HumanoidArm sourceArm = caseIndex == 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        for (HumanoidArm arm : HumanoidArm.values()) {
            ItemStack actual = arm == mc.player.getMainArm() ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
            ItemStack rendered = (ItemStack) managerType.getMethod("resolveRenderStack", Player.class, HumanoidArm.class, ItemStack.class).invoke(null, mc.player, arm, actual);
            JsonObject armFacts = new JsonObject(); overrides.add(arm.name().toLowerCase(java.util.Locale.ROOT), armFacts);
            armFacts.addProperty("resolved_stack", rendered.saveOptional(mc.level.registryAccess()).toString());
            armFacts.addProperty("matches_source_count_and_components", ItemStack.matches(inspectedSource, rendered));
            Object state = map.get(arm);
            if (state != null) for (String fieldName : List.of("hidden", "forceShow", "stack")) {
                Field field = state.getClass().getDeclaredField(fieldName); field.setAccessible(true); Object value = field.get(state);
                if (value instanceof ItemStack stack) armFacts.addProperty(fieldName, stack.saveOptional(mc.level.registryAccess()).toString());
                else armFacts.addProperty(fieldName, String.valueOf(value));
            }
            if (arm == sourceArm) sourceRender = rendered; else oppositeRender = rendered;
        }
        if (sourceRender.isEmpty() && ItemStack.matches(inspectedSource, oppositeRender)) transferObserved = true;
        if (transferObserved && ItemStack.matches(inspectedSource, sourceRender) && oppositeRender.isEmpty()) returnObserved = true;
        facts.addProperty("native_source_arm", String.valueOf(managerType.getMethod("getSourceArm").invoke(null)));
        facts.addProperty("client_inventory_unchanged", true); checkInventory(mc.player.getInventory());
    }
    private static boolean owned(Minecraft mc) { return started && worldName != null && mc.getSingleplayerServer() != null && worldName.equals(mc.getSingleplayerServer().getWorldData().getLevelName()); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static JsonObject initial() { JsonObject value = new JsonObject(); value.addProperty("author", "NingMeng123"); value.addProperty("timestamp", Instant.now().toString()); value.addProperty("scope", "Natural native inspect key playback, complete GameRenderer/ItemInHandRenderer/Punchy/AR GPU frames; owned disposable world only"); value.addProperty("manual_pose_clock_advance", false); value.add("cases", new JsonArray()); value.add("failures", new JsonArray()); return value; }
    private static Path output() { return Path.of(System.getProperty("modern_ite.gemInspectionAuditOutput", "build/verification/gem-inspection/native/audit.json")).toAbsolutePath().normalize(); }
    private static Path folder() { return output().getParent(); }
    private static void fail(Minecraft mc, Throwable failure) { REPORT.getAsJsonArray("failures").add(failure.toString()); LogUtils.getLogger().error("MODERN_ITE_GEM_INSPECTION_AUDIT", failure); finish(mc); }
    private static void finish(Minecraft mc) { if (finished) return; finished = true; boolean safe = owned(mc) || started && mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null; REPORT.addProperty("status", REPORT.getAsJsonArray("failures").isEmpty() ? "passed" : "failed"); REPORT.addProperty("safe_client_shutdown_requested", safe); write(); if (safe) mc.stop(); }
    private static void write() { try { Files.createDirectories(folder()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n", StandardCharsets.UTF_8); LogUtils.getLogger().info("MODERN_ITE_GEM_INSPECTION_AUDIT {} report={}", REPORT.get("status"), output()); } catch (Exception failure) { LogUtils.getLogger().error("Cannot write gem inspect audit", failure); } }
}
