package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemBoxItem;
import io.github.ningmeng123.modernite.gear.GemType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Real baked-model, hand-event and player-renderer GPU samples. @author NingMeng123 */
final class GemBoxCarryClientAudit {
    private static final int WIDTH = 480, HEIGHT = 360;
    private GemBoxCarryClientAudit() {}

    static JsonObject capture(Minecraft mc, ItemStack box, Path folder) throws Exception {
        try (GemBoxMovementAudit.State movement = new GemBoxMovementAudit.State(mc)) {
            movement.stage(GemBoxMovementAudit.Mode.IDLE, 0);
            JsonObject result = captureStanding(mc, box, folder);
            result.add("movement_visibility", GemBoxMovementAudit.capture(mc, box, folder));
            return result;
        }
    }

    private static JsonObject captureStanding(Minecraft mc, ItemStack box, Path folder) throws Exception {
        Files.createDirectories(folder);
        var player = mc.player;
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        HumanoidArm mainArm = player.getMainArm();
        int age = player.tickCount;
        float attack = player.attackAnim, oldAttack = player.oAttackAnim;
        float body = player.yBodyRot, oldBody = player.yBodyRotO;
        float head = player.yHeadRot, oldHead = player.yHeadRotO;
        float pitch = player.getXRot(), oldPitch = player.xRotO;
        boolean swinging = player.swinging;
        SavedWalk walk = new SavedWalk(player.walkAnimation);
        JsonObject result = new JsonObject();
        result.addProperty("scope", "Native baked quads, both RenderHandEvent calls and complete PlayerRenderer with skin and item GPU buffers");
        result.addProperty("actual_opengl_rendering", true);
        result.addProperty("shader_pack_visual_validation", false);
        JsonArray samples = new JsonArray(); result.add("samples", samples);
        int quads = geometry(mc, box, result);
        sound(mc, result);
        result.add("shared_player_armor_isolation", GemBoxModelIsolationAudit.capture(mc, box, folder));
        result.add("draw_time_arm_pose", GemBoxDrawPoseAudit.capture(mc, box, folder));
        try (NativeImage sheet = new NativeImage(WIDTH * 4, HEIGHT * 4, false)) {
            player.attackAnim = player.oAttackAnim = 0;
            player.swinging = false;
            player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = 0;
            player.setXRot(0); player.xRotO = 0;
            player.tickCount = 120;
            try (NativeImage gui = gui(mc, box)) {
                gui.writeToFile(folder.resolve("gem-box-gui.png"));
                check(foreground(gui) > 1500, "The native inventory renderer must show the complete 3D chest");
                result.addProperty("gui_foreground_pixels", foreground(gui));
            }
            int row = 0;
            for (HumanoidArm dominant : HumanoidArm.values()) {
                player.setMainArm(dominant);
                for (InteractionHand hand : InteractionHand.values()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    walk.stage(player.walkAnimation, false);
                    player.tickCount = 120;
                    try (NativeImage emptyBefore = third(mc, null, null)) {
                        player.setItemInHand(hand, box.copy());
                        JsonObject sample = new JsonObject(); samples.add(sample);
                        sample.addProperty("main_arm", dominant.name()); sample.addProperty("hand", hand.name());
                        String name = dominant.name().toLowerCase() + "-" + hand.name().toLowerCase();
                        ObservedBuffers firstBuffers = new ObservedBuffers(mc);
                        ObservedBuffers thirdBuffers = new ObservedBuffers(mc);
                        try (NativeImage firstIdle = first(mc, 0, firstBuffers, sample);
                                NativeImage thirdIdle = third(mc, thirdBuffers, sample)) {
                            sample.addProperty("first_chest_vertices", firstBuffers.atlasVertices);
                            sample.addProperty("third_chest_vertices", thirdBuffers.atlasVertices);
                            save(firstIdle, folder, name + "-first-idle", sheet, row, 0);
                            save(thirdIdle, folder, name + "-third-idle", sheet, row, 2);
                            check(firstBuffers.rightHand && firstBuffers.leftHand,
                                    "The real native right and left skin hands must both render: " + name);
                            check(firstBuffers.atlasVertices == quads * 4,
                                    "The two native hand events draw exactly one 3D chest: " + name + " (" + firstBuffers.atlasVertices + " vs " + quads * 4 + ")");
                            check(thirdBuffers.atlasVertices == quads * 4,
                                    "The complete player renderer draws exactly one chest: " + name + " (" + thirdBuffers.atlasVertices + " vs " + quads * 4 + ")");
                            sample.addProperty("third_arm_pose_oracle", "draw_time_arm_pose: actual emitted limb/sleeve geometry, not restored model fields");
                            player.tickCount = 134;
                            try (NativeImage breathed = first(mc, 0, new ObservedBuffers(mc), null)) {
                                int delta = difference(firstIdle, breathed);
                                check(delta > 10, "The idle lifting/breathing animation must reach the GPU: " + name);
                                sample.addProperty("first_idle_animation_changed_pixels", delta);
                                save(breathed, folder, name + "-first-breath", sheet, row, 1);
                            }
                            player.tickCount = 120;
                            walk.stage(player.walkAnimation, true);
                            try (NativeImage moving = third(mc, null, null)) {
                                int delta = difference(thirdIdle, moving);
                                check(delta > 20, "Walking must visibly move the native carry pose: " + name);
                                sample.addProperty("third_walking_changed_pixels", delta);
                                save(moving, folder, name + "-third-walking", sheet, row, 3);
                            }
                        }
                        walk.stage(player.walkAnimation, false);
                        player.tickCount = 120;
                        player.setItemInHand(hand, ItemStack.EMPTY);
                        try (NativeImage emptyAfter = third(mc, null, null)) {
                            check(difference(emptyBefore, emptyAfter) == 0,
                                    "Removing the box must restore both arms and all native item layers: " + name);
                        }
                    }
                    row++;
                }
            }
            player.setMainArm(HumanoidArm.RIGHT);
            player.setItemInHand(InteractionHand.MAIN_HAND, box.copy());
            player.setItemInHand(InteractionHand.OFF_HAND, box.copy());
            ObservedBuffers bothFirst = new ObservedBuffers(mc), bothThird = new ObservedBuffers(mc);
            try (NativeImage first = first(mc, 0, bothFirst, null); NativeImage third = third(mc, bothThird, null)) {
                check(bothFirst.atlasVertices == quads * 4 && bothThird.atlasVertices == quads * 4,
                        "A box in each hand still renders one central chest in each view");
                result.addProperty("both_hands_box_single_draw", true);
            }
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            try (NativeImage settled = first(mc, 0, new ObservedBuffers(mc), null);
                    NativeImage entering = first(mc, .75F, new ObservedBuffers(mc), null)) {
                int delta = difference(settled, entering);
                check(delta > 250, "The native equip progress must visibly lift the chest and both hands");
                result.addProperty("equip_lift_changed_pixels", delta);
                settled.writeToFile(folder.resolve("gem-box-lift-settled.png"));
                entering.writeToFile(folder.resolve("gem-box-lift-entering.png"));
            }
            result.add("lid_opening", opening(mc, box, folder, quads));
            result.add("animation_frames", animationFrames(mc, box, folder));
            result.add("native_first_person_pipeline", GemBoxNativeHandAudit.capture(mc, box, folder));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            JsonObject ordinary = new JsonObject();
            ObservedBuffers ordinaryBuffers = new ObservedBuffers(mc);
            try (NativeImage pixels = first(mc, 0, ordinaryBuffers, ordinary)) {
                check(foreground(pixels) == 0 && ordinaryBuffers.atlasVertices == 0,
                        "The carry event must not draw or cancel an unrelated ordinary item");
            }
            result.add("non_box_event_control", ordinary);
            Path preview = folder.resolve("gem-box-carry-poses.png"); sheet.writeToFile(preview);
            result.addProperty("preview", preview.toString());
            result.addProperty("gui_preview", folder.resolve("gem-box-gui.png").toString());
            result.addProperty("columns", "first idle; first idle breath; third idle; third walking");
            result.addProperty("rows", "left main/main hand; left main/off hand; right main/main hand; right main/off hand");
            result.addProperty("status", "passed_actual_gpu_rendering");
            return result;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off);
            player.setMainArm(mainArm); player.tickCount = age;
            player.attackAnim = attack; player.oAttackAnim = oldAttack; player.swinging = swinging;
            player.yBodyRot = body; player.yBodyRotO = oldBody; player.yHeadRot = head; player.yHeadRotO = oldHead;
            player.setXRot(pitch); player.xRotO = oldPitch; walk.restore(player.walkAnimation);
        }
    }

    private static int geometry(Minecraft mc, ItemStack box, JsonObject result) {
        var model = mc.getItemRenderer().getModel(box, mc.level, mc.player, 0);
        List<BakedQuad> quads = quads(model);
        float[] low = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] high = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (BakedQuad quad : quads) {
            check(!quad.getSprite().contents().name().getPath().contains("missingno"), "Every real baked chest face must resolve its texture");
            int[] vertices = quad.getVertices();
            for (int vertex = 0; vertex < vertices.length; vertex += 8) for (int axis = 0; axis < 3; axis++) {
                float coordinate = Float.intBitsToFloat(vertices[vertex + axis]);
                low[axis] = Math.min(low[axis], coordinate); high[axis] = Math.max(high[axis], coordinate);
            }
        }
        check(model.isGui3d() && quads.size() >= 24, "The resolved native model must be a cuboid chest rather than a generated flat sprite");
        JsonObject geometry = new JsonObject(); geometry.addProperty("quads", quads.size());
        for (int axis = 0; axis < 3; axis++) {
            float extent = high[axis] - low[axis];
            check(extent > .3F, "The native baked chest must have visible volume on every axis");
            geometry.addProperty(new String[]{"width", "height", "depth"}[axis], extent);
        }
        result.add("baked_model", geometry);
        return quads.size();
    }

    private static List<BakedQuad> quads(BakedModel model) {
        List<BakedQuad> quads = new ArrayList<>();
        quads.addAll(model.getQuads(null, null, RandomSource.create(196)));
        for (Direction side : Direction.values()) quads.addAll(model.getQuads(null, side, RandomSource.create(196)));
        return quads;
    }

    private static JsonObject opening(Minecraft mc, ItemStack box, Path folder, int wholeQuads) throws Exception {
        var bodyModel = mc.getModelManager().getModel(GemBoxModelRendering.BODY_MODEL);
        var lidModel = mc.getModelManager().getModel(GemBoxModelRendering.LID_MODEL);
        check(bodyModel != mc.getModelManager().getMissingModel() && lidModel != mc.getModelManager().getMissingModel(),
                "The actual model manager must resolve both registered chest animation parts");
        List<BakedQuad> body = quads(bodyModel), lid = quads(lidModel);
        check(body.size() + lid.size() == wholeQuads, "The animated parts preserve exactly the complete closed chest geometry");
        // The mouth centre must expose the internal floor, rather than another solid upper face.
        float bodyTop = Float.NEGATIVE_INFINITY, centralFloor = Float.NEGATIVE_INFINITY;
        for (BakedQuad quad : body) {
            int[] v = quad.getVertices();
            float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (int offset = 0; offset < v.length; offset += 8) {
                float x = Float.intBitsToFloat(v[offset]), y = Float.intBitsToFloat(v[offset + 1]), z = Float.intBitsToFloat(v[offset + 2]);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                bodyTop = Math.max(bodyTop, y);
            }
            if (quad.getDirection() == Direction.UP && maxY - minY < .0001F
                    && minX < .5F && maxX > .5F && minZ < .5F && maxZ > .5F)
                centralFloor = Math.max(centralFloor, maxY);
        }
        check(Float.isFinite(centralFloor) && bodyTop - centralFloor > .2F,
                "The real baked box body has an open mouth over a deep internal floor");
        JsonObject result = new JsonObject();
        result.addProperty("body_quads", body.size()); result.addProperty("lid_quads", lid.size());
        result.addProperty("baked_body_top", bodyTop); result.addProperty("baked_cavity_floor", centralFloor);
        result.addProperty("baked_cavity_depth", bodyTop - centralFloor);
        JsonArray samples = new JsonArray(); result.add("samples", samples);
        var player = mc.player;
        ItemStack savedMain = player.getMainHandItem(), savedOff = player.getOffhandItem();
        HumanoidArm savedArm = player.getMainArm();
        NativeImage redFirst = null, redThird = null;
        try (NativeImage sheet = new NativeImage(WIDTH * 8, HEIGHT * 16, false)) {
            int group = 0;
            for (GemType selected : new GemType[]{GemType.DAMAGE, GemType.FIREPROOF}) {
                ItemStack expectedGem = ExtremeGear.gem(selected, 1);
                int gemQuads = quads(mc.getItemRenderer().getModel(expectedGem, mc.level, player, 0)).size();
                check(gemQuads > 0, "The selected actual reward resolves its native item geometry");
                for (HumanoidArm dominant : HumanoidArm.values()) {
                    player.setMainArm(dominant);
                    for (InteractionHand hand : InteractionHand.values()) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                        ItemStack animated = box.copy();
                        animated.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
                        animated.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
                        player.setItemInHand(hand, animated);
                        String name = selected.name().toLowerCase() + "-" + dominant.name().toLowerCase() + "-" + hand.name().toLowerCase();
                        JsonObject sample = new JsonObject(); samples.add(sample);
                        sample.addProperty("gem", selected.name()); sample.addProperty("main_arm", dominant.name()); sample.addProperty("hand", hand.name());
                        sample.addProperty("gem_baked_quads", gemQuads);
                        JsonArray phases = new JsonArray(); sample.add("phases", phases);
                        try (NativeImage closedFirst = first(mc, 0, new ObservedBuffers(mc), null);
                                NativeImage closedThird = third(mc, null, null)) {
                            NativeImage priorFirst = null, priorThird = null;
                            float previousAngle = -1F;
                            float[] priorSkin = null;
                            int lidTransitions = 0;
                            try {
                                int column = 0;
                                for (int elapsed : new int[]{0, 8, 16, 24, 35, 40, 52, 55}) {
                                    animated.set(ExtremeGear.GEM_BOX_OPEN_STARTED.get(), mc.level.getGameTime() - elapsed);
                                    boolean revealed = elapsed >= GemBoxItem.REVEAL_TICK;
                                    if (revealed) animated.set(ExtremeGear.GEM_BOX_REVEALED_GEM.get(), selected.ordinal() + 1);
                                    else animated.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
                                    ItemStack actualGem = GemBoxItem.revealedGem(animated);
                                    check(revealed ? ItemStack.isSameItemSameComponents(expectedGem, actualGem) : actualGem.isEmpty(),
                                            "The synchronized selected reward is absent before reveal and matches afterwards");
                                    float progress = GemBoxItem.openingProgress(animated, mc.level, mc.getTimer().getGameTimeDeltaPartialTick(true));
                                    float angle = GemBoxModelRendering.lidAngle(progress);
                                    JsonObject phase = new JsonObject(); phases.add(phase);
                                    phase.addProperty("elapsed_ticks", elapsed); phase.addProperty("progress", progress);
                                    phase.addProperty("lid_degrees", angle); phase.addProperty("reward_revealed", revealed);
                                    ObservedBuffers firstBuffers = new ObservedBuffers(mc), thirdBuffers = new ObservedBuffers(mc);
                                    NativeImage first = first(mc, 0, firstBuffers, phase);
                                    NativeImage third = null;
                                    try {
                                        third = third(mc, thirdBuffers, phase);
                                        save(first, folder, "opening-" + name + "-" + elapsed + "-first", sheet, group * 2, column);
                                        save(third, folder, "opening-" + name + "-" + elapsed + "-third", sheet, group * 2 + 1, column);
                                        phase.addProperty("first_atlas_vertices", firstBuffers.atlasVertices);
                                        phase.addProperty("third_atlas_vertices", thirdBuffers.atlasVertices);
                                        int expectedVertices = (wholeQuads + (revealed ? gemQuads : 0)) * 4;
                                        check(firstBuffers.rightHand && firstBuffers.leftHand,
                                                "The two real hands remain visible throughout searching and taking the gem: " + name + "/" + elapsed);
                                        check(firstBuffers.atlasVertices == expectedVertices && thirdBuffers.atlasVertices == expectedVertices,
                                                "Each view draws one chest and exactly its selected revealed gem: " + name + "/" + elapsed
                                                        + " (" + firstBuffers.atlasVertices + ", " + thirdBuffers.atlasVertices + " vs " + expectedVertices + ")");
                                        check(angle >= previousAngle && (elapsed < 16 || angle > 95),
                                                "The lid opens monotonically and remains raised during searching and showing the gem");
                                        float[] skin = firstBuffers.rightSkinCenter();
                                        phase.addProperty("first_right_hand_x", skin[0]); phase.addProperty("first_right_hand_y", skin[1]); phase.addProperty("first_right_hand_z", skin[2]);
                                        phase.addProperty("third_arm_pose_oracle", "draw_time_arm_pose: actual emitted geometry at 16/24/35/40/52 ticks");
                                        if (priorFirst != null) {
                                            int firstDelta = difference(priorFirst, first), thirdDelta = difference(priorThird, third);
                                            phase.addProperty("first_changed_pixels", firstDelta); phase.addProperty("third_changed_pixels", thirdDelta);
                                            if (angle - previousAngle > 5F) {
                                                check(firstDelta > 100 && thirdDelta > 30,
                                                        "Actual lid movement must be visible in both GPU views: " + name + "/" + elapsed);
                                                lidTransitions++;
                                            }
                                            if (elapsed == 24 || elapsed == 40) {
                                                float firstHandMovement = vectorDistance(priorSkin, skin);
                                                phase.addProperty("first_right_hand_movement", firstHandMovement);
                                                check(firstHandMovement > .002F && firstDelta > 50 && thirdDelta > 20,
                                                        "The real right arm must search inside the open chest and then pull the gem out: " + name + "/" + elapsed);
                                            }
                                        }
                                        if (elapsed == 52 && dominant == HumanoidArm.RIGHT && hand == InteractionHand.MAIN_HAND) {
                                            if (selected == GemType.DAMAGE) {
                                                redFirst = copy(first); redThird = copy(third);
                                            } else {
                                                check(redFirst != null && redThird != null, "The red-gem native reference was sampled first");
                                                int firstColorDelta = difference(redFirst, first), thirdColorDelta = difference(redThird, third);
                                                check(firstColorDelta > 30 && thirdColorDelta > 10,
                                                        "The extracted red attack gem and blue fireproof gem must be visibly different real items");
                                                result.addProperty("selected_gem_first_view_changed_pixels", firstColorDelta);
                                                result.addProperty("selected_gem_third_view_changed_pixels", thirdColorDelta);
                                            }
                                        }
                                        if (priorFirst != null) priorFirst.close();
                                        if (priorThird != null) priorThird.close();
                                        priorFirst = first; priorThird = third; first = null; third = null;
                                        previousAngle = angle; priorSkin = skin;
                                    } finally {
                                        if (first != null) first.close();
                                        if (third != null) third.close();
                                    }
                                    column++;
                                }
                                check(lidTransitions >= 2, "The complete sequence includes both visible lid-opening transitions");
                            } finally {
                                if (priorFirst != null) priorFirst.close();
                                if (priorThird != null) priorThird.close();
                            }
                            animated.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
                            animated.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
                            try (NativeImage resetFirst = first(mc, 0, new ObservedBuffers(mc), null);
                                    NativeImage resetThird = third(mc, null, null)) {
                                check(difference(closedFirst, resetFirst) == 0 && difference(closedThird, resetThird) == 0,
                                        "Removing opening and reward markers restores the exact closed render with no gem or arm-pose residue");
                                sample.addProperty("closed_pixels_restored_after_marker_removal", true);
                            }
                        }
                        group++;
                    }
                }
            }
            Path preview = folder.resolve("gem-box-opening-storyboard.png"); sheet.writeToFile(preview);
            result.addProperty("preview", preview.toString());
            result.addProperty("columns", "0 steady; 8 lid opening; 16 lid raised; 24 rummaging; 35 finding; 40 extracting; 52 showing; 55 showing");
            result.addProperty("rows", "first then third view for red attack and blue fireproof rewards, each left/right dominant main/off hand combination");
            result.addProperty("status", "passed_actual_search_extract_and_show_gpu_rendering");
            return result;
        } finally {
            if (redFirst != null) redFirst.close(); if (redThird != null) redThird.close();
            player.setItemInHand(InteractionHand.MAIN_HAND, savedMain); player.setItemInHand(InteractionHand.OFF_HAND, savedOff);
            player.setMainArm(savedArm);
        }
    }

    private static NativeImage copy(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false); copy.copyFrom(source); return copy;
    }
    private static float vectorDistance(float[] before, float[] after) {
        float distance = 0;
        for (int index = 0; index < before.length; index++) distance += Math.abs(before[index] - after[index]);
        return distance;
    }

    /** Raw native GPU frames; the caller may encode these unchanged at the game's twenty ticks per second. */
    private static JsonObject animationFrames(Minecraft mc, ItemStack box, Path folder) throws Exception {
        Path frames = folder.resolve("gem-box-animation"); Files.createDirectories(frames);
        var player = mc.player;
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        HumanoidArm dominant = player.getMainArm();
        int tick = player.tickCount;
        ItemStack animated = box.copy();
        animated.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get()); animated.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
        JsonObject result = new JsonObject();
        try {
            player.setMainArm(HumanoidArm.RIGHT);
            player.setItemInHand(InteractionHand.MAIN_HAND, animated); player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            for (int elapsed = 0; elapsed < GemBoxItem.OPEN_TICKS; elapsed++) {
                player.tickCount = 120 + elapsed;
                animated.set(ExtremeGear.GEM_BOX_OPEN_STARTED.get(), mc.level.getGameTime() - elapsed);
                if (elapsed >= GemBoxItem.REVEAL_TICK) animated.set(ExtremeGear.GEM_BOX_REVEALED_GEM.get(), GemType.FIREPROOF.ordinal() + 1);
                else animated.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
                ObservedBuffers buffers = new ObservedBuffers(mc);
                try (NativeImage image = first(mc, 0, buffers, null)) {
                    check(buffers.rightHand && buffers.leftHand, "Every native animation frame retains both real skin hands");
                    image.writeToFile(frames.resolve(String.format(java.util.Locale.ROOT, "frame-%03d.png", elapsed)));
                }
            }
            result.addProperty("directory", frames.toString()); result.addProperty("filename_pattern", "frame-%03d.png");
            result.addProperty("frames", GemBoxItem.OPEN_TICKS); result.addProperty("frames_per_second", 20);
            result.addProperty("width", WIDTH); result.addProperty("height", HEIGHT);
            result.addProperty("gem", "FIREPROOF rank 1"); result.addProperty("scope", "Unmodified native first-person GPU renders, one frame per game tick");
            return result;
        } finally {
            animated.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get()); animated.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off);
            player.setMainArm(dominant); player.tickCount = tick;
        }
    }

    private static void sound(Minecraft mc, JsonObject result) throws Exception {
        var event = io.github.ningmeng123.modernite.gear.ExtremeGear.GEM_BOX_OPEN.get();
        var sounds = mc.getSoundManager().getSoundEvent(event.getLocation());
        check(sounds != null, "The real client sound manager resolves the registered chest event");
        var selected = sounds.getSound(RandomSource.create(196));
        check(selected.getLocation().toString().equals("modern_ite:item/gem_box_open"), "The chest event selects the renamed custom sound");
        long[] samples = {0};
        float[] peak = {0};
        try (var input = mc.getResourceManager().open(selected.getPath()); var stream = new JOrbisAudioStream(input)) {
            var format = stream.getFormat();
            check(format.getChannels() == 1, "The supplied sound uses mono positional playback");
            while (stream.readChunk(value -> { samples[0]++; peak[0] = Math.max(peak[0], Math.abs(value)); })) {}
            double seconds = samples[0] / (double)format.getSampleRate();
            check(seconds > 2.9 && seconds < 3.2 && peak[0] > 0 && peak[0] < 1,
                    "Minecraft's actual Vorbis decoder reads the complete three-second sound without clipping");
            JsonObject facts = new JsonObject();
            facts.addProperty("event", event.getLocation().toString());
            facts.addProperty("resource", selected.getPath().toString());
            facts.addProperty("decoded_seconds", seconds);
            facts.addProperty("decoded_samples", samples[0]);
            facts.addProperty("channels", format.getChannels());
            facts.addProperty("sample_rate", format.getSampleRate());
            facts.addProperty("peak", peak[0]);
            result.add("sound", facts);
        }
    }

    private static NativeImage gui(Minecraft mc, ItemStack box) {
        return frame(mc, false, pose -> {
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.pose().translate(WIDTH / 2F - 96, HEIGHT / 2F - 96, 0);
            // GuiGraphics puts items at Z=150; only magnify screen axes inside this target's depth range.
            graphics.pose().scale(12, 12, 1);
            graphics.renderItem(box, 0, 0); graphics.flush();
        });
    }

    private static NativeImage first(Minecraft mc, float equip, ObservedBuffers buffers, JsonObject facts) {
        return frame(mc, true, pose -> {
            boolean active = mc.player.getMainHandItem().is(io.github.ningmeng123.modernite.gear.ExtremeGear.GEM_BOX.get())
                    || mc.player.getOffhandItem().is(io.github.ningmeng123.modernite.gear.ExtremeGear.GEM_BOX.get());
            int canceled = 0;
            for (InteractionHand hand : InteractionHand.values()) {
                RenderHandEvent event = new RenderHandEvent(hand, pose, buffers, LightTexture.FULL_BRIGHT,
                        1, 0, 0, equip, mc.player.getItemInHand(hand));
                NeoForge.EVENT_BUS.post(event);
                if (event.isCanceled()) canceled++;
                check(event.isCanceled() == active, "Only an active two-hand chest pose owns both native hand events");
            }
            if (facts != null) {
                facts.addProperty("native_hand_events_canceled", canceled);
                facts.addProperty("right_skin_hand_rendered", buffers.rightHand);
                facts.addProperty("left_skin_hand_rendered", buffers.leftHand);
            }
        });
    }

    private static NativeImage third(Minecraft mc, ObservedBuffers observed, JsonObject facts) {
        return frame(mc, false, pose -> {
            pose.translate(WIDTH / 2F, HEIGHT - 22F, 50);
            pose.scale(145, 145, -145);
            pose.mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI + .35F));
            Lighting.setupForEntityInInventory();
            PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
            GemBoxDrawPoseAudit.renderThroughDispatcher(mc, pose, observed == null ? mc.renderBuffers().bufferSource() : observed);
            if (facts != null) {
                facts.addProperty("native_player_renderer", renderer.getClass().getName());
                facts.addProperty("native_third_person_entry_point", "EntityRenderDispatcher.render -> PlayerRenderer.render");
            }
        });
    }

    private static NativeImage frame(Minecraft mc, boolean first, Draw draw) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        try {
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(first
                    ? new Matrix4f().setPerspective((float)Math.toRadians(70), WIDTH / (float)HEIGHT, .05F, 100)
                    : new Matrix4f().setOrtho(0, WIDTH, HEIGHT, 0, -1000, 1000),
                    first ? VertexSorting.DISTANCE_TO_ORIGIN : VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1);
            Lighting.setupFor3DItems(); draw.render(new PoseStack()); mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch(); view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); Lighting.setupFor3DItems();
            mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static final class ObservedBuffers implements MultiBufferSource {
        private final Minecraft mc;
        private boolean rightHand, leftHand;
        private int atlasVertices, rightSkinVertices;
        private double rightSkinX, rightSkinY, rightSkinZ;
        private ObservedBuffers(Minecraft mc) { this.mc = mc; }
        @Override public VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) {
            int[] hand = {0};
            if (type.toString().contains(mc.player.getSkin().texture().toString())) {
                StackWalker.getInstance().forEach(frame -> {
                    if (frame.getMethodName().equals("renderRightHand")) { rightHand = true; hand[0] = 1; }
                    if (frame.getMethodName().equals("renderLeftHand")) { leftHand = true; hand[0] = -1; }
                });
            }
            VertexConsumer nativeBuffer = mc.renderBuffers().bufferSource().getBuffer(type);
            boolean atlas = type.toString().contains("textures/atlas/blocks.png");
            if (!atlas && hand[0] != 1) return nativeBuffer;
            return new VertexConsumer() {
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    if (atlas) atlasVertices++;
                    else { rightSkinVertices++; rightSkinX += x; rightSkinY += y; rightSkinZ += z; }
                    nativeBuffer.addVertex(x, y, z); return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { nativeBuffer.setColor(r, g, b, a); return this; }
                @Override public VertexConsumer setUv(float u, float v) { nativeBuffer.setUv(u, v); return this; }
                @Override public VertexConsumer setUv1(int u, int v) { nativeBuffer.setUv1(u, v); return this; }
                @Override public VertexConsumer setUv2(int u, int v) { nativeBuffer.setUv2(u, v); return this; }
                @Override public VertexConsumer setNormal(float x, float y, float z) { nativeBuffer.setNormal(x, y, z); return this; }
            };
        }
        private float[] rightSkinCenter() {
            check(rightSkinVertices > 0, "The actual right skin hand must emit measurable native vertices");
            return new float[]{(float)(rightSkinX / rightSkinVertices), (float)(rightSkinY / rightSkinVertices), (float)(rightSkinZ / rightSkinVertices)};
        }
    }

    private static void save(NativeImage image, Path folder, String name, NativeImage sheet, int row, int column) throws Exception {
        image.writeToFile(folder.resolve("gem-box-" + name + ".png"));
        check(foreground(image) > 500, "The actual GPU carry sample must be visible: " + name);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) sheet.setPixelRGBA(column * WIDTH + x, row * HEIGHT + y, image.getPixelRGBA(x, y));
    }
    private static int foreground(NativeImage image) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int pixel = image.getPixelRGBA(x, y);
            if (Math.abs((pixel & 255) - 16) + Math.abs((pixel >>> 8 & 255) - 24) + Math.abs((pixel >>> 16 & 255) - 32) > 18) count++;
        }
        return count;
    }
    private static int difference(NativeImage first, NativeImage second) {
        int changed = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int a = first.getPixelRGBA(x, y), b = second.getPixelRGBA(x, y);
            if (Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))
                    + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255)) > 12) changed++;
        }
        return changed;
    }
    private static float read(WalkAnimationState walk, String name) throws Exception {
        Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); return field.getFloat(walk);
    }
    private static void write(WalkAnimationState walk, String name, float value) throws Exception {
        Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); field.setFloat(walk, value);
    }
    private record SavedWalk(float speedOld, float speed, float position) {
        SavedWalk(WalkAnimationState walk) throws Exception { this(read(walk, "speedOld"), read(walk, "speed"), read(walk, "position")); }
        void stage(WalkAnimationState walk, boolean moving) throws Exception {
            write(walk, "speedOld", moving ? .8F : 0); write(walk, "speed", moving ? .8F : 0); write(walk, "position", moving ? 2.4F : 0);
        }
        void restore(WalkAnimationState walk) throws Exception { write(walk, "speedOld", speedOld); write(walk, "speed", speed); write(walk, "position", position); }
    }
    @FunctionalInterface private interface Draw { void render(PoseStack pose); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
