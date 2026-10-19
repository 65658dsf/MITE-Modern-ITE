package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Field;
import java.nio.file.Path;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Actual hand-event and player-renderer GPU samples in the owned audit world. @author NingMeng123 */
final class LanternCarryClientAudit {
    private static final int WIDTH = 400, HEIGHT = 300;
    private LanternCarryClientAudit() {}

    static JsonObject capture(Minecraft mc, ItemStack lantern, Path folder) throws Exception {
        var player = mc.player;
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        HumanoidArm mainArm = player.getMainArm();
        int age = player.tickCount;
        float attack = player.attackAnim, oldAttack = player.oAttackAnim;
        float body = player.yBodyRot, oldBody = player.yBodyRotO;
        float head = player.yHeadRot, oldHead = player.yHeadRotO;
        float pitch = player.getXRot(), oldPitch = player.xRotO;
        SavedWalk walk = new SavedWalk(player.walkAnimation);
        JsonObject result = new JsonObject();
        result.addProperty("scope", "Posted native RenderHandEvent and full PlayerRenderer, with real skin and item buffers");
        JsonArray samples = new JsonArray();
        result.add("samples", samples);
        try (NativeImage sheet = new NativeImage(WIDTH * 4, HEIGHT * 4, false)) {
            player.attackAnim = player.oAttackAnim = 0;
            player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = 0;
            player.setXRot(0); player.xRotO = 0;
            player.tickCount = 120;
            int row = 0;
            for (HumanoidArm dominant : HumanoidArm.values()) {
                player.setMainArm(dominant);
                for (InteractionHand hand : InteractionHand.values()) {
                    HumanoidArm carrying = hand == InteractionHand.MAIN_HAND ? dominant : dominant.getOpposite();
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    walk.stage(player.walkAnimation, false);
                    try (NativeImage emptyBefore = third(mc, null)) {
                        var model = ((PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player)).getModel();
                        var nativeArm = carrying == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                        float emptyX = nativeArm.xRot, emptyY = nativeArm.yRot, emptyZ = nativeArm.zRot;
                        player.setItemInHand(hand, lantern);
                        JsonObject sample = new JsonObject();
                        samples.add(sample);
                        sample.addProperty("main_arm", dominant.name());
                        sample.addProperty("hand", hand.name());
                        sample.addProperty("carrying_arm", carrying.name());
                        String name = dominant.name().toLowerCase() + "-" + hand.name().toLowerCase();
                        try (NativeImage firstIdle = first(mc, hand, lantern, sample);
                                NativeImage thirdIdle = third(mc, sample)) {
                            save(firstIdle, folder, name + "-first-idle", sheet, row, 0);
                            save(thirdIdle, folder, name + "-third-idle", sheet, row, 2);
                            check(Math.abs(nativeArm.xRot - emptyX) + Math.abs(nativeArm.yRot - emptyY)
                                    + Math.abs(nativeArm.zRot - emptyZ) > .1F,
                                "The real player renderer must visibly bend the lantern carrying arm: " + name);
                            sample.addProperty("native_arm_x_rotation", nativeArm.xRot);
                            sample.addProperty("native_arm_y_rotation", nativeArm.yRot);
                            sample.addProperty("native_arm_z_rotation", nativeArm.zRot);
                            walk.stage(player.walkAnimation, true);
                            try (NativeImage firstMoving = first(mc, hand, lantern, sample);
                                    NativeImage thirdMoving = third(mc, sample)) {
                                save(firstMoving, folder, name + "-first-walking", sheet, row, 1);
                                save(thirdMoving, folder, name + "-third-walking", sheet, row, 3);
                                int firstDelta = difference(firstIdle, firstMoving), thirdDelta = difference(thirdIdle, thirdMoving);
                                check(firstDelta > 30 && thirdDelta > 30, "Walking must visibly move both native carry views: " + name);
                                sample.addProperty("first_walking_changed_pixels", firstDelta);
                                sample.addProperty("third_walking_changed_pixels", thirdDelta);
                            }
                        }
                        walk.stage(player.walkAnimation, false);
                        player.setItemInHand(hand, ItemStack.EMPTY);
                        try (NativeImage emptyAfter = third(mc, null)) {
                            check(difference(emptyBefore, emptyAfter) == 0,
                                "Removing the lantern must restore the shared player model: " + name);
                        }
                    }
                    row++;
                }
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            JsonObject control = new JsonObject();
            try (NativeImage empty = first(mc, InteractionHand.MAIN_HAND, player.getMainHandItem(), control)) {
                check(foreground(empty) == 0, "The lantern event must not draw or cancel another item");
            }
            result.add("non_lantern_event_control", control);
            Path path = folder.resolve("lantern-carry-poses.png");
            sheet.writeToFile(path);
            result.addProperty("preview", path.toString());
            result.addProperty("columns", "first idle; first walking; third idle; third walking");
            result.addProperty("rows", "left main/main hand; left main/off hand; right main/main hand; right main/off hand");
            result.add("durability_hud", hud(mc, lantern, folder));
            result.add("durability_reequip", LanternWearClientAudit.capture(mc));
            result.addProperty("status", "passed");
            return result;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
            player.setMainArm(mainArm);
            player.tickCount = age;
            player.attackAnim = attack; player.oAttackAnim = oldAttack;
            player.yBodyRot = body; player.yBodyRotO = oldBody;
            player.yHeadRot = head; player.yHeadRotO = oldHead;
            player.setXRot(pitch); player.xRotO = oldPitch;
            walk.restore(player.walkAnimation);
        }
    }

    private static JsonObject hud(Minecraft mc, ItemStack lantern, Path folder) throws Exception {
        ItemStack saved = mc.player.getOffhandItem();
        boolean hidden = mc.options.hideGui;
        ItemStack sample = lantern.copy();
        JsonObject result = new JsonObject();
        JsonArray states = new JsonArray(); result.add("states", states);
        try (NativeImage sheet = new NativeImage(WIDTH, 80 * 3, false)) {
            mc.options.hideGui = false;
            mc.player.setItemInHand(InteractionHand.OFF_HAND, sample);
            int maximum = sample.getMaxDamage();
            long previous = Long.MIN_VALUE;
            int row = 0;
            for (int damage : new int[] {0, maximum / 2, maximum}) {
                sample.setDamageValue(damage);
                try (NativeImage image = hudFrame(mc)) {
                    int pixels = foreground(image);
                    check(pixels > 25, "The native HUD event must draw the remaining/maximum durability digits");
                    long fingerprint = 1;
                    for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
                        fingerprint = fingerprint * 31 + image.getPixelRGBA(x, y);
                    check(previous != fingerprint, "Changing lantern durability must change the actual HUD pixels");
                    previous = fingerprint;
                    JsonObject state = new JsonObject(); states.add(state);
                    state.addProperty("remaining", maximum - damage); state.addProperty("maximum", maximum);
                    state.addProperty("foreground_pixels", pixels); state.addProperty("pixel_fingerprint", fingerprint);
                    for (int y = 0; y < 80; y++) for (int x = 0; x < WIDTH; x++)
                        sheet.setPixelRGBA(x, row * 80 + y, image.getPixelRGBA(x, HEIGHT - 80 + y));
                }
                row++;
            }
            mc.options.hideGui = true;
            try (NativeImage image = hudFrame(mc)) {
                check(foreground(image) == 0, "F1 must hide the lantern durability HUD even when the layer event fires");
            }
            mc.options.hideGui = false;
            mc.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
            try (NativeImage image = hudFrame(mc)) {
                check(foreground(image) == 0, "A non-lantern offhand must not show the lantern HUD");
            }
            Path path = folder.resolve("lantern-durability-hud.png"); sheet.writeToFile(path);
            result.addProperty("preview", path.toString());
            result.addProperty("preview_rows", "full; half; exhausted");
            result.addProperty("f1_hidden_and_non_lantern_empty", true);
            return result;
        } finally {
            mc.options.hideGui = hidden;
            mc.player.setItemInHand(InteractionHand.OFF_HAND, saved);
        }
    }

    private static NativeImage hudFrame(Minecraft mc) {
        return frame(mc, false, ignored -> {
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            // Preserve the native HUD's GUI coordinates while centering its bottom edge in this target.
            graphics.pose().translate(WIDTH / 2F - graphics.guiWidth() / 2F, HEIGHT - graphics.guiHeight(), 0);
            NeoForge.EVENT_BUS.post(new RenderGuiLayerEvent.Post(graphics, DeltaTracker.ONE,
                VanillaGuiLayers.HOTBAR, (gui, delta) -> {}));
            graphics.flush();
        });
    }

    private static NativeImage first(Minecraft mc, InteractionHand hand, ItemStack stack, JsonObject facts) {
        boolean lantern = stack.getItem() instanceof io.github.ningmeng123.modernite.content.DynamicCoreItem;
        return frame(mc, true, pose -> {
            boolean[] requested = new boolean[2];
            String skin = mc.player.getSkin().texture().toString();
            MultiBufferSource source = type -> {
                String description = type.toString();
                if (description.contains(skin)) requested[0] = true;
                if (description.contains("textures/atlas/blocks.png")) requested[1] = true;
                return mc.renderBuffers().bufferSource().getBuffer(type);
            };
            RenderHandEvent event = new RenderHandEvent(hand, pose, source, LightTexture.FULL_BRIGHT, 1, 0, 0, 0, stack);
            NeoForge.EVENT_BUS.post(event);
            check(event.isCanceled() == lantern, "Only lantern hand events must be replaced");
            if (lantern) check(requested[0] && requested[1], "The event must draw both the player's skin arm and the lantern atlas");
            facts.addProperty("native_hand_event_canceled", event.isCanceled());
            facts.addProperty("skin_buffer_requested", requested[0]);
            facts.addProperty("lantern_atlas_buffer_requested", requested[1]);
        });
    }

    private static NativeImage third(Minecraft mc, JsonObject facts) {
        return frame(mc, false, pose -> {
            pose.translate(WIDTH / 2F, HEIGHT - 22F, 50);
            pose.scale(120, 120, -120);
            pose.mulPose(new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI + .35F));
            Lighting.setupForEntityInInventory();
            PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
            renderer.render(mc.player, 0, 1, pose, mc.renderBuffers().bufferSource(), LightTexture.FULL_BRIGHT);
            if (facts != null) facts.addProperty("native_player_renderer", renderer.getClass().getName());
        });
    }

