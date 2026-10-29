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
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

/** Enter the complete native first-person pipeline, including optional Punchy's HEAD injection. @author NingMeng123 */
final class GemBoxNativeHandAudit {
    private static final int WIDTH = 800, HEIGHT = 600;
    private static final String[] RENDER_FIELDS = {
            "mainHandItem", "offHandItem", "mainHandHeight", "oMainHandHeight", "offHandHeight", "oOffHandHeight"
    };
    private GemBoxNativeHandAudit() {}

    static JsonObject capture(Minecraft mc, ItemStack box, Path folder) throws Exception {
        Files.createDirectories(folder);
        var player = mc.player;
        var renderer = mc.gameRenderer.itemInHandRenderer;
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        HumanoidArm mainArm = player.getMainArm();
        CameraType camera = mc.options.getCameraType();
        Object[] renderState = new Object[RENDER_FIELDS.length];
        for (int index = 0; index < RENDER_FIELDS.length; index++) renderState[index] = field(RENDER_FIELDS[index]).get(renderer);
        float yaw = player.getYRot(), oldYaw = player.yRotO;
        float pitch = player.getXRot(), oldPitch = player.xRotO;
        float xBob = player.xBob, oldXBob = player.xBobO, yBob = player.yBob, oldYBob = player.yBobO;
        float attack = player.attackAnim, oldAttack = player.oAttackAnim;
        boolean swinging = player.swinging;
        JsonObject result = new JsonObject();
        result.addProperty("scope", "Complete ItemInHandRenderer.renderHandsWithItems GPU frames, including Punchy HEAD render ownership");
        JsonArray frames = new JsonArray(); result.add("frames", frames);
        Punchy punchy = Punchy.find();
        result.addProperty("punchy_installed", punchy != null);
        try {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            player.setYRot(0); player.yRotO = 0; player.setXRot(0); player.xRotO = 0;
            player.xBob = player.xBobO = player.yBob = player.yBobO = 0;
            player.attackAnim = player.oAttackAnim = 0; player.swinging = false;
            player.setMainArm(HumanoidArm.RIGHT);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            Punchy.Baseline baseline = punchy == null ? null : punchy.baseline(player);
            if (punchy != null) result.addProperty("punchy_enabled", baseline.enabled());
            int boxVertices = vertices(mc, box), gemVertices = vertices(mc, ExtremeGear.gem(GemType.FIREPROOF, 1));
            for (HumanoidArm dominant : HumanoidArm.values()) {
                player.setMainArm(dominant);
                for (InteractionHand carrying : InteractionHand.values()) {
                    for (int elapsed : new int[]{-1, 24, 52}) {
                        ItemStack held = box.copy();
                        held.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get()); held.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
                        if (elapsed >= 0) held.set(ExtremeGear.GEM_BOX_OPEN_STARTED.get(), mc.level.getGameTime() - elapsed);
                        if (elapsed >= 36) held.set(ExtremeGear.GEM_BOX_REVEALED_GEM.get(), GemType.FIREPROOF.ordinal() + 1);
                        player.setItemInHand(carrying, held);
                        player.setItemInHand(carrying == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                                new ItemStack(Items.STICK));
                        check(GemBoxArmPose.active(player), "The native full-pipeline fixture must enter the actual two-hand carry pose");
                        if (punchy != null) punchy.checkYield(player);
                        stageRenderer(mc);
                        JsonObject facts = new JsonObject(); frames.add(facts);
                        facts.addProperty("main_arm", dominant.name()); facts.addProperty("carrying_hand", carrying.name());
                        facts.addProperty("elapsed_ticks", elapsed);
                        int expected = boxVertices + (elapsed >= 36 ? gemVertices : 0);
                        String name = dominant.name().toLowerCase() + "-" + carrying.name().toLowerCase() + "-" + (elapsed < 0 ? "closed" : elapsed);
                        try (NativeImage image = frame(mc, facts, expected, true)) {
                            Path png = folder.resolve("gem-box-native-full-" + name + ".png"); image.writeToFile(png);
                            int visible = foreground(image);
                            check(visible > 1000, "The complete native first-person frame must show both arms and chest: " + name);
                            facts.addProperty("preview", png.toString()); facts.addProperty("foreground_pixels", visible);
                            if (elapsed < 0 || elapsed == 52) visibleHands(mc, image, folder, name, facts, expected, elapsed < 0);
                        }
                    }
                }
            }
            player.setMainArm(HumanoidArm.RIGHT);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            stageRenderer(mc);
            JsonObject control = new JsonObject(); result.add("ordinary_item_control", control);
            try (NativeImage image = frame(mc, control, -1, false)) {
                Path png = folder.resolve("gem-box-native-full-ordinary-stick.png"); image.writeToFile(png);
                control.addProperty("preview", png.toString());
                if (punchy != null) {
                    punchy.checkRestored(player, baseline);
                    if (baseline.enabled() && !baseline.mainHandBlacklisted())
                        check(control.get("punchy_skin_buffer_requests").getAsInt() > 0,
                                "Removing the chest restores Punchy's original ordinary-hand rendering");
                }
            }
            result.addProperty("punchy_configuration_preserved", true);
            result.addProperty("status", "passed_complete_native_first_person_pipeline");
            return result;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off);
            player.setMainArm(mainArm); mc.options.setCameraType(camera);
            player.setYRot(yaw); player.yRotO = oldYaw; player.setXRot(pitch); player.xRotO = oldPitch;
            player.xBob = xBob; player.xBobO = oldXBob; player.yBob = yBob; player.yBobO = oldYBob;
            player.attackAnim = attack; player.oAttackAnim = oldAttack; player.swinging = swinging;
            for (int index = 0; index < RENDER_FIELDS.length; index++) field(RENDER_FIELDS[index]).set(renderer, renderState[index]);
        }
    }

    private static int vertices(Minecraft mc, ItemStack stack) {
        var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        int quads = model.getQuads(null, null, RandomSource.create(196)).size();
        for (Direction face : Direction.values()) quads += model.getQuads(null, face, RandomSource.create(196)).size();
        return quads * 4;
    }

    static NativeImage movementFrame(Minecraft mc, JsonObject facts, int expectedVertices, boolean carried) throws Exception {
        stageRenderer(mc); return frame(mc, facts, expectedVertices, carried);
    }

    /** Include renderer wrappers which submit deferred native/Punchy geometry (notably AR). */
    static NativeImage movementCompleteFrame(Minecraft mc, JsonObject facts) throws Exception {
        facts.add("punchy_before_equipment_sync", runtimeEvidence(mc));
        syncMovementEquipment(mc);
        facts.add("punchy_after_equipment_sync", runtimeEvidence(mc));
        stageRenderer(mc); return frame(mc, facts, -1, false, HiddenHand.NONE, true);
    }

    static void syncMovementEquipment(Minecraft mc) throws Exception {
        Class<?> equip;
        try { equip = Class.forName("punchy.client.state.HandEquipStateMachine"); }
        catch (ClassNotFoundException absent) { return; }
        // Complete a deliberate instantaneous inventory fixture change through Punchy's own
        // public sync request and normal tick, rather than fabricating rendered/pending items.
        Field instance = equip.getDeclaredField("instance"); instance.setAccessible(true);
        Object machine = instance.get(null);
        check(machine != null, "Punchy's native equipment state machine must already exist in a ready client");
        equip.getMethod("requestRenderedSync").invoke(null);
        equip.getMethod("tick", Minecraft.class).invoke(machine, mc);
    }

    static void movementVisibleHands(Minecraft mc, NativeImage complete, Path folder, String name, JsonObject facts, int expectedVertices) throws Exception {
        visibleHands(mc, complete, folder, name, facts, expectedVertices, true);
    }

    private static void stageRenderer(Minecraft mc) throws Exception {
        field("mainHandItem").set(mc.gameRenderer.itemInHandRenderer, mc.player.getMainHandItem());
        field("offHandItem").set(mc.gameRenderer.itemInHandRenderer, mc.player.getOffhandItem());
        for (int index = 2; index < RENDER_FIELDS.length; index++) field(RENDER_FIELDS[index]).setFloat(mc.gameRenderer.itemInHandRenderer, 1F);
    }

    private static NativeImage frame(Minecraft mc, JsonObject facts, int expectedAtlasVertices, boolean carrying) {
        return frame(mc, facts, expectedAtlasVertices, carrying, HiddenHand.NONE);
    }

    private static NativeImage frame(Minecraft mc, JsonObject facts, int expectedAtlasVertices, boolean carrying, HiddenHand hidden) {
        return frame(mc, facts, expectedAtlasVertices, carrying, hidden, false);
    }

    private static NativeImage frame(Minecraft mc, JsonObject facts, int expectedAtlasVertices, boolean carrying, HiddenHand hidden, boolean complete) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        try (ByteBufferBuilder memory = new ByteBufferBuilder(786432)) {
            ObservedBuffers buffers = new ObservedBuffers(memory, mc.player.getSkin().texture().toString(), hidden);
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float)Math.toRadians(70), WIDTH / (float)HEIGHT, .05F, 100),
                    VertexSorting.DISTANCE_TO_ORIGIN);
            view.identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1); Lighting.setupFor3DItems();
            facts.add("runtime_before", runtimeEvidence(mc));
            JsonArray handEvents = new JsonArray(); facts.add("native_hand_events", handEvents);
            Consumer<RenderHandEvent> listener = event -> {
                JsonObject observed = new JsonObject(); observed.addProperty("hand", event.getHand().name());
                observed.addProperty("item", event.getItemStack().toString()); observed.addProperty("cancelled", event.isCanceled());
                observed.addProperty("audit_buffer", event.getMultiBufferSource() == buffers); handEvents.add(observed);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderHandEvent.class, listener);
            try {
                if (complete) {
                    try { GemBoxOuterHandAudit.render(mc, buffers); }
                    catch (Exception failure) { throw new IllegalStateException("Complete native hand render entry point", failure); }
                }
                else mc.gameRenderer.itemInHandRenderer.renderHandsWithItems(1, new PoseStack(), buffers, mc.player, LightTexture.FULL_BRIGHT);
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            buffers.endBatch();
            facts.addProperty("native_entry_point", complete ? "GameRenderer.renderItemInHand -> ItemInHandRenderer.renderHandsWithItems (including native BEFORE/AFTER submission)" : "ItemInHandRenderer.renderHandsWithItems");
            facts.add("runtime_after", runtimeEvidence(mc));
            facts.addProperty("skin_buffer_requests", buffers.skinRequests);
            facts.addProperty("punchy_skin_buffer_requests", buffers.punchySkinRequests);
            facts.addProperty("atlas_buffer_requests", buffers.atlasRequests);
            facts.addProperty("atlas_vertices", buffers.atlasVertices);
            facts.addProperty("native_right_skin_hand", buffers.rightHand);
            facts.addProperty("native_left_skin_hand", buffers.leftHand);
            facts.add("right_submitted_geometry", buffers.rightBounds.json());
            facts.add("left_submitted_geometry", buffers.leftBounds.json());
            facts.addProperty("deliberately_hidden_hand", hidden.name());
            if (carrying) {
                check(buffers.rightHand && buffers.leftHand, "The full native pipeline must render both original skin arms");
                check(buffers.punchySkinRequests == 0, "Punchy's HEAD pass must yield both occupied carry arms");
                check(buffers.atlasVertices == expectedAtlasVertices,
                        "The full pipeline must draw one chest and at most its actual selected gem, without a duplicate stick/chest: "
                                + buffers.atlasVertices + " vs " + expectedAtlasVertices);
            }
            NativeImage image = Screenshot.takeScreenshot(target);
            facts.addProperty("foreground_pixels", foreground(image));
            return image;
        } finally {
            view.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(projection, sorting);
            Lighting.setupFor3DItems(); mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    static JsonObject runtimeEvidence(Minecraft mc) {
        JsonObject facts = new JsonObject();
        facts.addProperty("camera", mc.options.getCameraType().name());
        facts.addProperty("box_active", GemBoxArmPose.active(mc.player));
        facts.addProperty("box_hidden_for_movement", GemBoxArmPose.hiddenForMovement(mc.player));
        try {
            Class<?> config = Class.forName("punchy.config.PunchyConfig");
            facts.add("punchy_configuration", new Gson().toJsonTree(config.getMethod("snapshot").invoke(null)));
            for (InteractionHand hand : InteractionHand.values()) facts.addProperty("punchy_" + hand.name().toLowerCase() + "_blacklisted",
                    (boolean)config.getMethod("isHandBlacklisted", Player.class, InteractionHand.class).invoke(null, mc.player, hand));
            Field hidden = Class.forName("punchy.client.render.PunchyArmRenderer").getDeclaredField("FIRSTPERSON_HIDE"); hidden.setAccessible(true);
            facts.addProperty("punchy_firstperson_hide", hidden.getBoolean(null));
            Class<?> equip = Class.forName("punchy.client.state.HandEquipStateMachine");
            facts.addProperty("punchy_visual_offhand", String.valueOf(equip.getMethod("getVisualOffhandStack", LocalPlayer.class).invoke(null, mc.player)));
            Method incoming = equip.getDeclaredMethod("shouldHideIncomingOffhandUntilHandInStarts", LocalPlayer.class); incoming.setAccessible(true);
            facts.addProperty("punchy_incoming_offhand_hidden", (boolean)incoming.invoke(null, mc.player));
        } catch (ClassNotFoundException absent) { facts.addProperty("punchy_installed", false); }
        catch (ReflectiveOperationException unavailable) { facts.addProperty("punchy_diagnostic_unavailable", unavailable.toString()); }
        return facts;
    }

    /** Negative controls keep the complete native scene, omitting only one real arm and its sleeve. */
    private static void visibleHands(Minecraft mc, NativeImage complete, Path folder, String name, JsonObject facts,
                                     int expectedVertices, boolean closed) throws Exception {
        JsonObject rightControl = new JsonObject(), leftControl = new JsonObject();
        try (NativeImage withoutRight = frame(mc, rightControl, expectedVertices, true, HiddenHand.RIGHT);
                NativeImage withoutLeft = frame(mc, leftControl, expectedVertices, true, HiddenHand.LEFT);
                NativeImage mask = new NativeImage(WIDTH, HEIGHT, false)) {
            PixelBounds right = new PixelBounds(), left = new PixelBounds();
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                boolean rightVisible = colourDistance(complete.getPixelRGBA(x, y), withoutRight.getPixelRGBA(x, y)) > 18;
                boolean leftVisible = colourDistance(complete.getPixelRGBA(x, y), withoutLeft.getPixelRGBA(x, y)) > 18;
                if (rightVisible) right.add(x, y);
                if (leftVisible) left.add(x, y);
                // NativeImage uses ABGR: green is the right hand; blue is the left; magenta is overlap.
                mask.setPixelRGBA(x, y, rightVisible && leftVisible ? 0xFFFF80FF : rightVisible ? 0xFF80DC00 : leftVisible ? 0xFFFFA050 : 0xFF000000);
            }
            Path maskPath = folder.resolve("gem-box-native-visible-hands-" + name + ".png"); mask.writeToFile(maskPath);
            JsonObject visibility = new JsonObject(); facts.add("visible_hands", visibility);
            visibility.addProperty("scope", "Actual final-frame RGB contribution: complete scene minus the same native pipeline with only that skin hand/sleeve omitted");
            visibility.add("right", right.json()); visibility.add("left", left.json());
            visibility.addProperty("difference_preview", maskPath.toString());
            visibility.addProperty("preview_colours", "right green; left blue; overlap magenta");
            visibility.add("right_omission_control", rightControl); visibility.add("left_omission_control", leftControl);
            int minimumVisible = Math.max(100, WIDTH * HEIGHT / 3000);
            check(right.count >= minimumVisible && left.count >= minimumVisible,
                    "Both real skin hands must remain visibly exposed in the final frame, rather than hidden by the chest or screen edge: "
                            + name + " right=" + right.count + " left=" + left.count + " minimum=" + minimumVisible);
            if (closed) check(left.centreX() + WIDTH * .025F < right.centreX(),
                    "The visible closed-chest hands must be distinguishable on its two sides: " + name);
        }
    }

    private enum HiddenHand { NONE, RIGHT, LEFT }

    private static final class PixelBounds {
        private int count, minX = WIDTH, minY = HEIGHT, maxX = -1, maxY = -1;
        private long sumX, sumY;
        private void add(int x, int y) {
            count++; minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); sumX += x; sumY += y;
        }
        private float centreX() { return count == 0 ? Float.NaN : sumX / (float)count; }
        private JsonObject json() {
            JsonObject result = new JsonObject(); result.addProperty("visible_pixels", count); result.addProperty("viewport_percent", count * 100F / (WIDTH * HEIGHT));
            JsonArray bounds = new JsonArray();
            if (count > 0) {
                bounds.add(minX); bounds.add(minY); bounds.add(maxX); bounds.add(maxY);
                result.addProperty("centre_x", centreX()); result.addProperty("centre_y", sumY / (float)count);
            }
            result.add("screen_bounds_left_top_right_bottom", bounds); return result;
        }
    }

    private static final class SubmittedBounds {
        private int vertices, inFront, inViewport;
        private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        private void add(float x, float y, float z) {
            vertices++;
            if (z >= -.05F) return;
            inFront++;
            double f = 1 / Math.tan(Math.toRadians(70) / 2);
            float ndcX = (float)(x * f / (WIDTH / (double)HEIGHT) / -z), ndcY = (float)(y * f / -z);
            float screenX = (ndcX + 1) * WIDTH / 2, screenY = (1 - ndcY) * HEIGHT / 2;
            minX = Math.min(minX, screenX); minY = Math.min(minY, screenY); maxX = Math.max(maxX, screenX); maxY = Math.max(maxY, screenY);
            if (ndcX >= -1 && ndcX <= 1 && ndcY >= -1 && ndcY <= 1) inViewport++;
        }
        private JsonObject json() {
            JsonObject result = new JsonObject(); result.addProperty("vertices", vertices); result.addProperty("vertices_in_front_of_near_plane", inFront);
            result.addProperty("vertices_inside_viewport", inViewport);
            JsonArray bounds = new JsonArray();
            if (inFront > 0) { bounds.add(minX); bounds.add(minY); bounds.add(maxX); bounds.add(maxY); }
            result.add("projected_screen_bounds_unclipped", bounds); return result;
        }
    }

    private static int colourDistance(int a, int b) {
        return Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255)) + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255));
    }

    private static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    private static final class ObservedBuffers extends MultiBufferSource.BufferSource {
        private final String skin;
        private final HiddenHand hidden;
        private int skinRequests, atlasRequests, punchySkinRequests, atlasVertices;
        private boolean rightHand, leftHand;
        private final SubmittedBounds rightBounds = new SubmittedBounds(), leftBounds = new SubmittedBounds();
        private ObservedBuffers(ByteBufferBuilder memory, String skin, HiddenHand hidden) { super(memory, new LinkedHashMap<>()); this.skin = skin; this.hidden = hidden; }
        @Override public VertexConsumer getBuffer(RenderType type) {
            String description = type.toString();
            int[] side = {0};
            if (description.contains(skin)) {
                skinRequests++;
                boolean[] punchy = {false};
                StackWalker.getInstance().forEach(frame -> {
                    if (frame.getClassName().equals("punchy.client.render.PunchyArmRenderer")) punchy[0] = true;
                    if (frame.getMethodName().equals("renderRightHand")) { rightHand = true; side[0] = 1; }
                    if (frame.getMethodName().equals("renderLeftHand")) { leftHand = true; side[0] = -1; }
                });
                if (punchy[0]) punchySkinRequests++;
            }
            boolean suppressed = hidden == HiddenHand.RIGHT && side[0] == 1 || hidden == HiddenHand.LEFT && side[0] == -1;
            VertexConsumer original = suppressed ? DISCARD : super.getBuffer(type);
            boolean atlas = description.contains("textures/atlas/blocks.png");
            if (!atlas && side[0] == 0) return original;
            if (atlas) atlasRequests++;
            return new VertexConsumer() {
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    if (atlas) atlasVertices++;
                    if (side[0] == 1) rightBounds.add(x, y, z);
                    else if (side[0] == -1) leftBounds.add(x, y, z);
                    original.addVertex(x, y, z); return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { original.setColor(r, g, b, a); return this; }
                @Override public VertexConsumer setUv(float u, float v) { original.setUv(u, v); return this; }
                @Override public VertexConsumer setUv1(int u, int v) { original.setUv1(u, v); return this; }
                @Override public VertexConsumer setUv2(int u, int v) { original.setUv2(u, v); return this; }
                @Override public VertexConsumer setNormal(float x, float y, float z) { original.setNormal(x, y, z); return this; }
            };
        }
    }

    private record Punchy(Class<?> config, Method itemBlacklisted, Method handBlacklisted, Method armBlacklisted, Method snapshot) {
        private record Baseline(String configuration, boolean enabled, boolean stick, boolean empty, boolean absent,
                                boolean mainHandBlacklisted, boolean offHandBlacklisted) {}
        static Punchy find() throws Exception {
            Class<?> config;
            try { config = Class.forName("punchy.config.PunchyConfig"); }
            catch (ClassNotFoundException absent) { return null; }
            Method arm = Class.forName("punchy.client.render.PunchyArmRenderer")
                    .getDeclaredMethod("isBlacklistedForArm", LocalPlayer.class, HumanoidArm.class);
            arm.setAccessible(true);
            return new Punchy(config, config.getMethod("isItemBlacklisted", ItemStack.class),
                    config.getMethod("isHandBlacklisted", Player.class, InteractionHand.class), arm, config.getMethod("snapshot"));
        }
        Baseline baseline(Player player) throws Exception {
            return new Baseline(new Gson().toJson(snapshot.invoke(null)), (boolean)config.getMethod("isModEnabled").invoke(null),
                    (boolean)itemBlacklisted.invoke(null, new ItemStack(Items.STICK)), (boolean)itemBlacklisted.invoke(null, ItemStack.EMPTY),
                    (boolean)itemBlacklisted.invoke(null, (Object)null), (boolean)handBlacklisted.invoke(null, player, InteractionHand.MAIN_HAND),
                    (boolean)handBlacklisted.invoke(null, player, InteractionHand.OFF_HAND));
        }
        void checkYield(LocalPlayer player) throws Exception {
            for (InteractionHand hand : InteractionHand.values()) {
                check((boolean)itemBlacklisted.invoke(null, player.getItemInHand(hand)), "Punchy item ownership must yield the box and the other hand's stick");
                check((boolean)handBlacklisted.invoke(null, player, hand), "Punchy must yield both native interaction hands");
                HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
                check((boolean)armBlacklisted.invoke(null, player, arm), "Punchy must yield both native physical arms");
            }
        }
        void checkRestored(Player player, Baseline baseline) throws Exception {
            check(baseline.equals(baseline(player)), "Removing the box restores all original Punchy predicates and preserves its entire configuration");
        }
    }

    private static Field field(String name) throws Exception {
        Field field = ItemInHandRenderer.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static int foreground(NativeImage image) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int pixel = image.getPixelRGBA(x, y);
            if (Math.abs((pixel & 255) - 16) + Math.abs((pixel >>> 8 & 255) - 24) + Math.abs((pixel >>> 16 & 255) - 32) > 18) count++;
        }
        return count;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
