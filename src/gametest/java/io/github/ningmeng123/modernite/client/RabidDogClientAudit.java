package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.modernite.client.entity.RabidDogRendering;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.github.ningmeng123.modernite.entity.RabidDogContent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** Verifies the registered dog renderer, native GPU output and new status icons. @author NingMeng123 */
public final class RabidDogClientAudit {
    private RabidDogClientAudit() {}

    public static JsonObject capture(Minecraft mc, Path screenshots) throws Exception {
        var dog = RabidDogContent.RABID_DOG.get().create(mc.level);
        check(dog != null, "The registered rabid dog factory must create an entity");
        dog.setPos(mc.player.position());
        dog.setNoAi(true);
        dog.setYRot(0);
        dog.setXRot(0);
        dog.yBodyRot = dog.yBodyRotO = dog.yHeadRot = dog.yHeadRotO = 0;
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        try {
            var registeredRenderer = mc.getEntityRenderDispatcher().getRenderer(dog);
            check(registeredRenderer instanceof WolfRenderer, "Rabid dog must have a native wolf renderer");
            var renderer = (WolfRenderer) registeredRenderer;
            check(renderer.getModel() instanceof RabidDogRendering.Model,
                    "Rabid dog must use its altered head and open-muzzle model");
            var model = (RabidDogRendering.Model) renderer.getModel();
            ResourceLocation texture = renderer.getTextureLocation(dog);
            check(texture.equals(ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_angry.png")),
                    "Rabid dog must select the angry wolf texture");
            try (var stream = mc.getResourceManager().getResourceOrThrow(texture).open();
                 NativeImage artwork = NativeImage.read(stream)) {
                check(artwork.getWidth() >= 64 && artwork.getHeight() >= 32,
                        "The chosen wolf texture must decode successfully at its expected dimensions");
            }
            report.addProperty("texture", texture.toString());
            report.addProperty("model", model.getClass().getName());
            Field headField = RabidDogRendering.Model.class.getDeclaredField("head");
            headField.setAccessible(true);
            ModelPart head = (ModelPart) headField.get(model);
            for (int tick = 0; tick < 4; tick++) {
                dog.tickCount = tick;
                model.setupAnim(dog, 0, 0, tick, 0, 0);
                check(head.xScale == 1.2F && head.yScale == 1.2F && head.zScale == 1.2F,
                        "The enlarged head must retain its scale after every animation update");
            }
            dog.tickCount = 0;
            Files.createDirectories(screenshots);
            try (NativeImage frame = MobRenderRegressionAudit.capture(mc, dog, false, .35F)) {
                int bodyPixels = 0;
                for (int y = 0; y < frame.getHeight(); y++) for (int x = 0; x < frame.getWidth(); x++) {
                    int p = frame.getPixelRGBA(x, y);
                    if (Math.abs((p & 255) - 16) + Math.abs((p >>> 8 & 255) - 24)
                            + Math.abs((p >>> 16 & 255) - 32) > 18) bodyPixels++;
                }
                check(bodyPixels > 200, "Actual native GPU rendering must draw a visible dog");
                check(head.xScale == 1.2F && head.yScale == 1.2F && head.zScale == 1.2F,
                        "The real renderer must preserve enlarged head scale");
                Path png = screenshots.resolve("rabid_dog.png");
                frame.writeToFile(png);
                report.addProperty("body_pixels", bodyPixels);
                report.addProperty("head_scale", head.xScale);
                report.addProperty("screenshot", png.toString());
            }
            JsonObject icons = new JsonObject();
            report.add("effect_icons", icons);
            for (String id : new String[] {"rabies", "dwarf_curse"}) {
                var sprite = mc.getMobEffectTextures().get(ExtremeEffects.VALUES.get(id));
                check(!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()),
                        "The new effect must have a resolved atlas icon: " + id);
                icons.addProperty(id, sprite.contents().name().toString());
            }
            report.addProperty("status", "passed");
            return report;
        } finally {
            dog.discard();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
