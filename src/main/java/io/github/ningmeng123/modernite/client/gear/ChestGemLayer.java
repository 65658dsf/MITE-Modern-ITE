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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** Equipped gems form translucent, beveled glass hexagrams on the chest. @author NingMeng123 */
public final class ChestGemLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public static final float STAR_SIZE = .07F;
    public static final float FRONT_Z = -3.14F / 16F;
    public static final ResourceLocation STAR_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    public static final int BASE_VERTICES = 192;
    public static final int HALO_VERTICES = 96;
    public static final int FLASH_VERTICES = 64;
    public static final int GLOW_VERTICES = HALO_VERTICES + FLASH_VERTICES;
    public static final int STAR_VERTICES = BASE_VERTICES + GLOW_VERTICES;
    public static final int STARS_PER_ROW = 5;
    public static final int MAX_STARS = 25;
    public static final float SIDE_FRONT_Z = -.0035F;
    public static final float BEVEL_FRONT_Z = -.0065F;
    public static final float FACE_PEAK_Z = -.008F;
    public static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static final List<EquipmentSlot> SOURCE_SLOTS = List.of(
            EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND);
    private static final float[][] STAR_POINTS = starPoints();
    private static final float[][] FLARE_POINTS = flarePoints();
    private static final float[][] CORE_POINTS = corePoints();
    private static final float[] FRONT_NORMAL = {0F, 0F, -1F};
    private static final float[][] SIDE_NORMALS = sideNormals();
    private static final float[][] BEVEL_NORMALS = bevelNormals();
    private static final float[][] FACE_NORMALS = faceNormals();
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

    /** One recognizable base color per gem family; ranks never change the star's appearance. */
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

    /** The center of a visibly colorful glass gradient; the thin rim retains the gem's type color. */
    public static int animatedColor(GemType type, float ticks, int index) {
        return glassColor(type, ticks, index, 0F, 0F);
    }

    /** Continuous color across the glass face and around a full twelve-second hue cycle. */
    public static int glassColor(GemType type, float ticks, int index, float x, float y) {
        float hue = (ticks % 240F) / 240F + index * .137F + type.ordinal() * .061F
                + x / STAR_SIZE * .35F + y / STAR_SIZE * .28F;
        hue -= (float)Math.floor(hue);
        return Mth.hsvToRgb(hue, .84F, 1F) & 0xFFFFFF;
    }

    private static float[][] starPoints() {
        float[][] points = new float[12][2];
        for (int i = 0; i < points.length; i++) {
            double angle = i * Math.PI / 6D;
            // The intersections of two equilateral triangles form a true six-pointed silhouette.
            float radius = (i % 2 == 0 ? .5F : .28867513F) * STAR_SIZE;
            points[i][0] = (float)Math.sin(angle) * radius;
            points[i][1] = -(float)Math.cos(angle) * radius;
        }
        return points;
    }

    private static float[][] flarePoints() {
        float[][] points = new float[8][2];
        for (int i = 0; i < points.length; i++) {
            double angle = (i + 1) * Math.PI / 4D;
            float radius = STAR_SIZE * (i % 2 == 0 ? .78F : .055F);
            points[i][0] = (float)Math.sin(angle) * radius;
            points[i][1] = -(float)Math.cos(angle) * radius;
        }
        return points;
    }

    private static float[][] corePoints() {
        float[][] points = new float[8][2];
        for (int i = 0; i < points.length; i++) {
            double angle = i * Math.PI / 4D;
            points[i][0] = (float)Math.sin(angle) * STAR_SIZE * .17F;
            points[i][1] = -(float)Math.cos(angle) * STAR_SIZE * .17F;
        }
        return points;
    }

    private static float[][] sideNormals() {
        float[][] normals = new float[12][3];
        for (int i = 0; i < normals.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            float x = b[1] - a[1], y = a[0] - b[0];
            float length = (float)Math.sqrt(x * x + y * y);
            normals[i] = new float[]{x / length, y / length, 0F};
        }
        return normals;
    }

    private static float[] frontNormal(float ax, float ay, float az, float bx, float by, float bz,
                                        float cx, float cy, float cz) {
        float ux = bx - ax, uy = by - ay, uz = bz - az;
        float vx = cx - ax, vy = cy - ay, vz = cz - az;
        float x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
        float length = (float)Math.sqrt(x * x + y * y + z * z);
        float sign = z > 0F ? -1F : 1F;
        return new float[]{x / length * sign, y / length * sign, z / length * sign};
    }

    private static float[][] bevelNormals() {
        float[][] normals = new float[12][3];
        for (int i = 0; i < normals.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            normals[i] = frontNormal(a[0] * .94F, a[1] * .94F, SIDE_FRONT_Z,
                    b[0] * .94F, b[1] * .94F, SIDE_FRONT_Z,
                    b[0] * .84F, b[1] * .84F, BEVEL_FRONT_Z);
        }
        return normals;
    }

    private static float[][] faceNormals() {
        float[][] normals = new float[12][3];
        for (int i = 0; i < normals.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            normals[i] = frontNormal(0F, 0F, FACE_PEAK_Z, a[0] * .84F, a[1] * .84F, BEVEL_FRONT_Z,
                    b[0] * .84F, b[1] * .84F, BEVEL_FRONT_Z);
        }
        return normals;
    }

    /** A slow three-second shimmer, with a small phase offset between neighboring stars. */
    public static float glowPulse(float ticks, int index) {
        return .5F + .5F * (float)Math.cos((ticks + index * 4F) * Math.PI * 2D / 60D);
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    /** Independent irregular flashes: 2.5--7 seconds apart, each with a quick rise and soft .45s burst. */
    public static float flashStrength(float ticks, int index, long entitySeed) {
        long seed = mix64(entitySeed + (index + 1L) * 0x9E3779B97F4A7C15L);
        double local = ticks - Math.floorMod(seed, 95L);
        long cycle = (long)Math.floor(local / 95D);
        int delay = (int)Math.floorMod(mix64(seed + cycle * 0xD1B54A32D192ED03L), 46L);
        double elapsed = local - cycle * 95D - delay;
        if (elapsed <= 0D || elapsed >= 9D) return 0F;
        double envelope = elapsed < 1.5D ? elapsed / 1.5D : (9D - elapsed) / 7.5D;
        return (float)(envelope * envelope * (3D - 2D * envelope));
    }

    private static void star(PoseStack.Pose pose, VertexConsumer vertices, GemType type, int light,
                             float pulse, float flash, float ticks, int index) {
        int color = starColor(type);
        // A real shallow side wall gives the star thickness when the wearer turns.
        for (int i = 0; i < STAR_POINTS.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length], n = SIDE_NORMALS[i];
            vertex(pose, vertices, a[0], a[1], 0F, color, .72F, light, 78, n);
            vertex(pose, vertices, b[0], b[1], 0F, color, .72F, light, 78, n);
            vertex(pose, vertices, b[0], b[1], SIDE_FRONT_Z, color, .72F, light, 78, n);
            vertex(pose, vertices, a[0], a[1], SIDE_FRONT_Z, color, .72F, light, 78, n);
        }
        // Only a narrow ring is darkened: no backing plate obscures the transparent center.
        for (int i = 0; i < STAR_POINTS.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            ring(pose, vertices, a, b, 1F, .94F, SIDE_FRONT_Z, SIDE_FRONT_Z,
                    color, .55F, light, Math.round(110F + 6F * pulse), FRONT_NORMAL);
        }
        // Bright glass bevels catch light above/left and retain a tinted lower edge.
        for (int i = 0; i < STAR_POINTS.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            float catchLight = Math.max(0F, -SIDE_NORMALS[i][0] * .6F - SIDE_NORMALS[i][1] * .8F);
            ring(pose, vertices, a, b, .94F, .84F, SIDE_FRONT_Z, BEVEL_FRONT_Z,
                    highlight(color, .12F + .28F * catchLight), .92F + .12F * catchLight,
                    light, Math.round(128F + 28F * catchLight), BEVEL_NORMALS[i]);
        }
        for (int i = 0; i < STAR_POINTS.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            int alpha = Math.round(108F + 6F * pulse + 10F * flash);
            float[] normal = FACE_NORMALS[i];
            int center = highlight(glassColor(type, ticks, index, 0F, 0F), .06F);
            int aColor = glassColor(type, ticks, index, a[0] * .84F, a[1] * .84F);
            int bColor = glassColor(type, ticks, index, b[0] * .84F, b[1] * .84F);
            vertex(pose, vertices, 0F, 0F, FACE_PEAK_Z, center, 1.03F, light, alpha, normal);
            vertex(pose, vertices, a[0] * .84F, a[1] * .84F, BEVEL_FRONT_Z, aColor, 1F, light, alpha, normal);
            vertex(pose, vertices, b[0] * .84F, b[1] * .84F, BEVEL_FRONT_Z, bColor, 1F, light, alpha, normal);
            vertex(pose, vertices, b[0] * .84F, b[1] * .84F, BEVEL_FRONT_Z, bColor, 1F, light, alpha, normal);
        }
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer vertices, float[] a, float[] b,
                              float outer, float inner, float outerZ, float innerZ, int color,
                              float shade, int light, int alpha, float[] normal) {
        vertex(pose, vertices, a[0] * outer, a[1] * outer, outerZ, color, shade, light, alpha, normal);
        vertex(pose, vertices, b[0] * outer, b[1] * outer, outerZ, color, shade, light, alpha, normal);
        vertex(pose, vertices, b[0] * inner, b[1] * inner, innerZ, color, shade, light, alpha, normal);
        vertex(pose, vertices, a[0] * inner, a[1] * inner, innerZ, color, shade, light, alpha, normal);
    }

    private static void glow(PoseStack.Pose pose, VertexConsumer vertices, int color,
                             float pulse, float flash) {
        int strength = Math.round(24F + 8F * pulse + 40F * flash);
        int edge = Math.round(strength * .45F);
        for (int i = 0; i < STAR_POINTS.length; i++) {
            float[] a = STAR_POINTS[i], b = STAR_POINTS[(i + 1) % STAR_POINTS.length];
            // A faint emissive center keeps the color readable in shade without turning white.
            vertex(pose, vertices, 0F, 0F, -.009F, color, 1.1F, LightTexture.FULL_BRIGHT, strength);
            vertex(pose, vertices, a[0], a[1], -.009F, color, 1F, LightTexture.FULL_BRIGHT, edge);
            vertex(pose, vertices, b[0], b[1], -.009F, color, 1F, LightTexture.FULL_BRIGHT, edge);
            vertex(pose, vertices, b[0], b[1], -.009F, color, 1F, LightTexture.FULL_BRIGHT, edge);
            // Fade smoothly to zero around the star; no particles or opaque outer silhouette.
            vertex(pose, vertices, a[0], a[1], -.009F, color, 1F, LightTexture.FULL_BRIGHT, edge);
            vertex(pose, vertices, a[0] * 1.65F, a[1] * 1.65F, -.009F, color, 1F, LightTexture.FULL_BRIGHT, 0);
            vertex(pose, vertices, b[0] * 1.65F, b[1] * 1.65F, -.009F, color, 1F, LightTexture.FULL_BRIGHT, 0);
            vertex(pose, vertices, b[0], b[1], -.009F, color, 1F, LightTexture.FULL_BRIGHT, edge);
        }
        flash(pose, vertices, color, flash);
    }

    private static int highlight(int color, float whiteness) {
        int highlight = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int channel = color >> shift & 255;
            highlight |= Math.round(channel + (255 - channel) * whiteness) << shift;
        }
        return highlight;
    }

    /** A small bright heart and thin X-shaped rays flash at the center of this star only. */
    private static void flash(PoseStack.Pose pose, VertexConsumer vertices, int color, float strength) {
        flashFan(pose, vertices, FLARE_POINTS, highlight(color, .8F), -.0095F, Math.round(190F * strength));
        flashFan(pose, vertices, CORE_POINTS, highlight(color, .92F), -.010F, Math.round(220F * strength));
    }

    private static void flashFan(PoseStack.Pose pose, VertexConsumer vertices, float[][] points,
                                  int color, float z, int alpha) {
        for (int i = 0; i < points.length; i++) {
            float[] a = points[i], b = points[(i + 1) % points.length];
            vertex(pose, vertices, 0F, 0F, z, color, 1F, LightTexture.FULL_BRIGHT, alpha);
            vertex(pose, vertices, a[0], a[1], z, color, 1F, LightTexture.FULL_BRIGHT, 0);
            vertex(pose, vertices, b[0], b[1], z, color, 1F, LightTexture.FULL_BRIGHT, 0);
            vertex(pose, vertices, b[0], b[1], z, color, 1F, LightTexture.FULL_BRIGHT, 0);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
                               int color, float shade, int light, int alpha) {
        vertex(pose, vertices, x, y, z, color, shade, light, alpha, null);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
                               int color, float shade, int light, int alpha, float[] normal) {
        vertices.addVertex(pose, x, y, z)
                .setColor(Math.min(255, Math.round((color >> 16 & 255) * shade)),
                        Math.min(255, Math.round((color >> 8 & 255) * shade)),
                        Math.min(255, Math.round((color & 255) * shade)), alpha)
                .setUv(.5F, .5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, normal == null ? 0F : normal[0], normal == null ? 0F : normal[1], normal == null ? -1F : normal[2]);
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
        long seed = entity.getUUID().getMostSignificantBits() ^ entity.getUUID().getLeastSignificantBits();
        List<SocketGem> sockets = selectSockets(allSockets, seed);
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        float front = chest.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST
                ? FRONT_Z : -2.3F / 16F;
        pose.pushPose();
        try {
            youngTransform(pose, parent);
            parent.body.translateAndRotate(pose);
            // Switching a non-fixed render type closes its buffer: finish the base stars first.
            renderPass(pose, buffers.getBuffer(RenderType.entityTranslucent(STAR_TEXTURE, false)),
                    light, sockets, ticks, front, seed, false);
            renderPass(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(STAR_TEXTURE, false)),
                    light, sockets, ticks, front, seed, true);
        } finally {
            pose.popPose();
        }
    }

    private static void renderPass(PoseStack pose, VertexConsumer vertices, int light,
                                   List<SocketGem> sockets, float ticks, float front, long seed, boolean glowing) {
        for (int index = 0; index < sockets.size(); index++) {
            pose.pushPose();
            try {
                pose.translate(socketX(index, sockets.size()) / 16F, socketY(index, sockets.size()) / 16F, front);
                GemType type = sockets.get(index).type();
                int color = animatedColor(type, ticks, index);
                float pulse = glowPulse(ticks, index);
                float flash = flashStrength(ticks, index, seed);
                if (glowing) glow(pose.last(), vertices, color, pulse, flash);
                else star(pose.last(), vertices, type, light, pulse, flash, ticks, index);
            } finally {
                pose.popPose();
            }
        }
    }
}
