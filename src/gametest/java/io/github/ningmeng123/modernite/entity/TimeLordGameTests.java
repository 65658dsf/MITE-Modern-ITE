package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.github.ningmeng123.modernite.content.TimeDilation;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual player simulation, effect expiry and multiplayer aura boundaries. @author NingMeng123 */
@GameTestHolder("modern_ite_time_lord")
@PrefixGameTestTemplate(false)
public final class TimeLordGameTests {
    private TimeLordGameTests() {}

    @GameTest(template = "gear_empty")
    public static void slowedPlayerRunsSixteenPhysicsStepsInEightyWorldTicks(GameTestHelper h) {
        var slowed = new TickingPlayer(h);
        var normal = new TickingPlayer(h);
        slowed.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
        for (int tick = 0; tick < 80; tick++) slowed.tick();
        for (int tick = 0; tick < 16; tick++) normal.tick();
        h.assertTrue(slowed.steps == 16 && normal.steps == 16, "Only the affected player runs at one fifth of normal speed");
        h.assertTrue(slowed.position().distanceToSqr(normal.position()) < 1E-8,
                "Real gravity and movement cover sixteen ordinary physics steps, including vertical movement");
        h.assertFalse(TimeDilation.active(slowed), "Eighty real ticks expire the four-second effect");
        slowed.tick();
        h.assertTrue(slowed.steps == 17, "The very next tick resumes ordinary physics");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void attackRecoveryAndItemUseFollowThePlayerClock(GameTestHelper h) {
        var player = new TickingPlayer(h);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(.1);
        player.resetAttackStrengthTicker();
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
        for (int tick = 0; tick < 80; tick++) player.tick();
        h.assertTrue(Math.abs(player.getAttackStrengthScale(0) - .08F) < 1E-5,
                "Attack strength recharges sixteen of two hundred logical ticks");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.getFoodData().setFoodLevel(10);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        int original = player.getUseItemRemainingTicks();
        h.assertTrue(original > 10, "Native bow use starts before checking the slowed item clock");
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 40));
        for (int tick = 0; tick < 40; tick++) player.tick();
        h.assertTrue(player.isUsingItem() && player.getUseItemRemainingTicks() == original - 8,
                "Bow use advances eight logical ticks in two real seconds: " + original + " -> " + player.getUseItemRemainingTicks());
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void refreshCureAndOtherEffectsKeepTheirNativeLifecycle(GameTestHelper h) {
        var player = new TickingPlayer(h);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        float digSpeed = player.getDigSpeed(Blocks.STONE.defaultBlockState(), player.blockPosition());
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
        h.assertTrue(Math.abs(player.getDigSpeed(Blocks.STONE.defaultBlockState(), player.blockPosition()) - digSpeed) < 1E-5,
                "Native per-step mining strength is unchanged; the action clock controls elapsed mining time");
        for (int tick = 0; tick < 40; tick++) player.tick();
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80, 4));
        int before = player.steps;
        for (int tick = 0; tick < 80; tick++) player.tick();
        h.assertTrue(player.steps - before == 16, "Amplifier upgrades and refreshes cannot stack time scaling");
        h.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 80,
                "Other potion durations continue in real time");
        h.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "Existing movement attributes remain intact");
        player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
        player.tick();
        player.removeAllEffects();
        before = player.steps;
        player.tick();
        h.assertTrue(player.steps == before + 1, "Native milk/clear removal immediately restores normal simulation");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void effectCannotSlowMobsCreativeOrSpectatorPlayers(GameTestHelper h) {
        float rate = h.getLevel().tickRateManager().tickrate();
        var cow = EntityType.COW.create(h.getLevel());
        h.assertFalse(cow.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80)),
                "Even direct effect application to a non-player is refused");
        try (var session = new Session(h)) {
            for (var mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                session.player.setGameMode(mode);
                h.assertFalse(session.player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80)),
                        "Creative and spectator players remain exempt");
            }
        }
        h.assertTrue(h.getLevel().tickRateManager().tickrate() == rate, "The global tick rate is untouched");
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_aura")
    public static void realPulseAffectsNearbyPlayersButRespectsWallsRangeAndCooldown(GameTestHelper h) {
        try (var target = new Session(h); var nearby = new Session(h); var distant = new Session(h)) {
            var lord = ExtremeMobs.type(ExtremeKind.TIME_LORD).create(h.getLevel());
            var state = (ExtremeMob) lord;
            MobAbilities.initialize(lord, state);
            state.extremeMemory().summons = TimeLordAbilities.MAX_SUMMONS;
            var origin = h.absolutePos(new BlockPos(2, 2, 2)).getCenter().add(0, 160, 0);
            lord.moveTo(origin);
            lord.setNoAi(true);
            lord.setTarget(target.player);
            target.player.moveTo(origin.add(4, 0, 0));
            nearby.player.moveTo(origin.add(0, 0, 4));
            distant.player.moveTo(origin.add(16.01, 0, 0));
            h.assertTrue(lord.getOffhandItem().is(Items.CLOCK) && ExtremeMobs.isBoss(lord), "The registered boss carries its clock");
            state.extremeMemory().cooldown = 0;
            TimeLordAbilities.tick(lord, state.extremeMemory(), target.player);
            h.assertTrue(TimeDilation.active(target.player) && TimeDilation.active(nearby.player),
                    "The real aura affects both nearby survival players");
            h.assertFalse(TimeDilation.active(distant.player), "The spherical sixteen-block range excludes the distant player");
            h.assertTrue(target.player.getEffect(ExtremeEffects.VALUES.get("time_dilation")).getDuration() == 80,
                    "The production pulse applies four real seconds");
            target.player.removeAllEffects(); nearby.player.removeAllEffects();
            TimeLordAbilities.tick(lord, state.extremeMemory(), target.player);
            h.assertFalse(TimeDilation.active(target.player), "The active cooldown prevents immediate reapplication");
            BlockPos wall = BlockPos.containing(origin.add(0, 0, 2));
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 3; y++) h.getLevel().setBlockAndUpdate(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
            try {
                lord.getSensing().tick();
                state.extremeMemory().cooldown = 0;
                TimeLordAbilities.tick(lord, state.extremeMemory(), target.player);
                h.assertTrue(TimeDilation.active(target.player), "The visible combat target receives the next pulse");
                h.assertFalse(TimeDilation.active(nearby.player), "A nearby player behind a solid wall remains unaffected");
            } finally {
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 3; y++) h.getLevel().setBlockAndUpdate(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState());
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void realServerPlayerDoTickKeepsConnectionUpdatesAndExpiresNormally(GameTestHelper h) {
        try (var session = new Session(h)) {
            var player = session.player;
            player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(.1);
            player.resetAttackStrengthTicker();
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
            for (int tick = 0; tick < 80; tick++) player.doTick();
            h.assertFalse(TimeDilation.active(player), "ServerPlayer.doTick expires the effect after eighty connection ticks");
            h.assertTrue(Math.abs(player.getAttackStrengthScale(0) - .08F) < 1E-5,
                    "The actual server player recharges at one fifth of the ordinary rate");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "time_lord_spawn")
    public static void spawnDateWeightHabitatAndExplicitSummonsUseExistingRules(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h); var settings = new IteReferenceSpawnGameTests.Settings()) {
            settings.set("extreme_mob_spawn_limit_day", 3);
            settings.set("time_lord_spawn_limit_day", 96);
            site.date(95, 95, true);
            h.assertFalse(MobAbilities.maySpawn(ExtremeKind.TIME_LORD, site.level, BlockPos.containing(site.origin)), "The preceding day keeps the new lord locked");
            site.date(96, 96, true);
            h.assertTrue(MobAbilities.maySpawn(ExtremeKind.TIME_LORD, site.level, BlockPos.containing(site.origin)), "The configured date unlocks the lord in the Overworld");
            h.assertTrue(ExtremeMobs.spawnWeight(ExtremeKind.TIME_LORD) == 1, "Default natural weight is rare");
            settings.set("time_lord_spawn_frequency", 0);
            h.assertTrue(ExtremeMobs.spawnWeight(ExtremeKind.TIME_LORD) == 0, "The live weight setting disables the natural candidate");
            var nether = site.level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertFalse(MobAbilities.customNaturalAllowed(ExtremeKind.TIME_LORD, nether), "The lord does not enter the Nether habitat");
            site.date(1, 1, true);
            var lord = ExtremeMobs.type(ExtremeKind.TIME_LORD).create(site.level);
            h.assertTrue(MobAbilities.spawnRules(lord, ExtremeKind.TIME_LORD, site.level, net.minecraft.world.entity.MobSpawnType.COMMAND),
                    "Explicit summon commands work before the natural unlock day");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void nativeServerActionsWaitForTheSameFifthStepAndPreserveOrder(GameTestHelper h) {
        try (var session = new Session(h)) {
            var player = session.player;
            player.getInventory().setItem(2, new ItemStack(Items.STICK));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.CLOCK));
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
            player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(2));
            player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            h.assertTrue(player.getInventory().selected == 0 && player.getOffhandItem().is(Items.CLOCK),
                    "Actual selection and offhand packets remain pending before a player step");
            for (int tick = 0; tick < 4; tick++) player.connection.tick();
            h.assertTrue(player.getInventory().selected == 0 && player.getOffhandItem().is(Items.CLOCK),
                    "The first four real connection ticks do not execute the input");
            player.connection.tick();
            h.assertTrue(player.getInventory().selected == 2 && player.getMainHandItem().is(Items.CLOCK)
                            && player.getOffhandItem().is(Items.STICK),
                    "The fifth connection tick selects the intended slot before swapping, without dropping either packet");
            player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(1));
            player.removeEffect(ExtremeEffects.VALUES.get("time_dilation"));
            player.connection.tick();
            h.assertTrue(player.getInventory().selected == 1, "A cure immediately drains pending input on the next ordinary connection tick");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void nativeMiningClockRunsAtOneFifthWithoutStackingASlownessPenalty(GameTestHelper h) throws Exception {
        try (var session = new Session(h)) {
            var player = session.player;
            var ticks = ServerPlayerGameMode.class.getDeclaredField("gameTicks");
            ticks.setAccessible(true);
            int before = ticks.getInt(player.gameMode);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            float strength = player.getDigSpeed(Blocks.STONE.defaultBlockState(), player.blockPosition());
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
            h.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || player.hasEffect(MobEffects.DIG_SLOWDOWN),
                    "Time control never adds native Slowness or Mining Fatigue");
            h.assertTrue(player.getDigSpeed(Blocks.STONE.defaultBlockState(), player.blockPosition()) == strength,
                    "The mining action step retains full native strength, preventing accidental one-twenty-fifth speed");
            for (int tick = 0; tick < 80; tick++) { player.gameMode.tick(); player.doTick(); }
            h.assertTrue(ticks.getInt(player.gameMode) - before == 16,
                    "The actual server mining-progress clock advances sixteen times in eighty world ticks");
            player.gameMode.tick();
            h.assertTrue(ticks.getInt(player.gameMode) - before == 17, "Mining immediately resumes its ordinary clock after expiry");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void pendingInputCannotLeakIntoAReplacementPlayerAfterRespawn(GameTestHelper h) {
        try (var session = new Session(h)) {
            var original = session.player;
            var connection = original.connection;
            original.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
            connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(3));
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "NingMeng123"), false);
            var replacement = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            replacement.connection = connection;
            replacement.setGameMode(GameType.SURVIVAL);
            replacement.setNoGravity(true);
            replacement.moveTo(original.position());
            connection.player = replacement;
            try {
                connection.tick();
                h.assertTrue(replacement.getInventory().selected == 0,
                        "The old body's delayed input must not act on a new player after the connection changes owner");
            } finally { connection.player = original; }
        }
        h.succeed();
    }

    private static final class TickingPlayer extends Player {
        int steps;
        TickingPlayer(GameTestHelper h) {
            super(h.getLevel(), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "NingMeng123"));
            moveTo(h.absolutePos(new BlockPos(2, 2, 2)).getCenter().add(0, 160, 0));
        }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public void aiStep() { steps++; super.aiStep(); }
    }

    private static final class Session implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "NingMeng123"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(h.absolutePos(new BlockPos(2, 2, 2)).getCenter().add(0, 160, 0));
        }
        @Override public void close() {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
