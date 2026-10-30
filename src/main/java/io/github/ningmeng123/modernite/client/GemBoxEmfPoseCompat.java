package io.github.ningmeng123.modernite.client;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;

/** Restore only the carrying arms after optional EMF animation hooks. @author NingMeng123 */
public final class GemBoxEmfPoseCompat {
    // Values also use weak references: EMF model parts retain their root internally.
    private static final Map<Object, CarryPose> POSES = new WeakHashMap<>();
    private static final EmfAccess EMF = EmfAccess.find();

    private GemBoxEmfPoseCompat() {}

    /** Call after the complete player setup and the carrying pose, including sleeve copies. */
    public static void capture(HumanoidModel<?> model, LivingEntity entity) {
        Object root = root(model);
        if (root == null) return;
        if (!(model instanceof PlayerModel<?> player) || !GemBoxArmPose.active(entity)) {
            POSES.remove(root);
            return;
        }
        Set<ModelPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<PartPose> parts = new ArrayList<>();
        captureTree(player.rightArm, seen, parts);
        captureTree(player.leftArm, seen, parts);
        captureTree(player.rightSleeve, seen, parts);
        captureTree(player.leftSleeve, seen, parts);
        remember(root, model, entity, parts, false, List.of());
    }

    /** Armor has its own EMF root and receives the same late animation callbacks as the skin. */
    public static void captureArmor(HumanoidModel<?> model, LivingEntity entity) {
        Object root = root(model);
        if (root == null || !GemBoxArmPose.active(entity)) return;
        // Keep the armor pack's child-part inflation and detail animation independent.
        remember(root, model, entity, List.of(new PartPose(model.rightArm), new PartPose(model.leftArm)), false, List.of());
    }

    /** ISMAH prepares one armor arm immediately before its EMF root animates and draws. */
    public static void captureFirstPersonArmor(HumanoidModel<?> model, HumanoidArm arm) {
        LivingEntity entity = Minecraft.getInstance().player;
        if (entity == null || !GemBoxArmPose.active(entity) || !GemBoxRenderState.borrowed(model)) return;
        Object root = root(model);
        if (root == null) return;
        Set<ModelPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<PartVisibility> visibility = new ArrayList<>();
        for (ModelPart part : List.of(model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg))
            part.getAllParts().filter(seen::add).forEach(child -> visibility.add(new PartVisibility(child)));
        remember(root, model, entity,
                List.of(new PartPose(arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm)), true, visibility);
    }

    private static void remember(Object root, HumanoidModel<?> model, LivingEntity entity,
            List<PartPose> parts, boolean firstPerson, List<PartVisibility> visibility) {
        POSES.put(root, new CarryPose(new WeakReference<>(model), new WeakReference<>(entity),
                entity.getUUID(), List.copyOf(parts), firstPerson, List.copyOf(visibility)));
    }

    /** Clear before each native first-person hand render, which reuses the player model. */
    public static void clear(HumanoidModel<?> model) {
        Object root = root(model);
        if (root != null) POSES.remove(root);
    }

    /** Preserve an outer carrying pass when first-person or another player's nested draw reuses its root. */
    public static PoseScope preserve(HumanoidModel<?> model) {
        Object root = root(model);
        return new PoseScope(root, root == null ? null : POSES.get(root));
    }

    public static final class PoseScope implements AutoCloseable {
        private final Object root;
        private final CarryPose previous;
        private boolean closed;
        private PoseScope(Object root, CarryPose previous) { this.root = root; this.previous = previous; }
        @Override public void close() {
            if (closed || root == null) return;
            closed = true;
            POSES.remove(root);
            if (previous == null) return;
            HumanoidModel<?> model = previous.model.get();
            LivingEntity entity = previous.entity.get();
            if (model != null && entity != null && GemBoxArmPose.active(entity) && root(model) == root)
                POSES.put(root, previous);
        }
    }

    /** Called at EMFModelPartRoot.animate TAIL, after every EMF onAnimationEnd callback. */
    public static void restore(Object root) {
        CarryPose saved = POSES.get(root);
        if (saved == null || EMF == null) return;
        HumanoidModel<?> model = saved.model.get();
        LivingEntity entity = saved.entity.get();
        if (model == null || entity == null || !GemBoxArmPose.active(entity)) {
            POSES.remove(root);
            return;
        }
        try {
            Object state = EMF.state.invoke(null);
            if (state == null || Boolean.TRUE.equals(EMF.firstPersonHand.invoke(state)) != saved.firstPerson) return;
            Object current = EMF.entity.invoke(state);
            // One skin renderer/root may render several players consecutively.
            if (current != entity || !entity.getUUID().equals(saved.uuid) || root(model) != root) return;
            for (PartPose part : saved.parts) part.restore();
            for (PartVisibility part : saved.visibility) part.restore();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            POSES.remove(root);
        }
    }

    private static void captureTree(ModelPart part, Set<ModelPart> seen, List<PartPose> poses) {
        part.getAllParts().filter(seen::add).forEach(child -> poses.add(new PartPose(child)));
    }

    private static Object root(HumanoidModel<?> model) {
        if (EMF == null || !EMF.root.getDeclaringClass().isInstance(model)) return null;
        try {
            return EMF.root.invoke(model);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private record CarryPose(WeakReference<HumanoidModel<?>> model, WeakReference<LivingEntity> entity,
            UUID uuid, List<PartPose> parts, boolean firstPerson, List<PartVisibility> visibility) {}

    private record PartVisibility(WeakReference<ModelPart> part, boolean visible, boolean skipDraw) {
        private PartVisibility(ModelPart part) { this(new WeakReference<>(part), part.visible, part.skipDraw); }
        private void restore() {
            ModelPart target = part.get();
            if (target != null) { target.visible = visible; target.skipDraw = skipDraw; }
        }
    }

    private record PartPose(WeakReference<ModelPart> part, float x, float y, float z,
            float xRot, float yRot, float zRot, float xScale, float yScale, float zScale) {
        private PartPose(ModelPart part) {
            this(new WeakReference<>(part), part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale);
        }

        private void restore() {
            ModelPart target = part.get();
            if (target == null) return;
            target.x = x;
            target.y = y;
            target.z = z;
            target.xRot = xRot;
            target.yRot = yRot;
            target.zRot = zRot;
            target.xScale = xScale;
            target.yScale = yScale;
            target.zScale = zScale;
        }
    }

    /** Public EMF interfaces only; the mod remains optional and needs no linked EMF classes. */
    private record EmfAccess(Method root, Method state, Method firstPersonHand, Method entity) {
        private static EmfAccess find() {
            try {
                Class<?> model = Class.forName("traben.entity_model_features.models.IEMFModel");
                Class<?> state = Class.forName("traben.entity_model_features.models.animation.state.EMFState");
                Class<?> renderState = Class.forName("traben.entity_model_features.models.animation.state.EMFEntityRenderState");
                return new EmfAccess(model.getMethod("emf$getEMFRootModel"), state.getMethod("state"),
                        renderState.getMethod("isFirstPersonHand"), renderState.getMethod("emfEntity"));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
