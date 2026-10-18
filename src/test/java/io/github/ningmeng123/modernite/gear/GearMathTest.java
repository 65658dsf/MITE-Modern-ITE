package io.github.ningmeng123.modernite.gear;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Boundary evidence for the independent life curve and source forge parameters. @author NingMeng123 */
public final class GearMathTest {
    @Test void dayUnlocksCapTheCombinedLevelAndGemHealth() {
        assertEquals(6, GearMath.health(0, 1, 0));
        assertEquals(20, GearMath.health(135, 95, 24));
        assertEquals(30, GearMath.health(135, 96, 24));
        assertEquals(30, GearMath.health(135, 127, 24));
        assertEquals(40, GearMath.health(135, 128, 24));
        assertEquals(40, GearMath.health(135, 169, 24));
        assertEquals(84, GearMath.health(135, 170, 24));
    }
    @Test void healthPlateauDoesNotImposeAnExperienceLevelCapOrOverflow() {
        assertEquals(84, GearMath.health(300, 170, 24));
        assertEquals(84, GearMath.health(Integer.MAX_VALUE, Long.MAX_VALUE, 24));
        assertEquals(6, GearMath.health(Integer.MIN_VALUE, 1, 0));
        assertEquals(24, GearMath.health(45, 170, 0));
        assertEquals(30, GearMath.health(45, 170, 6));
        assertEquals(60, GearMath.health(134, 170, 2));
    }
    @Test void materialMaximumsAndTableRequirementsAreIndependentOfGrowth() {
        assertEquals(3, GearMath.maximumGrade("iron"));
        assertEquals(3, GearMath.maximumGrade("ancient_metal"));
        assertEquals(5, GearMath.maximumGrade("mithril"));
        assertEquals(7, GearMath.maximumGrade("adamantium"));
        assertEquals(10, GearMath.maximumGrade("vibranium"));
        assertEquals(0, GearMath.maximumGrade("wood"));
        assertEquals(1, GearMath.tableTier("ancient_metal"));
        assertEquals(3, GearMath.tableTier("vibranium"));
    }
    @Test void forgeFailureToolsAndSourceEnhancementAreDeterministic() {
        assertEquals(35, GearMath.failurePercent(0, 2, 2));
        assertEquals(55, GearMath.failurePercent(4, 2, 2));
        assertEquals(65, GearMath.failurePercent(7, 2, 2));
        assertEquals(15, GearMath.failurePercent(0, 6, 6));
        assertEquals(2048, GearMath.axeCost(0));
        assertEquals(10240, GearMath.hammerCost(9));
        assertEquals(348, GearMath.forgeTicks(0, 2, 2));
        assertEquals(0, GearMath.enhanceFactor(0));
        assertTrue(GearMath.enhanceFactor(10) > .87 && GearMath.enhanceFactor(10) < .89);
    }
}
