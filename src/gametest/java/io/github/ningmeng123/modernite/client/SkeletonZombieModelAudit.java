package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.modernite.client.entity.SkeletonZombieModel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.neoforged.fml.ModList;

/** Original humanoid atlas and two-frame vanilla fallback controls. @author NingMeng123 */
final class SkeletonZombieModelAudit {
    private SkeletonZombieModelAudit() {}

    static void verify(Minecraft mc, Mob entity, JsonObject facts) throws Exception {
        var renderer = (LivingEntityRenderer<?, ?>)mc.getEntityRenderDispatcher().getRenderer(entity);
        require(renderer.getModel() instanceof SkeletonZombieModel, "Skeleton zombie must retain its dedicated native geometry model");
        SkeletonZombieModel model = (SkeletonZombieModel)renderer.getModel();
        JsonObject audit = new JsonObject(); facts.add("skeleton_zombie_animation", audit);
        boolean emfLoaded = ModList.get().isLoaded("entity_model_features");
        boolean expectedFa = emfLoaded && (mc.getResourceManager().getResource(
                ResourceLocation.withDefaultNamespace("optifine/cem/skeleton.jem")).isPresent()
                || mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("emf/cem/skeleton.jem")).isPresent());
        boolean applied = applied(model);
        audit.addProperty("emf_loaded", emfLoaded);
        audit.addProperty("fresh_animations_expected", expectedFa);
        audit.addProperty("fresh_animations_applied", applied);
        if (expectedFa) require(applied, "The actual rendered skeleton zombie must sample the installed skeleton animation");
        if (!emfLoaded) require(!applied, "Without EMF the native renderer must not borrow an animation pose");

        SkeletonModel<AbstractSkeleton> nativeControl = new SkeletonModel<>(nativeRoot());
        Map<String, List<Float>> expected = mesh(nativeControl);
        Map<String, List<Float>> actual = mesh(model);
        require(expected.size() == 7, "The original humanoid skeleton-zombie mesh contains seven cuboids");
        require(actual.equals(expected), "Every native cuboid coordinate and normalized UV must match the original Humanoid 64x64 mesh");
        require(!expected.equals(mesh(new SkeletonModel<AbstractSkeleton>(SkeletonModel.createBodyLayer().bakeRoot()))),
                "The negative control must distinguish the incompatible narrow Skeleton 64x32 mesh");
        audit.addProperty("native_cuboids_checked", actual.size());
        audit.addProperty("native_mesh_and_uv_identical", true);
        audit.addProperty("native_texture_width", 64);
        audit.addProperty("native_texture_height", 64);
        audit.addProperty("skeleton_64x32_negative_control_differs", true);
        audit.add("native_fallback_swimming", fallback((AbstractSkeleton)entity));
    }

    private static JsonObject fallback(AbstractSkeleton entity) throws Exception {
        // Direct layer baking supplies no CEM model even in the FA-enabled run, so both runs
        // exercise the same no-animation branch without modifying global mod or render state.
        SkeletonZombieModel actual = new SkeletonZombieModel(nativeRoot(), () -> SkeletonModel.createBodyLayer().bakeRoot());
        SkeletonModel<AbstractSkeleton> expected = new SkeletonModel<>(nativeRoot());
        actual.swimAmount = expected.swimAmount = .5F;
        actual.head.xRot = expected.head.xRot = .7F;
        JsonObject result = new JsonObject(); JsonArray frames = new JsonArray(); result.add("frames", frames);
        float firstHead = 0;
        for (int frame = 0; frame < 2; frame++) {
            actual.setupAnim(entity, .8F, .35F, 20F + frame, 12F, 30F);
            expected.setupAnim(entity, .8F, .35F, 20F + frame, 12F, 30F);
            require(!applied(actual), "Direct native controls must keep the Fresh Animations fallback inactive");
            require(pose(actual).equals(pose(expected)), "No-animation fallback must equal vanilla SkeletonModel for all seven bones on swimming frame " + frame);
            JsonObject row = new JsonObject(); frames.add(row);
            row.addProperty("frame", frame); row.addProperty("actual_head_x_rot", actual.head.xRot);
            row.addProperty("vanilla_head_x_rot", expected.head.xRot); row.addProperty("all_bone_transforms_equal", true);
            if (frame == 0) firstHead = actual.head.xRot;
            else require(Math.abs(firstHead - actual.head.xRot) > .0001F,
                    "The second frame must exercise preserved head interpolation instead of a reset pose");
        }
        result.addProperty("swim_amount", .5F); result.addProperty("initial_head_x_rot", .7F);
        result.addProperty("frames_checked", frames.size()); result.addProperty("passed", true);
        return result;
    }

    private static ModelPart nativeRoot() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64).bakeRoot();
    }

    private static boolean applied(SkeletonZombieModel model) throws Exception {
        Field field = SkeletonZombieModel.class.getDeclaredField("freshAnimationsApplied");
        field.setAccessible(true);
        return field.getBoolean(model);
    }

    private static Map<String, ModelPart> parts(HumanoidModel<?> model) {
        Map<String, ModelPart> result = new LinkedHashMap<>();
        result.put("head", model.head); result.put("hat", model.hat); result.put("body", model.body);
        result.put("right_arm", model.rightArm); result.put("left_arm", model.leftArm);
        result.put("right_leg", model.rightLeg); result.put("left_leg", model.leftLeg);
        return result;
    }

    private static Map<String, List<Float>> mesh(HumanoidModel<?> model) {
        Map<String, List<Float>> result = new LinkedHashMap<>();
        parts(model).forEach((name, part) -> part.visit(new PoseStack(), (pose, path, index, cube) -> {
            Vertices vertices = new Vertices();
            cube.compile(new PoseStack().last(), vertices, 0, 0, -1);
            result.put(name + path + ":" + index, vertices.values);
        }));
        return result;
    }

    private static Map<String, List<Float>> pose(HumanoidModel<?> model) {
        Map<String, List<Float>> result = new LinkedHashMap<>();
        parts(model).forEach((name, part) -> result.put(name, List.of(part.x, part.y, part.z,
                part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)));
        return result;
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
