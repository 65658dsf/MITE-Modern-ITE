package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;

/** Visible, bounded pulses affect players without changing any other entity's clock. @author NingMeng123 */
public final class TimeLordAbilities {
    public static final int SUMMON_INTERVAL = 100;
    public static final int MAX_SUMMONS = 6;
    public static final double SUMMON_RANGE = 32;
    private TimeLordAbilities() {}

    public static void tick(Mob lord, MobMemory memory, LivingEntity target) {
        if (!(lord.level() instanceof ServerLevel level) || !(target instanceof Player player)
                || player.isCreative() || player.isSpectator()) return;
        if (memory.summons < MAX_SUMMONS && memory.supportCooldown == 0
                && lord.distanceToSqr(target) <= SUMMON_RANGE * SUMMON_RANGE && lord.getSensing().hasLineOfSight(target)) {
            // Reuse the ordinary support admission, spawn finalization and cleanup pipeline.
            Mob support = MobAbilities.summonType(lord, EntityType.BREEZE, 1, false, mob -> TimeLordMinions.prepare(mob, lord));
            memory.supportCooldown = SUMMON_INTERVAL;
            if (support instanceof Breeze breeze) {
                breeze.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                memory.summons++;
                lord.playSound(SoundEvents.BREEZE_IDLE_GROUND, 1, .7F);
            }
        }
        double range = IteConfig.number("Entities.time_lord_effect_range");
        if (lord.distanceToSqr(target) > range * range || !lord.getSensing().hasLineOfSight(target)) return;
        if (memory.cooldown == 20) {
            lord.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, .6F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, lord.getX(), lord.getY() + 1, lord.getZ(), 32, .7, 1, .7, .03);
            player.displayClientMessage(Component.translatable("message.modern_ite.time_lord_warning"), true);
        }
        if (memory.cooldown != 0) return;
        memory.cooldown = IteConfig.integer("Entities.time_lord_cast_interval");
        int duration = IteConfig.integer("Entities.time_lord_effect_duration");
        for (var victim : level.getEntitiesOfClass(Player.class, lord.getBoundingBox().inflate(range),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && lord.distanceToSqr(p) <= range * range
                        && lord.getSensing().hasLineOfSight(p))) {
            victim.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), duration), lord);
            victim.displayClientMessage(Component.translatable("message.modern_ite.time_dilation"), true);
            level.sendParticles(ParticleTypes.ENCHANT, victim.getX(), victim.getY() + 1, victim.getZ(), 20, .5, .8, .5, .1);
        }
        lord.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.5F, .5F);
    }
}
