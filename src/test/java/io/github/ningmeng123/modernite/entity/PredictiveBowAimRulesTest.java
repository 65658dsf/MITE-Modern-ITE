package io.github.ningmeng123.modernite.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Independent flight integration and moving player collision evidence. @author NingMeng123 */
public final class PredictiveBowAimRulesTest {
    private static final double SPEED = 1.6D;
    private static final double GRAVITY = .05D;
    // Native arrows use float air drag even though their velocity components are doubles.
    private static final double DRAG = (double) .99F;

    @Test void stationaryPlayersAreHitAtSixFifteenAndThirtyBlocksWithHeightDifferences() {
        for (double distance : new double[] {6D, 15D, 30D}) {
            for (double height : new double[] {-8D, -3D, 0D, 3D, 6D}) {
                for (double bearing : new double[] {0D, Math.PI / 4D, Math.PI, -Math.PI / 3D}) {
                    Target target = new Target(distance * Math.cos(bearing), height,
                            distance * Math.sin(bearing), 0D, 0D);
                    var aim = aimAt(target);
                    assertEquals(SPEED, length(aim), 1.0E-7D,
                            "Reachable stationary solution must survive projectile speed normalization");
                    assertTrue(hitsPlayer(aim, target, SPEED, GRAVITY), () -> "Missed " + target);
                    assertInterceptCenter(aim, target, SPEED, GRAVITY);
                }
            }
        }
    }

    @Test void movingPlayersAreInterceptedWhereAimingAtTheirPresentPositionMisses() {
        Target[] targets = {
                new Target(30D, 0D, 0D, 0D, .28D),       // Perpendicular sprint.
                new Target(24D, 0D, 12D, .16D, -.20D),  // Diagonal movement.
                new Target(30D, 0D, 0D, -.28D, 0D),     // Approaching the archer.
                new Target(24D, 0D, 0D, .25D, 0D),      // Moving away.
                new Target(15D, 3D, 0D, 0D, .28D)
        };
        for (Target target : targets) {
            var predicted = aimAt(target);
            assertTrue(hitsPlayer(predicted, target, SPEED, GRAVITY), () -> "Prediction missed " + target);
            assertInterceptCenter(predicted, target, SPEED, GRAVITY);
            var presentPosition = PredictiveBowAimRules.aim(target.x, target.y, target.z,
                    0D, 0D, SPEED, GRAVITY);
            assertFalse(hitsPlayer(presentPosition, target, SPEED, GRAVITY),
                    () -> "Control should expose the cost of omitting movement lead: " + target);
        }
    }

    @Test void targetSpeedAndLeadDistanceAreBoundedIndependently() {
        var excessiveSpeed = PredictiveBowAimRules.aim(6D, 0D, 0D, 6D, 8D, SPEED, GRAVITY);
        var cappedSpeed = PredictiveBowAimRules.aim(6D, 0D, 0D, .36D, .48D, SPEED, GRAVITY);
        assertEquals(cappedSpeed.x(), excessiveSpeed.x(), 1.0E-9D);
        assertEquals(cappedSpeed.y(), excessiveSpeed.y(), 1.0E-9D);
        assertEquals(cappedSpeed.z(), excessiveSpeed.z(), 1.0E-9D);
        assertEquals(cappedSpeed.flightTicks(), excessiveSpeed.flightTicks(), 1.0E-9D);
        assertEquals(PredictiveBowAimRules.MAX_TARGET_SPEED,
                Math.hypot(excessiveSpeed.leadX(), excessiveSpeed.leadZ()) / excessiveSpeed.flightTicks(), 1.0E-9D);

        var longFlight = PredictiveBowAimRules.aim(1000D, 0D, 0D, .36D, .48D, SPEED, GRAVITY);
        assertEquals(PredictiveBowAimRules.MAX_LEAD_DISTANCE,
                Math.hypot(longFlight.leadX(), longFlight.leadZ()), 1.0E-9D);
        assertEquals(.75D, longFlight.leadX() / longFlight.leadZ(), 1.0E-9D);
        assertFinite(longFlight);
    }

    @Test void unreachableAndExtremeFiniteTargetsRetainFiniteBestEffortDirections() {
        Target unreachable = new Target(1000D, 100D, 0D, .3D, .4D);
        var aim = aimAt(unreachable);
        assertFinite(aim);
        assertFalse(hitsPlayer(aim, unreachable, SPEED, GRAVITY));
        assertTrue(aim.flightTicks() > 0D && aim.flightTicks() <= 60D);

        for (double[] position : new double[][] {
                {0D, 0D, 0D}, {1.0E-12D, -1.0E-12D, 0D},
                {Double.MAX_VALUE, 0D, 0D}, {-Double.MAX_VALUE, 0D, Double.MAX_VALUE},
                {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}
        }) {
            assertFinite(PredictiveBowAimRules.aim(position[0], position[1], position[2],
                    0D, 0D, SPEED, GRAVITY));
        }
    }

