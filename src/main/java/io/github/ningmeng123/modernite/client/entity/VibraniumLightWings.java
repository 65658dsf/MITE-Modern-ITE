package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Eight stepped light blades mounted to the wearer's final back pose. @author NingMeng123 */
public final class VibraniumLightWings {
    public static final int PERIOD_TICKS = 400;
    public static final int FLOW_PERIOD_TICKS = 80;
    public static final float MAX_HALF_SPAN = 23;
    public static final float MIN_MODEL_Y = -7;
    public static final float MAX_MODEL_Y = 18;
    public static final float BACK_ANCHOR_Z = 4.3F;
    private static final float PIXEL = 1F / 16;
    private static final float TAU = (float)(Math.PI * 2);
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(TEXTURE, false);
    // Vanilla's entity-emissive shader still applies directional lighting. The beacon shader does
    // not, and its translucent variant keeps depth testing without writing a solid wing silhouette.
    // Snow supplies a near-white native texel instead of concrete's approximately 83% gray base.
    private static final ResourceLocation CORE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/snow.png");
    private static final RenderType CORE_TYPE = RenderType.beaconBeam(CORE_TEXTURE, true);
    private static final int LEMON = 0xFFFFEF48;
    private static final int LIME = 0xFF98FF35;
    private static final int EMERALD = 0xFF20CF72;
    private static final int CYAN = 0xFF28DEDD;
    private static final Blade[] BLADES = {
            new Blade(4.2F, 1.6F, 10, -5.3F, 2.15F, 8, 4.85F, .13F),
            new Blade(5.4F, 4.2F, 22.4F, -.9F, 3.55F, 14, 4.45F, .37F),
            new Blade(5.4F, 8.2F, 21.4F, 13.2F, 3.5F, 13, 4.65F, .61F),
            new Blade(4.1F, 10.4F, 10, 17.1F, 2.2F, 8, 4.9F, .83F)
    };

    private VibraniumLightWings() {}

