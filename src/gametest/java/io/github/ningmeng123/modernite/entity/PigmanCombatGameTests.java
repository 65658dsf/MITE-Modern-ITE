package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native pigman goal scheduling after losing a bow or losing a firing lane. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class PigmanCombatGameTests {
    private PigmanCombatGameTests() {}

    private static ZombifiedPiglin pigman(GameTestHelper h, Vec3 position) {
        return pigman(h, position, new ItemStack(Items.BOW));
    }

    private static ZombifiedPiglin pigman(GameTestHelper h, Vec3 position, ItemStack weapon) {
        ZombifiedPiglin pig = EntityType.ZOMBIFIED_PIGLIN.create(h.getLevel());
        h.assertTrue(pig != null, "A native pigman creates successfully");
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setPos(position);
        pig.setOnGround(true);
        pig.getPersistentData().putBoolean("modern_ite:pigman_rolls", true);
        pig.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        h.assertTrue(h.getLevel().addFreshEntity(pig), "The real entity join installs the native pigman combat goals");
        return pig;
    }

    private static Player target(GameTestHelper h, Vec3 position) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setNoGravity(true);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        player.setHealth(1000F);
        player.setPos(position);
        return player;
    }

    private static void advanceSelector(ZombifiedPiglin pig, Player player, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            ++pig.tickCount;
            player.invulnerableTime = 0;
            pig.goalSelector.tick();
            pig.getLookControl().tick();
            BetterMobCombat.tick(pig);
        }
    }

    private static void discardPigman(GameTestHelper h, ZombifiedPiglin pig) {
        h.getLevel().getEntitiesOfClass(AbstractArrow.class, pig.getBoundingBox().inflate(64), arrow -> arrow.getOwner() == pig)
                .forEach(AbstractArrow::discard);
        BetterMobCombat.cancel(pig);
        pig.discard();
    }

    @GameTest(template = "gear_empty")
    public static void disarmedBowPigmanResumesRealMeleeThroughItsSelector(GameTestHelper h) {
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 160, 1)));
        ZombifiedPiglin pig = pigman(h, origin);
        Player player = target(h, origin.add(0D, 0D, .9D));
        try {
            pig.setTarget(player);
            pig.goalSelector.tick();
            h.assertTrue(pig.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof PigmanBowGoal),
                    "The installed bow goal actually runs before disarming");
            // The main mod's disarming enchantment clears this exact equipment slot.
            pig.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            float health = player.getHealth();
            advanceSelector(pig, player, 12);
            h.assertTrue(player.getHealth() < health,
                    "Losing the bow allows the real selector to run a damaging unarmed melee attack");
        } finally {
            discardPigman(h, pig);
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void bowPigmanPursuesATargetBehindNearbyCover(GameTestHelper h) {
        BlockPos origin = h.absolutePos(new BlockPos(1, 224, 1));
        Map<BlockPos, BlockState> changed = new LinkedHashMap<>();
        for (int x = 0; x <= 10; x++) for (int z = -3; z <= 3; z++)
            block(h, changed, origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++)
            block(h, changed, origin.offset(4, y, z), Blocks.STONE.defaultBlockState());
        ZombifiedPiglin pig = pigman(h, Vec3.atBottomCenterOf(origin));
        Player player = target(h, Vec3.atBottomCenterOf(origin.offset(8, 0, 0)));
        try {
            pig.setTarget(player);
            h.assertTrue(pig.distanceToSqr(player) < 225D && !pig.getSensing().hasLineOfSight(player),
                    "The target has a walkable route around cover inside the normal standing firing range");
            pig.goalSelector.tick();
            h.assertTrue(pig.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof PigmanBowGoal),
                    "The actual installed bow goal owns the selector while the target is hidden");
            h.assertTrue(pig.getNavigation().getPath() != null && !pig.getNavigation().isDone(),
                    "The ranged selector starts a real pursuit path instead of standing permanently behind cover");
        } finally {
            discardPigman(h, pig);
            player.discard();
            changed.forEach((position, state) -> h.getLevel().setBlockAndUpdate(position, state));
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void swordPigmanCanEquipABowAndReturnToRealMelee(GameTestHelper h) {
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 192, 1)));
        ZombifiedPiglin pig = pigman(h, origin, new ItemStack(Items.IRON_SWORD));
        Player player = target(h, origin.add(0D, 0D, .9D));
        try {
            pig.setTarget(player);
            float health = player.getHealth();
            advanceSelector(pig, player, 40);
            h.assertTrue(player.getHealth() < health, "A sword-born pigman runs its real melee attack");
            pig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            player.setPos(origin.add(0D, 0D, 8D));
            advanceSelector(pig, player, 12);
            h.assertTrue(pig.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof PigmanBowGoal),
                    "Equipping a bow preempts the previously running melee through the real selector");
            h.assertFalse(pig.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof SourceMeleeGoal),
                    "Melee cannot compete with the active bow goal");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(AbstractArrow.class, pig.getBoundingBox().inflate(16), arrow -> arrow.getOwner() == pig).isEmpty(),
                    "The newly selected ranged goal releases a real owned projectile");
            pig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            player.setPos(origin.add(0D, 0D, .9D));
            health = player.getHealth();
            advanceSelector(pig, player, 60);
            h.assertTrue(player.getHealth() < health, "Returning to the sword restores actual melee damage");
            h.assertFalse(pig.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof PigmanBowGoal),
                    "The ranged goal relinquishes its selector locks after the bow is removed");
        } finally {
            discardPigman(h, pig);
            player.discard();
        }
        h.succeed();
    }

    private static void block(GameTestHelper h, Map<BlockPos, BlockState> changed, BlockPos position, BlockState state) {
        changed.putIfAbsent(position, h.getLevel().getBlockState(position));
        h.getLevel().setBlockAndUpdate(position, state);
    }
}
