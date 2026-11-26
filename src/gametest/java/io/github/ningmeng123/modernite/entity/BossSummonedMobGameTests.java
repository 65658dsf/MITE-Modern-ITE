package io.github.ningmeng123.modernite.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Boss summons keep their combat protections through the real join, damage and target pipelines. @author NingMeng123 */
@GameTestHolder("modern_ite_boss")
@PrefixGameTestTemplate(false)
public final class BossSummonedMobGameTests {
    private BossSummonedMobGameTests() {}

    @GameTest(template = "gear_empty")
    public static void bothBossWavesIgnoreDamageFromEitherBoss(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(500, 500, true);
            var wither = (WitherBoss) site.mob(EntityType.WITHER);
            var dragon = (EnderDragon) site.mob(EntityType.ENDER_DRAGON);
            dragon.setPos(site.origin.add(0, 10, 0));
            h.assertTrue(site.level.addFreshEntity(wither) && site.level.addFreshEntity(dragon), "Both bosses join the test world");

            List<Mob> witherSummons = new ArrayList<>();
            List<Mob> dragonSummons = new ArrayList<>();
            Consumer<EntityJoinLevelEvent> capture = event -> {
                if (event.getLevel() != site.level || !(event.getEntity() instanceof Mob mob)) return;
                var data = mob.getPersistentData();
                if (!data.hasUUID(SpawnPopulation.SUMMONER)) return;
                var summoner = data.getUUID(SpawnPopulation.SUMMONER);
                if (summoner.equals(wither.getUUID())) witherSummons.add(mob);
                if (summoner.equals(dragon.getUUID())) dragonSummons.add(mob);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, capture);
            try {
                ExtremeNativeMobs.witherWave(wither);
                dragon.setHealth(dragon.getMaxHealth() / 3F);
                dragon.tickCount = 1;
                ExtremeNativeMobs.tick(new EntityTickEvent.Post(dragon));
            } finally {
                NeoForge.EVENT_BUS.unregister(capture);
                site.mobs.addAll(witherSummons);
                site.mobs.addAll(dragonSummons);
            }
            h.assertTrue(!witherSummons.isEmpty() && !dragonSummons.isEmpty(), "The actual waves from both bosses produce allied mobs");

            var skull = new WitherSkull(EntityType.WITHER_SKULL, site.level);
            skull.setOwner(wither);
            var cloud = new AreaEffectCloud(site.level, 0D, 0D, 0D);
            cloud.setOwner(dragon);
            var sources = site.level.damageSources();
            var bossDamage = List.of(sources.mobAttack(wither), sources.mobAttack(dragon),
                    sources.witherSkull(skull, wither), Explosion.getDefaultDamageSource(site.level, skull),
                    Explosion.getDefaultDamageSource(site.level, wither), sources.indirectMagic(cloud, dragon));
            try {
                for (var summons : List.of(witherSummons, dragonSummons)) {
                    for (Mob summon : summons) {
                        h.assertTrue(summon.isAddedToLevel(), "The allied mob passed the real join handlers");
                        for (DamageSource damage : bossDamage) {
                            float before = summon.getHealth();
                            h.assertFalse(summon.hurt(damage, 2F), "A boss's damage is rejected by either boss's ally: " + damage);
                            h.assertValueEqual(summon.getHealth(), before, "The protected ally retains all its health");
                        }
                    }
                }

                Mob control = site.mob(EntityType.COW);
                for (DamageSource damage : bossDamage) {
                    control.invulnerableTime = 0;
                    control.setHealth(control.getMaxHealth());
                    h.assertTrue(control.hurt(damage, 1F) && control.getHealth() < control.getMaxHealth(),
                            "An ordinary mob still takes damage from the same source: " + damage);
                }
            } finally {
                skull.discard();
                cloud.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void reloadedBossAllyTargetsOnlyEligiblePlayersEvenInRetaliation(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            var original = site.mob(EntityType.SKELETON);
            BossSummonedMobs.mark(original);
            var ally = (PathfinderMob) site.mob(EntityType.SKELETON);
            ally.load(original.saveWithoutId(new CompoundTag()));
            var control = (PathfinderMob) site.mob(EntityType.SKELETON);
            var attacker = site.mob(EntityType.ZOMBIE);
            attacker.setPos(site.origin.add(1, 0, 0));

            var survivor = site.player(2);
            var creative = h.makeMockPlayer(GameType.CREATIVE);
            var spectator = h.makeMockPlayer(GameType.SPECTATOR);
            var invulnerable = h.makeMockPlayer(GameType.SURVIVAL);
            invulnerable.getAbilities().invulnerable = true;
            for (var player : List.of(survivor, creative, spectator, invulnerable)) player.setPos(site.origin.add(2, 0, 0));
            ally.setTarget(survivor);
            h.assertTrue(ally.getTarget() == survivor, "A loaded ally can still select a survival player");
            for (var denied : List.of(creative, spectator, invulnerable, attacker)) {
                ally.setTarget(denied);
                h.assertTrue(ally.getTarget() == null || ally.getTarget() == survivor,
                        "An ally cannot acquire creative, spectator, invulnerable or non-player targets");
            }
            ally.setTarget(null);

            ally.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            ally.setHealth(100F);
            h.assertTrue(ally.hurt(site.level.damageSources().playerAttack(survivor), 2F) && ally.getHealth() < 100F,
                    "The registered survival player's fresh hit still damages the boss ally, remaining health " + ally.getHealth());
            for (var skeleton : List.of(ally, control)) {
                skeleton.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
                skeleton.setHealth(100F);
                skeleton.invulnerableTime = 0;
                skeleton.tickCount = 1;
                h.assertTrue(skeleton.hurt(site.level.damageSources().mobAttack(attacker), 2F),
                        "An ordinary mob can still hurt an ally and the control");
                var retaliation = new HurtByTargetGoal(skeleton);
                if (retaliation.canUse()) retaliation.start();
            }
            h.assertTrue(ally.getTarget() == null, "The real retaliation goal cannot acquire the attacking non-player mob");
            h.assertTrue(control.getTarget() == attacker, "The ordinary skeleton still retaliates against that same attacker");
        }
        h.succeed();
    }
}
