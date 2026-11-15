package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.survival.DualFoodData;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native mining and durability entry points combine socket levels with their actual source effects. @author NingMeng123 */
@GameTestHolder("modern_ite_gem_effects")
@PrefixGameTestTemplate(false)
public final class GemToolEffectsGameTests {
    private GemToolEffectsGameTests() {}

    private static ItemStack pick(GameTestHelper helper, ResourceKey<Enchantment> enchantment,
            int ordinary, int growth, GemType gem, int rank) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        if (ordinary > 0) stack.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(enchantment), ordinary);
        if (growth > 0) stack.set(GearProgression.PROGRESS.get(),
                new GearProgress(UUID.randomUUID(), growth, 0L, Map.of(enchantment.location(), growth), 1));
        if (rank > 0) stack.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, gem.ordinal() * 6 + rank));
        return stack;
    }

    private static Player miner(GameTestHelper helper, ItemStack tool) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(new BlockPos(2, 15, 2)).getCenter());
        player.setNoGravity(true);
        player.setItemSlot(EquipmentSlot.MAINHAND, tool);
        player.tick();
        player.setOnGround(true);
        player.experienceLevel = 0;
        player.getFoodData().setFoodLevel(6);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
        return player;
    }

    private static void close(GameTestHelper helper, double actual, double expected, String reason) {
        helper.assertTrue(Math.abs(actual - expected) < .0001D,
                reason + " expected=" + expected + " actual=" + actual);
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void ordinaryEfficiencyAndGemRanksAreSquaredTogetherThroughNativeMining(GameTestHelper helper) {
        ItemStack tool = pick(helper, Enchantments.EFFICIENCY, 2, 0, GemType.EFFICIENCY, 2);
        Player player = miner(helper, tool);
        var stone = Blocks.STONE.defaultBlockState();
        try {
            close(helper, player.getDigSpeed(stone, player.blockPosition()), tool.getDestroySpeed(stone) + 17D,
                    "EfficiencyII plus gemII contributes four squared plus one");
            helper.assertValueEqual(tool.get(DataComponents.ENCHANTMENTS).getLevel(helper.getLevel().registryAccess()
                            .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.EFFICIENCY)), 2,
                    "Combined mining never rewrites the ordinary Efficiency component");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void growthEfficiencyKeepsItsIndependentSquaredContribution(GameTestHelper helper) {
        ItemStack tool = pick(helper, Enchantments.EFFICIENCY, 3, 2, GemType.EFFICIENCY, 2);
        Player player = miner(helper, tool);
        var stone = Blocks.STONE.defaultBlockState();
        try {
            close(helper, player.getDigSpeed(stone, player.blockPosition()), tool.getDestroySpeed(stone) + 31D,
                    "OrdinaryIII plus gemII contributes26 while independent growthII contributes5");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void gemOnlyEfficiencyRequiresAnEffectiveToolAndUsesTheHighestSocketRank(GameTestHelper helper) {
        ItemStack tool = pick(helper, Enchantments.EFFICIENCY, 0, 0, GemType.EFFICIENCY, 2);
        tool.set(ExtremeGear.SOCKETS.get(), tool.get(ExtremeGear.SOCKETS.get())
                .with(1, GemType.EFFICIENCY.ordinal() * 6 + 1));
        Player player = miner(helper, tool);
        var stone = Blocks.STONE.defaultBlockState();
        var dirt = Blocks.DIRT.defaultBlockState();
        try {
            close(helper, player.getDigSpeed(stone, player.blockPosition()), tool.getDestroySpeed(stone) + 5D,
                    "Gem-only rankII contributes five and the lower duplicate socket adds no extra ranks");
            close(helper, player.getDigSpeed(dirt, player.blockPosition()), 1D,
                    "An ineffective pickaxe receives no efficiency-gem mining addition");
            tool.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY);
            close(helper, player.getDigSpeed(stone, player.blockPosition()), tool.getDestroySpeed(stone),
                    "Removing gems restores the native tool speed immediately");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void gemEfficiencyRunsBeforeHasteAirHungerAndLevelScaling(GameTestHelper helper) {
        ItemStack tool = pick(helper, Enchantments.EFFICIENCY, 2, 0, GemType.EFFICIENCY, 2);
        Player player = miner(helper, tool);
        var stone = Blocks.STONE.defaultBlockState();
        try {
            player.experienceLevel = 50;
            player.setOnGround(false);
            player.getFoodData().setFoodLevel(0);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200));
            float expected = tool.getDestroySpeed(stone) + 17F;
            expected *= 1.2F;
            expected /= 5F;
            expected /= 5F;
            expected *= 2F;
            close(helper, player.getDigSpeed(stone, player.blockPosition()), expected,
                    "All gem strength receives each native environment and source survival multiplier once");
        } finally { player.discard(); }
        helper.succeed();
    }

    private static void assertDurabilityRolls(GameTestHelper helper, ItemStack tool, float chance, long seed) {
        RandomSource expected = RandomSource.create(seed);
        int remaining = 1000;
        for (int point = 0; point < 1000; point++) if (expected.nextFloat() < chance) remaining--;
        helper.getLevel().random.setSeed(seed);
        helper.assertValueEqual(EnchantmentHelper.processDurabilityChange(helper.getLevel(), tool, 1000), remaining,
                "The actual enchantment entry point rolls the complete combined probability once per point");
        close(helper, helper.getLevel().random.nextFloat(), expected.nextFloat(),
                "Exactly one thousand shared RNG draws are consumed");
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void gemOnlyDurabilityUsesThreePointSevenFivePercentPerRank(GameTestHelper helper) {
        ItemStack tool = pick(helper, Enchantments.UNBREAKING, 0, 0, GemType.DURABILITY, 6);
        assertDurabilityRolls(helper, tool, .225F, 182341L);
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void durabilityGemsShareOneRollWithOrdinaryAndGrowthUnbreaking(GameTestHelper helper) {
        ItemStack tool = pick(helper, Enchantments.UNBREAKING, 2, 1, GemType.DURABILITY, 4);
        assertDurabilityRolls(helper, tool, .6F, 73494L);
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void removingDurabilityGemsPreservesTheExistingOrdinaryAndGrowthBehavior(GameTestHelper helper) {
        ItemStack ordinary = pick(helper, Enchantments.UNBREAKING, 2, 0, GemType.DURABILITY, 0);
        assertDurabilityRolls(helper, ordinary, .3F, 81354L);
        ItemStack growth = pick(helper, Enchantments.UNBREAKING, 2, 1, GemType.DURABILITY, 0);
        assertDurabilityRolls(helper, growth, .45F, 81354L);
        ItemStack plain = new ItemStack(Items.IRON_PICKAXE);
        RandomSource expected = RandomSource.create(4352L);
        helper.getLevel().random.setSeed(4352L);
        helper.assertValueEqual(EnchantmentHelper.processDurabilityChange(helper.getLevel(), plain, 100), 100,
                "Plain equipment retains every requested durability point");
        close(helper, helper.getLevel().random.nextFloat(), expected.nextFloat(),
                "Plain equipment consumes no extra random draws");
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void directItemStackWearAlsoUsesTheGemOnlySharedRoll(GameTestHelper helper) {
        ItemStack tool = pick(helper, Enchantments.UNBREAKING, 0, 0, GemType.DURABILITY, 3);
        Player player = miner(helper, tool);
        RandomSource expected = RandomSource.create(943245L);
        int remaining = 100;
        for (int point = 0; point < 100; point++) if (expected.nextFloat() < .1125F) remaining--;
        try {
            helper.getLevel().random.setSeed(943245L);
            tool.hurtAndBreak(100, player, EquipmentSlot.MAINHAND);
            helper.assertValueEqual(tool.getDamageValue(), remaining,
                    "Direct native wear reaches gem protection without EquipmentDurability.hurt");
            close(helper, helper.getLevel().random.nextFloat(), expected.nextFloat(),
                    "The direct native transaction also uses one roll per requested point");
        } finally { player.discard(); }
        helper.succeed();
    }
}
