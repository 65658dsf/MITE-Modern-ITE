package io.github.ningmeng123.modernite.survival;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.modernite.item.WholeFlint;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("modern_ite_starting")
@PrefixGameTestTemplate(false)
public final class StartingEquipmentGameTests {
    private StartingEquipmentGameTests() {}

    @GameTest(template = "gear_empty")
    public static void chipEntitiesBecomeWholeFlintWithoutMultiplyingTheirCount(GameTestHelper helper) {
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        var drop = new ItemEntity(helper.getLevel(), position.getX(), position.getY(), position.getZ(),
                new ItemStack(MiteItems.FLINT_CHIP.get(), 7));
        helper.getLevel().addFreshEntity(drop);
        helper.assertTrue(drop.getItem().is(Items.FLINT) && drop.getItem().getCount() == 7,
                "Real entity insertion replaces each chip with exactly one whole flint");
        helper.assertTrue(WholeFlint.replace(new ItemStack(Items.FLINT, 3)).getCount() == 3,
                "Already whole flint retains its amount");
        drop.discard();
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void wholeFlintRecipesCannotLoopThroughChipSplitting(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        helper.assertTrue(manager.byKey(ResourceLocation.parse("mite:flint_chips_from_flint")).isEmpty()
                        && manager.byKey(ResourceLocation.parse("mite:flint_from_chips")).isEmpty(),
                "Both obsolete conversions are absent from the real recipe manager");
        for (String id : new String[]{"minecraft:arrow", "mite:fishing_rod_flint_hook"}) {
            var recipe = manager.byKey(ResourceLocation.parse(id)).orElseThrow().value();
            helper.assertTrue(recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(Items.FLINT))),
                    "The real " + id + " recipe accepts whole flint");
            helper.assertTrue(recipe.getIngredients().stream().noneMatch(ingredient -> ingredient.test(new ItemStack(MiteItems.FLINT_CHIP.get()))),
                    "The real " + id + " recipe no longer asks for chips");
        }
        var dismantle = manager.byKey(ResourceLocation.parse("mite:flint_arrow_dismantle")).orElseThrow().value();
        helper.assertTrue(dismantle.getResultItem(helper.getLevel().registryAccess()).is(Items.FLINT),
                "Dismantling arrows also returns whole flint");
        helper.assertTrue(manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                        .noneMatch(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()).is(MiteItems.FLINT_CHIP.get())),
                "No loaded crafting recipe can reproduce chips");
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void firstLoginPlacesExactlyOneTorchAndCloningRetainsCompletion(GameTestHelper helper) {
        var player = player(helper);
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        StartingTorch.login(new PlayerEvent.PlayerLoggedInEvent(player));
        helper.assertTrue(StartingTorch.place(player) && helper.getLevel().getBlockState(position).is(Blocks.TORCH),
                "The first login places a torch in the block containing the player's feet");
        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        StartingTorch.login(new PlayerEvent.PlayerLoggedInEvent(player));
        helper.assertTrue(!StartingTorch.place(player), "Logging back in cannot create a second torch");
        var clone = player(helper);
        StartingTorch.clone(new PlayerEvent.Clone(clone, player, true));
        helper.assertTrue(clone.getPersistentData().getBoolean(StartingTorch.DATA),
                "The saved completion marker survives player cloning after death");
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void startingTorchWaitsForSafeGroundWithoutReplacingWaterOrSolidBlocks(GameTestHelper helper) {
        var player = player(helper);
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        StartingTorch.login(new PlayerEvent.PlayerLoggedInEvent(player));
        helper.getLevel().setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(position, Blocks.WATER.defaultBlockState());
        helper.assertTrue(!StartingTorch.place(player) && helper.getLevel().getBlockState(position).is(Blocks.WATER),
                "A wet spawn retains its water while the starting torch waits");
        helper.getLevel().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
        helper.assertTrue(!StartingTorch.place(player), "A solid occupied block is retained");
        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        helper.assertTrue(StartingTorch.place(player), "A safe empty position consumes the pending one-time placement");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "StartingEquipment"), net.minecraft.server.level.ClientInformation.createDefault());
    }
}
