package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.integration.jei.FurnaceJeiRecipe;
import io.github.ningmeng123.mite.integration.jei.FurnaceJeiRecipes;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The extension uses the base viewer's headless index and real thermal processing plan. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class FurnaceJeiGameTests {
    private static final String VIBRANIUM = "modern_ite:vibranium_furnace";
    private static final Set<String> ALL_DEVICES = Set.of("mite:clay_oven", "mite:large_clay_oven",
            "mite:sandstone_furnace", "minecraft:furnace", "mite:obsidian_furnace", "mite:netherrack_furnace",
            "minecraft:smoker", "minecraft:blast_furnace", VIBRANIUM);
    private FurnaceJeiGameTests() {}

    @GameTest(template = "gear_empty")
    public static void vibraniumExtendsTheImmutableBaseDeviceIndexExactlyOnce(GameTestHelper h) {
        var profiles = R196FurnaceRules.profiles();
        h.assertTrue(profiles.keySet().equals(ALL_DEVICES), "The thermal index retains all eight base devices and adds exactly one vibranium furnace");
        var expected = new R196FurnaceRules.Profile(5, true, 200);
        h.assertTrue(expected.equals(profiles.get(VIBRANIUM)), "The viewer profile has heat five, accepts large items and takes 200 ticks");
        h.assertTrue(expected.equals(FurnaceThermalController.profile(ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState())),
                "The viewer profile agrees with the real block's active thermal policy");
        boolean immutable = false;
        try { profiles.put("modern_ite:invalid_test_device", expected); }
        catch (UnsupportedOperationException correct) { immutable = true; }
        h.assertTrue(immutable && R196FurnaceRules.profiles().keySet().equals(ALL_DEVICES),
                "A caller cannot mutate the shared profile map or introduce duplicate devices on subsequent reads");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void allVibraniumInputAndFuelAlternativesAreRealExecutableBatches(GameTestHelper h) {
        var pages = FurnaceJeiRecipes.build(h.getLevel());
        h.assertTrue(pages.stream().map(FurnaceJeiRecipe::furnaceId).collect(Collectors.toSet()).equals(ALL_DEVICES),
                "The unmodified base recipe builder supplies the new device without losing any original furnace category");
        var profile = FurnaceThermalController.profile(ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState());
        var vibranium = pages.stream().filter(page -> page.furnaceId().equals(VIBRANIUM)).toList();
        h.assertTrue(!vibranium.isEmpty(), "The new device has actual recipe pages rather than only a catalyst");
        for (var page : vibranium) {
            h.assertTrue(page.cycleTicks() == 200 && page.sourceRecipe() != null && !page.inputs().isEmpty()
                    && !page.fuels().isEmpty() && !page.output().isEmpty(), "Every page has a real source, input, fuel, result and native cycle");
            for (ItemStack input : page.inputs()) {
                h.assertTrue(FurnaceThermalController.canInput(h.getLevel(), RecipeType.SMELTING, profile, input),
                        "Every displayed input can enter the real vibranium furnace");
                var recipe = recipe(h, input);
                for (ItemStack fuel : page.fuels()) {
                    var fuelRule = FurnaceThermalController.fuel(fuel);
                    h.assertTrue(FurnaceThermalController.canFuel(profile, fuel) && fuelRule.ticks() > 0
                            && fuelRule.heat() >= page.heat() && fuelRule.heat() <= 5,
                            "Every displayed fuel obeys the actual size and heat gates");
                    h.assertTrue(fuel.getCount() == (page.cycleTicks() + fuelRule.ticks() - 1) / fuelRule.ticks(),
                            "Displayed fuel counts sustain a complete uninterrupted cycle");
                    var batch = FurnaceThermalController.preview(h.getLevel(), RecipeType.SMELTING, profile, input,
                            ItemStack.EMPTY, 64, fuelRule.heat(), recipe);
                    h.assertTrue(batch != null && batch.consumed() == input.getCount()
                            && ItemStack.isSameItemSameComponents(batch.result(), page.output())
                            && batch.result().getCount() == page.output().getCount(),
                            "Every input/fuel combination produces exactly the displayed guaranteed batch");
                }
            }
        }
        h.assertTrue(vibranium.stream().anyMatch(page -> page.inputs().stream().anyMatch(input -> input.is(Items.RAW_IRON))),
                "The extension index retains ordinary base smelting instead of showing only extension-exclusive recipes");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void heatFiveAndTemperatureDependentBatchesMatchTheRealVibraniumFurnace(GameTestHelper h) {
        var all = FurnaceJeiRecipes.build(h.getLevel());
        var pages = all.stream().filter(page -> page.furnaceId().equals(VIBRANIUM)).toList();
        Item vibraniumBlock = item("mite:vibranium_block"), shard = item("modern_ite:gem_shard_6");
        var hottest = matching(pages, vibraniumBlock, shard);
        h.assertTrue(!hottest.isEmpty() && hottest.stream().allMatch(page -> page.heat() == 5
                && page.inputs().stream().allMatch(input -> input.getCount() == 1)
                && page.output().getCount() == 1 && page.fuels().stream().allMatch(fuel -> fuel.is(ExtremeContent.BLAZE_COAL.get()))),
                "The heat-five block conversion uses blaze coal and previews one guaranteed shard, without making the random bonus certain");
        h.assertTrue(all.stream().filter(page -> !page.furnaceId().equals(VIBRANIUM))
                .noneMatch(page -> page.inputs().stream().anyMatch(input -> input.is(vibraniumBlock))),
                "No lower-capacity furnace is advertised for the heat-five input");
        var blaze = FurnaceThermalController.fuel(new ItemStack(ExtremeContent.BLAZE_COAL.get()));
        h.assertTrue(blaze.heat() == 5 && blaze.ticks() == 6800, "The actual source blaze coal supplies heat five for 6800 ticks");
        for (Item sand : List.of(Items.SAND, Items.RED_SAND)) {
            Item sandstone = sand == Items.SAND ? Items.SANDSTONE : Items.RED_SANDSTONE;
            var low = matching(pages, sand, sandstone);
            var high = matching(pages, sand, Items.GLASS);
            h.assertTrue(!low.isEmpty() && low.stream().allMatch(page -> page.heat() == 1
                    && page.inputs().stream().allMatch(input -> input.getCount() == 4)
                    && page.fuels().stream().allMatch(fuel -> FurnaceThermalController.fuel(fuel).heat() == 1)),
                    "Four sand produce matching sandstone only at heat one");
            h.assertTrue(!high.isEmpty() && high.stream().allMatch(page -> page.heat() == 2
                    && page.inputs().stream().allMatch(input -> input.getCount() == 4)
                    && page.fuels().stream().allMatch(fuel -> FurnaceThermalController.fuel(fuel).heat() >= 2)),
                    "The separate four-sand glass page starts at heat two and never includes a cold fuel");
        }
        var clay = matching(pages, Items.CLAY_BALL, Items.BRICK);
        h.assertTrue(clay.stream().flatMap(page -> page.inputs().stream()).map(ItemStack::getCount).collect(Collectors.toSet())
                .equals(Set.of(1, 2, 3, 4)), "All one-to-four clay batch sizes survive the shared index");
        h.assertTrue(clay.stream().allMatch(page -> page.inputs().stream().allMatch(input -> input.getCount() == page.output().getCount())),
                "Each clay page shows its exact consumed count and guaranteed brick count");

        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlockAndUpdate(pos, ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState());
        var furnace = (VibraniumFurnaceEntity)h.getLevel().getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(vibraniumBlock));
        furnace.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get()));
        for (int tick = 0; tick < 199; tick++)
            AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        h.assertTrue(furnace.getItem(2).isEmpty() && furnace.getItem(0).getCount() == 1,
                "The actual heat-five conversion does not finish before the displayed 200-tick cycle");
        AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).is(shard)
                && furnace.getItem(2).getCount() >= 1 && furnace.getItem(2).getCount() <= 2,
                "The real 200th tick produces the displayed shard, with only the permitted optional one-item bonus");
        h.succeed();
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    private static List<FurnaceJeiRecipe> matching(List<FurnaceJeiRecipe> pages, Item input, Item output) {
        return pages.stream().filter(page -> page.output().is(output)
                && page.inputs().stream().anyMatch(stack -> stack.is(input))).toList();
    }
    private static RecipeHolder<? extends AbstractCookingRecipe> recipe(GameTestHelper h, ItemStack input) {
        return h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), h.getLevel()).orElse(null);
    }
}
