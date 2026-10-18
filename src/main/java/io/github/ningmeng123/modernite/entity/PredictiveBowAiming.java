package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.SkeletonAiming;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Observe server motion and choose the initial bow release; never steer flying arrows. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class PredictiveBowAiming {
    private static final Map<LivingEntity, Observation> OBSERVATIONS = new WeakHashMap<>();
    private record Observation(Vec3 position, long tick, Vec3 movement) {}

    private PredictiveBowAiming() {}

    @SubscribeEvent
    public static void observeTarget(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide && mob.isAlive()
                && (mob.getMainHandItem().getItem() instanceof BowItem || mob.getOffhandItem().getItem() instanceof BowItem)
                && mob.getTarget() != null) {
            movement(mob.getTarget());
        }
    }

    static Vec3 movement(LivingEntity target) {
        long tick = target.level().getGameTime();
        Vec3 position = target.position();
        Observation previous = OBSERVATIONS.get(target);
        Vec3 velocity;
        if (previous == null || tick < previous.tick || tick - previous.tick > 3) {
            velocity = target.getKnownMovement();
        } else if (tick == previous.tick) {
            // Multiple archers share one sample. Include movement packets received after this tick's sample.
            Vec3 latest = position.subtract(previous.position);
            return latest.lengthSqr() > 16D ? Vec3.ZERO
                    : latest.horizontalDistanceSqr() > 1.0E-8D ? horizontal(latest) : previous.movement;
        } else {
            Vec3 delta = position.subtract(previous.position);
            // Teleports and dimension changes must not become a huge movement lead.
            velocity = delta.lengthSqr() > 16D ? Vec3.ZERO : delta.scale(1D / (tick - previous.tick));
        }
        velocity = horizontal(velocity);
        OBSERVATIONS.put(target, new Observation(position, tick, velocity));
        return velocity;
    }

    private static Vec3 horizontal(Vec3 velocity) {
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.z)
                || velocity.horizontalDistanceSqr() < 1.0E-6D) return Vec3.ZERO;
        return new Vec3(velocity.x, 0D, velocity.z);
    }

    public static boolean shoot(Mob shooter, AbstractArrow arrow, LivingEntity target, float speed) {
        if (shooter.level().isClientSide || !target.isAlive()) return false;
        Vec3 point = SkeletonAiming.aimPoint(shooter, target);
        if (point == null) return false;
        Vec3 velocity = movement(target);
        Vec3 direction = point.subtract(arrow.position());
        var aim = PredictiveBowAimRules.aim(direction.x, direction.y, direction.z,
                velocity.x, velocity.z, speed, arrow.getGravity());
        // Keep an exposed body point when the anticipated position would be behind solid cover.
        if (!SourceRangedAttackGoal.clearShot(shooter, point.add(aim.leadX(), 0D, aim.leadZ()))) {
            aim = PredictiveBowAimRules.aim(direction.x, direction.y, direction.z, 0D, 0D, speed, arrow.getGravity());
        }
        float spread = velocity.horizontalDistanceSqr() < .0001D ? .15F : .35F;
        arrow.shoot(aim.x(), aim.y(), aim.z(), speed, spread);
        return true;
    }
}
