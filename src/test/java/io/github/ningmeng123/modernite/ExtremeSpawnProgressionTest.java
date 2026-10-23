package io.github.ningmeng123.modernite;

import io.github.ningmeng123.modernite.entity.ExtremeSpawnProgression;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Configurable ITE unlocks remain independent of base dates and later species floors. @author NingMeng123 */
class ExtremeSpawnProgressionTest {
    @Test void everyIteIdentityWaitsUntilSixtyFourCompleteDays() {
        for(String id:new String[]{"modern_ite:zombie_lord","modern_ite:zombie_pigman_lord","modern_ite:zombie_elite_lord",
                "modern_ite:skeleton_elite_lord","modern_ite:witch_lord","modern_ite:undying_zombie_emperor",
                "modern_ite:annihilation_skeleton","modern_ite:zombie_boss",
                "modern_ite:black_infernal_creeper","modern_ite:dark_iron_golem","modern_ite:wandering_witch",
                "modern_ite:zombie_iron_bucket","modern_ite:run_creeper","modern_ite:rabid_dog","modern_ite:future_monster"}) {
            assertEquals(65,ExtremeSpawnProgression.firstDay(id),id);
            assertFalse(ExtremeSpawnProgression.unlocked(id,33),id);
            assertFalse(ExtremeSpawnProgression.unlocked(id,63),id);
            assertFalse(ExtremeSpawnProgression.unlocked(id,64),id);
            assertTrue(ExtremeSpawnProgression.unlocked(id,65),id);
            assertTrue(ExtremeSpawnProgression.unlocked(id,160),id);
        }
    }
    @Test void otherNamespacesKeepTheirExistingDates() {
        for(String id:new String[]{"minecraft:zombie","mite:phase_spider","other:zombie_lord","other:modern_ite:zombie"}) {
            assertEquals(0,ExtremeSpawnProgression.firstDay(id));
            assertTrue(ExtremeSpawnProgression.unlocked(id,1));
        }
        for(String id:new String[]{"mite:bone_lord","mite:ancient_bone_lord"}) {
            assertEquals(33,ExtremeSpawnProgression.firstDay(id));
            assertFalse(ExtremeSpawnProgression.unlocked(id,32));
            assertTrue(ExtremeSpawnProgression.unlocked(id,33));
        }
    }
    @Test void theAncientBoneLordDateCanChangeWithoutMovingIteGates() {
        assertFalse(ExtremeSpawnProgression.unlocked("mite:ancient_bone_lord",95,96));
        assertTrue(ExtremeSpawnProgression.unlocked("mite:ancient_bone_lord",96,96));
        assertTrue(ExtremeSpawnProgression.unlocked("mite:ancient_bone_lord",1,1));
        assertFalse(ExtremeSpawnProgression.unlocked("modern_ite:zombie_lord",64,1));
        assertTrue(ExtremeSpawnProgression.unlocked("modern_ite:zombie_lord",65,96));
    }
    @Test void theSharedFloorNeverBringsForwardALaterSpeciesDate() {
        assertEquals(65,ExtremeKind.ZOMBIE_IRON_BUCKET.firstDay);
        assertEquals(65,ExtremeKind.EXCHANGER.firstDay);
        assertEquals(96,ExtremeKind.SKELETON_LIGHTNING.firstDay);
        assertEquals(96,ExtremeKind.WITCH_LORD.firstDay);
        assertEquals(128,ExtremeKind.UNDYING_ZOMBIE_EMPEROR.firstDay);
    }
    @Test void configuredFloorCanMoveEarlierOrLaterWithoutACompiledSixtyFiveDayMinimum() {
        for(String id:new String[]{"modern_ite:zombie_iron_bucket","modern_ite:run_creeper","modern_ite:rabid_dog"}) {
            assertFalse(ExtremeSpawnProgression.unlocked(id,32,192,33));
            assertTrue(ExtremeSpawnProgression.unlocked(id,33,192,33));
            assertFalse(ExtremeSpawnProgression.unlocked(id,172,192,173));
            assertTrue(ExtremeSpawnProgression.unlocked(id,173,192,173));
        }
        assertEquals(33,ExtremeKind.ZOMBIE_IRON_BUCKET.naturalFirstDay(33));
        assertEquals(64,ExtremeKind.EXCHANGER.naturalFirstDay(33));
        assertEquals(96,ExtremeKind.SKELETON_LIGHTNING.naturalFirstDay(33));
        assertEquals(128,ExtremeKind.UNDYING_ZOMBIE_EMPEROR.naturalFirstDay(33));
        assertEquals(173,ExtremeKind.UNDYING_ZOMBIE_EMPEROR.naturalFirstDay(173));
        assertTrue(ExtremeSpawnProgression.unlocked("minecraft:zombie",1,192,173));
        assertTrue(ExtremeSpawnProgression.unlocked("mite:bone_lord",33,192,173));
        assertFalse(ExtremeSpawnProgression.unlocked("mite:ancient_bone_lord",191,192,1));
        assertTrue(ExtremeSpawnProgression.unlocked("mite:ancient_bone_lord",192,192,173));
    }
}
