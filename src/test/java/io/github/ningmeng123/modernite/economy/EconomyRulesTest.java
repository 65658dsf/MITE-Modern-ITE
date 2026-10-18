package io.github.ningmeng123.modernite.economy;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact authored weights and source price boundaries. @author NingMeng123 */
public final class EconomyRulesTest {
    @Test void everyIntegerDrawHasExactlyItsAuthoredWeight() {
        var counts = new HashMap<String, Integer>();
        for (int roll = 0; roll < EconomyRules.TOTAL_CATCH_WEIGHT; roll++)
            counts.merge(String.valueOf(EconomyRules.catchAt(roll)), 1, Integer::sum);
        assertEquals(280, EconomyRules.TOTAL_CATCH_WEIGHT);
        assertEquals(100, counts.get("null"));
        for (var entry : EconomyRules.CATCHES) assertEquals(entry.weight(), counts.get(entry.id()));
        assertThrows(IllegalArgumentException.class, () -> EconomyRules.catchAt(-1));
        assertThrows(IllegalArgumentException.class, () -> EconomyRules.catchAt(280));
    }
    @Test void specialAndOrdinaryBookPricesKeepTheirSourceRangesAndCap() {
        assertEquals(5, EconomyRules.bookPrice(1, false, 0));
        assertEquals(34, EconomyRules.bookPrice(5, false, 9));
        assertEquals(35, EconomyRules.bookPrice(1, true, 0));
        assertEquals(44, EconomyRules.bookPrice(1, true, 9));
        assertEquals(64, EconomyRules.bookPrice(Integer.MAX_VALUE, false, 9));
        assertThrows(IllegalArgumentException.class, () -> EconomyRules.bookPrice(0, false, 0));
    }
}
