package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.integration.jei.EnchantingJeiRecipe;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Check the loaded food recipe and the point quote shared by both tables and JEI. @author NingMeng123 */
@GameTestHolder("modern_ite_crafting")
@PrefixGameTestTemplate(false)
public final class GoldenAppleGameTests {
    @GameTest(template = "gear_empty")
    public static void goldenAppleUsesIngotsOnBothSides(GameTestHelper h) {
        var level = h.getLevel();
        var manager = level.getRecipeManager();
        var ingredients = new ArrayList<>(Collections.nCopies(9, new ItemStack(Items.GOLD_NUGGET)));
        ingredients.set(4, new ItemStack(Items.APPLE));
        h.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, ingredients), level).isEmpty(),
                "The old eight-nugget recipe is unavailable");
        ingredients.set(3, new ItemStack(Items.GOLD_INGOT));
        ingredients.set(5, new ItemStack(Items.GOLD_INGOT));
        var input = CraftingInput.of(3, 3, ingredients);
        var selected = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        var output = selected.value().assemble(input, level.registryAccess());
        h.assertTrue(output.is(Items.GOLDEN_APPLE) && output.getCount() == 1, "The new layout makes one golden apple");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void goldenAppleCostsFourHundredPointsAtBothTables(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var level = h.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ApplePrice"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(pos.getCenter());
        h.assertValueEqual(EnchantingJeiRecipe.goldenApple().experienceCost(), 400, "JEI quotes the apple conversion");
        for (var block : List.of(Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get())) {
            level.setBlock(pos, block.defaultBlockState(), 2);
            var menu = new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(level, pos));
            for (int slot = 0; slot < 3; slot++) {
                menu.getSlot(0).set(new ItemStack(Items.GOLDEN_APPLE));
                h.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(slot), 400, "The menu quotes 400 points");
                PlayerExperience.setTotal(player, 399);
                h.assertFalse(menu.clickMenuButton(player, slot), "399 points cannot buy the offer");
                h.assertTrue(menu.getSlot(0).getItem().is(Items.GOLDEN_APPLE) && player.totalExperience == 399, "Failed payment preserves the apple and experience");
                PlayerExperience.setTotal(player, 400);
                h.assertTrue(menu.clickMenuButton(player, slot), "400 points buy the offer without lapis");
                h.assertTrue(menu.getSlot(0).getItem().is(Items.ENCHANTED_GOLDEN_APPLE) && player.totalExperience == 0, "One conversion costs 400 points");
            }
        }
        h.succeed();
    }
}
