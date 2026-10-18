package io.github.ningmeng123.modernite.gear;

/** Shared strike timing for server feedback and both client views. @author NingMeng123 */
public final class ForgingAnimation {
    public static final int CYCLE_TICKS = 24;
    public static final int IMPACT_TICK = 8;

    private ForgingAnimation() {}

    public static boolean isImpactTick(int elapsed) {
        return elapsed > 0 && elapsed % CYCLE_TICKS == IMPACT_TICK;
    }

    public static float cycleTicks(float elapsed) {
        return Math.max(0, elapsed) % CYCLE_TICKS;
    }

    /** A short downward stroke, a moment of contact, then a slower recovery. */
    public static float hammerLift(float elapsed) {
        float phase = cycleTicks(elapsed);
        if (phase < 4) return 1;
        if (phase < IMPACT_TICK) {
            float swing = (phase - 4) / (IMPACT_TICK - 4);
            return 1 - swing * swing;
        }
        if (phase < 11) return 0;
        float lift = (phase - 11) / (CYCLE_TICKS - 11);
        return lift * lift * (3 - 2 * lift);
    }

    public static float impactStrength(float elapsed) {
        float sinceImpact = cycleTicks(elapsed) - IMPACT_TICK;
        return sinceImpact < 0 ? 0 : Math.max(0, 1 - sinceImpact / 4);
    }
}
