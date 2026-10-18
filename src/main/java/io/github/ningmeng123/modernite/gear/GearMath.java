package io.github.ningmeng123.modernite.gear;

/** Pure source arithmetic, shared by menus, server operations and verification. @author NingMeng123 */
public final class GearMath {
    private GearMath() {}
    public static double health(int level, long day, int gems) {
        int bounded = Math.clamp(level, 0, 135);
        return Math.min(day < 96 ? 20 : day < 128 ? 30 : day < 170 ? 40 : Double.MAX_VALUE,
                Math.min(60, 6 + bounded / 5 * 2) + Math.clamp(gems, 0, 24));
    }
    public static double enhanceFactor(int grade) { return Math.pow(1.028, Math.clamp(grade, 0, 10) * 2.28) - 1; }
    public static int maximumGrade(String material) {
        if (material == null) return 0;
        return switch (material) {
            case "iron", "ancient_metal" -> 3;
            case "mithril" -> 5; case "adamantium" -> 7; case "vibranium" -> 10; default -> 0;
        };
    }
    public static int tableTier(String material) {
        return switch (material == null ? "" : material) {
            case "iron" -> 0; case "ancient_metal", "mithril" -> 1;
            case "adamantium" -> 2; case "vibranium" -> 3; default -> 4;
        };
    }
    public static int materialTier(String material) {
        return switch (material == null ? "" : material) {
            case "iron" -> 2; case "ancient_metal" -> 3; case "mithril" -> 4;
            case "adamantium" -> 5; case "vibranium" -> 6; default -> 0;
        };
    }
    public static int failurePercent(int grade, int axeTier, int hammerTier) {
        return Math.clamp((grade < 4 ? 35 : grade < 7 ? 55 : 65)
                - Math.max(0, axeTier - 2) * 2 - Math.max(0, hammerTier - 2) * 3, 0, 100);
    }
    public static int forgeTicks(int grade, int axeTier, int hammerTier) {
        return Math.max(1, (int)Math.round((400 + 200 * grade) / (.25 + axeTier / 5D + hammerTier / 4D)));
    }
    public static int axeCost(int grade) {
        return new int[]{2048, 2048, 3072, 3072, 4096, 4096, 6144, 6144, 8192, 8192}[Math.clamp(grade, 0, 9)];
    }
    public static int hammerCost(int grade) {
        return new int[]{2048, 3072, 3072, 4096, 4096, 6144, 6144, 8192, 10240, 10240}[Math.clamp(grade, 0, 9)];
    }
}
