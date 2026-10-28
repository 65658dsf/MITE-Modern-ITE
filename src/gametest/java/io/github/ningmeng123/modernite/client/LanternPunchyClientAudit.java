package io.github.ningmeng123.modernite.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/** Full first-person pipeline regression with optional Punchy ownership checks. @author NingMeng123 */
final class LanternPunchyClientAudit {
    private static final int WIDTH = 800, HEIGHT = 600;
    private static final String[] RENDER_FIELDS = {
        "mainHandItem", "offHandItem", "mainHandHeight", "oMainHandHeight", "offHandHeight", "oOffHandHeight"
    };
    private LanternPunchyClientAudit() {}

    static JsonObject capture(Minecraft mc, Path folder) throws Exception {
        JsonObject result = new JsonObject();
        Class<?> config;
        try {
            config = Class.forName("punchy.config.PunchyConfig");
        } catch (ClassNotFoundException absent) {
            result.addProperty("status", "skipped_punchy_not_installed");
            return result;
        }
        Method itemBlacklisted = config.getMethod("isItemBlacklisted", ItemStack.class);
        Method handBlacklisted = config.getMethod("isHandBlacklisted", Player.class, InteractionHand.class);
        Method armBlacklisted = Class.forName("punchy.client.render.PunchyArmRenderer")
            .getDeclaredMethod("isBlacklistedForArm", LocalPlayer.class, HumanoidArm.class);
        armBlacklisted.setAccessible(true);
        Method snapshot = config.getMethod("snapshot");
        Gson gson = new Gson();
        String configurationBefore = gson.toJson(snapshot.invoke(null));
        check((boolean) config.getMethod("isModEnabled").invoke(null), "Punchy compatibility audit requires Punchy enabled");
        check(!(boolean) itemBlacklisted.invoke(null, new ItemStack(Items.STICK)),
            "The ordinary stick control must not be blacklisted by the audit configuration");
        check(!(boolean) itemBlacklisted.invoke(null, ItemStack.EMPTY), "Empty stacks must not be lantern blacklisted");
        check(!(boolean) itemBlacklisted.invoke(null, (Object) null), "Punchy's null-stack fallback must remain false");

        var player = mc.player;
        var renderer = mc.gameRenderer.itemInHandRenderer;
        ItemStack savedMain = player.getMainHandItem(), savedOff = player.getOffhandItem();
        HumanoidArm savedArm = player.getMainArm();
        CameraType savedCamera = mc.options.getCameraType();
        Object[] savedRenderer = new Object[RENDER_FIELDS.length];
        for (int i = 0; i < RENDER_FIELDS.length; i++) savedRenderer[i] = field(RENDER_FIELDS[i]).get(renderer);
        var items = BuiltInRegistries.ITEM.stream().filter(item -> item instanceof DynamicCoreItem)
            .map(item -> (DynamicCoreItem) item).sorted(Comparator.comparingInt(item -> item.material.ordinal())).toList();
        check(items.size() == 3, "Punchy regression must cover all three retained lantern materials");
        JsonArray ownership = new JsonArray(), frames = new JsonArray();
        result.add("ownership_samples", ownership);
        result.add("full_pipeline_frames", frames);
        try {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            for (var item : items) {
                ItemStack lantern = new ItemStack(item);
                check((boolean) itemBlacklisted.invoke(null, lantern), "Punchy must yield every retained lantern: " + item.material);
                for (HumanoidArm dominant : HumanoidArm.values()) {
                    player.setMainArm(dominant);
                    for (int mode = 0; mode < 3; mode++) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, mode == 1 ? new ItemStack(Items.STICK) : lantern.copy());
                        player.setItemInHand(InteractionHand.OFF_HAND, mode == 0 ? new ItemStack(Items.STICK) : lantern.copy());
                        for (InteractionHand hand : InteractionHand.values()) {
                            boolean expected = player.getItemInHand(hand).getItem() instanceof DynamicCoreItem;
                            HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? dominant : dominant.getOpposite();
                            check((boolean) handBlacklisted.invoke(null, player, hand) == expected,
                                "Only the carrying interaction hand must yield: " + item.material + "/" + dominant + "/" + hand + "/" + mode);
                            check((boolean) armBlacklisted.invoke(null, player, arm) == expected,
                                "Only the carrying physical arm must yield: " + item.material + "/" + dominant + "/" + arm + "/" + mode);
                            JsonObject sample = new JsonObject(); ownership.add(sample);
                            sample.addProperty("material", item.material.name()); sample.addProperty("dominant_arm", dominant.name());
                            sample.addProperty("hand", hand.name()); sample.addProperty("arm", arm.name());
                            sample.addProperty("dual_lanterns", mode == 2); sample.addProperty("punchy_yields", expected);
                        }
                    }
                }
            }
            // Exercise the complete native entry point: Punchy's HEAD pass runs before RenderHandEvent.
            // Posting the event alone cannot reveal the duplicate arm that motivated this regression.
            ItemStack lantern = new ItemStack(items.getLast());
            for (HumanoidArm dominant : HumanoidArm.values()) {
                player.setMainArm(dominant);
                for (boolean dual : new boolean[] {false, true}) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, dual ? lantern.copy() : ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, lantern.copy());
                    stageRenderer(mc);
                    JsonObject facts = new JsonObject(); frames.add(facts);
                    facts.addProperty("dominant_arm", dominant.name()); facts.addProperty("dual_lanterns", dual);
                    String name = "lantern-punchy-full-" + dominant.name().toLowerCase() + (dual ? "-dual" : "-offhand");
                    try (NativeImage image = frame(mc, facts)) {
                        int foreground = foreground(image);
                        check(foreground > 1000, "Complete first-person frame must contain visible carry geometry: " + name);
                        Path path = folder.resolve(name + ".png"); image.writeToFile(path);
                        facts.addProperty("preview", path.toString()); facts.addProperty("foreground_pixels", foreground);
                    }
                }
            }
            check(configurationBefore.equals(gson.toJson(snapshot.invoke(null))), "Audit must preserve Punchy's configuration");
            result.addProperty("configuration_preserved", true);
            result.addProperty("scope", "All retained materials, both dominant arms, main/off/dual carry ownership; complete renderHandsWithItems GPU frames");
            result.addProperty("status", "passed");
            return result;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, savedMain);
            player.setItemInHand(InteractionHand.OFF_HAND, savedOff);
            player.setMainArm(savedArm);
            mc.options.setCameraType(savedCamera);
            for (int i = 0; i < RENDER_FIELDS.length; i++) field(RENDER_FIELDS[i]).set(renderer, savedRenderer[i]);
        }
    }

    private static void stageRenderer(Minecraft mc) throws Exception {
        field("mainHandItem").set(mc.gameRenderer.itemInHandRenderer, mc.player.getMainHandItem());
        field("offHandItem").set(mc.gameRenderer.itemInHandRenderer, mc.player.getOffhandItem());
        for (int i = 2; i < RENDER_FIELDS.length; i++) field(RENDER_FIELDS[i]).setFloat(mc.gameRenderer.itemInHandRenderer, 1F);
    }

    private static NativeImage frame(Minecraft mc, JsonObject facts) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        try (ByteBufferBuilder memory = new ByteBufferBuilder(786432)) {
            ObservedBuffers buffers = new ObservedBuffers(memory, mc.player.getSkin().texture().toString());
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float) Math.toRadians(70), WIDTH / (float) HEIGHT, .05F, 100),
                VertexSorting.DISTANCE_TO_ORIGIN);
            view.identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1); Lighting.setupFor3DItems();
            mc.gameRenderer.itemInHandRenderer.renderHandsWithItems(1, new PoseStack(), buffers, mc.player, LightTexture.FULL_BRIGHT);
            buffers.endBatch();
            check(buffers.skinRequests > 0 && buffers.atlasRequests > 0,
                "Complete native frame must request both skin and lantern atlas buffers");
            boolean dual = mc.player.getMainHandItem().getItem() instanceof DynamicCoreItem
                && mc.player.getOffhandItem().getItem() instanceof DynamicCoreItem;
            check(dual ? buffers.punchySkinRequests == 0 : buffers.punchySkinRequests > 0,
                "Punchy must draw only the free hand and yield both hands when both carry lanterns");
            facts.addProperty("native_entry_point", "ItemInHandRenderer.renderHandsWithItems");
            facts.addProperty("skin_buffer_requests", buffers.skinRequests);
            facts.addProperty("punchy_skin_buffer_requests", buffers.punchySkinRequests);
            facts.addProperty("atlas_buffer_requests", buffers.atlasRequests);
            return Screenshot.takeScreenshot(target);
        } finally {
            view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); Lighting.setupFor3DItems();
            mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static final class ObservedBuffers extends MultiBufferSource.BufferSource {
        private final String skin;
        int skinRequests, atlasRequests, punchySkinRequests;
        ObservedBuffers(ByteBufferBuilder memory, String skin) {
            super(memory, new LinkedHashMap<>());
            this.skin = skin;
        }
        @Override public VertexConsumer getBuffer(RenderType type) {
            String description = type.toString();
            if (description.contains(skin)) {
                skinRequests++;
                if (StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals("punchy.client.render.PunchyArmRenderer")))) punchySkinRequests++;
            }
            if (description.contains("textures/atlas/blocks.png")) atlasRequests++;
            return super.getBuffer(type);
        }
    }

    private static int foreground(NativeImage image) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int pixel = image.getPixelRGBA(x, y);
            if (Math.abs((pixel & 255) - 16) + Math.abs((pixel >>> 8 & 255) - 24) + Math.abs((pixel >>> 16 & 255) - 32) > 18) count++;
        }
        return count;
    }
    private static Field field(String name) throws Exception {
        Field field = ItemInHandRenderer.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
