package io.github.ningmeng123.modernite.entity;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies ITE's damage and phase compatibility against the actual Ender Dragon Plus mod. @author NingMeng123 */
@GameTestHolder("modern_ite_dragonplus")
@PrefixGameTestTemplate(false)
public final class EnderDragonPlusCombatGameTests {
    private EnderDragonPlusCombatGameTests() {}

    @GameTest(template = "gear_empty")
    public static void enhancedDragonUsesIteHeadDamageAtBothHealthThresholds(GameTestHelper h)
            throws ReflectiveOperationException {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            EnderDragon dragon = spawnDragon(h, site);
            var headContact = EnderDragon.class.getDeclaredMethod("hurt", List.class);
            headContact.setAccessible(true);
            for (float expected : new float[]{30F, 42.5F}) {
                var target = site.mob(EntityType.COW);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
                target.setHealth(100F);
                headContact.invoke(dragon, List.of(target));
                h.assertTrue(Math.abs(100F - target.getHealth() - expected) < .01F,
                        "The real enhanced dragon head hit deals " + expected + " damage");
                dragon.setHealth(dragon.getMaxHealth() / 4F);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void finalNovaDealsScaledDamageBeforeIteForceChargeResumes(GameTestHelper h)
            throws ReflectiveOperationException {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            EnderDragon dragon = spawnDragon(h, site);
            dragon.setFightOrigin(h.absolutePos(new BlockPos(1, 1, 1)));
            dragon.setHealth(dragon.getMaxHealth() / 5F);
            dragon.getPersistentData().putBoolean("modern_ite:dragon_half", true);
            dragon.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1D);
            var brain = dragon.getClass().getMethod("edp$getBrain").invoke(dragon);
            var center = (Vec3) brain.getClass().getMethod("arenaCenter").invoke(brain);
            dragon.setPos(center.add(0, 28, 0));
            var target = site.player(h.getLevel(), center);
            var spawnProtection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);
            spawnProtection.setInt(target, 0);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            target.setHealth(100F);

            var phases = Class.forName("com.enderdragonplus.dragon.DragonPhases");
            var finalNova = (EnderDragonPhase<?>) phases.getField("FINAL_NOVA").get(null);
            var manager = dragon.getPhaseManager();
            manager.setPhase(finalNova);
            DragonPhaseInstance nova = manager.getCurrentPhase();
            for (int tick = 1; tick <= 101; tick++) {
                dragon.tickCount = tick;
                nova.doServerTick();
                ExtremeNativeMobs.tick(new EntityTickEvent.Post(dragon));
                h.assertTrue(manager.getCurrentPhase() == nova, "ITE keeps Final Nova active through tick " + tick);
            }
            h.assertValueEqual(target.getHealth(), 70F,
                    "The actual Nova detonation deals 1 attack × 3 skill multiplier × 10 ITE multiplier");

            dragon.tickCount = 120;
            ExtremeNativeMobs.tick(new EntityTickEvent.Post(dragon));
            h.assertTrue(manager.getCurrentPhase().getPhase() == EnderDragonPhase.CHARGING_PLAYER,
                    "ITE can start its original charge once Final Nova has dealt damage");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void onlySkillTriggeredCrystalAndMeteorExplosionsDealScaledDamage(GameTestHelper h) throws Exception {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            EnderDragon dragon = spawnDragon(h, site);
            Mob target = site.mob(EntityType.COW);
            target.setPos(site.origin.add(5, 0, 0));
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024D);
            h.assertTrue(site.level.addFreshEntity(target), "The same unarmored target can take each explosion");

            var ordinaryCrystal = crystal(site.level, site.origin);
            float ordinaryCrystalDamage = explosionHit(h, target, ordinaryCrystal,
                    () -> ordinaryCrystal.hurt(site.level.damageSources().generic(), 1F));
            var shieldCrystal = crystal(site.level, site.origin);
            var shield = Class.forName("com.enderdragonplus.dragon.phase.ShieldAscensionPhase");
            var phase = shield.getConstructor(EnderDragon.class).newInstance(dragon);
            var crystals = shield.getDeclaredField("crystals");
            crystals.setAccessible(true);
            @SuppressWarnings("unchecked")
            var shieldCrystals = (List<EndCrystal>) crystals.get(phase);
            shieldCrystals.add(shieldCrystal);
            var explode = shield.getDeclaredMethod("explodeCrystals");
            explode.setAccessible(true);
            var config = Class.forName("com.enderdragonplus.EdpConfig");
            var explodeRatio = (ModConfigSpec.DoubleValue) config.getField("SHIELD_CRYSTAL_EXPLODE_RATIO").get(null);
            double previousRatio = explodeRatio.get();
            try {
                explodeRatio.set(1D);
                float shieldDamage = explosionHit(h, target, shieldCrystal, () -> explode.invoke(phase));
                h.assertValueEqual(shieldDamage, ordinaryCrystalDamage * 10F, "The same crystal blast is amplified only by the shield skill");
            } finally {
                explodeRatio.set(previousRatio);
                shieldCrystal.discard();
                ordinaryCrystal.discard();
            }

            target.setPos(site.origin.add(2, 0, 0));
            float ordinaryBlast = explosionHit(h, target, null, () -> site.level.explode(null,
                    site.origin.x, site.origin.y, site.origin.z, 2F, Level.ExplosionInteraction.MOB));
            float dragonBlast = explosionHit(h, target, dragon, () -> site.level.explode(dragon,
                    site.origin.x, site.origin.y, site.origin.z, 2F, Level.ExplosionInteraction.MOB));
            h.assertValueEqual(dragonBlast, ordinaryBlast * .5F, "A plain blast attributed to the dragon deals half damage");
            var brain = dragon.getClass().getMethod("edp$getBrain").invoke(dragon);
            var meteor = Class.forName("com.enderdragonplus.dragon.hazard.MeteorHazard");
            var hazard = meteor.getConstructor(Vec3.class, int.class, double.class, double.class, boolean.class)
                    .newInstance(site.origin, 1, 1D, 0D, true);
            var tick = meteor.getMethod("tick", ServerLevel.class, brain.getClass());
            float meteorBlast = explosionHit(h, target, dragon, () -> tick.invoke(hazard, site.level, brain));
            h.assertValueEqual(meteorBlast, ordinaryBlast * 10F, "The optional meteor blast amplifies damage at its original radius");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void wingAndVanillaBreathDealHalfDamageAndStillSpareAllies(GameTestHelper h)
            throws ReflectiveOperationException {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            EnderDragon dragon = spawnDragon(h, site);
            dragon.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10D);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            var target = site.mob(EntityType.COW);
            var ally = site.mob(EntityType.COW);
            ally.setPos(site.origin.add(1, 0, 0));
            BossSummonedMobs.mark(ally);
            for (Mob victim : List.of(target, ally)) {
                victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
                victim.setHealth(100F);
                victim.tickCount = 3;
                h.assertTrue(site.level.addFreshEntity(victim), "Each test victim enters the real entity index");
            }
            var wingContact = EnderDragon.class.getDeclaredMethod("knockBack", ServerLevel.class, List.class);
            wingContact.setAccessible(true);
            wingContact.invoke(dragon, site.level, List.of(target, ally));
            h.assertValueEqual(target.getHealth(), 97.5F, "The real wing contact deals half of its 5 damage");
            h.assertValueEqual(ally.getHealth(), 100F, "The boss ally still ignores wing damage");
            assertBreathDamage(h, site.level, target, ally, dragon, false, 3F, 0F);
            assertBreathDamage(h, site.level, target, ally, dragon, true, 3F, 0F);
            assertBreathDamage(h, site.level, target, ally, null, false, 6F, 6F);
        }
        h.succeed();
    }

    private static void assertBreathDamage(GameTestHelper h, ServerLevel level, Mob target, Mob ally,
            EnderDragon owner, boolean removeOwner, float targetDamage, float allyDamage) {
        Vec3 center = target.position();
        var cloud = new AreaEffectCloud(level, center.x, center.y, center.z);
        cloud.setOwner(owner);
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.setRadius(2F);
        cloud.setWaitTime(0);
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
        h.assertTrue(level.addFreshEntity(cloud), "The native breath cloud runs its real join attribution");
        try {
            if (removeOwner) cloud.setOwner(null);
            for (Mob victim : List.of(target, ally)) {
                victim.setHealth(100F);
                victim.invulnerableTime = 0;
            }
            cloud.tickCount = 4;
            level.tickNonPassenger(cloud);
            h.assertValueEqual(target.getHealth(), 100F - targetDamage, "Breath damage follows the cloud's boss attribution");
            h.assertValueEqual(ally.getHealth(), 100F - allyDamage, "Only dragon-attributed breath is harmless to boss allies");
        } finally {
            cloud.discard();
        }
    }

    private static EndCrystal crystal(ServerLevel level, Vec3 position) {
        var crystal = EntityType.END_CRYSTAL.create(level);
        crystal.setPos(position);
        level.addFreshEntity(crystal);
        return crystal;
    }

    private static float explosionHit(GameTestHelper h, Mob target, Entity source, Callable<?> trigger) throws Exception {
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        float[] hit = {0F};
        Consumer<LivingIncomingDamageEvent> capture = event -> {
            if (event.getEntity() == target && event.getSource().getDirectEntity() == source
                    && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) hit[0] = event.getAmount();
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, capture);
        try {
            trigger.call();
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
        }
        h.assertTrue(hit[0] > 0F, "The real explosion reaches a target inside the original radius");
        return hit[0];
    }

    private static EnderDragon spawnDragon(GameTestHelper h, SpawnBalanceGameTests.Site site)
            throws ReflectiveOperationException {
        var dragon = (EnderDragon) site.mob(EntityType.ENDER_DRAGON);
        h.assertTrue(h.getLevel().addFreshEntity(dragon), "The dragon executes both mods' actual join handlers");
        var brain = dragon.getClass().getMethod("edp$getBrain").invoke(dragon);
        h.assertTrue((boolean) brain.getClass().getMethod("active").invoke(brain), "Ender Dragon Plus activates");
        return dragon;
    }
}
