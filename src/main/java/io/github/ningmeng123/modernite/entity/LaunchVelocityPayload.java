package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Preserves special launches beyond the vanilla motion packet's 3.9-block velocity limit. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public record LaunchVelocityPayload(double x,double y,double z) implements CustomPacketPayload {
    public static final Type<LaunchVelocityPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"launch_velocity"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LaunchVelocityPayload> STREAM_CODEC=new StreamCodec<>() {
        public LaunchVelocityPayload decode(RegistryFriendlyByteBuf buffer){return new LaunchVelocityPayload(buffer.readDouble(),buffer.readDouble(),buffer.readDouble());}
        public void encode(RegistryFriendlyByteBuf buffer,LaunchVelocityPayload value){buffer.writeDouble(value.x);buffer.writeDouble(value.y);buffer.writeDouble(value.z);}
    };
    private static final Map<ServerPlayer,Vec3> PENDING=new WeakHashMap<>();
    @Override public Type<LaunchVelocityPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToClient(TYPE,STREAM_CODEC,(payload,context)->context.enqueueWork(()->{
            if(Double.isFinite(payload.x)&&Double.isFinite(payload.y)&&Double.isFinite(payload.z))
                context.player().setDeltaMovement(payload.x,payload.y,payload.z);
        }));
    }
    public static void queue(ServerPlayer player){
        Vec3 velocity=player.getDeltaMovement();
        if((Math.abs(velocity.x)>3.9||Math.abs(velocity.y)>3.9||Math.abs(velocity.z)>3.9)
                &&player.connection!=null&&player.connection.hasChannel(TYPE))PENDING.put(player,velocity);
    }
    @EventBusSubscriber(modid=ModernIte.MOD_ID)
    public static final class Flush {
        private Flush(){}
        @SubscribeEvent public static void afterServerTick(ServerTickEvent.Post event){
            // Send after ordinary entity tracking has sent its clamped motion packet.
            PENDING.forEach((player,velocity)->{
                if(!player.isRemoved()&&player.connection!=null&&player.connection.hasChannel(TYPE))
                    PacketDistributor.sendToPlayer(player,new LaunchVelocityPayload(velocity.x,velocity.y,velocity.z));
            });
            PENDING.clear();
        }
    }
}
