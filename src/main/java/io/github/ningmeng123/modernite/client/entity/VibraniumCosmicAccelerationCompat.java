package io.github.ningmeng123.modernite.client.entity;

import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.entity.ArmorAuraRules;
import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;

/** Keeps a cosmic wearer's native armor and custom shader in the same ordered pipeline. @author NingMeng123 */
public final class VibraniumCosmicAccelerationCompat {
    private static final Access ACCESS = Access.find();

    private VibraniumCosmicAccelerationCompat() {}

    /** AR accelerates the original BufferSource through mixins, so instanceof cannot detect it. */
    public static boolean begin(LivingEntity entity) {
        if (ACCESS == null || !ArmorAuraRules.fullProtectionFive(entity)) return false;
        try {
            if (!Boolean.TRUE.equals(ACCESS.enabled.invoke(null))
                    || !Boolean.TRUE.equals(ACCESS.accelerated.invoke(null))) return false;
            // This public stack controller is specifically provided for modded entities. Merely
            // bypassing acceleration on the star mesh draws it BEFORE AR's queued base armor.
            // Scope the complete wearer, including its base model, native armor and GUI portrait.
            ACCESS.vanilla.invoke(null);
            return true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot enter the scoped cosmic armor render pipeline", failure);
        }
    }

    public static void end() {
        try {
            ACCESS.reset.invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot restore the entity acceleration pipeline", failure);
        }
    }

    private record Access(Method enabled, Method accelerated, Method vanilla, Method reset) {
        private static Access find() {
            try {
                Class<?> type = Class.forName("com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature");
                return new Access(type.getMethod("isEnabled"), type.getMethod("shouldUseAcceleratedPipeline"),
                        type.getMethod("useVanillaPipeline"), type.getMethod("resetPipeline"));
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException | LinkageError unsupported) {
                LogUtils.getLogger().warn("Accelerated Rendering does not expose the scoped entity pipeline controller", unsupported);
                return null;
            }
        }
    }
}
