package io.github.ningmeng123.modernite.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.world.phys.AABB;

/** Draws one cloud's transparent puffs from back to front. @author NingMeng123 */
public final class InfernalCloudBatch extends Particle {
    private static final Comparator<Entry> BACK_TO_FRONT =
            (first, second) -> Double.compare(second.distanceSquared, first.distanceSquared);
    private final Consumer<List<Particle>> collect;
    private final BooleanSupplier active;
    private final List<Particle> particles = new ArrayList<>();
    private final List<Entry> entryPool = new ArrayList<>();
    private final List<Entry> ordered = new ArrayList<>();

    public InfernalCloudBatch(ClientLevel level, double x, double y, double z,
                              Consumer<List<Particle>> collect, BooleanSupplier active) {
        super(level, x, y, z);
        this.collect = collect;
        this.active = active;
        hasPhysics = false;
    }

    @Override public void tick() {
        if (Minecraft.getInstance().level != level || !active.getAsBoolean()) remove();
    }

    @Override public void render(VertexConsumer vertices, Camera camera, float partialTick) {
        if (!isAlive()) return;
        particles.clear();
        collect.accept(particles);
        ordered.clear();
        var cameraPosition = camera.getPosition();
        for (Particle particle : particles) {
            if (!particle.isAlive()) continue;
            int index = ordered.size();
            if (index == entryPool.size()) entryPool.add(new Entry());
            Entry entry = entryPool.get(index);
            entry.particle = particle;
            // Read each position once; sorting compares cached numbers without allocating vectors.
            entry.distanceSquared = particle.getPos().distanceToSqr(cameraPosition);
            ordered.add(entry);
        }
        ordered.sort(BACK_TO_FRONT);
        for (Entry entry : ordered) entry.particle.render(vertices, camera, partialTick);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public AABB getRenderBoundingBox(float partialTick) { return AABB.INFINITE; }

    private static final class Entry {
        Particle particle;
        double distanceSquared;
    }
}
