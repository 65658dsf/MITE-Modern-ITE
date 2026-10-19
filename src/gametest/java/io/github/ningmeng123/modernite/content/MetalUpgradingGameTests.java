package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingContent;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingEntity;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingMenu;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingRecipe;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real machine timing, heat, inventory transactions, persistence and recipe-viewer inputs. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class MetalUpgradingGameTests {
    private MetalUpgradingGameTests() {}

    @GameTest(template = "gear_empty", batch = "ite_config")
    public static void defaultConversionsUseAnIndependentMachineAndKeepGemSmelting(GameTestHelper h) {
        var before = IteConfig.effectiveSnapshot(); boolean local = before == IteConfig.localSnapshot();
        try {
            configure(6000, 2, 9000, 1);
            var recipes = h.getLevel().getRecipeManager().getAllRecipesFor(MetalUpgradingContent.RECIPE_TYPE.get());
            h.assertTrue(recipes.size() == 2 && recipes.stream().allMatch(holder -> holder.value().getType() != RecipeType.SMELTING),
                    "Two upgrades have their own real recipe type, separate from normal smelting");
            int index = 0;
            for (var holder : recipes) {
                var recipe = holder.value();
                h.assertTrue(holder.id().getPath().equals("metal_upgrading/" + recipe.stage().getSerializedName()),
                        "The separately registered recipe is addressable by a stable ID");
                ItemStack input = recipe.input();
                var normal = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), h.getLevel());
                Item expectedShard = item(recipe.stage() == MetalUpgradingRecipe.Stage.IRON_TO_MITHRIL
                        ? "modern_ite:gem_shard_2" : "modern_ite:gem_shard_4");
                h.assertTrue(normal.isPresent() && normal.get().value().getResultItem(h.getLevel().registryAccess()).is(expectedShard),
                        "The original iron/mithril block-to-gem recipe remains discoverable as normal smelting");
                var furnace = machine(h, index++);
                furnace.setItem(0, input.copy());
                int fuelTicks = FurnaceThermalController.fuel(new ItemStack(ExtremeContent.BLAZE_COAL.get())).ticks();
                furnace.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get(), (recipe.cookingTicks() + fuelTicks - 1) / fuelTicks));
                tick(h, furnace, recipe.cookingTicks() - 1);
                h.assertTrue(furnace.getItem(2).isEmpty() && furnace.getItem(0).getCount() == recipe.inputCount(),
                        "A default conversion keeps the complete input until exactly its configured 6000/9000-tick deadline");
                tick(h, furnace, 1);
                h.assertTrue(furnace.getItem(0).isEmpty() && ItemStack.isSameItemSameComponents(furnace.getItem(2), recipe.output())
                        && furnace.getItem(2).getCount() == 1, "The upgrade consumes exactly its configured block count and produces one higher metal ingot");

                BlockPos normalPos = h.absolutePos(new BlockPos(2 + index, 2, 5));
                h.getLevel().setBlockAndUpdate(normalPos, ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState());
                var ordinary = (VibraniumFurnaceEntity)h.getLevel().getBlockEntity(normalPos);
                ordinary.setItem(0, new ItemStack(input.getItem())); ordinary.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get()));
                for (int tick = 0; tick < 200; tick++) AbstractFurnaceBlockEntity.serverTick(h.getLevel(), normalPos,
                        h.getLevel().getBlockState(normalPos), ordinary);
                h.assertTrue(ordinary.getItem(0).isEmpty() && ordinary.getItem(2).is(expectedShard),
                        "An actual existing furnace still produces its original gem shard in 200 ticks");
            }
        } finally { restore(before, local); }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "ite_config")
    public static void insufficientInputsColdFuelAndBlockedOutputsDoNotBurnFuel(GameTestHelper h) {
        var before = IteConfig.effectiveSnapshot(); boolean local = before == IteConfig.localSnapshot();
        try {
            configure(4, 2, 4, 2);
            var iron = recipe(h, MetalUpgradingRecipe.Stage.IRON_TO_MITHRIL);
            var mithril = recipe(h, MetalUpgradingRecipe.Stage.MITHRIL_TO_ADAMANTIUM);
            var furnace = machine(h, 0);
            furnace.setItem(0, iron.input().copyWithCount(1)); furnace.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get()));
            tick(h, furnace, 8);
            h.assertTrue(furnace.getItem(1).getCount() == 1 && furnace.getItem(2).isEmpty(), "A partial input batch does not consume fuel");
            furnace.setItem(0, iron.input()); furnace.setItem(1, new ItemStack(Items.OAK_PLANKS));
            tick(h, furnace, 8);
            h.assertTrue(furnace.getItem(1).is(Items.OAK_PLANKS) && furnace.getItem(1).getCount() == 1 && furnace.getItem(2).isEmpty(),
                    "Wood cannot supply upgrade heat and remains unconsumed even when forcibly inserted");
            var heatThree = iron.fuelOptions().stream().filter(stack -> FurnaceThermalController.fuel(stack).heat() == 3).findFirst();
            h.assertTrue(iron.requiredHeat() == 3 && mithril.requiredHeat() == 4 && heatThree.isPresent(),
                    "The existing thermal policy distinguishes iron heat three from mithril heat four");
            furnace.setItem(0, mithril.input()); furnace.setItem(1, heatThree.orElseThrow().copyWithCount(1));
            tick(h, furnace, 8);
            h.assertTrue(furnace.getItem(1).getCount() == 1 && furnace.getItem(2).isEmpty(), "A valid iron fuel cannot be burned for a hotter mithril upgrade");
            furnace.setItem(0, iron.input()); furnace.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get()));
            furnace.setItem(2, new ItemStack(Items.DIRT)); tick(h, furnace, 8);
            h.assertTrue(furnace.getItem(1).getCount() == 1 && furnace.getItem(2).is(Items.DIRT), "An incompatible output never burns fuel or overwrites inventory");
            ItemStack full = iron.output(); full.setCount(full.getMaxStackSize()); furnace.setItem(2, full); tick(h, furnace, 8);
            h.assertTrue(furnace.getItem(1).getCount() == 1 && furnace.getItem(0).getCount() == 2,
                    "A full matching output also keeps both fuel and input untouched");
            full.shrink(1); tick(h, furnace, 4);
            h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).getCount() == furnace.getItem(2).getMaxStackSize(),
                    "Exactly one freed output space enables exactly one atomic conversion");
        } finally { restore(before, local); }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "ite_config")
    public static void reloadPreservesProgressAndChangedCostsRestartTheCurrentBatch(GameTestHelper h) {
        var before = IteConfig.effectiveSnapshot(); boolean local = before == IteConfig.localSnapshot();
        try {
            configure(7, 2, 9, 1);
            var furnace = machine(h, 0); var iron = recipe(h, MetalUpgradingRecipe.Stage.IRON_TO_MITHRIL);
            furnace.setItem(0, iron.input()); furnace.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get())); tick(h, furnace, 3);
            var saved = furnace.saveWithoutMetadata(h.getLevel().registryAccess());
            int burn = saved.getInt("burn_remaining");
            furnace.clearContent(); furnace.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(furnace.data.get(0) == 3 && furnace.data.get(2) == 7 && furnace.getItem(0).getCount() == 2
                    && furnace.saveWithoutMetadata(h.getLevel().registryAccess()).getInt("burn_remaining") == burn,
                    "Saving and restoring retains input, accumulated progress, batch identity and remaining fuel");
            tick(h, furnace, 3); h.assertTrue(furnace.getItem(2).isEmpty(), "Restored progress cannot finish one tick early");
            tick(h, furnace, 1); h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).is(iron.output().getItem()),
                    "Reload resumes the exact remaining four ticks without consuming the input twice");
            furnace.setItem(2, ItemStack.EMPTY); furnace.setItem(0, iron.input()); tick(h, furnace, 3);
            configure(5, 3, 9, 1); tick(h, furnace, 1);
            h.assertTrue(furnace.data.get(0) == 0 && furnace.data.get(2) == 5 && furnace.data.get(10) == 3
                    && furnace.getItem(0).getCount() == 2 && furnace.getItem(2).isEmpty(),
                    "A live time/count change clears prior progress and cannot consume the newly insufficient input");
            furnace.setItem(0, new ItemStack(Items.IRON_BLOCK, 3)); tick(h, furnace, 4);
            h.assertTrue(furnace.getItem(2).isEmpty(), "The replacement cost must run the entire new duration");
            tick(h, furnace, 1); h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).getCount() == 1,
                    "The new configuration controls both the real batch cost and completion tick");
        } finally { restore(before, local); }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "ite_config")
    public static void viewerFuelBatchesReallyFinishAndRecipeChangesNeverReuseProgress(GameTestHelper h) {
        var before = IteConfig.effectiveSnapshot(); boolean local = before == IteConfig.localSnapshot();
        try {
            int rodTicks = FurnaceThermalController.fuel(new ItemStack(Items.BLAZE_ROD)).ticks();
            configure(rodTicks + 2, 2, rodTicks + 2, 1);
            var furnace = machine(h, 0);
            for (var holder : h.getLevel().getRecipeManager().getAllRecipesFor(MetalUpgradingContent.RECIPE_TYPE.get())) {
                var recipe = holder.value(); h.assertTrue(!recipe.fuelOptions().isEmpty(), "Each upgrade supplies searchable fuel alternatives");
                for (ItemStack fuel : recipe.fuelOptions()) {
                    h.assertTrue(fuel.getCount() <= fuel.getMaxStackSize(), "This displayed full batch fits a real fuel stack");
                    furnace.clearContent(); furnace.loadWithComponents(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
                    furnace.setItem(0, recipe.input()); furnace.setItem(1, fuel.copy());
                    tick(h, furnace, recipe.cookingTicks() - 1);
                    h.assertTrue(furnace.getItem(2).isEmpty(), "No displayed fuel alternative accelerates the configured operation");
                    tick(h, furnace, 1);
                    h.assertTrue(furnace.getItem(0).isEmpty() && ItemStack.isSameItemSameComponents(furnace.getItem(2), recipe.output())
                            && furnace.getItem(2).getCount() == 1, "Every JEI fuel batch executes the advertised exact conversion, including refuelling across a burn boundary");
                }
            }
            configure(3, 2, 5, 1);
            furnace.loadWithComponents(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
            furnace.setItem(0, new ItemStack(Items.IRON_BLOCK, 2)); furnace.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get()));
            tick(h, furnace, 2); furnace.setItem(0, new ItemStack(item("mite:mithril_block")));
            tick(h, furnace, 4); h.assertTrue(furnace.getItem(2).isEmpty(), "Switching recipes cannot transfer old iron progress into an adamantium ingot");
            tick(h, furnace, 1); h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).is(item("mite:adamantium_ingot")),
                    "The selected replacement recipe uses its complete own duration and output");
        } finally { restore(before, local); }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "ite_config")
    public static void recipeSynchronizationAndLongMenuTimersRetainTheirIdentity(GameTestHelper h) {
        var before = IteConfig.effectiveSnapshot(); boolean local = before == IteConfig.localSnapshot();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            configure(70000, 2, 9000, 1);
            var original = recipe(h, MetalUpgradingRecipe.Stage.IRON_TO_MITHRIL);
            MetalUpgradingContent.SERIALIZER.get().streamCodec().encode(buffer, original);
            var decoded = MetalUpgradingContent.SERIALIZER.get().streamCodec().decode(buffer);
            h.assertTrue(decoded.stage() == original.stage() && decoded.cookingTicks() == 70000 && decoded.inputCount() == 2
                    && ItemStack.isSameItemSameComponents(decoded.output(), original.output()),
                    "The actual registered serializer synchronizes the stage while retaining server-controlled cost and duration");
            var furnace = machine(h, 0); furnace.setItem(0, original.input()); furnace.setItem(1, new ItemStack(ExtremeContent.BLAZE_COAL.get()));
            tick(h, furnace, 1);
            var saved = furnace.saveWithoutMetadata(h.getLevel().registryAccess()); saved.putInt("progress", 65537);
            saved.putInt("burn_duration", 70002); saved.putInt("burn_remaining", 69999);
            furnace.loadWithComponents(saved, h.getLevel().registryAccess());
            var clientData = new SimpleContainerData(MetalUpgradingEntity.DATA_COUNT);
            for (int index = 0; index < clientData.getCount(); index++) clientData.set(index, (short)furnace.data.get(index));
            var menu = new MetalUpgradingMenu(1, h.makeMockPlayer(GameType.SURVIVAL).getInventory(), new SimpleContainer(3), clientData);
            h.assertTrue(menu.progress() == 65537 && menu.total() == 70000 && menu.burnRemaining() == 69999 && menu.burnDuration() == 70002,
                    "Native signed-short menu packets cannot truncate the larger configured cooking and fuel timers");
        } finally { buffer.release(); restore(before, local); }
        h.succeed();
    }

    private static void configure(int ironTicks, int ironCount, int mithrilTicks, int mithrilCount) {
        var values = new HashMap<String, Object>(IteConfig.effectiveSnapshot());
        values.put("iron_to_mithril_cook_time", ironTicks); values.put("iron_block_count_to_mithril", ironCount);
        values.put("mithril_to_adamantium_cook_time", mithrilTicks); values.put("mithril_block_count_to_adamantium", mithrilCount);
        IteConfig.receiveServerSnapshot(values);
    }
    private static void restore(Map<String, Object> values, boolean local) { if (local) IteConfig.clearServerSnapshot(); else IteConfig.receiveServerSnapshot(values); }
    private static MetalUpgradingEntity machine(GameTestHelper h, int index) {
        BlockPos pos = h.absolutePos(new BlockPos(2 + index, 2, 2));
        h.getLevel().setBlockAndUpdate(pos, MetalUpgradingContent.FURNACE.get().defaultBlockState());
        return (MetalUpgradingEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void tick(GameTestHelper h, MetalUpgradingEntity furnace, int ticks) {
        for (int tick = 0; tick < ticks; tick++) MetalUpgradingEntity.serverTick(h.getLevel(), furnace.getBlockPos(),
                h.getLevel().getBlockState(furnace.getBlockPos()), furnace);
    }
    private static MetalUpgradingRecipe recipe(GameTestHelper h, MetalUpgradingRecipe.Stage stage) {
        return h.getLevel().getRecipeManager().getAllRecipesFor(MetalUpgradingContent.RECIPE_TYPE.get()).stream()
                .map(RecipeHolder::value).filter(recipe -> recipe.stage() == stage).findFirst().orElseThrow();
    }
    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
}
