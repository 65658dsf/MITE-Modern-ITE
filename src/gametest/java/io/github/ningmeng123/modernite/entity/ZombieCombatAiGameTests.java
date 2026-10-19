package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered zombie selectors must execute every target accepted by source combat. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ZombieCombatAiGameTests {
    private ZombieCombatAiGameTests() {}

    private record Fight(PathfinderMob zombie, Mob target, Vec3 origin) implements AutoCloseable {
        void holdPositions() {
            zombie.setPos(origin);
            target.setPos(origin.add(0D, 0D, .9D));
            zombie.setDeltaMovement(Vec3.ZERO);
            target.setDeltaMovement(Vec3.ZERO);
        }
        @Override public void close() {
            BetterMobCombat.cancel(zombie);
            zombie.discard();
            target.discard();
        }
    }

    private static Fight fight(GameTestHelper h, EntityType<? extends Mob> targetType) {
        var created = ExtremeMobs.type(ExtremeKind.MIRROR_ZOMBIE).create(h.getLevel());
        h.assertTrue(created instanceof PathfinderMob, "The real registered extreme zombie is a pathfinding mob");
        var zombie = (PathfinderMob)created;
        var target = targetType.create(h.getLevel());
        h.assertTrue(target != null, "The real retaliation or mount target creates");
        var anchor = h.absolutePos(new BlockPos(2, 10, 2));
        var origin = new Vec3(anchor.getX() + .5D, Math.max(128D, anchor.getY() + 64D), anchor.getZ() + .5D);
        var fight = new Fight(zombie, target, origin);
        fight.holdPositions();
        zombie.setNoAi(true);
        target.setNoAi(true);
        zombie.setNoGravity(true);
        target.setNoGravity(true);
        zombie.tickCount = 100;
        zombie.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
        h.assertTrue(h.getLevel().addFreshEntity(zombie) && h.getLevel().addFreshEntity(target),
                "Actual level joins install both production combat selectors");
        for (var slot : EquipmentSlot.values()) if (slot != EquipmentSlot.BODY) zombie.setItemSlot(slot, ItemStack.EMPTY);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.removeAllEffects();
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2D);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        zombie.setHealth(1000F);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        target.setHealth(1000F);
        // Refresh native equipment modifiers while NoAI keeps the real selectors idle.
        zombie.tick();
        fight.holdPositions();
        zombie.setYRot(0F);
        zombie.setYHeadRot(0F);
        zombie.yBodyRot = 0F;
        return fight;
    }

    private static void selectedMeleeDamagesTarget(GameTestHelper h, Fight fight) {
        var zombie = fight.zombie();
        var target = fight.target();
        h.assertTrue(zombie.getTarget() == target, "The production target selector has accepted the nonstandard target");
        h.assertTrue(zombie.goalSelector.getAvailableGoals().stream()
                        .anyMatch(w -> w.getGoal() instanceof SourceMeleeGoal && w.getGoal().canUse()),
                "An idle joined extreme zombie has a usable melee task for its accepted target");
        float before = target.getHealth();
        target.invulnerableTime = 0;
        zombie.goalSelector.tick();
        WrappedGoal selected = zombie.goalSelector.getAvailableGoals().stream()
                .filter(w -> w.isRunning() && w.getGoal() instanceof SourceMeleeGoal).findFirst().orElse(null);
        h.assertTrue(selected != null && selected.getPriority() == 5,
                "The real goal selector runs its lower-priority fallback, preserving the three source prey tasks");
        if (BetterCombatBridge.enabled()) {
            var profile = BetterCombatBridge.profile(zombie, 0);
            h.assertTrue(profile != null && BetterMobCombat.attacking(zombie),
                    "The selector's actual fallback starts the equipped sword's Better Combat windup");
            h.assertValueEqual(target.getHealth(), before, "The selected fallback respects the delayed weapon impact");
            for (int tick = 0; tick < profile.windupTicks(); ++tick) {
                ++zombie.tickCount;
                target.invulnerableTime = 0;
                fight.holdPositions();
                zombie.goalSelector.tick();
                zombie.getNavigation().stop();
                zombie.getLookControl().setLookAt(target, 30F, 30F);
                zombie.getLookControl().tick();
                BetterMobCombat.tick(zombie);
            }
        }
        h.assertTrue(target.getHealth() < before,
                "The production-selected fallback completes real native target damage with or without Better Combat");
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void idleExtremeZombieCanRetaliateAgainstAnIronGolem(GameTestHelper h) {
        try (var fight = fight(h, EntityType.IRON_GOLEM)) {
            fight.target().getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4D);
            h.assertTrue(fight.target().doHurtTarget(fight.zombie()), "A real golem melee hit gives the zombie a revenge target");
            h.assertTrue(fight.zombie().getLastHurtByMob() == fight.target(), "Native damage records the actual golem attacker");
            fight.holdPositions();
            fight.zombie().targetSelector.tick();
            selectedMeleeDamagesTarget(h, fight);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void idleExtremeZombieCanAttackTheSelectedBatMount(GameTestHelper h) {
        try (var fight = fight(h, EntityType.BAT)) {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            try {
                player.moveTo(fight.target().position());
                h.assertTrue(player.startRiding(fight.target(), true), "The player uses the bat mount allowed by extreme gameplay");
                fight.zombie().setTarget(player);
                h.assertTrue(fight.zombie().targetSelector.getAvailableGoals().stream()
                                .anyMatch(w -> w.getGoal() instanceof SourceMonsterTargeting.Nearest),
                        "The real level join installed source target selection");
                fight.zombie().targetSelector.tick();
                h.assertTrue(fight.zombie().getTarget() == fight.target(), "Source targeting redirects the player to the living bat mount");
                selectedMeleeDamagesTarget(h, fight);
            } finally {
                player.stopRiding();
                player.discard();
            }
        }
        h.succeed();
    }
}
