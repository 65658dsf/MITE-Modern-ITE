package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.client.gear.GearStationScreen;
import io.github.ningmeng123.modernite.gear.ForgeRecipes;
import io.github.ningmeng123.modernite.gear.GearStationMenu;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/** Native forge screen pixels in the disposable client audit world. @author NingMeng123 */
final class ForgingClientAudit {
    private static final int WIDTH = 400, HEIGHT = 320, PANEL_WIDTH = 176, PANEL_HEIGHT = 208;
    private static final int LEFT = (WIDTH - PANEL_WIDTH) / 2, TOP = (HEIGHT - PANEL_HEIGHT) / 2;
    private ForgingClientAudit() {}

    static JsonObject capture(Minecraft mc) throws Exception {
        check(mc.level != null && mc.player != null && mc.getSingleplayerServer() != null,
            "Forge screen checks require the disposable singleplayer audit world");
        Path directory = Path.of(System.getProperty("modern_ite.mobRenderAuditOutput", "build/verification/forging-client-audit.json"))
            .toAbsolutePath().normalize().getParent().resolve("forging-client-audit");
        Files.createDirectories(directory);
        JsonObject result = new JsonObject();
        result.addProperty("author", "NingMeng123");
        result.addProperty("scope", "Native GearStationScreen GPU draws; raised, impact and recovery phases; static idle/completed/failed and gem controls; unchanged slots and labels");
        result.addProperty("render_width", WIDTH);
        result.addProperty("render_height", HEIGHT);
        JsonArray samples = new JsonArray();
        result.add("samples", samples);

        try (NativeImage idle = screen(mc, false, 0, 0);
             NativeImage raised = screen(mc, false, 1, 2);
             NativeImage impact = screen(mc, false, 1, 8);
             NativeImage recovery = screen(mc, false, 1, 16);
             NativeImage completed = screen(mc, false, 2, 0);
             NativeImage failed = screen(mc, false, 3, 0);
             NativeImage gemIdle = screen(mc, true, 0, 0);
             NativeImage gemRunning = screen(mc, true, 1, 8)) {
            sample(directory, samples, "idle", idle);
            sample(directory, samples, "raised", raised);
            sample(directory, samples, "impact", impact);
            sample(directory, samples, "recovery", recovery);
            sample(directory, samples, "completed", completed);
            sample(directory, samples, "failed", failed);
            sample(directory, samples, "gem", gemIdle);

            int raisedPixels = changes(idle, raised, 110, 14, 171, 50);
            int impactPixels = changes(raised, impact, 110, 14, 171, 50);
            int recoveryPixels = changes(impact, recovery, 110, 14, 171, 50);
            result.addProperty("idle_to_raised_animation_pixels", raisedPixels);
            result.addProperty("raised_to_impact_animation_pixels", impactPixels);
            result.addProperty("impact_to_recovery_animation_pixels", recoveryPixels);
            check(raisedPixels > 20, "A running forge must draw visible hammer animation pixels");
            check(impactPixels > 15, "The native hammer impact must visibly differ from its raised phase");
            check(recoveryPixels > 15, "The native hammer must recover visibly after its impact");
            check(changes(idle, completed, 110, 14, 171, 50) == 0,
                "A completed forge must stop drawing the running animation");
            check(changes(idle, failed, 110, 14, 171, 50) == 0,
                "A failed forge must stop drawing the running animation");
            check(changes(gemIdle, gemRunning, -LEFT, -TOP, WIDTH - LEFT, HEIGHT - TOP) == 0,
                "The separate gem-setting screen must not animate forging progress");
            int unrelated = unrelatedChanges(raised, impact) + unrelatedChanges(impact, recovery);
            result.addProperty("changed_slot_and_label_pixels", unrelated);
            check(unrelated == 0, "Hammer phases must preserve every slot, label and button pixel outside the animation and progress bar");
            result.addProperty("completed_and_failed_animation_stopped", true);
            result.addProperty("gem_screen_unchanged", true);
            result.addProperty("all_slots_and_labels_preserved", true);

            try (NativeImage sheet = new NativeImage(PANEL_WIDTH * 4, PANEL_HEIGHT, false)) {
                NativeImage[] images = {idle, raised, impact, recovery};
                for (int column = 0; column < images.length; column++)
                    for (int y = 0; y < PANEL_HEIGHT; y++) for (int x = 0; x < PANEL_WIDTH; x++)
                        sheet.setPixelRGBA(column * PANEL_WIDTH + x, y, images[column].getPixelRGBA(LEFT + x, TOP + y));
                Path preview = directory.resolve("forge-animation-phases.png");
                sheet.writeToFile(preview);
                result.addProperty("preview", preview.toString());
            }
        }
        result.addProperty("native_gpu_screen_count", 8);
        return result;
    }

