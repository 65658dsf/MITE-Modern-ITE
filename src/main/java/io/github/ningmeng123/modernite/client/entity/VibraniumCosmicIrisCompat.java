package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Iris-compatible animated starlight using its supported emissive entity pipeline. @author NingMeng123 */
public final class VibraniumCosmicIrisCompat {
    private static final Map<ResourceLocation, StarTexture> TEXTURES = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static final IrisAccess IRIS = IrisAccess.find();
    private static long renderedPasses;
    private static int currentFrame = -1;
    private static String lastShaderClass = "not_drawn";

    private VibraniumCosmicIrisCompat() {}

    public static boolean activeShaderPack() {
        return IRIS != null && IRIS.flag(IRIS.packInUse);
    }

    public static long renderedPasses() { return renderedPasses; }
    public static int registeredTextures() { return TEXTURES.size(); }
    public static int currentFrame() { return currentFrame; }
    public static String lastShaderClass() { return lastShaderClass; }

    /** Return true when this shader-pack path handled the pass, including a skipped shadow pass. */
    static boolean render(PoseStack pose, MultiBufferSource output, VibraniumCosmicModel<?> model,
            ResourceLocation armorTexture) {
        if (!activeShaderPack()) return false;
        RenderSystem.assertOnRenderThread();
        // Stars are a surface highlight, never an additional shadow-casting armor mesh.
        if (IRIS.flag(IRIS.shadowPass)) return true;
        StarTexture texture = TEXTURES.computeIfAbsent(armorTexture, StarTexture::new);
        var level = Minecraft.getInstance().level;
        // One shared world clock keeps batched entities stable; no per-entity shader uniforms or flushes.
        currentFrame = (int)Math.floorMod(level == null ? 0L : level.getGameTime(), VibraniumCosmicShaders.PERIOD_TICKS) / 2;
        texture.update(currentFrame);
        model.renderToBuffer(pose, output.getBuffer(TYPES.computeIfAbsent(texture.id, VibraniumCosmicIrisCompat::createType)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        renderedPasses++;
        return true;
    }

    static void reload() {
        RenderSystem.assertOnRenderThread();
        var manager = Minecraft.getInstance().getTextureManager();
        TEXTURES.values().forEach(texture -> manager.release(texture.id));
        TEXTURES.clear();
        TYPES.clear();
        currentFrame = -1;
        lastShaderClass = "not_drawn";
    }

    private static ShaderInstance emissiveShader() {
        ShaderInstance shader = GameRenderer.getRendertypeEntityTranslucentEmissiveShader();
        lastShaderClass = shader == null ? "null" : shader.getClass().getName();
        return shader;
    }

    private static RenderType createType(ResourceLocation texture) {
        // Use the supported native shader getter, retaining the same surface bias as the original layer.
        return RenderType.create("entity_translucent_emissive", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, true, true, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(VibraniumCosmicIrisCompat::emissiveShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    }

    private static final class StarTexture {
        private final int width, height;
        private final int[] armorMask;
        private final DynamicTexture texture;
        private final ResourceLocation id;
        private int frame = -1;

        private StarTexture(ResourceLocation original) {
            var mc = Minecraft.getInstance();
            try (var stream = mc.getResourceManager().open(original); NativeImage armor = NativeImage.read(stream)) {
                // Four subpixels per native armor texel give compact stars without changing its cutouts.
                width = Math.min(512, armor.getWidth() * 4);
                height = Math.min(256, armor.getHeight() * 4);
                armorMask = new int[width * height];
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                    armorMask[y * width + x] = armor.getPixelRGBA(x * armor.getWidth() / width, y * armor.getHeight() / height);
                texture = new DynamicTexture(width, height, true);
                id = ResourceLocation.fromNamespaceAndPath("modern_ite", "dynamic/cosmic/" + original.getPath());
                mc.getTextureManager().register(id, texture);
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot read the actual vibranium armor alpha mask " + original, failure);
            }
        }

        private void update(int nextFrame) {
            if (frame == nextFrame) return;
            var pixels = texture.getPixels();
            if (pixels == null) return;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels.setPixelRGBA(x, y, 0);
            double phase = nextFrame * (Math.PI * 2D / (VibraniumCosmicShaders.PERIOD_TICKS / 2));
            int salt = id.getPath().hashCode();
            for (int y = 0; y < height / 8; y++) for (int x = 0; x < width / 8; x++) {
                int seed = VibraniumStarlightPixels.seed(x, y, salt);
                if ((seed & 255) < 110) continue;
                double twinkle = .65D + .35D * Math.sin(phase * 2D + (seed >>> 8 & 255) * .07D);
                int centerX = Math.floorMod(x * 8 + 2 + (seed >>> 16 & 3) + (int)Math.round(Math.sin(phase + y * .7D) * 2D), width);
                int centerY = Math.floorMod(y * 8 + 2 + (seed >>> 20 & 3) + (int)Math.round(Math.cos(phase + x * .5D) * 2D), height);
                boolean cross = (seed & 7) == 0;
                for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
                    int distance = Math.abs(dx) + Math.abs(dy);
                    if (distance > (cross ? 2 : 1) || (distance == 2 && dx != 0 && dy != 0)) continue;
                    int px = Math.floorMod(centerX + dx, width), py = Math.floorMod(centerY + dy, height);
                    double strength = twinkle * (distance == 0 ? .78D : distance == 1 ? .32D : .18D);
                    int color = VibraniumStarlightPixels.star(armorMask[py * width + px], strength);
                    if ((color >>> 24) > (pixels.getPixelRGBA(px, py) >>> 24)) pixels.setPixelRGBA(px, py, color);
                }
            }
            texture.upload();
            frame = nextFrame;
        }
    }

    /** Public Iris API is loaded lazily by name; Iris remains an optional client dependency. */
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
