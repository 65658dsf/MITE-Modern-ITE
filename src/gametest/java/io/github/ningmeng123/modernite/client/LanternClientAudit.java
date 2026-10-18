package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import io.github.ningmeng123.modernite.content.ExtremeLifeEvents;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;

/** Real baked models, GPU item transforms and lightmap switching in the owned audit world. @author NingMeng123 */
final class LanternClientAudit {
    private static final int WIDTH = 192, HEIGHT = 224;
    private static final ItemDisplayContext[] CONTEXTS = {ItemDisplayContext.GUI,
        ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND};
    private static final String[] LABELS = {"GUI", "First / main hand", "First / off hand", "Third / main hand", "Third / off hand"};
    private LanternClientAudit() {}

    static JsonObject capture(Minecraft mc) throws Exception {
        check(mc.level != null && mc.player != null && mc.getSingleplayerServer() != null,
            "Lantern checks require the disposable singleplayer audit world");
        List<DynamicCoreItem> items = BuiltInRegistries.ITEM.stream()
            .filter(item -> item instanceof DynamicCoreItem)
            .map(item -> (DynamicCoreItem) item)
            .sorted(Comparator.comparingInt(item -> item.material.ordinal())).toList();
        check(items.size() == 8, "Expected eight lantern material registrations");
        Path folder = Path.of(System.getProperty("modern_ite.mobRenderAuditOutput", "build/verification/mob-render-audit.json"))
            .toAbsolutePath().normalize().getParent().resolve("mob-render-audit");
        Files.createDirectories(folder);
        JsonObject result = new JsonObject();
        result.addProperty("author", "NingMeng123");
        result.addProperty("scope", "Native baked geometry, five actual item-display GPU contexts, and local player lightmap pixels");
        JsonArray models = new JsonArray();
        result.add("materials", models);
        try (NativeImage sheet = new NativeImage(WIDTH * CONTEXTS.length, HEIGHT * items.size(), false)) {
            int previousDurability = 0;
            for (int row = 0; row < items.size(); row++) {
                DynamicCoreItem item = items.get(row);
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                check(id.getNamespace().equals(ModernIte.MOD_ID), "Unexpected lantern namespace: " + id);
                ItemStack stack = new ItemStack(item);
                BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
                JsonObject facts = geometry(model, mc);
                models.add(facts);
                facts.addProperty("item", id.toString());
                facts.addProperty("material", item.material.name());
                facts.addProperty("durability", stack.getMaxDamage());
                check(stack.getMaxDamage() > previousDurability, "Lantern durability must increase with material: " + id);
                previousDurability = stack.getMaxDamage();
                JsonArray displays = new JsonArray();
                facts.add("gpu_item_displays", displays);
                for (int column = 0; column < CONTEXTS.length; column++) {
                    ItemDisplayContext context = CONTEXTS[column];
                    try (NativeImage image = render(mc, stack, model, context)) {
                        int foreground = foreground(image);
                        check(foreground > 250, "Native GPU item display is empty: " + id + "/" + context);
                        JsonObject display = new JsonObject();
                        display.addProperty("context", context.name());
                        display.addProperty("foreground_pixels", foreground);
                        displays.add(display);
                        // Copy exactly the rendered GPU pixels; each material keeps the five native display orientations.
                        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
                            sheet.setPixelRGBA(column * WIDTH + x, row * HEIGHT + y, image.getPixelRGBA(x, y));
                    }
                }
            }
            Path path = folder.resolve("lantern-eight-materials.png");
            sheet.writeToFile(path);
            result.addProperty("preview", path.toString());
        }
        result.addProperty("native_gpu_item_display_count", items.size() * CONTEXTS.length);
        result.add("lightmap", lightmap(mc, items));
        return result;
    }

