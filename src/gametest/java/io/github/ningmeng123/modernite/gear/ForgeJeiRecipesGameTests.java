package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.modernite.integration.jei.ForgeJeiRecipe;
import io.github.ningmeng123.modernite.integration.jei.ForgeJeiRecipes;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Recipe previews are checked against actual forge operations without loading JEI. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ForgeJeiRecipesGameTests {
    private record Transition(ResourceLocation item, int grade, boolean universal) {}

    private static final List<Map<String, Integer>> SOURCE_COSTS = List.of(
            Map.of("minecraft:gold_ingot", 1, "minecraft:iron_ingot", 1, "modern_ite:iron_enhance_stone", 1),
            Map.of("minecraft:gold_ingot", 1, "minecraft:iron_ingot", 2, "modern_ite:iron_enhance_stone", 1),
            Map.of("minecraft:gold_ingot", 2, "minecraft:iron_ingot", 2, "modern_ite:iron_enhance_stone", 1),
            Map.of("minecraft:gold_ingot", 2, "minecraft:iron_ingot", 2, "modern_ite:iron_enhance_stone", 2),
            Map.of("minecraft:gold_ingot", 1, "mite:mithril_ingot", 1, "minecraft:redstone", 1, "modern_ite:mithril_enhance_stone", 1),
            Map.of("minecraft:gold_ingot", 2, "mite:mithril_ingot", 1, "minecraft:redstone", 2, "modern_ite:mithril_enhance_stone", 1),
            Map.of("minecraft:gold_ingot", 2, "mite:mithril_ingot", 2, "minecraft:redstone_block", 1, "modern_ite:mithril_enhance_stone", 1),
            Map.of("minecraft:ghast_tear", 1, "mite:adamantium_ingot", 1, "mite:mithril_ingot", 2, "modern_ite:adamantium_enhance_stone", 1),
            Map.of("minecraft:ghast_tear", 2, "mite:adamantium_ingot", 1, "mite:mithril_ingot", 2, "modern_ite:adamantium_enhance_stone", 1),
            Map.of("minecraft:ghast_tear", 2, "mite:adamantium_ingot", 2, "mite:mithril_ingot", 2, "modern_ite:adamantium_enhance_stone", 1));

    private ForgeJeiRecipesGameTests() {}

    private static GearStationEntity station(GameTestHelper h, int tier) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        if (h.getLevel().getBlockEntity(pos) instanceof GearStationEntity previous) previous.clearContent();
        h.getLevel().setBlockAndUpdate(pos, ExtremeGear.FORGES.get(tier).get().defaultBlockState());
        return (GearStationEntity) h.getLevel().getBlockEntity(pos);
    }

    private static void prepare(GameTestHelper h, GearStationEntity station, ItemStack input,
            List<ItemStack> materials, ItemStack axe, ItemStack hammer) {
        // Reset an unfinished statistics probe through the same saved state used by chunk reloads.
        if (station.running()) {
            var saved = station.saveWithoutMetadata(h.getLevel().registryAccess());
            saved.putInt("status", 0); saved.putInt("progress", 0); saved.putInt("total", 0);
            station.loadWithComponents(saved, h.getLevel().registryAccess());
        }
        station.clearContent();
        h.assertTrue(materials.size() <= 5, "Every displayed cost fits the five actual material slots");
        for (int i = 0; i < materials.size(); i++) station.setItem(i, materials.get(i).copy());
        station.setItem(5, input.copy()); station.setItem(6, axe.copy()); station.setItem(7, hammer.copy());
    }

    private static void prepare(GameTestHelper h, GearStationEntity station, ForgeJeiRecipe page) {
        prepare(h, station, page.input(), page.materials(),
                new ItemStack(MiteTools.get("vibranium_axe")), new ItemStack(MiteTools.get("vibranium_war_hammer")));
    }

    private static Map<String, Integer> counts(List<ItemStack> materials) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : materials)
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        return counts;
    }

    private static Map<String, Integer> sourceCosts(int grade, boolean universal) {
        Map<String, Integer> result = new LinkedHashMap<>();
        SOURCE_COSTS.get(grade).forEach((id, count) -> result.put(universal && id.endsWith("_enhance_stone")
                ? "modern_ite:universal_enhance_stone" : id, count));
        return result;
    }

    private static ForgeJeiRecipe vibraniumPage(List<ForgeJeiRecipe> pages, int grade, boolean universal) {
        return pages.stream().filter(page -> page.itemId().equals(ResourceLocation.parse("mite:vibranium_sword"))
                && page.inputGrade() == grade && page.universal() == universal).findFirst().orElseThrow();
    }

    private static ItemStack completeSuccessfully(GameTestHelper h, GearStationEntity station) {
        station.start();
        h.assertTrue(station.running(), "The displayed operation starts on the actual server block entity");
        var saved = station.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.putBoolean("doomed", false);
        station.loadWithComponents(saved, h.getLevel().registryAccess());
        int total = station.data.get(2);
        for (int tick = 0; tick <= total && station.running(); tick++)
            GearStationEntity.serverTick(h.getLevel(), station.getBlockPos(), station.getBlockState(), station);
        h.assertTrue(!station.running() && station.data.get(3) == 2 && station.getItem(5).isEmpty(),
                "A completed successful operation moves its one equipment item to the output slot");
        return station.getItem(8);
    }

    @GameTest(template = "gear_empty")
    public static void categoryCatalystsAndAllTransitionsMatchActualForgeEligibility(GameTestHelper h) {
        var profiles = ForgeJeiRecipes.profiles();
        var pages = ForgeJeiRecipes.build();
        h.assertTrue(profiles.size() == 4 && !pages.isEmpty(), "Four nonempty forge categories are available without JEI");
        for (int tier = 0; tier < 4; tier++) {
            h.assertTrue(profiles.containsKey(tier) && profiles.get(tier).tier() == tier
                    && profiles.get(tier).id().equals(BuiltInRegistries.BLOCK.getKey(ExtremeGear.FORGES.get(tier).get())),
                    "Category IDs use the actual registered forge blocks");
            var catalysts = ForgeJeiRecipes.catalysts(tier);
            h.assertTrue(catalysts.size() == 4 - tier, "Minimum forge tier exposes exactly 4/3/2/1 usable catalysts");
            for (int actualTier = 0; actualTier < 4; actualTier++) {
                final int candidate = actualTier;
                h.assertTrue(catalysts.stream().anyMatch(stack -> stack.is(ExtremeGear.FORGES.get(candidate).get().asItem()))
                        == (actualTier >= tier), "Only the same or higher tier forge appears on the left");
            }
        }
        Set<ResourceLocation> ids = new HashSet<>();
        for (ForgeJeiRecipe page : pages) {
            String label = page.itemId() + " +" + page.inputGrade() + " universal=" + page.universal();
            h.assertTrue(ids.add(page.id()), "Every transition and stone variant has its own stable recipe ID: " + label);
            int expectedTier = GearMath.tableTier(GearProgression.material(page.input()));
            h.assertTrue(page.minimumTier() == expectedTier && ForgeJeiRecipes.minimumTier(page.input()) == expectedTier,
                    "Display category uses the server's material gate: " + label);
            h.assertTrue(page.outputGrade() == page.inputGrade() + 1, "Every page displays exactly one strengthening step: " + label);
            h.assertTrue(counts(page.materials()).equals(sourceCosts(page.inputGrade(), page.universal())),
                    "Each indexed page carries exact source material counts: " + label);
        }
        // Reuse the existing gear fixture position rather than touching neighboring test structures.
        for (int tier = 0; tier < 4; tier++) {
            GearStationEntity actual = station(h, tier);
            for (ForgeJeiRecipe page : pages) {
                String label = page.itemId() + " +" + page.inputGrade() + " universal=" + page.universal();
                int expectedTier = GearMath.tableTier(GearProgression.material(page.input()));
                prepare(h, actual, page);
                h.assertTrue(actual.canPlaceItem(5, page.input()), "Every displayed target fits the real equipment slot: " + label);
                h.assertTrue(actual.canStart() == (tier >= expectedTier)
                        && ForgeJeiRecipes.canForge(tier, page.input()) == actual.canStart(),
                        "Displayed catalysts agree with native canStart for every target/grade/stone variant: " + label);
                h.assertTrue(ForgeRecipes.usesUniversal(actual) == page.universal(), "Displayed stone variant matches native payment: " + label);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void everySupportedItemHasAllStepsAndUnsupportedOrMaxedTargetsHaveNone(GameTestHelper h) {
        List<ForgeJeiRecipe> pages = ForgeJeiRecipes.build();
        Set<Transition> actual = new HashSet<>();
        for (ForgeJeiRecipe page : pages) h.assertTrue(actual.add(new Transition(page.itemId(), page.inputGrade(), page.universal())),
                "A target/grade/stone variant is not duplicated in several categories");
        Set<Transition> expected = new HashSet<>();
        var top = station(h, 3);
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            boolean accepted = !stack.isEmpty() && ExtremeGear.socketable(stack)
                    && GearMath.maximumGrade(GearProgression.material(stack)) > 0;
            h.assertTrue(ForgeJeiRecipes.supports(stack) == accepted, "The display respects actual tool/armor profiles: " + BuiltInRegistries.ITEM.getKey(item));
            if (!accepted) continue;
            int maximum = GearMath.maximumGrade(GearProgression.material(stack));
            for (int grade = 0; grade < maximum; grade++) for (boolean universal : List.of(false, true))
                expected.add(new Transition(BuiltInRegistries.ITEM.getKey(item), grade, universal));
            stack.set(ExtremeGear.FORGE_GRADE.get(), maximum);
            prepare(h, top, stack, ForgeJeiRecipes.materials(Math.min(maximum, 9), false), new ItemStack(Items.IRON_AXE),
                    new ItemStack(MiteTools.get("copper_war_hammer")));
            h.assertTrue(!ForgeJeiRecipes.canForge(3, stack) && !top.canStart(),
                    "Already maximum-grade equipment has no further display or native transition");
        }
        h.assertTrue(actual.equals(expected), "The index contains all and only native supported equipment transitions, in both stone modes");
        for (ItemStack rejected : List.of(new ItemStack(Items.SHIELD), new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW),
                new ItemStack(Items.GOLDEN_SWORD), new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.DIRT), ItemStack.EMPTY)) {
            h.assertTrue(!top.canPlaceItem(5, rejected) && !ForgeJeiRecipes.supports(rejected)
                    && !ForgeJeiRecipes.canForge(3, rejected), "Shields, bows and unsupported materials are not advertised as forge targets");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void displayedMaterialCountsAndUniversalSubstitutionsPayTheNativeRecipe(GameTestHelper h) {
        var station = station(h, 3);
        for (int grade = 0; grade < 10; grade++) for (boolean universal : List.of(false, true)) {
            List<ItemStack> materials = ForgeJeiRecipes.materials(grade, universal);
            h.assertTrue(counts(materials).equals(sourceCosts(grade, universal)), "Displayed counts match source costs for old grade " + grade);
            ItemStack target = new ItemStack(MiteTools.get("vibranium_sword")); target.set(ExtremeGear.FORGE_GRADE.get(), grade);
            prepare(h, station, target, materials, new ItemStack(Items.IRON_AXE), new ItemStack(MiteTools.get("copper_war_hammer")));
            h.assertTrue(station.canStart() && ForgeRecipes.usesUniversal(station) == universal, "Exact displayed ingredients pay the native recipe");
            for (int slot = 0; slot < materials.size(); slot++) {
                ItemStack exact = station.getItem(slot).copy(); station.getItem(slot).shrink(1);
                h.assertTrue(!station.canStart(), "One fewer of each displayed ingredient must block the native recipe");
                station.setItem(slot, exact);
            }
            h.assertTrue(ForgeRecipes.consume(station, true), "Native allocation consumes the advertised recipe");
            for (int slot = 0; slot < 5; slot++) h.assertTrue(station.getItem(slot).isEmpty(), "Exact payments leave no material behind");
        }
        // The two-stone step also permits one ordinary stone plus one universal stone.
        ItemStack target = new ItemStack(MiteTools.get("vibranium_sword")); target.set(ExtremeGear.FORGE_GRADE.get(), 3);
        prepare(h, station, target, ForgeJeiRecipes.materials(3, false), new ItemStack(Items.IRON_AXE),
                new ItemStack(MiteTools.get("copper_war_hammer")));
        for (int slot = 0; slot < 5; slot++) if (station.getItem(slot).is(ExtremeGear.STONES.get(0).get())) station.getItem(slot).setCount(1);
        station.setItem(4, new ItemStack(ExtremeGear.STONES.get(3).get()));
        h.assertTrue(station.canStart() && ForgeRecipes.usesUniversal(station) && station.data.get(4) == 50,
                "A mixed stone payment uses the universal probability, even when only one stone is universal");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void toolChoicesAndDisplayedRangesMatchActualServerOperations(GameTestHelper h) {
        List<ItemStack> axes = ForgeJeiRecipes.tools(ToolFamily.AXE), hammers = ForgeJeiRecipes.tools(ToolFamily.WAR_HAMMER);
        List<ForgeJeiRecipe> pages = ForgeJeiRecipes.build();
        h.assertTrue(!axes.isEmpty() && !hammers.isEmpty(), "Both required tool slots expose usable choices");
        var station = station(h, 3);
        for (ItemStack axe : axes) h.assertTrue(axe.isDamageableItem() && R196EnchantmentCatalog.family(axe) == ToolFamily.AXE
                && station.canPlaceItem(6, axe), "Every displayed axe is accepted by the native slot");
        for (ItemStack hammer : hammers) h.assertTrue(hammer.isDamageableItem() && R196EnchantmentCatalog.family(hammer) == ToolFamily.WAR_HAMMER
                && station.canPlaceItem(7, hammer), "Every displayed war hammer is accepted by the native slot");
        h.assertTrue(axes.stream().anyMatch(stack -> stack.is(MiteTools.get("flint_axe")))
                && hammers.stream().anyMatch(stack -> stack.is(MiteTools.get("copper_war_hammer"))),
                "Low-tier source tools remain valid choices; forging has no invented iron minimum");
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;
            ToolFamily family = R196EnchantmentCatalog.family(stack);
            if (family == ToolFamily.AXE || family == ToolFamily.WAR_HAMMER)
                h.assertTrue((family == ToolFamily.AXE ? axes : hammers).stream().anyMatch(choice -> choice.is(item)),
                        "Every registered tool accepted by the actual slot is discoverable");
        }
        for (int grade = 0; grade < 10; grade++) for (boolean universal : List.of(false, true)) {
            int minimumTicks = Integer.MAX_VALUE, maximumTicks = 0, minimumFailure = Integer.MAX_VALUE, maximumFailure = 0;
            ItemStack target = new ItemStack(MiteTools.get("vibranium_sword")); target.set(ExtremeGear.FORGE_GRADE.get(), grade);
            for (ItemStack axe : axes) for (ItemStack hammer : hammers) {
                prepare(h, station, target, ForgeJeiRecipes.materials(grade, universal), axe, hammer);
                h.assertTrue(station.canStart(), "Every displayed tool pair can start the actual operation");
                int failure = station.data.get(4); station.start();
                h.assertTrue(station.running(), "The native start path accepts the displayed tool pair");
                int ticks = station.data.get(2);
                minimumTicks = Math.min(minimumTicks, ticks); maximumTicks = Math.max(maximumTicks, ticks);
                minimumFailure = Math.min(minimumFailure, failure); maximumFailure = Math.max(maximumFailure, failure);
            }
            var displayed = ForgeJeiRecipes.stats(grade, universal);
            h.assertTrue(displayed.minimumTicks() == minimumTicks && displayed.maximumTicks() == maximumTicks
                    && displayed.minimumFailure() == minimumFailure && displayed.maximumFailure() == maximumFailure,
                    "Displayed time and probability ranges exactly cover native operations at old grade " + grade);
            for (ForgeJeiRecipe page : pages) if (page.inputGrade() == grade && page.universal() == universal)
                h.assertTrue(page.stats().equals(displayed), "Every indexed page stores the verified range for its transition and stone mode");
        }
        prepare(h, station, vibraniumPage(pages, 0, false));
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void independentPreviewOutputsMatchActualCompletedTransitionsAndQuality(GameTestHelper h) {
        List<ForgeJeiRecipe> pages = ForgeJeiRecipes.build();
        var station = station(h, 3);
        Quality[] rewards = {null, Quality.FINE, Quality.FINE, Quality.EXCELLENT, Quality.EXCELLENT,
                Quality.SUPERB, Quality.SUPERB, Quality.MASTERWORK, Quality.MASTERWORK, Quality.LEGENDARY};
        for (int grade = 0; grade < 10; grade++) for (boolean universal : List.of(false, true)) {
            ForgeJeiRecipe page = vibraniumPage(pages, grade, universal);
            ItemStack inputBefore = page.input().copy(), outputBefore = page.output().copy();
            h.assertTrue(page.input() != page.output(), "Input and output are independent item stacks");
            ItemStack constructorInput = page.input().copy(), constructorOutput = page.output().copy();
            List<ItemStack> constructorMaterials = page.materials().stream().map(ItemStack::copy).toList();
            var snapshot = new ForgeJeiRecipe(page.minimumTier(), page.itemId(), constructorInput, constructorOutput,
                    constructorMaterials, page.universal(), page.stats());
            constructorInput.set(DataComponents.CUSTOM_NAME, Component.literal("Changed source"));
            constructorOutput.set(ExtremeGear.FORGE_GRADE.get(), 0); constructorMaterials.getFirst().shrink(1);
            h.assertTrue(ItemStack.matches(snapshot.input(), inputBefore) && ItemStack.matches(snapshot.output(), outputBefore)
                    && counts(snapshot.materials()).equals(sourceCosts(grade, universal)),
                    "Recipe snapshots own independent copies of constructor input, output and material stacks");
            ItemStack detached = page.output().copy(); detached.setDamageValue(17);
            detached.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, 6));
            h.assertTrue(ItemStack.matches(page.input(), inputBefore) && ItemStack.matches(page.output(), outputBefore),
                    "Mutating an output copy cannot modify the indexed input or output components");
            Quality expected = EquipmentDurability.quality(inputBefore);
            if (rewards[grade] != null && rewards[grade].ordinal() > expected.ordinal()) expected = rewards[grade];
            h.assertTrue(EquipmentDurability.quality(outputBefore) == expected, "Quality rewards use the old grade, including no guarantee for zero to one");
            prepare(h, station, page);
            ItemStack actual = completeSuccessfully(h, station);
            h.assertTrue(ItemStack.matches(actual, outputBefore), "The preview matches an actual completed native upgrade at old grade " + grade);
            h.assertTrue(ItemStack.matches(page.input(), inputBefore), "Completing a copied recipe cannot mutate its indexed input");
            for (int slot = 0; slot < 5; slot++) h.assertTrue(station.getItem(slot).isEmpty(), "Every successful displayed recipe consumes its exact material counts");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void completingPreviewInputCopiesPreservesExistingHigherQualityAndComponents(GameTestHelper h) {
        ForgeJeiRecipe page = vibraniumPage(ForgeJeiRecipes.build(), 1, false);
        ItemStack target = page.input().copy(), pristinePreview = page.output().copy();
        EquipmentDurability.setQuality(target, Quality.LEGENDARY); target.setDamageValue(31);
        target.set(DataComponents.CUSTOM_NAME, Component.literal("Independent forging input"));
        target.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, 6).with(7, 12));
        target.set(ExtremeGear.GRINDINGS.get(), 3);
        target.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 8, 713,
                Map.of(ResourceLocation.withDefaultNamespace("unbreaking"), 2), 1));
        ItemStack expected = target.copy(); expected.set(ExtremeGear.FORGE_GRADE.get(), 2);
        var station = station(h, 3);
        prepare(h, station, target, page.materials(), new ItemStack(MiteTools.get("vibranium_axe")),
                new ItemStack(MiteTools.get("vibranium_war_hammer")));
        ItemStack result = completeSuccessfully(h, station);
        h.assertTrue(ItemStack.matches(result, expected), "Actual strengthening preserves wear, higher quality, name, gems, grinding and growth components");
        h.assertTrue(ExtremeGear.grade(target) == 1 && ItemStack.matches(page.output(), pristinePreview)
                && !page.input().has(DataComponents.CUSTOM_NAME), "A real customized operation cannot corrupt either cached preview template");
        h.succeed();
    }
}
