package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.MobAbilities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real player movement, effect lifecycle and the big-head zombie's range. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class BigHeadEffectGameTests {
    private static final ResourceLocation MOVEMENT_PENALTY = ResourceLocation.fromNamespaceAndPath("modern_ite", "big_head_movement_penalty");
    private static final double REMAINING_SPEED = .55D;

    private BigHeadEffectGameTests() {}

    @GameTest(template = "gear_empty")
    public static void everyBigHeadLevelSlowsARealPlayerByFortyFivePercent(GameTestHelper h) {
        var player = new EffectTickingPlayer(h);
        double original = speed(player);
        h.assertTrue(original > 0D, "The player begins with a positive movement attribute");
        for (int amplifier : new int[]{0, 1, 4}) {
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("big_head"), 100, amplifier));
            assertSpeed(h, player, original * REMAINING_SPEED, "Every big-head level keeps fifty-five percent of the real movement attribute");
            h.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(MOVEMENT_PENALTY),
                    "Big head owns its own namespaced movement modifier");
            player.removeEffect(ExtremeEffects.VALUES.get("big_head"));
            assertSpeed(h, player, original, "Removing big head immediately restores the movement attribute");
            h.assertFalse(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(MOVEMENT_PENALTY),
                    "Effect removal leaves no big-head movement modifier");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void refreshesAndAmplifierUpgradesNeverCompoundTheMovementPenalty(GameTestHelper h) {
        var player = new EffectTickingPlayer(h);
        double original = speed(player);
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("big_head"), 5, 0));
        player.advanceEffects();
        for (int refresh = 0; refresh < 6; refresh++) {
            int duration = 40 + refresh;
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("big_head"), duration, 0));
            h.assertTrue(player.getEffect(ExtremeEffects.VALUES.get("big_head")).getDuration() == duration,
                    "Each longer application refreshes the real active effect");
            assertSpeed(h, player, original * REMAINING_SPEED, "Refreshing big head never adds another movement penalty");
        }
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("big_head"), 80, 4));
        h.assertTrue(player.getEffect(ExtremeEffects.VALUES.get("big_head")).getAmplifier() == 4,
                "The real effect upgrades to the stronger amplifier");
        assertSpeed(h, player, original * REMAINING_SPEED, "An amplifier upgrade keeps the same fixed movement penalty");
        h.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream()
                        .filter(modifier -> modifier.id().equals(MOVEMENT_PENALTY)).count() == 1,
                "Only one big-head movement modifier exists after refreshes and upgrades");
        player.removeEffect(ExtremeEffects.VALUES.get("big_head"));
        assertSpeed(h, player, original, "Removing a refreshed and upgraded effect restores the original movement attribute");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void naturalExpiryRemovesThePenaltyOnTheLastEffectTick(GameTestHelper h) {
        var player = new EffectTickingPlayer(h);
        double original = speed(player);
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("big_head"), 4, 0));
        for (int tick = 0; tick < 3; tick++) player.advanceEffects();
        h.assertTrue(player.hasEffect(ExtremeEffects.VALUES.get("big_head")), "Big head remains active before its final tick");
        assertSpeed(h, player, original * REMAINING_SPEED, "The movement penalty lasts for the full active duration");
        player.advanceEffects();
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("big_head")), "The native effect lifecycle expires big head");
        h.assertFalse(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(MOVEMENT_PENALTY),
                "Natural expiry also removes the attribute modifier");
        assertSpeed(h, player, original, "Natural expiry restores the original movement attribute without an explicit cure");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void bigHeadPreservesVanillaSpeedAndSlownessThroughRemovalAndExpiry(GameTestHelper h) {
        var player = new EffectTickingPlayer(h);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
        double vanillaMovement = speed(player);
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("big_head"), 4, 0));
        assertSpeed(h, player, vanillaMovement * REMAINING_SPEED, "Big head multiplies movement after native Speed and Slowness");
        player.removeEffect(ExtremeEffects.VALUES.get("big_head"));
        assertVanillaEffects(h, player);
        assertSpeed(h, player, vanillaMovement, "Removing big head restores the surviving vanilla movement modifiers");
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("big_head"), 4, 0));
        for (int tick = 0; tick < 4; tick++) player.advanceEffects();
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("big_head")), "The second big-head application expires naturally");
        assertVanillaEffects(h, player);
        assertSpeed(h, player, vanillaMovement, "Big-head expiry preserves both native movement modifiers");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void actualBigHeadZombieAppliesAndRefreshesThePenaltyWithinFifteenBlocks(GameTestHelper h) {
        var player = new EffectTickingPlayer(h);
        var zombie = ExtremeMobs.type(ExtremeKind.FISHING_ROD_ZOMBIE).create(h.getLevel());
        h.assertTrue(zombie != null, "The registered big-head zombie creates successfully");
        var state = (ExtremeMob) zombie;
        MobAbilities.initialize(zombie, state);
        // Keep the real line-of-sight check clear of neighboring test structures.
        var origin = h.absolutePos(new BlockPos(1, 3, 1)).getCenter().add(0D, 160D, 0D);
        zombie.moveTo(origin);
        zombie.setTarget(player);
        double original = speed(player);
        player.moveTo(origin.add(15.01D, 0D, 0D));
        state.extremeMemory().clock = 19;
        MobAbilities.tick(zombie, state);
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("big_head")), "The real ability cannot reach beyond fifteen blocks");
        player.moveTo(origin.add(15D, 0D, 0D));
        state.extremeMemory().clock = 19;
        MobAbilities.tick(zombie, state);
        h.assertTrue(player.hasEffect(ExtremeEffects.VALUES.get("big_head")), "The real ability includes the fifteen-block boundary");
        h.assertTrue(player.getEffect(ExtremeEffects.VALUES.get("big_head")).getDuration() == 100,
                "The zombie applies the production five-second big-head effect");
        assertSpeed(h, player, original * REMAINING_SPEED, "The real zombie ability slows its player target");
        for (int tick = 0; tick < 20; tick++) player.advanceEffects();
        state.extremeMemory().clock = 39;
        MobAbilities.tick(zombie, state);
        h.assertTrue(player.getEffect(ExtremeEffects.VALUES.get("big_head")).getDuration() == 100,
                "The next real ability pulse refreshes the shortened duration");
        assertSpeed(h, player, original * REMAINING_SPEED, "Repeated zombie pulses preserve a single fixed movement penalty");
        h.succeed();
    }

    private static double speed(Player player) {
        return player.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    private static void assertSpeed(GameTestHelper h, Player player, double expected, String message) {
        h.assertTrue(Math.abs(speed(player) - expected) < 1.0E-9D, message);
    }

    private static void assertVanillaEffects(GameTestHelper h, Player player) {
        h.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED) && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1,
                "Big-head cleanup preserves native Speed II");
        h.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 0,
                "Big-head cleanup preserves native Slowness I");
    }

    /** Drives the native expiry path without unrelated hunger, movement or world AI ticks. */
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
