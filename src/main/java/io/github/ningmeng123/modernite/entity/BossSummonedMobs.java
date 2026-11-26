package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Boss summons only target players and are immune to damage from either boss. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class BossSummonedMobs {
    static final float DRAGON_DAMAGE_MULTIPLIER = 0.5F;
    private static final String SUMMONED = "modern_ite:boss_summoned";
    private static final String BOSS_HAZARD = "modern_ite:boss_hazard";

    private BossSummonedMobs() {}

    public static void mark(Mob mob) { mob.getPersistentData().putBoolean(SUMMONED, true); }

    public static boolean isSummoned(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        var data = mob.getPersistentData();
        return data.getBoolean(SUMMONED) || data.getBoolean("modern_ite:wither_minion")
                || data.getBoolean("modern_ite:dragon_summoned");
    }

    public static boolean isBoss(Entity entity) { return entity instanceof WitherBoss || entity instanceof EnderDragon; }

    public static void markBossHazard(Entity entity) { entity.getPersistentData().putBoolean(BOSS_HAZARD, true); }

    public static void clearBossHazard(Entity entity) { entity.getPersistentData().remove(BOSS_HAZARD); }

    public static boolean isBossHazard(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(BOSS_HAZARD);
    }

    public static boolean excludesTarget(Mob mob, LivingEntity target) {
        return target != null && isSummoned(mob) && (!(target instanceof Player player)
                || !player.isAlive() || player.level() != mob.level() || player.isCreative() || player.isSpectator()
                || player.getAbilities().invulnerable);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void target(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide
                && excludesTarget(mob, event.getNewAboutToBeSetTarget())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!isSummoned(mob) && (mob.tickCount % 20 != 0 || !restoreSummonMark(mob, level))) return;
        if (excludesTarget(mob, mob.getTarget())) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        var brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)
                && excludesTarget(mob, brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    private static boolean restoreSummonMark(Mob mob, ServerLevel level) {
        // Older periodic dragon summons recorded just the summoner UUID.
        var data = mob.getPersistentData();
        if (!data.hasUUID(SpawnPopulation.SUMMONER)
                || !isBoss(level.getEntity(data.getUUID(SpawnPopulation.SUMMONER)))) return false;
        mark(mob);
        return true;
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var entity = event.getEntity();
        if (entity instanceof Mob mob && !isSummoned(mob)) restoreSummonMark(mob, level);
        if ((entity instanceof WitherSkull skull && skull.getOwner() instanceof WitherBoss)
                || (entity instanceof DragonFireball fireball && fireball.getOwner() instanceof EnderDragon)
                || (entity instanceof AreaEffectCloud cloud && isBoss(cloud.getOwner()))) {
            markBossHazard(entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent event) {
        var victim = event.getEntity();
        if (victim.level().isClientSide) return;
        var source = event.getSource();
        var direct = source.getDirectEntity();
        var attacker = source.getEntity();
        if (direct instanceof Projectile projectile && (attacker == null || attacker == direct)) {
            attacker = projectile.getOwner();
        } else if (direct instanceof AreaEffectCloud cloud && (attacker == null || attacker == direct)) {
            attacker = cloud.getOwner();
        }
        boolean markedHazard = (attacker == null || attacker == direct) && isBossHazard(direct);
        boolean bossDamage = isBoss(attacker) || isBoss(direct) || markedHazard;
        if ((bossDamage && isSummoned(victim)) || (isBoss(victim) && isSummoned(attacker))) {
            event.setCanceled(true);
            return;
        }

        if (attacker instanceof EnderDragon || direct instanceof EnderDragon || (markedHazard && isDragonHazard(direct))) {
            event.setAmount(event.getAmount() * DRAGON_DAMAGE_MULTIPLIER);
        }
    }

    private static boolean isDragonHazard(Entity direct) {
        return direct instanceof EndCrystal || direct instanceof DragonFireball
                || (direct instanceof AreaEffectCloud cloud && cloud.getParticle().getType() == ParticleTypes.DRAGON_BREATH);
    }

    @SubscribeEvent
    public static void lightning(EntityStruckByLightningEvent event) {
        if (isBossHazard(event.getLightning()) && isSummoned(event.getEntity())) event.setCanceled(true);
    }
}
