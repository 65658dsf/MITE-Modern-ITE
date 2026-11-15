package io.github.ningmeng123.modernite.gear;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Formula boundaries distinguish combined levels from independently squared gem bonuses. @author NingMeng123 */
public final class GemEffectRulesTest {
    @Test void efficiencyCombinesOrdinaryLevelsWithGemsBeforeSquaring() {
        assertEquals(5D, GemEffectRules.efficiencyAddition(0, 2));
        assertEquals(12D, GemEffectRules.efficiencyAddition(2, 2));
        assertEquals(96D, GemEffectRules.efficiencyAddition(5, 6));
        assertEquals(0D, GemEffectRules.efficiencyAddition(5, 0));
        assertEquals(0D, GemEffectRules.efficiencyAddition(0, -1));
        assertEquals(37D, GemEffectRules.efficiencyAddition(0, 100));
    }

    @Test void durabilityAddsThreePointSevenFivePercentPerGemRankToTheSharedRoll() {
        assertEquals(.0375F, GemEffectRules.unbreakingNegationChance(0F, 1), .000001F);
        assertEquals(.225F, GemEffectRules.unbreakingNegationChance(0F, 6), .000001F);
        assertEquals(.675F, GemEffectRules.unbreakingNegationChance(.45F, 6), .000001F);
        assertEquals(.45F, GemEffectRules.unbreakingNegationChance(.45F, 0), .000001F);
        assertEquals(1F, GemEffectRules.unbreakingNegationChance(.9F, 6));
    }
}
