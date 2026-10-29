package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Native first/third person movement matrix with draw-time limb and item geometry checks. @author NingMeng123 */
final class GemBoxMovementAudit {
    private GemBoxMovementAudit() {}

    enum Mode {
        IDLE(0, 0, 0, false, false, true, false),
        SLOW_WALK(.08, .08, .32F, false, false, true, false),
        NORMAL_WALK(.18, .18, .60F, false, false, true, false),
        CROUCH(.03, .03, .12F, false, true, true, false),
        SPRINT(.18, .18, .85F, true, false, true, true),
        FAST_VELOCITY(.30, 0, .85F, false, false, true, true),
        FAST_POSITION(0, .30, .85F, false, false, true, true),
        AIRBORNE(.08, .08, .32F, false, false, false, true);
        final double velocity, realDelta;
        final float animationSpeed;
        final boolean sprint, crouch, ground, hidden;
        Mode(double velocity, double realDelta, float animationSpeed, boolean sprint, boolean crouch, boolean ground, boolean hidden) {
            this.velocity = velocity; this.realDelta = realDelta; this.animationSpeed = animationSpeed;
            this.sprint = sprint; this.crouch = crouch; this.ground = ground; this.hidden = hidden;
        }
    }

    static JsonObject capture(Minecraft mc, ItemStack box, Path folder) throws Exception {
        Files.createDirectories(folder);
        JsonObject result = new JsonObject();
        result.addProperty("scope", "Actual native first-person pipeline and full PlayerRenderer: grounded idle/walk/crouch carry, sprint/fast/airborne hide, free item and native locomotion retained");
        JsonArray environment = new JsonArray();
        ModList.get().getMods().forEach(mod -> {
            String key = (mod.getModId() + " " + mod.getDisplayName()).toLowerCase(java.util.Locale.ROOT);
            if (key.contains("touch") || key.contains("motion") || key.contains("emf") || key.contains("entity_model")
                    || key.contains("sway") || key.contains("notenough") || key.contains("playeranim") || key.contains("punchy"))
                environment.add(mod.getModId() + " " + mod.getVersion());
        });
        result.add("loaded_animation_mods", environment);
        JsonArray packs = new JsonArray(); mc.options.resourcePacks.forEach(packs::add); result.add("selected_resource_packs", packs);
        JsonArray samples = new JsonArray(); result.add("samples", samples);
        int boxVertices = vertices(mc, box), stickVertices = vertices(mc, new ItemStack(Items.STICK));
        try (State state = new State(mc)) {
            var player = mc.player;
            for (HumanoidArm dominant : HumanoidArm.values()) {
                player.setMainArm(dominant);
                for (InteractionHand hand : InteractionHand.values()) {
                    for (Mode mode : Mode.values()) {
                        state.stage(mode, 2.4F);
                        // Empty hands are the pose reference for carrying. Generic held sticks are
                        // the native locomotion reference for a hidden but still occupied item slot.
                        player.setItemInHand(InteractionHand.MAIN_HAND, mode.hidden ? new ItemStack(Items.STICK) : ItemStack.EMPTY);
                        player.setItemInHand(InteractionHand.OFF_HAND, mode.hidden ? new ItemStack(Items.STICK) : ItemStack.EMPTY);
                        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                        try (GemBoxDrawPoseAudit.Frame nativeReference = GemBoxDrawPoseAudit.movementFrame(mc)) {
                            ItemStack held = box.copy();
                            held.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get()); held.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
                            player.setItemInHand(hand, held);
                            player.setItemInHand(other(hand), new ItemStack(Items.STICK));
                            String name = dominant.name().toLowerCase() + "-" + hand.name().toLowerCase() + "-" + mode.name().toLowerCase();
                            JsonObject facts = new JsonObject(); samples.add(facts);
                            facts.addProperty("main_arm", dominant.name()); facts.addProperty("box_hand", hand.name()); facts.addProperty("mode", mode.name());
                            facts.addProperty("expected_hidden", mode.hidden); facts.addProperty("sprinting", player.isSprinting()); facts.addProperty("grounded", player.onGround());
                            facts.addProperty("horizontal_velocity", player.getDeltaMovement().horizontalDistance());
                            facts.addProperty("horizontal_real_delta", Math.hypot(player.getX() - player.xo, player.getZ() - player.zo));
                            facts.addProperty("crouching", player.isCrouching());
                            check(GemBoxArmPose.hiddenForMovement(player) == mode.hidden && GemBoxArmPose.active(player) == !mode.hidden,
                                    "The movement mode must independently classify real carry versus full hide: " + name);
                            mc.options.setCameraType(CameraType.FIRST_PERSON);
                            JsonObject firstFacts = new JsonObject(); facts.add("first_person", firstFacts);
                            int expected = mode.hidden ? stickVertices : boxVertices;
                            if (mode.hidden) hiddenFirstPerson(mc, held, hand, firstFacts, folder, name, stickVertices);
                            else try (NativeImage image = GemBoxNativeHandAudit.movementFrame(mc, firstFacts, expected, true)) {
                                Path path = folder.resolve("gem-box-movement-" + name + "-first.png"); image.writeToFile(path); firstFacts.addProperty("preview", path.toString());
                                check(firstFacts.get("atlas_vertices").getAsInt() == expected,
                                        "The native first-person mode must draw one carried chest: " + name + " (actual=" + firstFacts.get("atlas_vertices") + ", expected=" + expected + ")");
                                GemBoxNativeHandAudit.movementVisibleHands(mc, image, folder, "movement-" + name, firstFacts, expected);
                            }
                            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                            try (GemBoxDrawPoseAudit.Frame actual = GemBoxDrawPoseAudit.movementFrame(mc)) {
                                Path path = folder.resolve("gem-box-movement-" + name + "-third.png"); actual.image().writeToFile(path);
                                JsonObject thirdFacts = actual.json(); facts.add("third_person", thirdFacts);
                                validateCustomModel(actual.modelEvidence());
                                thirdFacts.addProperty("preview", path.toString()); thirdFacts.addProperty("atlas_vertices", actual.atlasVertices());
                                check(actual.atlasVertices() == expected,
                                        "The full player renderer must hide every box stack while preserving the other stick: " + name + " (" + actual.atlasVertices() + " vs " + expected + ")");
                                if (!mode.hidden) raisedArms(actual, nativeReference, thirdFacts, name);
                                else nativeArms(actual, nativeReference, thirdFacts, name);
                                if (mode == Mode.SPRINT) sprintPhase(mc, state, box, hand, actual, nativeReference, thirdFacts, name, folder);
                            }
                        }
                    }
                    state.stage(Mode.IDLE, 0);
                    player.setItemInHand(hand, box.copy()); player.setItemInHand(other(hand), new ItemStack(Items.STICK));
                    check(GemBoxArmPose.active(player) && !GemBoxArmPose.hiddenForMovement(player), "Stopping restores the two-handed chest instead of retaining the hidden state");
                    JsonObject restored = new JsonObject();
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    try (NativeImage image = GemBoxNativeHandAudit.movementFrame(mc, restored, boxVertices, true)) {
                        image.writeToFile(folder.resolve("gem-box-movement-" + dominant.name().toLowerCase() + "-" + hand.name().toLowerCase() + "-restored-idle.png"));
                    }
                }
            }
            // Both physical slots can contain a box. Hiding must suppress both, not just the selected carrying hand.
            state.stage(Mode.SPRINT, 2.4F);
            player.setItemInHand(InteractionHand.MAIN_HAND, box.copy()); player.setItemInHand(InteractionHand.OFF_HAND, box.copy());
            JsonObject dual = new JsonObject(); result.add("dual_box_sprint", dual);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            try (NativeImage image = GemBoxNativeHandAudit.movementFrame(mc, dual, 0, false)) {
                check(dual.get("atlas_vertices").getAsInt() == 0, "Sprinting with two boxes cannot fall back to either native one-hand box");
                image.writeToFile(folder.resolve("gem-box-movement-dual-sprint-first.png"));
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            try (GemBoxDrawPoseAudit.Frame actual = GemBoxDrawPoseAudit.movementFrame(mc)) {
                check(actual.atlasVertices() == 0, "Both box stacks are hidden in the full third-person renderer during sprinting");
                actual.image().writeToFile(folder.resolve("gem-box-movement-dual-sprint-third.png"));
            }
            result.addProperty("status", "passed_native_movement_visibility_matrix");
            return result;
        }
    }

