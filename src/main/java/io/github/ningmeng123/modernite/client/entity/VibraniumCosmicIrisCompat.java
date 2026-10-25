package io.github.ningmeng123.modernite.client.entity;

import java.lang.reflect.Method;
import net.minecraft.client.renderer.MultiBufferSource;

/** Optional Iris detection; no pack-specific programs, textures or private Iris hooks. @author NingMeng123 */
public final class VibraniumCosmicIrisCompat {
    private static final IrisAccess IRIS = IrisAccess.find();
    private static final BufferWrapperAccess BUFFER_WRAPPER = BufferWrapperAccess.find();
    private VibraniumCosmicIrisCompat() {}
    public static boolean activeShaderPack() { return IRIS != null && IRIS.flag(IRIS.packInUse); }
    static boolean shadowPass() { return IRIS != null && IRIS.flag(IRIS.shadowPass); }
    static boolean deferWorldSurface() { return ShaderPackSurfaceRenderer.inWorld() && activeShaderPack(); }
    public static long renderedPasses() { return ShaderPackSurfaceRenderer.renderedPasses(); }
    public static int pendingPasses() { return ShaderPackSurfaceRenderer.pendingPasses(); }
    public static String lastShaderClass() { return ShaderPackSurfaceRenderer.lastShaderClass(); }
    static void reload() { ShaderPackSurfaceRenderer.reload(); }

    /** Iris may keep this wrapper after its shader pack has been disabled in the same session. */
    static MultiBufferSource unwrapBuffers(MultiBufferSource caller) {
        if (BUFFER_WRAPPER == null) return caller;
        // Only unwrap the known delegating Iris wrapper. Unknown sources keep their own contract.
        for (int depth = 0; depth < 8 && BUFFER_WRAPPER.type.isInstance(caller); depth++) {
            try {
                Object original = BUFFER_WRAPPER.original.invoke(caller);
                if (!(original instanceof MultiBufferSource source) || source == caller) break;
                caller = source;
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                break;
            }
        }
        return caller;
    }

    private record BufferWrapperAccess(Class<?> type, Method original) {
        private static BufferWrapperAccess find() {
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.layer.BufferSourceWrapper");
                return new BufferWrapperAccess(type, type.getMethod("getOriginal"));
            } catch (ReflectiveOperationException | LinkageError absent) { return null; }
        }
    }

    private record IrisAccess(Object api, Method packInUse, Method shadowPass) {
        private static IrisAccess find() {
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                return new IrisAccess(type.getMethod("getInstance").invoke(null),
                        type.getMethod("isShaderPackInUse"), type.getMethod("isRenderingShadowPass"));
            } catch (ReflectiveOperationException | LinkageError absent) { return null; }
        }
        private boolean flag(Method method) {
            try { return Boolean.TRUE.equals(method.invoke(api)); }
            catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
        }
    }
}
