package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One burst packet; the mushroom cloud is animated entirely by nearby clients. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record InfernalExplosionPayload(ResourceLocation dimension, double x, double y, double z,
                                      float radius, long seed) implements CustomPacketPayload {
    public static final Type<InfernalExplosionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "infernal_explosion"));
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ModernIte.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOUD = PARTICLES.register(
            "infernal_cloud", () -> new SimpleParticleType(true));
    public static final StreamCodec<RegistryFriendlyByteBuf, InfernalExplosionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public InfernalExplosionPayload decode(RegistryFriendlyByteBuf buffer) {
            return new InfernalExplosionPayload(buffer.readResourceLocation(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat(), buffer.readLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, InfernalExplosionPayload value) {
            buffer.writeResourceLocation(value.dimension());
            buffer.writeDouble(value.x()); buffer.writeDouble(value.y()); buffer.writeDouble(value.z());
            buffer.writeFloat(value.radius()); buffer.writeLong(value.seed());
        }
    };

    public static void register(IEventBus bus) { PARTICLES.register(bus); }
    @Override public Type<InfernalExplosionPayload> type() { return TYPE; }

    @SubscribeEvent public static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (Double.isFinite(payload.x()) && Double.isFinite(payload.y()) && Double.isFinite(payload.z())
                    && Float.isFinite(payload.radius()) && payload.radius() > 0 && payload.radius() <= 40)
                io.github.ningmeng123.modernite.client.InfernalExplosionParticles.start(payload);
        }));
    }

    public static void send(ServerLevel level, Vec3 center, float radius) {
        var payload = new InfernalExplosionPayload(level.dimension().location(), center.x, center.y, center.z,
                radius, level.random.nextLong());
        for (var player : level.players()) {
            if (player.connection != null && player.connection.hasChannel(TYPE)
                    && player.distanceToSqr(center) <= 256D * 256D)
                PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