    @Test void zeroGravityStillProducesAnActualIntercept() {
        Target target = new Target(30D, 4D, -6D, .15D, .1D);
        var aim = PredictiveBowAimRules.aim(target.x, target.y, target.z,
                target.vx, target.vz, SPEED, 0D);
        assertTrue(hitsPlayer(aim, target, SPEED, 0D));
        assertInterceptCenter(aim, target, SPEED, 0D);
    }

    @Test void invalidCoordinatesVelocitySpeedAndGravityAreRejected() {
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (int argument = 0; argument < 7; argument++) {
                double[] values = {15D, 0D, 0D, .1D, 0D, SPEED, GRAVITY};
                values[argument] = invalid;
                assertThrows(IllegalArgumentException.class, () -> PredictiveBowAimRules.aim(
                        values[0], values[1], values[2], values[3], values[4], values[5], values[6]));
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> PredictiveBowAimRules.aim(15D, 0D, 0D, 0D, 0D, 0D, GRAVITY));
        assertThrows(IllegalArgumentException.class,
                () -> PredictiveBowAimRules.aim(15D, 0D, 0D, 0D, 0D, -1D, GRAVITY));
        assertThrows(IllegalArgumentException.class,
                () -> PredictiveBowAimRules.aim(15D, 0D, 0D, 0D, 0D, SPEED, -.01D));
    }

    private record Target(double x, double y, double z, double vx, double vz) {}
    private record Point(double x, double y, double z) {}

    private static PredictiveBowAimRules.Aim aimAt(Target target) {
        return PredictiveBowAimRules.aim(target.x, target.y, target.z, target.vx, target.vz, SPEED, GRAVITY);
    }

    private static double length(PredictiveBowAimRules.Aim aim) {
        return Math.hypot(Math.hypot(aim.x(), aim.z()), aim.y());
    }

    private static void assertFinite(PredictiveBowAimRules.Aim aim) {
        for (double value : new double[] {aim.x(), aim.y(), aim.z(), aim.flightTicks(), aim.leadX(), aim.leadZ()}) {
            assertTrue(Double.isFinite(value), () -> "Nonfinite solution: " + aim);
        }
        assertTrue(Math.hypot(aim.leadX(), aim.leadZ()) <= PredictiveBowAimRules.MAX_LEAD_DISTANCE + 1.0E-9D);
    }

    private static void assertInterceptCenter(PredictiveBowAimRules.Aim aim, Target target,
                                              double speed, double gravity) {
        Point position = integrateTo(aim, speed, gravity, aim.flightTicks());
        assertEquals(target.x + target.vx * aim.flightTicks(), position.x, 1.0E-5D);
        assertEquals(target.y, position.y, 1.0E-5D);
        assertEquals(target.z + target.vz * aim.flightTicks(), position.z, 1.0E-5D);
    }

    /** Integrate movement, drag, gravity in the same order as a live arrow, without solver formulas. */
    private static Point integrateTo(PredictiveBowAimRules.Aim aim, double speed, double gravity, double ticks) {
        double scale = speed / length(aim);
        double vx = aim.x() * scale, vy = aim.y() * scale, vz = aim.z() * scale;
        double x = 0D, y = 0D, z = 0D;
        for (int tick = 0; tick < Math.ceil(ticks); tick++) {
            double step = Math.min(1D, ticks - tick);
            x += vx * step;
            y += vy * step;
            z += vz * step;
            vx *= DRAG;
            vy = vy * DRAG - gravity;
            vz *= DRAG;
        }
        return new Point(x, y, z);
    }

    private static boolean hitsPlayer(PredictiveBowAimRules.Aim aim, Target target, double speed, double gravity) {
        double scale = speed / length(aim);
        double vx = aim.x() * scale, vy = aim.y() * scale, vz = aim.z() * scale;
        double x = 0D, y = 0D, z = 0D;
        for (int tick = 0; tick < 60; tick++) {
            Point from = new Point(x - target.x - target.vx * tick, y - target.y,
                    z - target.z - target.vz * tick);
            x += vx;
            y += vy;
            z += vz;
            Point to = new Point(x - target.x - target.vx * (tick + 1), y - target.y,
                    z - target.z - target.vz * (tick + 1));
            if (crossesPlayerBox(from, to)) return true;
            vx *= DRAG;
            vy = vy * DRAG - gravity;
            vz *= DRAG;
        }
        return false;
    }

    /** Relative motion reduces a moving 0.6 x 1.8 player box to one static slab intersection. */
    private static boolean crossesPlayerBox(Point from, Point to) {
        double enter = 0D, exit = 1D;
        double[] start = {from.x, from.y, from.z};
        double[] finish = {to.x, to.y, to.z};
        for (int axis = 0; axis < 3; axis++) {
            double halfSize = axis == 1 ? .9D : .3D;
            double delta = finish[axis] - start[axis];
            if (Math.abs(delta) < 1.0E-12D) {
                if (Math.abs(start[axis]) > halfSize) return false;
                continue;
            }
            double first = (-halfSize - start[axis]) / delta;
            double second = (halfSize - start[axis]) / delta;
            enter = Math.max(enter, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
            if (enter > exit) return false;
        }
        return true;
    }
}
