package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Transparent surfaces replayed after the pack's final composite, before hand depth is cleared.
 * Uses the same frame's posed geometry and shared world depth, independent of G-buffer layouts.
 * @author NingMeng123
 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class ShaderPackSurfaceRenderer {
    private static final int MAX_PASSES = 512;
    private static final List<Surface> PENDING = new ArrayList<>();
    private static ByteBufferBuilder storage;
    private static boolean inWorld;
    private static long renderedPasses;
    private static String lastShaderClass = "not_drawn";

    private ShaderPackSurfaceRenderer() {}

    public static void beginWorld() {
        RenderSystem.assertOnRenderThread();
        discard();
        inWorld = true;
    }

    static boolean inWorld() { return inWorld; }
    static long renderedPasses() { return renderedPasses; }
    static int pendingPasses() { return PENDING.size(); }
    static String lastShaderClass() { return lastShaderClass; }

    static void submit(PoseStack pose, Model model, RenderType type, int light, Runnable uniforms) {
        RenderSystem.assertOnRenderThread();
        if (!inWorld || PENDING.size() >= MAX_PASSES) return;
        if (storage == null) storage = new ByteBufferBuilder(256 * 1024);
        MeshData mesh = null;
        try {
            BufferBuilder builder = new BufferBuilder(storage, type.mode(), type.format());
            model.renderToBuffer(pose, builder, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            mesh = builder.build();
            if (mesh != null) PENDING.add(new Surface(mesh, type, uniforms, View.capture()));
        } catch (RuntimeException | Error failure) {
            if (mesh != null) mesh.close();
            inWorld = false;
            discard();
            // A failed model may leave an unfinished tail, which ByteBufferBuilder.clear()
            // retains. Release the allocator as well so it cannot contaminate the next mesh.
            storage.close();
            storage = null;
            throw failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        inWorld = false;
        if (PENDING.isEmpty()) return;
        // GameRenderer dispatches this AFTER LevelRenderer returns and Iris finalizes its pipeline.
        // Earlier stages still run inside the pack; renderLevel TAIL would lose the world depth.
        State previous = new State();
        var matrices = RenderSystem.getModelViewStack();
        matrices.pushMatrix();
        try {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            for (Surface surface : PENDING) {
                surface.view.apply();
                surface.uniforms.run();
                try {
                    surface.type.setupRenderState();
                    BufferUploader.drawWithShader(surface.mesh);
                    ShaderInstance shader = RenderSystem.getShader();
                    lastShaderClass = shader == null ? "null" : shader.getClass().getName();
                    renderedPasses++;
                } finally {
                    surface.type.clearRenderState();
                }
            }
        } finally {
            try {
                matrices.popMatrix();
                RenderSystem.applyModelViewMatrix();
                previous.restore();
            } finally {
                discard();
            }
        }
    }

    @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event) {
        // Aborted world draws must never leak geometry into GUI previews or later frames.
        inWorld = false;
        discard();
    }

    static void reload() {
        RenderSystem.assertOnRenderThread();
        inWorld = false;
        discard();
        if (storage != null) { storage.close(); storage = null; }
        lastShaderClass = "not_drawn";
    }

    private static void discard() {
        PENDING.forEach(surface -> surface.mesh.close());
        PENDING.clear();
    }

    private record Surface(MeshData mesh, RenderType type, Runnable uniforms, View view) {}

    private record View(Matrix4f modelView, Matrix4f projection, VertexSorting sorting,
            float[] color, float fogStart, float fogEnd, float[] fogColor, FogShape fogShape) {
        static View capture() {
            return new View(new Matrix4f(RenderSystem.getModelViewMatrix()),
                    new Matrix4f(RenderSystem.getProjectionMatrix()), RenderSystem.getVertexSorting(),
                    RenderSystem.getShaderColor().clone(), RenderSystem.getShaderFogStart(),
                    RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogColor().clone(), RenderSystem.getShaderFogShape());
        }
        void apply() {
            RenderSystem.getModelViewStack().set(modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            RenderSystem.setShaderFogShape(fogShape);
        }
    }

    /** Restore RenderSystem caches and GL state for subsequent world callbacks, hands and GUI. */
    private static final class State {
        private final View view = View.capture();
        private final ShaderInstance shader = RenderSystem.getShader();
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int activeTexture = GlStateManager._getActiveTexture();
        private final int[] textures = new int[3];
        private final int[] textureBindings = new int[3];
        private final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final int[] colorMask = new int[4];
        private final int[] viewport = new int[4];
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        State() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMask);
            try {
                for (int i = 0; i < textures.length; i++) {
                    textures[i] = RenderSystem.getShaderTexture(i);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                    textureBindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                }
            } finally {
                GlStateManager._activeTexture(activeTexture);
            }
        }
        void restore() {
            view.apply();
            RenderSystem.setShader(() -> shader);
            GlStateManager._glUseProgram(program);
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.setShaderTexture(i, textures[i]);
                // ShaderInstance.clear() unbinds its samplers. Restoring only RenderSystem's
                // sampler cache would leave later raw draws with a different actual binding.
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                GlStateManager._bindTexture(textureBindings[i]);
            }
            GlStateManager._activeTexture(activeTexture);
            // Do not leave BufferUploader believing its immediate VAO is still bound.
            BufferUploader.invalidate();
            GlStateManager._glBindVertexArray(vertexArray);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthMask);
            RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
}
