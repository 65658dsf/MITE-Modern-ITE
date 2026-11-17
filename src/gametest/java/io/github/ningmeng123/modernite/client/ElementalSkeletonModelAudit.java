package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.client.entity.ElementalSkeletonModel;
import io.github.ningmeng123.modernite.client.entity.SourceGlowLayer;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Actual GPU controls for the three elemental skeleton appearances. @author NingMeng123 */
final class ElementalSkeletonModelAudit {
    private static final List<ExtremeKind> KINDS = List.of(ExtremeKind.SKELETON_BLAZE,
            ExtremeKind.SKELETON_LIGHTNING, ExtremeKind.SKELETON_FLOATING);

    private ElementalSkeletonModelAudit() {}

    static JsonObject capture(Minecraft mc, Path directory) throws Exception {
        Files.createDirectories(directory);
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        JsonArray cases = new JsonArray();
        report.add("skeletons", cases);
        for (ExtremeKind kind : KINDS) {
            AbstractSkeleton skeleton = (AbstractSkeleton)ExtremeMobs.type(kind).create(mc.level);
            require(skeleton != null, "The registered elemental skeleton must create: " + kind);
            prepare(mc, skeleton);
            JsonObject facts = new JsonObject();
            cases.add(facts);
            facts.addProperty("kind", kind.id());
            try {
                verify(mc, skeleton, kind, directory, facts);
            } finally {
                skeleton.discard();
            }
        }
        report.addProperty("status", "passed");
        report.addProperty("visual_review", "review_actual_gpu_screenshots");
        return report;
    }

