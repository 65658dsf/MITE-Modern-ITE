package io.github.ningmeng123.modernite.client;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.joml.Matrix4f;

/** Exercise the complete native hand pass, including AR's outer flush hooks. @author NingMeng123 */
public final class GemBoxOuterHandAudit {
    private static final Field BUFFER_SOURCE = bufferSourceField();
    private static final Method RENDER_HAND = renderHandMethod();

    private GemBoxOuterHandAudit() {}

    /** The caller owns the target framebuffer, camera fixture, projection and final batch flush. */
    public static void render(Minecraft mc, MultiBufferSource.BufferSource observed) throws Exception {
        RenderBuffers buffers = mc.renderBuffers();
        Object original = BUFFER_SOURCE.get(buffers);
        BUFFER_SOURCE.set(buffers, observed);
        try {
            RENDER_HAND.invoke(mc.gameRenderer, mc.gameRenderer.getMainCamera(), 1F, new Matrix4f());
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) throw checked;
            if (cause instanceof Error error) throw error;
            throw new RuntimeException("Native outer hand pass failed", cause);
        } finally {
            BUFFER_SOURCE.set(buffers, original);
        }
    }

    private static Field bufferSourceField() {
        try {
            Field field = RenderBuffers.class.getDeclaredField("bufferSource");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Cannot observe the native RenderBuffers hand source", exception);
        }
    }

    private static Method renderHandMethod() {
        try {
            Method method = GameRenderer.class.getDeclaredMethod("renderItemInHand", Camera.class, float.class, Matrix4f.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Cannot invoke the native outer hand pass", exception);
        }
    }
}