    private static JsonObject geometry(BakedModel model, Minecraft mc) {
        check(model != null && model != mc.getModelManager().getMissingModel(), "Lantern must have a real baked model");
        check(model.isGui3d(), "Lantern must use a 3D inventory model");
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, RandomSource.create(0)));
        for (Direction face : Direction.values()) quads.addAll(model.getQuads(null, face, RandomSource.create(0)));
        check(quads.size() >= 12, "Lantern must contain actual solid model geometry");
        float[] minimum = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] maximum = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (BakedQuad quad : quads) {
            check(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),
                "Lantern texture must be present in the stitched atlas");
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                float coordinate = Float.intBitsToFloat(vertices[vertex * stride + axis]);
                minimum[axis] = Math.min(minimum[axis], coordinate);
                maximum[axis] = Math.max(maximum[axis], coordinate);
            }
        }
        JsonObject facts = new JsonObject();
        facts.addProperty("baked_quad_count", quads.size());
        JsonArray dimensions = new JsonArray();
        for (int axis = 0; axis < 3; axis++) {
            float extent = maximum[axis] - minimum[axis];
            check(extent >= 0.25F, "Lantern must have volume on every axis");
            dimensions.add(extent);
        }
        facts.add("geometry_dimensions", dimensions);
        facts.addProperty("gui_3d", true);
        return facts;
    }

    private static NativeImage render(Minecraft mc, ItemStack stack, BakedModel model, ItemDisplayContext context) {
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
            var pose = graphics.pose();
            boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            var transform = model.getTransforms().getTransform(context);
            float largestScale = Math.max(Math.abs(transform.scale.x), Math.max(Math.abs(transform.scale.y), Math.abs(transform.scale.z)));
            check(largestScale > 0, "Native item transform must have a visible scale: " + context);
            // Normalize only thumbnail framing; native display rotations, mirroring and scale proportions still render normally.
            float size = 128F / largestScale;
            pose.pushPose();
            pose.translate(WIDTH / 2F, 115, 100);
            pose.scale(size, -size, size);
            pose.translate((left ? 1 : -1) * transform.translation.x, -transform.translation.y, -transform.translation.z);
            mc.getItemRenderer().renderStatic(mc.player, stack, context, left, pose, graphics.bufferSource(), mc.level,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            graphics.flush();
            pose.popPose();
            int column = java.util.Arrays.asList(CONTEXTS).indexOf(context);
            graphics.drawCenteredString(mc.font, LABELS[column], WIDTH / 2, 8, 0xFFDCE6EE);
            graphics.drawCenteredString(mc.font, stack.getHoverName(), WIDTH / 2, 195, 0xFFFFFFFF);
            graphics.drawCenteredString(mc.font, "Durability " + stack.getMaxDamage(), WIDTH / 2, 208, 0xFFB8C6D4);
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

    private static int foreground(NativeImage image) {
        int count = 0;
        for (int y = 25; y < 185; y++) for (int x = 0; x < WIDTH; x++) {
            int pixel = image.getPixelRGBA(x, y);
            if (Math.abs((pixel & 255) - 16) + Math.abs((pixel >>> 8 & 255) - 24) + Math.abs((pixel >>> 16 & 255) - 32) > 30) count++;
        }
        return count;
    }

    private static JsonObject lightmap(Minecraft mc, List<DynamicCoreItem> items) throws Exception {
        JsonObject result = new JsonObject();
        var inventory = mc.player.getInventory();
        List<ItemStack> savedInventory = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) savedInventory.add(inventory.getItem(slot).copy());
        MobEffectInstance oldNightVision = mc.player.getEffect(MobEffects.NIGHT_VISION);
        MobEffectInstance savedNightVision = oldNightVision == null ? null : new MobEffectInstance(oldNightVision);
        long savedTime = mc.level.getDayTime();
        double savedGamma = mc.options.gamma().get();
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        check(info != null, "The native client player must have its player-info entry");
        Field gameMode = PlayerInfo.class.getDeclaredField("gameMode");
        gameMode.setAccessible(true);
        GameType savedGameMode = info.getGameMode();
        try {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) inventory.setItem(slot, ItemStack.EMPTY);
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            mc.level.setDayTime(18000);
            mc.options.gamma().set(0D);
            gameMode.set(info, GameType.SURVIVAL);
            int dark = pixel(mc, true);
            result.addProperty("baseline_dark_pixel", rgb(dark));
            ItemStack first = new ItemStack(items.getFirst());
            mc.player.setItemSlot(EquipmentSlot.MAINHAND, first);
            check(pixel(mc, true) == dark, "A lantern held only in the main hand must not illuminate the lightmap");
            mc.player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            inventory.setItem(9, first);
            check(pixel(mc, true) == dark, "A lantern stored in the inventory must not illuminate the lightmap");
            inventory.setItem(9, ItemStack.EMPTY);
            result.addProperty("mainhand_and_inventory_inactive", true);
            int lit = 0;
            JsonArray materials = new JsonArray();
            for (DynamicCoreItem item : items) {
                ItemStack stack = new ItemStack(item);
                mc.player.setItemSlot(EquipmentSlot.OFFHAND, stack);
                int active = pixel(mc, false);
                check(rgb(active) > rgb(dark) + 300, "The actual lightmap must brighten when the lantern is equipped: " + item.material);
                if (lit == 0) lit = active;
                check(active == lit, "Every lantern material must provide the same full lightmap brightness");
                check(!mc.player.hasEffect(MobEffects.NIGHT_VISION), "A lantern must not add a night-vision potion effect");
                JsonObject material = new JsonObject();
                material.addProperty("material", item.material.name());
                material.addProperty("active_pixel", rgb(active));
                materials.add(material);
                mc.player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                check(pixel(mc, false) == dark, "Removing the lantern must restore the next frame without waiting for a tick");
                stack.setDamageValue(stack.getMaxDamage());
                mc.player.setItemSlot(EquipmentSlot.OFFHAND, stack);
                check(ExtremeLifeEvents.coreLevel(mc.player) == 0 && pixel(mc, true) == dark,
                    "An exhausted lantern must not illuminate the lightmap: " + item.material);
                mc.player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
            result.add("all_material_pixels", materials);
            result.addProperty("instant_equipment_switch_without_lightmap_tick", true);
            result.addProperty("no_injected_night_vision_effect", true);
            result.addProperty("all_exhausted_materials_inactive", true);
            mc.player.setItemSlot(EquipmentSlot.OFFHAND, first);
            gameMode.set(info, GameType.SPECTATOR);
            check(mc.player.isSpectator() && pixel(mc, true) == dark, "A spectator's offhand lantern must not illuminate the lightmap");
            gameMode.set(info, GameType.CREATIVE);
            check(mc.player.isCreative() && pixel(mc, false) == lit, "A creative player's fueled offhand lantern must illuminate the lightmap");
            result.addProperty("spectator_inactive_creative_active", true);
            mc.player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            gameMode.set(info, GameType.SURVIVAL);
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 7, 0, false, false));
            MobEffectInstance potion = mc.player.getEffect(MobEffects.NIGHT_VISION);
            check(potion != null, "The native short night-vision potion must be applied");
            float nativeScale = GameRenderer.getNightVisionScale(mc.player, 1);
            int potionPixel = pixel(mc, true);
            mc.player.setItemSlot(EquipmentSlot.OFFHAND, first);
            check(pixel(mc, false) == lit, "Lantern brightness must stay full during a fading night-vision potion");
            check(mc.player.getEffect(MobEffects.NIGHT_VISION) == potion && potion.getDuration() == 7
                && GameRenderer.getNightVisionScale(mc.player, 1) == nativeScale, "Lantern use must preserve the actual potion and native night-vision scale");
            mc.player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            check(pixel(mc, false) == potionPixel, "Removing the lantern must restore the real fading night-vision potion immediately");
            result.addProperty("fading_potion_unchanged", true);
            result.addProperty("native_fading_potion_scale", nativeScale);
            result.addProperty("native_fading_potion_pixel", rgb(potionPixel));
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false));
            check(pixel(mc, true) == lit, "Lantern lightmap brightness must equal vanilla full night vision");
            result.addProperty("same_brightness_as_vanilla_full_night_vision", true);
            return result;
        } finally {
            gameMode.set(info, savedGameMode);
            for (int slot = 0; slot < savedInventory.size(); slot++) inventory.setItem(slot, savedInventory.get(slot));
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            if (savedNightVision != null) mc.player.addEffect(savedNightVision);
            mc.level.setDayTime(savedTime);
            mc.options.gamma().set(savedGamma);
            mc.gameRenderer.lightTexture().tick();
            mc.gameRenderer.lightTexture().updateLightTexture(1);
        }
    }

    private static int pixel(Minecraft mc, boolean tick) throws Exception {
        LightTexture texture = mc.gameRenderer.lightTexture();
        if (tick) texture.tick();
        texture.updateLightTexture(1);
        Field field = LightTexture.class.getDeclaredField("lightPixels");
        field.setAccessible(true);
        return ((NativeImage) field.get(texture)).getPixelRGBA(0, 0);
    }

    private static int rgb(int pixel) { return (pixel & 255) + (pixel >>> 8 & 255) + (pixel >>> 16 & 255); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
