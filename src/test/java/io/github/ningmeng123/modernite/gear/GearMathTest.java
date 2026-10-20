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
    @Test void equipmentCapsUseForgeGradeInsteadOfMaterialGrowthBands() {
        for (String material : new String[]{"wood", "flint", "copper", "iron", "mithril", "adamantium", "vibranium"}) {
            assertEquals(15, GearMath.maximumGearLevel(material, false, 0));
            assertEquals(18, GearMath.maximumGearLevel(material, false, 3));
            assertEquals(25, GearMath.maximumGearLevel(material, false, 10));
        }
        assertEquals(0, GearMath.maximumGearLevel(null, false, 10));
        assertEquals(15, GearMath.maximumGearLevel("iron", false, -1));
        assertEquals(25, GearMath.maximumGearLevel("iron", false, Integer.MAX_VALUE));
    }
    @Test void armorCapsUseMiteHarvestLevelsRatherThanForgingToolTiers() {
        assertEquals(2, GearMath.materialHarvestLevel("flint"));
        assertEquals(2, GearMath.materialHarvestLevel("stone"));
        assertEquals(3, GearMath.materialHarvestLevel("obsidian"));
        assertEquals(4, GearMath.materialHarvestLevel("diamond"));
        assertEquals(0, GearMath.maximumGearLevel("leather", true, 0));
        for (String material : new String[]{"copper", "silver", "gold", "rusted_iron"})
            assertEquals(6, GearMath.maximumGearLevel(material, true, 0));
        assertEquals(9, GearMath.maximumGearLevel("iron", true, 0));
        assertEquals(9, GearMath.maximumGearLevel("ancient_metal", true, 0));
        assertEquals(12, GearMath.maximumGearLevel("mithril", true, 0));
        assertEquals(15, GearMath.maximumGearLevel("adamantium", true, 0));
        assertEquals(18, GearMath.maximumGearLevel("vibranium", true, 0));
        assertEquals(12, GearMath.maximumGearLevel("iron", true, 3));
        assertEquals(17, GearMath.maximumGearLevel("mithril", true, 5));
        assertEquals(22, GearMath.maximumGearLevel("adamantium", true, 7));
        assertEquals(28, GearMath.maximumGearLevel("vibranium", true, 10));
        assertEquals(28, GearMath.maximumGearLevel("vibranium", true, Integer.MAX_VALUE));
    }
    @Test void forgedExperienceRestoresDoublePrecisionAndFractionalPointAwards() {
        assertEquals(1050L, GearMath.experienceMillis(1000, 1));
        assertEquals(1150L, GearMath.experienceMillis(1000, 2));
        assertEquals(3750L, GearMath.experienceMillis(1000, 10));
        assertEquals(1048L, GearMath.experienceMillis(999, 1));
        assertEquals(3746L, GearMath.experienceMillis(999, 10));
        assertEquals(7496L, GearMath.experienceMillis(1999, 10));
        assertEquals(63_000L, GearMath.experienceMillis(60_000, 1));
        assertEquals(117_000L, GearMath.experienceMillis(90_000, 3));
        assertEquals(41_000L, GearMath.experienceMillis(20_000, 6));
        assertEquals(0L, GearMath.experienceMillis(Long.MIN_VALUE, 10));
        assertEquals(Long.MAX_VALUE, GearMath.experienceMillis(Long.MAX_VALUE, 10));
        assertEquals(1000L, GearMath.experienceMillis(1000, -1));
        assertEquals(3750L, GearMath.experienceMillis(1000, Integer.MAX_VALUE));
    }
}
