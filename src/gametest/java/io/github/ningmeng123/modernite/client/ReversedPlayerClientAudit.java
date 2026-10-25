package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/** Real transformed renderer checks and equipped GPU controls. @author NingMeng123 */
public final class ReversedPlayerClientAudit {
    private ReversedPlayerClientAudit() {}

    public static JsonObject capture(Minecraft mc, Path folder) throws Exception {
        Files.createDirectories(folder);
        var effect = ExtremeEffects.VALUES.get("reversed");
        var prior = mc.player.getEffect(effect);
        var saved = prior == null ? null : new MobEffectInstance(prior);
        JsonObject report = new JsonObject(); report.addProperty("author", "NingMeng123");
        try {
            mc.player.removeEffect(effect);
            check(!LivingEntityRenderer.isEntityUpsideDown(mc.player), "Unaffected local player must remain upright");
            mc.player.addEffect(new MobEffectInstance(effect, 200));
            check(LivingEntityRenderer.isEntityUpsideDown(mc.player), "Actual mixed predicate must invert the local player");
            mc.player.removeEffect(effect);
            check(!LivingEntityRenderer.isEntityUpsideDown(mc.player), "Clearing the effect must restore the local player");
            report.addProperty("local_player_add_remove", true);
            JsonArray samples = new JsonArray(); report.add("equipped_gpu_samples", samples);
            for (PlayerSkin.Model shape : PlayerSkin.Model.values()) {
                PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, shape, false);
                RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")) {
                    @Override public PlayerSkin getSkin() { return skin; }
                };
                try {
                    player.setPos(mc.player.position().add(0, 10, 0)); player.setNoGravity(true);
                    player.yBodyRot = player.yBodyRotO = 0; player.yHeadRot = player.yHeadRotO = 18;
                    player.setXRot(12); player.xRotO = 12;
                    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                    player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                    player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                    PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
                    Matrix4f normal = rotations(renderer, player);
                    check(!LivingEntityRenderer.isEntityUpsideDown(player), "Unaffected remote control must stay upright");
                    try (NativeImage before = BucketHeadClientAudit.playerFrame(mc, player)) {
                        Path normalPng = folder.resolve("player-" + shape.id() + "-normal.png"); before.writeToFile(normalPng);
                        player.addEffect(new MobEffectInstance(effect, 200));
                        check(LivingEntityRenderer.isEntityUpsideDown(player), "Remote renderer must read its own effect");
                        check(!LivingEntityRenderer.isEntityUpsideDown(mc.player), "Remote effect must not leak into the local player");
                        Matrix4f reversed = rotations(renderer, player);
                        near(normal.m11(), 1, "Normal model Y axis"); near(reversed.m11(), -1, "Reversed model Y axis");
                        near(reversed.m31() - normal.m31(), (player.getBbHeight() + .1F) / player.getScale(), "Native height compensation");
                        try (NativeImage affected = BucketHeadClientAudit.playerFrame(mc, player)) {
                            int changed = difference(before, affected);
                            check(changed > 100, "Effect must visibly change the real equipped GPU model");
                            Path reversedPng = folder.resolve("player-" + shape.id() + "-reversed.png"); affected.writeToFile(reversedPng);
                            JsonObject facts = new JsonObject(); samples.add(facts);
                            facts.addProperty("skin", shape.id()); facts.addProperty("changed_pixels", changed);
                            facts.addProperty("normal_screenshot", normalPng.toString()); facts.addProperty("reversed_screenshot", reversedPng.toString());
                            facts.addProperty("model_y_axis", reversed.m11()); facts.addProperty("height_offset", reversed.m31());
                            player.removeEffect(effect);
                            try (NativeImage cleared = BucketHeadClientAudit.playerFrame(mc, player)) {
                                check(difference(before, cleared) == 0, "Clearing reversed must restore every equipped player pixel");
                            }
                            check(!LivingEntityRenderer.isEntityUpsideDown(player), "Remote predicate must restore after clear");
                            facts.addProperty("clear_restores_pixels", true);
                        }
                    }
                } finally { player.discard(); }
            }
            var zombie = EntityType.ZOMBIE.create(mc.level);
            check(zombie != null, "Vanilla zombie control must exist");
            try {
                check(!LivingEntityRenderer.isEntityUpsideDown(zombie), "Normal zombie must retain its original orientation");
                zombie.addEffect(new MobEffectInstance(effect, 200));
                check(!LivingEntityRenderer.isEntityUpsideDown(zombie), "Player-only inversion must not change ordinary zombies");
                zombie.setCustomName(Component.literal("Dinnerbone"));
                check(LivingEntityRenderer.isEntityUpsideDown(zombie), "Native Dinnerbone behavior must remain available");
                zombie.setCustomName(Component.literal("Grumm"));
                check(LivingEntityRenderer.isEntityUpsideDown(zombie), "Native Grumm behavior must remain available");
            } finally { zombie.discard(); }
            report.addProperty("non_player_and_native_name_controls", true);
            report.addProperty("status", "passed"); return report;
        } finally {
            mc.player.removeEffect(effect); if (saved != null) mc.player.addEffect(saved);
        }
    }

    private static Matrix4f rotations(PlayerRenderer renderer, AbstractClientPlayer player) throws Exception {
        var method = PlayerRenderer.class.getDeclaredMethod("setupRotations", AbstractClientPlayer.class,
                PoseStack.class, float.class, float.class, float.class, float.class);
        method.setAccessible(true); PoseStack pose = new PoseStack();
        method.invoke(renderer, player, pose, 0F, 0F, 1F, player.getScale());
        return new Matrix4f(pose.last().pose());
    }

    private static int difference(NativeImage first, NativeImage second) {
        int changed = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++)
            if (first.getPixelRGBA(x, y) != second.getPixelRGBA(x, y)) changed++;
        return changed;
    }

    private static void near(float actual, float expected, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < 1e-5F, message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
