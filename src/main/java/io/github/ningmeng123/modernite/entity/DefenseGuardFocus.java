package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** A temporary defensive focus never replaces the pursuit target. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class DefenseGuardFocus {
    private static final Map<Mob, DefenseIntent.Threat> FOCI = new WeakHashMap<>();

    private DefenseGuardFocus() {}

    /** Inactive goals are not checked every tick. Keep two-tick intent observation independent of that cadence. */
    @SubscribeEvent
    public static void observe(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide && mob.isAlive()
                && (MobShields.handles(mob) && MobShields.melee(mob.getMainHandItem())
                        && MobShields.shield(mob.getOffhandItem())
                    || mob instanceof ExtremeMobs.ZombieVariant zombie && DoorGuard.holdsDoor(zombie)))
            DefenseIntent.evaluate(mob, FOCI.get(mob));
    }

    public static boolean guarding(Mob mob) {
        return mob.isAlive() && (MobShields.defending(mob)
                || mob instanceof ExtremeMobs.ZombieVariant zombie && zombie.isDoorRaised());
    }

    public static DefenseIntent.Threat focus(Mob mob) { return FOCI.get(mob); }

    public static void begin(Mob mob, DefenseIntent.Threat threat) {
        if (threat != null) FOCI.put(mob, threat);
    }

    public static void clear(Mob mob) { FOCI.remove(mob); }

    public static void refresh(Mob mob) {
        DefenseIntent.Threat next = DefenseIntent.evaluate(mob, FOCI.get(mob));
        // A released bow or a completed swing must not cancel an already committed guard window.
        // Keep the last observed point; do not track an attacker through cover.
        if (next != null) FOCI.put(mob, next);
    }

    /** Called after the melee/navigation move control and immediately before native look control. */
    public static void beforeLook(Mob mob) {
        if (mob.level().isClientSide || !guarding(mob)) return;
        DefenseIntent.Threat threat = FOCI.get(mob);
        if (threat == null) return;
        Entity attacker = threat.attacker() == null ? threat.source() : threat.attacker();
        if (attacker != mob.getTarget()) {
            mob.getNavigation().stop();
            mob.setSpeed(0F);
            mob.setXxa(0F);
            mob.setZza(0F);
        }
        Vec3 point = threat.guardPoint();
        mob.getLookControl().setLookAt(point.x, point.y, point.z, 25F, 25F);
    }

    /** Shared server/client body motion follows synchronized head rotation instead of snapping to a threat. */
    public static void afterBodyTurn(Mob mob, float previousBodyYaw) {
        if (guarding(mob) && Float.isFinite(previousBodyYaw) && Float.isFinite(mob.yHeadRot))
            mob.yBodyRot = Mth.approachDegrees(previousBodyYaw, mob.yHeadRot, 15F);
    }
}
