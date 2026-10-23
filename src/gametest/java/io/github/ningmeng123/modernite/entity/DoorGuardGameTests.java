package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Door guards exercise actual registered entities and the complete native damage chain. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class DoorGuardGameTests {
    private static int nextArena;
    private DoorGuardGameTests() {}

    private record Fight(ExtremeMobs.ZombieVariant zombie, Player player, Vec3 origin) implements AutoCloseable {
        void holdZombie() {
            zombie.setPos(origin);
            zombie.setDeltaMovement(Vec3.ZERO);
            zombie.setYRot(0F);
            zombie.setYHeadRot(0F);
            zombie.yBodyRot = 0F;
        }

        void playerAt(double angle, double distance) {
            double radians = Math.toRadians(angle);
            player.setPos(origin.add(-Math.sin(radians) * distance, 0D, Math.cos(radians) * distance));
            player.setDeltaMovement(Vec3.ZERO);
            player.setYRot((float)(angle + 180D));
            player.setYHeadRot((float)(angle + 180D));
            player.setXRot(0F);
        }

        void advance(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                // Direct native ticks advance equipment, swing recovery and the renderer's pose floats.
                // EntityTickEvent belongs to the level ticker, so invoke its ability entry point below.
                zombie.tickCount++;
                zombie.tick();
                holdZombie();
                zombie.getSensing().tick();
                // This is the same production entry point called by EntityTickEvent.Post.
                MobAbilities.tick(zombie, zombie);
            }
        }

        boolean hurt(DamageSource source) {
            zombie.invulnerableTime = 0;
            zombie.setPos(origin);
            zombie.setDeltaMovement(Vec3.ZERO);
            return zombie.hurt(source, 6F);
        }

        DamageSource melee() { return zombie.damageSources().playerAttack(player); }

        @Override public void close() {
            BetterMobCombat.cancel(zombie);
            zombie.discard();
            player.discard();
        }
    }

    private static Fight fight(GameTestHelper h) {
        var created = ExtremeMobs.type(ExtremeKind.ZOMBIE_DOOR).create(h.getLevel());
        h.assertTrue(created instanceof ExtremeMobs.ZombieVariant, "The registered door thief creates its production zombie class");
        var zombie = (ExtremeMobs.ZombieVariant)created;
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var anchor = h.absolutePos(new BlockPos(2, 10, 2));
        int arena = nextArena++;
        var origin = new Vec3(anchor.getX() + .5D, 144D, anchor.getZ() + 16000.5D + arena * 80D);
        h.getLevel().getChunk(BlockPos.containing(origin));
        var fight = new Fight(zombie, player, origin);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.tickCount = 100;
        zombie.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
        fight.holdZombie();
        h.assertTrue(h.getLevel().addFreshEntity(zombie), "A real level join installs production equipment and source melee goals");
        for (var slot : EquipmentSlot.values()) if (slot != EquipmentSlot.BODY) zombie.setItemSlot(slot, ItemStack.EMPTY);
        // Equip the door only after the registration tick, so the fixture's baseline is not already slowed.
        zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        zombie.stopUsingItem();
        zombie.removeAllEffects();
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2D);
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0D);
        zombie.setHealth(1000F);
        // The unrelated old summoning ability and native random offhand shields must not affect guard tests.
        zombie.extremeMemory().exchangerTried = true;
        zombie.extremeMemory().summons = 10;
        zombie.extremeMemory().cooldown = 200;
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setNoGravity(true);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        player.setHealth(1000F);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        fight.playerAt(0D, .9D);
        h.assertTrue(h.getLevel().addFreshEntity(player), "The armed, facing survival player enters the real entity index");
        zombie.setTarget(player);
        h.assertTrue(zombie.getOffhandItem().isEmpty() && !zombie.isUsingItem() && !MobShields.defending(zombie),
                "The fixture has no native shield use that could hide a broken door guard");
        return fight;
    }

    private static void withFights(GameTestHelper h, int count, Consumer<List<Fight>> test) {
        List<Fight> fights = new ArrayList<>();
        for (int i = 0; i < count; i++) fights.add(fight(h));
        h.runAfterDelay(1, () -> {
            try {
                for (Fight fight : fights) fight.zombie().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_DOOR));
                test.accept(fights);
                h.succeed();
            }
            finally { fights.forEach(Fight::close); }
        });
    }

    private static void raise(GameTestHelper h, Fight fight) {
        fight.advance(1);
        h.assertTrue(fight.zombie().isDoorRaised() && !fight.zombie().isDoorBlocking(),
                "A nearby visible target starts the door raise before damage can be blocked");
        fight.advance(DoorGuard.RAISE_TICKS);
        h.assertTrue(fight.zombie().isDoorBlocking(), "Five complete raise ticks enable the actual door block");
    }

    private static void assertDamage(GameTestHelper h, Fight fight, DamageSource source, boolean blocked, String reason) {
        float before = fight.zombie().getHealth();
        boolean accepted = fight.hurt(source);
        if (blocked) {
            h.assertFalse(accepted, "The real hurt call rejects a blocked hit: " + reason);
            h.assertValueEqual(fight.zombie().getHealth(), before, "A blocked hit preserves health: " + reason);
        } else {
            h.assertTrue(accepted && fight.zombie().getHealth() < before, "The real hurt chain applies damage: " + reason);
        }
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void doorRaiseBlockAndRestHaveFiniteWindowsAndRestoreMeleeAndSpeed(GameTestHelper h) {
        withFights(h, 1, fights -> {
        try (var fight = fights.getFirst()) {
            var zombie = fight.zombie();
            double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
            fight.advance(1);
            h.assertTrue(zombie.isDoorRaised() && !zombie.isDoorBlocking(), "The first tick shows the raise pose without immediate protection");
            h.assertTrue(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "Raising the heavy door slows the actual movement attribute");
            assertDamage(h, fight, fight.melee(), false, "a sword connects during the raise delay");
            float targetHealth = fight.player().getHealth();
            h.assertFalse(zombie.doHurtTarget(fight.player()), "The native zombie attack cannot hit through its own raising door");
            h.assertValueEqual(fight.player().getHealth(), targetHealth, "The blocked own attack does not damage its target");
            fight.advance(DoorGuard.RAISE_TICKS - 1);
            h.assertFalse(zombie.isDoorBlocking(), "Protection stays unavailable for all five raise ticks");
            fight.advance(1);
            h.assertTrue(zombie.isDoorBlocking() && zombie.getDoorGuardProgress(1F) == 1F, "The guard and rendered pose reach full height together");
            assertDamage(h, fight, fight.melee(), true, "the raised door stops the same sword hit");
            h.assertFalse(zombie.doHurtTarget(fight.player()), "The zombie also cannot melee through a fully raised door");
            fight.advance(DoorGuard.BLOCK_TICKS - 1);
            h.assertTrue(zombie.isDoorBlocking(), "The door protects through the last active tick");
            fight.advance(1);
            h.assertFalse(zombie.isDoorRaised() || zombie.isDoorBlocking(), "Thirty active ticks end the guard and begin its opening");
            h.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < .000001D, "Lowering the door removes its temporary movement penalty");
            fight.player().invulnerableTime = 0;
            h.assertTrue(zombie.doHurtTarget(fight.player()) && fight.player().getHealth() < targetHealth,
                    "The same production melee method immediately recovers during the opening");
            assertDamage(h, fight, fight.melee(), false, "the opening admits another real sword hit");
            fight.advance(DoorGuard.REST_TICKS - 1);
            h.assertFalse(zombie.isDoorRaised(), "The zombie cannot raise its door early during the thirty-tick opening");
            fight.advance(1);
            h.assertTrue(zombie.isDoorRaised() && !zombie.isDoorBlocking(), "A new cycle begins with another visible raise delay");
        }
        });
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void sourceMeleeRetriesCannotSwingTheRaisedDoorButOpeningsRestoreSwing(GameTestHelper h) {
        withFights(h, 2, fights -> {
        for (boolean broken : new boolean[]{false, true}) {
            try (var fight = fights.get(broken ? 1 : 0)) {
                var zombie = fight.zombie();
                var goal = zombie.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                        .filter(SourceMeleeGoal.class::isInstance).map(SourceMeleeGoal.class::cast)
                        .filter(SourceMeleeGoal::canUse).findFirst().orElse(null);
                h.assertTrue(goal != null, "The joined door thief has a real source melee goal for its nearby player");
                float targetHealth = fight.player().getHealth();
                fight.advance(1);
                h.assertTrue(zombie.isDoorRaised() && !zombie.isDoorBlocking(), "The source attack fixture begins during the door raise");
                zombie.swing(InteractionHand.MAIN_HAND);
                h.assertFalse(zombie.swinging, "An explicit native swing cannot animate a raising door");
                goal.tick();
                h.assertFalse(zombie.swinging, "The actual source goal's pre-attack swing is suppressed while raising");
                h.assertValueEqual(fight.player().getHealth(), targetHealth, "The raising door also prevents the source goal's native damage");
                fight.advance(DoorGuard.RAISE_TICKS);
                h.assertTrue(zombie.isDoorBlocking(), "The same source attack fixture reaches full protection");
                zombie.swing(InteractionHand.MAIN_HAND);
                h.assertFalse(zombie.swinging, "An explicit native swing cannot animate a fully raised door");
                // Exercise enough real goal retries to expire its first native attack cooldown.
                for (int retry = 0; retry < 20; retry++) goal.tick();
                h.assertFalse(zombie.swinging, "Repeated source melee retries cannot make the guarded door twitch");
                h.assertValueEqual(fight.player().getHealth(), targetHealth, "Repeated source attacks cannot damage through their own raised door");
                if (broken) {
                    fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                    assertDamage(h, fight, fight.melee(), true, "a real axe block creates the swing recovery opening");
                } else fight.advance(DoorGuard.BLOCK_TICKS);
                h.assertFalse(zombie.isDoorRaised(), "Both a normal opening and an axe break lower the door: broken=" + broken);
                zombie.swing(InteractionHand.MAIN_HAND);
                h.assertTrue(zombie.swinging, "The native swing resumes after the door is lowered: broken=" + broken);
            }
        }
        });
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void actualMeleeDamageUsesTheDoorsBodyFacingOneHundredTwentyDegreeArc(GameTestHelper h) {
        withFights(h, 1, fights -> {
        try (var fight = fights.getFirst()) {
            raise(h, fight);
            for (double angle : new double[]{0D, 59D, -59D, 61D, -61D, 180D}) {
                fight.playerAt(angle, 2D);
                assertDamage(h, fight, fight.melee(), Math.abs(angle) < 60D, "attacker angle " + angle);
            }
            fight.playerAt(180D, 2D);
            fight.zombie().setYHeadRot(180F);
            assertDamage(h, fight, fight.melee(), false, "turning only the head does not move the door behind the body");
        }
        });
    }

    private static Arrow arrow(GameTestHelper h, Fight fight, boolean front, boolean piercing) {
        var arrow = EntityType.ARROW.create(h.getLevel());
        h.assertTrue(arrow != null, "The native arrow entity creates");
        arrow.setOwner(fight.player());
        arrow.setPos(fight.origin().add(0D, 1D, front ? 2D : -2D));
        arrow.setDeltaMovement(0D, 0D, front ? -1D : 1D);
        if (piercing) {
            try {
                Method setter = AbstractArrow.class.getDeclaredMethod("setPierceLevel", byte.class);
                setter.setAccessible(true);
                setter.invoke(arrow, (byte)1);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Native piercing arrow fixture setup failed", error);
            }
            h.assertTrue(arrow.getPierceLevel() > 0, "The real arrow carries its native piercing state");
        }
        return arrow;
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void ordinaryArrowsFollowImpactDirectionWhilePiercingArrowsPassTheDoor(GameTestHelper h) {
        withFights(h, 1, fights -> {
        try (var fight = fights.getFirst()) {
            raise(h, fight);
            for (boolean front : new boolean[]{true, false}) for (boolean piercing : new boolean[]{false, true}) {
                // A curved or deflected arrow can reach the opposite side from its original shooter.
                fight.playerAt(front ? 180D : 0D, 3D);
                var arrow = arrow(h, fight, front, piercing);
                try {
                    assertDamage(h, fight, h.getLevel().damageSources().arrow(arrow, fight.player()), front && !piercing,
                            "native arrow front=" + front + ", piercing=" + piercing + ", shooter on the opposite side");
                } finally { arrow.discard(); }
            }
            var coincident = arrow(h, fight, true, false);
            try {
                coincident.setPos(fight.zombie().position());
                assertDamage(h, fight, h.getLevel().damageSources().arrow(coincident, fight.player()), true,
                        "an arrow already at impact still arrives from its reverse velocity");
            } finally { coincident.discard(); }
        }
        });
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void environmentalMagicExplosionAndShieldBypassingDamageRemainEffective(GameTestHelper h) {
        withFights(h, 1, fights -> {
        try (var fight = fights.getFirst()) {
            raise(h, fight);
            assertDamage(h, fight, h.getLevel().damageSources().generic(), false, "unattributed environmental damage");
            assertDamage(h, fight, h.getLevel().damageSources().drown(), false, "drowning");
            assertDamage(h, fight, h.getLevel().damageSources().magic(), false, "magic");
            var registry = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            var bypass = new DamageSource(registry.getHolderOrThrow(DamageTypes.SONIC_BOOM), fight.player());
            h.assertTrue(bypass.is(DamageTypeTags.BYPASSES_SHIELD), "The sourced frontal bypass fixture is tagged by the real damage registry");
            assertDamage(h, fight, bypass, false, "a frontal damage source tagged BYPASSES_SHIELD");
            var explosion = new DamageSource(registry.getHolderOrThrow(DamageTypes.PLAYER_EXPLOSION), fight.player());
            h.assertTrue(explosion.is(DamageTypeTags.IS_EXPLOSION), "The frontal explosion fixture uses a genuine explosion damage type");
            assertDamage(h, fight, explosion, false, "a player-attributed frontal explosion");
            h.assertTrue(fight.zombie().isDoorBlocking(), "Damage outside the door's coverage does not silently end the active guard");
        }
        });
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void axesBlockTheCurrentHitThenExposeTheZombieForSixtyTicks(GameTestHelper h) {
        withFights(h, 1, fights -> {
        try (var fight = fights.getFirst()) {
            raise(h, fight);
            fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            float guardHealth = fight.zombie().getHealth();
            fight.zombie().invulnerableTime = 0;
            fight.zombie().hurt(fight.melee(), 0F);
            h.assertTrue(fight.zombie().isDoorBlocking(), "A zero-damage axe contact cannot break or lower the active door");
            h.assertValueEqual(fight.zombie().getHealth(), guardHealth, "A zero-damage axe contact preserves the guard's health");
            assertDamage(h, fight, fight.melee(), true, "the axe's first impact is absorbed by the raised door");
            h.assertFalse(fight.zombie().isDoorRaised() || fight.zombie().isDoorBlocking(), "The accepted axe block immediately breaks the door stance");
            fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            assertDamage(h, fight, fight.melee(), false, "a follow-up sword exploits the axe break");
            float before = fight.player().getHealth();
            fight.player().invulnerableTime = 0;
            h.assertTrue(fight.zombie().doHurtTarget(fight.player()) && fight.player().getHealth() < before,
                    "A guard break also releases the zombie's own melee attack");
            fight.advance(DoorGuard.BREAK_TICKS - 1);
            h.assertFalse(fight.zombie().isDoorRaised(), "The sixty-tick axe penalty cannot be replaced by the normal shorter rest");
            fight.advance(1);
            h.assertTrue(fight.zombie().isDoorRaised() && !fight.zombie().isDoorBlocking(), "After the full axe penalty, recovery still requires a new raise delay");
            fight.advance(DoorGuard.RAISE_TICKS);
            assertDamage(h, fight, fight.melee(), true, "the door can protect again after its complete break recovery");
        }
        });
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void hardInvalidationLowersTheDoorWhileLostIntentFinishesItsFiniteWindow(GameTestHelper h) {
        withFights(h, 4, fights -> {
        for (int scenario = 0; scenario < 4; scenario++) {
            try (var fight = fights.get(scenario)) {
                double speed = fight.zombie().getAttributeValue(Attributes.MOVEMENT_SPEED);
                raise(h, fight);
                switch (scenario) {
                    case 0 -> fight.zombie().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    case 1 -> fight.zombie().setHealth(0F);
                    case 2 -> {
                        fight.zombie().setTarget(null);
                        fight.player().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                        fight.playerAt(180D, 8.01D);
                    }
                    default -> fight.playerAt(0D, 8.01D);
                }
                fight.advance(1);
                if (scenario < 2) {
                    h.assertFalse(fight.zombie().isDoorRaised() || fight.zombie().isDoorBlocking(),
                            "Losing the door or the defender's life immediately clears protection: scenario " + scenario);
                } else {
                    h.assertTrue(fight.zombie().isDoorBlocking(), "Lost player intent does not cancel a committed protection window");
                    fight.advance(DoorGuard.RAISE_TICKS + DoorGuard.BLOCK_TICKS + DoorGuard.REST_TICKS);
                    h.assertFalse(fight.zombie().isDoorRaised(), "A missing pursuit target or distant player cannot start another guard");
                }
                h.assertTrue(Math.abs(fight.zombie().getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < .000001D,
                        "Invalidating a guard removes its temporary movement modifier: scenario " + scenario);
                fight.zombie().setHealth(1000F);
                fight.player().setHealth(1000F);
                fight.playerAt(0D, .9D);
                assertDamage(h, fight, fight.melee(), false, "the invalidated guard cannot block stale damage: scenario " + scenario);
            }
        }
        });
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void lostSightFinishesTheCommittedDoorWindowWithoutRestartingBehindCover(GameTestHelper h) {
        withFights(h, 1, fights -> {
        try (var fight = fights.getFirst()) {
            fight.playerAt(0D, 1.5D);
            double speed = fight.zombie().getAttributeValue(Attributes.MOVEMENT_SPEED);
            raise(h, fight);
            fight.playerAt(0D, 4D);
            BlockPos wall = BlockPos.containing(fight.origin().add(0D, 1D, 2D));
            var oldState = h.getLevel().getBlockState(wall);
            try {
                h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                fight.zombie().getSensing().tick();
                h.assertFalse(fight.zombie().getSensing().hasLineOfSight(fight.player()), "The real world collision ray is blocked by the fixture wall");
                fight.advance(1);
                h.assertTrue(fight.zombie().isDoorBlocking(), "Losing sight retains the already committed finite guard window");
                fight.advance(DoorGuard.RAISE_TICKS + DoorGuard.BLOCK_TICKS + DoorGuard.REST_TICKS);
                h.assertFalse(fight.zombie().isDoorRaised() || fight.zombie().isDoorBlocking(), "Cover cannot trigger another door guard after its finite window and rest");
                h.assertTrue(Math.abs(fight.zombie().getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < .000001D,
                        "Losing sight releases the real movement attribute");
            } finally { h.getLevel().setBlockAndUpdate(wall, oldState); }
        }
        });
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void loadingActiveOrBrokenGuardsNeverRetainsAStalePosePenaltyOrCooldown(GameTestHelper h) {
        withFights(h, 2, fights -> {
        for (boolean broken : new boolean[]{false, true}) {
            try (var fight = fights.get(broken ? 1 : 0)) {
                var zombie = fight.zombie();
                double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
                raise(h, fight);
                if (broken) {
                    fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                    assertDamage(h, fight, fight.melee(), true, "save a real axe-broken stance");
                }
                var saved = new CompoundTag();
                zombie.saveWithoutId(saved);
                zombie.load(saved);
                h.assertFalse(zombie.isDoorRaised() || zombie.isDoorBlocking(), "Reading NBT into the same live entity clears transient guard state: broken=" + broken);
                h.assertTrue(zombie.getDoorGuardProgress(1F) == 0F, "Loading cannot retain a client-visible raised pose: broken=" + broken);
                h.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < .000001D,
                        "Loading cannot retain the temporary movement penalty: broken=" + broken);
                zombie.setTarget(fight.player());
                fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                fight.playerAt(0D, .9D);
                raise(h, fight);
                assertDamage(h, fight, fight.melee(), true, "a loaded entity begins a fresh complete raise cycle: broken=" + broken);
            }
        }
        });
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void preAiGuardWaitsForAnExistingSwingThenWinsTheNextAttackAndTicksOnlyOnce(GameTestHelper h) {
        withFights(h, 2, fights -> {
            try (Fight fight = fights.getFirst()) {
                var zombie = fight.zombie();
                zombie.swing(InteractionHand.MAIN_HAND);
                h.assertTrue(zombie.swinging, "The fixture begins with a real unguarded native weapon swing");
                zombie.tickCount++;
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
                h.assertTrue(zombie.swinging && !zombie.isDoorRaised(), "The defensive pre-AI decision never cancels an already active attack");
                for (int tick = 0; tick < 12 && zombie.swinging; tick++) {
                    zombie.tickCount++;
                    zombie.tick();
                    fight.holdZombie();
                    if (zombie.swinging) {
                        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
                        h.assertFalse(zombie.isDoorRaised(), "The guard waits throughout the native swing recovery");
                    }
                }
                h.assertFalse(zombie.swinging, "The native swing finishes its own recovery");
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
                h.assertTrue(zombie.isDoorRaised() && !zombie.isDoorBlocking(), "The first free pre-AI frame begins raising before another melee attempt can claim it");
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(zombie));
                h.assertFalse(zombie.isDoorBlocking(), "The old post-AI entry point cannot advance that same frame twice");
                h.assertFalse(zombie.doHurtTarget(fight.player()), "The already chosen guard prevents the next native attack");
                for (int tick = 1; tick <= DoorGuard.RAISE_TICKS; tick++) {
                    zombie.tickCount++;
                    zombie.tick();
                    fight.holdZombie();
                    NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
                    NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(zombie));
                    h.assertTrue(zombie.isDoorBlocking() == (tick == DoorGuard.RAISE_TICKS),
                            "Pre and Post together preserve the exact five-tick raise delay: tick " + tick);
                }
            }
            try (Fight fight = fights.get(1)) {
                var zombie = fight.zombie();
                var previousConfig=MiteConfig.effectiveSnapshot();
                boolean localConfig=previousConfig==MiteConfig.localSnapshot();
                boolean previousMobCombat=MiteConfig.BETTER_COMBAT_MOBS.get();
                try {
                    MiteConfig.BETTER_COMBAT_MOBS.set(true);MiteConfig.refreshEnchantmentRules();MiteConfig.clearServerSnapshot();
                    // A genuine controller recovery state is meaningful even without the optional client mod.
                    var statesField = BetterMobCombat.class.getDeclaredField("STATES");
                    statesField.setAccessible(true);
                    @SuppressWarnings("unchecked") Map<Object, Object> states = (Map<Object, Object>)statesField.get(null);
                    var stateType = Class.forName(BetterMobCombat.class.getName() + "$State");
                    var constructor = stateType.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object state = constructor.newInstance();
                    var readyAt = stateType.getDeclaredField("readyAt");
                    readyAt.setAccessible(true);
                    readyAt.setInt(state, zombie.tickCount + 3);
                    Object previous = states.put(zombie, state);
                    try {
                        for (int frame = 0; frame < 3; frame++) {
                            h.assertTrue(BetterMobCombat.attacking(zombie), "The fixture is in the real combat controller's recovery window");
                            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
                            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(zombie));
                            h.assertFalse(zombie.isDoorRaised(), "Defense never cancels an existing combat recovery frame");
                            zombie.tickCount++;
                        }
                        h.assertFalse(BetterMobCombat.attacking(zombie), "The original combat recovery expires on its scheduled entity tick");
                        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
                        h.assertTrue(zombie.isDoorRaised() && !zombie.isDoorBlocking(), "The first available Pre frame claims defense before another attack can starve it");
                        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(zombie));
                        h.assertFalse(zombie.isDoorBlocking() || zombie.doHurtTarget(fight.player()), "The same frame remains an unprotected raise and blocks the defender's own next attack");
                        for (int tick = 1; tick <= DoorGuard.RAISE_TICKS; tick++) {
                            zombie.tickCount++;
                            zombie.tick();
                            fight.holdZombie();
                            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
                            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(zombie));
                            h.assertTrue(zombie.isDoorBlocking() == (tick == DoorGuard.RAISE_TICKS),
                                    "Recovery-triggered defense also advances exactly once per entity tick: " + tick);
                        }
                    } finally {
                        if (previous == null) states.remove(zombie);
                        else states.put(zombie, previous);
                    }
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Native combat recovery fixture cannot initialize", error);
                } finally {
                    MiteConfig.BETTER_COMBAT_MOBS.set(previousMobCombat);MiteConfig.refreshEnchantmentRules();
                    if(!localConfig)MiteConfig.receiveServerSnapshot(previousConfig);
                }
            }
        });
    }
}