    private static void hiddenFirstPerson(Minecraft mc, ItemStack box, InteractionHand hand, JsonObject facts,
                                         Path folder, String name, int stickVertices) throws Exception {
        var player = mc.player;
        JsonObject control = new JsonObject(), empty = new JsonObject();
        facts.add("native_only_free_stick_control", control); facts.add("native_empty_control", empty);
        boolean accelerated;
        try { Class.forName("com.github.argon4w.acceleratedrendering.core.CoreFeature"); accelerated = true; }
        catch (ClassNotFoundException absent) { accelerated = false; }
        facts.addProperty("accelerated_rendering_present", accelerated);
        facts.addProperty("baked_stick_vertices", stickVertices);
        Path diagnostic = folder.resolve("gem-box-movement-" + name + "-first.json");
        try (NativeImage actual = GemBoxNativeHandAudit.movementCompleteFrame(mc, facts)) {
            Path path = folder.resolve("gem-box-movement-" + name + "-first.png"); actual.writeToFile(path); facts.addProperty("preview", path.toString());
            player.setItemInHand(hand, ItemStack.EMPTY);
            try (NativeImage ordinary = GemBoxNativeHandAudit.movementCompleteFrame(mc, control)) {
                Path nativePath = folder.resolve("gem-box-movement-" + name + "-first-native-free-stick.png"); ordinary.writeToFile(nativePath); control.addProperty("preview", nativePath.toString());
                player.setItemInHand(other(hand), ItemStack.EMPTY);
                try (NativeImage blank = GemBoxNativeHandAudit.movementCompleteFrame(mc, empty)) {
                    Path emptyPath = folder.resolve("gem-box-movement-" + name + "-first-native-empty.png"); blank.writeToFile(emptyPath); empty.addProperty("preview", emptyPath.toString());
                    int submitted = facts.get("atlas_vertices").getAsInt(), nativeSubmitted = control.get("atlas_vertices").getAsInt();
                    facts.addProperty("actual_minus_native_control_atlas_vertices", submitted - nativeSubmitted);
                    facts.addProperty("actual_minus_native_control_foreground_pixels", facts.get("foreground_pixels").getAsInt() - control.get("foreground_pixels").getAsInt());
                    JsonObject pixels = nativeItemPixels(actual, ordinary, blank); facts.add("free_item_native_pixel_control", pixels);
                    // Save diagnostics before every assertion so a failing sample survives the
                    // enclosing capture's exception, including its original incoming-hand state.
                    Files.writeString(diagnostic, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(facts));
                    check(facts.get("foreground_pixels").getAsInt() > 128 && control.get("foreground_pixels").getAsInt() > 128,
                            "The full native outer pass must visibly retain the free stick: " + name + " (actual pixels=" + facts.get("foreground_pixels") + ", native control=" + control.get("foreground_pixels") + ", actual atlas=" + submitted + ", native atlas=" + nativeSubmitted + ")");
                    check(pixels.get("matching_native_stick_contribution_pixels").getAsInt() > 64,
                            "The hidden-box frame must preserve visible pixels from the same-state native free-stick control: " + name + " (matched=" + pixels.get("matching_native_stick_contribution_pixels") + ")");
                    check(submitted == nativeSubmitted,
                            "Hiding the box must preserve exactly the native free-item atlas contribution: " + name + " (actual=" + submitted + ", native control=" + nativeSubmitted + ", baked stick=" + stickVertices + ")");
                    check((accelerated && submitted == 0) || submitted == stickVertices,
                            "A nondeferred native pass must submit exactly one stick and no box: " + name + " (actual=" + submitted + ", expected=" + stickVertices + ", AR=" + accelerated + ")");
                    boolean cancelledBox = false, uncancelledFree = false;
                    for (var element : facts.getAsJsonArray("native_hand_events")) {
                        var event = element.getAsJsonObject();
                        if (event.get("hand").getAsString().equals(hand.name())) cancelledBox |= event.get("cancelled").getAsBoolean();
                        else uncancelledFree |= !event.get("cancelled").getAsBoolean();
                    }
                    check(cancelledBox && uncancelledFree,
                            "The original outer hand invocation must cancel only the actual box event while releasing the free hand: " + name + " events=" + facts.get("native_hand_events"));
                    var actualRuntime = facts.getAsJsonObject("runtime_after");
                    var controlRuntime = control.getAsJsonObject("runtime_after");
                    if (actualRuntime.has("punchy_configuration") && controlRuntime.has("punchy_configuration"))
                        check(actualRuntime.get("punchy_configuration").equals(controlRuntime.get("punchy_configuration")),
                                "A real native free-item control must retain the user's complete Punchy configuration");
                    facts.addProperty("status", "passed_complete_native_outer_pass_and_same_state_free_item_control");
                }
            }
        } finally {
            player.setItemInHand(hand, box); player.setItemInHand(other(hand), new ItemStack(Items.STICK));
            GemBoxNativeHandAudit.syncMovementEquipment(mc);
            Files.writeString(diagnostic, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(facts));
        }
    }

