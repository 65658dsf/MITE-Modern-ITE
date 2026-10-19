package io.github.ningmeng123.modernite.entity;

/** Shared automatic-spawn unlocks use complete registry identities. @author NingMeng123 */
public final class ExtremeSpawnProgression {
    private ExtremeSpawnProgression() {}

    public static int firstDay(String id) {
        return switch (id) {
            case "mite:bone_lord", "mite:ancient_bone_lord",
                    "modern_ite:zombie_lord", "modern_ite:zombie_pigman_lord",
                    "modern_ite:zombie_elite_lord", "modern_ite:skeleton_elite_lord",
                    "modern_ite:witch_lord", "modern_ite:undying_zombie_emperor",
                    "modern_ite:annihilation_skeleton", "modern_ite:zombie_boss",
                    "modern_ite:black_infernal_creeper", "modern_ite:dark_iron_golem" -> 33;
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
