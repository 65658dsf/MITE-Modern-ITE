package io.github.ningmeng123.modernite.survival;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.client.UnderworldCooldownHud;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Publishes changes to the displayed recovery time for the recovering player. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public record UnderworldCooldownPayload(int secondsRemaining) implements CustomPacketPayload {
    public static final Type<UnderworldCooldownPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "underworld_cooldown"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UnderworldCooldownPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UnderworldCooldownPayload::secondsRemaining, UnderworldCooldownPayload::new);
    private static final Map<ServerGamePacketListenerImpl, Integer> LAST_SENT_SECONDS = new WeakHashMap<>();

    @Override
    public Type<UnderworldCooldownPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC, (payload, context) ->
                UnderworldCooldownHud.receive(context.connection(), payload.secondsRemaining()));
    }

    public static void sync(ServerPlayer player, int secondsRemaining) {
        var connection = player.connection;
        if (connection == null || secondsRemaining == LAST_SENT_SECONDS.getOrDefault(connection, 0)
                || !connection.hasChannel(TYPE)) return;

        PacketDistributor.sendToPlayer(player, new UnderworldCooldownPayload(secondsRemaining));
        if (secondsRemaining > 0) {
            LAST_SENT_SECONDS.put(connection, secondsRemaining);
        } else {
            LAST_SENT_SECONDS.remove(connection);
        }
    }
}
