package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/** Native player FOV and event composition in the disposable client audit world. @author NingMeng123 */
public final class BigHeadClientAudit {
    private static final float ZOOM = .75F;
    private BigHeadClientAudit() {}

    public static JsonObject capture(Minecraft mc) {
        check(mc.level != null && mc.player != null && mc.getSingleplayerServer() != null,
            "Big-head FOV checks require the disposable singleplayer audit world");
        check(!mc.player.isUsingItem(), "FOV samples require the audit player's empty-hand state");
        var player = mc.player;
        var effect = ExtremeEffects.VALUES.get("big_head");
        MobEffectInstance prior = player.getEffect(effect);
        MobEffectInstance saved = prior == null ? null : new MobEffectInstance(prior);
        double savedEffectScale = mc.options.fovEffectScale().get();
        int savedFov = mc.options.fov().get();
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("scope", "Actual AbstractClientPlayer FOV hook, option preservation and event composition");
        report.addProperty("attribute_scope", "Client-local effects leave the synchronized movement attribute unchanged; server slowdown is checked separately");
        JsonArray samples = new JsonArray();
        report.add("native_fov_samples", samples);
        try {
            player.removeEffect(effect);
            for (double effectScale : new double[] {0D, 1D}) {
                mc.options.fovEffectScale().set(effectScale);
                for (int configuredFov : new int[] {55, 100}) {
                    mc.options.fov().set(configuredFov);
                    float baseline = player.getFieldOfViewModifier();
                    double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    check(Float.isFinite(baseline) && baseline > 0, "The native FOV baseline must be usable");
                    player.addEffect(new MobEffectInstance(effect, 200));
                    float affected = player.getFieldOfViewModifier();
                    float repeated = player.getFieldOfViewModifier();
                    near(affected, baseline * ZOOM, "The native event chain must apply the big-head zoom exactly once");
                    near(repeated, affected, "Repeated FOV queries must not accumulate the zoom");
                    player.addEffect(new MobEffectInstance(effect, 400));
                    float refreshed = player.getFieldOfViewModifier();
                    near(refreshed, affected, "Refreshing big-head duration must retain the same zoom");
                    check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed,
                        "Client-local effect samples must compare the same synchronized movement speed");
                    check(mc.options.fov().get() == configuredFov && mc.options.fovEffectScale().get() == effectScale,
                        "Big-head zoom must preserve both chosen FOV settings");
                    player.removeEffect(effect);
                    float cleared = player.getFieldOfViewModifier();
                    near(cleared, baseline, "Removing big-head must restore native FOV without saved camera state");
                    JsonObject sample = new JsonObject();
                    sample.addProperty("fov_effect_scale", effectScale);
                    sample.addProperty("configured_fov", configuredFov);
                    sample.addProperty("movement_speed", speed);
                    sample.addProperty("baseline_modifier", baseline);
                    sample.addProperty("effect_modifier", affected);
                    sample.addProperty("repeated_modifier", repeated);
                    sample.addProperty("refreshed_modifier", refreshed);
                    sample.addProperty("cleared_modifier", cleared);
                    sample.addProperty("settings_preserved", true);
                    samples.add(sample);
                }
            }
            report.add("other_player_control", otherPlayer(mc));
            report.add("modifier_composition", composition(mc));
            report.addProperty("status", "passed");
            return report;
        } finally {
            player.removeEffect(effect);
            if (saved != null) player.addEffect(saved);
            mc.options.fovEffectScale().set(savedEffectScale);
            mc.options.fov().set(savedFov);
        }
    }

    private static JsonObject otherPlayer(Minecraft mc) {
        var effect = ExtremeEffects.VALUES.get("big_head");
        RemotePlayer other = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "BigHeadAudit"));
        other.setPos(mc.player.position().add(0, 10, 0));
        other.setNoGravity(true);
        mc.level.addEntity(other);
        var connection = mc.getConnection();
        check(connection != null, "The observer audit requires the real client packet listener");
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(other);
        var model = renderer.getModel();
        float localBefore = mc.player.getFieldOfViewModifier();
        float otherBefore = other.getFieldOfViewModifier();
        try {
            connection.handleUpdateMobEffect(new ClientboundUpdateMobEffectPacket(other.getId(),
                    new MobEffectInstance(effect, 200), false));
            check(other.hasEffect(effect) && other.getEffect(effect).getDuration() == 200,
                "The real observer packet handler must apply big-head state to the affected remote entity");
            model.setupAnim(other, 0, 0, 0, 0, 0);
            check(model.head.xScale == 2F && model.hat.xScale == 2F,
                "A network-updated remote player must enlarge both head layers");
            float localAfter = mc.player.getFieldOfViewModifier();
            float otherAfter = other.getFieldOfViewModifier();
            near(localAfter, localBefore, "Another player's big-head effect must not change local FOV");
            near(otherAfter, otherBefore * ZOOM, "The FOV event must use its own player rather than a global player");
            JsonObject facts = new JsonObject();
            facts.addProperty("local_before", localBefore);
            facts.addProperty("local_with_other_affected", localAfter);
            facts.addProperty("other_before", otherBefore);
            facts.addProperty("other_affected", otherAfter);
            facts.addProperty("native_observer_update_applied", true);
            facts.addProperty("remote_head_scale", model.head.xScale);
            facts.addProperty("remote_hat_scale", model.hat.xScale);
            connection.handleRemoveMobEffect(new ClientboundRemoveMobEffectPacket(other.getId(), effect));
            check(!other.hasEffect(effect), "The real observer remove packet must clear remote big-head state");
            model.setupAnim(other, 0, 0, 0, 0, 0);
            check(model.head.xScale == 1F && model.hat.xScale == 1F,
                "A native remote cure must restore the shared player head model");
            near(mc.player.getFieldOfViewModifier(), localBefore, "A remote cure must leave local FOV unchanged");
            facts.addProperty("native_observer_remove_applied", true);
            facts.addProperty("cleared_head_scale", model.head.xScale);
            facts.addProperty("cleared_hat_scale", model.hat.xScale);
            return facts;
        } finally {
            other.removeEffect(effect);
            other.discard();
            model.setupAnim(mc.player, 0, 0, 0, 0, 0);
        }
    }

    private static JsonObject composition(Minecraft mc) {
        var effect = ExtremeEffects.VALUES.get("big_head");
        ComputeFovModifierEvent clear = new ComputeFovModifierEvent(mc.player, 1F);
        clear.setNewFovModifier(1.2F);
        ExtremeVisuals.fov(clear);
        near(clear.getNewFovModifier(), 1.2F, "Unaffected players retain an existing FOV event modifier");
        try {
            mc.player.addEffect(new MobEffectInstance(effect, 200));
            ComputeFovModifierEvent affected = new ComputeFovModifierEvent(mc.player, 1F);
            affected.setNewFovModifier(1.2F);
            ExtremeVisuals.fov(affected);
            near(affected.getNewFovModifier(), .9F, "Big-head must multiply an existing event modifier instead of replacing it");
            JsonObject facts = new JsonObject();
            facts.addProperty("existing_modifier", 1.2F);
            facts.addProperty("unaffected_result", clear.getNewFovModifier());
            facts.addProperty("affected_result", affected.getNewFovModifier());
            return facts;
        } finally {
            mc.player.removeEffect(effect);
        }
    }

    private static void near(float actual, float expected, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < 1e-5F,
            message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
