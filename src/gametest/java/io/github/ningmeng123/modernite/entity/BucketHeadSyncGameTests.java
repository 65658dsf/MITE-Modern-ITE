package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Capture native observer packets through actual ChunkMap tracking and server ticks. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class BucketHeadSyncGameTests {
    private BucketHeadSyncGameTests() {}

    @GameTest(template = "gear_empty")
    public static void startingNativeTrackingReceivesAnAlreadyActiveBucket(GameTestHelper h) {
        try (var session = new Session(h)) {
            session.target.player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("bucket_head"), 200, 0));
            h.assertTrue(session.updates().isEmpty(), "An observer outside the native tracking set receives no bucket packet");
            session.trackViewer();
            h.assertTrue(session.updates().size() == 1,
                    "The real native pairing event sends exactly one active bucket update to the new observer");
            assertUpdate(h, session.updates().getFirst(), session.target.player, 200);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void observersReceiveBucketChangesWithoutCountdownPacketSpam(GameTestHelper h) {
        try (var session = new Session(h)) {
            session.trackViewer();
            session.viewer.packets.clear();
            session.target.player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("bucket_head"), 200, 0));
            session.tickTarget();
            h.assertTrue(session.updates().size() == 1, "Adding bucket head sends one update to the native observer");
            assertUpdate(h, session.updates().getFirst(), session.target.player, 199);
            for (int tick = 0; tick < 10; tick++) session.tickTarget();
            h.assertTrue(session.target.player.getEffect(ExtremeEffects.VALUES.get("bucket_head")).getDuration() == 189,
                    "Real player effect ticks advance the native countdown");
            h.assertTrue(session.updates().size() == 1, "An ordinary countdown never broadcasts a bucket update every tick");
            session.target.player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("bucket_head"), 200, 0));
            session.tickTarget();
            h.assertTrue(session.updates().size() == 2, "Refreshing bucket head sends one new observer update");
            assertUpdate(h, session.updates().getLast(), session.target.player, 199);
            session.target.player.removeEffect(ExtremeEffects.VALUES.get("bucket_head"));
            session.tickTarget();
            h.assertTrue(session.removals().size() == 1, "Explicit removal sends the observer one native remove-effect packet");
            for (int tick = 0; tick < 3; tick++) session.tickTarget();
            h.assertTrue(session.removals().size() == 1, "An absent effect never repeatedly sends remove packets");
            session.target.player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("bucket_head"), 3, 0));
            session.tickTarget();
            h.assertTrue(session.updates().size() == 3, "A later bucket application is visible after an earlier cure");
            session.tickTarget();
            session.tickTarget();
            h.assertTrue(!session.target.player.hasEffect(ExtremeEffects.VALUES.get("bucket_head"))
                            && session.removals().size() == 2,
                    "Native expiry also sends exactly one observer removal packet");
        }
        h.succeed();
    }

    private static void assertUpdate(GameTestHelper h, ClientboundUpdateMobEffectPacket packet, ServerPlayer target, int duration) {
        h.assertTrue(packet.getEntityId() == target.getId()
                        && packet.getEffect().equals(ExtremeEffects.VALUES.get("bucket_head"))
                        && packet.getEffectAmplifier() == 0 && packet.getEffectDurationTicks() == duration,
                "The captured native packet identifies the affected player, bucket effect, fixed level and remaining duration");
    }

    private static final class Session implements AutoCloseable {
        private final GameTestHelper helper;
        private final Peer target;
        private final Peer viewer;

        private Session(GameTestHelper helper) {
            this.helper = helper;
            var origin = helper.absolutePos(new BlockPos(1, 160, 1)).getCenter();
            target = new Peer(helper);
            target.player.moveTo(origin);
            helper.getLevel().addNewPlayer(target.player);
            viewer = new Peer(helper);
            viewer.player.moveTo(origin.add(2, 0, 0));
            // A headless connection cannot receive chunk acknowledgments. Keep the target's
            // real chunk pending until trackViewer explicitly makes that chunk available.
            viewer.player.connection.chunkSender.markChunkPendingToSend(helper.getLevel().getChunkAt(target.player.blockPosition()));
            helper.getLevel().addNewPlayer(viewer.player);
            helper.assertTrue(!helper.getLevel().getChunkSource().chunkMap.getPlayersWatching(target.player).contains(viewer.player),
                    "The fixture begins without a native observer pairing");
            viewer.packets.clear();
        }

        private void trackViewer() {
            viewer.player.connection.chunkSender.dropChunk(viewer.player, target.player.chunkPosition());
            viewer.player.setChunkTrackingView(ChunkTrackingView.of(target.player.chunkPosition(), 8));
            helper.getLevel().getChunkSource().move(viewer.player);
            helper.assertTrue(helper.getLevel().getChunkSource().chunkMap.getPlayersWatching(target.player).contains(viewer.player),
                    "ChunkMap.move really pairs the observer and fires the native StartTracking event");
        }

        private void tickTarget() {
            // The network listener normally drives doTick, then the world runs tickNonPassenger.
            // These real paths advance effects and publish the registered EntityTick.Post event.
            target.player.doTick();
            helper.getLevel().tickNonPassenger(target.player);
        }

        private List<ClientboundUpdateMobEffectPacket> updates() {
            return viewer.packets.stream().filter(packet -> packet instanceof ClientboundUpdateMobEffectPacket update
                            && update.getEntityId() == target.player.getId()
                            && update.getEffect().equals(ExtremeEffects.VALUES.get("bucket_head")))
                    .map(packet -> (ClientboundUpdateMobEffectPacket) packet).toList();
        }

        private List<ClientboundRemoveMobEffectPacket> removals() {
            return viewer.packets.stream().filter(packet -> packet instanceof ClientboundRemoveMobEffectPacket remove
                            && remove.entityId() == target.player.getId()
                            && remove.effect().equals(ExtremeEffects.VALUES.get("bucket_head")))
                    .map(packet -> (ClientboundRemoveMobEffectPacket) packet).toList();
        }

        @Override public void close() {
            target.player.removeAllEffects();
            helper.getLevel().tickNonPassenger(target.player);
            target.player.discard();
            viewer.player.discard();
            target.channel.finishAndReleaseAll();
            viewer.channel.finishAndReleaseAll();
        }
    }

    private static final class Peer {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final List<Packet<?>> packets = new ArrayList<>();

        private Peer(GameTestHelper helper) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "NingMeng123"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie) {
                @Override public void send(Packet<?> packet) { capture(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { capture(packet); }
            };
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
        }

        private void capture(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) bundle.subPackets().forEach(this::capture);
            else packets.add(packet);
        }
    }
}
