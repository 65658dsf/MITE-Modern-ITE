package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real melee and native effect expiry, including range and obstruction regressions. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class RabiesGameTests {
    private RabiesGameTests() {}

    @GameTest(template = "gear_empty")
    public static void frenzyUsesPlayerMeleeAndStopsWhenRemoved(GameTestHelper h) {
        var player = new FrenziedPlayer(h);
        var cow = h.spawn(EntityType.COW, new BlockPos(2, 1, 3));
        cow.setNoAi(true);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("rabies"), Rabies.DURATION));
        float health = cow.getHealth();
        player.tickCount = 10;
        Rabies.tick(player);
        h.assertTrue(player.attacks == 1 && player.lastTarget == cow, "Frenzy enters the real player attack path once");
        h.assertTrue(cow.getHealth() < health, "The automatic attack actually damages the nearby creature");
        player.removeEffect(ExtremeEffects.VALUES.get("rabies"));
        player.tickCount = 20;
        float yaw = player.getYRot();
        Rabies.tick(player);
        h.assertTrue(player.attacks == 1 && player.getYRot() == yaw, "Removal immediately restores attack and look control");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void frenzyCannotAttackThroughSolidWallsOrBeyondReach(GameTestHelper h) {
        var player = new FrenziedPlayer(h);
        var cow = h.spawn(EntityType.COW, new BlockPos(2, 1, 7));
        cow.setNoAi(true);
        h.assertTrue(Rabies.target(player) == null, "Targets beyond native entity reach are excluded");
        cow.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 3.8)));
        h.assertTrue(Rabies.target(player) == cow, "A visible target inside native reach is found");
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
        h.assertTrue(Rabies.target(player) == null, "A solid wall prevents the involuntary attack");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void frenzyExpiresAfterExactlySixSecondsAndRefreshDoesNotStack(GameTestHelper h) {
        var player = new FrenziedPlayer(h);
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("rabies"), Rabies.DURATION));
        for (int i = 0; i < 60; i++) player.advanceEffects();
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("rabies"), Rabies.DURATION));
        h.assertTrue(player.getEffect(ExtremeEffects.VALUES.get("rabies")).getDuration() == 120,
                "A new bite refreshes to six seconds instead of stacking duration");
        for (int i = 0; i < 119; i++) player.advanceEffects();
        h.assertTrue(Rabies.active(player), "Frenzy remains active through tick 119");
        player.advanceEffects();
        h.assertFalse(Rabies.active(player), "Native effect expiration ends frenzy at tick 120");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void creativeAndSpectatorPlayersKeepTheirControls(GameTestHelper h) {
        var player = new FrenziedPlayer(h);
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("rabies"), Rabies.DURATION));
        player.creative = true;
        h.assertFalse(Rabies.active(player), "Creative players are immune to involuntary controls");
        player.creative = false;
        player.spectator = true;
        h.assertFalse(Rabies.active(player), "Spectators are immune to involuntary controls");
        player.spectator = false;
        h.assertTrue(Rabies.active(player), "A living survival player is controlled while the effect lasts");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void frenzyUsesOneNearestTargetAndKeepsEmptySwings(GameTestHelper h) {
        var player = new FrenziedPlayer(h);
        var near = h.spawn(EntityType.COW, new BlockPos(2, 1, 3));
        var far = h.spawn(EntityType.COW, new BlockPos(4, 1, 2));
        near.setNoAi(true);
        far.setNoAi(true);
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("rabies"), Rabies.DURATION));
        player.tickCount = 10;
        Rabies.tick(player);
        h.assertTrue(player.attacks == 1 && player.lastTarget == near, "Each attack interval selects only the nearest target");
        near.discard();
        far.discard();
        player.swinging = false;
        player.tickCount = 15;
        float yaw = player.getYRot();
        Rabies.tick(player);
        h.assertTrue(player.swinging && player.getYRot() != yaw && player.attacks == 1,
                "With no targets, the affected player still turns and swings without creating damage");
        h.succeed();
    }

    private static final class FrenziedPlayer extends Player {
        private int attacks;
        private Entity lastTarget;
        private boolean creative, spectator;

        private FrenziedPlayer(GameTestHelper h) {
            super(h.getLevel(), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "NingMeng123"));
            setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 2.5)));
        }

        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public void attack(Entity target) { attacks++; lastTarget = target; super.attack(target); }
        private void advanceEffects() { tickEffects(); }
    }
}
