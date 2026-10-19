package io.github.ningmeng123.modernite.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Geometry and focus selection independent of Minecraft initialization. @author NingMeng123 */
class DefenseIntentRulesTest {
    private static final DefenseIntentRules.Obstruction CLEAR = (x, y, z, ex, ey, ez) -> Double.POSITIVE_INFINITY;

    @Test void meleeFacingIncludesSixtyDegreesButNotThePlayersBack() {
        assertTrue(DefenseIntentRules.meleeFacing(Math.sin(Math.PI / 3), Math.cos(Math.PI / 3), 0, 1));
        assertFalse(DefenseIntentRules.meleeFacing(1, 0, 0, 1));
        assertFalse(DefenseIntentRules.meleeFacing(0, -1, 0, 1));
    }

    @Test void approachNeedsTwoObservedTicksClosingSpeedAndSixBlockLimit() {
        assertFalse(DefenseIntentRules.approaching(36, .03, true, 1));
        assertTrue(DefenseIntentRules.approaching(36, .03, true, 2));
        assertFalse(DefenseIntentRules.approaching(36.001, .3, true, 2));
        assertFalse(DefenseIntentRules.approaching(16, -.3, true, 2));
        assertFalse(DefenseIntentRules.approaching(16, .3, false, 2));
    }

    @Test void bowAimAllowsVerticalCompensationButOnlyTenDegreesHorizontally() {
        double up = Math.toRadians(25);
        assertTrue(DefenseIntentRules.rangedFacing(0, 0, 20, 0, Math.sin(up), Math.cos(up)));
        assertFalse(DefenseIntentRules.rangedFacing(0, 0, 20, 0, .5, Math.sqrt(.75)));
        assertTrue(DefenseIntentRules.rangedFacing(0, 0, 20, Math.sin(Math.toRadians(10)), 0, Math.cos(Math.toRadians(10))));
        assertFalse(DefenseIntentRules.rangedFacing(0, 0, 20, Math.sin(Math.toRadians(11)), 0, Math.cos(Math.toRadians(11))));
    }

    @Test void farmToolsDoNotAcquireCombatIntentMerelyByBeingHeld() {
        for (String family : new String[]{"PICKAXE", "SHOVEL", "HOE", "MATTOCK", "SHEARS"})
            assertFalse(DefenseIntentRules.combatFamily(family), family);
        for (String family : new String[]{"SWORD", "AXE", "BATTLE_AXE", "HATCHET", "DAGGER", "CLUB", "WAR_HAMMER"})
            assertTrue(DefenseIntentRules.combatFamily(family), family);
    }

    @Test void equalThreatsKeepFocusButUrgentArrowsCanReplaceIt() {
        assertFalse(DefenseIntentRules.replace(1, 100, true, 1, 0, false));
        assertTrue(DefenseIntentRules.replace(1, 100, false, 1, 100, true));
        assertTrue(DefenseIntentRules.replace(1, 100, true, 2, 0, false));
        assertFalse(DefenseIntentRules.replace(3, 8, true, 3, 6.01, false));
        assertTrue(DefenseIntentRules.replace(3, 8, true, 3, 6, false));
        assertTrue(DefenseIntentRules.replace(3, 6.01, false, 3, 8, true));
        assertFalse(DefenseIntentRules.replace(3, 6, false, 3, 8, true));
    }

    @Test void arrowsThatHitThePaddedBodyArePredictedWithinTwelveTicks() {
        double impact = impact(-15, 1, 0, 2, 0, 0, CLEAR);
        assertTrue(impact > 7 && impact < 9, "A real gravity/drag trajectory enters the body");
        assertEquals(Double.POSITIVE_INFINITY, impact(-40, 1, 0, 1, 0, 0, CLEAR));
    }

    @Test void awayPassingStoppedAndWallBoundArrowsDoNotThreaten() {
        assertEquals(Double.POSITIVE_INFINITY, impact(-6, 1, 0, -2, 0, 0, CLEAR));
        assertEquals(Double.POSITIVE_INFINITY, impact(-6, 1, 4, 2, 0, 0, CLEAR));
        assertEquals(Double.POSITIVE_INFINITY, impact(-6, 1, 0, 0, 0, 0, CLEAR));
        assertEquals(Double.POSITIVE_INFINITY, impact(-6, 1, 0, 2, 0, 0, (x, y, z, ex, ey, ez) -> .5D));
    }

    @Test void aWallAfterBodyContactDoesNotHideAnActualIncomingHit() {
        double impact = impact(-1, 1, 0, 2, 0, 0, (x, y, z, ex, ey, ez) -> .9D);
        assertTrue(impact >= 0 && impact < 1);
        assertEquals(Double.POSITIVE_INFINITY, impact(-1, 1, 0, 2, 0, 0, (x, y, z, ex, ey, ez) -> .1D));
    }

    private static double impact(double x, double y, double z, double vx, double vy, double vz,
            DefenseIntentRules.Obstruction obstruction) {
        return DefenseIntentRules.projectileImpact(x, y, z, vx, vy, vz, .05, DefenseIntentRules.AIR_DRAG,
                -.6, -.3, -.6, .6, 2.1, .6, obstruction);
    }
}
