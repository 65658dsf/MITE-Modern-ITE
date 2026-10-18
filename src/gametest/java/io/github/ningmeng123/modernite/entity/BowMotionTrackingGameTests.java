package io.github.ningmeng123.modernite.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Network movement estimates must not keep leading a player who has stopped. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class BowMotionTrackingGameTests {
    private BowMotionTrackingGameTests() {}

    @GameTest(template = "gear_empty")
    public static void serverPositionsDetectStopsReversalsAndTeleports(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absolutePos(new BlockPos(1, 3, 1)).getCenter());
        // Deliberately leave a stale movement estimate, like the last successful client move packet.
        player.setDeltaMovement(.3D, 0D, 0D);
        h.assertTrue(PredictiveBowAiming.movement(player).x > .29D, "First observation can use known movement");
        h.runAfterDelay(1, () -> {
            player.setPos(player.position().add(.3D, 0D, 0D));
            h.assertTrue(Math.abs(PredictiveBowAiming.movement(player).x - .3D) < 1.0E-6D,
                    "Server position difference measures movement per tick");
            h.assertTrue(Math.abs(PredictiveBowAiming.movement(player).x - .3D) < 1.0E-6D,
                    "Other archers in the same tick share the observation");
            h.runAfterDelay(1, () -> {
                h.assertTrue(player.getKnownMovement().x > .29D, "The old client movement is still present");
                h.assertTrue(PredictiveBowAiming.movement(player).equals(Vec3.ZERO),
                        "A stationary player gets no movement lead despite the stale estimate");
                h.runAfterDelay(1, () -> {
                    player.setPos(player.position().add(-.3D, 0D, 0D));
                    h.assertTrue(PredictiveBowAiming.movement(player).x < -.29D,
                            "A reversal replaces the previous direction immediately");
                    player.setPos(player.position().add(40D, 0D, 0D));
                    h.assertTrue(PredictiveBowAiming.movement(player).equals(Vec3.ZERO),
                            "A teleport in the release tick cannot produce a movement lead");
                    h.succeed();
                });
            });
        });
    }
}
