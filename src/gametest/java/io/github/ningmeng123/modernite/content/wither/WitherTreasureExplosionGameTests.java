package io.github.ningmeng123.modernite.content.wither;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native summon, skull-impact and TNT damage against dropped treasures. @author NingMeng123 */
@GameTestHolder("modern_ite_wither")
@PrefixGameTestTemplate(false)
public final class WitherTreasureExplosionGameTests {
    private WitherTreasureExplosionGameTests() {}

    @GameTest(template = "gear_empty")
    public static void enhancedWitherSpawnBlastSparesOnlyTheTreasureAndTntCanStillDestroyIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = helper.absolutePos(new BlockPos(2, 32, 2)).getCenter();
        WitherBoss wither = EntityType.WITHER.create(level);
        wither.moveTo(origin);
        wither.setNoGravity(true);
        WitherEncounters.markSummon(wither, true);
        wither.makeInvulnerable();
        wither.setInvulnerableTicks(1);
        Vec3 dropPos = wither.getEyePosition().add(0.25D, 0, 0);
        ItemEntity treasure = drop(level, dropPos, new ItemStack(WitherTreasureContent.TREASURE.get(), 5));
        ItemEntity stick = drop(level, dropPos, new ItemStack(Items.STICK));
        try {
            level.tickNonPassenger(wither);
            helper.assertTrue(wither.getInvulnerableTicks() == 0 && stick.isRemoved(),
                    "The Wither's spawn explosion destroys the ordinary item beside the treasure");
            helper.assertTrue(!treasure.isRemoved() && treasure.getItem().getCount() == 5,
                    "The entire dropped treasure stack survives the spawn explosion");
            var tnt = new PrimedTnt(level, origin.x, dropPos.y, origin.z, null);
            tnt.setDeltaMovement(Vec3.ZERO);
            tnt.setFuse(1);
            level.tickNonPassenger(tnt);
            helper.assertTrue(tnt.isRemoved() && treasure.isRemoved(), "Ordinary TNT still destroys the dropped treasure");
        } finally {
            treasure.discard();
            stick.discard();
            wither.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void skullImpactProtectsTreasureEvenAfterTheFiringWitherIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wall = helper.absolutePos(new BlockPos(2, 32, 2));
        var previous = level.getBlockState(wall);
        var wither = EntityType.WITHER.create(level);
        level.setBlockAndUpdate(wall, Blocks.BEDROCK.defaultBlockState());
        try {
            skullImpact(helper, wall, wither, true);
            wither.discard();
            skullImpact(helper, wall, wither, true);
            skullImpact(helper, wall, helper.makeMockPlayer(GameType.SURVIVAL), false);
        } finally {
            level.setBlockAndUpdate(wall, previous);
            wither.discard();
        }
        helper.succeed();
    }

    private static void skullImpact(GameTestHelper helper, BlockPos wall, LivingEntity owner, boolean protectedDrop) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = wall.west().getCenter();
        var skull = new WitherSkull(EntityType.WITHER_SKULL, level);
        skull.setOwner(owner);
        skull.setPos(origin);
        skull.setDeltaMovement(1, 0, 0);
        Vec3 dropPos = origin.add(-0.25D, 0, 0);
        ItemEntity treasure = drop(level, dropPos, new ItemStack(WitherTreasureContent.TREASURE.get(), 5));
        ItemEntity stick = drop(level, dropPos, new ItemStack(Items.STICK));
        try {
            level.tickNonPassenger(skull);
            helper.assertTrue(skull.isRemoved() && stick.isRemoved(), "The real skull hits the wall and explodes beside both drops");
            helper.assertValueEqual(treasure.isAlive(), protectedDrop,
                    "Treasure immunity follows the skull's Wither ownership, including a departed shooter");
        } finally {
            skull.discard();
            treasure.discard();
            stick.discard();
        }
    }

    private static ItemEntity drop(ServerLevel level, Vec3 pos, ItemStack stack) {
        var drop = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
        drop.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(drop);
        return drop;
    }
}
