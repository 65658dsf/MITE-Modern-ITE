package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Recovery notices follow the actual successful death-drop transaction. @author NingMeng123 */
@GameTestHolder("modern_ite_recovery")
@PrefixGameTestTemplate(false)
public final class DeathDropRecoveryGameTests {
    private DeathDropRecoveryGameTests() {}

    @GameTest(template = "gear_empty")
    public static void successfulTransferReportsCoordinatesAndRecoversTheExactForgedStackAfterReload(GameTestHelper h) {
        try (Peer peer = new Peer(h)) {
            ItemStack gear = MobAbilities.item("mite:mithril_chestplate");
            gear.setDamageValue(17);
            gear.set(ExtremeGear.FORGE_GRADE.get(), 4);
            gear.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 recovery"));
            ItemStack expected = gear.copy();
            List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), peer.player.getX(),
                    peer.player.getY(), peer.player.getZ(), gear)));
            roll(peer.player, 0);
            ExtremeMobEvents.drops(new LivingDropsEvent(peer.player, peer.player.damageSources().generic(), drops, false));
            Mob mimic = peer.mimic(h);
            try {
                h.assertTrue(drops.isEmpty() && peer.notices.size() == 1, "Only a completed transfer publishes one recovery message");
                var text = (TranslatableContents) peer.notices.getFirst().content().getContents();
                var pos = mimic.blockPosition();
                h.assertTrue(java.util.Arrays.equals(text.getArgs(), new Object[]{pos.getX(), pos.getY(), pos.getZ()}),
                        "The persistent chat message contains the real substitute's spawn coordinates");
                CompoundTag saved = new CompoundTag();
                mimic.saveWithoutId(saved);
                Mob loaded = ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE).create(h.getLevel());
                loaded.load(saved);
                List<ItemEntity> recovered = new ArrayList<>();
                PlayerMimicInventory.release(loaded, recovered);
                PlayerMimicInventory.release(loaded, recovered);
                h.assertTrue(recovered.size() == 1 && ItemStack.matches(expected, recovered.getFirst().getItem()),
                        "One exact stack with damage, name and forge grade survives reload and releases once");
            } finally { mimic.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void unsuccessfulRollCanceledJoinAndEmptyInventoryDoNotReportMissingGear(GameTestHelper h) {
        try (Peer peer = new Peer(h)) {
            ItemStack stack = new ItemStack(Items.IRON_HELMET);
            List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), peer.player.getX(),
                    peer.player.getY(), peer.player.getZ(), stack)));
            roll(peer.player, 1);
            ExtremeMobEvents.drops(new LivingDropsEvent(peer.player, peer.player.damageSources().generic(), drops, false));
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem() == stack && peer.notices.isEmpty(),
                    "An unsuccessful probability roll leaves the original drop and sends no recovery notice");
            Consumer<EntityJoinLevelEvent> deny = event -> {
                if (event.getEntity() instanceof Mob mob && mob instanceof ExtremeMob e
                        && e.extremeKind() == ExtremeKind.MIMIC_ZOMBIE
                        && peer.player.getGameProfile().getName().equals(e.extremeMemory().mimicName)) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityJoinLevelEvent.class, deny);
            try {
                roll(peer.player, 0);
                ExtremeMobEvents.drops(new LivingDropsEvent(peer.player, peer.player.damageSources().generic(), drops, false));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem() == stack && peer.notices.isEmpty(),
                        "A vetoed substitute join leaves the drop and publishes no false recovery location");
            } finally { NeoForge.EVENT_BUS.unregister(deny); }
            roll(peer.player, 0);
            ExtremeMobEvents.drops(new LivingDropsEvent(peer.player, peer.player.damageSources().generic(), new ArrayList<>(), false));
            Mob mimic = peer.mimic(h);
            try { h.assertTrue(peer.notices.isEmpty(), "A substitute with no transferred inventory publishes no missing-gear message"); }
            finally { mimic.discard(); }
        }
        h.succeed();
    }

    private static void roll(ServerPlayer player, int expected) {
        for (long seed = 0; seed < 10_000; seed++) {
            player.getRandom().setSeed(seed);
            if (player.getRandom().nextInt(20) == expected) { player.getRandom().setSeed(seed); return; }
        }
        throw new IllegalStateException("Missing deterministic mimic roll");
    }

    private static final class Peer implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final List<ClientboundSystemChatPacket> notices = new ArrayList<>();
        Peer(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "Recover" + UUID.randomUUID().toString().substring(0, 6)), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player, cookie) {
                @Override public void send(Packet<?> packet) { capture(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { capture(packet); }
            };
            player.moveTo(h.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        }
        private void capture(Packet<?> packet) {
            if (packet instanceof ClientboundSystemChatPacket chat && chat.content().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("message.modern_ite.player_mimic_inventory")) notices.add(chat);
        }
        Mob mimic(GameTestHelper h) {
            var mimics = h.getLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(2),
                    mob -> mob instanceof ExtremeMob e && e.extremeKind() == ExtremeKind.MIMIC_ZOMBIE
                            && player.getGameProfile().getName().equals(e.extremeMemory().mimicName));
            h.assertTrue(mimics.size() == 1, "The actual death-drop callback creates exactly one persistent substitute");
            return mimics.getFirst();
        }
        @Override public void close() { channel.finishAndReleaseAll(); }
    }
}
