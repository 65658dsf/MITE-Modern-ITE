package io.github.ningmeng123.modernite.economy;

import java.util.List;

/** Explicit Extreme weights and source trade arithmetic. @author NingMeng123 */
public final class EconomyRules {
    public record Catch(String id, int weight) {}
    public static final int RETAINED_FISH_WEIGHT = 100;
    public static final List<Catch> CATCHES = List.of(
            new Catch("modern_ite:voucher_fishing", 36), new Catch("minecraft:apple", 16),
            new Catch("minecraft:potato", 16), new Catch("mite:banana", 16), new Catch("minecraft:bread", 16),
            new Catch("minecraft:carrot", 16), new Catch("mite:orange", 16),
            new Catch("mite:copper_nugget", 8), new Catch("mite:silver_nugget", 8),
            new Catch("minecraft:gold_nugget", 8), new Catch("minecraft:iron_nugget", 8),
            new Catch("mite:mithril_nugget", 4), new Catch("mite:ancient_metal_nugget", 4),
            new Catch("mite:adamantium_nugget", 4), new Catch("mite:vibranium_nugget", 4));
    public static final int TOTAL_CATCH_WEIGHT = RETAINED_FISH_WEIGHT + CATCHES.stream().mapToInt(Catch::weight).sum();
    private EconomyRules() {}
    /** Null retains the complete modern fish result, including the offshore large-fish conversion. */
    public static String catchAt(int roll) {
        if (roll < 0 || roll >= TOTAL_CATCH_WEIGHT) throw new IllegalArgumentException("Fishing roll outside pool");
        if (roll < RETAINED_FISH_WEIGHT) return null;
        roll -= RETAINED_FISH_WEIGHT;
        for (Catch entry : CATCHES) { if (roll < entry.weight()) return entry.id(); roll -= entry.weight(); }
        throw new IllegalStateException("Incomplete fishing pool");
    }
    public static int bookPrice(int level, boolean singleLevelSpecial, int roll) {
        if (level < 1 || roll < 0 || roll > 9) throw new IllegalArgumentException("Invalid book price input");
        return (int)Math.min(64L, (singleLevelSpecial ? 35L : (long)level * 5L) + roll);
    }
}
