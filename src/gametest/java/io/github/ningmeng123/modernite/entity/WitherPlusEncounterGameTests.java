package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.content.wither.WitherEncounters;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional WitherPlus is exercised with its actual skull, brain and saved state. @author NingMeng123 */
@GameTestHolder("modern_ite_witherplus")
@PrefixGameTestTemplate(false)
public final class WitherPlusEncounterGameTests {
    private WitherPlusEncounterGameTests() {}

    @GameTest(template = "gear_empty")
    public static void specialSkullSummonsAnEnhancedWitherWithActiveSkills(GameTestHelper h) {
        WitherBoss wither = WitherEncounterGameTests.summon(h, specialSkull(h), false);
        try {
            WitherEncounterGameTests.assertProfile(h, wither, true);
            h.assertTrue(wither.getName().equals(Component.translatable("entity.modern_ite.enhanced_wither")),
                    "The actual enhanced summon announces its localized name");
            var renamed = Component.literal("Test Wither");
            wither.setCustomName(renamed);
            WitherBoss restored = EntityType.WITHER.create(h.getLevel());
            restored.load(wither.saveWithoutId(new CompoundTag()));
            h.assertTrue(restored.getName().equals(renamed) && WitherEncounters.isEnhanced(restored),
                    "A player-assigned name and the special identity both survive loading");
            h.assertTrue(WitherEncounterGameTests.brainActive(wither), "The real special-head summon retains WitherPlus skills");
            h.assertTrue(wither.saveWithoutId(new CompoundTag()).getBoolean("WpSpecialSummon"),
                    "WitherPlus itself stores this actual summon as special");
        } finally { wither.discard(); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void placedSpecialSkullRetainsIdentityAndSummonsInEitherOrientation(GameTestHelper h) {
        for (Direction face : new Direction[]{Direction.UP, Direction.SOUTH}) {
            WitherBoss wither = WitherEncounterGameTests.summon(h, specialSkull(h), 0, face);
            try {
                WitherEncounterGameTests.assertProfile(h, wither, true);
                h.assertTrue(WitherEncounterGameTests.brainActive(wither), "The first special skull activates skills when the last skull is ordinary");
            } finally { wither.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void finalWallMountedSpecialSkullIsRecognizedBeforeConsumption(GameTestHelper h) {
        WitherBoss wither = WitherEncounterGameTests.summon(h, specialSkull(h), 2, Direction.SOUTH);
        try {
            WitherEncounterGameTests.assertProfile(h, wither, true);
            h.assertTrue(WitherEncounterGameTests.brainActive(wither), "The last wall-mounted special skull activates skills");
        } finally { wither.discard(); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void underworldSupportsHorizontalAndUprightEnhancedSummonsAtDepth(GameTestHelper h) {
        var underworld = h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        h.assertTrue(underworld != null, "The actual MITE Underworld dimension is loaded");
        var center = new BlockPos(88, 5, 88);
        try (var calendar = new SpawnBalanceGameTests.Site(h)) {
            calendar.date(1, 1, false);
            h.assertFalse(SpawnPopulation.automaticAllowed(underworld, EntityType.WITHER), "First-day grace blocks automatic Withers in the Underworld");
            WitherBoss automatic = EntityType.WITHER.create(underworld);
            try {
                automatic.moveTo(center.getCenter());
                automatic.makeInvulnerable();
                h.assertFalse(underworld.addFreshEntity(automatic), "A charging Wither without a skull ritual is still denied");
            } finally { automatic.discard(); }
            for (int y : new int[]{5, -40}) {
                for (Direction face : new Direction[]{Direction.UP, Direction.SOUTH}) {
                    WitherBoss wither = WitherEncounterGameTests.summon(h, underworld, center.atY(y), specialSkull(h), 2, face);
                    try {
                        WitherEncounterGameTests.assertProfile(h, wither, true);
                        h.assertTrue(WitherEncounterGameTests.brainActive(wither), "The Underworld summon has active enhanced skills at Y=" + y);
                    } finally { wither.discard(); }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void nearbyUnrelatedSpecialSkullCannotUpgradeAnOrdinarySummon(GameTestHelper h) {
        WitherBoss wither = WitherEncounterGameTests.summon(h, specialSkull(h), true);
        try {
            WitherEncounterGameTests.assertProfile(h, wither, false);
            h.assertFalse(WitherEncounterGameTests.brainActive(wither),
                    "WitherPlus's nearby-head scan cannot override the three ordinary matched skulls");
            h.assertFalse(wither.saveWithoutId(new CompoundTag()).getBoolean("WpSpecialSummon"),
                    "A rejected nearby special-head marker cannot persist a false enhanced identity");
            WitherEncounterGameTests.setBrainSpecial(wither, true);
            h.assertFalse(WitherEncounterGameTests.brainActive(wither) || WitherEncounters.isEnhanced(wither),
                    "A later legacy setter cannot override the known ordinary summon identity");
        } finally { wither.discard(); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void nativeWitherPlusSavedFlagIsAuthoritativeDuringReload(GameTestHelper h) {
        specialSkull(h);
        for (boolean savedSpecial : new boolean[]{false, true}) {
            WitherBoss original = WitherEncounterGameTests.create(h, !savedSpecial);
            WitherBoss restored = EntityType.WITHER.create(h.getLevel());
            h.assertTrue(restored != null, "The actual Wither registry creates the reload instance");
            try {
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                saved.putBoolean("WpSpecialSummon", savedSpecial);
                restored.load(saved);
                restored.setUUID(UUID.randomUUID());
                h.assertTrue(WitherEncounters.isEnhanced(restored) == savedSpecial,
                        "The saved WitherPlus flag replaces a stale opposite persistent encounter marker");
                h.assertTrue(WitherEncounterGameTests.brainActive(restored) == savedSpecial,
                        "Skills follow the authoritative saved summon identity after the real load callback");
                h.assertTrue(h.getLevel().addFreshEntity(restored), "The restored Wither passes the actual join handlers");
                h.assertTrue(restored.getMaxHealth() == 1300F,
                        "Rejoining retains 1300 health for either authoritative saved summon identity");
                h.assertTrue(restored.saveWithoutId(new CompoundTag()).getBoolean("WpSpecialSummon") == savedSpecial,
                        "Saving again preserves the reconciled WitherPlus identity");
            } finally { original.discard(); restored.discard(); }
        }
        h.succeed();
    }

    private static ItemStack specialSkull(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("witherplus"), "The dedicated optional integration namespace requires WitherPlus");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("witherplus", "special_wither_skeleton_skull");
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The actual WitherPlus special skull is registered");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isPresent(),
                "The special-skull recipe survives WitherPlus's original format and loads through the compatibility correction");
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
