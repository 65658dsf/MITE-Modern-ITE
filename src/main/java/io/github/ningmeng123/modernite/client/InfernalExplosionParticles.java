package io.github.ningmeng123.modernite.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.InfernalExplosionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** A rising plume feeds rolling cloud lobes, with one sorted render batch. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class InfernalExplosionParticles {
    private static final int DURATION = 360;
    private static final int MAX_CLOUDS = 4;
    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static SpriteSet sprites;
    private static int spriteRevision;
    private static InfernalCloudBatch batch;
    private InfernalExplosionParticles() {}

    @EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Providers {
        private Providers() {}
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> spriteRevision++);
        }
        @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(InfernalExplosionPayload.CLOUD.get(), set -> {
                sprites = set;
                return (options, level, x, y, z, vx, vy, vz) -> {
                    Cloud cloud = new Cloud(level, x, y, z, 1, 1, level.random.nextLong());
                    return new Puff(cloud, Shape.BASE, vx, vy, vz, 1.5F);
                };
            });
        }
    }

    public static void start(InfernalExplosionPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || sprites == null || !mc.level.dimension().location().equals(payload.dimension())) return;
        CLOUDS.removeIf(cloud -> {
            if (cloud.level == mc.level) return false;
            cloud.clear(); return true;
        });
        if (CLOUDS.size() >= MAX_CLOUDS) CLOUDS.removeFirst().clear();
        ParticleStatus setting = mc.options.particles().get();
        double density = setting == ParticleStatus.MINIMAL ? .35 : setting == ParticleStatus.DECREASED ? .65 : 1;
        double distance = mc.player == null ? 0 : Math.sqrt(mc.player.distanceToSqr(payload.x(), payload.y(), payload.z()));
        density *= distance > 160 ? .4 : distance > 96 ? .7 : 1;
        CLOUDS.add(new Cloud(mc.level, payload.x(), payload.y(), payload.z(), payload.radius() / 40F, density, payload.seed()));
        // Draw outside ParticleEngine: queue eviction, reloads and particle-position culling must not erase the cloud.
        if (batch != null) batch.remove();
        batch = new InfernalCloudBatch(mc.level, payload.x(), payload.y(), payload.z(),
                InfernalExplosionParticles::collect, () -> !CLOUDS.isEmpty());
        mc.level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, payload.x(), payload.y() + 2, payload.z(), 0, 0, 0);
    }

    private static void collect(List<Particle> particles) {
        for (Cloud cloud : CLOUDS) particles.addAll(cloud.puffs);
    }

    public static int activeClouds() { return CLOUDS.size(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CLOUDS.isEmpty()
                || batch == null || !batch.isAlive()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || CLOUDS.getFirst().level != mc.level) return;
        var light = mc.gameRenderer.lightTexture();
        light.turnOnLightLayer();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        try {
            var vertices = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin(Tesselator.getInstance(), mc.getTextureManager());
            if (vertices != null) {
                batch.render(vertices, event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
                var mesh = vertices.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            light.turnOffLightLayer();
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            CLOUDS.forEach(Cloud::clear); CLOUDS.clear();
            if (batch != null) { batch.remove(); batch = null; }
            return;
        }
        if (mc.isPaused()) return;
        CLOUDS.removeIf(cloud -> {
            if (cloud.level != mc.level || cloud.age >= DURATION) { cloud.clear(); return true; }
            cloud.tick(); return false;
        });
    }

    private static double ease(double progress) {
        double t = Mth.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private enum Shape { STEM, CAP, COLLAR, BASE, SHOCK, CONDENSATION }

    private static final class Cloud {
        final ClientLevel level;
        final double x, y, z, density;
        final float scale;
        final RandomSource random;
        final List<Puff> puffs = new ArrayList<>(868);
        int age;
        int lastSpriteRevision = spriteRevision;
        double lift, spread, baseExpansion, headY, flowTime;
        float cooling;

        Cloud(ClientLevel level, double x, double y, double z, float scale, double density, long seed) {
            this.level = level; this.x = x; this.y = y; this.z = z; this.scale = scale; this.density = density;
            random = RandomSource.create(seed); geometry();
        }
        void clear() { puffs.forEach(Puff::remove); puffs.clear(); }
        void geometry() {
            lift = ease(age / 110D);
            spread = ease((age - 18) / 100D);
            baseExpansion = ease(age / 38D);
            headY = 44 * lift + Math.max(0, age - 120) * .028;
            flowTime = flowClock(age);
            cooling = (float)ease((age - 135) / 155D);
        }
        static double flowClock(double age) {
            return age < 160 ? age : 160 + 85 * (1 - Math.exp(-(age - 160) / 85));
        }
        void emit(Shape shape, double x, double y, double z, float size) {
            if (density < 1 && random.nextDouble() > density) return;
            puffs.add(new Puff(this, shape, x, y, z, size));
        }
        void tick() {
            if (lastSpriteRevision != spriteRevision) {
                puffs.forEach(puff -> puff.pickSprite(sprites));
                lastSpriteRevision = spriteRevision;
            }
            geometry();
            // Full detail emits at most 868 puffs. They never enter the global queue individually.
            if (age < 96) for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                double lane = random.nextDouble();
                emit(Shape.CAP, Math.cos(angle) * lane, random.nextDouble(), Math.sin(angle) * lane,
                        3.6F + random.nextFloat() * 1.8F);
            }
            if (age < 240) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                double radius = Math.sqrt(random.nextDouble()) * 3.4;
                emit(Shape.STEM, Math.cos(angle) * radius, random.nextDouble(), Math.sin(angle) * radius,
                        2.5F + random.nextFloat() * 1.2F);
            }
            if (age >= 40 && age < 88) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                emit(Shape.COLLAR, Math.cos(angle), random.nextDouble() * Mth.TWO_PI, Math.sin(angle),
                        3 + random.nextFloat());
            }
            if (age >= 80 && age < 112) for (int i = 0; i < 2; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                emit(Shape.CONDENSATION, Math.cos(angle), random.nextDouble() * 3, Math.sin(angle),
                        2.7F + random.nextFloat());
            }
            if (age < 18) for (int i = 0; i < 2; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                double radius = Math.sqrt(random.nextDouble()) * 9;
                emit(Shape.BASE, Math.cos(angle) * radius, random.nextDouble() * 3, Math.sin(angle),
                        3 + random.nextFloat() * 1.5F);
            }
            boolean groundWave = age < 2;
            if (groundWave || age >= 64 && age < 66) for (int i = 0; i < 24; i++) {
                double angle = (i + random.nextDouble()) * Mth.TWO_PI / 24;
                emit(Shape.SHOCK, Math.cos(angle), groundWave ? 3 : 31, Math.sin(angle),
                        2.2F + random.nextFloat());
            }
            puffs.removeIf(puff -> { puff.tick(); return !puff.isAlive(); });
            age++;
        }
    }

    private static final class Puff extends TextureSheetParticle {
        private final Cloud cloud;
        private final Shape shape;
        private final double offsetX, offsetY, offsetZ, lane, azimuth, phase, birthFlow;
        private final float size, shade;
        private final int birthTick;
        private double localX, localY, localZ;

        Puff(Cloud cloud, Shape shape, double x, double y, double z, float size) {
            super(cloud.level, cloud.x, cloud.y, cloud.z);
            this.cloud = cloud; this.shape = shape; birthTick = cloud.age; birthFlow = cloud.flowTime;
            offsetX = x; offsetY = y; offsetZ = z;
            lane = Math.sqrt(x * x + z * z); azimuth = Math.atan2(z, x);
            phase = cloud.random.nextDouble() * 2.2 - 1.1;
            this.size = size * cloud.scale;
            shade = .8F + cloud.random.nextFloat() * .2F;
            lifetime = switch (shape) {
                case SHOCK -> y < 10 ? 82 : 100;
                case CONDENSATION -> 75 + cloud.random.nextInt(25);
                default -> 310 - cloud.age + cloud.random.nextInt(28);
            };
            hasPhysics = false;
            roll = oRoll = cloud.random.nextFloat() * Mth.TWO_PI;
            setSize(this.size * 2, this.size * 2);
            pickSprite(sprites);
            position(); xo = this.x; yo = this.y; zo = this.z; appearance();
        }

        private void position() {
            double t = cloud.flowTime - birthFlow;
            double growth = .03 + .97 * cloud.spread;
            double angle = azimuth + t * .003;
            double radius;
            switch (shape) {
                case CAP -> {
                    // Feed each lobe from the ground, then circulate inside-up / outside-down around the head.
                    double transit = 46 + lane * 18;
                    double arrival = ease(t / transit);
                    double capture = ease((t / transit - .65) / .35);
                    double major = 4.5 + lane * 10;
                    double tube = 4 + lane * 4;
                    double turn = Math.PI + phase - Math.max(0, t - transit) * .035;
                    double ringRadius = (major + Math.cos(turn) * tube) * growth;
                    radius = Mth.lerp(capture, (1 + lane * 2.5) * (.1 + .9 * cloud.lift), ringRadius);
                    localY = Mth.lerp(capture, cloud.headY * arrival,
                            cloud.headY + Math.sin(turn) * tube * .92 * cloud.spread);
                }
                case STEM -> {
                    radius = lane * (.06 + .94 * cloud.lift) * (1 + .15 * Math.sin(t * .04 + phase));
                    localY = Math.min(t * (.44 + offsetY * .2), cloud.headY);
                }
                case COLLAR -> {
                    double turn = offsetY - t * .027;
                    radius = (11 + Math.cos(turn) * 3) * growth;
                    localY = cloud.headY * .77 + Math.sin(turn) * 3 * cloud.spread;
                }
                case CONDENSATION -> {
                    radius = (24 + offsetY + age * .025) * growth;
                    localY = cloud.headY - 5 * cloud.lift + offsetY;
                }
                case SHOCK -> {
                    int waveAge = cloud.age - birthTick;
                    radius = 3 + waveAge * (offsetY < 10 ? 1.2 : .72);
                    localY = offsetY < 10 ? offsetY + waveAge * .035 : 31 * cloud.lift;
                    angle = azimuth;
                }
                case BASE -> {
                    radius = lane * (.03 + .97 * cloud.baseExpansion) + t * .025;
                    localY = offsetY * cloud.baseExpansion + t * .014;
                }
                default -> throw new IllegalStateException();
            }
            localX = Math.cos(angle) * radius;
            localZ = Math.sin(angle) * radius;
            if (shape != Shape.SHOCK && shape != Shape.CONDENSATION) {
                double curl = .45 * cloud.lift;
                localX += Math.sin(t * .045 + offsetX) * curl;
                localZ += Math.cos(t * .045 + offsetZ) * curl;
            }
            setPos(cloud.x + localX * cloud.scale, cloud.y + localY * cloud.scale, cloud.z + localZ * cloud.scale);
        }

        private void appearance() {
            boolean mist = shape == Shape.SHOCK || shape == Shape.CONDENSATION;
            if (mist) {
                setColor(.9F * shade, .94F * shade, shade);
                alpha = (shape == Shape.CONDENSATION ? .18F : offsetY < 10 ? .48F : .28F)
                        * (float)ease(age / 12D) * Mth.clamp((lifetime - age) / 30F, 0, 1);
            } else {
                double dy = localY - cloud.headY + 2;
                double heatRadius = 18 + cloud.spread * 14;
                float hot = Mth.clamp(1F - (float)((localX * localX + dy * dy + localZ * localZ)
                        / (heatRadius * heatRadius)), 0, 1);
                if (shape == Shape.STEM) hot = Math.max(hot, .46F + .2F * (float)(localY / Math.max(1, cloud.headY)));
                if (shape == Shape.COLLAR) hot = Math.max(hot, .62F);
                hot *= 1 - cloud.cooling;
                if (shape == Shape.BASE) hot *= 1 - (float)ease((cloud.age - 25) / 80D);
                // A moving hot core and simultaneous cool outer smoke give the head visible depth.
                setColor(Mth.lerp(Math.min(1, hot * 2), .18F, 1) * shade,
                        Mth.lerp(hot, .17F, .98F) * shade,
                        Mth.lerp(hot * hot * hot, .16F, .38F) * shade);
                alpha = .94F * (float)ease(age / 12D) * Mth.clamp((lifetime - age) / 55F, 0, 1);
            }
            double growth = switch (shape) {
                case BASE -> cloud.baseExpansion;
                case STEM -> cloud.lift;
                case CAP, COLLAR -> cloud.spread;
                default -> 1;
            };
            float emergence = (float)(.05 + .95 * ease(age / 12D));
            float waveExpansion = shape == Shape.SHOCK ? 1 + Math.min(age, 75) * .023F : 1;
            quadSize = size * (float)(.06 + .94 * growth) * emergence * waveExpansion
                    * (1 + Math.min(age, 150) * .0015F);
        }

        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime) { remove(); return; }
            position(); appearance();
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override protected int getLightColor(float partialTick) {
            return shape == Shape.SHOCK || shape == Shape.CONDENSATION || cloud.cooling < .65F
                    ? 0xF000F0 : super.getLightColor(partialTick);
        }
        @Override public AABB getRenderBoundingBox(float partialTick) { return getBoundingBox().inflate(quadSize); }
    }
}
