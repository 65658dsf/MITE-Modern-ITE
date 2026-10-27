package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual boss ability ticks acquire visible players without discarding a remembered hidden opponent. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class BossTargetSightGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("modern_ite_boss_target_sight", Integer::compareTo);

    @GameTest(template = "gear_empty", batch = "boss_target_sight")
    public static void randomBossRetargetCannotDiscoverAnUnseenPlayerBehindStone(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.wall(true);
        f.whenReady(() -> {
            h.assertFalse(SourceMonsterTargeting.canSee(f.boss, f.candidate, false),
                    "The newcomer is physically concealed by the opaque divider");
            f.retarget();
            h.assertTrue(f.boss.getTarget() == f.remembered,
                    "The real sixty-count boss ability cannot replace a known target with an unseen newcomer");
            f.wall(false);
            h.assertTrue(SourceMonsterTargeting.canSee(f.boss, f.candidate, false),
                    "Removing the divider exposes the newcomer to the boss's real source vision");
            f.retarget();
            h.assertTrue(f.boss.getTarget() == f.candidate,
                    "The same real ability can acquire the newly visible player instead of disabling retargeting");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "gear_empty", batch = "boss_target_sight")
    public static void randomBossRetargetRetainsTheCurrentPlayerAfterTheyHide(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            h.assertTrue(SourceMonsterTargeting.canSee(f.boss, f.candidate, false),
                    "The boss first encounters the player in clear view");
            f.retarget();
            h.assertTrue(f.boss.getTarget() == f.candidate, "The actual ability first accepts the visible player");
            f.wall(true);
            h.assertFalse(SourceMonsterTargeting.canSee(f.boss, f.candidate, false),
                    "The already acquired player then hides behind the opaque divider");
            f.retarget();
            h.assertTrue(f.boss.getTarget() == f.candidate,
                    "The visibility requirement for new targets preserves the existing pursuit memory");
            f.close();
            h.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final int ticketKey;
        final BlockPos origin;
        final List<ChunkPos> chunks = new ArrayList<>();
        final ExtremeMobs.ZombieVariant boss;
        final Player remembered, candidate;
        boolean closed;

        Fixture(GameTestHelper h) {
            helper = h;
            ticketKey = IDS.getAndIncrement();
            origin = new BlockPos(1_720_000 + ticketKey * 96,
                    h.absolutePos(BlockPos.ZERO).getY() + 12, 1_720_000);
            for (int x = -2; x <= 24; ++x) for (int z = -3; z <= 3; ++z) {
                h.getLevel().setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                for (int y = 0; y <= 3; ++y)
                    h.getLevel().setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            ChunkPos low = new ChunkPos(origin.offset(-2, 0, -3)), high = new ChunkPos(origin.offset(24, 0, 3));
            for (int x = low.x; x <= high.x; ++x) for (int z = low.z; z <= high.z; ++z) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketKey);
            }
            var created = ExtremeMobs.type(ExtremeKind.ZOMBIE_BOSS).create(h.getLevel());
            h.assertTrue(created instanceof ExtremeMobs.ZombieVariant, "The actual registered zombie boss creates");
            boss = (ExtremeMobs.ZombieVariant)created;
            boss.moveTo(Vec3.atBottomCenterOf(origin));
            boss.setNoAi(true);
            boss.setNoGravity(true);
            h.assertTrue(h.getLevel().addFreshEntity(boss), "The real boss passes production level-join initialization");
            remembered = player(origin.east(20));
            candidate = player(origin.east(8));
            boss.setTarget(remembered);
        }

        private Player player(BlockPos pos) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(pos));
            player.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(player), "The survival player enters the real entity index");
            return player;
        }

        void wall(boolean solid) {
            for (int z = -3; z <= 3; ++z) for (int y = 0; y <= 3; ++y)
                helper.getLevel().setBlock(origin.offset(3, y, z),
                        (solid ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        }

        void retarget() {
            boss.extremeMemory().summons = 59;
            MobAbilities.tick(boss, boss);
            helper.assertTrue(boss.extremeMemory().summons == 0,
                    "The real boss ability reaches and completes its periodic retarget branch");
        }

        void whenReady(Runnable action) {
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                    helper.getLevel().getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(16)).contains(candidate),
                    "The newcomer is published in the boss's actual nearby-player query before ticking the ability"))
                    .thenExecute(() -> {
                        if (closed) return;
                        try { action.run(); } catch (RuntimeException | Error failure) { close(); throw failure; }
                    });
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            boss.discard();
            remembered.discard();
            candidate.discard();
            for (ChunkPos chunk : chunks)
                helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
        }
    }
}
