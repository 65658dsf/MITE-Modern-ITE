package io.github.ningmeng123.modernite.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.joml.Vector3f;

/** A gem-box pass borrows shared player/armor models without retaining its pose. @author NingMeng123 */
public final class GemBoxRenderState implements AutoCloseable {
    /** The player renderer exposes its protected armor layers to the outer dispatcher scope. */
    public interface Owner {
        GemBoxRenderState modernite$captureGemBoxRenderState();
    }

    private static final ClassValue<List<Field>> PART_FIELDS = fieldsOfType(ModelPart.class);
    private static final ClassValue<List<Field>> ARMOR_FIELDS = fieldsOfType(HumanoidModel.class);
    private static final Map<HumanoidModel<?>, Integer> BORROWED = new WeakHashMap<>();
    private final Map<HumanoidModel<?>, ModelState> models = new IdentityHashMap<>();
    private final Map<HumanoidModel<?>, GemBoxEmfPoseCompat.PoseScope> emfPoses = new IdentityHashMap<>();
    private final Map<ModelPart, PartState> parts = new IdentityHashMap<>();
    private boolean closed;

    private GemBoxRenderState(HumanoidModel<?> model) { add(model); }

    public static GemBoxRenderState capture(HumanoidModel<?> model) {
        return new GemBoxRenderState(model).begin();
    }

    /** Nested ordinary-player renders must also preserve a model borrowed by a carrying pass. */
    public static boolean borrowed(HumanoidModel<?> model) { return BORROWED.containsKey(model); }

    /** Native armor models are shared by every player rendered with this skin renderer. */
    public static GemBoxRenderState capture(HumanoidModel<?> model, Iterable<?> layers) {
        GemBoxRenderState saved = new GemBoxRenderState(model);
        for (Object layer : layers) {
            if (!(layer instanceof HumanoidArmorLayer<?, ?, ?>)) continue;
            for (Field field : ARMOR_FIELDS.get(layer.getClass())) {
                try {
                    if (field.get(layer) instanceof HumanoidModel<?> armor) saved.add(armor);
                } catch (IllegalAccessException failure) {
                    throw new IllegalStateException("Cannot preserve a shared gem-box armor model", failure);
                }
            }
        }
        return saved.begin();
    }

    private GemBoxRenderState begin() {
        models.keySet().forEach(model -> BORROWED.merge(model, 1, Integer::sum));
        return this;
    }

    private void add(HumanoidModel<?> model) {
        if (models.containsKey(model)) return;
        models.put(model, new ModelState(model));
        emfPoses.put(model, GemBoxEmfPoseCompat.preserve(model));
        for (Field field : PART_FIELDS.get(model.getClass())) {
            try {
                if (field.get(model) instanceof ModelPart part)
                    part.getAllParts().forEach(child -> parts.putIfAbsent(child, new PartState(child)));
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot preserve a shared gem-box player model", failure);
            }
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            parts.forEach((part, state) -> state.restore(part));
            models.forEach((model, state) -> state.restore(model));
        } finally {
            try {
                emfPoses.values().forEach(GemBoxEmfPoseCompat.PoseScope::close);
            } finally {
                models.keySet().forEach(model -> BORROWED.computeIfPresent(model,
                        (ignored, depth) -> depth > 1 ? depth - 1 : null));
            }
        }
    }

    private static ClassValue<List<Field>> fieldsOfType(Class<?> type) {
        return new ClassValue<>() {
            @Override protected List<Field> computeValue(Class<?> owner) {
                List<Field> fields = new ArrayList<>();
                for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
                    for (Field field : current.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())
                                && field.trySetAccessible()) fields.add(field);
                    }
                }
                return List.copyOf(fields);
            }
        };
    }

    private record ModelState(float attack, boolean riding, boolean young, boolean crouching, float swim,
            HumanoidModel.ArmPose left, HumanoidModel.ArmPose right, Vector3f supportAnchor) {
        private ModelState(HumanoidModel<?> model) {
            this(model.attackTime, model.riding, model.young, model.crouching, model.swimAmount,
                    model.leftArmPose, model.rightArmPose, GemBoxArmPose.savedSupportAnchor(model));
        }
        private void restore(HumanoidModel<?> model) {
            model.attackTime = attack;
            model.riding = riding;
            model.young = young;
            model.crouching = crouching;
            model.swimAmount = swim;
            model.leftArmPose = left;
            model.rightArmPose = right;
            GemBoxArmPose.restoreSupportAnchor(model, supportAnchor);
        }
    }

    private record PartState(float x, float y, float z, float xRot, float yRot, float zRot,
            float xScale, float yScale, float zScale, boolean visible, boolean skipDraw) {
        private PartState(ModelPart part) {
            this(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale,
                    part.visible, part.skipDraw);
        }
        private void restore(ModelPart part) {
            part.x = x; part.y = y; part.z = z;
            part.xRot = xRot; part.yRot = yRot; part.zRot = zRot;
            part.xScale = xScale; part.yScale = yScale; part.zScale = zScale;
            part.visible = visible; part.skipDraw = skipDraw;
        }
    }
}
