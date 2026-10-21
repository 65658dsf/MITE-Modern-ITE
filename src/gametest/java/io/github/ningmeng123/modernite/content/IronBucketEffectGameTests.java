package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.MobAbilities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real melee and native effect cleanup must never equip a bucket on the player. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IronBucketEffectGameTests {
    private static final int DURATION = 200;

    private IronBucketEffectGameTests() {}

    @GameTest(template = "gear_empty")
    public static void realBucketZombieHitPreservesTheOriginalEnchantedHelmet(GameTestHelper h) {
        var player = player(h);
        ItemStack helmet = helmet(h);
        ItemStack before = helmet.copy();
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        hit(h, zombie(h, ExtremeKind.ZOMBIE_IRON_BUCKET), player);
        assertBucket(h, player, DURATION);
        assertHelmet(h, player, helmet, before);
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void realBucketZombieHitLeavesAnEmptyHeadSlotEmpty(GameTestHelper h) {
        var player = player(h);
        h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "The target starts without a helmet");
        hit(h, zombie(h, ExtremeKind.ZOMBIE_IRON_BUCKET), player);
        assertBucket(h, player, DURATION);
        h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                "Bucket head is an effect even when there is no helmet to preserve");
        h.assertTrue(player.getInventory().isEmpty(), "The attack never inserts a bucket item into the inventory");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void rejectedBucketZombieMeleeDoesNotApplyTheEffect(GameTestHelper h) {
        var player = player(h);
        var zombie = zombie(h, ExtremeKind.ZOMBIE_IRON_BUCKET);
        player.setInvulnerable(true);
        float health = player.getHealth();
        h.assertFalse(zombie.doHurtTarget(player), "An invulnerable player rejects the real melee attack");
        h.assertTrue(player.getHealth() == health, "Rejected melee leaves health unchanged");
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("bucket_head")),
                "The failed melee attack never applies bucket head");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void ownedArrowsAndOtherZombiesCannotApplyBucketHead(GameTestHelper h) {
        var player = player(h);
        var bucketZombie = zombie(h, ExtremeKind.ZOMBIE_IRON_BUCKET);
        var arrow = EntityType.ARROW.create(h.getLevel());
        h.assertTrue(arrow != null, "The owned arrow creates successfully");
        arrow.setOwner(bucketZombie);
        float health = player.getHealth();
        h.assertTrue(player.hurt(h.getLevel().damageSources().arrow(arrow, bucketZombie), 2)
                        && player.getHealth() < health,
                "A bucket-zombie-owned projectile completes real damage through the event chain");
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("bucket_head")),
                "Projectile ownership cannot invoke the melee-only bucket ability");
        player.invulnerableTime = 0;
        hit(h, zombie(h, ExtremeKind.ZOMBIE_JUMP), player);
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("bucket_head")),
                "Another extreme zombie's real melee does not gain the bucket ability");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void bucketZombieDoesNotApplyThePlayerEffectToOtherCreatures(GameTestHelper h) {
        var pig = EntityType.PIG.create(h.getLevel());
        h.assertTrue(pig != null, "The non-player melee target creates successfully");
        var zombie = zombie(h, ExtremeKind.ZOMBIE_IRON_BUCKET);
        pig.moveTo(zombie.position().add(1, 0, 0));
        float health = pig.getHealth();
        h.assertTrue(zombie.doHurtTarget(pig) && pig.getHealth() < health,
                "The real bucket zombie successfully damages a non-player target");
        h.assertFalse(pig.hasEffect(ExtremeEffects.VALUES.get("bucket_head")),
                "The player head effect does not leak to other creatures");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void repeatedBucketZombieHitsRefreshOneEffectAndKeepTheHelmet(GameTestHelper h) {
        var player = player(h);
        ItemStack helmet = helmet(h);
        ItemStack before = helmet.copy();
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        var zombie = zombie(h, ExtremeKind.ZOMBIE_IRON_BUCKET);
        hit(h, zombie, player);
        for (int tick = 0; tick < 60; tick++) player.advanceEffects();
        assertBucket(h, player, DURATION - 60);
        player.invulnerableTime = 0;
        hit(h, zombie, player);
        assertBucket(h, player, DURATION);
        h.assertTrue(player.getActiveEffects().stream()
                        .filter(effect -> effect.getEffect().equals(ExtremeEffects.VALUES.get("bucket_head"))).count() == 1,
                "Repeated hits refresh a single bucket effect instead of stacking instances");
        assertHelmet(h, player, helmet, before);
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void naturalBucketExpiryAndDrinkingMilkPreserveTheHelmet(GameTestHelper h) {
        var player = player(h);
        ItemStack helmet = helmet(h);
        ItemStack before = helmet.copy();
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        var zombie = zombie(h, ExtremeKind.ZOMBIE_IRON_BUCKET);
        hit(h, zombie, player);
        for (int tick = 0; tick < DURATION - 1; tick++) player.advanceEffects();
        assertBucket(h, player, 1);
        player.advanceEffects();
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("bucket_head")),
                "The native final effect tick removes the bucket effect");
        assertHelmet(h, player, helmet, before);
        player.invulnerableTime = 0;
        hit(h, zombie, player);
        assertBucket(h, player, DURATION);
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), h.getLevel(), player);
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("bucket_head")),
                "Drinking a real milk bucket cures bucket head through the native effect lifecycle");
        assertHelmet(h, player, helmet, before);
        h.succeed();
    }

    private static EffectTickingPlayer player(GameTestHelper h) {
        var player = new EffectTickingPlayer(h);
        player.moveTo(h.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        return player;
    }

    private static Mob zombie(GameTestHelper h, ExtremeKind kind) {
        Mob zombie = ExtremeMobs.type(kind).create(h.getLevel());
        h.assertTrue(zombie != null, "The registered zombie creates successfully: " + kind);
        MobAbilities.initialize(zombie, (ExtremeMob) zombie);
        zombie.setNoAi(true);
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
        zombie.moveTo(h.absolutePos(new BlockPos(1, 3, 2)).getCenter());
        return zombie;
    }

    private static ItemStack helmet(GameTestHelper h) {
        var helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 preserved helmet"));
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        helmet.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.PROTECTION), 3);
        return helmet;
    }

    private static void hit(GameTestHelper h, Mob zombie, Player player) {
        float health = player.getHealth();
        h.assertTrue(zombie.doHurtTarget(player) && player.getHealth() < health,
                "The registered zombie completes an actual damaging melee hit");
    }

    private static void assertBucket(GameTestHelper h, Player player, int duration) {
        var effect = player.getEffect(ExtremeEffects.VALUES.get("bucket_head"));
        h.assertTrue(effect != null && effect.getDuration() == duration && effect.getAmplifier() == 0,
                "The independent bucket effect has the expected remaining duration and fixed level: " + duration);
    }

    private static void assertHelmet(GameTestHelper h, Player player, ItemStack helmet, ItemStack before) {
        h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD) == helmet
                        && ItemStack.matches(player.getItemBySlot(EquipmentSlot.HEAD), before),
                "The original helmet instance, custom name, enchantment and all components remain equipped");
    }

    /** Drives native effect expiry without unrelated player/world ticking. */
    private static final class EffectTickingPlayer extends Player {
        private EffectTickingPlayer(GameTestHelper h) {
            super(h.getLevel(), BlockPos.ZERO, 0F, new GameProfile(UUID.randomUUID(), "NingMeng123"));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        private void advanceEffects() {
            tickEffects();
        }
    }
}
