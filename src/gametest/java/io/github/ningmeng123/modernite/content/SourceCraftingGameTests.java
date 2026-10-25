package io.github.ningmeng123.modernite.content;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real recipe lookup, click modes, completion boundaries and redstone manufacturing. @author NingMeng123 */
@GameTestHolder("modern_ite_crafting")
@PrefixGameTestTemplate(false)
public final class SourceCraftingGameTests {
    private SourceCraftingGameTests() {}

    @GameTest(template = "gear_empty")
    public static void everyTorchBundleVariantWaitsAndConsumesOnlyAtTheFinalTick(GameTestHelper h) {
        for (boolean workbench : new boolean[]{false, true}) {
            try (Session session = new Session(h, workbench)) {
                for (Item binding : List.of(Items.STRING, MiteItems.SINEW.get())) {
                    for (Item fuel : List.of(Items.COAL, Items.CHARCOAL)) {
                        session.bundle(binding, fuel, 2);
                        h.assertTrue(TimedCraftingController.isTimed(session.player, session.menu), "Every registered bundle variant starts a manufacturing session");
                        session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
                        session.finishBatch(0, ExtremeContent.TORCH_BUNDLE.get(), 1);
                        h.assertValueEqual(session.grid.getItem(0).getCount(), 1, "Exactly one log is consumed per bundle");
                        h.assertValueEqual(session.grid.getItem(1).getCount(), 1, "Exactly one binding is consumed per bundle");
                        h.assertValueEqual(session.grid.getItem(2).getCount(), 1, "Exactly one first fuel stack is consumed");
                        h.assertValueEqual(session.grid.getItem(3).getCount(), 1, "Exactly one second fuel stack is consumed");
                        session.tick(session.period() * 2);
                        h.assertValueEqual(session.outputs(ExtremeContent.TORCH_BUNDLE.get()), 1, "An ordinary click makes one batch even when more ingredients remain");
                    }
                }
                session.unpack(2);
                h.assertValueEqual(session.period(), 25, "The legacy tiny component difficulty retains its minimum twenty-five-tick unpacking period");
                session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
                session.finishBatch(0, Items.TORCH, 16);
                h.assertValueEqual(session.grid.getItem(0).getCount(), 1, "One completed unpack consumes exactly one bundle");
                session.tick(50);
                h.assertValueEqual(session.outputs(Items.TORCH), 16, "An ordinary unpack leaves the next bundle untouched");
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void shiftAndNumberKeysCannotBypassEitherTorchBundlePeriod(GameTestHelper h) {
        for (boolean workbench : new boolean[]{false, true}) for (boolean unpack : new boolean[]{false, true}) {
            try (Session session = new Session(h, workbench)) {
                if (unpack) session.unpack(3); else session.bundle(Items.STRING, Items.COAL, 3);
                Item output = unpack ? Items.TORCH : ExtremeContent.TORCH_BUNDLE.get();
                int amount = unpack ? 16 : 1;
                var inputs = TimedCraftingController.snapshot(session.grid);
                for (int hotbar = 0; hotbar < 9; hotbar++) session.menu.clicked(0, hotbar, ClickType.SWAP, session.player);
                session.menu.clicked(0, 40, ClickType.SWAP, session.player);
                h.assertTrue(TimedCraftingController.matches(inputs, session.grid) && session.menu.getCarried().isEmpty()
                        && session.outputs(output) == 0, "Number keys and offhand swapping neither consume ingredients nor deliver a preview");
                session.menu.clicked(0, 0, ClickType.QUICK_MOVE, session.player);
                for (int batch = 0; batch < 3; batch++) session.finishBatch(batch, output, amount);
                h.assertTrue(session.grid.isEmpty(), "Continuous manufacturing consumes each of the three paid batches");
                session.tick(50);
                h.assertValueEqual(session.outputs(output), 3 * amount, "An exhausted continuous session cannot duplicate outputs");
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void cancellingOrChangingTorchInputsDiscardsProgressWithoutConsumingMaterials(GameTestHelper h) {
        try (Session session = new Session(h, false)) {
            session.unpack(2);
            session.menu.clicked(0, 0, ClickType.QUICK_MOVE, session.player);
            session.tick(12);
            session.menu.clicked(0, 1, ClickType.PICKUP, session.player);
            session.tick(50);
            h.assertTrue(!TimedCraftingController.isActive(session.player) && session.grid.getItem(0).getCount() == 2
                    && session.outputs(Items.TORCH) == 0, "Right-click cancellation preserves both bundles and emits no torches");
            session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
            session.finishBatch(0, Items.TORCH, 16);
            session.bundle(Items.STRING, Items.COAL, 2);
            session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
            session.tick(session.period() / 2);
            session.grid.setItem(2, new ItemStack(Items.CHARCOAL, 2));
            session.grid.setItem(3, new ItemStack(Items.CHARCOAL, 2));
            session.menu.slotsChanged(session.grid);
            session.tick(1);
            h.assertTrue(!TimedCraftingController.isActive(session.player) && TimedCraftingController.progress(session.player) == 0,
                    "Changing to a different valid same-output recipe discards the old recipe's progress");
            h.assertTrue(session.grid.getItem(0).getCount() == 2 && session.outputs(ExtremeContent.TORCH_BUNDLE.get()) == 0,
                    "Changing fuel preserves every unconsumed ingredient");
            session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
            session.finishBatch(0, ExtremeContent.TORCH_BUNDLE.get(), 1);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", timeoutTicks = 200)
    public static void redstoneUnpackingAlsoWaitsAndProducesOneSixteenTorchBatchPerPulse(GameTestHelper h) {
        BlockPos position = new BlockPos(1, 1, 1);
        h.setBlock(position, Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.EAST_UP));
        h.setBlock(position.east(), Blocks.CHEST);
        CrafterBlockEntity crafter = (CrafterBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(position));
        Container output = (Container)h.getLevel().getBlockEntity(h.absolutePos(position.east()));
        crafter.setItem(0, new ItemStack(ExtremeContent.TORCH_BUNDLE.get(), 2));
        var holder = CrafterBlock.getPotentialResults(h.getLevel(), crafter.asCraftInput()).orElseThrow();
        h.assertValueEqual(holder.id(), ResourceLocation.parse("modern_ite:unpack_torches_1"), "Automation matches the real registered unpacking recipe");
        h.pulseRedstone(position.west(), 5);
        h.runAfterDelay(25, () -> h.assertTrue(output.isEmpty() && crafter.getItem(0).getCount() == 2,
                "The redstone request cannot dispense torches before the full twenty-five manufacturing ticks"));
        h.runAfterDelay(35, () -> h.assertTrue(count(output, Items.TORCH) == 16 && crafter.getItem(0).getCount() == 1,
                "The full manufacturing period emits exactly sixteen torches and consumes one bundle"));
        h.runAfterDelay(70, () -> {
            h.assertTrue(count(output, Items.TORCH) == 16 && crafter.getItem(0).getCount() == 1,
                    "One pulse cannot queue a second automatic unpack");
            h.succeed();
        });
    }

    @GameTest(template = "gear_empty")
    public static void allLoadedStaticExtensionCraftingRecipesHaveFiniteManufacturingRules(GameTestHelper h) throws Exception {
        var report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        var timed = new JsonArray();
        var missing = new JsonArray();
        var dynamic = new JsonArray();
        var otherInstant = new JsonArray();
        var otherDynamic = new JsonArray();
        int otherTimed = 0;
        var manager = h.getLevel().getRecipeManager();
        for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .sorted(Comparator.comparing(value -> value.id().toString())).toList()) {
            var row = new JsonObject();
            row.addProperty("recipe", holder.id().toString());
            row.addProperty("serializer", BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer()).toString());
            var output = holder.value().getResultItem(h.getLevel().registryAccess());
            if (output.isEmpty()) {
                if (holder.id().getNamespace().equals("modern_ite")) dynamic.add(row);
                else otherDynamic.add(row);
                continue;
            }
            row.addProperty("output", BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
            row.addProperty("count", output.getCount());
            var rule = CraftingQuality.rule(holder, output, CraftingInput.EMPTY);
            if (!holder.id().getNamespace().equals("modern_ite")) {
                if (rule == null) otherInstant.add(row);
                else otherTimed++;
                continue;
            }
            if (rule == null || !Float.isFinite(rule.difficulty()) || rule.difficulty() < 0F) missing.add(row);
            else {
                row.addProperty("difficulty", rule.difficulty());
                row.addProperty("required_bench", rule.requiredBench());
                row.addProperty("level_zero_base_ticks", TimedCraftingRules.basePeriod(rule.difficulty()));
                timed.add(row);
            }
        }
        report.addProperty("timed_static_count", timed.size());
        report.addProperty("missing_or_invalid_count", missing.size());
        report.addProperty("dynamic_count", dynamic.size());
        report.add("timed_static", timed);
        report.add("missing_or_invalid", missing);
        report.add("dynamic_requires_real_inputs", dynamic);
        report.addProperty("other_timed_static_count", otherTimed);
        report.addProperty("other_instant_static_count", otherInstant.size());
        report.add("other_instant_static", otherInstant);
        report.add("other_dynamic_requires_real_inputs", otherDynamic);
        Path path = Path.of(System.getProperty("modern_ite.craftingCoverageOutput", "build/verification/ite-crafting-coverage.json"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        h.assertTrue(timed.size() >= 154, "The live manager audits all static extension recipes, including every gem, enhancement stone and resource recipe");
        h.assertValueEqual(missing.size(), 0, "Every loaded static extension crafting recipe reaches a valid manufacturing rule: " + missing);
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void loadedSourceDifficultyOverridesAndComponentSumsMatchIndependentEvidence(GameTestHelper h) {
        var vectors = Map.ofEntries(
                Map.entry("gem_damage_2", 75F), Map.entry("gem_health_6", 75F),
                Map.entry("gem_box_from_gem_speed_6", 75F), Map.entry("nether_ender_eye", 100F),
                Map.entry("resurrection_array", 25F), Map.entry("voucher_club_core", 3F),
                Map.entry("maple_slab", 240F), Map.entry("maple_stairs", 480F),
                Map.entry("iron_enhance_stone", 2400F), Map.entry("mithril_enhance_stone", 8800F),
                Map.entry("adamantium_enhance_stone", 40000F), Map.entry("universal_enhance_stone", 580F));
        for (var vector : vectors.entrySet()) {
            var id = ResourceLocation.parse("modern_ite:" + vector.getKey());
            var holder = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(value -> value.id().equals(id)).findFirst().orElseThrow();
            var output = holder.value().getResultItem(h.getLevel().registryAccess());
            var rule = CraftingQuality.rule(holder, output, CraftingInput.EMPTY);
            h.assertTrue(rule != null, "The evidence vector reaches the shared player/JEI/Crafter lookup: " + id);
            h.assertValueEqual(rule.difficulty(), vector.getValue().floatValue(),
                    "The loaded recipe retains its explicit source override or whole-batch component sum: " + id);
        }
        var lantern = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(value -> value.id().equals(ResourceLocation.withDefaultNamespace("lantern"))).findFirst().orElseThrow();
        var lanternRule = CraftingQuality.rule(lantern, lantern.value().getResultItem(h.getLevel().registryAccess()), CraftingInput.EMPTY);
        h.assertTrue(lanternRule != null && lanternRule.difficulty() == 711.1111F,
                "The native modern lantern recipe retains the source whole-batch metal component difficulty");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void clubRepairAndSmithingUseTheExplicitSourceLowestDifficulty(GameTestHelper h) {
        for (String material : List.of("copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium")) {
            ItemStack first = new ItemStack(ExtremeContent.ITEM_ENTRIES.get("club_" + material).get());
            h.assertValueEqual(CraftingQuality.lowestDifficulty(first), 1F,
                    "Every club retains the source's explicit minimum production difficulty: " + material);
            h.assertValueEqual(MaterialUpgradeRequirements.difficulty(first), 1F,
                    "The real smithing controller must not fall back to an invented hundred difficulty: " + material);
            first.setDamageValue(first.getMaxDamage() * 3 / 4);
            var input = CraftingInput.of(2, 1, List.of(first, first.copy()));
            var holder = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
            h.assertTrue(holder.value() instanceof RepairItemRecipe, "The real manager matches native two-item club repair: " + material);
            var output = holder.value().assemble(input, h.getLevel().registryAccess());
            var rule = CraftingQuality.rule(holder, output, input);
            h.assertTrue(rule != null && rule.difficulty() == .5F,
                    "A club repair uses half its explicit lowest production difficulty: " + material);
        }
        // Initial club manufacture still pays the actual material recipe difficulty.
        h.assertValueEqual(TimedCraftingRecipes.get(ResourceLocation.parse("modern_ite:club_iron")).difficulty(), 2755.5557F,
                "The item-level minimum must not replace the complete iron-club recipe's material sum");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void damagedMaterialLanternsMergeOnlyAfterACompleteRepairPeriod(GameTestHelper h) {
        try (Session session = new Session(h, false)) {
            for (String material : List.of("mithril", "adamantium", "vibranium")) {
                session.clear();
                Item item = ExtremeContent.ITEM_ENTRIES.get("dynamic_core_" + material).get();
                ItemStack first = new ItemStack(item);
                first.setDamageValue(first.getMaxDamage() * 3 / 4);
                session.grid.setItem(0, first.copy());
                session.grid.setItem(1, first.copy());
                session.menu.slotsChanged(session.grid);
                var holder = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                        session.grid.asCraftInput(), h.getLevel()).orElseThrow();
                h.assertTrue(holder.value() instanceof RepairItemRecipe, "Two damaged lanterns match the actual native repair recipe: " + material);
                ItemStack expected = holder.value().assemble(session.grid.asCraftInput(), h.getLevel().registryAccess());
                h.assertTrue(expected.is(item) && expected.getDamageValue() < first.getDamageValue(),
                        "The native preview combines both remaining lantern lifetimes: " + material);
                h.assertValueEqual(session.period(), 25, "A material lantern repair retains the source minimum period: " + material);
                session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
                h.assertTrue(TimedCraftingController.isActive(session.player),
                        "The real controller admits damaged lantern inputs through its repair allowance: " + material);
                session.finishBatch(0, item, 1);
                h.assertTrue(session.grid.isEmpty(), "The final repair tick consumes exactly the two damaged lanterns: " + material);
                ItemStack delivered = session.player.getInventory().items.stream().filter(stack -> stack.is(item)).findFirst().orElseThrow();
                h.assertTrue(ItemStack.matches(delivered, expected), "The actual completed transaction preserves the exact native repair result: " + material);
                session.tick(50);
                h.assertValueEqual(session.outputs(item), 1, "An exhausted repair cannot manufacture an extra lantern: " + material);
            }
        }
        h.succeed();
    }

    private static int count(Container container, Item item) {
        int count = 0;
        for (int index = 0; index < container.getContainerSize(); index++) {
            ItemStack stack = container.getItem(index);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static final class Session implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final AbstractContainerMenu menu;
        private final CraftingContainer grid;
        private final BlockPos table;
        private final BlockState previous;

        private Session(GameTestHelper helper, boolean workbench) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ite-crafting"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.setInvulnerable(true);
            player.getInventory().clearContent();
            player.removeAllEffects();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            previous = helper.getLevel().getBlockState(table);
            if (workbench) {
                helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
                menu = new CraftingMenu(49, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            } else menu = player.inventoryMenu;
            player.containerMenu = menu;
            grid = (CraftingContainer)menu.getSlot(1).container;
        }

        private void clear() {
            TimedCraftingController.forget(player);
            grid.clearContent();
            player.getInventory().clearContent();
            menu.setCarried(ItemStack.EMPTY);
        }

        private void bundle(Item binding, Item fuel, int batches) {
            clear();
            grid.setItem(0, new ItemStack(Items.OAK_LOG, batches));
            grid.setItem(1, new ItemStack(binding, batches));
            grid.setItem(2, new ItemStack(fuel, batches));
            grid.setItem(3, new ItemStack(fuel, batches));
            menu.slotsChanged(grid);
            helper.assertTrue(menu.getSlot(0).getItem().is(ExtremeContent.TORCH_BUNDLE.get()), "The real grid previews the source torch bundle");
            var holder = player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), player.level()).orElseThrow();
            helper.assertValueEqual(TimedCraftingRecipes.get(holder.id()).difficulty(), 195F, "All four source bundle recipes retain the full 195-difficulty batch");
            helper.assertValueEqual(period(), menu == player.inventoryMenu ? 138 : 114,
                    "The unskilled bundle period matches the source's inventory or workbench formula");
        }

        private void unpack(int batches) {
            clear();
            grid.setItem(0, new ItemStack(ExtremeContent.TORCH_BUNDLE.get(), batches));
            menu.slotsChanged(grid);
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.TORCH) && menu.getSlot(0).getItem().getCount() == 16,
                    "The loaded unpacking recipe previews exactly sixteen torches");
        }

        private int period() { return TimedCraftingController.period(player, menu); }
        private void tick(int ticks) { for (int index = 0; index < ticks; index++) TimedCraftingController.tick(player); }
        private int outputs(Item item) { return count(player.getInventory(), item) + (menu.getCarried().is(item) ? menu.getCarried().getCount() : 0); }

        private void finishBatch(int completed, Item output, int amount) {
            int period = period();
            helper.assertTrue(period >= 25, "The matched recipe retains at least the legacy minimum period");
            var inputs = TimedCraftingController.snapshot(grid);
            tick(period - 1);
            helper.assertTrue(TimedCraftingController.matches(inputs, grid), "All ingredients remain intact through the penultimate production tick");
            helper.assertValueEqual(TimedCraftingController.progress(player), period - 1, "The server advances exactly one progress tick per production tick");
            helper.assertValueEqual(outputs(output), completed * amount, "No early output escapes through the cursor, hotbar or inventory");
            TimedCraftingController.tick(player);
            helper.assertValueEqual(outputs(output), (completed + 1) * amount, "The final tick delivers exactly one paid output batch");
        }

        @Override public void close() {
            TimedCraftingController.forget(player);
            grid.clearContent();
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().clearContent();
            player.containerMenu = player.inventoryMenu;
            player.server.getPlayerList().remove(player);
            helper.getLevel().setBlockAndUpdate(table, previous);
            channel.finishAndReleaseAll();
        }
    }
}
