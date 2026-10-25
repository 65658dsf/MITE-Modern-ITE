package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.client.entity.BucketHeadLayer;
import io.github.ningmeng123.modernite.client.entity.BucketHelmetModel;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Native effect-event pixels and both installed player renderers. @author NingMeng123 */
public final class BucketHeadClientAudit {
    private static final int SIZE = 320;
    private BucketHeadClientAudit() {}

    public static JsonObject capture(Minecraft mc, Path folder) throws Exception {
        check(mc.level != null && mc.player != null && mc.getSingleplayerServer() != null,
                "Bucket audit requires the disposable singleplayer world");
        Files.createDirectories(folder);
        JsonObject result = new JsonObject();
        result.addProperty("author", "NingMeng123");
        result.add("screen_overlays", overlays(mc, folder));
        result.add("third_person", players(mc, folder));
        result.addProperty("status", "passed");
        return result;
    }

    private static JsonObject overlays(Minecraft mc, Path folder) throws Exception {
        var player = mc.player;
        var effect = ExtremeEffects.VALUES.get("bucket_head");
        var savedEffect = player.getEffect(effect);
        savedEffect = savedEffect == null ? null : new MobEffectInstance(savedEffect);
        var pumpkin = ExtremeEffects.VALUES.get("occlusion");
        var savedPumpkin = player.getEffect(pumpkin);
        savedPumpkin = savedPumpkin == null ? null : new MobEffectInstance(savedPumpkin);
        var camera = mc.getCameraEntity();
        CameraType perspective = mc.options.getCameraType();
        boolean hidden = mc.options.hideGui;
        float health = player.getHealth();
        var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        var info = mc.getConnection().getPlayerInfo(player.getUUID());
        check(info != null, "The real local player must have client player information");
        Field mode = PlayerInfo.class.getDeclaredField("gameMode"); mode.setAccessible(true);
        GameType oldMode = info.getGameMode();
        JsonObject result = new JsonObject();
        try {
            mode.set(info, GameType.CREATIVE);
            mc.setCameraEntity(player);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            player.removeEffect(effect);
            player.removeEffect(pumpkin);
            int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
            int edge = (int)Math.ceil(height * .75D);
            try (NativeImage clearBlue = overlayFrame(mc, 0xFF80B0D0);
                    NativeImage clearWarm = overlayFrame(mc, 0xFFF0B060)) {
                player.addEffect(new MobEffectInstance(effect, 200));
                check(BucketHeadOverlay.active(mc), "The live first-person player's synchronized effect must activate the overlay");
                try (NativeImage bucketBlue = overlayFrame(mc, 0xFF80B0D0);
                        NativeImage bucketWarm = overlayFrame(mc, 0xFFF0B060)) {
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        if (y < edge) {
                            check(bucketBlue.getPixelRGBA(x,y) == bucketWarm.getPixelRGBA(x,y),
                                    "Every pixel above the opening must be opaque on different backgrounds");
                            check(bucketBlue.getPixelRGBA(x,y) != clearBlue.getPixelRGBA(x,y),
                                    "No original scene pixel may remain above the opening");
                        } else {
                            check(bucketBlue.getPixelRGBA(x,y) == clearBlue.getPixelRGBA(x,y)
                                            && bucketWarm.getPixelRGBA(x,y) == clearWarm.getPixelRGBA(x,y),
                                    "The entire bottom quarter must retain the original scene pixels");
                        }
                    }
                    Path png = folder.resolve("bucket-head-first-person.png"); bucketBlue.writeToFile(png);
                    result.addProperty("preview", png.toString());
                    result.addProperty("opaque_top_pixels", width * edge);
                    result.addProperty("unchanged_bottom_pixels", width * (height - edge));
                    result.addProperty("width", width); result.addProperty("height", height);
                    result.addProperty("opening_top", edge);
                    mc.options.hideGui = true;
                    try (NativeImage f1 = overlayFrame(mc, 0xFF80B0D0)) {
                        check(difference(bucketBlue, f1) == 0, "F1 must retain the same vision restriction");
                    }
                    mc.options.hideGui = false;
                    for (CameraType view : List.of(CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT)) {
                        mc.options.setCameraType(view);
                        try (NativeImage control = overlayFrame(mc, 0xFF80B0D0)) {
                            check(difference(bucketBlue, control) == 0, "Both third-person cameras must retain the same bucket mask");
                        }
                    }
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    var pig = EntityType.PIG.create(mc.level);
                    check(pig != null, "Camera control must create the detached non-player entity");
                    try {
                        mc.setCameraEntity(pig);
                        try (NativeImage control = overlayFrame(mc, 0xFF80B0D0)) {
                            check(difference(clearBlue, control) == 0, "Another camera entity must not inherit the local player's mask");
                        }
                    } finally { mc.setCameraEntity(player); pig.discard(); }
                    player.setHealth(0F);
                    try (NativeImage control = overlayFrame(mc, 0xFF80B0D0)) {
                        check(difference(clearBlue, control) == 0, "The death camera must not retain the bucket mask");
                    } finally { player.setHealth(health); }
                    mode.set(info, GameType.SPECTATOR);
                    check(player.isSpectator(), "The negative control must use the real spectator query");
                    try (NativeImage control = overlayFrame(mc, 0xFF80B0D0)) {
                        check(difference(clearBlue, control) == 0, "A spectator must not receive the bucket mask");
                    } finally { mode.set(info, GameType.CREATIVE); }
                    player.removeEffect(effect);
                    try (NativeImage control = overlayFrame(mc, 0xFF80B0D0)) {
                        check(difference(clearBlue, control) == 0, "Effect removal must restore every original pixel");
                    }
                    result.add("pumpkin_and_combined", pumpkinOverlays(mc, folder, clearBlue, bucketBlue, edge));
                }
            }
            check(player.getItemBySlot(EquipmentSlot.HEAD) == helmet, "Rendering must leave the actual helmet stack untouched");
            result.addProperty("event_source", "NeoForge.EVENT_BUS RenderGuiEvent.Post");
            result.addProperty("f1_preserves_mask", true);
            result.addProperty("all_three_perspectives_same_mask", true);
            result.addProperty("death_spectator_other_camera_and_clear_controls", true);
            result.addProperty("status", "passed");
            return result;
        } finally {
            mode.set(info, oldMode);
            player.setHealth(health);
            player.removeEffect(effect); if (savedEffect != null) player.addEffect(savedEffect);
            player.removeEffect(pumpkin); if (savedPumpkin != null) player.addEffect(savedPumpkin);
            mc.options.hideGui = hidden; mc.options.setCameraType(perspective); mc.setCameraEntity(camera);
        }
    }

    private static JsonObject pumpkinOverlays(Minecraft mc, Path folder, NativeImage clear, NativeImage bucket, int edge) throws Exception {
        var pumpkin = ExtremeEffects.VALUES.get("occlusion");
        var bucketEffect = ExtremeEffects.VALUES.get("bucket_head");
        JsonObject facts = new JsonObject();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.player.addEffect(new MobEffectInstance(pumpkin, 200));
        try (NativeImage first = overlayFrame(mc, 0xFF80B0D0)) {
            check(difference(clear, first) > 100, "The actual pumpkin status must visibly draw its native texture");
            Path png = folder.resolve("pumpkin-occlusion-all-perspectives.png"); first.writeToFile(png);
            facts.addProperty("pumpkin_preview", png.toString());
            facts.addProperty("pumpkin_changed_pixels", difference(clear, first));
            for (CameraType view : CameraType.values()) {
                mc.options.setCameraType(view);
                try (NativeImage actual = overlayFrame(mc, 0xFF80B0D0)) {
                    check(difference(first, actual) == 0, "Pumpkin occlusion must retain identical pixels in " + view);
                }
                mc.options.hideGui = true;
                try (NativeImage f1 = overlayFrame(mc, 0xFF80B0D0)) {
                    check(difference(first, f1) == 0, "F1 must retain pumpkin occlusion in " + view);
                } finally { mc.options.hideGui = false; }
                mc.player.addEffect(new MobEffectInstance(bucketEffect, 200));
                try (NativeImage mixed = overlayFrame(mc, 0xFF80B0D0)) {
                    for (int y = 0; y < edge; y++) for (int x = 0; x < mixed.getWidth(); x++)
                        check(mixed.getPixelRGBA(x,y) == bucket.getPixelRGBA(x,y),
                                "The bucket must remain fully opaque above the opening when pumpkin occlusion is also active");
                    if (view == CameraType.FIRST_PERSON) {
                        Path mixedPng = folder.resolve("bucket-head-with-pumpkin.png"); mixed.writeToFile(mixedPng);
                        facts.addProperty("combined_preview", mixedPng.toString());
                    }
                } finally { mc.player.removeEffect(bucketEffect); }
            }
            mc.player.removeEffect(pumpkin);
            for (CameraType view : CameraType.values()) {
                mc.options.setCameraType(view);
                try (NativeImage removed = overlayFrame(mc, 0xFF80B0D0)) {
                    check(difference(clear, removed) == 0, "Clearing both effects must restore every original pixel in " + view);
                }
            }
            facts.addProperty("all_three_perspectives_same_pumpkin_mask", true);
            facts.addProperty("f1_retains_pumpkin_mask", true);
            facts.addProperty("combined_bucket_top_opaque_in_all_perspectives", true);
            facts.addProperty("clear_restores_all_pixels_in_all_perspectives", true);
            facts.addProperty("status", "passed");
            return facts;
        } finally {
            mc.player.removeEffect(bucketEffect); mc.player.removeEffect(pumpkin);
            mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static NativeImage overlayFrame(Minecraft mc, int background) {
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        return frame(mc, width, height, background, () -> {
            GuiGraphics gui = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            NeoForge.EVENT_BUS.post(new RenderGuiEvent.Post(gui, DeltaTracker.ONE));
            gui.flush();
        });
    }

    private static JsonObject players(Minecraft mc, Path folder) throws Exception {
        var effect = ExtremeEffects.VALUES.get("bucket_head");
        JsonObject report = new JsonObject(); JsonArray cases = new JsonArray(); report.add("cases", cases);
        try (NativeImage sheet = new NativeImage(SIZE * 4, SIZE * 2, false)) {
            int row = 0;
            for (PlayerSkin.Model shape : PlayerSkin.Model.values()) {
                PlayerSkin nativeSkin = mc.player.getSkin();
                PlayerSkin skin = new PlayerSkin(nativeSkin.texture(), null, null, null, shape, false);
                RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")) {
                    @Override public PlayerSkin getSkin() { return skin; }
                };
                try {
                    player.setPos(mc.player.position().add(0, 10, 0)); player.setNoGravity(true);
                    player.yBodyRot = player.yBodyRotO = 0; player.yHeadRot = player.yHeadRotO = 18;
                    player.setXRot(12); player.xRotO = 12;
                    ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
                    player.setItemSlot(EquipmentSlot.HEAD, helmet);
                    PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
                    check(mc.getEntityRenderDispatcher().getSkinMap().get(shape) == renderer,
                            "The detached player must use the actual registered " + shape + " renderer");
                    Field layersField = LivingEntityRenderer.class.getDeclaredField("layers"); layersField.setAccessible(true);
                    List<?> layers = (List<?>)layersField.get(renderer);
                    List<?> matching = layers.stream().filter(BucketHeadLayer.class::isInstance).toList();
                    check(matching.size() == 1, "Both registered skin renderers need exactly one bucket state layer");
                    var layer = (BucketHeadLayer)matching.getFirst();
                    Field bucketField = BucketHeadLayer.class.getDeclaredField("bucket"); bucketField.setAccessible(true);
                    BucketHelmetModel bucket = (BucketHelmetModel)bucketField.get(layer);
                    JsonObject facts = new JsonObject(); cases.add(facts); facts.addProperty("skin", shape.id());
                    try (NativeImage before = playerFrame(mc, player)) {
                        player.addEffect(new MobEffectInstance(effect, 200));
                        try (NativeImage affected = playerFrame(mc, player)) {
                            int changed = difference(before, affected);
                            check(changed > 100, "The independent bucket state must visibly cover the player head");
                            facts.addProperty("bucket_changed_pixels", changed);
                            check(player.getItemBySlot(EquipmentSlot.HEAD) == helmet && helmet.is(Items.DIAMOND_HELMET),
                                    "The real diamond helmet must remain equipped under the bucket visual");
                            sameHead(renderer, bucket);
                            copy(before, sheet, row, 0); copy(affected, sheet, row, 1);
                            player.removeEffect(effect);
                            try (NativeImage removed = playerFrame(mc, player)) {
                                check(difference(before, removed) == 0, "Clearing the state must restore the exact original player image");
                                copy(removed, sheet, row, 2);
                            }
                            player.addEffect(new MobEffectInstance(effect, 200));
                            player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING);
                            try (NativeImage crouched = playerFrame(mc, player)) { sameHead(renderer, bucket); }
                            player.setShiftKeyDown(false); player.setPose(Pose.STANDING);
                            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("big_head"), 200));
                            try (NativeImage big = playerFrame(mc, player)) {
                                sameHead(renderer, bucket);
                                check(bucket.head.xScale == 2F, "The bucket must follow the final big-head scale");
                                copy(big, sheet, row, 3);
                            }
                        }
                    }
                    facts.addProperty("layer_count", matching.size()); facts.addProperty("helmet_preserved", true);
                    facts.addProperty("clear_restores_pixels", true); facts.addProperty("crouch_and_big_head_pose", true);
                } finally { player.discard(); }
                row++;
            }
            Path png = folder.resolve("bucket-head-player-poses.png"); sheet.writeToFile(png);
            report.addProperty("preview", png.toString());
            report.addProperty("columns", "diamond helmet; bucket effect; cleared; bucket with big head");
            report.addProperty("rows", "slim; wide");
        }
        report.addProperty("status", "passed"); return report;
    }

    private static void sameHead(PlayerRenderer renderer, BucketHelmetModel bucket) {
        var head = renderer.getModel().head; var actual = bucket.head;
        check(actual.x == head.x && actual.y == head.y && actual.z == head.z
                        && actual.xRot == head.xRot && actual.yRot == head.yRot && actual.zRot == head.zRot
                        && actual.xScale == head.xScale && actual.yScale == head.yScale && actual.zScale == head.zScale,
                "The independent bucket geometry must copy the final animated head transform");
    }

    static NativeImage playerFrame(Minecraft mc, AbstractClientPlayer player) {
        return frame(mc, SIZE, SIZE, 0xFF101820, () -> {
            PoseStack pose = new PoseStack(); pose.translate(SIZE / 2F, SIZE - 24F, 50);
            pose.scale(110, 110, -110);
            pose.mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI + .35F));
            Lighting.setupForEntityInInventory();
            PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
            renderer.render(player, 0, 1, pose, mc.renderBuffers().bufferSource(), LightTexture.FULL_BRIGHT);
        });
    }

    private static NativeImage frame(Minecraft mc, int width, int height, int background, Runnable render) {
        TextureTarget target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        try {
            target.setClearColor((background >>> 16 & 255) / 255F, (background >>> 8 & 255) / 255F, (background & 255) / 255F, 1);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, -10000, 10000), VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1);
            Lighting.setupFor3DItems(); render.run(); mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch(); view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); Lighting.setupFor3DItems();
            mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static int difference(NativeImage first, NativeImage second) {
        int changed = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++)
            if (first.getPixelRGBA(x,y) != second.getPixelRGBA(x,y)) changed++;
        return changed;
    }

    private static void copy(NativeImage frame, NativeImage sheet, int row, int column) {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            sheet.setPixelRGBA(column * SIZE + x, row * SIZE + y, frame.getPixelRGBA(x,y));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
