package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Share visible player effects with nearby players through native effect packets. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class PlayerVisualEffectSync {
    private static final Map<String, Map<ServerPlayer, Snapshot>> PREVIOUS = Map.of(
            "bucket_head", new WeakHashMap<>(), "reversed", new WeakHashMap<>(),
            "big_head", new WeakHashMap<>());

    private PlayerVisualEffectSync() {}

    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PREVIOUS.forEach((id, previous) -> sync(player, id, previous));
    }

    private static void sync(ServerPlayer player, String id, Map<ServerPlayer, Snapshot> snapshots) {
        var holder = ExtremeEffects.VALUES.get(id);
        var effect = player.getEffect(holder);
        Snapshot previous = snapshots.get(player);
        if (effect == null) {
            if (snapshots.remove(player) != null)
                player.serverLevel().getChunkSource().broadcast(player,
                        new ClientboundRemoveMobEffectPacket(player.getId(), holder));
            return;
        }

        Snapshot current = Snapshot.of(player, effect);
        if (previous == null || !current.follows(previous))
            player.serverLevel().getChunkSource().broadcast(player,
                    new ClientboundUpdateMobEffectPacket(player.getId(), effect, false));
        snapshots.put(player, current);
    }

    @SubscribeEvent public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)
                || !(event.getTarget() instanceof ServerPlayer target)) return;
        for (String id : PREVIOUS.keySet()) {
            var effect = target.getEffect(ExtremeEffects.VALUES.get(id));
            if (effect != null)
                viewer.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), effect, false));
        }
    }

    private record Snapshot(int tick, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon) {
        private static Snapshot of(ServerPlayer player, MobEffectInstance effect) {
            return new Snapshot(player.tickCount, effect.getDuration(), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon());
        }

        private boolean follows(Snapshot previous) {
            long elapsed = Math.max(0L, (long) tick - previous.tick);
            long expectedDuration = previous.duration < 0 ? previous.duration : Math.max(0L, previous.duration - elapsed);
            return duration == expectedDuration && amplifier == previous.amplifier
                    && ambient == previous.ambient && visible == previous.visible && showIcon == previous.showIcon;
        }
    }
}
