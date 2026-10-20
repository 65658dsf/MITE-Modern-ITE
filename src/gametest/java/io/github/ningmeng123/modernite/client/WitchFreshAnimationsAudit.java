package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.modernite.client.entity.SourceWitchModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.WitchModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

/** Exact native mesh/UV and real red-eye GPU checks, with optional FA pose sampling. @author NingMeng123 */
final class WitchFreshAnimationsAudit {
    private WitchFreshAnimationsAudit() {}

    static void verify(Minecraft mc, Mob entity, JsonObject facts, Path directory) throws Exception {
        var renderer = (LivingEntityRenderer<?, ?>)mc.getEntityRenderDispatcher().getRenderer(entity);
        require(renderer.getModel() instanceof SourceWitchModel, "Source witch must use its UV-preserving pose model");
        SourceWitchModel model = (SourceWitchModel)renderer.getModel();
        String entityName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        JsonObject audit = new JsonObject(); facts.add("source_witch_animation", audit);
        boolean expectedFa = ModList.get().isLoaded("entity_model_features")
                && mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("optifine/cem/witch.jem")).isPresent();
        audit.addProperty("fresh_animations_expected", expectedFa);
        audit.addProperty("fresh_animations_applied", model.freshAnimationsApplied());
        audit.addProperty("actual_fa_head_mapped", model.freshHeadMapped());
        if (expectedFa) require(model.freshAnimationsApplied() && model.freshHeadMapped(),
                "Real FA witch motion and its reparented head must be sampled");

        Map<String, List<Float>> expected = mesh(WitchModel.createBodyLayer().bakeRoot());
        Map<String, List<Float>> actual = mesh(model.root());
        require(actual.equals(expected), "Visible witch cuboids and every normalized UV must retain the original 64x128 atlas");
        audit.addProperty("native_mesh_and_uv_identical", true);
        audit.addProperty("native_cuboids_checked", expected.size());
        requireAnchoredHat(model);
        audit.addProperty("native_hat_segment_positions_retained", true);

        Map<String, List<Float>> idle = pose(model);
        for (int frame = 0; frame < 3; frame++) entity.walkAnimation.update(1F, 1F);
        entity.tickCount = 60;
        try (NativeImage walking = MobRenderRegressionAudit.capture(mc, entity, false, .15F)) {
            require(!idle.equals(pose(model)), "Actual witch renderer must animate walking rather than remain in a static pose");
            Path path = directory.resolve(entityName + "-walking.png");
            Files.createDirectories(path.getParent()); walking.writeToFile(path);
            audit.addProperty("walking_screenshot", path.toString());
        }
        audit.addProperty("walking_changes_pose", true);
        requireAnchoredHat(model);

        Map<ModelPart, Boolean> visibility = new IdentityHashMap<>();
        model.root().getAllParts().forEach(part -> visibility.put(part, part.visible));
        try {
            for (String name : List.of("body", "arms", "right_leg", "left_leg")) model.root().getChild(name).visible = false;
            model.getHead().getChild("hat").visible = false;
            try (NativeImage face = MobRenderRegressionAudit.capture(mc, entity, true, 0F)) {
                int red = redPixels(face);
                require(red > 4, "Head-only native GPU output must contain the source's emissive red eyes, with red clothing and hat hidden");
                Path path = directory.resolve(entityName + "-red-eyes.png");
                face.writeToFile(path);
                audit.addProperty("head_only_red_eye_pixels", red);
                audit.addProperty("head_only_screenshot", path.toString());
            }
        } finally { visibility.forEach((part, visible) -> part.visible = visible); }
        audit.addProperty("custom_skin_and_glow_retained", true);
    }

    private static void requireAnchoredHat(SourceWitchModel model) {
        ModelPart part = model.getHead().getChild("hat");
        for (int index = 1; index <= 4; index++) {
            PartPose initial = part.getInitialPose();
            require(Math.abs(part.x - initial.x) < .0001F && Math.abs(part.y - initial.y) < .0001F
                    && Math.abs(part.z - initial.z) < .0001F, "Each native witch hat segment stays attached at its original pivot");
            if (index < 4) part = part.getChild("hat" + (index + 1));
        }
    }

    private static Map<String, List<Float>> pose(SourceWitchModel model) {
        Map<String, List<Float>> result = new LinkedHashMap<>();
        for (String name : List.of("head", "body", "arms", "right_leg", "left_leg")) {
            ModelPart part = model.root().getChild(name);
            result.put(name, List.of(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale));
        }
        return result;
    }

    private static Map<String, List<Float>> mesh(ModelPart root) {
        Map<String, List<Float>> result = new LinkedHashMap<>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            Vertices vertices = new Vertices();
            // Identity transforms compare cuboid coordinates and UVs independently of current animation.
            cube.compile(new PoseStack().last(), vertices, 0, 0, -1);
            result.put(path + ":" + index, vertices.values);
        });
        return result;
    }

    private static int redPixels(NativeImage image) {
        int red = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int color = image.getPixelRGBA(x, y), r = color & 255, g = color >>> 8 & 255, b = color >>> 16 & 255;
            if (r > 140 && g < 70 && b < 70 && r > g * 2 && r > b * 2) red++;
        }
        return red;
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class Vertices implements VertexConsumer {
        final List<Float> values = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) { values.add(x); values.add(y); values.add(z); return this; }
        @Override public VertexConsumer setUv(float u, float v) { values.add(u); values.add(v); return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