    private static JsonObject nativeItemPixels(NativeImage actual, NativeImage ordinary, NativeImage blank) {
        int nativeChanged = 0, matched = 0;
        for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++) {
            int normal = ordinary.getPixelRGBA(x, y), clear = blank.getPixelRGBA(x, y), observed = actual.getPixelRGBA(x, y);
            if (rgbDistance(normal, clear) <= 24 || rgbDistance(normal, 0xff201810) <= 18) continue;
            nativeChanged++;
            if (rgbDistance(normal, observed) <= 18) matched++;
        }
        JsonObject result = new JsonObject(); result.addProperty("native_stick_changed_pixels", nativeChanged);
        result.addProperty("matching_native_stick_contribution_pixels", matched); return result;
    }

    private static int rgbDistance(int a, int b) {
        return Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255)) + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255));
    }

    private static void raisedArms(GemBoxDrawPoseAudit.Frame actual, GemBoxDrawPoseAudit.Frame reference, JsonObject facts, String label) {
        for (var arm : new GemBoxDrawPoseAudit.Part[]{GemBoxDrawPoseAudit.Part.RIGHT_ARM, GemBoxDrawPoseAudit.Part.LEFT_ARM}) {
            var limb = actual.require(arm); var idle = reference.require(arm);
            float dot = alignment(limb.axis(), idle.axis()); facts.addProperty(arm.name().toLowerCase() + "_alignment_with_empty_motion", dot);
            check(dot < .85F, "Both real drawn arms must support the chest in idle, normal/slow walk and crouch: " + label + "/" + arm);
            var sleeve = actual.require(arm == GemBoxDrawPoseAudit.Part.RIGHT_ARM ? GemBoxDrawPoseAudit.Part.RIGHT_SLEEVE : GemBoxDrawPoseAudit.Part.LEFT_SLEEVE);
            check(alignment(limb.axis(), sleeve.axis()) > .97F && distance(limb.centre(), sleeve.centre()) / limb.length() < .15F,
                    "The sleeve remains attached to the actual supporting arm in every visible movement mode: " + label);
        }
    }

    private static void nativeArms(GemBoxDrawPoseAudit.Frame actual, GemBoxDrawPoseAudit.Frame reference, JsonObject facts, String label) {
        for (var arm : new GemBoxDrawPoseAudit.Part[]{GemBoxDrawPoseAudit.Part.RIGHT_ARM, GemBoxDrawPoseAudit.Part.LEFT_ARM}) {
            var limb = actual.require(arm); var nativeLimb = reference.require(arm);
            float dot = alignment(limb.axis(), nativeLimb.axis()), gap = distance(limb.centre(), nativeLimb.centre()) / limb.length();
            facts.addProperty(arm.name().toLowerCase() + "_alignment_with_native_locomotion", dot);
            facts.addProperty(arm.name().toLowerCase() + "_relative_gap_from_native_locomotion", gap);
            check(dot > .95F && gap < .25F,
                    "Hidden movement must release the custom carry pose and retain the native running/airborne arms: " + label + "/" + arm);
        }
    }

    private static void sprintPhase(Minecraft mc, State state, ItemStack box, InteractionHand hand, GemBoxDrawPoseAudit.Frame before,
                                    GemBoxDrawPoseAudit.Frame referenceBefore, JsonObject facts, String name, Path folder) throws Exception {
        state.stage(Mode.SPRINT, 5.6F);
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK)); mc.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        try (GemBoxDrawPoseAudit.Frame nativeAfter = GemBoxDrawPoseAudit.movementFrame(mc)) {
            mc.player.setItemInHand(hand, box.copy()); mc.player.setItemInHand(other(hand), new ItemStack(Items.STICK));
            try (GemBoxDrawPoseAudit.Frame after = GemBoxDrawPoseAudit.movementFrame(mc)) {
                after.image().writeToFile(folder.resolve("gem-box-movement-" + name + "-second-run-phase.png"));
                for (var arm : new GemBoxDrawPoseAudit.Part[]{GemBoxDrawPoseAudit.Part.RIGHT_ARM, GemBoxDrawPoseAudit.Part.LEFT_ARM}) {
                    var nativeA = referenceBefore.require(arm); var nativeB = nativeAfter.require(arm);
                    var actualA = before.require(arm); var actualB = after.require(arm);
                    float referenceChange = 1 - alignment(nativeA.axis(), nativeB.axis()) + distance(nativeA.centre(), nativeB.centre()) / nativeB.length();
                    float actualChange = 1 - alignment(actualA.axis(), actualB.axis()) + distance(actualA.centre(), actualB.centre()) / actualB.length();
                    facts.addProperty(arm.name().toLowerCase() + "_native_run_phase_change", referenceChange);
                    facts.addProperty(arm.name().toLowerCase() + "_actual_run_phase_change", actualChange);
                    // CEM packs may cache all animation variables within one render callback. Where
                    // the native reference advances, the hidden box must not freeze that movement.
                    if (referenceChange > .015F) check(actualChange > referenceChange * .25F,
                            "The hidden box cannot freeze the native sprint swing: " + name + "/" + arm);
                }
                nativeArms(after, nativeAfter, facts, name + "/second-run-phase");
            }
        }
    }

    /** Fixture-owned movement state; never ticks, teleports or edits the user's actual game directory. */
    static final class State implements AutoCloseable {
        private final Minecraft mc;
        private final LocalPlayer player;
        private final ItemStack main, off;
        private final Vec3 position, velocity;
        private final double[] oldPositions;
        private final boolean ground, sprint, shift, crouching, flying, swinging;
        private final Pose pose;
        private final HumanoidArm mainArm;
        private final CameraType camera;
        private final float[] walk, rotation, impulses;
        private final boolean[] keys;
        private final int ticks;
        State(Minecraft mc) throws Exception {
            this.mc = mc; player = mc.player; main = player.getMainHandItem(); off = player.getOffhandItem();
            position = player.position(); velocity = player.getDeltaMovement();
            oldPositions = new double[]{player.xo, player.yo, player.zo, player.xOld, player.yOld, player.zOld};
            ground = player.onGround(); sprint = player.isSprinting(); shift = player.isShiftKeyDown(); crouching = crouching().getBoolean(player);
            flying = player.getAbilities().flying; swinging = player.swinging; pose = player.getPose(); mainArm = player.getMainArm(); camera = mc.options.getCameraType();
            ticks = player.tickCount;
            walk = new float[]{read(player.walkAnimation, "speedOld"), read(player.walkAnimation, "speed"), read(player.walkAnimation, "position")};
            rotation = new float[]{player.getYRot(), player.yRotO, player.getXRot(), player.xRotO, player.yBodyRot, player.yBodyRotO,
                    player.yHeadRot, player.yHeadRotO, player.attackAnim, player.oAttackAnim, player.xBob, player.xBobO, player.yBob, player.yBobO};
            impulses = new float[]{player.input.forwardImpulse, player.input.leftImpulse};
            keys = new boolean[]{player.input.up, player.input.down, player.input.left, player.input.right, player.input.jumping, player.input.shiftKeyDown};
        }
        void stage(Mode mode, float phase) throws Exception {
            player.getAbilities().flying = false; player.setSprinting(mode.sprint); player.setShiftKeyDown(mode.crouch);
            crouching().setBoolean(player, mode.crouch); player.setPose(mode.crouch ? Pose.CROUCHING : Pose.STANDING);
            player.setPos(position); player.setOnGround(mode.ground); player.setDeltaMovement(0, 0, mode.velocity);
            player.xo = position.x; player.yo = position.y; player.zo = position.z - mode.realDelta;
            player.xOld = player.xo; player.yOld = player.yo; player.zOld = player.zo;
            player.input.forwardImpulse = mode.animationSpeed > 0 ? 1 : 0; player.input.leftImpulse = 0;
            player.input.up = mode.animationSpeed > 0; player.input.down = player.input.left = player.input.right = player.input.jumping = false;
            player.input.shiftKeyDown = mode.crouch; player.swinging = false; player.tickCount = 120;
            player.setYRot(0); player.yRotO = 0; player.setXRot(0); player.xRotO = 0;
            player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = player.attackAnim = player.oAttackAnim = 0;
            player.xBob = player.xBobO = player.yBob = player.yBobO = 0;
            write(player.walkAnimation, "speedOld", mode.animationSpeed); write(player.walkAnimation, "speed", mode.animationSpeed); write(player.walkAnimation, "position", phase);
        }
        @Override public void close() throws Exception {
            player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off);
            player.setMainArm(mainArm); mc.options.setCameraType(camera); player.getAbilities().flying = flying;
            player.setSprinting(sprint); player.setShiftKeyDown(shift); crouching().setBoolean(player, crouching); player.setPose(pose);
            player.setPos(position); player.setDeltaMovement(velocity); player.setOnGround(ground); player.tickCount = ticks; player.swinging = swinging;
            player.xo = oldPositions[0]; player.yo = oldPositions[1]; player.zo = oldPositions[2]; player.xOld = oldPositions[3]; player.yOld = oldPositions[4]; player.zOld = oldPositions[5];
            player.setYRot(rotation[0]); player.yRotO = rotation[1]; player.setXRot(rotation[2]); player.xRotO = rotation[3];
            player.yBodyRot = rotation[4]; player.yBodyRotO = rotation[5]; player.yHeadRot = rotation[6]; player.yHeadRotO = rotation[7];
            player.attackAnim = rotation[8]; player.oAttackAnim = rotation[9]; player.xBob = rotation[10]; player.xBobO = rotation[11]; player.yBob = rotation[12]; player.yBobO = rotation[13];
            write(player.walkAnimation, "speedOld", walk[0]); write(player.walkAnimation, "speed", walk[1]); write(player.walkAnimation, "position", walk[2]);
            player.input.forwardImpulse = impulses[0]; player.input.leftImpulse = impulses[1];
            player.input.up = keys[0]; player.input.down = keys[1]; player.input.left = keys[2]; player.input.right = keys[3]; player.input.jumping = keys[4]; player.input.shiftKeyDown = keys[5];
            GemBoxNativeHandAudit.syncMovementEquipment(mc);
        }
    }

    private static InteractionHand other(InteractionHand hand) { return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND; }

    static JsonObject emfEvidence(Minecraft mc) {
        var model = ((PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player)).getModel();
        JsonObject result = new JsonObject();
        result.addProperty("model_class", model.getClass().getName());
        result.addProperty("right_arm_class", model.rightArm.getClass().getName()); result.addProperty("left_arm_class", model.leftArm.getClass().getName());
        result.addProperty("right_sleeve_class", model.rightSleeve.getClass().getName()); result.addProperty("left_sleeve_class", model.leftSleeve.getClass().getName());
        result.addProperty("fa_player_selected", mc.options.resourcePacks.stream().anyMatch(id -> id.toLowerCase(java.util.Locale.ROOT).contains("fa+player")));
        try {
            Class<?> access = Class.forName("traben.entity_model_features.models.IEMFModel");
            result.addProperty("emf_interface_available", true); result.addProperty("implements_emf_model", access.isInstance(model));
            Class<?> stateAccess = Class.forName("traben.entity_model_features.models.animation.state.EMFState");
            Class<?> renderState = Class.forName("traben.entity_model_features.models.animation.state.EMFEntityRenderState");
            Object state = stateAccess.getMethod("state").invoke(null);
            result.addProperty("emf_native_state_present", state != null);
            if (state != null) {
                Object entity = renderState.getMethod("emfEntity").invoke(state);
                result.addProperty("emf_state_entity_is_audit_player", entity == mc.player);
                result.addProperty("emf_state_entity_class", entity == null ? "null" : entity.getClass().getName());
                result.addProperty("emf_state_is_first_person_hand", (boolean)renderState.getMethod("isFirstPersonHand").invoke(state));
            }
            if (access.isInstance(model)) {
                Object root = access.getMethod("emf$getEMFRootModel").invoke(model);
                result.addProperty("emf_root_present", root != null);
                if (root != null) {
                    result.addProperty("emf_root_class", root.getClass().getName());
                    try { result.addProperty("root_has_custom_model", root.getClass().getField("hasCustomModel").getBoolean(root)); }
                    catch (ReflectiveOperationException absentField) {
                        try { result.addProperty("root_has_custom_model", (boolean)root.getClass().getMethod("hasCustomModel").invoke(root)); }
                        catch (ReflectiveOperationException absentMethod) { result.addProperty("custom_model_flag_unavailable", true); }
                    }
                }
            }
        } catch (ClassNotFoundException absent) { result.addProperty("emf_interface_available", false); }
        catch (ReflectiveOperationException failure) { result.addProperty("optional_emf_reflection_error", failure.toString()); }
        return result;
    }

    private static void validateCustomModel(JsonObject evidence) {
        check(evidence != null, "The actual player draw must emit skin vertices before reporting its model evidence");
        if (!evidence.get("fa_player_selected").getAsBoolean()) return;
        check(evidence.has("implements_emf_model") && evidence.get("implements_emf_model").getAsBoolean()
                        && evidence.has("emf_root_present") && evidence.get("emf_root_present").getAsBoolean(),
                "FA Player is selected, so the actual draw must use its real EMF player root rather than silently testing a vanilla model: " + evidence);
        check(evidence.has("emf_state_entity_is_audit_player") && evidence.get("emf_state_entity_is_audit_player").getAsBoolean()
                        && evidence.has("emf_state_is_first_person_hand") && !evidence.get("emf_state_is_first_person_hand").getAsBoolean(),
                "The real third-person draw must inherit the dispatcher's correct current player context rather than a stale first-person state: " + evidence);
        if (evidence.has("root_has_custom_model")) check(evidence.get("root_has_custom_model").getAsBoolean(),
                "FA Player is selected but the real EMF root reports no custom player model: " + evidence);
    }
    private static int vertices(Minecraft mc, ItemStack stack) {
        var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0); int count = model.getQuads(null, null, RandomSource.create(196)).size();
        for (Direction face : Direction.values()) count += model.getQuads(null, face, RandomSource.create(196)).size(); return count * 4;
    }
    private static float alignment(float[] a, float[] b) { return Math.abs(a[0] * b[0] + a[1] * b[1] + a[2] * b[2]); }
    private static float distance(float[] a, float[] b) { float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2]; return (float)Math.sqrt(x * x + y * y + z * z); }
    private static Field crouching() throws Exception { Field field = LocalPlayer.class.getDeclaredField("crouching"); field.setAccessible(true); return field; }
    private static float read(WalkAnimationState walk, String name) throws Exception { Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); return field.getFloat(walk); }
    private static void write(WalkAnimationState walk, String name, float value) throws Exception { Field field = WalkAnimationState.class.getDeclaredField(name); field.setAccessible(true); field.setFloat(walk, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
