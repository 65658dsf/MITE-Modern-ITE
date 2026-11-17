package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.integration.jei.ForgeJeiRecipes;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Netherite manufacturing and high-grade forge payments use actual registered entry points. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class NetheriteForgingGameTests {
    private NetheriteForgingGameTests() {}

    @GameTest(template = "gear_empty")
    public static void vibraniumForgingTableRequiresTwoNetheriteIngotsAndTimedManufacturing(GameTestHelper h) {
        var ingredients = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 3; slot++) ingredients.add(new ItemStack(MiteMaterials.VIBRANIUM_INGOT.get()));
        ingredients.add(new ItemStack(Items.NETHERITE_INGOT));
        ingredients.add(new ItemStack(ExtremeGear.FORGES.get(2).get()));
        ingredients.add(new ItemStack(Items.NETHERITE_INGOT));
        var manager = h.getLevel().getRecipeManager();
        var authored = manager.byKey(ExtremeContent.id("vibranium_forging_table")).orElseThrow();
        var recipe = (CraftingRecipe)authored.value();
        CraftingInput input = CraftingInput.of(3, 2, ingredients);
        h.assertTrue(recipe.matches(input, h.getLevel()), "The three original ingots and table require two flanking netherite ingots");
        var selected = manager.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        ItemStack output = selected.value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(output.is(ExtremeGear.FORGES.get(3).get().asItem()) && output.getCount() == 1,
                "The normal recipe manager crafts one vibranium forging table");
        var rule = CraftingQuality.rule(selected, output, input);
        h.assertTrue(rule != null && rule.difficulty() == 160800F,
                "Timed manufacturing includes both new netherite ingots in the complete material sum");
        for (int slot : new int[]{3, 5}) {
            ingredients.set(slot, ItemStack.EMPTY);
            h.assertTrue(!recipe.matches(CraftingInput.of(3, 2, ingredients), h.getLevel()),
                    "Each side independently requires its netherite ingot");
            ingredients.set(slot, new ItemStack(Items.NETHERITE_INGOT));
        }
        ingredients.set(3, ItemStack.EMPTY);
        ingredients.set(5, ItemStack.EMPTY);
        h.assertTrue(!recipe.matches(CraftingInput.of(3, 2, ingredients), h.getLevel()),
                "The previous four-component upgrade cannot manufacture the new table");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void highGradeScrapsAreAtomicAndPaidOnSuccessAndFailureAfterReload(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, ExtremeGear.FORGES.get(3).get().defaultBlockState());
        var station = (GearStationEntity)h.getLevel().getBlockEntity(pos);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(pos.getCenter());
        var menu = new GearStationMenu(87, player.getInventory(), station, station.data, false);
        for (int grade = 7; grade <= 9; grade++) for (boolean universal : new boolean[]{false, true})
            for (boolean success : new boolean[]{false, true}) {
                station.clearContent();
                var materials = ForgeJeiRecipes.materials(grade, universal);
                h.assertValueEqual(materials.size(), 5, "Each high-grade recipe fits the five physical material slots");
                for (int slot = 0; slot < materials.size(); slot++)
                    station.setItem(slot, materials.get(slot).copyWithCount(materials.get(slot).getCount() + 1));
                ItemStack target = new ItemStack(MiteTools.get("vibranium_sword"));
                target.set(ExtremeGear.FORGE_GRADE.get(), grade);
                target.setDamageValue(100);
                int remaining = target.getMaxDamage() - target.getDamageValue();
                station.setItem(5, target);
                station.setItem(6, new ItemStack(MiteTools.get("vibranium_axe")));
                station.setItem(7, new ItemStack(MiteTools.get("vibranium_war_hammer")));
                h.assertTrue(station.getItem(4).is(Items.NETHERITE_SCRAP)
                        && materials.get(4).getCount() == grade - 6, "The three new scrap costs are one, two and three");
                h.assertTrue(station.canStart() && ForgeRecipes.usesUniversal(station) == universal,
                        "Both displayed stone modes retain their correct native eligibility");
                ItemStack scraps = station.getItem(4).copy();
                station.getItem(4).setCount(grade - 7);
                var before = new ArrayList<ItemStack>();
                for (int slot = 0; slot < 5; slot++) before.add(station.getItem(slot).copy());
                h.assertTrue(!station.canStart() && !ForgeRecipes.consume(station, true),
                        "A missing scrap blocks even a universal-stone payment");
                for (int slot = 0; slot < 5; slot++) h.assertTrue(ItemStack.matches(before.get(slot), station.getItem(slot)),
                        "A failed allocation leaves every supplied material untouched");
                station.setItem(4, scraps);
                h.assertTrue(menu.clickMenuButton(player, 0) && station.running(),
                        "The normal server menu button starts the fully paid high-grade operation");
                var saved = station.saveWithoutMetadata(h.getLevel().registryAccess());
                saved.putBoolean("doomed", !success);
                saved.putInt("fail_at", 8);
                station.loadWithComponents(saved, h.getLevel().registryAccess());
                int total = station.data.get(2);
                for (int tick = 0; tick <= total && station.running(); tick++)
                    GearStationEntity.serverTick(h.getLevel(), pos, station.getBlockState(), station);
                h.assertTrue(!station.running() && station.data.get(3) == (success ? 2 : 3),
                        "Reload preserves the chosen terminal outcome through the real server tick");
                for (int slot = 0; slot < 5; slot++) h.assertValueEqual(station.getItem(slot).getCount(), 1,
                        "Success and failure each charge one complete recipe and preserve the surplus unit");
                h.assertValueEqual(station.getItem(6).getDamageValue(), GearMath.axeCost(grade) / (success ? 1 : 2),
                        "The high-grade payment retains existing axe wear");
                h.assertValueEqual(station.getItem(7).getDamageValue(), GearMath.hammerCost(grade) / (success ? 1 : 2),
                        "The high-grade payment retains existing hammer wear");
                if (success) h.assertTrue(station.getItem(5).isEmpty() && ExtremeGear.grade(station.getItem(8)) == grade + 1,
                        "Successful high-grade forging delivers its one next-grade equipment item");
                else h.assertTrue(station.getItem(8).isEmpty() && ExtremeGear.grade(station.getItem(5)) == grade
                        && station.getItem(5).getMaxDamage() - station.getItem(5).getDamageValue() == Math.round(remaining * .9F),
                        "High-grade failure retains the grade and charges the existing ten-percent remaining durability");
            }
        h.succeed();
    }
}
