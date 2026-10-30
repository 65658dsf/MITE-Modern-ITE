package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Observe actual limb geometry during the native draw, before late animation mods restore model fields. @author NingMeng123 */
final class GemBoxDrawPoseAudit {
    private static final int WIDTH = 480, HEIGHT = 360;
    private GemBoxDrawPoseAudit() {}

    static JsonObject capture(Minecraft mc, ItemStack box, Path folder) throws Exception {
        Files.createDirectories(folder);
        var player = mc.player;
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        HumanoidArm dominant = player.getMainArm();
        CameraType camera = mc.options.getCameraType();
        float[] rotation = {player.getYRot(), player.yRotO, player.getXRot(), player.xRotO,
                player.yBodyRot, player.yBodyRotO, player.yHeadRot, player.yHeadRotO, player.attackAnim, player.oAttackAnim};
        int ticks = player.tickCount;
        boolean swinging = player.swinging;
        float[] walk = {read(player.walkAnimation, "speedOld"), read(player.walkAnimation, "speed"), read(player.walkAnimation, "position")};
        JsonObject result = new JsonObject();
        result.addProperty("scope", "Complete EntityRenderDispatcher.render to PlayerRenderer with third-person camera and native ETF/EMF context; skin UV limb point clouds and ModelPart poses sampled during vertex emission");
        result.addProperty("post_render_model_fields_are_not_the_pose_oracle", true);
        JsonArray samples = new JsonArray(); result.add("samples", samples);
        try {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            player.setYRot(0); player.yRotO = 0; player.setXRot(0); player.xRotO = 0;
            player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = 0;
            player.attackAnim = player.oAttackAnim = 0; player.swinging = false; player.tickCount = 120;
            write(player.walkAnimation, "speedOld", 0); write(player.walkAnimation, "speed", 0); write(player.walkAnimation, "position", 0);
            for (HumanoidArm mainArm : HumanoidArm.values()) {
                player.setMainArm(mainArm);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                try (Frame empty = frame(mc)) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                    try (Frame beforeStick = frame(mc)) {
                        for (InteractionHand hand : InteractionHand.values()) {
                            ItemStack animated = box.copy();
                            player.setItemInHand(hand, animated);
                            player.setItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                                    new ItemStack(Items.STICK));
                            String name = mainArm.name().toLowerCase() + "-" + hand.name().toLowerCase();
                            Stats previousRight = null;
                            for (int elapsed : new int[]{-1, 16, 24, 35, 40, 52}) {
                                animated.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get()); animated.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
                                if (elapsed >= 0) animated.set(ExtremeGear.GEM_BOX_OPEN_STARTED.get(), mc.level.getGameTime() - elapsed);
                                if (elapsed >= 36) animated.set(ExtremeGear.GEM_BOX_REVEALED_GEM.get(), GemType.FIREPROOF.ordinal() + 1);
                                JsonObject facts = new JsonObject(); samples.add(facts);
                                facts.addProperty("main_arm", mainArm.name()); facts.addProperty("hand", hand.name()); facts.addProperty("elapsed_ticks", elapsed);
                                try (Frame actual = frame(mc)) {
                                    Path png = folder.resolve("gem-box-draw-pose-" + name + "-" + (elapsed < 0 ? "idle" : elapsed) + ".png");
                                    actual.image().writeToFile(png); facts.addProperty("preview", png.toString());
                                    facts.add("drawn_limbs", actual.json());
                                    check(GemBoxArmPose.active(player), "The actual third-person fixture must own the complete carry pose");
                                    for (Part arm : new Part[]{Part.RIGHT_ARM, Part.LEFT_ARM}) {
                                        Stats limb = actual.require(arm), hanging = empty.require(arm);
                                        float alignment = alignment(limb.axis(), hanging.axis());
                                        facts.addProperty(arm.name().toLowerCase() + "_alignment_with_empty_arm", alignment);
                                        check(alignment < .85F,
                                                "The actual GPU " + arm.name() + " must be raised from the empty hanging pose at draw time: " + name + "/" + elapsed + " alignment=" + alignment);
                                        Part sleeve = arm == Part.RIGHT_ARM ? Part.RIGHT_SLEEVE : Part.LEFT_SLEEVE;
                                        Stats cloth = actual.require(sleeve);
                                        float sleeveAlignment = alignment(limb.axis(), cloth.axis());
                                        float sleeveGap = distance(limb.centre(), cloth.centre()) / limb.length();
                                        facts.addProperty(sleeve.name().toLowerCase() + "_alignment_with_arm", sleeveAlignment);
                                        facts.addProperty(sleeve.name().toLowerCase() + "_relative_centre_gap", sleeveGap);
                                        check(sleeveAlignment > .97F && sleeveGap < .15F,
                                                "The actual skin sleeve must remain aligned and attached to its raised arm: " + name + "/" + elapsed + "/" + sleeve);
                                    }
                                    Stats right = actual.require(Part.RIGHT_ARM);
                                    if (previousRight != null && (elapsed == 24 || elapsed == 40 || elapsed == 52)) {
                                        float turn = 1F - alignment(previousRight.axis(), right.axis());
                                        float movement = distance(previousRight.centre(), right.centre()) / right.length();
                                        facts.addProperty("right_arm_turn_from_previous_phase", turn);
                                        facts.addProperty("right_arm_relative_movement_from_previous_phase", movement);
                                        check(turn > .003F || movement > .025F,
                                                "The right arm must actually change its drawn geometry for rummaging and extraction: " + name + "/" + elapsed);
                                    }
                                    previousRight = right;
                                }
                            }
                        }
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                        try (Frame afterStick = frame(mc)) {
                            Path png = folder.resolve("gem-box-draw-pose-" + mainArm.name().toLowerCase() + "-restored-stick.png");
                            afterStick.image().writeToFile(png);
                            int changed = difference(beforeStick.image(), afterStick.image());
                            check(changed == 0, "Switching back to an ordinary stick restores the complete native player draw without residual arm or sleeve poses");
                            for (Part part : Part.values()) {
                                Stats before = beforeStick.require(part), after = afterStick.require(part);
                                check(distance(before.centre(), after.centre()) < .0001F && alignment(before.axis(), after.axis()) > .9999F,
                                        "The ordinary-item control restores the exact emitted limb geometry: " + part);
                            }
                        }
                    }
                }
            }
            result.addProperty("status", "passed_actual_draw_time_arms_and_sleeves");
            return result;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off);
            player.setMainArm(dominant); mc.options.setCameraType(camera); player.tickCount = ticks; player.swinging = swinging;
            player.setYRot(rotation[0]); player.yRotO = rotation[1]; player.setXRot(rotation[2]); player.xRotO = rotation[3];
            player.yBodyRot = rotation[4]; player.yBodyRotO = rotation[5]; player.yHeadRot = rotation[6]; player.yHeadRotO = rotation[7];
            player.attackAnim = rotation[8]; player.oAttackAnim = rotation[9];
            write(player.walkAnimation, "speedOld", walk[0]); write(player.walkAnimation, "speed", walk[1]); write(player.walkAnimation, "position", walk[2]);
        }
    }

    static Frame movementFrame(Minecraft mc) { return frame(mc); }

    private static Frame frame(Minecraft mc) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
        Recorder recorder = new Recorder(mc, renderer.getModel());
        try {
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, WIDTH, HEIGHT, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1); Lighting.setupForEntityInInventory();
            PoseStack pose = new PoseStack(); pose.translate(WIDTH / 2F, HEIGHT - 22F, 50); pose.scale(145, 145, -145);
            pose.mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI + .35F));
            renderThroughDispatcher(mc, pose, recorder);
            mc.renderBuffers().bufferSource().endBatch();
            return new Frame(Screenshot.takeScreenshot(target), recorder.stats(), recorder.drawPoses, recorder.afterPoses(), recorder.atlasVertices, recorder.modelEvidence);
        } finally {
            mc.renderBuffers().bufferSource().endBatch(); view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); Lighting.setupFor3DItems(); mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    /** Preserve the native ETF/EMF render-context lifecycle; never mount a synthetic mod state. */
    static void renderThroughDispatcher(Minecraft mc, PoseStack pose, MultiBufferSource buffers) {
        renderThroughDispatcher(mc, mc.player, pose, buffers);
    }

    static void renderThroughDispatcher(Minecraft mc, Entity entity, PoseStack pose, MultiBufferSource buffers) {
        var dispatcher = mc.getEntityRenderDispatcher();
        boolean shadows;
        try {
            Field shadow = EntityRenderDispatcher.class.getDeclaredField("shouldRenderShadow");
            shadow.setAccessible(true); shadows = shadow.getBoolean(dispatcher);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot preserve the native dispatcher shadow flag for the owned GPU audit", failure);
        }
        boolean hitboxes = dispatcher.shouldRenderHitBoxes();
        dispatcher.setRenderShadow(false); dispatcher.setRenderHitBoxes(false);
        try {
            dispatcher.render(entity, 0, 0, 0, 0, 1, pose, buffers, LightTexture.FULL_BRIGHT);
        } finally {
            dispatcher.setRenderShadow(shadows); dispatcher.setRenderHitBoxes(hitboxes);
        }
    }

    enum Part { RIGHT_ARM, LEFT_ARM, RIGHT_SLEEVE, LEFT_SLEEVE }
    private record Point(float x, float y, float z, float u, float v) {}
    record Stats(int vertices, float[] centre, float[] axis, float length) {
        JsonObject json() {
            JsonObject result = new JsonObject(); result.addProperty("vertices", vertices); result.addProperty("long_axis_length", length);
            result.add("centre", numbers(centre)); result.add("long_axis", numbers(axis)); return result;
        }
    }
    record Frame(NativeImage image, Map<Part, Stats> limbs, Map<Part, float[]> atDraw, Map<Part, float[]> afterDraw, int atlasVertices, JsonObject modelEvidence) implements AutoCloseable {
        Stats require(Part part) {
            Stats stats = limbs.get(part); check(stats != null && stats.vertices() >= 16 && stats.length() > 20,
                    "The real skin buffer must contain measurable native geometry for " + part); return stats;
        }
        JsonObject json() {
            JsonObject result = new JsonObject();
            if (modelEvidence != null) result.add("actual_player_model_during_vertex_emission", modelEvidence);
            for (Part part : Part.values()) {
                JsonObject limb = limbs.containsKey(part) ? limbs.get(part).json() : new JsonObject();
                if (atDraw.containsKey(part)) limb.add("model_part_pose_during_vertex_emission", numbers(atDraw.get(part)));
                if (afterDraw.containsKey(part)) limb.add("model_part_pose_after_renderer_return", numbers(afterDraw.get(part)));
                result.add(part.name().toLowerCase(), limb);
            }
            return result;
        }
        @Override public void close() { image.close(); }
    }

    private static final class Recorder implements MultiBufferSource {
        private final Minecraft mc;
        private final PlayerModel<?> model;
        private final Map<Part, List<Point>> points = new EnumMap<>(Part.class);
        private final Map<Part, float[]> drawPoses = new EnumMap<>(Part.class);
        private int atlasVertices;
        private JsonObject modelEvidence;
        private Recorder(Minecraft mc, PlayerModel<?> model) { this.mc = mc; this.model = model; }
        @Override public VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) {
            VertexConsumer original = mc.renderBuffers().bufferSource().getBuffer(type);
            boolean skin = type.toString().contains(mc.player.getSkin().texture().toString());
            boolean atlas = type.toString().contains("textures/atlas/blocks.png");
            if (!skin && !atlas) return original;
            return new VertexConsumer() {
                private final List<Point> quad = new ArrayList<>(4);
                private float x, y, z;
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    this.x = x; this.y = y; this.z = z;
                    if (atlas) atlasVertices++;
                    if (skin && modelEvidence == null) modelEvidence = GemBoxMovementAudit.emfEvidence(mc);
                    original.addVertex(x, y, z); return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { original.setColor(r, g, b, a); return this; }
                @Override public VertexConsumer setUv(float u, float v) {
                    if (skin) {
                        quad.add(new Point(x, y, z, u, v));
                        if (quad.size() == 4) { recordQuad(quad); quad.clear(); }
                    }
                    original.setUv(u, v); return this;
                }
                @Override public VertexConsumer setUv1(int u, int v) { original.setUv1(u, v); return this; }
                @Override public VertexConsumer setUv2(int u, int v) { original.setUv2(u, v); return this; }
                @Override public VertexConsumer setNormal(float x, float y, float z) { original.setNormal(x, y, z); return this; }
            };
        }
        private void recordQuad(List<Point> quad) {
            float u = 0, v = 0;
            for (Point point : quad) { u += point.u() * 16; v += point.v() * 16; }
            Part part = null;
            if (u > 40 && u < 56 && v > 16 && v < 32) part = Part.RIGHT_ARM;
            else if (u > 32 && u < 48 && v > 48 && v < 64) part = Part.LEFT_ARM;
            else if (u > 40 && u < 56 && v > 32 && v < 48) part = Part.RIGHT_SLEEVE;
            else if (u > 48 && u < 64 && v > 48 && v < 64) part = Part.LEFT_SLEEVE;
            if (part == null) return;
            points.computeIfAbsent(part, key -> new ArrayList<>()).addAll(quad);
            drawPoses.putIfAbsent(part, pose(modelPart(model, part)));
        }
        private Map<Part, Stats> stats() {
            Map<Part, Stats> result = new EnumMap<>(Part.class);
            points.forEach((part, vertices) -> result.put(part, principalAxis(vertices))); return result;
        }
        private Map<Part, float[]> afterPoses() {
            Map<Part, float[]> result = new EnumMap<>(Part.class);
            for (Part part : Part.values()) result.put(part, pose(modelPart(model, part))); return result;
        }
    }

    private static Stats principalAxis(List<Point> points) {
        double[] mean = new double[3];
        for (Point point : points) { mean[0] += point.x(); mean[1] += point.y(); mean[2] += point.z(); }
        for (int index = 0; index < 3; index++) mean[index] /= points.size();
        double[][] covariance = new double[3][3];
        for (Point point : points) {
            double[] delta = {point.x() - mean[0], point.y() - mean[1], point.z() - mean[2]};
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) covariance[row][column] += delta[row] * delta[column];
        }
        int maximum = covariance[1][1] > covariance[0][0] ? 1 : 0;
        if (covariance[2][2] > covariance[maximum][maximum]) maximum = 2;
        double[] axis = new double[3]; axis[maximum] = 1;
        for (int iteration = 0; iteration < 32; iteration++) {
            double[] next = new double[3];
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) next[row] += covariance[row][column] * axis[column];
            double length = Math.sqrt(next[0] * next[0] + next[1] * next[1] + next[2] * next[2]);
            check(length > .00001, "The emitted arm geometry has a measurable long axis");
            for (int index = 0; index < 3; index++) axis[index] = next[index] / length;
        }
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (Point point : points) {
            double projected = point.x() * axis[0] + point.y() * axis[1] + point.z() * axis[2];
            low = Math.min(low, projected); high = Math.max(high, projected);
        }
        return new Stats(points.size(), new float[]{(float)mean[0], (float)mean[1], (float)mean[2]},
                new float[]{(float)axis[0], (float)axis[1], (float)axis[2]}, (float)(high - low));
    }
    private static ModelPart modelPart(PlayerModel<?> model, Part part) {
        return switch (part) { case RIGHT_ARM -> model.rightArm; case LEFT_ARM -> model.leftArm; case RIGHT_SLEEVE -> model.rightSleeve; case LEFT_SLEEVE -> model.leftSleeve; };
    }
    private static float[] pose(ModelPart part) { return new float[]{part.xRot, part.yRot, part.zRot, part.x, part.y, part.z, part.xScale, part.yScale, part.zScale}; }
    private static JsonArray numbers(float[] values) { JsonArray result = new JsonArray(); for (float value : values) result.add(value); return result; }
    private static float alignment(float[] a, float[] b) { return Math.abs(a[0] * b[0] + a[1] * b[1] + a[2] * b[2]); }
    private static float distance(float[] a, float[] b) { float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2]; return (float)Math.sqrt(x * x + y * y + z * z); }
    private static int difference(NativeImage a, NativeImage b) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) if (a.getPixelRGBA(x, y) != b.getPixelRGBA(x, y)) count++;
        return count;
    }
    private static float read(WalkAnimationState walk, String name) throws Exception { Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); return field.getFloat(walk); }
    private static void write(WalkAnimationState walk, String name, float value) throws Exception { Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); field.setFloat(walk, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
