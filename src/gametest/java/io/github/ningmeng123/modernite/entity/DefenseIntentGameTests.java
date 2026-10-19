package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.entity.ShieldBlockGoal;
import io.github.ningmeng123.mite.registry.MiteShields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Observe real player intent and exercise the delegated native shield goal. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class DefenseIntentGameTests {
    private static int nextArena;
    private static BlockPos arenaAnchor;

    private DefenseIntentGameTests() {}

    private static final class Fight implements AutoCloseable {
        final GameTestHelper helper;
        final Zombie mob;
        final Vec3 origin;
        final List<Player> players = new ArrayList<>();
        final List<Entity> projectiles = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<ChunkPos> arenaChunks = new ArrayList<>();
        final List<ChunkPos> forcedChunks = new ArrayList<>();
        boolean indexed;
        PlayerTeam team;

        Fight(GameTestHelper h, GameType... modes) {
            helper = h;
            // A test's template row may move independently of the global arena counter.
            // One shared anchor keeps arenas disjoint, including their forced chunk ownership.
            if (arenaAnchor == null) arenaAnchor = h.absolutePos(BlockPos.ZERO).immutable();
            BlockPos anchor = arenaAnchor;
            origin = new Vec3(anchor.getX() + 2.5D, 144D, anchor.getZ() + 4096.5D + nextArena++ * 80D);
            for (int x = BlockPos.containing(origin.add(-16, 0, 0)).getX() >> 4;
                    x <= BlockPos.containing(origin.add(16, 0, 0)).getX() >> 4; x++)
                for (int z = BlockPos.containing(origin.add(0, 0, -16)).getZ() >> 4;
                        z <= BlockPos.containing(origin.add(0, 0, 32)).getZ() >> 4; z++) {
                    ChunkPos chunk = new ChunkPos(x, z);
                    arenaChunks.add(chunk);
                    if (!h.getLevel().getForcedChunks().contains(chunk.toLong())) {
                        h.getLevel().setChunkForced(x, z, true);
                        forcedChunks.add(chunk);
                    }
                    h.getLevel().getChunk(x, z);
                }
            mob = EntityType.ZOMBIE.create(h.getLevel());
            mob.setPos(origin);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setPersistenceRequired();
            mob.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
            h.assertTrue(h.getLevel().addFreshEntity(mob), "The defender joins its real entity manager and receives native goals");
            mob.setNoAi(true);
            for (EquipmentSlot slot : EquipmentSlot.values()) if (slot != EquipmentSlot.BODY) mob.setItemSlot(slot, ItemStack.EMPTY);
            // A non-armor head covering prevents unrelated daylight fire from polluting real projectile damage.
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Blocks.CARVED_PUMPKIN));
            // Detector fixtures have no melee weapon, so only the explicit real-tick observations below sample them.
            // Lifecycle tests equip the sword before exercising their installed production ShieldBlockGoal.
            mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(MiteShields.get("iron")));
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
            mob.getAttribute(Attributes.ARMOR).setBaseValue(0D);
            mob.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0D);
            // Settle the normal growth modifier and its 1024 ceiling before recording fixture health.
            MonsterDayScaling.refresh(mob);
            mob.setHealth(1000F);
            mob.tickCount = 100;
            for (GameType mode : modes) {
                Player player = h.makeMockPlayer(mode);
                player.setNoGravity(true);
                player.getInventory().clearContent();
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
                player.setHealth(1000F);
                player.setPos(origin.add(0, 0, 4.5D + players.size() * 3D));
                face(player, mob.getBoundingBox().getCenter());
                h.assertTrue(h.getLevel().addFreshEntity(player), "The observed player is registered before its observation ticks");
                players.add(player);
            }
            if (!players.isEmpty()) mob.setTarget(players.getFirst());
        }

        Player player() { return players.getFirst(); }

        void position(Player player, double x, double z) {
            player.setPos(origin.add(x, 0D, z));
            player.setDeltaMovement(Vec3.ZERO);
            face(player, mob.getBoundingBox().getCenter());
        }

        ShieldBlockGoal goal() {
            return mob.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal())
                    .filter(ShieldBlockGoal.class::isInstance).map(ShieldBlockGoal.class::cast).findFirst().orElseThrow();
        }

        void tick(ShieldBlockGoal goal) {
            mob.tickCount++;
            if (goal != null) goal.tick();
            mob.tick();
            mob.setPos(origin);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.getSensing().tick();
        }

        boolean hurt(Player player) {
            mob.invulnerableTime = 0;
            mob.hurtTime = 0;
            return mob.hurt(mob.damageSources().playerAttack(player), 6F);
        }

        void wall() {
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(origin.add(-2D, 0D, 5D)),
                    BlockPos.containing(origin.add(2D, 3D, 5D)))) {
                BlockPos key = pos.immutable();
                blocks.putIfAbsent(key, helper.getLevel().getBlockState(key));
                helper.getLevel().setBlockAndUpdate(key, Blocks.STONE.defaultBlockState());
            }
        }

        Arrow arrow(double x, double z, double vx, double vz, boolean piercing) {
            Arrow arrow = EntityType.ARROW.create(helper.getLevel());
            arrow.setOwner(player());
            arrow.setPos(origin.add(x, 1.2D, z));
            arrow.setDeltaMovement(vx, .08D, vz);
            if (piercing) {
                CompoundTag tag = arrow.saveWithoutId(new CompoundTag());
                tag.putByte("PierceLevel", (byte)1);
                arrow.load(tag);
                helper.assertTrue(arrow.getPierceLevel() > 0, "The negative fixture is a real native piercing arrow");
            }
            helper.assertTrue(helper.getLevel().addFreshEntity(arrow), "The incoming arrow is a real registered projectile");
            projectiles.add(arrow);
            return arrow;
        }

        @Override public void close() {
            DefenseGuardFocus.clear(mob);
            BetterMobCombat.cancel(mob);
            mob.stopUsingItem();
            projectiles.forEach(Entity::discard);
            players.forEach(Entity::discard);
            mob.discard();
            blocks.forEach((pos, state) -> helper.getLevel().setBlockAndUpdate(pos, state));
            if (team != null) helper.getLevel().getScoreboard().removePlayerTeam(team);
            forcedChunks.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, false));
        }
    }

    private static void face(Player player, Vec3 point) {
        Vec3 toward = point.subtract(player.getEyePosition());
        float yaw = (float)(Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90D);
        float pitch = (float)-Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance()));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }

    private static List<Fight> fights(GameTestHelper h, int count) {
        List<Fight> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new Fight(h, GameType.SURVIVAL));
        return result;
    }

    private static void step(GameTestHelper h, List<Fight> fights, int delay, Runnable action) {
        h.runAfterDelay(delay, () -> {
            try { whenIndexed(h, fights, 40, action); }
            catch (RuntimeException | Error error) { fights.forEach(Fight::close); throw error; }
        });
    }

    private static void whenIndexed(GameTestHelper h, List<Fight> fights, int remaining, Runnable action) {
        boolean ready = true;
        for (Fight fight : fights) if (!fight.indexed) {
            // The two-argument overload deliberately hides spectators; indexing itself must include them here.
            List<Player> indexed = h.getLevel().getEntitiesOfClass(Player.class, fight.mob.getBoundingBox().inflate(32D), player -> true);
            // FULL generation and successful player registration precede spatial section promotion.
            // Wait for the complete projectile corridor and one actual defender tick before testing AI.
            fight.indexed = fight.mob.tickCount > 100
                    && h.getLevel().isPositionEntityTicking(fight.mob.blockPosition())
                    && fight.players.stream().allMatch(player -> h.getLevel().isPositionEntityTicking(player.blockPosition()))
                    && fight.arenaChunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(
                            new BlockPos(chunk.getMinBlockX(), 144, chunk.getMinBlockZ())))
                    && indexed.containsAll(fight.players);
            ready &= fight.indexed;
        }
        if (ready) { action.run(); return; }
        h.assertTrue(remaining > 0, "All registered fixture players must become visible to the actual spatial entity query");
        h.runAfterDelay(1, () -> {
            try { whenIndexed(h, fights, remaining - 1, action); }
            catch (RuntimeException | Error error) { fights.forEach(Fight::close); throw error; }
        });
    }

    private static void finish(GameTestHelper h, List<Fight> fights) { fights.forEach(Fight::close); h.succeed(); }

    private static DefenseIntent.Threat kind(GameTestHelper h, Fight fight, DefenseIntent.Kind expected, String why) {
        DefenseIntent.Threat threat = DefenseIntent.evaluate(fight.mob);
        h.assertTrue(threat != null && threat.kind() == expected, why + "; actual=" + (threat == null ? "none" : threat.kind()));
        return threat;
    }

    private static void none(GameTestHelper h, Fight fight, String why) {
        h.assertTrue(DefenseIntent.evaluate(fight.mob) == null, why);
    }

    @GameTest(template = "gear_empty")
    public static void heldWeaponsNeedReachAndFacingWhileIdleToolsAndMiningDoNotThreaten(GameTestHelper h) {
        List<Fight> fights = fights(h, 6);
        step(h, fights, 1, () -> {
            for (Fight fight : fights) fight.position(fight.player(), 0D, 1.5D);
            fights.get(1).player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            fights.get(1).player().swing(InteractionHand.MAIN_HAND);
            fights.get(2).player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            fights.get(3).player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            fights.get(3).player().setYRot(0F);
            fights.get(3).player().setXRot(0F);
            fights.get(4).player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            fights.get(5).player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            fights.get(5).player().swing(InteractionHand.MAIN_HAND);
            none(h, fights.get(0), "An idle empty-handed nearby player does not make a defender raise its shield");
            none(h, fights.get(1), "A mining pickaxe swing is not a combat windup");
            kind(h, fights.get(2), DefenseIntent.Kind.MELEE_READY, "An armed player facing the mob within actual melee reach is ready");
            none(h, fights.get(3), "An armed player facing away is not ready to hit this defender");
            none(h, fights.get(4), "Simply carrying an unused bow does not imply aiming");
            kind(h, fights.get(5), DefenseIntent.Kind.SWING, "A real sword swing aimed through the mob is an immediate windup");
            finish(h, fights);
        });
    }

    @GameTest(template = "gear_empty")
    public static void approachNeedsTwoRealTicksAndStopsIgnoreStaleVelocityAndTeleports(GameTestHelper h) {
        List<Fight> fights = fights(h, 2);
        Fight approach = fights.getFirst(), teleport = fights.get(1);
        step(h, fights, 1, () -> {
            for (Fight fight : fights) {
                fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                fight.position(fight.player(), 0D, 5.5D);
                none(h, fight, "A stationary armed player outside melee reach has no approach intent");
            }
            step(h, fights, 1, () -> {
                approach.position(approach.player(), 0D, 5.35D);
                none(h, approach, "One inward movement tick cannot trigger an approach guard");
                teleport.position(teleport.player(), 0D, 30D);
                none(h, teleport, "A distant player remains outside the approach radius");
                step(h, fights, 1, () -> {
                    approach.position(approach.player(), 0D, 5.2D);
                    kind(h, approach, DefenseIntent.Kind.APPROACH, "Two consecutive real server position changes confirm approach");
                    teleport.position(teleport.player(), 0D, 5.2D);
                    teleport.player().setDeltaMovement(0D, 0D, -.3D);
                    none(h, teleport, "A teleport into range cannot become a high-speed approach");
                    teleport.player().setDeltaMovement(Vec3.ZERO);
                    step(h, fights, 1, () -> {
                        approach.position(approach.player(), 0D, 5.2D);
                        approach.player().setDeltaMovement(0D, 0D, -.3D);
                        none(h, approach, "Stopped server positions clear approach despite a stale movement estimate");
                        approach.player().setDeltaMovement(Vec3.ZERO);
                        finish(h, fights);
                    });
                });
            });
        });
    }

    @GameTest(template = "gear_empty")
    public static void mainAndOffhandRangedAimingNeedTwoTicksAndRejectWrongAngles(GameTestHelper h) {
        List<Fight> fights = fights(h, 7);
        step(h, fights, 1, () -> {
            for (int i = 0; i < fights.size(); i++) {
                Fight fight = fights.get(i);
                fight.position(fight.player(), 0D, 12D);
                ItemStack stack = new ItemStack(i == 2 || i == 3 ? Items.CROSSBOW : i == 4 ? Items.TRIDENT : Items.BOW);
                InteractionHand hand = i == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                fight.player().setItemInHand(hand, stack);
                if (i == 3) stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
                else fight.player().startUsingItem(hand);
                if (i == 5) fight.player().setYRot(fight.player().getYRot() + 25F);
                if (i == 6) fight.player().setXRot(-70F);
                none(h, fight, "A newly observed ranged pose cannot bypass its two-tick intent confirmation");
            }
            step(h, fights, 1, () -> {
                for (int i = 0; i < 5; i++) kind(h, fights.get(i), DefenseIntent.Kind.RANGED_AIM,
                        "Confirmed ranged pose supports main/offhand bows, charging/loaded crossbows and tridents: fixture " + i);
                h.assertFalse(fights.get(3).player().isUsingItem(), "The loaded crossbow is genuinely threatening without active item use");
                none(h, fights.get(5), "A drawn bow outside the horizontal aiming cone targets somewhere else");
                none(h, fights.get(6), "A drawn bow aimed far above the defender is not an incoming shot");
                finish(h, fights);
            });
        });
    }

    @GameTest(template = "gear_empty", timeoutTicks = 100)
    public static void onlyAcceptedRecentAttackAttemptsCreateAShortDefensiveMemory(GameTestHelper h) {
        List<Fight> fights = fights(h, 2);
        step(h, fights, 1, () -> {
            for (Fight fight : fights) { fight.position(fight.player(), 0D, 4.5D); fight.player().setYRot(0F); }
            NeoForge.EVENT_BUS.post(new AttackEntityEvent(fights.getFirst().player(), fights.getFirst().mob));
            AttackEntityEvent canceled = new AttackEntityEvent(fights.get(1).player(), fights.get(1).mob);
            canceled.setCanceled(true);
            NeoForge.EVENT_BUS.post(canceled);
            kind(h, fights.getFirst(), DefenseIntent.Kind.RECENT_ATTACK, "An accepted empty-hand attack attempt leaves defensive memory without needing damage");
            none(h, fights.get(1), "An attack canceled by another hook leaves no defensive memory");
            step(h, fights, 21, () -> {
                none(h, fights.getFirst(), "The accepted attack memory expires after its twenty-tick window");
                finish(h, fights);
            });
        });
    }

    @GameTest(template = "gear_empty")
    public static void projectilePredictionRejectsPiercingNearMissesWallsRecedingAndLateArrows(GameTestHelper h) {
        List<Fight> fights = fights(h, 6);
        step(h, fights, 1, () -> {
            for (Fight fight : fights) { fight.position(fight.player(), 0D, 22D); fight.player().setYRot(0F); }
            fights.getFirst().arrow(0D, 10D, 0D, -1.5D, false);
            fights.get(1).arrow(0D, 10D, 0D, -1.5D, true);
            fights.get(2).arrow(2D, 10D, 0D, -1.5D, false);
            fights.get(3).wall();
            fights.get(3).arrow(0D, 10D, 0D, -1.5D, false);
            fights.get(4).arrow(0D, 10D, 0D, 1.5D, false);
            fights.get(5).arrow(0D, 28D, 0D, -.5D, false);
            step(h, fights, 1, () -> {
                DefenseIntent.Threat incoming = kind(h, fights.getFirst(), DefenseIntent.Kind.PROJECTILE, "A registered arrow on a native collision course is an immediate threat");
                h.assertTrue(incoming.source() instanceof AbstractArrow && incoming.impactTicks() >= 0D && incoming.impactTicks() <= 12D,
                        "The arrow threat retains its actual projectile and finite impending impact");
                none(h, fights.get(1), "Piercing arrows cannot be usefully blocked");
                none(h, fights.get(2), "A nearby arrow that misses the expanded body is not a threat");
                none(h, fights.get(3), "A stone obstruction protects the defender from the incoming arrow");
                none(h, fights.get(4), "A receding arrow does not trigger a guard");
                none(h, fights.get(5), "An arrow that cannot arrive within twelve ticks does not trigger an early guard");
                finish(h, fights);
            });
        });
    }

    @GameTest(template = "gear_empty")
    public static void nearbySecondaryAttackersAreSelectedStablyWithoutChangingThePursuitTarget(GameTestHelper h) {
        Fight fight = new Fight(h, GameType.SURVIVAL, GameType.SURVIVAL, GameType.SURVIVAL);
        List<Fight> fights = List.of(fight);
        step(h, fights, 1, () -> {
            Player idle = fight.players.get(0), left = fight.players.get(1), right = fight.players.get(2);
            fight.position(idle, 0D, -8D);
            fight.position(left, -1D, 1D);
            fight.position(right, 1D, 1D);
            h.assertTrue(h.getLevel().getEntitiesOfClass(Player.class, fight.mob.getBoundingBox().inflate(6D)).containsAll(List.of(left, right)),
                    "Both secondary attackers are accessible after crossing the fixture's chunk boundary");
            left.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            right.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            DefenseIntent.Threat selected = kind(h, fight, DefenseIntent.Kind.MELEE_READY, "The scan finds an armed secondary attacker outside the pursuit target");
            h.assertTrue(selected.attacker() != idle && fight.mob.getTarget() == idle, "Defense selection preserves the original pursuit target");
            step(h, fights, 1, () -> {
                DefenseIntent.Threat stable = DefenseIntent.evaluate(fight.mob, selected);
                h.assertTrue(stable != null && stable.source() == selected.source(), "Equal-priority attackers preserve the previous defensive focus");
                selected.attacker().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                selected.attacker().setYRot(0F);
                step(h, fights, 1, () -> {
                    DefenseIntent.Threat replacement = DefenseIntent.evaluate(fight.mob, selected);
                    h.assertTrue(replacement != null && replacement.source() != selected.source(), "A focus that loses its hostile intent yields to the remaining attacker");
                    h.assertTrue(fight.mob.getTarget() == idle, "Changing defensive focus still never overwrites the pursuit target");
                    Arrow arrow = fight.arrow(0D, 10D, 0D, -1.5D, false);
                    step(h, fights, 1, () -> {
                        DefenseIntent.Threat urgent = DefenseIntent.evaluate(fight.mob, replacement);
                        h.assertTrue(urgent != null && urgent.kind() == DefenseIntent.Kind.PROJECTILE && urgent.source() == arrow,
                                "A genuinely incoming arrow supersedes an ordinary melee warning");
                        finish(h, fights);
                    });
                });
            });
        });
    }

    @GameTest(template = "gear_empty")
    public static void deadCreativeSpectatorAndAlliedPlayersNeverStartDefensiveIntent(GameTestHelper h) {
        List<Fight> fights = new ArrayList<>();
        fights.add(new Fight(h, GameType.SURVIVAL));
        fights.add(new Fight(h, GameType.CREATIVE));
        fights.add(new Fight(h, GameType.SPECTATOR));
        fights.add(new Fight(h, GameType.SURVIVAL));
        step(h, fights, 1, () -> {
            for (Fight fight : fights) { fight.position(fight.player(), 0D, 1.5D); fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); }
            fights.getFirst().player().setHealth(0F);
            Fight allied = fights.get(3);
            allied.team = h.getLevel().getScoreboard().addPlayerTeam("intent_" + nextArena);
            h.getLevel().getScoreboard().addPlayerToTeam(allied.mob.getScoreboardName(), allied.team);
            h.getLevel().getScoreboard().addPlayerToTeam(allied.player().getScoreboardName(), allied.team);
            for (int i = 0; i < fights.size(); i++) none(h, fights.get(i), "Invalid player categories cannot trigger a guard: fixture " + i);
            finish(h, fights);
        });
    }

    @GameTest(template = "gear_empty")
    public static void nativeShieldGoalKeepsItsRaiseDelayFiniteWindowRecoveryAndAxeBreak(GameTestHelper h) {
        List<Fight> fights = fights(h, 2);
        step(h, fights, 1, () -> {
            for (Fight fight : fights) {
                fight.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                fight.position(fight.player(), 0D, 1.5D);
                fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                fight.mob.setYRot(0F); fight.mob.setYHeadRot(0F); fight.mob.yBodyRot = 0F;
                ShieldBlockGoal goal = fight.goal();
                h.assertTrue(goal.canUse(), "The real installed ShieldBlockGoal accepts a confirmed melee intent");
                goal.start();
                float before = fight.mob.getHealth();
                h.assertTrue(fight.hurt(fight.player()) && fight.mob.getHealth() < before, "The first native hit lands before the shield has risen");
                for (int tick = 0; tick < 4; tick++) fight.tick(goal);
                h.assertFalse(fight.mob.isBlocking(), "Four native use ticks cannot skip the five-tick raise delay");
                fight.tick(goal);
                h.assertTrue(fight.mob.isBlocking(), "Five complete native equipment ticks enable blocking");
                before = fight.mob.getHealth();
                h.assertFalse(fight.hurt(fight.player()), "A frontal sword hit is rejected by the complete native damage chain");
                h.assertValueEqual(fight.mob.getHealth(), before, "A fully blocked native hit preserves health");
                if (fight == fights.get(1)) {
                    fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                    fight.hurt(fight.player());
                    h.assertTrue(MobShields.disabled(fight.mob) && !MobShields.defending(fight.mob), "A real axe block disables the actual shield");
                    h.assertFalse(goal.canContinueToUse(), "A disabled shield cannot keep its committed goal active");
                    goal.stop();
                    h.assertFalse(goal.canUse(), "An axe break cannot immediately restart a guard");
                } else {
                    int active = 5;
                    while (goal.canContinueToUse() && active <= 40) { fight.tick(goal); active++; }
                    h.assertTrue(active >= 18 && active <= 30, "The total committed native guard lasts eighteen to thirty ticks, including its raise");
                    goal.stop();
                    h.assertFalse(MobShields.defending(fight.mob) || goal.canUse(), "An exhausted guard lowers the shield and begins a mandatory opening");
                    for (int tick = 0; tick < 23; tick++) fight.tick(null);
                    h.assertFalse(goal.canUse(), "The rest cannot end before twenty-four ticks");
                    for (int tick = 0; tick < 18; tick++) fight.tick(null);
                    h.assertTrue(goal.canUse(), "A persistent real threat can start a fresh guard after the full possible rest");
                }
            }
            finish(h, fights);
        });
    }

    @GameTest(template = "gear_empty")
    public static void guardTurnsAtBoundedSpeedTowardItsSecondaryThreatAndReleasesFocusAfterStopping(GameTestHelper h) {
        Fight fight = new Fight(h, GameType.SURVIVAL, GameType.SURVIVAL);
        List<Fight> fights = List.of(fight);
        step(h, fights, 1, () -> {
            Player pursuit = fight.players.getFirst(), attacker = fight.players.get(1);
            fight.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            fight.position(pursuit, 0D, -8D);
            fight.position(attacker, 1.5D, 0D);
            h.assertTrue(h.getLevel().getEntitiesOfClass(Player.class, fight.mob.getBoundingBox().inflate(6D)).contains(attacker),
                    "The side attacker remains visible to the real local spatial query");
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            fight.mob.setYRot(0F); fight.mob.setYHeadRot(0F); fight.mob.yBodyRot = 0F;
            ShieldBlockGoal goal = fight.goal();
            fight.mob.swing(InteractionHand.MAIN_HAND);
            h.assertFalse(goal.canUse(), "A defender already performing its own native weapon swing cannot raise a shield");
            for (int tick = 0; tick < 10; tick++) fight.tick(null);
            h.assertTrue(goal.canUse(), "The real shield goal becomes available after the native swing finishes");
            goal.start();
            h.assertTrue(DefenseGuardFocus.focus(fight.mob) != null && DefenseGuardFocus.focus(fight.mob).attacker() == attacker,
                    "The guard records the actual secondary attacker as its temporary focus");
            Vec3 knockback = new Vec3(.3D, 0D, .2D);
            fight.mob.setXxa(.5F);
            fight.mob.setZza(1F);
            fight.mob.setDeltaMovement(knockback);
            DefenseGuardFocus.beforeLook(fight.mob);
            h.assertTrue(fight.mob.xxa == 0F && fight.mob.zza == 0F, "Defending against another attacker pauses the original pursuit's movement inputs");
            h.assertTrue(fight.mob.getDeltaMovement().equals(knockback), "Pausing pursuit preserves the defender's existing physical knockback");
            fight.mob.setDeltaMovement(Vec3.ZERO);
            for (int tick = 0; tick < 6; tick++) {
                float beforeHead = fight.mob.getYHeadRot(), beforeBody = fight.mob.yBodyRot;
                DefenseGuardFocus.beforeLook(fight.mob);
                fight.mob.getLookControl().tick();
                fight.tick(goal);
                h.assertTrue(Math.abs(Mth.wrapDegrees(fight.mob.getYHeadRot() - beforeHead)) <= 25.001F,
                        "Native head tracking turns at most twenty-five degrees in a tick");
                h.assertTrue(Math.abs(Mth.wrapDegrees(fight.mob.yBodyRot - beforeBody)) <= 15.001F,
                        "The real torso turn hook turns the shield at most fifteen degrees in a tick");
            }
            h.assertTrue(Math.abs(Mth.wrapDegrees(fight.mob.yBodyRot)) >= 60F, "The torso actually follows a side attacker instead of keeping its old shield direction");
            h.assertTrue(fight.mob.getTarget() == pursuit, "Turning the defensive pose leaves the original pursuit target intact");
            goal.stop();
            h.assertTrue(DefenseGuardFocus.focus(fight.mob) == null && !DefenseGuardFocus.guarding(fight.mob), "Stopping restores ordinary movement and releases the defensive focus");
            fight.mob.getLookControl().setLookAt(pursuit, 25F, 25F);
            fight.mob.getLookControl().tick();
            h.assertTrue(DefenseGuardFocus.focus(fight.mob) == null, "Ordinary target looking cannot restore a finished defensive focus");
            finish(h, fights);
        });
    }

    @GameTest(template = "gear_empty", timeoutTicks = 120)
    public static void releasingTheBowKeepsTheCommittedShieldUpUntilItsRealArrowArrives(GameTestHelper h) {
        List<Fight> fights = fights(h, 1);
        Fight fight = fights.getFirst();
        step(h, fights, 1, () -> {
            fight.position(fight.player(), 0D, 12D);
            fight.player().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            fight.player().startUsingItem(InteractionHand.MAIN_HAND);
            fight.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            fight.mob.setYRot(0F); fight.mob.setYHeadRot(0F); fight.mob.yBodyRot = 0F;
            none(h, fight, "One observed draw tick cannot start the shield goal early");
            step(h, fights, 1, () -> {
                ShieldBlockGoal goal = fight.goal();
                h.assertTrue(goal.canUse(), "Two real aiming observations permit the installed native shield goal");
                goal.start();
                fight.player().stopUsingItem();
                h.assertFalse(fight.player().isUsingItem(), "The attacker genuinely releases its active bow pose");
                h.assertTrue(goal.canContinueToUse(), "Releasing the bow does not cancel the already committed guard window");
                Arrow arrow = fight.arrow(0D, 12D, 0D, -1.5D, false);
                arrow.setDeltaMovement(0D, .2D, -1.5D);
                arrow.setBaseDamage(4D);
                float health = fight.mob.getHealth();
                int wear = fight.mob.getOffhandItem().getDamageValue();
                waitForNativeBlock(h, fights, fight, goal, arrow, health, wear, 0);
            });
        });
    }

    private static void waitForNativeBlock(GameTestHelper h, List<Fight> fights, Fight fight, ShieldBlockGoal goal,
            Arrow arrow, float health, int wear, int ticks) {
        step(h, fights, 1, () -> {
            goal.tick();
            h.assertTrue(goal.canContinueToUse(), "The finite guard remains committed while its observed arrow is still arriving");
            h.assertValueEqual(fight.mob.getHealth(), health, "The real released arrow cannot damage the committed shield defender");
            if (fight.mob.getOffhandItem().getDamageValue() > wear) {
                h.assertTrue(ticks + 1 >= 5 && fight.mob.isBlocking(), "The arrow reaches the native shield only after its complete raise delay");
                h.assertTrue(!arrow.getDeltaMovement().equals(new Vec3(0D, .2D, -1.5D)), "The actual projectile has advanced through its native flight ticks");
                goal.stop();
                finish(h, fights);
                return;
            }
            h.assertTrue(ticks < 14 && !arrow.isRemoved(), "The genuine arrow must reach and wear the shield; a missing projectile is not a successful block");
            waitForNativeBlock(h, fights, fight, goal, arrow, health, wear, ticks + 1);
        });
    }
}
