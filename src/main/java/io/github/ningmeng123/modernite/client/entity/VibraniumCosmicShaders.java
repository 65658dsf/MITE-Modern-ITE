package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.ArmorAuraRules;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Reloadable, immediately drawn cosmic armor passes with a deterministic clock. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class VibraniumCosmicShaders {
    public static final int PERIOD_TICKS = 400;
    private static final int MAX_DEFERRED_GLINT = 8;
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "vibranium_cosmic");
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static final Map<LivingEntity, DeferredGlint> DEFERRED_GLINT = new IdentityHashMap<>();
    private static final Deque<DeferredGlint> GLINT_POOL = new ArrayDeque<>();
    private static ShaderInstance shader;
    private static Uniform cosmicTime;
    private static Uniform cosmicYaw;
    private static Uniform cosmicPitch;

    private VibraniumCosmicShaders() {}

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        VibraniumCosmicIrisCompat.reload();
        DEFERRED_GLINT.values().forEach(DeferredGlint::close);
        DEFERRED_GLINT.clear();
        GLINT_POOL.forEach(DeferredGlint::close);
        GLINT_POOL.clear();
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
        if (VibraniumCosmicIrisCompat.render(pose, caller, model, texture)) return;
        if (!isReady()) return;
        RenderSystem.assertOnRenderThread();
        RenderType type = TYPES.computeIfAbsent(texture, VibraniumCosmicShaders::createType);
        MultiBufferSource.BufferSource output = outputBuffers(caller);
        if (output == null) return;
        // Native armor and trims must be drawn before transparent starlight is blended over them.
        // Flushing only our own type would leave the underlying material in a pending buffer.
        output.endBatch();
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

    private static MultiBufferSource.BufferSource outputBuffers(MultiBufferSource caller) {
        var renderBuffers = Minecraft.getInstance().renderBuffers();
        if (caller instanceof MultiBufferSource.BufferSource source) return source;
        // The world's outline wrapper shares this exact parent buffer. Original armor still
        // supplies its outline; starlight must be blended after its normal material pass.
        if (caller == renderBuffers.outlineBufferSource()) return renderBuffers.bufferSource();
        return null; // Unknown wrappers retain the complete, unmodified native armor path.
    }

    /** Includes growth-affix protection V even when ItemStack.hasFoil() is false. */
    private static boolean shouldDeferGlint(LivingEntity entity, MultiBufferSource caller,
            HumanoidArmorLayer<?, ?, ?> armorLayer) {
        return !VibraniumCosmicIrisCompat.activeShaderPack()
                && outputBuffers(caller) != null && ArmorAuraRules.fullProtectionFive(entity)
                && VibraniumGildingLayer.followsArmorLayer(entity, armorLayer);
    }

    /** Capture the actual armor model and pose while each native equipment slot is configured. */
    public static boolean deferGlint(LivingEntity entity, PoseStack pose, MultiBufferSource caller,
            Model model, int light, HumanoidArmorLayer<?, ?, ?> armorLayer) {
        if (!shouldDeferGlint(entity, caller, armorLayer)) return false;
        RenderSystem.assertOnRenderThread();
        DeferredGlint deferred = DEFERRED_GLINT.get(entity);
        if (deferred == null) {
            if (DEFERRED_GLINT.size() >= MAX_DEFERRED_GLINT) return false;
            deferred = GLINT_POOL.pollFirst();
            if (deferred == null) deferred = new DeferredGlint();
            DEFERRED_GLINT.put(entity, deferred);
        }
        // getArmorFoilBuffer uses this exact render type. Submitting the native model here also
        // preserves its exact depth, per-slot visibility and any NeoForge armor-model hook.
        // The 1.01/.51 starlight mesh cannot be substituted: vanilla glint uses depth EQUAL.
        model.renderToBuffer(pose, deferred.buffers.getBuffer(RenderType.armorEntityGlint()),
                light, OverlayTexture.NO_OVERLAY);
        return true;
    }

    /** Draw one native purple glint after both transparent starlight passes, without a second copy. */
    public static void renderDeferredGlint(LivingEntity entity, MultiBufferSource caller) {
        DeferredGlint deferred = DEFERRED_GLINT.remove(entity);
        if (deferred == null) return;
        MultiBufferSource.BufferSource output = outputBuffers(caller);
        if (output == null) {
            deferred.close();
            return;
        }
        boolean completed = false;
        try {
            // Also correct if the optional starlight shader has not loaded: native depth and trims
            // still need to be present before the unchanged EQUAL-depth armor glint is drawn.
            output.endBatch();
            deferred.buffers.endBatch(RenderType.armorEntityGlint());
            completed = true;
        } finally {
            if (completed && GLINT_POOL.size() < MAX_DEFERRED_GLINT) GLINT_POOL.addLast(deferred);
            else deferred.close();
        }
    }

    /** A third-party renderer cannot leave a captured entity alive beyond its rendered frame. */
    @EventBusSubscriber(modid=ModernIte.MOD_ID, value=Dist.CLIENT)
    public static final class GlintLifecycle {
        private GlintLifecycle() {}

        @SubscribeEvent public static void afterLiving(RenderLivingEvent.Post<?, ?> event) {
            // Normally the starlight layer has already consumed this entry. If another layer
            // changed the pipeline during rendering, finish the original glint at the same target.
            renderDeferredGlint(event.getEntity(), event.getMultiBufferSource());
        }

        @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event) {
            // No delayed draw at an unrelated target: discard interrupted work and release all
            // entity references. Both pending work and the reusable pool also have hard bounds.
            DEFERRED_GLINT.values().forEach(DeferredGlint::close);
            DEFERRED_GLINT.clear();
        }
    }

    private static final class DeferredGlint implements AutoCloseable {
        private final ByteBufferBuilder storage = new ByteBufferBuilder(16 * 1024);
        private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(storage);

        @Override public void close() {
            storage.close();
        }
    }

    private static RenderType createType(ResourceLocation texture) {
        return RenderType.create("modern_ite_vibranium_cosmic", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, true, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }
}
