package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.content.wither.WitherEncounters;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
            h.assertTrue(WitherEncounterGameTests.brainActive(wither), "The real special-head summon retains WitherPlus skills");
            h.assertTrue(wither.saveWithoutId(new CompoundTag()).getBoolean("WpSpecialSummon"),
                    "WitherPlus itself stores this actual summon as special");
        } finally { wither.discard(); }
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
