package io.github.ningmeng123.modernite;

import io.github.ningmeng123.modernite.entity.ExtremeSpawnProgression;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Every strong enemy shares the first post-blood-moon unlock. @author NingMeng123 */
class ExtremeSpawnProgressionTest {
    @Test void allTwelveStrongIdentitiesUnlockOnDayThirtyThree() {
        for(String id:new String[]{"mite:bone_lord","mite:ancient_bone_lord",
                "modern_ite:zombie_lord","modern_ite:zombie_pigman_lord","modern_ite:zombie_elite_lord",
                "modern_ite:skeleton_elite_lord","modern_ite:witch_lord","modern_ite:undying_zombie_emperor",
                "modern_ite:annihilation_skeleton","modern_ite:zombie_boss",
                "modern_ite:black_infernal_creeper","modern_ite:dark_iron_golem"}) {
            assertEquals(33,ExtremeSpawnProgression.firstDay(id),id);
            assertFalse(ExtremeSpawnProgression.unlocked(id,31),id);
            assertFalse(ExtremeSpawnProgression.unlocked(id,32),id);
            assertTrue(ExtremeSpawnProgression.unlocked(id,33),id);
            assertTrue(ExtremeSpawnProgression.unlocked(id,160),id);
        }
    }
    @Test void ordinaryIdentitiesDoNotAcquireAHiddenLordGate() {
        for(String id:new String[]{"minecraft:zombie","mite:phase_spider","modern_ite:wandering_witch","other:zombie_lord"}) {
            assertEquals(0,ExtremeSpawnProgression.firstDay(id));
            assertTrue(ExtremeSpawnProgression.unlocked(id,1));
        }
    }
    @Test void theAncientBoneLordDateCanChangeWithoutMovingOtherLordGates() {
        assertFalse(ExtremeSpawnProgression.unlocked("mite:ancient_bone_lord",95,96));
        assertTrue(ExtremeSpawnProgression.unlocked("mite:ancient_bone_lord",96,96));
        assertTrue(ExtremeSpawnProgression.unlocked("mite:ancient_bone_lord",1,1));
        assertFalse(ExtremeSpawnProgression.unlocked("modern_ite:zombie_lord",32,1));
        assertTrue(ExtremeSpawnProgression.unlocked("modern_ite:zombie_lord",33,96));
    }
}