    /** The calling armor layer owns the complete-vibranium/protection-V condition. */
    public static void render(PoseStack pose, MultiBufferSource buffers, HumanoidModel<?> model,
            float ticks, int light) {
        if (!model.body.visible) return;
        float cycle = Mth.positiveModulo(ticks, PERIOD_TICKS) / PERIOD_TICKS;
        float phase = cycle * TAU;
        float animationTicks = cycle * PERIOD_TICKS;
        pose.pushPose();
        try {
            // This is exactly the body branch of vanilla AgeableListModel, not the head scale.
            if (model.young) {
                pose.scale(.5F, .5F, .5F);
                pose.translate(0, 1.5, 0);
            }
            model.body.translateAndRotate(pose);
            // Complete each buffer before requesting the next type: BufferSource may flush its
            // previous non-fixed builder when getBuffer switches between shell and light passes.
            for (boolean luminousPass : new boolean[]{false, true}) {
                var geometry = new Geometry(buffers.getBuffer(luminousPass ? CORE_TYPE : TYPE),
                        pose.last(), light, luminousPass);
                mount(geometry, animationTicks);
                for (int side : new int[]{-1, 1}) {
                    pose.pushPose();
                    try {
                        // A slight mirrored fold and breath keep the paired blades behind the wearer.
                        pose.translate(side * 2.8F * PIXEL, 6 * PIXEL, BACK_ANCHOR_Z * PIXEL);
                        pose.mulPose(Axis.YP.rotationDegrees(-side * (4.5F + Mth.sin(phase * 2) * .35F)));
                        pose.mulPose(Axis.ZP.rotationDegrees(side * Mth.sin(phase * 2) * .2F));
                        pose.translate(-side * 2.8F * PIXEL, -6 * PIXEL, -BACK_ANCHOR_Z * PIXEL);
                        geometry.pose = pose.last();
                        for (Blade blade : BLADES) blade(geometry, side, blade, animationTicks);
                    } finally {
                        pose.popPose();
                    }
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private static void mount(Geometry geometry, float ticks) {
        geometry.box(-1.7F, 2.6F, 3.08F, 3.4F, 7, 1.15F, 0xF31B3039, false);
        geometry.box(-1.22F, 3.0F, 4.24F, 2.44F, 6.2F, .13F, 0xE50B2027, false);
        int rail = sampleFlowColor(0, ticks);
        for (int side : new int[]{-1, 1}) {
            geometry.box(side < 0 ? -1.42F : 1.19F, 3, 4.39F, .23F, 6.2F, .1F, rail, true);
            // The short root spine touches the central mount and all four attachment bars.
            geometry.box(side < 0 ? -3.05F : 2.35F, 1.1F, 3.55F, .7F, 10.0F, .8F, 0xD32D5555, false);
            for (Blade blade : BLADES) {
                float length = blade.rootX - 1.4F;
                float start = side < 0 ? -blade.rootX : 1.4F;
                geometry.box(start, blade.rootY - .48F, 3.8F, length, .96F, .67F, 0xDE37514B, false);
                geometry.box(start + .12F, blade.rootY - .19F, 4.48F,
                        Math.max(.2F, length - .24F), .38F, .13F, rail, true);
            }
        }
    }

    private static void blade(Geometry geometry, int side, Blade blade, float ticks) {
        float columnWidth = (blade.tipX - blade.rootX) / blade.steps;
        for (int index = 0; index < blade.steps; index++) {
            float t = (index + .5F) / blade.steps;
            float x = blade.rootX + index * columnWidth;
            float center = pixelStep(Mth.lerp(t, blade.rootY, blade.tipY));
            float height = Math.max(.52F, pixelStep(Mth.lerp(t, blade.rootWidth, .45F)));
            int rootColor = sampleFlowColor((float)index / blade.steps, ticks);
            int tipColor = sampleFlowColor((float)(index + 1) / blade.steps, ticks);
            // Mirrored geometry reverses its x endpoints, but the color always travels root to tip.
            int leftColor = side < 0 ? tipColor : rootColor;
            int rightColor = side < 0 ? rootColor : tipColor;
            float start = side < 0 ? -x - columnWidth : x;
            // Broad translucent teal/gold segments surround a luminous, narrower blade core.
            geometry.gradientBox(start, center - height / 2, blade.z, columnWidth + .025F,
                    height, .34F, shellColor(leftColor), shellColor(rightColor), false);
            float coreHeight = Math.max(.4F, height * .78F);
            geometry.gradientBox(start + .035F, center - coreHeight / 2, blade.z - .045F,
                    Math.max(.25F, columnWidth - .045F), coreHeight, .43F,
                    leftColor, rightColor, true);
        }
        movingFleck(geometry, side, blade, ticks);
    }

    /** A full hue-and-brightness band travels the complete blade in four seconds. */
    public static int sampleFlowColor(float normalizedDistance, float ticks) {
        float wave = Mth.positiveModulo(normalizedDistance - ticks / FLOW_PERIOD_TICKS, 1);
        float sector = wave * 4;
        int color;
        if (sector < 1) color = mix(LEMON, LIME, sector);
        else if (sector < 2) color = mix(LIME, EMERALD, sector - 1);
        else if (sector < 3) color = mix(EMERALD, CYAN, sector - 2);
        else color = mix(CYAN, LEMON, sector - 3);
        float distance = Math.min(wave, 1 - wave);
        float pulse = Mth.clamp(1 - distance / .16F, 0, 1);
        pulse = pulse * pulse * (3 - 2 * pulse);
        // Keep the moving lemon crest saturated; large white mixes wash the whole wing to milk.
        color = mix(color, 0xFFFFFFA2, pulse * .13F);
        return withAlpha(color, 218 + Math.round(pulse * 37));
    }

    private static int shellColor(int core) {
        return withAlpha(mix(0xFF204B51, core, .32F), 72 + ((core >>> 24) - 218) / 2);
    }

    private static void movingFleck(Geometry geometry, int side, Blade blade, float ticks) {
        float t = Mth.positiveModulo(ticks / FLOW_PERIOD_TICKS + blade.phase, 1);
        float x = side * Mth.lerp(t, blade.rootX, blade.tipX);
        int column = Math.min(blade.steps - 1, (int)(t * blade.steps));
        float center = pixelStep(Mth.lerp((column + .5F) / blade.steps, blade.rootY, blade.tipY));
        float size = Math.min(.46F, Mth.lerp(t, blade.rootWidth, .45F) * .58F);
        float fade = Mth.clamp(Math.min(t, 1 - t) / .075F, 0, 1);
        int color = withAlpha(mix(sampleFlowColor(t, ticks), 0xFFFFFFAB, .55F), Math.round(fade * 245));
        // These little pixel crosses physically slide over the blade; they are not stationary
        // particles whose opacity merely oscillates. The faded endpoints hide the wrap to the root.
        geometry.box(x - size / 2, center - size / 2, blade.z - .11F,
                size, size, .65F, color, true);
        geometry.box(x - size * .85F, center - size * .17F, blade.z - .12F,
                size * 1.7F, size * .34F, .67F, withAlpha(color, Math.round(fade * 185)), true);
        geometry.box(x - size * .17F, center - size * .85F, blade.z - .12F,
                size * .34F, size * 1.7F, .67F, withAlpha(color, Math.round(fade * 185)), true);
    }

    private static float pixelStep(float value) {
        return Math.round(value * 2) * .5F;
    }

    private static int mix(int first, int second, float amount) {
        amount = Mth.clamp(amount, 0, 1);
        int r = Math.round(Mth.lerp(amount, first >>> 16 & 255, second >>> 16 & 255));
        int g = Math.round(Mth.lerp(amount, first >>> 8 & 255, second >>> 8 & 255));
        int b = Math.round(Mth.lerp(amount, first & 255, second & 255));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0xFFFFFF | Mth.clamp(alpha, 0, 255) << 24;
    }

    private record Blade(float rootX, float rootY, float tipX, float tipY,
            float rootWidth, int steps, float z, float phase) {}

    /** One reusable transformed position/normal avoids allocating vectors for every emitted vertex. */
    private static final class Geometry {
        private final VertexConsumer vertices;
        private final int light;
        private final boolean luminousPass;
        private final Vector3f position = new Vector3f();
        private final Vector3f normal = new Vector3f();
        private PoseStack.Pose pose;
        private int leftColor;
        private int rightColor;
        private float minX;
        private float faceShade;
        private int packedLight;

        Geometry(VertexConsumer vertices, PoseStack.Pose pose, int light, boolean luminousPass) {
            this.vertices = vertices;
            this.pose = pose;
            this.light = light;
            this.luminousPass = luminousPass;
        }

        void box(float x, float y, float z, float width, float height, float depth, int tint, boolean luminous) {
            gradientBox(x, y, z, width, height, depth, tint, tint, luminous);
        }

        void gradientBox(float x, float y, float z, float width, float height, float depth,
                int leftTint, int rightTint, boolean luminous) {
            if (luminous != luminousPass) return;
            packedLight = luminous ? LightTexture.FULL_BRIGHT : light;
            float x1 = x + width, y1 = y + height, z1 = z + depth;
            leftColor = leftTint;
            rightColor = rightTint;
            minX = x;
            faceShade = 1;
            face(0, 0, -1, x1, y, z, x, y, z, x, y1, z, x1, y1, z);
            face(0, 0, 1, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1);
            faceShade = luminous ? .98F : .94F;
            face(0, -1, 0, x, y, z, x1, y, z, x1, y, z1, x, y, z1);
            faceShade = luminous ? .90F : .68F;
            face(0, 1, 0, x, y1, z1, x1, y1, z1, x1, y1, z, x, y1, z);
            faceShade = luminous ? .94F : .82F;
            face(-1, 0, 0, x, y, z1, x, y1, z1, x, y1, z, x, y, z);
            face(1, 0, 0, x1, y, z, x1, y1, z, x1, y1, z1, x1, y, z1);
        }

        private void face(float nx, float ny, float nz, float ax, float ay, float az,
                float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
            pose.transformNormal(nx, ny, nz, normal);
            vertex(ax, ay, az, .25F, .25F);
            vertex(bx, by, bz, .75F, .25F);
            vertex(cx, cy, cz, .75F, .75F);
            vertex(dx, dy, dz, .25F, .75F);
        }

        private void vertex(float x, float y, float z, float u, float v) {
            pose.pose().transformPosition(x * PIXEL, y * PIXEL, z * PIXEL, position);
            int color = shade(x <= minX ? leftColor : rightColor, faceShade);
            vertices.addVertex(position.x, position.y, position.z).setColor(color).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
                    .setNormal(normal.x, normal.y, normal.z);
        }

        private static int shade(int color, float factor) {
            int r = Math.round((color >>> 16 & 255) * factor);
            int g = Math.round((color >>> 8 & 255) * factor);
            int b = Math.round((color & 255) * factor);
            return color & 0xFF000000 | r << 16 | g << 8 | b;
        }
    }
}