    private static NativeImage screen(Minecraft mc, boolean gem, int status, int progress) {
        SimpleContainer station = new SimpleContainer(9);
        SimpleContainerData data = new SimpleContainerData(5);
        data.set(0, 0);
        data.set(1, progress);
        data.set(2, status == 1 ? 120 : 0);
        data.set(3, status);
        data.set(4, 35);
        if (gem) {
            station.setItem(0, new ItemStack(Items.IRON_SWORD));
        } else {
            int slot = 0;
            for (var cost : ForgeRecipes.costs(0)) {
                var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(cost.id()));
                station.setItem(slot++, new ItemStack(item, cost.count()));
            }
            station.setItem(5, new ItemStack(Items.IRON_SWORD));
            station.setItem(6, new ItemStack(Items.IRON_AXE));
            var hammer = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:vibranium_war_hammer"));
            check(hammer != Items.AIR, "The native forge preview requires an actual registered war hammer");
            station.setItem(7, new ItemStack(hammer));
        }
        GearStationMenu menu = new GearStationMenu(194, mc.player.getInventory(), station, data, gem);
        GearStationScreen screen = new GearStationScreen(menu, mc.player.getInventory(),
            Component.translatable(gem ? "container.modern_ite.gem_setting" : "container.modern_ite.forging"));
        screen.init(mc, WIDTH, HEIGHT);
        screen.tick();
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var window = mc.getWindow();
        int framebufferWidth = window.getWidth(), framebufferHeight = window.getHeight();
        double guiScale = window.getGuiScale();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            // GuiGraphics resolves its real scissor rectangle through Window. Match only
            // those framebuffer coordinates to this offscreen target, then restore them.
            window.setWidth(WIDTH);
            window.setHeight(HEIGHT);
            window.setGuiScale(1);
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, WIDTH, HEIGHT, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            Lighting.setupFor3DItems();
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            screen.render(graphics, -1, -1, 0);
            graphics.flush();
            mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
            window.setWidth(framebufferWidth);
            window.setHeight(framebufferHeight);
            window.setGuiScale(guiScale);
            mc.getMainRenderTarget().bindWrite(true);
            target.destroyBuffers();
        }
    }

    private static void sample(Path directory, JsonArray samples, String phase, NativeImage image) throws Exception {
        Path path = directory.resolve("forge-" + phase + ".png");
        image.writeToFile(path);
        JsonObject sample = new JsonObject();
        sample.addProperty("phase", phase);
        sample.addProperty("screenshot", path.toString());
        samples.add(sample);
    }

    private static int changes(NativeImage a, NativeImage b, int x1, int y1, int x2, int y2) {
        int changed = 0;
        for (int y = TOP + y1; y < TOP + y2; y++) for (int x = LEFT + x1; x < LEFT + x2; x++)
            if (delta(a.getPixelRGBA(x, y), b.getPixelRGBA(x, y)) > 12) changed++;
        return changed;
    }

    private static int unrelatedChanges(NativeImage a, NativeImage b) {
        int changed = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int relativeX = x - LEFT, relativeY = y - TOP;
            if (relativeX >= 110 && relativeX < 171 && relativeY >= 14 && relativeY < 50) continue;
            if (relativeX >= 110 && relativeX < 135 && relativeY >= 56 && relativeY < 64) continue;
            if (delta(a.getPixelRGBA(x, y), b.getPixelRGBA(x, y)) > 12) changed++;
        }
        return changed;
    }

    private static int delta(int a, int b) {
        return Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))
            + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
