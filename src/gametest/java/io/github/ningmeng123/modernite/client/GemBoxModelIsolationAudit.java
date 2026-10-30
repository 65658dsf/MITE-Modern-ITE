package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Alternate two real players through the same skin and armor models. @author NingMeng123 */
final class GemBoxModelIsolationAudit {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] BODY = {"head", "hat", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final String[] CLOTH = {"leftSleeve", "rightSleeve", "leftPants", "rightPants", "jacket", "cloak", "ear"};

    private GemBoxModelIsolationAudit() {}

    static JsonObject capture(Minecraft mc, ItemStack box, Path folder) throws Exception {
        Files.createDirectories(folder);
        var local = mc.player;
        var renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(local);
        List<?> layers = layers(renderer);
        var camera = mc.options.getCameraType();
        ItemStack main = local.getMainHandItem(), off = local.getOffhandItem();
        ItemStack[] equipped = new ItemStack[ARMOR.length];
        for (int i = 0; i < ARMOR.length; i++) equipped[i] = local.getItemBySlot(ARMOR[i]);
        RemotePlayer teammate = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return local.getSkin(); }
        };
        teammate.moveTo(local.position()); teammate.setOnGround(true);
        teammate.setXRot(25); teammate.xRotO = 25;
        teammate.tickCount = 120;
        teammate.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        ironArmor(teammate);
        mc.level.addEntity(teammate);
        try {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            check(mc.getEntityRenderDispatcher().getRenderer(teammate) == renderer,
                    "Both distinct players must use the same real skin renderer and armor layers");
            try (NativeImage before = frame(mc, false, pose -> GemBoxDrawPoseAudit.renderThroughDispatcher(
                    mc, teammate, pose, mc.renderBuffers().bufferSource()))) {
                before.writeToFile(folder.resolve("gem-box-teammate-armor-before.png"));
            }
            Map<String, String> baseline = state(renderer, layers);
            check(baseline.keySet().stream().anyMatch(key -> key.startsWith("armor")),
                    "The isolation fixture includes actual reusable inner and outer armor models");
            local.setItemInHand(InteractionHand.MAIN_HAND, box.copy());
            local.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ironArmor(local);
            check(GemBoxArmPose.active(local), "The isolation fixture must exercise a real carrying player");
            try (NativeImage ignored = frame(mc, true, pose -> {
                for (InteractionHand hand : InteractionHand.values()) {
                    RenderHandEvent event = new RenderHandEvent(hand, pose, mc.renderBuffers().bufferSource(),
                            LightTexture.FULL_BRIGHT, 1, 0, 0, 0, local.getItemInHand(hand));
                    NeoForge.EVENT_BUS.post(event);
                    check(event.isCanceled(), "The native event must actually render the two supporting box hands");
                }
            })) {}
            assertState(baseline, state(renderer, layers), folder, "first-person",
                    "Drawing local box hands must preserve every shared player/armor pose, scale and visibility flag");
            int[] nested = {0};
            boolean[] failAfterNested = {false}, emfPreserved = {false};
            Consumer<RenderLivingEvent.Post<?, ?>> nestedDraw = event -> {
                if (event.getEntity() != local) return;
                try {
                    Map<String, String> outer = state(renderer, layers);
                    Object root = emfRoot(renderer.getModel());
                    Object cachedPose = root == null ? null : emfSavedPose(root);
                    if (root != null) check(cachedPose != null, "An actual EMF carrier must own a pose snapshot before the nested draw");
                    GemBoxDrawPoseAudit.renderThroughDispatcher(mc, teammate, event.getPoseStack(), event.getMultiBufferSource());
                    assertState(outer, state(renderer, layers), folder, "nested-teammate",
                            "An ordinary teammate's nested real draw must return the outer carrier's player and armor pose");
                    if (root != null) {
                        check(emfSavedPose(root) == cachedPose,
                                "A nested ordinary render must not clear the outer carrier's EMF pose snapshot");
                        emfPreserved[0] = true;
                    }
                    nested[0]++;
                    if (failAfterNested[0]) throw new IntentionalFailure();
                } catch (IntentionalFailure expected) {
                    throw expected;
                } catch (Exception failure) {
                    throw new IllegalStateException("Cannot audit the nested native teammate draw", failure);
                }
            };
            NeoForge.EVENT_BUS.addListener(nestedDraw);
            boolean etfFailureContextChecked = false;
            try {
                try (NativeImage carried = frame(mc, false, pose -> GemBoxDrawPoseAudit.renderThroughDispatcher(
                        mc, local, pose, mc.renderBuffers().bufferSource()))) {
                    carried.writeToFile(folder.resolve("gem-box-armored-carrier.png"));
                }
                assertState(baseline, state(renderer, layers), folder, "third-person",
                        "A complete armored box-player draw must restore models previously used by another player");
                failAfterNested[0] = true;
                try (EtfFailureContext context = EtfFailureContext.capture(local)) {
                    boolean threw = false;
                    try (NativeImage ignored = frame(mc, false, pose -> GemBoxDrawPoseAudit.renderThroughDispatcher(
                            mc, local, pose, mc.renderBuffers().bufferSource()))) {
                        throw new IllegalStateException("The controlled nested-draw exception did not reach the renderer");
                    } catch (RuntimeException failure) {
                        check(intentional(failure), "Only the deliberately injected render failure is expected");
                        threw = true;
                    }
                    check(threw && nested[0] == 2, "Both nested native passes must actually execute");
                    assertState(baseline, state(renderer, layers), folder, "exception",
                            "An exception still restores every shared player/armor state");
                    etfFailureContextChecked = context != null;
                }
            } finally {
                NeoForge.EVENT_BUS.unregister(nestedDraw);
            }
            try (NativeImage after = frame(mc, false, pose -> GemBoxDrawPoseAudit.renderThroughDispatcher(
                    mc, teammate, pose, mc.renderBuffers().bufferSource()))) {
                after.writeToFile(folder.resolve("gem-box-teammate-armor-after.png"));
            }
            JsonObject facts = new JsonObject();
            facts.addProperty("two_distinct_players_share_renderer", true);
            facts.addProperty("first_person_preserves_teammate_models", true);
            facts.addProperty("third_person_preserves_teammate_armor_models", true);
            facts.addProperty("nested_non_carrier_draws", nested[0]);
            facts.addProperty("nested_emf_snapshot_checked", emfPreserved[0]);
            facts.addProperty("exception_restores_shared_models", true);
            facts.addProperty("expected_exception_etf_context_checked", etfFailureContextChecked);
            facts.addProperty("native_part_snapshots_checked", baseline.size());
            return facts;
        } finally {
            teammate.discard();
            local.setItemInHand(InteractionHand.MAIN_HAND, main); local.setItemInHand(InteractionHand.OFF_HAND, off);
            for (int i = 0; i < ARMOR.length; i++) local.setItemSlot(ARMOR[i], equipped[i]);
            mc.options.setCameraType(camera);
        }
    }

    private static void assertState(Map<String, String> expected, Map<String, String> actual, Path folder,
            String pass, String message) throws Exception {
        if (expected.equals(actual)) return;
        JsonObject differences = new JsonObject();
        var keys = new LinkedHashSet<>(expected.keySet()); keys.addAll(actual.keySet());
        for (String key : keys) if (!Objects.equals(expected.get(key), actual.get(key))) {
            JsonObject value = new JsonObject();
            value.addProperty("before", expected.get(key)); value.addProperty("after", actual.get(key));
            differences.add(key, value);
        }
        Files.writeString(folder.resolve("gem-box-model-state-" + pass + "-differences.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(differences));
        throw new IllegalStateException(message + "; changed " + differences.size() + " keys: " + differences);
    }

    private static Object emfRoot(PlayerModel<?> model) throws Exception {
        try {
            Class<?> api = Class.forName("traben.entity_model_features.models.IEMFModel");
            return api.isInstance(model) ? api.getMethod("emf$getEMFRootModel").invoke(model) : null;
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private static Object emfSavedPose(Object root) throws Exception {
        Field field = GemBoxEmfPoseCompat.class.getDeclaredField("POSES"); field.setAccessible(true);
        return ((Map<?, ?>)field.get(null)).get(root);
    }

    private static boolean intentional(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause())
            if (current instanceof IntentionalFailure) return true;
        return false;
    }

    private static final class IntentionalFailure extends RuntimeException {
        private IntentionalFailure() { super("Controlled gem-box nested-render isolation audit failure"); }
    }

    /** ETF only unmounts on a normal RETURN; isolate this audit's deliberate failed draw. */
    private record EtfFailureContext(Method state, Method unMount, Method entity, Object before, Entity rendered)
            implements AutoCloseable {
        private static EtfFailureContext capture(Entity rendered) throws ReflectiveOperationException {
            Class<?> api;
            try {
                api = Class.forName("traben.entity_texture_features.features.state.ETFState");
            } catch (ClassNotFoundException absent) {
                return null;
            }
            Method state = api.getMethod("state");
            Method entity = Class.forName("traben.entity_texture_features.features.state.ETFEntityRenderState")
                    .getMethod("entity");
            return new EtfFailureContext(state, api.getMethod("unMount"), entity, state.invoke(null), rendered);
        }

        @Override public void close() throws ReflectiveOperationException {
            Object after = state.invoke(null);
            if (after == before) return;
            check(after != null && entity.invoke(after) == rendered,
                    "The deliberately failed draw may only leave its own ETF context mounted");
            unMount.invoke(null);
            check(state.invoke(null) == before, "The failed draw's single ETF mount must return its prior context");
        }
    }

    private static void ironArmor(net.minecraft.world.entity.LivingEntity entity) {
        entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        entity.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        entity.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }

    private static List<?> layers(PlayerRenderer renderer) throws Exception {
        Field field = LivingEntityRenderer.class.getDeclaredField("layers"); field.setAccessible(true);
        return (List<?>)field.get(renderer);
    }

    private static Map<String, String> state(PlayerRenderer renderer, List<?> layers) throws Exception {
        Map<String, String> facts = new LinkedHashMap<>();
        model(facts, "player", renderer.getModel());
        for (Object layer : layers) if (layer instanceof HumanoidArmorLayer<?, ?, ?>) {
            for (String name : List.of("innerModel", "outerModel")) {
                Field field = HumanoidArmorLayer.class.getDeclaredField(name); field.setAccessible(true);
                model(facts, "armor/" + name, (HumanoidModel<?>)field.get(layer));
            }
        }
        return facts;
    }

    private static void model(Map<String, String> facts, String prefix, HumanoidModel<?> model) throws Exception {
        facts.put(prefix, model.attackTime + "/" + model.riding + "/" + model.young + "/" + model.crouching
                + "/" + model.swimAmount + "/" + model.leftArmPose + "/" + model.rightArmPose);
        var anchor = GemBoxArmPose.savedSupportAnchor(model);
        facts.put(prefix + "/boxSupport", anchor == null ? "none" : anchor.x + "/" + anchor.y + "/" + anchor.z);
        List<String> names = new ArrayList<>(List.of(BODY));
        if (model instanceof PlayerModel<?>) names.addAll(List.of(CLOTH));
        for (String name : names) {
            Class<?> owner = List.of(BODY).contains(name) ? HumanoidModel.class : PlayerModel.class;
            Field field = owner.getDeclaredField(name); field.setAccessible(true);
            List<ModelPart> parts = ((ModelPart)field.get(model)).getAllParts().toList();
            for (int i = 0; i < parts.size(); i++) {
                ModelPart part = parts.get(i);
                facts.put(prefix + "/" + name + "/" + i, part.x + "/" + part.y + "/" + part.z + "/"
                        + part.xRot + "/" + part.yRot + "/" + part.zRot + "/" + part.xScale + "/" + part.yScale
                        + "/" + part.zScale + "/" + part.visible + "/" + part.skipDraw);
            }
        }
    }

    private static NativeImage frame(Minecraft mc, boolean first, Draw draw) {
        TextureTarget target = new TextureTarget(480, 360, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        try {
            target.setClearColor(.063F, .094F, .125F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(first ? new Matrix4f().setPerspective((float)Math.toRadians(70), 4F / 3, .05F, 100)
                    : new Matrix4f().setOrtho(0, 480, 360, 0, -1000, 1000),
                    first ? VertexSorting.DISTANCE_TO_ORIGIN : VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); Lighting.setupForEntityInInventory();
            PoseStack pose = new PoseStack();
            if (!first) {
                pose.translate(240, 338, 50); pose.scale(145, 145, -145);
                pose.mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI + .35F));
            }
            draw.render(pose); mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch(); view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); Lighting.setupFor3DItems();
            mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    @FunctionalInterface private interface Draw { void render(PoseStack pose); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