    private static NativeImage frame(Minecraft mc, boolean first, Draw draw) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        try {
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(first
                ? new Matrix4f().setPerspective((float) Math.toRadians(70), WIDTH / (float) HEIGHT, .05F, 100)
                : new Matrix4f().setOrtho(0, WIDTH, HEIGHT, 0, -1000, 1000),
                first ? VertexSorting.DISTANCE_TO_ORIGIN : VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            Lighting.setupFor3DItems();
            draw.render(new PoseStack());
            mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch();
            view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            Lighting.setupFor3DItems();
            mc.getMainRenderTarget().bindWrite(true);
            target.destroyBuffers();
        }
    }

    private static void save(NativeImage image, Path folder, String name, NativeImage sheet, int row, int column) throws Exception {
        check(foreground(image) > 500, "The actual carry GPU sample must be visible: " + name);
        image.writeToFile(folder.resolve("lantern-" + name + ".png"));
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
            sheet.setPixelRGBA(column * WIDTH + x, row * HEIGHT + y, image.getPixelRGBA(x, y));
    }

    private static int foreground(NativeImage image) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int p = image.getPixelRGBA(x, y);
            if (Math.abs((p & 255) - 16) + Math.abs((p >>> 8 & 255) - 24) + Math.abs((p >>> 16 & 255) - 32) > 18) count++;
        }
        return count;
    }

    private static int difference(NativeImage first, NativeImage second) {
        int changed = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int a = first.getPixelRGBA(x, y), b = second.getPixelRGBA(x, y);
            if (Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))
                    + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255)) > 12) changed++;
        }
        return changed;
    }

    private static float read(WalkAnimationState walk, String name) throws Exception {
        Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); return field.getFloat(walk);
    }
    private static void write(WalkAnimationState walk, String name, float value) throws Exception {
        Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); field.setFloat(walk, value);
    }
    private record SavedWalk(float speedOld, float speed, float position) {
        SavedWalk(WalkAnimationState walk) throws Exception { this(read(walk, "speedOld"), read(walk, "speed"), read(walk, "position")); }
        void stage(WalkAnimationState walk, boolean moving) throws Exception {
            write(walk, "speedOld", moving ? .8F : 0); write(walk, "speed", moving ? .8F : 0); write(walk, "position", moving ? 2.4F : 0);
        }
        void restore(WalkAnimationState walk) throws Exception { write(walk, "speedOld", speedOld); write(walk, "speed", speed); write(walk, "position", position); }
    }
    @FunctionalInterface private interface Draw { void render(PoseStack pose); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
