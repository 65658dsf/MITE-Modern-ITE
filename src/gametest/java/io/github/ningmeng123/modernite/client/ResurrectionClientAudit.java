package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.content.ResurrectionBlockEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix4f;

/** Disposable title-screen GPU audit of the altar and both owner skin geometries. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class ResurrectionClientAudit {
    private static final int WIDTH = 320, HEIGHT = 400;
    private static boolean baked, finished;
    private static int waiting, titleTicks;

    private ResurrectionClientAudit() {}

    @EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event) {
            if (Boolean.getBoolean("modern_ite.resurrectionAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("modern_ite.resurrectionAudit") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (!unconnected(mc)) {
            finished = true;
            JsonObject report = initialReport();
            report.addProperty("status", "aborted_world_present");
            report.addProperty("safe_client_shutdown_requested", false);
            report.getAsJsonArray("failures").add("Refused to audit or close a client attached to a world/server");
            write(report);
            return;
        }
        if (++waiting > 1800) {
            finished = true;
            JsonObject report = initialReport();
            report.getAsJsonArray("failures").add("Timed out waiting for the isolated title screen and completed resource reload");
            finish(mc, report);
        }
        if (!baked || mc.getOverlay() != null) return;
        if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
        if (mc.screen instanceof TitleScreen) titleTicks++;
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("modern_ite.resurrectionAudit") || finished || !baked || titleTicks < 20) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getScreen() instanceof TitleScreen) || mc.getOverlay() != null || !unconnected(mc)) return;
        finished = true;
        JsonObject report = initialReport();
        try {
            event.getGuiGraphics().flush();
            capture(mc, report);
        } catch (Throwable failure) {
            report.getAsJsonArray("failures").add(failure.toString());
            LogUtils.getLogger().error("MODERN_ITE_RESURRECTION_AUDIT", failure);
        }
        finish(mc, report);
    }

    private static boolean unconnected(Minecraft mc) {
        return mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null;
    }

    private static JsonObject initialReport() {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("scope", "Native baked altar/item sprites, registered block entity renderer, owner portrait GPU rendering, wide/slim geometry and rotation controls; no world opened");
        report.addProperty("world_created", false);
        report.addProperty("model_baking_completed", baked);
        report.add("failures", new JsonArray());
        return report;
    }

    private static void capture(Minecraft mc, JsonObject report) throws Exception {
        var state = ExtremeContent.RESURRECTION.get().defaultBlockState();
        var entity = new ResurrectionBlockEntity(BlockPos.ZERO, state);
        var nativeRenderer = mc.getBlockEntityRenderDispatcher().getRenderer(entity);
        check(nativeRenderer instanceof ResurrectionArrayRenderer, "Resurrection block entity renderer was not registered during native resource reload");
        var renderer = (ResurrectionArrayRenderer)nativeRenderer;
        report.addProperty("registered_renderer", renderer.getClass().getName());
        ItemStack stack = new ItemStack(ExtremeContent.RESURRECTION.get());
        BakedModel item = mc.getItemRenderer().getModel(stack, null, null, 0);
        report.add("block_model", geometry(mc, mc.getBlockRenderer().getBlockModel(state)));
        report.add("item_model", geometry(mc, item));
        PlayerSkin wide = skin("wide/steve", PlayerSkin.Model.WIDE);
        PlayerSkin slimControl = skin("wide/steve", PlayerSkin.Model.SLIM);
        PlayerSkin alex = skin("slim/alex", PlayerSkin.Model.SLIM);
        for (PlayerSkin skin : List.of(wide, slimControl, alex))
            check(mc.getResourceManager().getResource(skin.texture()).isPresent(), "Missing skin control texture: " + skin.texture());
        List<NativeImage> frames = new ArrayList<>();
        try {
            frames.add(render(mc, renderer, wide, 0, false, "Wide / Steve", "0 seconds"));
            frames.add(render(mc, renderer, wide, 200, false, "Wide / Steve", "10 seconds"));
            frames.add(render(mc, renderer, slimControl, 0, false, "Slim geometry control", "Same Steve texture"));
            frames.add(render(mc, renderer, alex, 0, false, "Slim / Alex", "0 seconds"));
            frames.add(render(mc, renderer, null, 0, true, "Inventory model", "Nether-star inlays"));
            try (NativeImage emptyCover = render(mc, renderer, null, 0, false, "Glass cover", "Visibility control")) {
                int visiblePortrait = changed(frames.getFirst(), emptyCover);
                check(visiblePortrait > 1000, "The owner's portrait must remain visible through the glass cover: " + visiblePortrait);
                report.addProperty("portrait_visible_through_glass_pixels", visiblePortrait);
            }
            int rotated = changed(frames.get(0), frames.get(1));
            int geometry = changed(frames.get(0), frames.get(2));
            check(rotated > 100, "Owner portrait did not visibly rotate between t=0 and t=200: " + rotated);
            check(geometry > 30, "Wide/slim geometry did not change for the same texture: " + geometry);
            report.addProperty("rotation_changed_pixels", rotated);
            report.addProperty("wide_slim_geometry_changed_pixels", geometry);
            JsonArray foreground = new JsonArray();
            for (NativeImage frame : frames) {
                int count = foreground(frame);
                check(count > 1000, "GPU preview frame is empty or too small: " + count);
                foreground.add(count);
            }
            report.add("frame_foreground_pixels", foreground);
            Path preview = output().getParent().resolve("preview.png");
            Files.createDirectories(preview.getParent());
            try (NativeImage sheet = new NativeImage(WIDTH * frames.size(), HEIGHT, false)) {
                for (int column = 0; column < frames.size(); column++)
                    for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
                        sheet.setPixelRGBA(column * WIDTH + x, y, frames.get(column).getPixelRGBA(x, y));
                sheet.writeToFile(preview);
            }
            report.addProperty("preview", preview.toString());
        } finally {
            frames.forEach(NativeImage::close);
        }
    }

    private static PlayerSkin skin(String path, PlayerSkin.Model model) {
        return new PlayerSkin(ResourceLocation.withDefaultNamespace("textures/entity/player/" + path + ".png"), null, null, null, model, true);
    }

    private static JsonObject geometry(Minecraft mc, BakedModel model) {
        check(model != null && model != mc.getModelManager().getMissingModel(), "Expected a real baked altar model");
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, RandomSource.create(0)));
        for (Direction direction : Direction.values()) quads.addAll(model.getQuads(null, direction, RandomSource.create(0)));
        check(quads.size() > 24 && model.isGui3d(), "Altar must contain an actual 3D model");
        Set<String> sprites = new HashSet<>();
        quads.forEach(quad -> {
            check(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing stitched altar texture");
            sprites.add(quad.getSprite().contents().name().toString());
        });
        check(sprites.stream().anyMatch(name -> name.contains("nether_star")), "Altar must include visible nether-star inlays in its static model");
        check(sprites.contains("minecraft:block/glass"), "Altar must include the clear glass display cover");
        JsonObject facts = new JsonObject();
        facts.addProperty("baked_quads", quads.size());
        JsonArray names = new JsonArray();
        sprites.stream().sorted().forEach(names::add);
        facts.add("sprites", names);
        return facts;
    }

    private static NativeImage render(Minecraft mc, ResurrectionArrayRenderer renderer, PlayerSkin skin, float ticks,
            boolean inventory, String title, String subtitle) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, WIDTH, HEIGHT, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            Lighting.setupFor3DItems();
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(WIDTH / 2F, 300, 100);
            pose.scale(210, -210, 210);
            pose.mulPose(Axis.XP.rotationDegrees(24));
            pose.mulPose(Axis.YP.rotationDegrees(32));
            var buffers = graphics.bufferSource();
            if (inventory) {
                mc.getItemRenderer().renderStatic(new ItemStack(ExtremeContent.RESURRECTION.get()), ItemDisplayContext.FIXED,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, null, 0);
            } else {
                pose.translate(-.5, 0, -.5);
                mc.getBlockRenderer().renderSingleBlock(ExtremeContent.RESURRECTION.get().defaultBlockState(), pose, buffers,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                if (skin != null) renderer.renderPortrait(skin, ticks, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
            graphics.flush();
            graphics.drawCenteredString(mc.font, title, WIDTH / 2, 14, 0xFFEEF5FF);
            graphics.drawCenteredString(mc.font, subtitle, WIDTH / 2, 30, 0xFF91A7BC);
            graphics.flush();
            mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            mc.getMainRenderTarget().bindWrite(true);
            target.destroyBuffers();
        }
    }

    private static int changed(NativeImage first, NativeImage second) {
        int count = 0;
        for (int y = 50; y < 340; y++) for (int x = 20; x < WIDTH - 20; x++)
            if (delta(first.getPixelRGBA(x, y), second.getPixelRGBA(x, y)) > 24) count++;
        return count;
    }

    private static int foreground(NativeImage image) {
        int count = 0;
        for (int y = 50; y < 340; y++) for (int x = 20; x < WIDTH - 20; x++)
            if (delta(image.getPixelRGBA(x, y), 0xFF201810) > 30) count++;
        return count;
    }

    private static int delta(int a, int b) {
        return Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))
                + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255));
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static void finish(Minecraft mc, JsonObject report) {
        report.addProperty("status", report.getAsJsonArray("failures").isEmpty() ? "passed" : "failed");
        report.addProperty("safe_client_shutdown_requested", true);
        write(report);
        mc.stop();
    }

    private static Path output() {
        return Path.of(System.getProperty("modern_ite.resurrectionAuditOutput", "build/verification/resurrection/audit.json")).toAbsolutePath().normalize();
    }

    private static void write(JsonObject report) {
        try {
            Path output = output();
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            LogUtils.getLogger().info("MODERN_ITE_RESURRECTION_AUDIT {} report={}", report.get("status"), output);
        } catch (Exception failure) {
            LogUtils.getLogger().error("Cannot write resurrection audit", failure);
        }
    }
}
