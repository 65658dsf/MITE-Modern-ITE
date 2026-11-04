package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.content.wither.WitherEncounters;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Boss health is authoritative after optional mods and remains stable across saved joins. @author NingMeng123 */
@GameTestHolder("modern_ite_boss")
@PrefixGameTestTemplate(false)
public final class BossHealthGameTests {
    private BossHealthGameTests() {}

    @GameTest(template = "gear_empty")
    public static void actualFreshJoinsKeepBothWithersAtThirteenHundredAndDragonAtSeventyFiveHundred(GameTestHelper h) {
        for (boolean enhanced : new boolean[]{false, true}) {
            WitherBoss wither = WitherEncounterGameTests.create(h, enhanced);
            try {
                h.assertTrue(wither.getMaxHealth() == 1300F && wither.getAttributeBaseValue(Attributes.MAX_HEALTH) == 1300D,
                        "Both actual Wither joins finish with the shared 1300-health baseline");
                h.assertTrue(WitherEncounters.isEnhanced(wither) == enhanced,
                        "Sharing health does not merge the ordinary and enhanced encounter identities");
                h.assertTrue(wither.getInvulnerableTicks() == (enhanced ? 600 : 220),
                        "The two encounters retain their distinct summon charge durations");
            } finally { wither.discard(); }
        }
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(h.getLevel());
        h.assertTrue(dragon != null, "The actual Ender Dragon registry type creates");
        try {
            dragon.moveTo(h.absolutePos(new BlockPos(2, 4, 2)).getCenter());
            dragon.setNoAi(true);
            h.assertTrue(h.getLevel().addFreshEntity(dragon), "The actual dragon passes every installed mod's join callback");
            h.assertTrue(dragon.getMaxHealth() == 7500F && dragon.getAttributeBaseValue(Attributes.MAX_HEALTH) == 7500D,
                    "The final installed-mod join result retains the 7500-health dragon baseline");
        } finally { dragon.discard(); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void savedWithersRepairLegacyHealthWithoutHealingOrCompounding(GameTestHelper h) {
        for (boolean enhanced : new boolean[]{false, true}) {
            WitherBoss original = WitherEncounterGameTests.create(h, enhanced);
            WitherBoss restored = EntityType.WITHER.create(h.getLevel());
            h.assertTrue(restored != null, "The real Wither type creates the disk reload instance");
            try {
                legacyHealth(original, 600D);
                original.setInvulnerableTicks(0);
                restored.load(original.saveWithoutId(new CompoundTag()));
                restored.setUUID(UUID.randomUUID());
                h.assertTrue(restored.getMaxHealth() == 600F && restored.getHealth() == 150F,
                        "The native save/load carries the actual old 600-health, quarter-health boss state");
                loadedJoin(h, restored);
                assertQuarterHealth(h, restored, 1300F);
                h.assertTrue(WitherEncounters.isEnhanced(restored) == enhanced,
                        "Legacy health migration preserves the saved ordinary or enhanced identity");
                legacyHealth(restored, 400D);
                loadedJoin(h, restored);
                assertQuarterHealth(h, restored, 1300F);
                loadedJoin(h, restored);
                assertQuarterHealth(h, restored, 1300F);
            } finally { original.discard(); restored.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void savedDragonRepairsOldAndOptionalModHealthWithoutHealingOrCompounding(GameTestHelper h) {
        EnderDragon original = EntityType.ENDER_DRAGON.create(h.getLevel());
        EnderDragon restored = EntityType.ENDER_DRAGON.create(h.getLevel());
        h.assertTrue(original != null && restored != null, "The actual dragon registry creates both saved and restored entities");
        try {
            original.moveTo(h.absolutePos(new BlockPos(2, 4, 2)).getCenter());
            original.setNoAi(true);
            legacyHealth(original, 400D);
            restored.load(original.saveWithoutId(new CompoundTag()));
            restored.setUUID(UUID.randomUUID());
            h.assertTrue(restored.getMaxHealth() == 400F && restored.getHealth() == 100F,
                    "The native save/load carries the actual old 400-health, quarter-health dragon state");
            loadedJoin(h, restored);
            assertQuarterHealth(h, restored, 7500F);
            legacyHealth(restored, 600D);
            loadedJoin(h, restored);
            assertQuarterHealth(h, restored, 7500F);
            loadedJoin(h, restored);
            assertQuarterHealth(h, restored, 7500F);
        } finally { original.discard(); restored.discard(); }
        h.succeed();
    }

    private static void legacyHealth(Mob boss, double maximum) {
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximum);
        boss.setHealth((float) maximum / 4F);
        boss.getPersistentData().putBoolean("modern_ite:source_profile", true);
        boss.getPersistentData().putBoolean("modern_ite:boss", true);
    }

    private static void loadedJoin(GameTestHelper h, Mob boss) {
        var event = new EntityJoinLevelEvent(boss, h.getLevel(), true);
        NeoForge.EVENT_BUS.post(event);
        h.assertFalse(event.isCanceled(), "Loading an existing saved boss is admitted by the actual join event pipeline");
    }

    private static void assertQuarterHealth(GameTestHelper h, Mob boss, float maximum) {
        h.assertTrue(boss.getMaxHealth() == maximum && boss.getAttributeBaseValue(Attributes.MAX_HEALTH) == maximum,
                "The final join handler repairs the legacy maximum even when its old profile marker is present");
        h.assertTrue(Math.abs(boss.getHealth() - maximum / 4F) < .01F,
                "Reapplying the authoritative maximum preserves the injured 25-percent health ratio");
    }
}
