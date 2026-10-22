package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.VibraniumUpgradeRecipe;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemSockets;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder("modern_ite_feedback")
@PrefixGameTestTemplate(false)
public final class CopperArmorUpgradeGameTests {
    private CopperArmorUpgradeGameTests() {}

    @GameTest(template = "gear_empty")
    public static void copperClubUpgradeUsesTheFullIronRecipeAndOneTimedWorkbenchTransaction(GameTestHelper helper) {
        try (Session session = new Session(helper)) {
            var recipeId = ResourceLocation.parse("modern_ite:equipment_upgrade/club_copper_to_iron");
            var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElseThrow();
            helper.assertTrue(holder.value() instanceof VibraniumUpgradeRecipe, "The copper club route is a shaped upgrade, not smithing");
            var recipe = (VibraniumUpgradeRecipe)holder.value();
            var source = new ItemStack(ExtremeContent.ITEM_ENTRIES.get("club_copper").get());
            EquipmentDurability.setQuality(source, Quality.FINE);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 copper club"));
            source.set(DataComponents.REPAIR_COST, 7);
            source.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 4, 713L, Map.of(), 1));
            source.set(ExtremeGear.FORGE_GRADE.get(), 2);
            source.set(ExtremeGear.GRINDINGS.get(), 3);
            source.set(ExtremeGear.SOCKETS.get(), new GemSockets(List.of(1, 12, 18, 24, 30, 36, 42, 60)));
            source.setDamageValue(source.getMaxDamage() / 3);
            String[] shape = {"#*#", "#*#", "C* "};
            int ingots = 0, nuggets = 0;
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
                char token = shape[row].charAt(column);
                if (token == '*') ingots++;
                if (token == '#') nuggets++;
                session.grid.setItem(row * 3 + column, switch (token) {
                    case '*' -> new ItemStack(Items.IRON_INGOT);
                    case '#' -> new ItemStack(Items.IRON_NUGGET);
                    case 'C' -> source.copy();
                    default -> ItemStack.EMPTY;
                });
            }
            helper.assertTrue(ingots == 3 && nuggets == 4, "The workbench pays the full three iron ingots and four nuggets plus the copper club");
            helper.assertTrue(recipe.matches(session.grid.asCraftInput(), helper.getLevel()), "The real workbench accepts the exact full-cost pattern");
            var expected = recipe.assemble(session.grid.asCraftInput(), helper.getLevel().registryAccess());
            helper.assertTrue(expected.is(ExtremeContent.ITEM_ENTRIES.get("club_iron").get()), "The recipe produces the corresponding iron club");
            helper.assertValueEqual(expected.get(DataComponents.CUSTOM_NAME), source.get(DataComponents.CUSTOM_NAME), "The personal name survives");
            helper.assertValueEqual(expected.get(DataComponents.REPAIR_COST), source.get(DataComponents.REPAIR_COST), "Repair history survives");
            helper.assertValueEqual(GearProgression.state(expected), GearProgression.state(source), "Growth identity, historical level and stored experience survive unchanged");
            helper.assertValueEqual(expected.get(ExtremeGear.FORGE_GRADE.get()), source.get(ExtremeGear.FORGE_GRADE.get()), "Forge grade survives");
            helper.assertValueEqual(expected.get(ExtremeGear.GRINDINGS.get()), source.get(ExtremeGear.GRINDINGS.get()), "Grinding history survives");
            helper.assertValueEqual(expected.get(ExtremeGear.SOCKETS.get()), source.get(ExtremeGear.SOCKETS.get()), "Every gem socket survives");
            helper.assertValueEqual(EquipmentDurability.quality(expected), Quality.FINE, "Equipment quality survives");
            helper.assertTrue(Math.abs((double)source.getDamageValue() / source.getMaxDamage()
                    - (double)expected.getDamageValue() / expected.getMaxDamage()) <= 1D / expected.getMaxDamage(), "Wear retains its previous proportion");
            var smithingInput = new SmithingRecipeInput(new ItemStack(io.github.ningmeng123.mite.registry.MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()),
                    source.copy(), new ItemStack(Items.IRON_INGOT, 4));
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, smithingInput, helper.getLevel()).isEmpty(),
                    "Even a full smithing payment cannot bypass the copper club workbench recipe");
            int period = TimedCraftingController.period(session.player, session.menu);
            helper.assertTrue(period >= 25, "The copper club upgrade has a real manufacturing duration");
            session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
            for (int tick = 1; tick < period; tick++) TimedCraftingController.tick(session.player);
            helper.assertTrue(session.player.getInventory().isEmpty(), "No result appears before the final production tick");
            helper.assertValueEqual(TimedCraftingController.progress(session.player), period - 1, "Production advances without completing early");
            TimedCraftingController.tick(session.player);
            helper.assertTrue(session.grid.isEmpty(), "A completed transaction consumes the copper club and all seven metal ingredients");
            helper.assertValueEqual(session.player.getInventory().countItem(expected.getItem()), 1, "The upgrade delivers exactly one iron club");
            var actual = session.player.getInventory().items.stream().filter(stack -> stack.is(expected.getItem())).findFirst().orElseThrow();
            helper.assertTrue(ItemStack.isSameItemSameComponents(expected, actual), "Timed delivery preserves every preview component");
            TimedCraftingController.tick(session.player);
            helper.assertValueEqual(session.player.getInventory().countItem(expected.getItem()), 1, "The completed transaction cannot duplicate its output");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void fourCopperArmorUpgradesUseFullIronShapesAndPreservePersonalEquipment(GameTestHelper helper) {
        for (String family : List.of("helmet", "chestplate", "leggings", "boots")) try (Session session = new Session(helper)) {
            var recipeId = ResourceLocation.parse("mite:equipment_upgrade/copper_" + family + "_to_iron");
            var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElseThrow();
            helper.assertTrue(holder.value() instanceof VibraniumUpgradeRecipe, "The old smithing ID now loads as a shaped equipment upgrade");
            var recipe = (VibraniumUpgradeRecipe)holder.value();
            var source = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:copper_" + family)));
            EquipmentDurability.setQuality(source, Quality.FINE);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("Personal copper " + family));
            source.setDamageValue(source.getMaxDamage() / 3);
            String[] shape = switch (family) {
                case "helmet" -> new String[]{"III", "ICI", "   "};
                case "chestplate" -> new String[]{"ICI", "III", "III"};
                case "leggings" -> new String[]{"III", "ICI", "I I"};
                default -> new String[]{"ICI", "I I", "   "};
            };
            int iron = 0;
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
                char token = shape[row].charAt(column);
                if (token == 'I') iron++;
                session.grid.setItem(row * 3 + column, token == 'C' ? source.copy()
                        : token == 'I' ? new ItemStack(Items.IRON_INGOT) : ItemStack.EMPTY);
            }
            helper.assertValueEqual(iron, switch (family) {case "helmet" -> 5; case "chestplate" -> 8; case "leggings" -> 7; default -> 4;},
                    "The upgrade uses the complete standard iron recipe plus one corresponding copper armor");
            helper.assertTrue(recipe.matches(session.grid.asCraftInput(), helper.getLevel()), "The authored shape matches the real workbench grid");
            var expected = recipe.assemble(session.grid.asCraftInput(), helper.getLevel().registryAccess());
            helper.assertTrue(expected.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:iron_" + family))), "The matching iron piece is produced");
            helper.assertValueEqual(expected.get(DataComponents.CUSTOM_NAME), source.get(DataComponents.CUSTOM_NAME), "The personal name survives");
            helper.assertValueEqual(EquipmentDurability.quality(expected), Quality.FINE, "The source quality survives");
            helper.assertTrue(Math.abs((double)source.getDamageValue() / source.getMaxDamage()
                    - (double)expected.getDamageValue() / expected.getMaxDamage()) <= 1D / expected.getMaxDamage(), "The wear fraction survives within one point");
            var smithingInput = new SmithingRecipeInput(new ItemStack(io.github.ningmeng123.mite.registry.MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()),
                    source.copy(), new ItemStack(Items.IRON_INGOT));
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, smithingInput, helper.getLevel()).isEmpty(),
                    "A smithing table cannot retain the cheaper copper armor route");
            int period = TimedCraftingController.period(session.player, session.menu);
            helper.assertTrue(period >= 25, "Workbench upgrades require a real manufacturing period");
            session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
            for (int tick = 1; tick < period; tick++) TimedCraftingController.tick(session.player);
            helper.assertValueEqual(TimedCraftingController.progress(session.player), period - 1, "An upgrade cannot finish before its last production tick");
            helper.assertTrue(session.player.getInventory().isEmpty(), "No early output is delivered");
            TimedCraftingController.tick(session.player);
            helper.assertTrue(session.grid.isEmpty(), "The completed upgrade consumes all iron and the copper armor");
            helper.assertValueEqual(session.player.getInventory().countItem(expected.getItem()), 1, "Exactly one iron piece is delivered");
            var actual = session.player.getInventory().items.stream().filter(stack -> stack.is(expected.getItem())).findFirst().orElseThrow();
            helper.assertTrue(ItemStack.isSameItemSameComponents(expected, actual), "The timed result preserves the component-bearing preview");
        }
        helper.succeed();
    }

    private static final class Session implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final CraftingMenu menu;
        private final CraftingContainer grid;
        private final GameTestHelper helper;
        private final BlockPos table;
        private final BlockState previous;
        private Session(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "copper-upgrade"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            previous = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("iron").get().defaultBlockState());
            menu = new CraftingMenu(47, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer)menu.getSlot(1).container;
        }
        @Override public void close() {
            TimedCraftingController.forget(player);
            CraftingQuality.clear(player);
            grid.clearContent();
            player.containerMenu = player.inventoryMenu;
            player.server.getPlayerList().remove(player);
            helper.getLevel().setBlockAndUpdate(table, previous);
            channel.finishAndReleaseAll();
        }
    }
}
