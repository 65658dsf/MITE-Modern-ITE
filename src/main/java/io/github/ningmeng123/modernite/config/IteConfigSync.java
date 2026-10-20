package io.github.ningmeng123.modernite.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
public record IteConfigSync(Map<String,Object> values, Map<String,Double> naturalSpawnRates) implements CustomPacketPayload {
    private static final Gson GSON=new Gson();
    private static final java.lang.reflect.Type MAP_TYPE=new TypeToken<Map<String,Object>>(){}.getType();
    public static final Type<IteConfigSync> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"gameplay_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf,IteConfigSync> CODEC=new StreamCodec<>() {
        @Override public IteConfigSync decode(RegistryFriendlyByteBuf buffer) {
            JsonObject document=GSON.fromJson(buffer.readUtf(262144),JsonObject.class);
            return new IteConfigSync(IteConfig.validated(GSON.fromJson(document.get("values"),MAP_TYPE)),
                    IteSpawnConfig.validated(GSON.fromJson(document.get("natural_spawn_rates"),MAP_TYPE)));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer,IteConfigSync payload) {
            var document=new JsonObject();
            document.add("values",GSON.toJsonTree(payload.values()));
            document.add("natural_spawn_rates",GSON.toJsonTree(payload.naturalSpawnRates()));
            buffer.writeUtf(GSON.toJson(document),262144);
        }
    };
    public IteConfigSync { values=Map.copyOf(values);naturalSpawnRates=IteSpawnConfig.validated(naturalSpawnRates); }
    public IteConfigSync(Map<String,Object> values) { this(values,IteSpawnConfig.localSnapshot()); }
    @Override public Type<IteConfigSync> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE,CODEC,(payload,context)->context.enqueueWork(()->
                io.github.ningmeng123.modernite.config.client.IteConfigClient.receive(payload.values(),payload.naturalSpawnRates())));
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
