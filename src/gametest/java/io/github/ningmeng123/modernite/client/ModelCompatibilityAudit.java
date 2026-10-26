package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.modernite.client.entity.SpeciesHeadLayer;
import io.github.ningmeng123.modernite.client.entity.VariantZombieModel;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.fml.ModList;
import org.joml.Vector3f;

/** Focused real-render checks for source skins, hidden drivers and species head attachments. @author NingMeng123 */
final class ModelCompatibilityAudit {
    private static final List<String> BONES = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    private static final List<String> CREEPER_BONES = List.of("head", "body", "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg");

    private ModelCompatibilityAudit() {}

    static JsonObject capture(Minecraft mc, Path directory) throws Exception {
        Files.createDirectories(directory);
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("actual_gpu_rendering", true);
        report.addProperty("emf_loaded", ModList.get().isLoaded("entity_model_features"));
        JsonArray samples = new JsonArray(); report.add("entities", samples);
        for (ExtremeKind kind : List.of(ExtremeKind.ZOMBIE_FLY_AWAY, ExtremeKind.ZOMBIE_GHAST, ExtremeKind.RED_GHOUL)) {
            Zombie entity = (Zombie)create(mc, ExtremeMobs.type(kind));
            JsonObject facts = new JsonObject(); samples.add(facts); facts.addProperty("entity", kind.id());
            try { zombie(mc, entity, kind, facts, directory); }
            finally { entity.discard(); }
        }
        for (boolean black : new boolean[]{false, true}) {
            Creeper entity = (Creeper)create(mc, black ? ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER) : MiteMobs.type(MobKind.INFERNAL_CREEPER));
            JsonObject facts = new JsonObject(); samples.add(facts);
            facts.addProperty("entity", black ? "modern_ite:black_infernal_creeper" : "mite:infernal_creeper");
            try { creeper(mc, entity, black, facts, directory); }
            finally { entity.discard(); }
        }
        report.addProperty("focused_entities", samples.size());
        report.addProperty("visual_verdict", "requires_manual_screenshot_review");
        return report;
    }

    private static void zombie(Minecraft mc, Zombie entity, ExtremeKind kind, JsonObject facts, Path directory) throws Exception {
        var renderer = renderer(mc, entity);
        require(renderer.getModel() instanceof VariantZombieModel, "The source zombie variant model must remain visible: " + kind);
        VariantZombieModel model = (VariantZombieModel)renderer.getModel();
        HumanoidModel<Zombie> reference = reference(kind);
        require(mesh(model).equals(mesh(reference)), "Source cuboids and normalized UVs must be unchanged: " + kind);
        facts.addProperty("source_geometry_and_uvs_retained", true);
        sourceTexture(mc, entity, facts);
        boolean expected = hasAnimation(mc, "zombie");
        Map<String, List<Float>> idle;
        entity.tickCount = 40;
        try (NativeImage image = render(mc, entity)) {
            write(image, directory, kind.id() + "-idle", facts);
            require(foreground(image) > 250, "The native zombie draw has visible geometry");
            idle = pose(model);
            require(model.freshAnimationsApplied() == expected, "Actual zombie drawing follows the installed animation: " + kind);
            if (expected) {
                require(model.freshHeadMapped(), "The actual FA head chain must map to the native attachment");
                facts.addProperty("idle_world_space_bones", compareZombie(model));
            }
        }
        for (int tick = 0; tick < 6; tick++) entity.walkAnimation.update(.8F, 1F);
        entity.tickCount = 60;
        try (NativeImage image = render(mc, entity)) {
            write(image, directory, kind.id() + "-walking", facts);
            require(!idle.equals(pose(model)), "Walking changes the visible native zombie bones: " + kind);
            if (expected) facts.addProperty("walking_world_space_bones", compareZombie(model));
        }
        facts.addProperty("fresh_animations_expected", expected);
        facts.addProperty("fresh_animations_applied", model.freshAnimationsApplied());
        if (kind == ExtremeKind.ZOMBIE_GHAST) {
            require(cubes(model.head).isEmpty() && cubes(model.hat).isEmpty(), "The ghast zombie must not grow a vanilla zombie head");
            List<Object> layers = layers(renderer);
            require(layers.stream().anyMatch(SpeciesHeadLayer.class::isInstance), "The ghast species head layer is present");
            entity.walkAnimation.update(0, 1); entity.tickCount = 40;
            List<Object> saved = new ArrayList<>(layers);
            try (NativeImage actual = render(mc, entity)) {
                layers.removeIf(SpeciesHeadLayer.class::isInstance);
                try (NativeImage headless = render(mc, entity)) {
                    int changed = changed(actual, headless);
                    require(changed > 150, "The actual native ghast head adds visible GPU pixels");
                    facts.addProperty("ghast_head_visible_pixels", changed);
                    write(actual, directory, "zombie_ghast-head", facts);
                }
            } finally { layers.clear(); layers.addAll(saved); }
            facts.addProperty("vanilla_head_absent", true);
        }
        if (kind == ExtremeKind.ZOMBIE_FLY_AWAY) {
            entity.attackAnim = entity.oAttackAnim = .5F;
            try (NativeImage image = render(mc, entity)) {
                require(Math.abs(model.rightArm.xRot - model.leftArm.xRot) < .0001F && model.rightArm.xRot < -2F,
                        "The strong zombie retains its two-arm uppercut after borrowed motion");
                facts.addProperty("uppercut_right_arm", model.rightArm.xRot);
                facts.addProperty("uppercut_left_arm", model.leftArm.xRot);
                write(image, directory, kind.id() + "-uppercut", facts);
            } finally { entity.attackAnim = entity.oAttackAnim = 0; }
        }
    }

    private static void creeper(Minecraft mc, Creeper entity, boolean black, JsonObject facts, Path directory) throws Exception {
        var renderer = renderer(mc, entity);
        Object model = renderer.getModel();
        require(model instanceof CreeperModel<?>, "The source creeper still uses a creeper model");
        ModelPart root = (ModelPart)model.getClass().getMethod("root").invoke(model);
        ModelPart reference = CreeperModel.createBodyLayer(CubeDeformation.NONE).bakeRoot();
        require(mesh(root).equals(mesh(reference)), "All six native creeper cuboids and 64x32 UVs must match");
        require(root.getAllParts().count() == 7, "Source creepers must not inherit random stone/mud CEM geometry");
        facts.addProperty("six_native_cuboids_and_source_uvs", true);
        sourceTexture(mc, entity, facts);
        boolean expected = hasAnimation(mc, "creeper");
        String name = black ? "black_infernal_creeper" : "infernal_creeper";
        entity.tickCount = 40;
        Map<String, List<Float>> idle;
        try (NativeImage image = render(mc, entity)) {
            write(image, directory, name + "-idle", facts);
            require(foreground(image) > 200, "The actual native creeper has a visible source body");
            idle = pose(root);
            require(flag(model, "freshAnimationsApplied") == expected, "Creeper borrowing follows the installed animation");
            if (expected) {
                require(flag(model, "freshHeadMapped"), "The real creeper head geometry drives the source head");
                facts.addProperty("idle_world_space_bones", compareCreeper(model, root));
            }
        }
        for (int tick = 0; tick < 6; tick++) entity.walkAnimation.update(.8F, 1F);
        entity.tickCount = 60;
        try (NativeImage image = render(mc, entity)) {
            write(image, directory, name + "-walking", facts);
            require(!idle.equals(pose(root)), "The visible source creeper walk pose changes");
            if (expected) facts.addProperty("walking_world_space_bones", compareCreeper(model, root));
        }
        facts.addProperty("fresh_animations_expected", expected);
        facts.addProperty("fresh_animations_applied", flag(model, "freshAnimationsApplied"));
        set(entity, "swell", 15); set(entity, "oldSwell", 15);
        try (NativeImage image = render(mc, entity)) { write(image, directory, name + "-swelling", facts); }
        finally { set(entity, "swell", 0); set(entity, "oldSwell", 0); }
        charged(mc, entity, renderer, model, root, expected, facts, directory, name);
        if (black) {
            entity.walkAnimation.update(0, 1); entity.tickCount = 40;
            JsonArray variants = new JsonArray(); facts.add("uuid_samples", variants);
            for (int index = 0; index < 8; index++) {
                entity.setUUID(new UUID(0x123456789ABCDEFL, 0x5678901234000000L + index * 104729L));
                try (NativeImage image = render(mc, entity)) {
                    require(mesh(root).equals(mesh(reference)), "Different UUIDs cannot replace the source model with a resource-pack random variant");
                    JsonObject sample = new JsonObject(); sample.addProperty("uuid", entity.getUUID().toString());
                    sample.addProperty("native_uvs_unchanged", true);
                    sample.addProperty("source_texture", texture(mc, entity).toString());
                    variants.add(sample);
                    if (index < 3) write(image, directory, name + "-uuid-" + index, facts);
                }
            }
            Map<ModelPart, Boolean> visibility = new java.util.IdentityHashMap<>();
            root.getAllParts().forEach(part -> visibility.put(part, part.visible));
            List<Object> layers = layers(renderer), saved = new ArrayList<>(layers);
            try {
                for (String part : List.of("body", "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg")) root.getChild(part).visible = false;
                try (NativeImage glowing = MobRenderRegressionAudit.capture(mc, entity, true, .35F)) {
                    layers.clear();
                    try (NativeImage plain = MobRenderRegressionAudit.capture(mc, entity, true, .35F)) {
                        int eyePixels = changed(glowing, plain);
                        require(eyePixels > 2, "Black infernal red eyes actually light the head, rather than the torso");
                        facts.addProperty("head_only_emissive_changed_pixels", eyePixels);
                        write(glowing, directory, name + "-head-eyes", facts);
                    }
                }
            } finally { visibility.forEach((part, visible) -> part.visible = visible); layers.clear(); layers.addAll(saved); }
        }
    }

    private static void charged(Minecraft mc, Creeper entity, Object renderer, Object parentModel, ModelPart parentRoot,
                                boolean expectedAnimation, JsonObject facts, Path directory, String name) throws Exception {
        List<Object> energyLayers = layers(renderer).stream().filter(EnergySwirlLayer.class::isInstance).toList();
        require(energyLayers.size() == 1, "Exactly one source charged layer must replace the vanilla CEM armour layer");
        Object armour = field(energyLayers.getFirst(), "model");
        require(armour.getClass().getName().equals("io.github.ningmeng123.mite.client.mob.SourceCreeperModel"),
                "Charged source creepers require the same source model implementation as their parent");
        ModelPart armourRoot = (ModelPart)armour.getClass().getMethod("root").invoke(armour);
        ModelPart reference = CreeperModel.createBodyLayer(new CubeDeformation(2F)).bakeRoot();
        require(mesh(armourRoot).equals(mesh(reference)) && armourRoot.getAllParts().count() == 7,
                "The charged layer keeps six source cuboids, inflation two and the original normalized 64x32 UVs");
        entity.walkAnimation.update(0, 1); entity.tickCount = 40;
        setPowered(entity, false);
        try (NativeImage uncharged = render(mc, entity)) {
            setPowered(entity, true);
            require(entity.isPowered(), "The actual synced creeper powered flag is enabled");
            try (NativeImage powered = render(mc, entity)) {
                int changed = changed(uncharged, powered);
                require(changed > 0, "The real charged render adds visible energy overlay pixels");
                require(flag(armour, "freshAnimationsApplied") == expectedAnimation
                                && flag(armour, "freshAnimationsApplied") == flag(parentModel, "freshAnimationsApplied"),
                        "Charged and parent models use the same installed animation branch");
                require(samePose(pose(parentRoot), pose(armourRoot)),
                        "Every charged bone shares its parent's position, rotation and scale despite the larger cuboids");
                facts.addProperty("charged_source_model_and_uvs_retained", true);
                facts.addProperty("charged_overlay_changed_pixels", changed);
                facts.addProperty("charged_animation_matches_parent", true);
                facts.addProperty("charged_bone_transforms_match_parent", true);
                write(powered, directory, name + "-charged", facts);
            }
        } finally { setPowered(entity, false); }
    }

    @SuppressWarnings("unchecked")
    private static void setPowered(Creeper entity, boolean powered) throws Exception {
        Field field = Creeper.class.getDeclaredField("DATA_IS_POWERED"); field.setAccessible(true);
        entity.getEntityData().set((EntityDataAccessor<Boolean>)field.get(null), powered);
    }

    private static boolean samePose(Map<String, List<Float>> first, Map<String, List<Float>> second) {
        if (!first.keySet().equals(second.keySet())) return false;
        for (String part : first.keySet()) for (int index = 0; index < first.get(part).size(); index++)
            if (Math.abs(first.get(part).get(index) - second.get(part).get(index)) > .0001F) return false;
        return true;
    }

    private static int compareZombie(VariantZombieModel model) throws Exception {
        Object driver = field(model, "sourceAnimation");
        ModelPart root = (ModelPart)field(driver, "animationRoot");
        Map<String, ModelPart> nativeParts = parts(model);
        Map<String, List<Vector3f>> expected = new LinkedHashMap<>();
        collect(root, "", new PoseStack(), nativeParts, expected);
        ModelPart.Cube virtualHead = cubes(reference(ExtremeKind.RED_GHOUL).head).getFirst();
        for (String name : BONES) {
            require(expected.containsKey(name), "The visible animated geometry exists for " + name);
            ModelPart part = nativeParts.get(name);
            ModelPart.Cube cube = cubes(part).isEmpty() ? virtualHead : cubes(part).getFirst();
            PoseStack pose = new PoseStack(); part.translateAndRotate(pose);
            for (Vector3f vertex : corners(cube, pose)) require(expected.get(name).stream().anyMatch(point -> point.distanceSquared(vertex) < .000001F),
                    "The visible native " + name + " follows the real cumulative FA geometry");
        }
        return BONES.size();
    }

    private static int compareCreeper(Object model, ModelPart nativeRoot) throws Exception {
        ModelPart driverRoot = (ModelPart)field(model, "animationRoot");
        Map<String, ModelPart> nativeParts = new LinkedHashMap<>();
        for (String bone : CREEPER_BONES) nativeParts.put(bone, nativeRoot.getChild(bone));
        Map<String, List<Vector3f>> expected = new LinkedHashMap<>();
        collect(driverRoot, "", new PoseStack(), nativeParts, expected);
        for (String name : CREEPER_BONES) {
            require(expected.containsKey(name), "The actual CEM creeper geometry exists for " + name);
            ModelPart part = nativeParts.get(name); PoseStack pose = new PoseStack(); part.translateAndRotate(pose);
            for (Vector3f vertex : corners(cubes(part).getFirst(), pose)) require(expected.get(name).stream().anyMatch(point -> point.distanceSquared(vertex) < .000001F),
                    "The source creeper " + name + " follows the actual CEM geometry");
        }
        return CREEPER_BONES.size();
    }

    private static void collect(ModelPart part, String path, PoseStack pose, Map<String, ModelPart> nativeParts, Map<String, List<Vector3f>> output) throws Exception {
        if (!part.visible) return;
        pose.pushPose(); part.translateAndRotate(pose);
        try {
            String name = bone(path);
            if (!part.skipDraw && nativeParts.containsKey(name)) for (ModelPart.Cube cube : cubes(part)) {
                List<ModelPart.Cube> nativeCubes = cubes(nativeParts.get(name));
                ModelPart.Cube source = nativeCubes.isEmpty() ? cubes(reference(ExtremeKind.RED_GHOUL).head).getFirst() : nativeCubes.getFirst();
                if (Math.abs(cube.maxX - cube.minX - source.maxX + source.minX) > .001F
                        || Math.abs(cube.maxY - cube.minY - source.maxY + source.minY) > .001F
                        || Math.abs(cube.maxZ - cube.minZ - source.maxZ + source.minZ) > .001F) continue;
                if (!output.containsKey(name) || path.contains("head2")) output.put(name, corners(cube, pose));
            }
            for (var child : children(part).entrySet()) collect(child.getValue(), path + "/" + child.getKey(), pose, nativeParts, output);
        } finally { pose.popPose(); }
    }

    private static List<Vector3f> corners(ModelPart.Cube cube, PoseStack pose) {
        List<Vector3f> result = new ArrayList<>();
        for (float x : new float[]{cube.minX, cube.maxX}) for (float y : new float[]{cube.minY, cube.maxY})
            for (float z : new float[]{cube.minZ, cube.maxZ}) result.add(pose.last().pose().transformPosition(new Vector3f(x / 16, y / 16, z / 16)));
        return result;
    }

    private static HumanoidModel<Zombie> reference(ExtremeKind kind) {
        var mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        if (kind == ExtremeKind.ZOMBIE_FLY_AWAY || kind == ExtremeKind.ZOMBIE_GHAST) mesh.getRoot().addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        if (kind == ExtremeKind.ZOMBIE_GHAST) mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        return new HumanoidModel<>(LayerDefinition.create(mesh, 64, kind == ExtremeKind.RED_GHOUL ? 32 : 64).bakeRoot());
    }

    private static boolean hasAnimation(Minecraft mc, String model) {
        return ModList.get().isLoaded("entity_model_features") && (mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("optifine/cem/" + model + ".jem")).isPresent()
                || mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("emf/cem/" + model + ".jem")).isPresent());
    }

    private static Map<String, ModelPart> parts(HumanoidModel<?> model) {
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        parts.put("head", model.head); parts.put("hat", model.hat); parts.put("body", model.body);
        parts.put("right_arm", model.rightArm); parts.put("left_arm", model.leftArm); parts.put("right_leg", model.rightLeg); parts.put("left_leg", model.leftLeg);
        return parts;
    }

    private static Map<String, List<Float>> pose(HumanoidModel<?> model) {
        Map<String, List<Float>> result = new LinkedHashMap<>(); parts(model).forEach((name, part) -> result.put(name, transform(part))); return result;
    }
    private static Map<String, List<Float>> pose(ModelPart root) {
        Map<String, List<Float>> result = new LinkedHashMap<>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> { try { result.put(path, transform(resolve(root, path))); } catch (Exception exception) { throw new IllegalStateException(exception); } });
        return result;
    }
    private static List<Float> transform(ModelPart part) { return List.of(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale); }
    private static ModelPart resolve(ModelPart root, String path) { for (String name : path.split("/")) if (!name.isEmpty()) root = root.getChild(name); return root; }

    private static Map<String, List<Float>> mesh(HumanoidModel<?> model) {
        Map<String, List<Float>> result = new LinkedHashMap<>();
        parts(model).forEach((name, part) -> meshPart(name, part, result)); return result;
    }
    private static Map<String, List<Float>> mesh(ModelPart root) { Map<String, List<Float>> result = new LinkedHashMap<>(); meshPart("", root, result); return result; }
    private static void meshPart(String name, ModelPart part, Map<String, List<Float>> result) {
        part.visit(new PoseStack(), (pose, path, index, cube) -> { Vertices vertices = new Vertices(); cube.compile(new PoseStack().last(), vertices, 0, 0, -1); result.put(name + path + ":" + index, vertices.values); });
    }

    private static NativeImage render(Minecraft mc, Mob entity) { return MobRenderRegressionAudit.capture(mc, entity, false, .35F); }
    private static Mob create(Minecraft mc, EntityType<? extends Mob> type) {
        Mob entity = type.create(mc.level); require(entity != null, "Entity creation succeeds");
        entity.setPos(mc.player.position()); entity.setNoAi(true); entity.setOnGround(true);
        entity.setYRot(0); entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = 0;
        return entity;
    }
    @SuppressWarnings("rawtypes") private static LivingEntityRenderer renderer(Minecraft mc, Mob entity) { return (LivingEntityRenderer)mc.getEntityRenderDispatcher().getRenderer(entity); }
    @SuppressWarnings("unchecked") private static ResourceLocation texture(Minecraft mc, Mob entity) { return renderer(mc, entity).getTextureLocation(entity); }
    private static void sourceTexture(Minecraft mc, Mob entity, JsonObject facts) throws Exception {
        ResourceLocation texture = texture(mc, entity); facts.addProperty("source_texture", texture.toString());
        require(texture.getNamespace().equals("mite") || texture.getNamespace().equals("modern_ite"), "Resource-pack texture overrides cannot replace species skins");
        try (var stream = mc.getResourceManager().open(texture); NativeImage image = NativeImage.read(stream)) { facts.addProperty("skin_width", image.getWidth()); facts.addProperty("skin_height", image.getHeight()); }
    }
    private static void write(NativeImage image, Path directory, String name, JsonObject facts) throws Exception { Path path = directory.resolve(name + ".png"); image.writeToFile(path); facts.addProperty(name + "_screenshot", path.toString()); }
    private static int foreground(NativeImage image) { int count = 0; for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) { int p = image.getPixelRGBA(x,y); if (Math.abs((p&255)-16)+Math.abs((p>>>8&255)-24)+Math.abs((p>>>16&255)-32)>18) count++; } return count; }
    private static int changed(NativeImage first, NativeImage second) { int count=0; for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++){int a=first.getPixelRGBA(x,y),b=second.getPixelRGBA(x,y); if(Math.abs((a&255)-(b&255))+Math.abs((a>>>8&255)-(b>>>8&255))+Math.abs((a>>>16&255)-(b>>>16&255))>12)count++;}return count; }
    private static boolean flag(Object model, String method) throws Exception { return Boolean.TRUE.equals(model.getClass().getMethod(method).invoke(model)); }
    @SuppressWarnings("unchecked") private static List<ModelPart.Cube> cubes(ModelPart part) throws Exception { return (List<ModelPart.Cube>)field(part,"cubes"); }
    @SuppressWarnings("unchecked") private static Map<String,ModelPart> children(ModelPart part) throws Exception { return (Map<String,ModelPart>)field(part,"children"); }
    @SuppressWarnings("unchecked") private static List<Object> layers(Object renderer) throws Exception { return (List<Object>)field(renderer,"layers"); }
    private static Object field(Object object,String name)throws Exception { for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name); }
    private static void set(Object object,String name,int value)throws Exception { for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);field.setInt(object,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name); }
    private static String bone(String path) { String[] names=path.split("/");for(int index=names.length-1;index>=0;index--){String name=names[index].replaceFirst("^EMF_","");if(name.equals("head2"))return "head";if(BONES.contains(name)||CREEPER_BONES.contains(name))return name;}return ""; }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}

    private static final class Vertices implements VertexConsumer {
        final List<Float> values = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z){values.add(x);values.add(y);values.add(z);return this;}
        @Override public VertexConsumer setUv(float u,float v){values.add(u);values.add(v);return this;}
        @Override public VertexConsumer setColor(int red,int green,int blue,int alpha){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
}
