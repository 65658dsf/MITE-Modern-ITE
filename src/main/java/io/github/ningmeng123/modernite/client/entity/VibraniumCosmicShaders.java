package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.ningmeng123.modernite.ModernIte;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Reloadable, immediately drawn cosmic armor passes with a deterministic clock. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class VibraniumCosmicShaders {
    public static final int PERIOD_TICKS = 400;
    private static final int BUFFER_BYTES = 128 * 1024;
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "vibranium_cosmic");
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static ShaderInstance shader;
    private static Uniform cosmicTime;
    private static Uniform cosmicYaw;
    private static Uniform cosmicPitch;
    private static ByteBufferBuilder fallbackStorage;
    private static MultiBufferSource.BufferSource fallbackBuffers;

    private VibraniumCosmicShaders() {}

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        // Reuse one native allocation across frames, and release the old allocation on reload.
        if (fallbackStorage != null) {
            try {
                fallbackBuffers.endBatch();
            } finally {
                fallbackStorage.close();
                fallbackStorage = null;
                fallbackBuffers = null;
            }
        }
        shader = null;
        cosmicTime = null;
        cosmicYaw = null;
        cosmicPitch = null;
        TYPES.clear();
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.NEW_ENTITY), loaded -> {
            cosmicTime = requiredUniform(loaded, "CosmicTime");
            cosmicYaw = requiredUniform(loaded, "CosmicYaw");
            cosmicPitch = requiredUniform(loaded, "CosmicPitch");
            shader = loaded;
        });
    }

    private static Uniform requiredUniform(ShaderInstance loaded, String name) {
        return Objects.requireNonNull(loaded.getUniform(name), "Cosmic armor shader is missing uniform " + name);
    }

    public static boolean isReady() {
        return shader != null;
    }

    public static void render(PoseStack pose, MultiBufferSource caller, VibraniumCosmicModel<?> model,
            ResourceLocation texture, int light, float ticks) {
        if (!isReady()) return;
        RenderSystem.assertOnRenderThread();
        RenderType type = TYPES.computeIfAbsent(texture, VibraniumCosmicShaders::createType);
        MultiBufferSource.BufferSource output = caller instanceof MultiBufferSource.BufferSource source
                ? source : fallbackBuffers();
        // Finish any preceding pass before replacing its shared program uniforms.
        output.endBatch(type);
        float wrappedTicks = ticks % PERIOD_TICKS;
        if (wrappedTicks < 0) wrappedTicks += PERIOD_TICKS;
        cosmicTime.set(wrappedTicks / 20F);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        cosmicYaw.set((float)Math.toRadians(camera.getYRot()));
        cosmicPitch.set((float)Math.toRadians(camera.getXRot()));
        model.renderToBuffer(pose, output.getBuffer(type), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        // BufferSource normally batches entities. This pass must use this entity's clock and camera.
        output.endBatch(type);
    }

    private static MultiBufferSource.BufferSource fallbackBuffers() {
        if (fallbackBuffers == null) {
            fallbackStorage = new ByteBufferBuilder(BUFFER_BYTES);
            fallbackBuffers = MultiBufferSource.immediate(fallbackStorage);
        }
        return fallbackBuffers;
    }

    private static RenderType createType(ResourceLocation texture) {
        return RenderType.create("modern_ite_vibranium_cosmic", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }
}
