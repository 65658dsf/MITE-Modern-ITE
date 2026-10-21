package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;

/** Real renderer and GPU regression for native head and emissive-eye enlargement. @author NingMeng123 */
public final class EndermanHeadClientAudit {
    private EndermanHeadClientAudit() {}

    public static JsonObject capture(Minecraft mc, Path screenshots) throws Exception {
        EnderMan mob = EntityType.ENDERMAN.create(mc.level);
        check(mob != null, "Enderman factory must create the audit entity");
        mob.setPos(mc.player.position());
        mob.setNoAi(true);
        mob.setYRot(0);
        mob.setXRot(0);
        mob.yBodyRot = mob.yBodyRotO = mob.yHeadRot = mob.yHeadRotO = 0;
        var renderer = (EndermanRenderer) mc.getEntityRenderDispatcher().getRenderer(mob);
        EndermanModel<EnderMan> model = renderer.getModel();
        Field modelField = LivingEntityRenderer.class.getDeclaredField("model");
        modelField.setAccessible(true);
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("status", "incomplete");
        Files.createDirectories(screenshots);
        double originalSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float originalWidth = mob.getBbWidth(), originalHeight = mob.getBbHeight();
        try {
            JsonArray poses = new JsonArray();
            report.add("native_pose_checks", poses);
            for (int frame = 0; frame < 8; frame++) {
                model.creepy = (frame & 1) != 0;
                model.carrying = (frame & 2) != 0;
                model.setupAnim(mob, frame * .7F, .5F, frame, 30, -15);
                checkScale(model.head, 2, "head");
                checkScale(model.hat, 2, "hat");
                for (ModelPart body : new ModelPart[] {model.body, model.rightArm, model.leftArm,
                        model.rightLeg, model.leftLeg}) checkScale(body, 1, "body and limbs");
                check(Math.abs(model.head.yRot - (float) Math.PI / 6) < .00001F,
                        "The enlarged head must retain native yaw");
                check(model.head.y == (model.creepy ? -18F : -13F),
                        "The enlarged head must retain native angry and calm offsets");
                if (model.carrying) check(model.rightArm.xRot == -.5F && model.leftArm.xRot == -.5F,
                        "Carrying must preserve both native arm poses");
                JsonObject pose = new JsonObject();
                pose.addProperty("frame", frame);
                pose.addProperty("creepy", model.creepy);
                pose.addProperty("carrying", model.carrying);
                pose.addProperty("head_scale", model.head.xScale);
                poses.add(pose);
            }
            model.creepy = model.carrying = false;
            Files.createDirectories(screenshots);
            // The negative control uses the same native shape and pose but restores its original size.
            var normal = new EndermanModel<EnderMan>(EndermanModel.createBodyLayer().bakeRoot()) {
                @Override public void setupAnim(EnderMan entity, float limb, float amount, float age, float yaw, float pitch) {
                    super.setupAnim(entity, limb, amount, age, yaw, pitch);
                    head.xScale = head.yScale = head.zScale = 1F;
                    hat.xScale = hat.yScale = hat.zScale = 1F;
                }
            };
            try (NativeImage enlarged = MobRenderRegressionAudit.capture(mc, mob, true, 0, 70F)) {
                modelField.set(renderer, normal);
                try (NativeImage control = MobRenderRegressionAudit.capture(mc, mob, true, 0, 70F)) {
                    Path enlargedPng = screenshots.resolve("enderman_big_head.png");
                    Path normalPng = screenshots.resolve("enderman_original_head_control.png");
                    enlarged.writeToFile(enlargedPng);
                    control.writeToFile(normalPng);
                    report.addProperty("screenshot", enlargedPng.toString());
                    report.addProperty("ordinary_head_control", normalPng.toString());
                    // Keep the old crop values as diagnostic evidence: shoulder geometry can enter
                    // rows 118-124, making a normal head falsely measure as wide as both shoulders.
                    report.addProperty("enlarged_legacy_top125_width", width(enlarged, 125));
                    report.addProperty("ordinary_legacy_top125_width", width(control, 125));
                    int bigEyes = eyePixels(enlarged), smallEyes = eyePixels(control);
                    report.addProperty("enlarged_emissive_eye_pixels", bigEyes);
                    report.addProperty("ordinary_emissive_eye_pixels", smallEyes);

                    modelField.set(renderer, model);
                    try (NativeImage largeHead = headOnly(mc, mob, model)) {
                        modelField.set(renderer, normal);
                        try (NativeImage ordinaryHead = headOnly(mc, mob, normal)) {
                            int bigWidth = width(largeHead, largeHead.getHeight());
                            int smallWidth = width(ordinaryHead, ordinaryHead.getHeight());
                            report.addProperty("enlarged_head_pixel_width", bigWidth);
                            report.addProperty("ordinary_head_pixel_width", smallWidth);
                            report.addProperty("width_measurement", "Isolated native head and hat geometry; body and limbs hidden");
                            largeHead.writeToFile(screenshots.resolve("enderman_big_head_only.png"));
                            ordinaryHead.writeToFile(screenshots.resolve("enderman_original_head_only_control.png"));
                            check(smallEyes > 0 && bigEyes > smallEyes * 2,
                                    "The enlarged native emissive eyes must remain visible and cover more pixels: " + bigEyes + " vs " + smallEyes);
                            check(smallWidth > 0 && bigWidth > smallWidth * 1.7,
                                    "The actual rendered head must be visibly wider than the original head: " + bigWidth + " vs " + smallWidth);
                        }
                    }
                } finally {
                    modelField.set(renderer, model);
                }
            }
            check(mob.getActiveEffects().isEmpty(), "Visual enlargement must not add player status effects");
            check(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) == originalSpeed,
                    "Visual enlargement must preserve movement speed");
            check(mob.getBbWidth() == originalWidth && mob.getBbHeight() == originalHeight,
                    "Visual enlargement must preserve native collision dimensions");
            report.addProperty("status", "passed");
            return report;
        } catch (Exception failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            throw failure;
        } finally {
            modelField.set(renderer, model);
            model.creepy = model.carrying = false;
            mob.discard();
            Files.writeString(screenshots.resolve("enderman-head-diagnostics.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
    }

    private static int eyePixels(NativeImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int p = image.getPixelRGBA(x, y);
            int red = p & 255, green = p >>> 8 & 255, blue = p >>> 16 & 255;
            if (red > 100 && blue > 100 && red > green * 1.25 && blue > green * 1.25) count++;
        }
        return count;
    }

    private static NativeImage headOnly(Minecraft mc, EnderMan mob, EndermanModel<EnderMan> model) {
        ModelPart[] parts = {model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        boolean[] visible = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            visible[i] = parts[i].visible;
            parts[i].visible = false;
        }
        try {
            return MobRenderRegressionAudit.capture(mc, mob, true, 0, 70F);
        } finally {
            for (int i = 0; i < parts.length; i++) parts[i].visible = visible[i];
        }
    }

    private static int width(NativeImage image, int rows) {
        int maximum = 0;
        for (int y = 0; y < rows; y++) {
            int first = image.getWidth(), last = -1;
            for (int x = 0; x < image.getWidth(); x++) {
                int p = image.getPixelRGBA(x, y);
                if (Math.abs((p & 255) - 16) + Math.abs((p >>> 8 & 255) - 24)
                        + Math.abs((p >>> 16 & 255) - 32) > 18) {
                    first = Math.min(first, x);
                    last = x;
                }
            }
            maximum = Math.max(maximum, last - first + 1);
        }
        return maximum;
    }

    private static void checkScale(ModelPart part, float expected, String name) {
        check(part.xScale == expected && part.yScale == expected && part.zScale == expected,
                name + " must retain scale " + expected + " across repeated animation calls");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
