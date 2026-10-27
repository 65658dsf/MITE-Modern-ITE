package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Only the Time Lord's owned breezes receive persistent, non-stacking improvements. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class TimeLordMinions {
    public static final String MARKER = "modern_ite:time_lord_minion";
    private static final String EXPLICIT_SUPPORT = "modern_ite:time_lord_manual_support";
    public static final double HEALTH_MULTIPLIER = 1.5;
    public static final double DAMAGE_MULTIPLIER = 1.25;
    public static final double SPEED_MULTIPLIER = 1.1;
    private static final ResourceLocation BOOST = ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "time_lord_minion");

    private TimeLordMinions() {}

    static void prepare(Mob mob) {
        if (!(mob instanceof Breeze)) throw new IllegalArgumentException("Time Lord support must be a breeze");
        mob.getPersistentData().putBoolean(MARKER, true);
        apply(mob);
    }

    static void prepare(Mob mob, Mob lord) {
        prepare(mob);
        // An explicitly requested boss encounter must work even in a new test world.
        if (lord instanceof ExtremeMob extreme && extreme.extremeKind() == ExtremeKind.TIME_LORD && SpawnPopulation.explicit(lord))
            mob.getPersistentData().putBoolean(EXPLICIT_SUPPORT, true);
    }

    static boolean explicitSupport(Mob mob) {
        return mob instanceof Breeze && mob.getPersistentData().getBoolean(MARKER)
                && mob.getPersistentData().getBoolean(EXPLICIT_SUPPORT)
                && mob.getPersistentData().hasUUID(SpawnPopulation.SUMMONER);
    }

    private static void apply(Mob mob) {
        float health = mob.getHealth(), maximum = mob.getMaxHealth();
        modifier(mob, Attributes.MAX_HEALTH, HEALTH_MULTIPLIER - 1);
        modifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_MULTIPLIER - 1);
        modifier(mob, Attributes.MOVEMENT_SPEED, SPEED_MULTIPLIER - 1);
        if (maximum != mob.getMaxHealth()) mob.setHealth(Math.min(mob.getMaxHealth(), health / maximum * mob.getMaxHealth()));
    }

    private static void modifier(Mob mob, Holder<Attribute> key, double amount) {
        var attribute = mob.getAttribute(key);
        if (attribute == null) return;
        var existing = attribute.getModifier(BOOST);
        if (existing != null && existing.amount() == amount && existing.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) return;
        attribute.removeModifier(BOOST);
        attribute.addPermanentModifier(new AttributeModifier(BOOST, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && !event.getLevel().isClientSide && event.getEntity() instanceof Breeze breeze
                && breeze.getPersistentData().getBoolean(MARKER)) apply(breeze);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void windDamage(LivingIncomingDamageEvent event) {
        // Vanilla wind-charge direct hits use a fixed damage value, not ATTACK_DAMAGE.
        if (!event.isCanceled() && event.getSource().getDirectEntity() instanceof BreezeWindCharge
                && event.getSource().getEntity() instanceof Breeze breeze && breeze.getPersistentData().getBoolean(MARKER))
            event.setAmount(event.getAmount() * (float) DAMAGE_MULTIPLIER);
    }
}
