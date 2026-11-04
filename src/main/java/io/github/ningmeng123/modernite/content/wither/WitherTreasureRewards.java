package io.github.ningmeng123.modernite.content.wither;

/** Integer tickets preserve the exact, mutually exclusive treasure odds. @author NingMeng123 */
public final class WitherTreasureRewards {
    public enum Reward { GEM_3, GEM_4, GEM_5, GEM_6, DIAMONDS, ENCHANTED_BOOK }

    private WitherTreasureRewards() {}

    public static Reward reward(int ticket) {
        if (ticket < 0 || ticket >= 100) throw new IllegalArgumentException("Treasure ticket must be in [0, 100)");
        if (ticket < 30) return Reward.GEM_3;
        if (ticket < 50) return Reward.GEM_4;
        if (ticket < 60) return Reward.GEM_5;
        if (ticket < 65) return Reward.GEM_6;
        if (ticket < 85) return Reward.DIAMONDS;
        return Reward.ENCHANTED_BOOK;
    }
}
