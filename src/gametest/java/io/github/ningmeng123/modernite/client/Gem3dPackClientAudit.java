package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/** Real OBJ geometry, image and native atlas observations in an isolated title screen. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class Gem3dPackClientAudit {
    private static final int TURN_FRAMES = 120, OBSERVE_TICKS = 180;
    private static final List<Gem> GEMS = new ArrayList<>();
    private static final JsonObject REPORT = report();
    private static boolean baked, finished, hidden, imagesCaptured;
    private static int waiting, titleTicks, firstTick = -1, lastTick = -1, turnFrame;
    private static NativeImage atlasPixels;

    private Gem3dPackClientAudit() {}
    private static boolean enabled() { return Boolean.getBoolean("modern_ite.gem3dPackAudit"); }
    private static boolean packExpected() { return Boolean.parseBoolean(System.getProperty("modern_ite.gem3dPackExpected", "true")); }

    @EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event) {
            if (enabled()) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!enabled() || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (attached(mc)) {
            REPORT.getAsJsonArray("failures").add("Refused to inspect or close a client attached to a world/server");
            REPORT.addProperty("status", "aborted_world_present");
            finished = true; closePixels(); write(); return;
        }
        if (!hidden) { GLFW.glfwHideWindow(mc.getWindow().getWindow()); hidden = true; }
        try {
            check(++waiting <= 1800, "Timed out waiting for native model reload");
            if (!baked || mc.getOverlay() != null) return;
            if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
            if (!(mc.screen instanceof TitleScreen) || ++titleTicks < 20) return;
            if (GEMS.isEmpty()) initialize(mc);
        } catch (Throwable failure) { fail(mc, failure); }
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (!enabled() || finished || GEMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getScreen() instanceof TitleScreen) || mc.getOverlay() != null || attached(mc)) return;
        try {
            if (waiting == lastTick) return;
            event.getGuiGraphics().flush();
            if (!imagesCaptured) {
                try (NativeImage image = renderGrid(mc, false, 0)) { image.writeToFile(folder().resolve("all-gems.png")); }
                try (NativeImage image = renderGrid(mc, true, 35)) { image.writeToFile(folder().resolve("showcase.png")); }
                imagesCaptured = true;
            }
            // Every gem takes the real item draw path each sampled tick, keeping the native
            // visibility-scheduled atlas clock active without manually advancing it.
            try (NativeImage image = renderGrid(mc, false, 0)) {
                if (firstTick >= 0 && waiting - firstTick == 100) image.writeToFile(folder().resolve("all-gems-later.png"));
            }
            if (turnFrame < TURN_FRAMES) {
                try (NativeImage image = renderGrid(mc, true, 360F * turnFrame / TURN_FRAMES)) {
                    image.writeToFile(folder().resolve(String.format(java.util.Locale.ROOT, "turntable-%02d.png", turnFrame)));
                }
                turnFrame++;
            }
            if (firstTick < 0) firstTick = waiting;
            captureAtlas(mc);
            lastTick = waiting;
            if (waiting - firstTick >= OBSERVE_TICKS) {
                for (Gem gem : GEMS) {
                    gem.facts.addProperty("actual_gpu_unique_frames", gem.observed.size());
                    gem.facts.addProperty("actual_gpu_transitions", gem.transitions);
                    boolean passed = gem.observed.size() >= 2 && gem.transitions > 0
                            && gem.sourceHashes.containsAll(gem.observed);
                    gem.facts.addProperty("status", passed ? "passed" : "failed");
                    if (!passed) REPORT.getAsJsonArray("failures").add(gem.id + ": native atlas did not animate the source frames");
                }
                REPORT.addProperty("observation_span_ticks", waiting - firstTick);
                REPORT.addProperty("turntable_frames", turnFrame);
                REPORT.addProperty("turntable_capture", "native ItemRenderer FIXED, loaded item geometry, 3 degree yaw steps");
                finish(mc);
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private static void initialize(Minecraft mc) throws Exception {
        Files.createDirectories(folder());
        for (var item : ExtremeGear.GEMS) GEMS.add(new Gem(mc, new ItemStack(item.get())));
        for (var item : ExtremeGear.SHARDS) GEMS.add(new Gem(mc, new ItemStack(item.get())));
        check(GEMS.size() == 65, "Expected all 65 gem registrations");
        REPORT.addProperty("gem_count", GEMS.size());
        REPORT.addProperty("genuine_3d_pack_expected", packExpected());
        REPORT.addProperty("model_baking_completed", baked);
        REPORT.addProperty("native_title_screen", mc.screen instanceof TitleScreen);
        JsonArray resources = new JsonArray();
        for (var entry : mc.getResourceManager().listResources("punchy", id -> id.getPath().endsWith(".json")).entrySet()) {
            String contents;
            try (var stream = entry.getValue().open()) { contents = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
            if (contents.contains("modern_ite_gem_inspect")) {
                JsonObject resource = new JsonObject();
                resource.addProperty("id", entry.getKey().toString());
                resource.addProperty("pack", entry.getValue().sourcePackId());
                resource.addProperty("valid_json", JsonParser.parseString(contents).isJsonObject());
                resources.add(resource);
            }
        }
        REPORT.add("inspection_pack_resources", resources);
        check(resources.size() >= 2, "Missing gem custom inspect clip/compat resources in the native resource manager");
        inspectBindings(mc);
    }

    /** Resolve the actual loaded optional Punchy bindings without opening a world or changing its state. */
    private static void inspectBindings(Minecraft mc) throws Exception {
        JsonObject facts = new JsonObject(); REPORT.add("punchy_inspection", facts);
        Class<?> resolver;
        try { resolver = Class.forName("punchy.client.state.CustomAnimationResolver"); }
        catch (ClassNotFoundException absent) { facts.addProperty("status", "not_installed_resources_only"); return; }
        Class<?> entryType = Class.forName("punchy.config.PunchyTuningConfig$CustomAnimationEntry");
        var find = resolver.getDeclaredMethod("findEntry", ItemStack.class, String.class); find.setAccessible(true);
        var resolve = resolver.getDeclaredMethod("resolveNamedOrTypeFallback", ItemStack.class, String.class,
                entryType, Minecraft.class, InteractionHand.class); resolve.setAccessible(true);
        int resolved = 0;
        for (Gem gem : GEMS) {
            Object entry = find.invoke(null, gem.stack, "inspect");
            check(entry != null, "Punchy did not load the inspect binding: " + gem.id);
            check("modern_ite_gem_inspect".equals(entryType.getField("name").get(entry)), "Unexpected native inspect binding: " + gem.id);
            for (InteractionHand hand : InteractionHand.values()) {
                Object lookup = resolve.invoke(null, gem.stack, "inspect", entry, mc, hand);
                check(lookup != null, "Punchy inspect resolver returned no lookup: " + gem.id + " " + hand);
                var clipAccessor = lookup.getClass().getDeclaredMethod("clip"); clipAccessor.setAccessible(true);
                var fallbackAccessor = lookup.getClass().getDeclaredMethod("usedTypeFallback"); fallbackAccessor.setAccessible(true);
                Object clip = clipAccessor.invoke(lookup);
                check(clip != null && !(Boolean) fallbackAccessor.invoke(lookup), "Punchy inspect clip fell back or is missing: " + gem.id + " " + hand);
                Class<?> clipType = clip.getClass();
                check("modern_ite_gem_inspect".equals(clipType.getMethod("getName").invoke(clip)), "Incorrect resolved gem clip: " + gem.id);
                float length = ((Number) clipType.getMethod("getLength").invoke(clip)).floatValue();
                check(Math.abs(length - 5.2F) < .001F && !(Boolean) clipType.getMethod("isLoop").invoke(clip), "Incorrect gem clip timing");
                var tracks = (java.util.Map<?, ?>) clipType.getMethod("getTracks").invoke(clip);
                check(tracks.keySet().containsAll(List.of("right_arm", "left_arm", "itemgrip_right", "itemgrip_left")), "Missing inspect hand/item tracks");
                for (String marker : List.of("use_keyframes_v2", "anim_speed_1", "dual_handed"))
                    check((Boolean) clipType.getMethod("hasEffectAny", String.class).invoke(clip, marker), "Missing native inspect marker " + marker);
                resolved++;
            }
            gem.facts.addProperty("native_inspect_both_hands", true);
        }
        facts.addProperty("status", "passed"); facts.addProperty("item_bindings", GEMS.size());
        facts.addProperty("resolved_hand_clips", resolved); facts.addProperty("clip", "modern_ite_gem_inspect");
        facts.addProperty("clip_duration_seconds", 5.2); facts.addProperty("clip_tracks", 4);
        facts.addProperty("global_speed_override", 1); facts.addProperty("used_type_fallback", false);
    }

    private static NativeImage renderGrid(Minecraft mc, boolean showcase, float angle) {
        int width = showcase ? 960 : 960, height = showcase ? 640 : 1760;
        TextureTarget target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        try {
            target.setClearColor(0.085F, 0.105F, 0.14F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); Lighting.setupFor3DItems();
            int count = showcase ? 6 : GEMS.size();
            for (int i = 0; i < count; i++) {
                int sourceIndex = showcase ? new int[]{12, 7, 20, 27, 40, 59}[i] : i;
                int slot = showcase ? i : i >= 60 ? i + 1 : i;
                int columns = showcase ? 3 : 6, cell = showcase ? 320 : 160;
                PoseStack pose = new PoseStack();
                pose.translate((slot % columns + .5F) * cell, (slot / columns + .5F) * cell, 100);
                float scale = showcase ? 270 : 135;
                pose.scale(scale, -scale, scale);
                if (showcase) {
                    pose.mulPose(Axis.XP.rotationDegrees(20));
                    pose.mulPose(Axis.YP.rotationDegrees(angle));
                }
                mc.getItemRenderer().renderStatic(GEMS.get(sourceIndex).stack,
                        showcase ? ItemDisplayContext.FIXED : ItemDisplayContext.GUI,
                        15728880, OverlayTexture.NO_OVERLAY, pose, mc.renderBuffers().bufferSource(), null, 0);
            }
            mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch();
            view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); Lighting.setupFor3DItems();
            mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static void captureAtlas(Minecraft mc) throws Exception {
        TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            GlStateManager._bindTexture(atlas.getId());
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (atlasPixels == null) atlasPixels = new NativeImage(width, height, false);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            atlasPixels.downloadTexture(0, false);
            for (Gem gem : GEMS) {
                String hash = pixelHash(atlasPixels, gem.sprite.getX(), gem.sprite.getY(), gem.width, gem.height);
                if (gem.previous != null && !gem.previous.equals(hash)) gem.transitions++;
                gem.observed.add(hash); gem.previous = hash;
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer); GlStateManager._bindTexture(binding);
        }
    }

    private static final class Gem {
        final ItemStack stack;
        final ResourceLocation id;
        final TextureAtlasSprite sprite;
        final int width, height;
        final JsonObject facts = new JsonObject();
        final Set<String> sourceHashes = new LinkedHashSet<>(), observed = new LinkedHashSet<>();
        String previous;
        int transitions;

        Gem(Minecraft mc, ItemStack stack) throws Exception {
            this.stack = stack; id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            BakedModel model = mc.getModelManager().getModel(ModelResourceLocation.inventory(id));
            check(model != mc.getModelManager().getMissingModel(), "Missing gem model: " + id);
            sprite = model.getParticleIcon();
            check(!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing gem texture: " + id);
            width = sprite.contents().width(); height = sprite.contents().height();
            int size = Integer.getInteger("modern_ite.gem3dTextureSize", packExpected() ? 64 : 16);
            int frameCount = Integer.getInteger("modern_ite.gem3dFrameCount", 28);
            check(width == size && height == size, "Incorrect pack sprite dimensions: " + id + " " + width + "x" + height);
            var animation = sprite.contents().metadata().getSection(AnimationMetadataSection.SERIALIZER).orElseThrow();
            int[] frames = sprite.contents().getUniqueFrames().toArray();
            check(frames.length == frameCount, "Incorrect native frame count: " + id);
            int[] cooldowns = {0}, expected = {0};
            animation.forEachFrame((index, ticks) -> {
                check(index == expected[0], "Reordered gem frame sequence: " + id);
                if (index == 0) { check(ticks >= 30 && ticks <= 100, "Cooldown outside 1.5-5 seconds: " + id); cooldowns[0]++; }
                else check(ticks == 1, "Active frame must last one tick: " + id);
                expected[0] = (expected[0] + 1) % frameCount;
            });
            check(cooldowns[0] == 12 && expected[0] == 0, "Expected twelve cooldown/shine cycles: " + id);
            var modelResource = mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/item/" + id.getPath() + ".json")).orElseThrow();
            try (var reader = modelResource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (packExpected()) check(json.has("loader") && json.get("loader").getAsString().equals("neoforge:obj"), "Gem override must use the native OBJ loader: " + id);
            }
            List<BakedQuad> quads = new ArrayList<>();
            for (BakedModel pass : model.getRenderPasses(stack, true)) {
                quads.addAll(pass.getQuads(null, null, RandomSource.create(0)));
                for (Direction face : Direction.values()) quads.addAll(pass.getQuads(null, face, RandomSource.create(0)));
            }
            check(quads.size() > (packExpected() ? 16 : 0), "Missing or insufficient gemstone mesh: " + id);
            double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (BakedQuad quad : quads) {
                int[] data = quad.getVertices(); int stride = data.length / 4;
                for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                    float value = Float.intBitsToFloat(data[vertex * stride + axis]);
                    check(Float.isFinite(value), "Nonfinite gem mesh coordinate: " + id);
                    min[axis] = Math.min(min[axis], value); max[axis] = Math.max(max[axis], value);
                }
            }
            if (packExpected()) check(max[0] - min[0] > .35 && max[1] - min[1] > .3 && max[2] - min[2] > .35,
                    "Gem is still flat or has insufficient true depth: " + id);
            facts.addProperty("item", id.toString()); facts.addProperty("quad_count", quads.size());
            facts.addProperty("model_class", model.getClass().getName()); facts.addProperty("model_pack", modelResource.sourcePackId());
            facts.addProperty("frame_width", width); facts.addProperty("frame_height", height); facts.addProperty("unique_frame_indices", frames.length);
            JsonArray spans = new JsonArray(); for (int axis = 0; axis < 3; axis++) spans.add(max[axis] - min[axis]); facts.add("baked_mesh_xyz_span", spans);
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(sprite.contents().name().getNamespace(), "textures/" + sprite.contents().name().getPath() + ".png");
            byte[] png; try (var stream = mc.getResourceManager().getResource(texture).orElseThrow().open()) { png = stream.readAllBytes(); }
            try (NativeImage image = NativeImage.read(new ByteArrayInputStream(png))) {
                int columns = image.getWidth() / width;
                for (int frame : frames) sourceHashes.add(pixelHash(image, frame % columns * width, frame / columns * height, width, height));
            }
            facts.addProperty("source_unique_frames", sourceHashes.size()); REPORT.getAsJsonArray("gems").add(facts);
        }
    }

    private static String pixelHash(NativeImage image, int x0, int y0, int width, int height) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int color = image.getPixelRGBA(x0 + x, y0 + y); if ((color >>> 24) == 0) color = 0;
            for (int shift = 0; shift < 32; shift += 8) digest.update((byte)(color >>> shift));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static JsonObject report() {
        JsonObject result = new JsonObject(); result.addProperty("author", "NingMeng123"); result.addProperty("timestamp", Instant.now().toString());
        result.addProperty("world_created", false); result.addProperty("manual_animation_advance", false);
        result.add("gems", new JsonArray()); result.add("failures", new JsonArray()); return result;
    }
    private static Path output() { return Path.of(System.getProperty("modern_ite.gem3dPackAuditOutput", "build/verification/gem-3d-pack/native/audit.json")).toAbsolutePath().normalize(); }
    private static Path folder() { return output().getParent(); }
    private static boolean attached(Minecraft mc) { return mc.level != null || mc.getConnection() != null || mc.getSingleplayerServer() != null; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void closePixels() { if (atlasPixels != null) { atlasPixels.close(); atlasPixels = null; } }
    private static void fail(Minecraft mc, Throwable failure) { REPORT.getAsJsonArray("failures").add(failure.toString()); LogUtils.getLogger().error("MODERN_ITE_GEM_3D_AUDIT", failure); finish(mc); }
    private static void finish(Minecraft mc) { finished = true; closePixels(); REPORT.addProperty("status", REPORT.getAsJsonArray("failures").isEmpty() ? "passed" : "failed"); write(); if (!attached(mc)) mc.stop(); }
    private static void write() {
        try { Files.createDirectories(folder()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n", StandardCharsets.UTF_8);
            LogUtils.getLogger().info("MODERN_ITE_GEM_3D_AUDIT {} report={}", REPORT.get("status"), output());
        } catch (Exception failure) { LogUtils.getLogger().error("Cannot write 3D gem audit", failure); }
    }
}
