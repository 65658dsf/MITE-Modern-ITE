package io.github.ningmeng123.modernite.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Server-only publication; no client-to-server configuration write channel. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public record IteConfigSync(Map<String,Object> values) implements CustomPacketPayload {
    private static final Gson GSON=new Gson();
    private static final java.lang.reflect.Type MAP_TYPE=new TypeToken<Map<String,Object>>(){}.getType();
    public static final Type<IteConfigSync> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"gameplay_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf,IteConfigSync> CODEC=new StreamCodec<>() {
        @Override public IteConfigSync decode(RegistryFriendlyByteBuf buffer) {
            return new IteConfigSync(IteConfig.validated(GSON.fromJson(buffer.readUtf(65536),MAP_TYPE)));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer,IteConfigSync payload) {
            buffer.writeUtf(GSON.toJson(payload.values()),65536);
        }
    };
    public IteConfigSync { values=Map.copyOf(values); }
    @Override public Type<IteConfigSync> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(payload,context)->context.enqueueWork(()->
                io.github.ningmeng123.modernite.config.client.IteConfigClient.receive(payload.values())));
    }
    public static void send(ServerPlayer player) {
        if(player.connection!=null&&player.connection.hasChannel(TYPE))
            PacketDistributor.sendToPlayer(player,new IteConfigSync(IteConfig.localSnapshot()));
    }
    public static void broadcast() {
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null)server.execute(()->server.getPlayerList().getPlayers().forEach(IteConfigSync::send));
    }
    @EventBusSubscriber(modid=ModernIte.MOD_ID)
    public static final class Login {
        private Login() {}
        @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
            if(event.getEntity() instanceof ServerPlayer player)send(player);
        }
    }
}
