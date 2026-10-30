package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Compare the carrying skin and actual armor while their vertices reach the GPU. @author NingMeng123 */
final class GemBoxArmorAlignmentAudit {
    private static final double ACTOR_MATRIX_TOLERANCE = 1E-6D;
    private static final int ACTOR_RASTER_PIXEL_BUDGET = 8, ACTOR_RASTER_CHANNEL_BUDGET = 16;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] HAND_FIELDS = {"mainHandItem", "offHandItem", "mainHandHeight", "oMainHandHeight", "offHandHeight", "oOffHandHeight"};
    private GemBoxArmorAlignmentAudit() {}

    static JsonObject capture(Minecraft mc, ItemStack box, Path folder) throws Exception {
        check(ModList.get().isLoaded("ismah"), "The armored-hand regression requires the actual ISMAH mod");
        var armorResource = mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("optifine/cem/outer_armor.jem"));
        check(armorResource.isPresent() && armorResource.get().sourcePackId().contains("Traben"),
                "The regression requires the actual enabled Traben 3D armor model and its scale animation");
        Files.createDirectories(folder);
        PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
        HumanoidModel<?>[] armor = armorModels(renderer);
        check(armor[1].rightArm.getAllParts().count() > 2, "The real armor root must contain the resource pack's custom arm geometry");
        ItemStack[] equipped = new ItemStack[ARMOR.length];
        for (int i = 0; i < ARMOR.length; i++) equipped[i] = mc.player.getItemBySlot(ARMOR[i]);
        Object[] nativeHands = new Object[HAND_FIELDS.length];
        for (int i = 0; i < HAND_FIELDS.length; i++) nativeHands[i] = handField(HAND_FIELDS[i]).get(mc.gameRenderer.itemInHandRenderer);
        JsonObject result = new JsonObject(); JsonArray samples = new JsonArray(); result.add("samples", samples);
        result.addProperty("armor_source", armorResource.get().sourcePackId());
        result.addProperty("scope", "Live native skin/armor vertex emission, full dispatcher and GameRenderer hand entry, actual ISMAH and Traben geometry");
        result.addProperty("actor_matrix_absolute_tolerance", ACTOR_MATRIX_TOLERANCE);
        result.addProperty("actor_structure_visibility_and_skip_draw_exact", true);
        result.addProperty("actor_800x600_raster_max_changed_pixels", ACTOR_RASTER_PIXEL_BUDGET);
        result.addProperty("actor_raster_max_channel_delta", ACTOR_RASTER_CHANNEL_BUDGET);
        try (GemBoxMovementAudit.State movement = new GemBoxMovementAudit.State(mc)) {
            movement.stage(GemBoxMovementAudit.Mode.IDLE, 0);
            for (String material : List.of("iron", "mithril")) {
                // Touch'n Motion animates an observed player's armor-item changes.
                // Each reference actor starts already equipped, so its before/after
                // pair tests model borrowing instead of a legitimate donning gesture.
                RemotePlayer teammate = teammate(mc, material);
                try {
                    check(mc.getEntityRenderDispatcher().getRenderer(teammate) == renderer,
                            "The full-armor switching fixture must reuse the same real skin and armor renderer");
                    equip(mc.player, material);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    stageTeammate(teammate);
                    JsonObject teammateBefore = new JsonObject();
                    result.add(material + "_teammate_before", teammateBefore);
                    String beforePixels, beforePng;
                    try (NativeImage image = frame(mc, teammate, renderer.getModel(), armor, false, false, teammateBefore)) {
                        Path png = folder.resolve("gem-box-armor-alignment-" + material + "-teammate-before.png");
                        image.writeToFile(png);
                        beforePixels = pixelHash(image); beforePng = fileHash(png);
                        teammateBefore.addProperty("pixel_sha256", beforePixels); teammateBefore.addProperty("png_sha256", beforePng);
                    }
                    for (HumanoidArm main : HumanoidArm.values()) for (InteractionHand hand : InteractionHand.values()) {
                        mc.player.setMainArm(main);
                        for (int elapsed : new int[]{-1, 24, 52}) {
                            ItemStack held = box.copy();
                            if (elapsed >= 0) held.set(ExtremeGear.GEM_BOX_OPEN_STARTED.get(), mc.level.getGameTime() - elapsed);
                            mc.player.setItemInHand(hand, held);
                            mc.player.setItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            check(GemBoxArmPose.active(mc.player), "The armored fixture must actually carry the box");
                            for (boolean first : new boolean[]{false, true}) {
                                mc.options.setCameraType(first ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
                                stageHands(mc);
                                JsonObject facts = new JsonObject(); samples.add(facts);
                                facts.addProperty("full_armor_material", material);
                                facts.addProperty("main_arm", main.name()); facts.addProperty("carrying_hand", hand.name());
                                facts.addProperty("elapsed_ticks", elapsed); facts.addProperty("first_person", first);
                                String name = material + "-" + main.name().toLowerCase() + "-" + hand.name().toLowerCase() + "-" + elapsed + (first ? "-fps" : "-third");
                                try (NativeImage image = frame(mc, mc.player, renderer.getModel(), armor, first, true, facts)) {
                                    Path png = folder.resolve("gem-box-armor-alignment-" + name + ".png");
                                    image.writeToFile(png); facts.addProperty("preview", png.toString());
                                }
                            }
                        }
                    }
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    stageTeammate(teammate);
                    JsonObject teammateAfter = new JsonObject();
                    result.add(material + "_teammate_after", teammateAfter);
                    try (NativeImage image = frame(mc, teammate, renderer.getModel(), armor, false, false, teammateAfter)) {
                        Path png = folder.resolve("gem-box-armor-alignment-" + material + "-teammate-after.png");
                        image.writeToFile(png);
                        String afterPixels = pixelHash(image), afterPng = fileHash(png);
                        teammateAfter.addProperty("pixel_sha256", afterPixels); teammateAfter.addProperty("png_sha256", afterPng);
                        JsonObject raster = imageDifference(folder.resolve("gem-box-armor-alignment-" + material + "-teammate-before.png"), image);
                        raster.addProperty("rgba_sha256_identical", beforePixels.equals(afterPixels));
                        raster.addProperty("png_sha256_identical", beforePng.equals(afterPng));
                        result.add(material + "_actor_raster_difference", raster);
                        check(raster.get("changed_pixels").getAsInt() <= ACTOR_RASTER_PIXEL_BUDGET
                                        && raster.get("max_channel_delta").getAsInt() <= ACTOR_RASTER_CHANNEL_BUDGET,
                                "The ordinary teammate may only differ within the explicit subpixel raster budget: " + material + "; " + raster);
                    }
                    check(teammateBefore.get("actor_inputs_before_draw").equals(teammateAfter.get("actor_inputs_before_draw")),
                            "The ordinary teammate's complete reference inputs must remain exactly fixed");
                    check(teammateBefore.get("outer_pose_before_dispatch").equals(teammateAfter.get("outer_pose_before_dispatch")),
                            "The two ordinary teammate draws must use the identical outer projection pose");
                    assertActor(teammateBefore.get("draw_time_model_parts"), teammateAfter.get("draw_time_model_parts"), folder, material);
                    result.addProperty(material + "_actor_switch_preserves_drawn_pose", true);
                    result.addProperty(material + "_actor_switch_raster_within_roundoff_budget", true);
                } finally {
                    teammate.discard();
                }
            }
            result.add("strict_shared_model_isolation", GemBoxModelIsolationAudit.capture(mc, box, folder));
            result.addProperty("status", "passed_live_carrier_skin_and_armor_alignment");
            return result;
        } finally {
            Files.writeString(folder.resolve("gem-box-armor-alignment-progress.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result));
            for (int i = 0; i < ARMOR.length; i++) mc.player.setItemSlot(ARMOR[i], equipped[i]);
            for (int i = 0; i < HAND_FIELDS.length; i++) handField(HAND_FIELDS[i]).set(mc.gameRenderer.itemInHandRenderer, nativeHands[i]);
        }
    }

    private static NativeImage frame(Minecraft mc, AbstractClientPlayer actor, PlayerModel<?> skin, HumanoidModel<?>[] armor,
            boolean first, boolean carrying, JsonObject facts) throws Exception {
        TextureTarget target = new TextureTarget(800, 600, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix()); VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        facts.add("actor_inputs_before_draw", actorInputs(actor));
        try (ByteBufferBuilder memory = new ByteBufferBuilder(786432)) {
            ObservedBuffers buffers = new ObservedBuffers(memory, actor, skin, armor, first, carrying);
            target.setClearColor(.063F, .094F, .125F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(first ? new Matrix4f().setPerspective((float)Math.toRadians(70), 4F / 3F, .05F, 100)
                    : new Matrix4f().setOrtho(0, 800, 600, 0, -1000, 1000), first ? VertexSorting.DISTANCE_TO_ORIGIN : VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1); Lighting.setupForEntityInInventory();
            PoseStack pose = new PoseStack();
            try {
                if (first) GemBoxOuterHandAudit.render(mc, buffers);
                else {
                    pose.translate(400, 565, 50); pose.scale(240, 240, -240);
                    pose.mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI + .35F));
                    JsonArray outerPose = new JsonArray();
                    for (float value : pose.last().pose().get(new float[16])) outerPose.add(value);
                    facts.add("outer_pose_before_dispatch", outerPose);
                    GemBoxDrawPoseAudit.renderThroughDispatcher(mc, actor, pose, buffers);
                }
                buffers.endBatch(); mc.renderBuffers().bufferSource().endBatch();
                buffers.verify(facts);
            } finally {
                facts.add("draw_time_model_parts", buffers.drawParts);
                facts.add("actor_inputs_after_draw", actorInputs(actor));
            }
            return Screenshot.takeScreenshot(target);
        } finally {
            view.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(projection, sorting);
            Lighting.setupFor3DItems(); mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static final class ObservedBuffers extends MultiBufferSource.BufferSource {
        private final AbstractClientPlayer actor;
        private final PlayerModel<?> skin;
        private final HumanoidModel<?>[] armor;
        private final boolean first;
        private final boolean carrying;
        private final Map<HumanoidArm, float[]> skinAtDraw = new EnumMap<>(HumanoidArm.class);
        private final Map<HumanoidArm, Integer> armorVertices = new EnumMap<>(HumanoidArm.class);
        private final JsonObject drawParts = new JsonObject();
        private final GemBoxArmorGeometry geometry = new GemBoxArmorGeometry();
        private int thirdArmorVertices;
        private int innerLegVertices, outerLegVertices;
        private boolean inflationSeen;
        private ObservedBuffers(ByteBufferBuilder memory, AbstractClientPlayer actor, PlayerModel<?> skin,
                HumanoidModel<?>[] armor, boolean first, boolean carrying) {
            super(memory, new LinkedHashMap<>()); this.actor = actor; this.skin = skin; this.armor = armor;
            this.first = first; this.carrying = carrying;
        }
        @Override public VertexConsumer getBuffer(RenderType type) {
            VertexConsumer delegate = super.getBuffer(type);
            String description = type.toString();
            boolean isSkin = description.contains(actor.getSkin().texture().toString());
            boolean isArmor = description.contains("textures/models/armor/")
                    && (description.contains("iron_layer_") || description.contains("mithril_layer_"));
            boolean inner = description.contains("_layer_2.png");
            boolean solidSkin = isSkin && description.contains("entity_solid");
            if (!isSkin && !isArmor) return delegate;
            HumanoidArm[] selected = {null};
            StackWalker.getInstance().forEach(frame -> {
                if (frame.getMethodName().equals("renderRightHand")) selected[0] = HumanoidArm.RIGHT;
                if (frame.getMethodName().equals("renderLeftHand")) selected[0] = HumanoidArm.LEFT;
            });
            return new VertexConsumer() {
                private boolean recorded;
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    if (isSkin && selected[0] != null) skinAtDraw.put(selected[0], transform(arm(skin, selected[0])));
                    if (solidSkin && selected[0] != null) geometry.skin(selected[0], x, y, z);
                    if (!recorded) {
                        if (isSkin) recordParts("skin", skin);
                        if (isArmor) recordParts(inner ? "inner_armor" : "outer_armor", armor[inner ? 0 : 1]);
                        recorded = true;
                    }
                    if (isArmor) {
                        observeArmor(selected[0], inner);
                        if (first && selected[0] != null) geometry.armor(selected[0], x, y, z);
                    }
                    delegate.addVertex(x, y, z); return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r,g,b,a); return this; }
                @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u,v); return this; }
                @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u,v); return this; }
                @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u,v); return this; }
                @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x,y,z); return this; }
            };
        }
        private void observeArmor(HumanoidArm selected, boolean inner) {
            HumanoidModel<?> model = armor[inner ? 0 : 1];
            if (first) {
                check(selected != null, "The actual armored-hand vertices must belong to one native hand draw");
                check(skinAtDraw.containsKey(selected), "The native supporting skin hand must draw before its ISMAH armor");
                equal(skinAtDraw.get(selected), transform(arm(model, selected)), "First-person " + selected + " armor must use its supporting skin hand's exact drawn transform");
                for (ModelPart hidden : List.of(model.head, model.hat, model.body, model.rightLeg, model.leftLeg,
                        arm(model, selected == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT)))
                    check(hidden.getAllParts().noneMatch(part -> part.visible), "A first-person supporting armor pass cannot emit the torso, legs or opposite arm");
                check(arm(model, selected).visible, "The selected native armor arm must remain visible");
                armorVertices.merge(selected, 1, Integer::sum);
            } else {
                if (carrying) {
                    for (HumanoidArm side : HumanoidArm.values())
                        equal(transform(arm(skin, side)), transform(arm(model, side)), "Third-person " + side + " armor must stay attached to the final carrying skin pose during vertex emission");
                    equal(transform(skin.rightLeg), transform(model.rightLeg), "Third-person right armor leg must match the drawn skin leg");
                    equal(transform(skin.leftLeg), transform(model.leftLeg), "Third-person left armor leg must match the drawn skin leg");
                } else {
                    check(actor != Minecraft.getInstance().player && !GemBoxArmPose.active(actor)
                                    && GemBoxArmPose.carryingHand(actor) == null && actor.getMainHandItem().is(Items.STICK)
                                    && GemBoxArmPose.savedSupportAnchor(skin) == null,
                            "The ordinary teammate must render its own stick pose without any carrier state or support anchor");
                    visibleJoint("head", skin.head, model.head);
                    visibleJoint("hat", skin.hat, model.hat);
                    visibleJoint("body", skin.body, model.body);
                    visibleJoint("right arm", skin.rightArm, model.rightArm);
                    visibleJoint("left arm", skin.leftArm, model.leftArm);
                    visibleJoint("right leg", skin.rightLeg, model.rightLeg);
                    visibleJoint("left leg", skin.leftLeg, model.leftLeg);
                }
                if (model.rightLeg.visible || model.leftLeg.visible) {
                    check(model.rightLeg.visible && model.leftLeg.visible, "A complete leggings or boots pass must retain both leg roots");
                    visibleMesh(model.rightLeg, "right armor leg"); visibleMesh(model.leftLeg, "left armor leg");
                    if (inner) innerLegVertices++; else outerLegVertices++;
                }
                thirdArmorVertices++;
            }
            inflationSeen |= model.rightArm.getAllParts().anyMatch(part -> part.xScale > 1.3F)
                    || model.leftArm.getAllParts().anyMatch(part -> part.xScale > 1.3F);
        }
        private void verify(JsonObject facts) {
            if (first) for (HumanoidArm side : HumanoidArm.values()) {
                int count = armorVertices.getOrDefault(side, 0);
                facts.addProperty(side.name().toLowerCase() + "_armor_vertices", count);
                check(count >= 24, "The real ISMAH pass must submit measurable " + side + " armor geometry");
            } else {
                facts.addProperty("armor_vertices", thirdArmorVertices);
                facts.addProperty("inner_armor_leg_vertices", innerLegVertices);
                facts.addProperty("outer_armor_leg_vertices", outerLegVertices);
                check(thirdArmorVertices >= 48, "The actual third-person chest armor geometry must reach the GPU");
                check(innerLegVertices >= 24 && outerLegVertices >= 24,
                        "Both the actual leggings and boot armor models must emit full-set leg geometry");
            }
            if (first) facts.add("drawn_skin_armor_geometry", geometry.verify());
            check(inflationSeen, "The 3D armor's child-part inflation animation must remain active");
            facts.add("draw_time_model_parts", drawParts);
            facts.addProperty("armor_child_inflation_preserved", true);
            facts.addProperty("draw_time_arm_transforms_match", true);
            facts.addProperty("draw_time_leg_transforms_match", !first);
            facts.addProperty("ordinary_non_carrier_state_checked", !first && !carrying);
            facts.addProperty("ordinary_visible_joint_coupling_checked", !first && !carrying);
        }
        private void recordParts(String prefix, HumanoidModel<?> model) {
            String[] names = {"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
            ModelPart[] parts = {model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
            for (int i = 0; i < parts.length; i++) {
                JsonArray tree = new JsonArray();
                parts[i].getAllParts().forEach(part -> {
                    JsonObject value = new JsonObject(); JsonArray matrix = new JsonArray();
                    for (float number : transform(part)) matrix.add(number);
                    value.add("local_transform", matrix);
                    value.addProperty("visible", part.visible); value.addProperty("skip_draw", part.skipDraw);
                    tree.add(value);
                });
                drawParts.add(prefix + "/" + names[i], tree);
            }
            try {
                Class<?> api = Class.forName("traben.entity_model_features.models.IEMFModel");
                if (api.isInstance(model) && api.getMethod("emf$getEMFRootModel").invoke(model) instanceof ModelPart root) {
                    JsonArray matrix = new JsonArray();
                    for (float value : transform(root)) matrix.add(value);
                    JsonObject pose = new JsonObject(); pose.add("local_transform", matrix);
                    pose.addProperty("visible", root.visible); pose.addProperty("skip_draw", root.skipDraw);
                    drawParts.add(prefix + "/emf_root", pose);
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("The real EMF regression must expose its outer root pose", failure);
            }
        }
    }

    private static ModelPart arm(HumanoidModel<?> model, HumanoidArm side) { return side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm; }
    private static void visibleJoint(String name, ModelPart skin, ModelPart armor) {
        if (armor.visible) equal(transform(skin), transform(armor), "The ordinary teammate's visible " + name + " armor must match its own skin joint during vertex emission");
    }
    private static void visibleMesh(ModelPart root, String name) {
        check(root.visible && root.getAllParts().anyMatch(part -> part.visible && !part.skipDraw && !part.isEmpty()),
                "The actual " + name + " must retain visible custom mesh children after supporting-hand renders");
    }
    private static void assertActor(JsonElement before, JsonElement after, Path folder, String material) throws Exception {
        JsonObject differences = new JsonObject();
        differences(before, after, "", differences);
        Files.writeString(folder.resolve("gem-box-armor-actor-" + material + "-differences.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(differences));
        if (differences.size() == 0) return;
        throw new IllegalStateException("Alternating the fully " + material + " armored carrier and ordinary teammate must preserve the teammate's actual emitted player/armor geometry state; changed "
                + differences.size() + " fields: " + differences);
    }
    private static void differences(JsonElement before, JsonElement after, String path, JsonObject result) {
        if (before != null && before.equals(after)) return;
        // Repeated CEM matrix arithmetic may differ by a few float ULPs. Only
        // these normalized matrix entries receive tolerance; flags and shape stay exact.
        if (path.contains("/local_transform/") && before != null && after != null
                && before.isJsonPrimitive() && after.isJsonPrimitive()
                && before.getAsJsonPrimitive().isNumber() && after.getAsJsonPrimitive().isNumber()
                && Math.abs(before.getAsDouble() - after.getAsDouble()) <= ACTOR_MATRIX_TOLERANCE) return;
        if (before != null && after != null && before.isJsonObject() && after.isJsonObject()) {
            var keys = new java.util.LinkedHashSet<>(before.getAsJsonObject().keySet()); keys.addAll(after.getAsJsonObject().keySet());
            for (String key : keys) differences(before.getAsJsonObject().get(key), after.getAsJsonObject().get(key), path + "/" + key, result);
        } else if (before != null && after != null && before.isJsonArray() && after.isJsonArray()
                && before.getAsJsonArray().size() == after.getAsJsonArray().size()) {
            for (int i = 0; i < before.getAsJsonArray().size(); i++) differences(before.getAsJsonArray().get(i), after.getAsJsonArray().get(i), path + "/" + i, result);
        } else {
            JsonObject value = new JsonObject(); value.add("before", before); value.add("after", after); result.add(path, value);
        }
    }
    private static String fileHash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static JsonObject imageDifference(Path beforePng, NativeImage after) throws Exception {
        JsonObject result = new JsonObject(); int changed = 0, maxDelta = 0;
        int minX = after.getWidth(), minY = after.getHeight(), maxX = -1, maxY = -1;
        try (var input = Files.newInputStream(beforePng); NativeImage before = NativeImage.read(input)) {
            check(before.getWidth() == after.getWidth() && before.getHeight() == after.getHeight(),
                    "The actor reference images must have identical framebuffer dimensions");
            for (int y = 0; y < after.getHeight(); y++) for (int x = 0; x < after.getWidth(); x++) {
                int a = before.getPixelRGBA(x, y), b = after.getPixelRGBA(x, y);
                if (a == b) continue;
                changed++; minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                for (int shift = 0; shift < 32; shift += 8)
                    maxDelta = Math.max(maxDelta, Math.abs((a >>> shift & 255) - (b >>> shift & 255)));
            }
        }
        result.addProperty("changed_pixels", changed); result.addProperty("max_channel_delta", maxDelta);
        JsonArray bounds = new JsonArray();
        if (changed > 0) { bounds.add(minX); bounds.add(minY); bounds.add(maxX); bounds.add(maxY); }
        result.add("changed_pixel_bounds", bounds);
        return result;
    }
    private static String pixelHash(NativeImage image) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgba = image.getPixelRGBA(x, y);
            hash.update((byte)rgba); hash.update((byte)(rgba >>> 8)); hash.update((byte)(rgba >>> 16)); hash.update((byte)(rgba >>> 24));
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    private static float[] transform(ModelPart part) {
        PoseStack stack = new PoseStack(); part.translateAndRotate(stack); return stack.last().pose().get(new float[16]);
    }
    private static void equal(float[] expected, float[] actual, String message) {
        for (int i = 0; i < expected.length; i++) check(Math.abs(expected[i] - actual[i]) < .00001F,
                message + " (matrix[" + i + "] skin=" + expected[i] + ", armor=" + actual[i] + ")");
    }
    private static void equip(AbstractClientPlayer player, String material) {
        String[] names = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < ARMOR.length; i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(material.equals("iron") ? "minecraft" : "mite", material + "_" + names[i]);
            check(BuiltInRegistries.ITEM.containsKey(id), "The full-armor fixture needs the real registered item " + id);
            player.setItemSlot(ARMOR[i], new ItemStack(BuiltInRegistries.ITEM.get(id)));
        }
    }
    private static void stageTeammate(RemotePlayer player) throws Exception {
        player.xo = player.xOld = player.getX(); player.yo = player.yOld = player.getY(); player.zo = player.zOld = player.getZ();
        player.setYRot(0); player.yRotO = 0; player.setXRot(0); player.xRotO = 0;
        player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = 0;
        player.attackAnim = player.oAttackAnim = 0; player.swinging = false;
        player.tickCount = 120; player.hurtTime = player.deathTime = 0; player.fallDistance = 0;
        player.setDeltaMovement(0, 0, 0); player.setOnGround(true); player.setSprinting(false);
        player.setShiftKeyDown(false); player.setSwimming(false); player.setPose(Pose.STANDING);
        player.getAbilities().flying = false; player.setMainArm(HumanoidArm.RIGHT);
        player.getRandom().setSeed(0x4E696E674D656E67L);
        for (String name : List.of("speedOld", "speed", "position")) {
            Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); field.setFloat(player.walkAnimation, 0);
        }
    }
    private static RemotePlayer teammate(Minecraft mc, String material) throws Exception {
        UUID uuid = UUID.nameUUIDFromBytes(("modern_ite:gem_box_armor_reference_" + material).getBytes(StandardCharsets.UTF_8));
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(uuid, "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return mc.player.getSkin(); }
        };
        player.moveTo(mc.player.position());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        equip(player, material);
        stageTeammate(player);
        mc.level.addEntity(player);
        return player;
    }
    private static JsonObject actorInputs(AbstractClientPlayer player) {
        JsonObject facts = new JsonObject();
        facts.addProperty("uuid", player.getUUID().toString());
        facts.addProperty("age", player.tickCount); facts.addProperty("pose", player.getPose().name());
        facts.addProperty("position", player.position().toString()); facts.addProperty("velocity", player.getDeltaMovement().toString());
        facts.addProperty("old_position", player.xo + "/" + player.yo + "/" + player.zo);
        facts.addProperty("render_old_position", player.xOld + "/" + player.yOld + "/" + player.zOld);
        facts.addProperty("yaw", player.getYRot() + "/" + player.yRotO); facts.addProperty("pitch", player.getXRot() + "/" + player.xRotO);
        facts.addProperty("body_yaw", player.yBodyRot + "/" + player.yBodyRotO); facts.addProperty("head_yaw", player.yHeadRot + "/" + player.yHeadRotO);
        facts.addProperty("grounded", player.onGround()); facts.addProperty("sprinting", player.isSprinting()); facts.addProperty("crouching", player.isCrouching());
        facts.addProperty("main_arm", player.getMainArm().name()); facts.addProperty("walk_speed", player.walkAnimation.speed(1));
        facts.addProperty("walk_position", player.walkAnimation.position(1)); facts.addProperty("attack", player.getAttackAnim(1));
        facts.addProperty("main_item", player.getMainHandItem().toString()); facts.addProperty("off_item", player.getOffhandItem().toString());
        return facts;
    }
    private static HumanoidModel<?>[] armorModels(PlayerRenderer renderer) throws Exception {
        Field layers = LivingEntityRenderer.class.getDeclaredField("layers"); layers.setAccessible(true);
        for (Object layer : (Iterable<?>)layers.get(renderer)) if (layer instanceof HumanoidArmorLayer<?, ?, ?>) {
            HumanoidModel<?>[] models = new HumanoidModel<?>[2];
            String[] names = {"innerModel", "outerModel"};
            for (int i = 0; i < models.length; i++) {
                Field model = HumanoidArmorLayer.class.getDeclaredField(names[i]); model.setAccessible(true);
                models[i] = (HumanoidModel<?>)model.get(layer);
            }
            return models;
        }
        throw new IllegalStateException("The real player renderer must own a native armor layer");
    }
    private static Field handField(String name) throws Exception { Field field = ItemInHandRenderer.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void stageHands(Minecraft mc) throws Exception {
        handField(HAND_FIELDS[0]).set(mc.gameRenderer.itemInHandRenderer, mc.player.getMainHandItem());
        handField(HAND_FIELDS[1]).set(mc.gameRenderer.itemInHandRenderer, mc.player.getOffhandItem());
        for (int i = 2; i < HAND_FIELDS.length; i++) handField(HAND_FIELDS[i]).setFloat(mc.gameRenderer.itemInHandRenderer, 1F);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
