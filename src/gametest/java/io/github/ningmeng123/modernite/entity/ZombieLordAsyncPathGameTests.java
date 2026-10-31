package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.AsyncPathSearchEngine;
import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Indexed lord and its real, naturally timed eight-troop army use delayed pursuit without changing summon rules. @author NingMeng123 */
@GameTestHolder("modern_ite_async_lord")
@PrefixGameTestTemplate(false)
public final class ZombieLordAsyncPathGameTests {
    private static final TicketType<UUID> TICKET = TicketType.create("modern_ite_async_lord_test", UUID::compareTo);
    private static final Set<String> SUPPORT_TYPES = Set.of("modern_ite:miner_zombie", "mite:ghoul", "mite:wight", "mite:revenant");

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite_zombie_lord", batch = "zombie_lord_async_army", timeoutTicks = 1000)
    public static void anIndexedLordAndEightNaturallyTimedSupportsShareSafeDelayedPursuitWithNormalGravity(GameTestHelper h) {
        h.assertTrue(MiteConfig.asyncPathfindingEnabled(), "This compatibility test requires mite.asyncPathfinding=true");
        Fixture f = new Fixture(h);
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(f.chunks.stream().allMatch(chunk -> f.site.level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                            && f.site.level.isPositionEntityTicking(chunk.getWorldPosition())),
                    "The real arena chunks are published and ticking before the indexed army is observed");
            h.assertTrue(f.lord.onGround(), "The real indexed lord settles onto the actual floor under normal gravity; " + f.diagnostic(f.lord));
        }).thenExecute(f::beginCombat).thenWaitUntil(() -> {
            f.observe(h);
            long elapsed = f.site.level.getGameTime() - f.began;
            h.assertTrue(elapsed >= ZombieLordAbilities.SUMMON_INTERVAL * 8L + 2L,
                    "Waiting for eight unmodified real 51-combat-tick summon intervals; elapsed=" + elapsed);
            var troops = f.troops();
            h.assertTrue(troops.size() == 8 && f.memory().summons == 8,
                    "The actual normal owner ledger and successful summon counter both stop at eight living supports");
            h.assertTrue(f.completed.contains(f.lord.getUUID()) && f.completed.containsAll(troops.stream().map(Mob::getUUID).toList()),
                    "Waiting for a genuine completed pursuit for the lord and all eight real supports; completed=" + f.completed.size());
        }).thenExecute(() -> {
            var troops = f.troops();
            h.assertTrue(f.pending.contains(f.lord.getUUID()), "The actual addon lord entered the asynchronous pursuit path");
            h.assertTrue(troops.stream().anyMatch(troop -> f.pending.contains(troop.getUUID())),
                    "At least one actual owned troop entered asynchronous pursuit after settling with native gravity");
            h.assertTrue(f.memory().summons == 8 && SpawnPopulation.supportCount(f.lord) == 8,
                    "Delayed path decisions never alter summon quota accounting or create a ninth living troop");
            h.assertTrue(h.getLevel().getBlockState(f.origin.east(3)).is(Blocks.BEDROCK),
                    "The actual blocked target remains blocked throughout the compatibility test");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite_zombie_lord", batch = "zombie_lord_async_natural_goals", timeoutTicks = 1200)
    public static void autonomousLordAndSupportGoalsKeepMoveOwnershipWhileWaitingAndFinishTheirOwnBlockedDecisions(GameTestHelper h) {
        h.assertTrue(MiteConfig.asyncPathfindingEnabled(), "This autonomous compatibility test requires mite.asyncPathfinding=true");
        Fixture f = new Fixture(h, false);
        h.startSequence().thenWaitUntil(() -> {
            f.observeNatural(h);
            h.assertTrue(f.chunks.stream().allMatch(chunk -> f.site.level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                            && f.site.level.isPositionEntityTicking(chunk.getWorldPosition())),
                    "The autonomous lord arena uses actual loaded ticking chunks");
            h.assertTrue(f.lord.onGround(), "The autonomous lord naturally settles with its original goals enabled; " + f.diagnostic(f.lord));
        }).thenExecute(f::beginCombat).thenWaitUntil(() -> {
            // Observe public state only: registered goal selectors and normal server tick hooks drive every query.
            f.observeNatural(h);
            long elapsed = f.site.level.getGameTime() - f.began;
            h.assertTrue(elapsed >= ZombieLordAbilities.SUMMON_INTERVAL * 8L + 2L,
                    "Waiting for the unchanged autonomous 51-tick reinforcement cadence; elapsed=" + elapsed);
            var troops = f.troops();
            h.assertTrue(troops.size() == 8 && f.memory().summons == 8,
                    "The autonomous encounter retains its eight living support allowance; " + f.briefArmyDiagnostic());
            h.assertTrue(f.completed.contains(f.lord.getUUID()) && f.completed.containsAll(troops.stream().map(Mob::getUUID).toList()),
                    "The actual unmodified goals finish blocked pursuit decisions for the lord and all eight supports; completed="
                            + f.completed.size() + "; " + f.briefArmyDiagnostic());
        }).thenExecute(() -> {
            var troops = f.troops();
            h.assertTrue(f.pending.contains(f.lord.getUUID()) && f.reservedMove.contains(f.lord.getUUID()),
                    "The lord's actual source melee goal owns MOVE during an autonomous asynchronous decision");
            h.assertTrue(troops.stream().anyMatch(troop -> f.pending.contains(troop.getUUID()) && f.reservedMove.contains(troop.getUUID())),
                    "An actual support's original source melee goal also reserves MOVE while waiting");
            h.assertTrue(f.memory().summons == 8 && SpawnPopulation.supportCount(f.lord) == 8,
                    "Autonomous async pursuit does not reset the reinforcement count or bypass the eight-live-support ledger");
            h.assertTrue(h.getLevel().getBlockState(f.origin.east(3)).is(Blocks.BEDROCK), "Autonomous pursuit never crosses the actual bedrock divider");
            f.close();
        }).thenSucceed();
    }

    private static final class Fixture implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final BlockPos origin;
        final PathfinderMob lord;
        final ServerPlayer player;
        final UUID ticket = UUID.randomUUID();
        final List<ChunkPos> chunks = new ArrayList<>();
        final Set<UUID> seen = new HashSet<>(), pending = new HashSet<>(), completed = new HashSet<>();
        final Set<UUID> reservedMove = new HashSet<>();
        final List<Mob> admittedSupports = new ArrayList<>();
        final Consumer<EntityJoinLevelEvent> joined;
        final boolean controlledGoals;
        long began;
        String firstLostLordTarget;
        boolean combatStarted, closed;

        Fixture(GameTestHelper h) { this(h, true); }

        Fixture(GameTestHelper h, boolean controlledGoals) {
            this.controlledGoals = controlledGoals;
            site = new SpawnBalanceGameTests.Site(h);
            site.date(128, 128, true);
            origin = BlockPos.containing(site.origin);
            // A rear summon checks its destination collision, not a swept route from the lord.
            // Its maximum horizontal offset is sqrt(3.5^2 + 2.5^2) < 4.31 blocks, so a five-block
            // divider also keeps a turning lord from legitimately summoning into the player's room.
            for (int x = -6; x <= 13; x++) for (int z = -13; z <= 13; z++) for (int y = -1; y <= 4; y++) {
                boolean solid = y == -1 || y == 4 || x == -6 || x == 13 || z == -13 || z == 13 || x >= 3 && x <= 7;
                site.level.setBlock(origin.offset(x, y, z), (solid ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
            ChunkPos low = new ChunkPos(origin.offset(-6, 0, -13)), high = new ChunkPos(origin.offset(13, 0, 13));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                site.level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket);
            }
            player = site.player(8);
            player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));
            lord = (PathfinderMob) site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_LORD));
            lord.moveTo(Vec3.atBottomCenterOf(origin));
            MobAbilities.initialize(lord, (ExtremeMob)lord);
            EventHooks.finalizeMobSpawn(lord, site.level, site.level.getCurrentDifficultyAt(lord.blockPosition()), MobSpawnType.SPAWN_EGG, null);
            lord.setYRot(-90F);
            joined = event -> {
                if (event.getLevel() == site.level && event.getEntity() instanceof Mob mob && (mob == lord || owned(mob))) {
                    if (owned(mob)) admittedSupports.add(mob);
                    configureFixtureMob(mob);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, joined);
            h.assertTrue(site.level.addFreshEntity(lord), "The registered addon lord really joins the ticking server world");
            configureFixtureMob(lord);
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) {
                    System.err.println("[MITE lord async fixture] " + armyDiagnostic());
                    close();
                }
            });
        }

        MobMemory memory() { return ((ExtremeMob)lord).extremeMemory(); }
        void beginCombat() {
            if (combatStarted || !lord.isAddedToLevel() || !lord.onGround())
                throw new AssertionError("Combat begins exactly once after real registration and natural floor settling");
            if (lord.getTarget() != null || memory().summons != 0 || memory().supportCooldown != 0)
                throw new AssertionError("Preparation must not begin combat or advance a summon counter: " + armyDiagnostic());
            combatStarted = true;
            began = site.level.getGameTime();
            lord.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            lord.targetSelector.enableControlFlag(Goal.Flag.TARGET);
            // Assign only after the real join hooks and chunk/physics preparation have completed.
            // Neither the natural 51-tick countdown nor any successful summon counter is reset.
            lord.setTarget(player);
        }
        private void configureFixtureMob(Mob mob) {
            // NoAI also disables native travel, so stop only goal-driven actions in this planning fixture.
            // Keep the real entity tick, gravity, collision settling and addon summon cooldown running.
            if (controlledGoals) {
                mob.goalSelector.removeAllGoals(goal -> true);
                mob.targetSelector.removeAllGoals(goal -> true);
                mob.getBrain().removeAllBehaviors();
                mob.getNavigation().stop();
            } else if (!combatStarted) {
                // Preparation may take many server ticks; do not start autonomous combat or wandering early.
                mob.goalSelector.disableControlFlag(Goal.Flag.MOVE);
                mob.targetSelector.disableControlFlag(Goal.Flag.TARGET);
            }
            mob.setNoAi(false);
            mob.setNoGravity(false);
        }
        String diagnostic(Mob mob) {
            BlockPos below = mob.blockPosition().below();
            return "pos=" + mob.position() + ", velocity=" + mob.getDeltaMovement() + ", grounded=" + mob.onGround()
                    + ", noAI=" + mob.isNoAi() + ", noGravity=" + mob.isNoGravity() + ", noPhysics=" + mob.noPhysics
                    + ", alive=" + mob.isAlive() + ", removed=" + mob.isRemoved() + ", added=" + mob.isAddedToLevel()
                    + ", indexed=" + (site.level.getEntity(mob.getUUID()) == mob) + ", age=" + mob.tickCount
                    + ", entityTicking=" + site.level.isPositionEntityTicking(mob.blockPosition())
                    + ", below=" + below + "/" + site.level.getBlockState(below) + ", arena=" + origin;
        }
        String armyDiagnostic() {
            StringBuilder result = new StringBuilder("elapsed=").append(site.level.getGameTime() - began)
                    .append(", live=").append(troops().size()).append(", ledger=").append(SpawnPopulation.supportCount(lord))
                    .append(", cumulativeSummons=").append(memory().summons).append(", admitted=").append(admittedSupports.size())
                    .append(", clock=").append(memory().clock).append(", cooldown=").append(memory().supportCooldown)
                    .append(", lordTarget=").append(target(lord)).append(", digging=").append(R196MonsterAi.diggingEnabled(lord))
                    .append(", held=").append(lord.getMainHandItem()).append(", tool=").append(EquipmentDurability.toolProfile(lord.getMainHandItem()))
                    .append(", lordHealth=").append(lord.getHealth()).append(", lord={").append(diagnostic(lord)).append('}')
                    .append(", playerAlive=").append(player.isAlive()).append(", playerHealth=").append(player.getHealth())
                    .append(", firstLostLordTarget=").append(firstLostLordTarget);
            for (Mob troop : admittedSupports) {
                result.append("; troop=").append(BuiltInRegistries.ENTITY_TYPE.getKey(troop.getType()))
                        .append('/').append(troop.getUUID()).append(", health=").append(troop.getHealth())
                        .append(", target=").append(target(troop)).append(", state={").append(diagnostic(troop)).append('}');
                if (troop instanceof PathfinderMob pathfinder)
                    result.append(", pending=").append(MeleePursuitPaths.pending(pathfinder)).append(", stats=").append(MeleePursuitPaths.searchStats(pathfinder));
            }
            return result.toString();
        }
        String briefArmyDiagnostic() {
            return "elapsed=" + (site.level.getGameTime() - began) + ", live=" + troops().size()
                    + ", ledger=" + SpawnPopulation.supportCount(lord) + ", summons=" + memory().summons
                    + ", completed=" + completed.size() + ", playerAlive=" + player.isAlive()
                    + ", playerHealth=" + player.getHealth() + ", lordPos=" + lord.position()
                    + ", lordTarget=" + (lord.getTarget() == player ? "player" : lord.getTarget());
        }
        private static String target(Mob mob) {
            var target = mob.getTarget();
            return target == null ? "null" : BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + "/" + target.getUUID()
                    + "/distance=" + mob.distanceTo(target) + "/alive=" + target.isAlive() + "/pos=" + target.position();
        }
        boolean owned(Mob mob) {
            return mob.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                    && mob.getPersistentData().getUUID(SpawnPopulation.SUMMONER).equals(lord.getUUID());
        }
        List<Mob> troops() {
            List<Mob> result = new ArrayList<>();
            for (var entity : site.level.getAllEntities())
                if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved() && owned(mob)) result.add(mob);
            return result;
        }

        void observe(GameTestHelper h) {
            List<PathfinderMob> mobs = new ArrayList<>(); mobs.add(lord);
            for (Mob mob : troops()) {
                h.assertTrue(SUPPORT_TYPES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()),
                        "The lord still summons only its four actual legacy support species");
                h.assertTrue(mob.getTarget() == player && mob.getSpawnType() == MobSpawnType.MOB_SUMMONED,
                        "Every real troop retains the lord's player target and actual summon spawn reason");
                if (mob instanceof PathfinderMob pathfinder) mobs.add(pathfinder);
            }
            for (PathfinderMob mob : mobs) {
                h.assertFalse(mob.isNoAi(), "The indexed compatibility fixture keeps native travel enabled; " + diagnostic(mob));
                h.assertFalse(mob.isNoGravity(), "The indexed compatibility fixture keeps native mob gravity");
                if (!mob.onGround()) continue;
                if (seen.add(mob.getUUID())) MeleePursuitPaths.invalidate(mob);
                var before = MeleePursuitPaths.searchStats(mob);
                var path = MeleePursuitPaths.find(mob, player, null);
                var after = MeleePursuitPaths.searchStats(mob);
                if (MeleePursuitPaths.pending(mob)) {
                    pending.add(mob.getUUID());
                    h.assertTrue(after.failures() == before.failures() && after.retryAt() == before.retryAt(),
                            "Pending real army queries do not create failure backoff before a search finishes");
                } else if (after.searches() > 0 && after.failures() > 0) {
                    h.assertFalse(MeleePursuitPaths.reaches(mob, path, player), "The real sealed arena does not invent a path through bedrock");
                    completed.add(mob.getUUID());
                }
            }
            AsyncPathSearchEngine.pump(site.level.getServer(), TimeUnit.MILLISECONDS.toNanos(4));
        }

        void observeNatural(GameTestHelper h) {
            h.assertTrue(player.isAlive(), "The actual bedrock-separated player remains alive during autonomous pursuit; " + briefArmyDiagnostic());
            if (combatStarted && lord.getTarget() != player && firstLostLordTarget == null)
                firstLostLordTarget = "tick=" + site.level.getGameTime() + ", clock=" + memory().clock + ", target=" + target(lord)
                        + ", summons=" + memory().summons + ", state={" + diagnostic(lord) + '}';
            List<PathfinderMob> mobs = new ArrayList<>(); mobs.add(lord);
            for (Mob troop : troops()) {
                h.assertTrue(SUPPORT_TYPES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(troop.getType()).toString()),
                        "Autonomous reinforcements retain the original four-species summon list");
                h.assertTrue((troop.getTarget() == null || troop.getTarget() == player) && troop.getSpawnType() == MobSpawnType.MOB_SUMMONED,
                        "Autonomous supports retain their summon identity and never switch to another target; native sight memory may expire after a blocked decision");
                if (troop instanceof PathfinderMob pathfinder) mobs.add(pathfinder);
            }
            for (PathfinderMob mob : mobs) {
                h.assertTrue(!mob.isNoAi() && !mob.isNoGravity(), "Autonomous goals and native physics stay enabled; " + diagnostic(mob));
                h.assertTrue(mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof SourceMeleeGoal),
                        "The actual registered source melee goal stays installed on the autonomous mob");
                if (MeleePursuitPaths.pending(mob)) {
                    pending.add(mob.getUUID());
                    boolean meleeRunning = mob.goalSelector.getAvailableGoals().stream()
                            .anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof SourceMeleeGoal);
                    h.assertTrue(meleeRunning,
                            "Pending pursuit retains the source melee goal's MOVE reservation instead of letting idle wandering cancel it; " + diagnostic(mob));
                    reservedMove.add(mob.getUUID());
                } else if (MeleePursuitPaths.searchStats(mob).failures() > 0) completed.add(mob.getUUID());
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            NeoForge.EVENT_BUS.unregister(joined);
            MeleePursuitPaths.invalidate(lord);
            for (Mob troop : troops()) {
                if (troop instanceof PathfinderMob pathfinder) MeleePursuitPaths.invalidate(pathfinder);
                troop.discard(); SpawnPopulation.release(troop);
            }
            chunks.forEach(chunk -> site.level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket));
            site.close();
        }
    }
}
