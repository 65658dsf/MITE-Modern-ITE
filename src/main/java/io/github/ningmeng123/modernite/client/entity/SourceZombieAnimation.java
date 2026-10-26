package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.FreshAnimationsModelDriver;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Borrows visible zombie CEM motion without replacing species geometry or UVs. @author NingMeng123 */
final class SourceZombieAnimation {
    private final HumanoidModel<Zombie> animationModel;
    private final ModelPart animationRoot;
    private final Map<String, NativeBone> nativeBones = new HashMap<>();
    private boolean applied;
    private boolean headMapped;

    SourceZombieAnimation(HumanoidModel<Zombie> visible, ModelPart nativeRoot, Supplier<ModelPart> rootSupplier) {
        nativeRoot.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (index != 0) return;
            String name = path.substring(path.lastIndexOf('/') + 1);
            ModelPart part = part(visible, name);
            if (part != null) nativeBones.put(name, new NativeBone(part, cube));
        });
        // A headless body still needs the animated head attachment point for its
        // separate ghast/dragon head layer. This virtual cube is never rendered.
        nativeBones.putIfAbsent("head", new NativeBone(visible.head, 0, -4, 0, 8, 8, 8));
        if (rootSupplier == null || !ModList.get().isLoaded("entity_model_features")) {
            animationRoot = null;
            animationModel = null;
        } else {
            // Defer the standard layer bake until after the visible Model constructor,
            // otherwise EMF can attach the hidden CEM root to the species model.
            ModelPart candidateRoot = rootSupplier.get();
            HumanoidModel<Zombie> candidateModel = new HumanoidModel<>(candidateRoot);
            boolean animated = FreshAnimationsModelDriver.hasAnimation(candidateModel);
            animationRoot = animated ? candidateRoot : null;
            animationModel = animated ? candidateModel : null;
        }
    }

    void reset() {
        if (applied) nativeBones.values().forEach(bone -> bone.part().resetPose());
        applied = false;
        headMapped = false;
    }

    void apply(HumanoidModel<Zombie> visible, Zombie entity, float limb, float amount, float age, float yaw, float pitch) {
        if (animationModel == null) return;
        visible.copyPropertiesTo(animationModel);
        animationModel.swimAmount = visible.swimAmount;
        animationRoot.getAllParts().forEach(ModelPart::resetPose);
        animationModel.setupAnim(entity, limb, amount, age, yaw, pitch);
        applied = FreshAnimationsModelDriver.animate(entity, animationModel);
        if (!applied) return;
        animationModel.copyPropertiesTo(visible);
        Map<String, Integer> mapped = new HashMap<>();
        animationRoot.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (index != 0 || !activeGeometry(path)) return;
            String name = boneName(path);
            NativeBone target = nativeBones.get(name);
            if (target == null || !target.sameSize(cube)) return;
            int priority = path.matches(".*(?:^|/)(?:EMF_)?head2(?:/.*)?") ? 2 : 1;
            if (mapped.getOrDefault(name, 0) >= priority) return;
            Matrix4f transform = new Matrix4f(pose.pose()).translate(
                    ((cube.minX + cube.maxX) * .5F - target.centerX()) / 16F,
                    ((cube.minY + cube.maxY) * .5F - target.centerY()) / 16F,
                    ((cube.minZ + cube.maxZ) * .5F - target.centerZ()) / 16F);
            setTransform(target.part(), transform);
            mapped.put(name, priority);
            if (name.equals("head")) headMapped = true;
        });
        visible.hat.copyFrom(visible.head);
    }

    boolean applied() { return applied; }
    boolean headMapped() { return headMapped; }

    private boolean activeGeometry(String path) {
        ModelPart node = animationRoot;
        if (!node.visible) return false;
        for (String name : path.split("/")) {
            if (name.isEmpty()) continue;
            node = node.getChild(name);
            if (!node.visible) return false;
        }
        // ModelPart.visit also traverses EMF's retained but skipped vanilla cubes.
        return !node.skipDraw;
    }

    private static ModelPart part(HumanoidModel<?> model, String name) {
        return switch (name) {
            case "head" -> model.head;
            case "hat" -> model.hat;
            case "body" -> model.body;
            case "right_arm" -> model.rightArm;
            case "left_arm" -> model.leftArm;
            case "right_leg" -> model.rightLeg;
            case "left_leg" -> model.leftLeg;
            default -> null;
        };
    }

    private static String boneName(String path) {
        String[] names = path.split("/");
        for (int i = names.length - 1; i >= 0; i--) {
            String name = names[i].replaceFirst("^EMF_", "");
            if (name.equals("head2")) return "head";
            if (name.equals("head") || name.equals("body") || name.equals("right_arm")
                    || name.equals("left_arm") || name.equals("right_leg") || name.equals("left_leg")) return name;
        }
        return "";
    }

    private static void setTransform(ModelPart part, Matrix4f matrix) {
        Vector3f position = matrix.getTranslation(new Vector3f());
        Vector3f scale = matrix.getScale(new Vector3f());
        // ModelPart uses Rz * Ry * Rx; extract that order directly. The bundled
        // quaternion Euler helper does not exactly invert rotationZYX.
        float sinY = Mth.clamp(-matrix.m02() / scale.x, -1F, 1F);
        float x, z;
        float y = (float)Math.asin(sinY);
        if (Math.abs(sinY) < .9999999F) {
            x = (float)Math.atan2(matrix.m12() / scale.y, matrix.m22() / scale.z);
            z = (float)Math.atan2(matrix.m01() / scale.x, matrix.m00() / scale.x);
        } else {
            x = 0F;
            z = (float)Math.atan2(-matrix.m10() / scale.y, matrix.m11() / scale.y);
        }
        part.setPos(position.x * 16F, position.y * 16F, position.z * 16F);
        part.setRotation(x, y, z);
        part.xScale = scale.x;
        part.yScale = scale.y;
        part.zScale = scale.z;
    }

    private record NativeBone(ModelPart part, float centerX, float centerY, float centerZ,
                              float width, float height, float depth) {
        NativeBone(ModelPart part, ModelPart.Cube cube) {
            this(part, (cube.minX + cube.maxX) * .5F, (cube.minY + cube.maxY) * .5F,
                    (cube.minZ + cube.maxZ) * .5F, cube.maxX - cube.minX, cube.maxY - cube.minY, cube.maxZ - cube.minZ);
        }

        boolean sameSize(ModelPart.Cube cube) {
            return Math.abs(cube.maxX - cube.minX - width) < .001F
                    && Math.abs(cube.maxY - cube.minY - height) < .001F
                    && Math.abs(cube.maxZ - cube.minZ - depth) < .001F;
        }
    }
}
