package io.github.ningmeng123.modernite.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** Equipped gems form a small animated star medal on the chest. @author NingMeng123 */
public final class ChestGemLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public static final float STAR_SIZE = .07F;
    public static final float FRONT_Z = -3.14F / 16F;
    public static final ResourceLocation STAR_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    public static final int GLOW_VERTICES = 112;
    public static final int STAR_VERTICES = 80 + GLOW_VERTICES;
    public static final int STARS_PER_ROW = 5;
    public static final int MAX_STARS = 25;
    public static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static final List<EquipmentSlot> SOURCE_SLOTS = List.of(
            EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND);
    private static final float[][] STAR_POINTS = starPoints();
    private static final float[][] SPARKLE_POINTS = sparklePoints();
    private static final Field[] BABY_FIELDS = babyFields();
    private static final Map<HumanoidModel<?>, BabyTransform> BABY_TRANSFORMS = new WeakHashMap<>();

    public ChestGemLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** Preserve socket order and duplicate gems; visuals describe the stored items, not only active effects. */
    public record SocketGem(int slot, int code, GemType type, int rank) {}

    private record BabyTransform(float bodyScale, float bodyY) {}

    public static List<SocketGem> visibleSockets(ItemStack equipment) {
        if (equipment.isEmpty() || equipment.getItem() instanceof AnimalArmorItem
                || !(equipment.getItem() instanceof ArmorItem) && !ExtremeGear.socketable(equipment)) return List.of();
        var sockets = ExtremeGear.sockets(equipment).values();
        List<SocketGem> visible = new ArrayList<>(8);
        for (int slot = 0; slot < sockets.size(); slot++) {
            int code = sockets.get(slot);
            if (code != 0) visible.add(new SocketGem(slot, code,
                    GemType.values()[(code - 1) / 6], (code - 1) % 6 + 1));
        }
        return List.copyOf(visible);
    }

    /** Armor must be worn in its own slot; only the current main-hand weapon or tool contributes. */
    public static List<SocketGem> visibleSockets(ItemStack equipment, EquipmentSlot equippedSlot) {
        if (equipment.getItem() instanceof ArmorItem armor)
            return ARMOR_SLOTS.contains(equippedSlot) && armor.getEquipmentSlot() == equippedSlot
                    ? visibleSockets(equipment) : List.of();
        return equippedSlot == EquipmentSlot.MAINHAND ? visibleSockets(equipment) : List.of();
    }

    /** Read all five equipped sources without merging duplicate gems or discarding inactive old sockets. */
    public static List<SocketGem> visibleSockets(LivingEntity entity) {
        List<SocketGem> gems = new ArrayList<>(40);
        for (EquipmentSlot slot : SOURCE_SLOTS) gems.addAll(visibleSockets(entity.getItemBySlot(slot), slot));
        return List.copyOf(gems);
    }

    /** Stable sampling without replacement prevents more than 25 gems from flickering between frames. */
    public static List<SocketGem> selectSockets(List<SocketGem> sockets, long seed) {
        if (sockets.size() <= MAX_STARS) return sockets;
        for (SocketGem gem : sockets) seed = 31L * seed + gem.code() * 8L + gem.slot();
        Random random = new Random(seed);
        int[] selected = new int[MAX_STARS];
        for (int index = 0; index < MAX_STARS; index++) selected[index] = index;
        for (int index = MAX_STARS; index < sockets.size(); index++) {
            int replacement = random.nextInt(index + 1);
            if (replacement < MAX_STARS) selected[replacement] = index;
        }
        Arrays.sort(selected);
        List<SocketGem> visible = new ArrayList<>(MAX_STARS);
        for (int index : selected) visible.add(sockets.get(index));
        return List.copyOf(visible);
    }

    /** A compact five-by-five medal; a partially filled row remains centered. */
    public static float socketX(int index, int count) {
        int rowSize = Math.min(STARS_PER_ROW, count - index / STARS_PER_ROW * STARS_PER_ROW);
        return (index % STARS_PER_ROW - (rowSize - 1) * .5F) * 1.4F;
    }

    public static float socketY(int index, int count) { return 2.9F + index / STARS_PER_ROW * 1.5F; }

    private static Field[] babyFields() {
        try {
            String[] names = {"babyBodyScale", "bodyYOffset"};
            Field[] fields = new Field[names.length];
            for (int i = 0; i < fields.length; i++) {
                fields[i] = AgeableListModel.class.getDeclaredField(names[i]);
                if (!fields[i].trySetAccessible()) return new Field[0];
            }
            return fields;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return new Field[0];
        }
    }

    private static BabyTransform babyTransform(HumanoidModel<?> model) {
        return BABY_TRANSFORMS.computeIfAbsent(model, ignored -> {
            try {
                if (BABY_FIELDS.length == 2) return new BabyTransform(
                        1F / BABY_FIELDS[0].getFloat(model), BABY_FIELDS[1].getFloat(model) / 16F);
            } catch (IllegalAccessException | RuntimeException unavailable) {
                // Standard humanoid values remain a safe fallback for inaccessible third-party models.
            }
            return new BabyTransform(.5F, 1.5F);
        });
    }

    private static void youngTransform(PoseStack pose, HumanoidModel<?> parent) {
        if (!parent.young) return;
        BabyTransform baby = babyTransform(parent);
        pose.scale(baby.bodyScale(), baby.bodyScale(), baby.bodyScale());
        pose.translate(0, baby.bodyY(), 0);
    }

    /** One stable color per gem family; ranks never change the star's silhouette or color. */
    public static int starColor(GemType type) {
        return switch (type) {
            case DAMAGE -> 0xD8D4C8;
            case HEALTH -> 0xDE4352;
            case PROTECTION -> 0xA15CDC;
            case RECOVER -> 0x528CE1;
            case EFFICIENCY -> 0x4CC59E;
            case DURABILITY -> 0x687E93;
            case SPEED -> 0xDFBB43;
            case FIREPROOF -> 0x7DC3DB;
            case EXPLOSION_PROOF -> 0xA6CC4D;
            case NUTRITION -> 0xE48643;
        };
    }

    private static float[][] starPoints() {
        float[][] points = new float[10][2];
        for (int i = 0; i < points.length; i++) {
            double angle = i * Math.PI / 5D;
            float radius = (i % 2 == 0 ? .5F : .22F) * STAR_SIZE;
            points[i][0] = (float)Math.sin(angle) * radius;
            points[i][1] = -(float)Math.cos(angle) * radius;
        }
        return points;
    }

    private static float[][] sparklePoints() {
        float[][] points = new float[8][2];
        for (int i = 0; i < points.length; i++) {
            double angle = i * Math.PI / 4D;
            float radius = STAR_SIZE * (i % 2 == 0 ? .32F : .055F);
            points[i][0] = STAR_SIZE * .18F + (float)Math.sin(angle) * radius;
            points[i][1] = -STAR_SIZE * .22F - (float)Math.cos(angle) * radius;
        }
        return points;
    }

    /** A slow three-second shimmer, with a small phase offset between neighboring stars. */
    public static float glowPulse(float ticks, int index) {
        return .5F + .5F * (float)Math.cos((ticks + index * 4F) * Math.PI * 2D / 60D);
    }

    /** A narrow band of light travels diagonally across the stationary medal once every four seconds. */
    public static float lightSweep(float ticks, int index) {
        float phase = (ticks % 80F + 80F) % 80F / 80F;
        float distance = index % STARS_PER_ROW + index / STARS_PER_ROW * .65F - (phase * 10F - 1.5F);
        return (float)Math.exp(-distance * distance / .45F);
    }

    private static void star(PoseStack.Pose pose, VertexConsumer vertices, int color, int light,
                             float pulse, float sweep) {
        starFace(pose, vertices, color, light, 1F, 0F, true, Math.round(50F + 8F * pulse));
        // Both the rim and face are translucent, so the underlying chest material remains visible.
        starFace(pose, vertices, color, light, .9F, -.0005F, false,
                Math.round(120F + 18F * pulse + 18F * sweep));
    }

    private static void starFace(PoseStack.Pose pose, VertexConsumer vertices, int color, int light,
                                 float scale, float z, boolean rim, int alpha) {
        for (int i = 0; i < STAR_POINTS.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            float edgeShade = rim ? .55F : i < 5 ? 1F : .94F;
            vertex(pose, vertices, 0F, 0F, z, color, rim ? .55F : 1.1F, light, alpha);
            vertex(pose, vertices, a[0] * scale, a[1] * scale, z, color, edgeShade, light, alpha);
            vertex(pose, vertices, b[0] * scale, b[1] * scale, z, color, edgeShade, light, alpha);
            // Native entity buffers consume quads; a repeated corner makes this a single triangle.
            vertex(pose, vertices, b[0] * scale, b[1] * scale, z, color, edgeShade, light, alpha);
        }
    }

    private static void glow(PoseStack.Pose pose, VertexConsumer vertices, int color,
                             float pulse, float sweep) {
        int strength = Math.round(24F + 16F * pulse + 20F * sweep);
        int edge = Math.round(strength * .45F);
        for (int i = 0; i < STAR_POINTS.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            // A faint emissive center keeps the color readable in shade without turning white.
            vertex(pose, vertices, 0F, 0F, -.001F, color, 1.1F, LightTexture.FULL_BRIGHT, strength);
            vertex(pose, vertices, a[0], a[1], -.001F, color, 1F, LightTexture.FULL_BRIGHT, edge);
            vertex(pose, vertices, b[0], b[1], -.001F, color, 1F, LightTexture.FULL_BRIGHT, edge);
            vertex(pose, vertices, b[0], b[1], -.001F, color, 1F, LightTexture.FULL_BRIGHT, edge);
            // Fade smoothly to zero around the star; no particles or opaque outer silhouette.
            vertex(pose, vertices, a[0], a[1], -.001F, color, 1F, LightTexture.FULL_BRIGHT, edge);
            vertex(pose, vertices, a[0] * 1.65F, a[1] * 1.65F, -.001F, color, 1F, LightTexture.FULL_BRIGHT, 0);
            vertex(pose, vertices, b[0] * 1.65F, b[1] * 1.65F, -.001F, color, 1F, LightTexture.FULL_BRIGHT, 0);
            vertex(pose, vertices, b[0], b[1], -.001F, color, 1F, LightTexture.FULL_BRIGHT, edge);
        }
        sparkle(pose, vertices, color, sweep * sweep);
    }

    /** A tiny four-point glint appears at the upper tip as the light band crosses a star. */
    private static void sparkle(PoseStack.Pose pose, VertexConsumer vertices, int color, float strength) {
        int highlight = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int channel = color >> shift & 255;
            highlight |= Math.round(channel + (255 - channel) * .65F) << shift;
        }
        float cx = STAR_SIZE * .18F, cy = -STAR_SIZE * .22F;
        for (int i = 0; i < SPARKLE_POINTS.length; i++) {
            float[] a = SPARKLE_POINTS[i], b = SPARKLE_POINTS[(i + 1) % SPARKLE_POINTS.length];
            vertex(pose, vertices, cx, cy, -.0015F, highlight, 1F, LightTexture.FULL_BRIGHT, Math.round(128F * strength));
            vertex(pose, vertices, a[0], a[1], -.0015F, highlight, 1F, LightTexture.FULL_BRIGHT, 0);
            vertex(pose, vertices, b[0], b[1], -.0015F, highlight, 1F, LightTexture.FULL_BRIGHT, 0);
            vertex(pose, vertices, b[0], b[1], -.0015F, highlight, 1F, LightTexture.FULL_BRIGHT, 0);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
                               int color, float shade, int light, int alpha) {
        vertices.addVertex(pose, x, y, z)
                .setColor(Math.min(255, Math.round((color >> 16 & 255) * shade)),
                        Math.min(255, Math.round((color >> 8 & 255) * shade)),
                        Math.min(255, Math.round((color & 255) * shade)), alpha)
                .setUv(.5F, .5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0F, 0F, -1F);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float age,
                       float headYaw, float headPitch) {
        M parent = getParentModel();
        if (!parent.body.visible) return;
        List<SocketGem> allSockets = visibleSockets(entity);
        if (allSockets.isEmpty()) return;
        float ticks = entity.tickCount + partialTick;
        List<SocketGem> sockets = selectSockets(allSockets,
                entity.getUUID().getMostSignificantBits() ^ entity.getUUID().getLeastSignificantBits());
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        float front = chest.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST
                ? FRONT_Z : -2.3F / 16F;
        pose.pushPose();
        try {
            youngTransform(pose, parent);
            parent.body.translateAndRotate(pose);
            // Switching a non-fixed render type closes its buffer: finish the base stars first.
            renderPass(pose, buffers.getBuffer(RenderType.entityTranslucent(STAR_TEXTURE, false)),
                    light, sockets, ticks, front, false);
            renderPass(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(STAR_TEXTURE, false)),
                    light, sockets, ticks, front, true);
        } finally {
            pose.popPose();
        }
    }

    private static void renderPass(PoseStack pose, VertexConsumer vertices, int light,
                                   List<SocketGem> sockets, float ticks, float front, boolean glowing) {
        for (int index = 0; index < sockets.size(); index++) {
            pose.pushPose();
            try {
                pose.translate(socketX(index, sockets.size()) / 16F, socketY(index, sockets.size()) / 16F, front);
                int color = starColor(sockets.get(index).type());
                float pulse = glowPulse(ticks, index);
                float sweep = lightSweep(ticks, index);
                if (glowing) glow(pose.last(), vertices, color, pulse, sweep);
                else star(pose.last(), vertices, color, light, pulse, sweep);
            } finally {
                pose.popPose();
            }
        }
    }
}
