package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.FreshAnimationsModelDriver;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.model.WitchModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.monster.Witch;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Samples FA motion while keeping the source skin, emissive eyes and connected native hat. @author NingMeng123 */
public final class SourceWitchModel extends WitchModel<Witch> {
    private final WitchModel<Witch> animationModel;
    private boolean freshAnimationsApplied;
    private boolean freshHeadMapped;

    public SourceWitchModel(ModelPart nativeRoot, Supplier<ModelPart> animationRoot) {
        super(nativeRoot);
        // EMF associates a baked layer with the next model constructor. Keep the visible
        // source layer and the hidden standard witch layer as two separate model instances.
        animationModel = new WitchModel<>(animationRoot.get());
    }

    @Override public void setHoldingItem(boolean holding) {
        super.setHoldingItem(holding);
        if (animationModel != null) animationModel.setHoldingItem(holding);
    }

    @Override public void setupAnim(Witch entity, float limb, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity, limb, amount, age, yaw, pitch);
        copyPropertiesTo(animationModel);
        animationModel.root().getAllParts().forEach(ModelPart::resetPose);
        animationModel.setupAnim(entity, limb, amount, age, yaw, pitch);
        freshHeadMapped = false;
        freshAnimationsApplied = FreshAnimationsModelDriver.animate(entity, animationModel);
        if (!freshAnimationsApplied) return;

        root().copyFrom(animationModel.root());
        for (String name : new String[]{"head", "body", "arms", "right_leg", "left_leg"})
            root().getChild(name).copyFrom(animationModel.root().getChild(name));

        // FA relocates its actual head under the animated body; the canonical head is
        // only an animation reference. Flatten that real head pose into our root-level
        // head, while retaining every original cuboid and the red-eye atlas coordinates.
        Matrix4f rootInverse = transform(animationModel.root()).invert();
        Map<String, Vector3f> hatMotion = new HashMap<>();
        animationModel.root().visit(new PoseStack(), (pose, path, index, cube) -> {
            if (index != 0) return;
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.equals("EMF_head2") || name.equals("head2")) {
                if (!sameSize(cube, 8F, 10F, 8F)) return;
                // FA's head cuboid projects 1.5 pixels farther forward. Match centers
                // instead of transplanting that cuboid or its separate facial texture.
                Matrix4f head = new Matrix4f(rootInverse).mul(pose.pose()).translate(
                        (cube.minX + cube.maxX) / 32F,
                        ((cube.minY + cube.maxY) * .5F + 5F) / 16F,
                        (cube.minZ + cube.maxZ) / 32F);
                setTransform(getHead(), head);
                freshHeadMapped = true;
            } else if (name.matches("(?:EMF_)?bone[456]")) {
                ModelPart part = partAt(animationModel.root(), path);
                PartPose initial = part.getInitialPose();
                hatMotion.put(name.replace("EMF_", ""), new Vector3f(
                        part.xRot - initial.xRot, part.yRot - initial.yRot, part.zRot - initial.zRot));
            }
        });
        // The old atlas describes a connected native hat. Add FA's local sway without
        // copying the reparented FA hat segment positions, which would separate its tip.
        ModelPart tip = getHead().getChild("hat");
        for (int number = 4; number <= 6; number++) {
            tip = tip.getChild("hat" + (number - 2));
            Vector3f motion = hatMotion.get("bone" + number);
            if (motion != null) { tip.xRot += motion.x; tip.yRot += motion.y; tip.zRot += motion.z; }
        }
    }

    public boolean freshAnimationsApplied() { return freshAnimationsApplied; }
    public boolean freshHeadMapped() { return freshHeadMapped; }

    private static Matrix4f transform(ModelPart part) {
        PoseStack pose = new PoseStack(); part.translateAndRotate(pose); return new Matrix4f(pose.last().pose());
    }

    private static void setTransform(ModelPart part, Matrix4f matrix) {
        Vector3f position = matrix.getTranslation(new Vector3f());
        Vector3f rotation = matrix.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());
        Vector3f scale = matrix.getScale(new Vector3f());
        part.setPos(position.x * 16F, position.y * 16F, position.z * 16F);
        part.setRotation(rotation.x, rotation.y, rotation.z);
        part.xScale = scale.x; part.yScale = scale.y; part.zScale = scale.z;
    }

    private static ModelPart partAt(ModelPart root, String path) {
        ModelPart part = root;
        for (String name : path.split("/")) if (!name.isEmpty()) part = part.getChild(name);
        return part;
    }

    private static boolean sameSize(ModelPart.Cube cube, float x, float y, float z) {
        return Math.abs(cube.maxX - cube.minX - x) < .001F
                && Math.abs(cube.maxY - cube.minY - y) < .001F
                && Math.abs(cube.maxZ - cube.minZ - z) < .001F;
    }
}
