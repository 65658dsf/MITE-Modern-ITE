package io.github.ningmeng123.modernite.content.wither;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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

    @GameTest(template = "gear_empty")
    public static void realWitherSkullScalesBothImpactAndBlast(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = helper.absolutePos(new BlockPos(2, 32, 2)).getCenter();
        WitherBoss wither = EntityType.WITHER.create(level);
        try {
            for (boolean enhanced : new boolean[]{false, true}) {
                WitherEncounters.markSummon(wither, enhanced);
                assertSkullDamage(helper, origin, wither, false, 2.5F);
            }
            assertSkullDamage(helper, origin, helper.makeMockPlayer(GameType.SURVIVAL), false, 1F);
            assertSkullDamage(helper, origin, wither, true, 2.5F);
            assertSkullDamage(helper, origin, null, false, 1F);
        } finally {
            wither.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void realWitherChargeBlastScalesDamageForEitherSummon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = helper.absolutePos(new BlockPos(2, 32, 2)).getCenter();
        for (boolean enhanced : new boolean[]{false, true}) {
            WitherBoss wither = EntityType.WITHER.create(level);
            wither.moveTo(origin);
            wither.setNoGravity(true);
            WitherEncounters.markSummon(wither, enhanced);
            wither.makeInvulnerable();
            wither.setInvulnerableTicks(1);
            LivingEntity victim = damageVictim(helper, wither.getEyePosition().add(2, -.25, 0));
            float[] observed = new float[2];
            Consumer<LivingIncomingDamageEvent> capture = event -> {
                if (event.getEntity() == victim && event.getSource().getDirectEntity() == wither
                        && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                    observed[0] = event.getOriginalAmount();
                    observed[1] = event.getAmount();
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, capture);
            try {
                level.tickNonPassenger(wither);
                helper.assertTrue(wither.getInvulnerableTicks() == 0 && observed[0] > 0F,
                        "The real charging Wither explodes and damages the nearby target");
                helper.assertValueEqual(observed[1], observed[0] * 2.5F, "The charge blast gets the halved 5× damage multiplier once");
            } finally {
                NeoForge.EVENT_BUS.unregister(capture);
                victim.discard();
                wither.discard();
            }
        }
        helper.succeed();
    }

    private static void assertSkullDamage(GameTestHelper helper, Vec3 origin, LivingEntity owner,
            boolean removeOwner, float multiplier) {
        ServerLevel level = helper.getLevel();
        var skull = new WitherSkull(EntityType.WITHER_SKULL, level);
        skull.setOwner(owner);
        skull.setPos(origin);
        skull.setDeltaMovement(1, 0, 0);
        boolean ownerless = owner == null || removeOwner;
        LivingEntity impactVictim = damageVictim(helper, origin.add(.9, -.3, 0));
        LivingEntity blastVictim = damageVictim(helper, origin.add(-1.2, -.3, 0));
        float[] observed = new float[3];
        Consumer<LivingIncomingDamageEvent> capture = event -> {
            var source = event.getSource();
            boolean impact = ownerless ? source.is(DamageTypes.MAGIC) && source.getDirectEntity() == null
                    : source.is(DamageTypes.WITHER_SKULL) && source.getDirectEntity() == skull;
            if (event.getEntity() == impactVictim && impact) {
                observed[0] = event.getAmount();
            }
            if (event.getEntity() == blastVictim && source.getDirectEntity() == skull && source.is(DamageTypeTags.IS_EXPLOSION)) {
                observed[1] = event.getOriginalAmount();
                observed[2] = event.getAmount();
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, capture);
        try {
            helper.assertTrue(level.addFreshEntity(skull), "The actual skull joins while its owner is known");
            if (removeOwner) owner.discard();
            helper.assertTrue((skull.getOwner() == null) == ownerless, "The skull resolves the expected owner state at impact");
            level.tickNonPassenger(skull);
            helper.assertTrue(skull.isRemoved() && observed[1] > 0F, "The real skull collided and damaged a separate target with its blast");
            helper.assertValueEqual(observed[0], (ownerless ? 5F : 8F) * multiplier,
                    "The direct impact applies its original Wither attribution even after the owner is gone");
            helper.assertValueEqual(observed[2], observed[1] * multiplier, "The blast uses the multiplier for its actual owner");
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
            impactVictim.discard();
            blastVictim.discard();
            skull.discard();
        }
    }

    private static LivingEntity damageVictim(GameTestHelper helper, Vec3 position) {
        var victim = EntityType.COW.create(helper.getLevel());
        victim.moveTo(position);
        victim.setNoGravity(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        victim.setHealth(1000F);
        helper.assertTrue(helper.getLevel().addFreshEntity(victim), "The actual victim is added to the explosion's entity search");
        return victim;
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
