package io.github.ningmeng123.modernite.entity;

/** Shared automatic-spawn unlocks use complete registry identities. @author NingMeng123 */
public final class ExtremeSpawnProgression {
    /** The first 64 complete days are reserved for base-game enemies. */
    public static final int FIRST_ITE_DAY = 65;

    private ExtremeSpawnProgression() {}

    public static int firstDay(String id) {
        if (id.startsWith("modern_ite:")) return FIRST_ITE_DAY;
        return switch (id) {
            case "mite:bone_lord", "mite:ancient_bone_lord" -> 33;
            default -> 0;
        };
    }

    public static boolean unlocked(String id, long highestDay) {
        return highestDay >= firstDay(id);
    }
    public static boolean unlocked(String id,long highestDay,int ancientBoneLordDay) {
        return highestDay >= (id.equals("mite:ancient_bone_lord")?ancientBoneLordDay:firstDay(id));
    }
}
