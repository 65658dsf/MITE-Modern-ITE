package io.github.ningmeng123.modernite.entity;

/** Release-time interception using native, discrete arrow physics. @author NingMeng123 */
public final class PredictiveBowAimRules {
    public static final double AIR_DRAG = (double) .99F;
    public static final double MAX_TARGET_SPEED = .6D;
    public static final double MAX_LEAD_DISTANCE = 8D;
    private static final double MAX_FLIGHT_TICKS = 60D;

    private PredictiveBowAimRules() {}

    public record Aim(double x, double y, double z, double flightTicks, double leadX, double leadZ) {}

    public static Aim aim(double dx, double dy, double dz, double vx, double vz, double speed, double gravity) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || !Double.isFinite(vx) || !Double.isFinite(vz) || !Double.isFinite(speed)
                || !Double.isFinite(gravity) || speed <= 0D || gravity < 0D) {
            throw new IllegalArgumentException("Finite coordinates, positive speed and nonnegative gravity required");
        }
        double movement = Math.hypot(vx, vz);
        if (movement > MAX_TARGET_SPEED) {
            vx *= MAX_TARGET_SPEED / movement;
            vz *= MAX_TARGET_SPEED / movement;
        }
        Aim best = atTime(dx, dy, dz, vx, vz, gravity, .01D);
        double bestError = Double.POSITIVE_INFINITY;
        double previousTime = .01D;
        for (double time = .25D; time <= MAX_FLIGHT_TICKS; time += .25D) {
            Aim candidate = atTime(dx, dy, dz, vx, vz, gravity, time);
            double error = length(candidate) - speed;
            // The first crossing gives the low trajectory. Refine instead of rounding flight time.
            if (error <= 0D) {
                double low = previousTime, high = time;
                for (int iteration = 0; iteration < 24; iteration++) {
                    double middle = (low + high) * .5D;
                    if (length(atTime(dx, dy, dz, vx, vz, gravity, middle)) > speed) low = middle;
                    else high = middle;
                }
                return atTime(dx, dy, dz, vx, vz, gravity, (low + high) * .5D);
            }
            if (error < bestError) {
                bestError = error;
                best = candidate;
            }
            previousTime = time;
        }
        // Unreachable targets retain a finite best-effort direction and ordinary projectile behavior.
        return best;
    }

    private static double length(Aim aim) {
        return Math.hypot(Math.hypot(aim.x, aim.z), aim.y);
    }

    private static Aim atTime(double dx, double dy, double dz, double vx, double vz, double gravity, double time) {
        double leadX = vx * time, leadZ = vz * time;
        double lead = Math.hypot(leadX, leadZ);
        if (lead > MAX_LEAD_DISTANCE) {
            leadX *= MAX_LEAD_DISTANCE / lead;
            leadZ *= MAX_LEAD_DISTANCE / lead;
        }
        int whole = (int) time;
        double fraction = time - whole;
        double drag = Math.pow(AIR_DRAG, whole);
        double displacement = (1D - drag) / (1D - AIR_DRAG) + fraction * drag;
        // Native arrows move, then apply drag, then gravity. Interpolate the last flight segment.
        double drop = gravity / (1D - AIR_DRAG)
                * (whole - (1D - drag) / (1D - AIR_DRAG) + fraction * (1D - drag));
        return new Aim((dx + leadX) / displacement, (dy + drop) / displacement,
                (dz + leadZ) / displacement, time, leadX, leadZ);
    }
}
