package io.github.ningmeng123.modernite.content.wither;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.BossSummonedMobs;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Balances Wither hits and explosions before the target's armor mitigates them. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class WitherDamage {
    public static final float NATIVE_DAMAGE_MULTIPLIER = 5F;
    public static final float OUTGOING_DAMAGE_MULTIPLIER = 0.5F;

    private WitherDamage() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scaleNativeDamage(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide && isNativeWitherDamage(event.getSource())) {
            event.setAmount(event.getAmount() * NATIVE_DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void reduceOutgoingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        // Run after ITE adds the flat bonus from earlier hits by this Wither.
        var source = event.getSource();
        if (source.getEntity() instanceof WitherBoss || source.getDirectEntity() instanceof WitherBoss
                || isNativeWitherDamage(source)) {
            event.setAmount(event.getAmount() * OUTGOING_DAMAGE_MULTIPLIER);
        }
    }

    private static boolean isNativeWitherDamage(DamageSource source) {
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        var direct = source.getDirectEntity();
        if (explosion && direct instanceof WitherBoss) return true;
        if (!(direct instanceof WitherSkull skull) || (!explosion && !source.is(DamageTypes.WITHER_SKULL))) return false;

        var attacker = source.getEntity();
        if (attacker == skull || attacker == null) attacker = skull.getOwner();
        return attacker instanceof WitherBoss || (attacker == null && BossSummonedMobs.isBossHazard(skull));
    }
}
