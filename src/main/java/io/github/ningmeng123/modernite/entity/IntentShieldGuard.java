package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;

/** Intent-driven starts retain finite native shield use and mandatory counterattack openings. @author NingMeng123 */
public final class IntentShieldGuard {
    private final Mob mob;
    private int remaining, nextAttempt;
    private DefenseIntent.Threat pending;

    public IntentShieldGuard(Mob mob) { this.mob = mob; }

    private boolean equipped() {
        return IteConfig.bool("mob_defense") && !mob.level().isClientSide && mob.isAlive() && !mob.isRemoved()
                && MobShields.melee(mob.getMainHandItem()) && MobShields.shield(mob.getOffhandItem())
                && !MobShields.disabled(mob);
    }

    public boolean canUse() {
        if (!equipped() || mob.tickCount < nextAttempt || mob.isUsingItem() || mob.swinging
                || BetterMobCombat.attacking(mob)) return false;
        pending = DefenseIntent.evaluate(mob);
        return pending != null;
    }

    public void start() {
        if (!equipped() || pending == null) return;
        remaining = 18 + mob.getRandom().nextInt(13);
        DefenseGuardFocus.begin(mob, pending);
        mob.startUsingItem(InteractionHand.OFF_HAND);
    }

    public boolean canContinue() {
        return remaining > 0 && equipped() && MobShields.defending(mob)
                && mob.getUseItem() == mob.getOffhandItem();
    }

    public void tick() {
        DefenseGuardFocus.refresh(mob);
        --remaining;
    }

    public void stop() {
        if (MobShields.defending(mob)) mob.stopUsingItem();
        DefenseGuardFocus.clear(mob);
        pending = null;
        remaining = 0;
        nextAttempt = mob.tickCount + 24 + mob.getRandom().nextInt(17);
    }
}