    private static void verify(Minecraft mc, AbstractSkeleton skeleton, ExtremeKind kind,
            Path directory, JsonObject facts) throws Exception {
        LivingEntityRenderer<?, ?> renderer = renderer(mc, skeleton);
        require(renderer.getModel() instanceof ElementalSkeletonModel,
                "The registered renderer must use its elemental native model: " + kind);
        ElementalSkeletonModel model = (ElementalSkeletonModel)renderer.getModel();
        facts.addProperty("model", model.getClass().getName());
        geometry(model, facts);
        textures(mc, skeleton, kind, facts);
        require(layers(renderer).stream().filter(SourceGlowLayer.class::isInstance).count() == 1,
                "Each elemental renderer must have one emissive mask layer");

        try (NativeImage idle = render(mc, skeleton, false)) {
            require(foreground(idle) > 250, "The complete elemental body must draw visible GPU pixels");
            write(idle, directory, kind.id() + "-idle", facts);
            facts.addProperty("idle_foreground_pixels", foreground(idle));
            Map<String, List<Float>> idlePose = pose(model);
            for (int tick = 0; tick < 8; tick++) skeleton.walkAnimation.update(.8F, 1F);
            skeleton.tickCount = 60;
            try (NativeImage walking = render(mc, skeleton, false)) {
                require(!pose(model).equals(idlePose), "Walking must animate the elemental skeleton bones");
                require(changed(idle, walking) > 10, "The walking pose must change the actual GPU image");
                write(walking, directory, kind.id() + "-walking", facts);
            }
        }
        skeleton.walkAnimation.update(0, 1F);
        skeleton.tickCount = 40;
        skeleton.setAggressive(true);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        for (boolean leftHanded : new boolean[] {false, true}) {
            skeleton.setLeftHanded(leftHanded);
            Equipment equipment = new Equipment(skeleton);
            try (NativeImage bow = render(mc, skeleton, false)) {
                HumanoidModel.ArmPose bowPose = leftHanded ? model.leftArmPose : model.rightArmPose;
                require(bowPose == HumanoidModel.ArmPose.BOW_AND_ARROW,
                        "The active bow hand must retain the native aiming pose");
                require(model.leftArm.xRot < -.5F && model.rightArm.xRot < -.5F,
                        "Both arms must participate in native bow aiming");
                write(bow, directory, kind.id() + (leftHanded ? "-bow-left" : "-bow-right"), facts);
            }
            equipment.unchanged(skeleton);
        }
        skeleton.setAggressive(false);
        skeleton.setLeftHanded(false);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        try (NativeImage bare = render(mc, skeleton, false)) {
            skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            Equipment equipment = new Equipment(skeleton);
            try (NativeImage armored = render(mc, skeleton, false)) {
                require(changed(bare, armored) > 100, "Equipped iron armor must visibly render on the new skeleton");
                write(armored, directory, kind.id() + "-iron-armor", facts);
            }
            equipment.unchanged(skeleton);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) skeleton.setItemSlot(slot, ItemStack.EMPTY);
        try (NativeImage glowing = render(mc, skeleton, true);
             NativeImage plain = withoutGlow(mc, skeleton, true)) {
            int changed = changed(glowing, plain);
            require(changed > 5, "The elemental emissive mask must visibly illuminate actual dim GPU pixels");
            facts.addProperty("dim_emissive_changed_pixels", changed);
            write(glowing, directory, kind.id() + "-dim-glow", facts);
            write(plain, directory, kind.id() + "-dim-without-glow", facts);
        }
        skeleton.setInvisible(true);
        try (NativeImage invisible = render(mc, skeleton, true);
             NativeImage plain = withoutGlow(mc, skeleton, true)) {
            require(changed(invisible, plain) == 0, "Invisibility must suppress the elemental emissive layer");
            write(invisible, directory, kind.id() + "-invisible", facts);
        } finally {
            skeleton.setInvisible(false);
        }
        skeleton.setGlowingTag(true);
        try (NativeImage outlined = render(mc, skeleton, false)) {
            write(outlined, directory, kind.id() + "-glowing-flag", facts);
            facts.addProperty("glowing_flag_render_succeeded", true);
        } finally {
            skeleton.setGlowingTag(false);
        }
        boolean emfLoaded = ModList.get().isLoaded("entity_model_features");
        boolean expectedAnimation = emfLoaded && (mc.getResourceManager().getResource(
                ResourceLocation.withDefaultNamespace("optifine/cem/skeleton.jem")).isPresent()
                || mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("emf/cem/skeleton.jem")).isPresent());
        boolean appliedAnimation = model.freshAnimationsApplied();
        facts.addProperty("emf_loaded", emfLoaded);
        facts.addProperty("fresh_animations_expected", expectedAnimation);
        facts.addProperty("fresh_animations_applied", appliedAnimation);
        require(appliedAnimation == expectedAnimation,
                "The actual elemental skeleton draw must follow the installed skeleton animation: " + kind);
        facts.addProperty("equipment_preserved", true);
    }

    private static void geometry(ElementalSkeletonModel model, JsonObject facts) throws Exception {
        require(cubes(model.body).isEmpty(), "The open ribcage must have no solid torso box");
        require(cubes(model.hat).isEmpty(), "The skull must have no opaque outer head shell");
        for (String name : List.of("rib_upper", "rib_middle", "rib_lower", "spine", "pelvis")) {
            ModelPart part = model.body.getChild(name);
            require(part.getAllParts().anyMatch(child -> {
                try { return !cubes(child).isEmpty(); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            }), "The modeled ribcage component must contain real cuboids: " + name);
        }
        SkeletonModel<AbstractSkeleton> reference = new SkeletonModel<>(SkeletonModel.createBodyLayer().bakeRoot());
        Map<String, ModelPart> actualParts = parts(model), referenceParts = parts(reference);
        actualParts.forEach((name, part) -> {
            var actual = part.getInitialPose();
            var expected = referenceParts.get(name).getInitialPose();
            require(actual.x == expected.x && actual.y == expected.y && actual.z == expected.z,
                    "Native skeleton attachment pivots must remain compatible: " + name);
        });
        Uvs uvs = new Uvs();
        model.root().visit(new PoseStack(), (pose, path, index, cube) -> cube.compile(new PoseStack().last(), uvs, 0, 0, -1));
        require(uvs.coordinates > 100 && uvs.invalid == 0,
                "All rendered elemental cuboids must use normalized atlas UVs");
        facts.addProperty("open_ribcage_verified", true);
        facts.addProperty("native_attachment_pivots_preserved", true);
        facts.addProperty("uv_coordinates_checked", uvs.coordinates);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void textures(Minecraft mc, AbstractSkeleton skeleton, ExtremeKind kind, JsonObject facts) throws Exception {
        String suffix = switch (kind) {
            case SKELETON_BLAZE -> "blaze";
            case SKELETON_LIGHTNING -> "lightning";
            case SKELETON_FLOATING -> "floating";
            default -> throw new IllegalArgumentException("Unexpected skeleton " + kind);
        };
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,
                "textures/entity/skeleton/elemental_" + suffix + ".png");
        ResourceLocation selected = ((LivingEntityRenderer)renderer(mc, skeleton)).getTextureLocation(skeleton);
        require(selected.equals(expected), "The actual renderer must select the dedicated elemental skin");
        ResourceLocation glow = ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,
                "textures/entity/skeleton/elemental_" + suffix + "_glow.png");
        try (var skinStream = mc.getResourceManager().open(selected);
             var glowStream = mc.getResourceManager().open(glow);
             NativeImage skin = NativeImage.read(skinStream);
             NativeImage mask = NativeImage.read(glowStream)) {
            require(skin.getWidth() == 64 && skin.getHeight() == 64
                    && mask.getWidth() == 64 && mask.getHeight() == 64, "Both elemental atlases must be 64x64");
            int lit = 0;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                if ((mask.getPixelRGBA(x, y) >>> 24) == 0) continue;
                lit++;
                require((skin.getPixelRGBA(x, y) >>> 24) != 0, "Glow texels must correspond to opaque skin texels");
            }
            require(lit > 5 && lit < 500, "The elemental glow mask must remain sparse and visible");
            facts.addProperty("glow_texels", lit);
        }
        facts.addProperty("skin", selected.toString());
        facts.addProperty("glow", glow.toString());
    }

    private static void prepare(Minecraft mc, AbstractSkeleton skeleton) {
        skeleton.setPos(mc.player.position());
        skeleton.setNoAi(true);
        skeleton.setOnGround(true);
        skeleton.tickCount = 40;
        skeleton.setYRot(0);
        skeleton.yRotO = skeleton.yBodyRot = skeleton.yBodyRotO = skeleton.yHeadRot = skeleton.yHeadRotO = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) skeleton.setItemSlot(slot, ItemStack.EMPTY);
    }

    private static NativeImage render(Minecraft mc, AbstractSkeleton skeleton, boolean dim) {
        return MobRenderRegressionAudit.capture(mc, skeleton, dim, .35F);
    }

    private static NativeImage withoutGlow(Minecraft mc, AbstractSkeleton skeleton, boolean dim) throws Exception {
        List<Object> layers = layers(renderer(mc, skeleton)), saved = new ArrayList<>(layers);
        try {
            layers.removeIf(SourceGlowLayer.class::isInstance);
            return render(mc, skeleton, dim);
        } finally {
            layers.clear();
            layers.addAll(saved);
        }
    }

    private static LivingEntityRenderer<?, ?> renderer(Minecraft mc, AbstractSkeleton skeleton) {
        return (LivingEntityRenderer<?, ?>)mc.getEntityRenderDispatcher().getRenderer(skeleton);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> layers(LivingEntityRenderer<?, ?> renderer) throws Exception {
        Field field = LivingEntityRenderer.class.getDeclaredField("layers");
        field.setAccessible(true);
        return (List<Object>)field.get(renderer);
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubes(ModelPart part) throws Exception {
        Field field = ModelPart.class.getDeclaredField("cubes");
        field.setAccessible(true);
        return (List<ModelPart.Cube>)field.get(part);
    }

    private static Map<String, ModelPart> parts(HumanoidModel<?> model) {
        Map<String, ModelPart> result = new LinkedHashMap<>();
        result.put("head", model.head);
        result.put("hat", model.hat);
        result.put("body", model.body);
        result.put("right_arm", model.rightArm);
        result.put("left_arm", model.leftArm);
        result.put("right_leg", model.rightLeg);
        result.put("left_leg", model.leftLeg);
        return result;
    }

    private static Map<String, List<Float>> pose(HumanoidModel<?> model) {
        Map<String, List<Float>> result = new LinkedHashMap<>();
        parts(model).forEach((name, part) -> result.put(name,
                List.of(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot)));
        return result;
    }

    private static void write(NativeImage image, Path directory, String name, JsonObject facts) throws Exception {
        Path path = directory.resolve(name + ".png");
        image.writeToFile(path);
        facts.addProperty(name + "_screenshot", path.toString());
    }

    private static int foreground(NativeImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getPixelRGBA(x, y);
            if (Math.abs((pixel & 255) - 16) + Math.abs((pixel >>> 8 & 255) - 24)
                    + Math.abs((pixel >>> 16 & 255) - 32) > 18) count++;
        }
        return count;
    }

    private static int changed(NativeImage first, NativeImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            int a = first.getPixelRGBA(x, y), b = second.getPixelRGBA(x, y);
            if (Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))
                    + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255)) > 12) count++;
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Uvs implements VertexConsumer {
        int coordinates, invalid;
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setUv(float u, float v) {
            coordinates++;
            if (u < 0 || u > 1 || v < 0 || v > 1) invalid++;
            return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    private static final class Equipment {
        private final ItemStack[] references = new ItemStack[EquipmentSlot.values().length];
        private final ItemStack[] copies = new ItemStack[references.length];

        Equipment(AbstractSkeleton skeleton) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                references[slot.ordinal()] = skeleton.getItemBySlot(slot);
                copies[slot.ordinal()] = references[slot.ordinal()].copy();
            }
        }

        void unchanged(AbstractSkeleton skeleton) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                require(skeleton.getItemBySlot(slot) == references[slot.ordinal()]
                        && ItemStack.matches(skeleton.getItemBySlot(slot), copies[slot.ordinal()]),
                        "Rendering must retain the equipment instances and components in " + slot);
            }
        }
    }
}
