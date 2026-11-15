package io.github.ningmeng123.modernite.gear;

/** Extreme's gem additions before the shared efficiency and Unbreaking formulas. @author NingMeng123 */
public final class GemEffectRules {
    private GemEffectRules() {}

    public static double efficiencyAddition(int ordinaryLevel, int gemRank) {
        int rank = Math.clamp(gemRank, 0, 6);
        int ordinary = Math.max(ordinaryLevel, 0);
        return efficiencyBonus((long) ordinary + rank) - efficiencyBonus(ordinary);
    }

    private static double efficiencyBonus(long level) {
        return level > 0 ? (double) level * level + 1D : 0D;
    }

    public static float unbreakingNegationChance(float original, int gemRank) {
        return Math.clamp(original + Math.clamp(gemRank, 0, 6) * .05F * .75F, 0F, 1F);
    }
}
