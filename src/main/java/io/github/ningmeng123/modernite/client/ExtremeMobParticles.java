package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/** Nearby auras are animated locally without streaming particle packets. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class ExtremeMobParticles {
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(.52F, .015F, .04F), 1.2F);
    private static final DustParticleOptions VOID = new DustParticleOptions(new Vector3f(.36F, .025F, .58F), 1.15F);
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1F, .35F, .055F), .45F);
    private static final DustParticleOptions LEVITATION = new DustParticleOptions(new Vector3f(.62F, .35F, .85F), .55F);
    private ExtremeMobParticles() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()
                    || living.distanceToSqr(mc.player) > 48 * 48) continue;
            int clock = living.tickCount + living.getId() * 7;
            if (!(living instanceof ExtremeMob extreme)) continue;
            switch (extreme.extremeKind()) {
                case TIME_LORD -> {
                    if (clock % 4 == 0) {
                        ring(living, ParticleTypes.END_ROD, .9, 1.25, -clock * .04, 4, .005);
                        ring(living, ParticleTypes.ENCHANT, .7, .15, clock * .05, 4, .01);
                    }
                }
                case UNDYING_ZOMBIE_EMPEROR -> {
                    if (clock % 3 == 0) {
                        ring(living, CRIMSON, 1.05, .12, -clock * .045, 7, .015);
                        ring(living, ParticleTypes.SOUL_FIRE_FLAME, .65, 1.5 + Math.sin(clock * .05) * .25, clock * .055, 3, .012);
                    }
                    if (clock % 8 == 0) point(living, ParticleTypes.SOUL, .7, .4, .035);
                }
                case SKELETON_LIGHTNING -> {
                    if (clock % 4 == 0) ring(living, ParticleTypes.ELECTRIC_SPARK, .32,
                            1.05 + Math.sin(clock * .16) * .4, clock * .32, 2, .005);
                }
                case SKELETON_FLOATING -> {
                    if (clock % 4 == 0) ring(living, LEVITATION, .34, .24, clock * .055, 1, .008);
                    if (clock % 5 == 0) ring(living, LEVITATION, .38,
                            .8 + Math.sin(clock * .05) * .5, -clock * .045, 1, .006);
                    if (clock % 40 == 0) ring(living, ParticleTypes.END_ROD, .22, .3, clock * .055, 1, .005);
                }
                case SKELETON_BLAZE -> {
                    if (clock % 5 == 0) {
                        attached(living, ParticleTypes.SMALL_FLAME, -.3, 0, 1.45, .012);
                        attached(living, ParticleTypes.SMALL_FLAME, .3, 0, 1.45, .012);
                    }
                    if (clock % 10 == 0) {
                        attached(living, ParticleTypes.FLAME, 0, -.08, 1.85, .008);
                        point(living, EMBER, .2, 1.25, .018);
                    }
                }
                case ANNIHILATION_SKELETON -> {
                    if (clock % 3 == 0) {
                        ring(living, VOID, 1.1, .1, clock * .035, 7, .008);
                        ring(living, ParticleTypes.REVERSE_PORTAL, .72, 1.6, -clock * .07, 4, .015);
                    }
                    if (clock % 6 == 0) point(living, ParticleTypes.LARGE_SMOKE, .7, .4, .025);
                }
                case PHASE_CREEPER -> {
                    if (clock % 5 == 0) point(living, ParticleTypes.PORTAL, .5, 1, .015);
                }
                case BLACK_INFERNAL_CREEPER -> {
                    if (clock % 3 == 0) {
                        point(living, ParticleTypes.LARGE_SMOKE, .5, .2, .025);
                        point(living, ParticleTypes.SMOKE, .45, 1, .035);
                        point(living, ParticleTypes.SMOKE, .35, 1.65, .025);
                    }
                }
                default -> {}
            }
        }
    }

    private static void attached(LivingEntity entity, ParticleOptions particle, double side, double forward, double y, double rise) {
        double yaw = Math.toRadians(entity.yBodyRot);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        entity.level().addParticle(particle, entity.getX() + side * cos - forward * sin,
                entity.getY() + y, entity.getZ() + side * sin + forward * cos, 0, rise, 0);
    }

    private static void ring(LivingEntity entity, ParticleOptions particle, double radius, double y, double angle, int count, double rise) {
        for (int i = 0; i < count; i++) {
            double a = angle + i * Math.PI * 2 / count;
            entity.level().addParticle(particle, entity.getX() + Math.cos(a) * radius,
                    entity.getY() + y, entity.getZ() + Math.sin(a) * radius, 0, rise, 0);
        }
    }

    private static void point(LivingEntity entity, ParticleOptions particle, double spread, double y, double rise) {
        var random = entity.getRandom();
        entity.level().addParticle(particle, entity.getX() + (random.nextDouble() - .5) * spread * 2,
                entity.getY() + y, entity.getZ() + (random.nextDouble() - .5) * spread * 2, 0, rise, 0);
    }
}
