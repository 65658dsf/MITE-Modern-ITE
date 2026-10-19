package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native equipment changes must preserve one usable source combat scheduler. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class SkeletonCombatAiGameTests {
    private SkeletonCombatAiGameTests() {}

    private static ExtremeMobs.SkeletonVariant skeleton(GameTestHelper h, ExtremeKind kind) {
        var created = ExtremeMobs.type(kind).create(h.getLevel());
        h.assertTrue(created instanceof ExtremeMobs.SkeletonVariant, "The registered skeleton constructs safely: " + kind);
        var skeleton = (ExtremeMobs.SkeletonVariant)created;
        skeleton.moveTo(h.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        skeleton.setNoAi(true);
        skeleton.setNoGravity(true);
        h.assertTrue(h.getLevel().addFreshEntity(skeleton), "The skeleton passes the real source and addon join handlers: " + kind);
        h.assertTrue(skeleton.extremeMemory().initialized, "The addon join handler initializes the fresh combat state: " + kind);
        return skeleton;
    }

    private static SourceRangedAttackGoal assertCombatGoals(GameTestHelper h, ExtremeMobs.SkeletonVariant skeleton, boolean bow) {
        var goals = skeleton.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal()).toList();
        long melee = goals.stream().filter(SourceMeleeGoal.class::isInstance).count();
        long ranged = goals.stream().filter(SourceRangedAttackGoal.class::isInstance).count();
        String context = skeleton.extremeKind() + " holding " + skeleton.getMainHandItem() + " goals="
                + goals.stream().map(goal -> goal.getClass().getSimpleName()).toList();
        h.assertTrue(melee == (bow ? 0 : 1) && ranged == (bow ? 1 : 0),
                "Only the source attack matching the held weapon remains: " + context);
        h.assertTrue(goals.stream().noneMatch(goal -> goal instanceof MeleeAttackGoal
                        || goal instanceof RangedBowAttackGoal<?> || goal instanceof RangedAttackGoal),
                "Native equipment reassessment never reinstalls a competing vanilla attack: " + context);
        return bow ? goals.stream().filter(SourceRangedAttackGoal.class::isInstance)
                .map(SourceRangedAttackGoal.class::cast).findFirst().orElseThrow() : null;
    }

    @GameTest(template = "gear_empty")
    public static void everySkeletonChangesBetweenBowUnarmedAndSwordThroughNativeEquipment(GameTestHelper h) {
        int checked = 0;
        for (var kind : ExtremeKind.values()) {
            if (kind.family != ExtremeKind.Family.SKELETON) continue;
            var skeleton = skeleton(h, kind);
            try {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                assertCombatGoals(h, skeleton, true);
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                assertCombatGoals(h, skeleton, false);
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                assertCombatGoals(h, skeleton, false);
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                assertCombatGoals(h, skeleton, true);
                checked++;
            } finally {
                skeleton.discard();
            }
        }
        h.assertTrue(checked == 11, "All eleven Extreme skeleton identities cover disarming and weapon pickup");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void fastArrowKeepsThirtyTickCadenceAcrossJoinAndWeaponChanges(GameTestHelper h) {
        var skeleton = skeleton(h, ExtremeKind.SKELETON_FAST_ARROW);
        try {
            var ranged = assertCombatGoals(h, skeleton, true);
            int expected = R196MonsterAi.frenzied(skeleton) ? (int)(30 * .67F) : 30;
            h.assertTrue(ranged.attackInterval() == expected,
                    "Real source join preserves the fast archer's thirty-tick base cadence; actual=" + ranged.attackInterval());
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            assertCombatGoals(h, skeleton, false);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            h.assertTrue(assertCombatGoals(h, skeleton, true).attackInterval() == expected,
                    "Picking up another bow restores the same fast cadence");
        } finally {
            skeleton.discard();
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void unchangedBowAndArmorChangesPreserveTheRunningShotCountdown(GameTestHelper h) {
        var skeleton = skeleton(h, ExtremeKind.MIRROR_SKELETON);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        try {
            player.moveTo(skeleton.position().add(4, 0, 0));
            skeleton.setTarget(player);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            var ranged = assertCombatGoals(h, skeleton, true);
            h.assertTrue(ranged.canUse(), "The real source ranged goal acquires the player");
            ranged.start();
            ranged.tick();
            int remaining = ranged.remainingAttackTicks();
            h.assertTrue(remaining > 0, "The running bow goal has a pending shot");
            skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, skeleton.getMainHandItem().copy());
            h.assertTrue(assertCombatGoals(h, skeleton, true) == ranged && ranged.remainingAttackTicks() == remaining,
                    "Mirror armor and equivalent bow copies preserve the active source goal and shot countdown");
            ranged.stop();
        } finally {
            skeleton.discard();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void runningBowAndMeleeGoalsReleaseTheirControlsWhenEquipmentChanges(GameTestHelper h) {
        var skeleton = skeleton(h, ExtremeKind.MIRROR_SKELETON);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        try {
            player.moveTo(skeleton.position().add(.9, 0, 0));
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            player.setHealth(1000);
            skeleton.setTarget(player);
            var originalBow = assertCombatGoals(h, skeleton, true);
            skeleton.goalSelector.tick();
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream()
                            .anyMatch(wrapped -> wrapped.getGoal() == originalBow && wrapped.isRunning()) && skeleton.isUsingItem(),
                    "The real selector starts its source bow goal and active bow use before disarming");

            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            skeleton.goalSelector.tick();
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream()
                            .anyMatch(wrapped -> wrapped.getGoal() instanceof SourceMeleeGoal && wrapped.isRunning()),
                    "Replacing a running bow releases MOVE and LOOK so the source melee goal actually starts");
            h.assertTrue(!skeleton.isUsingItem(), "Stopping the old bow goal also clears the stale item-use state");
            assertCombatGoals(h, skeleton, false);

            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            skeleton.goalSelector.tick();
            var replacementBow = assertCombatGoals(h, skeleton, true);
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream()
                            .anyMatch(wrapped -> wrapped.getGoal() == replacementBow && wrapped.isRunning()) && skeleton.isUsingItem(),
                    "Replacing a running melee goal releases its controls and starts the source bow goal again");
        } finally {
            skeleton.discard();
            player.discard();
        }
        h.succeed();
    }
}
