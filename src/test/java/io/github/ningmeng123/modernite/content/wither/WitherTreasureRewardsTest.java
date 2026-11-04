package io.github.ningmeng123.modernite.content.wither;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exhaustive percentage boundaries for the user-defined treasure distribution. @author NingMeng123 */
public final class WitherTreasureRewardsTest {
    @Test void allHundredTicketsHaveExactlyOneRewardWithTheRequestedOdds() {
        var counts = new java.util.EnumMap<WitherTreasureRewards.Reward, Integer>(WitherTreasureRewards.Reward.class);
        for (int ticket = 0; ticket < 100; ticket++) counts.merge(WitherTreasureRewards.reward(ticket), 1, Integer::sum);
        assertEquals(30, counts.get(WitherTreasureRewards.Reward.GEM_3));
        assertEquals(20, counts.get(WitherTreasureRewards.Reward.GEM_4));
        assertEquals(10, counts.get(WitherTreasureRewards.Reward.GEM_5));
        assertEquals(5, counts.get(WitherTreasureRewards.Reward.GEM_6));
        assertEquals(20, counts.get(WitherTreasureRewards.Reward.DIAMONDS));
        assertEquals(15, counts.get(WitherTreasureRewards.Reward.ENCHANTED_BOOK));
        assertEquals(WitherTreasureRewards.Reward.GEM_3, WitherTreasureRewards.reward(29));
        assertEquals(WitherTreasureRewards.Reward.GEM_4, WitherTreasureRewards.reward(30));
        assertEquals(WitherTreasureRewards.Reward.GEM_5, WitherTreasureRewards.reward(50));
        assertEquals(WitherTreasureRewards.Reward.GEM_6, WitherTreasureRewards.reward(60));
        assertEquals(WitherTreasureRewards.Reward.DIAMONDS, WitherTreasureRewards.reward(65));
        assertEquals(WitherTreasureRewards.Reward.ENCHANTED_BOOK, WitherTreasureRewards.reward(85));
    }

    @Test void invalidTicketsCannotCreateAnExtraProbabilityBucket() {
        assertThrows(IllegalArgumentException.class, () -> WitherTreasureRewards.reward(-1));
        assertThrows(IllegalArgumentException.class, () -> WitherTreasureRewards.reward(100));
    }
}
