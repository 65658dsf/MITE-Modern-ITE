package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.SmithingTableTier;
import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import io.github.ningmeng123.mite.data.MaterialUpgradeRecipes;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemSockets;
import io.github.ningmeng123.modernite.gear.GemType;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Extreme tool acquisition and native single-stage smithing transactions. @author NingMeng123 */
@GameTestHolder("modern_ite_tool_smithing")
@PrefixGameTestTemplate(false)
public final class ToolSmithingGameTests {
    private static final Set<String> HIGH_METALS = Set.of("ancient_metal", "mithril", "adamantium", "vibranium");
    private static final List<String> CHAIN = List.of("iron", "ancient_metal", "mithril", "adamantium", "vibranium");

    private ToolSmithingGameTests() {}

    @GameTest(template = "gear_empty")
    public static void ancientMetalAndHigherToolsHaveNoCraftingResultsWhileIronKeepsItsRecipe(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(h.getLevel().registryAccess());
            var tool = MiteTools.definition(result);
            var simply = SimplySwordsCompat.definition(result);
            h.assertTrue(tool == null || !HIGH_METALS.contains(tool.material()),
                    "A loaded crafting recipe cannot produce a high-tier tool: " + holder.id());
            h.assertTrue(simply == null || !HIGH_METALS.contains(simply.material()),
                    "Optional weapon recipes follow the same high-tier acquisition rule: " + holder.id());
        }
        for (Item item : BuiltInRegistries.ITEM) {
            var definition = MiteTools.definition(new ItemStack(item));
            if (definition == null || !HIGH_METALS.contains(definition.material())) continue;
            h.assertTrue(manager.byKey(BuiltInRegistries.ITEM.getKey(item))
                            .map(holder -> holder.value().getType() != RecipeType.CRAFTING).orElse(true),
                    "The synchronized ID index cannot retain the original direct tool recipe: " + definition.id());
        }
        for (String id : List.of("mite:ancient_metal_pickaxe", "mite:mithril_chestplate", "mite:adamantium_chestplate"))
            h.assertTrue(manager.byKey(ResourceLocation.parse(id)).isEmpty(), "Old direct advanced-equipment recipes are absent: " + id);
        h.assertTrue(manager.byKey(ResourceLocation.parse("minecraft:iron_pickaxe")).isPresent(), "Iron retains its direct starting recipe");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void allToolFamiliesKeepTheirSingleStageSmithingAndPersonalComponents(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        int checked = 0;
        for (var route : toolRoutes()) {
            if (!CHAIN.contains(route.fromMaterial()) || !CHAIN.contains(route.toMaterial())) continue;
            ItemStack source = new ItemStack(route.base());
            if (MiteTools.definition(source) == null && SimplySwordsCompat.definition(source) == null) continue;
            EquipmentDurability.setQuality(source, Quality.FINE);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 inherited tool"));
            source.set(DataComponents.REPAIR_COST, 5);
            source.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 3, 123_456L, Map.of(), 1));
            source.set(ExtremeGear.SOCKETS.get(), new GemSockets(List.of(1, 12, 18, 24, 30, 36, 42, 60)));
            source.set(ExtremeGear.FORGE_GRADE.get(), 2);
            source.setDamageValue(source.getMaxDamage() / 3);
            ItemStack before = source.copy();
            int payment = MaterialUpgradeRequirements.ingots(new ItemStack(route.result()));
            var input = new SmithingRecipeInput(template(), source, new ItemStack(route.addition(), payment));
            var selected = manager.getRecipeFor(RecipeType.SMITHING, input, h.getLevel())
                    .orElseThrow(() -> new IllegalStateException("Missing smithing route: " + route.id()));
            h.assertTrue(selected.id().equals(ResourceLocation.parse(route.id())), "The active manager selects the same-family next-material route: " + route.id());
            ItemStack output = selected.value().assemble(input, h.getLevel().registryAccess());
            h.assertTrue(output.is(route.result()) && output.getCount() == 1, "One source produces exactly one next-material tool");
            assertPreserved(h, before, output);
            h.assertTrue(ItemStack.matches(source, before), "Smithing preview keeps its source untouched");
            for (String addition : CHAIN) {
                var candidate = new SmithingRecipeInput(template(), source, new ItemStack(ingot(addition), payment));
                h.assertTrue(manager.getRecipeFor(RecipeType.SMITHING, candidate, h.getLevel()).isPresent()
                                == addition.equals(route.toMaterial()),
                        "Skipped, reversed and same-material routes cannot match: " + route.id() + " / " + addition);
            }
            h.assertTrue(manager.getRecipeFor(RecipeType.SMITHING,
                    new SmithingRecipeInput(ItemStack.EMPTY, source, new ItemStack(route.addition(), payment)), h.getLevel()).isEmpty(),
                    "Every tool upgrade requires the shared smithing template");
            checked++;
        }
        h.assertTrue(checked >= 52, "All twelve original tool families and the completed knife family traverse four material stages");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void pickaxeChainWaitsForCompletionAndConsumesEachExactMaterialPayment(GameTestHelper h) {
        try (SmithingSession session = new SmithingSession(h)) {
            ItemStack current = new ItemStack(Items.IRON_PICKAXE);
            EquipmentDurability.setQuality(current, Quality.FINE);
            current.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 continuous pickaxe"));
            current.set(ExtremeGear.FORGE_GRADE.get(), 2);
            current.setDamageValue(current.getMaxDamage() / 4);
            for (String material : CHAIN.subList(1, CHAIN.size())) {
                Item target = item("mite:" + material + "_pickaxe");
                int required = MaterialUpgradeRequirements.ingots(new ItemStack(target));
                ItemStack before = current.copy();
                session.menu.setCarried(ItemStack.EMPTY);
                session.menu.getSlot(0).set(template());
                session.menu.getSlot(1).set(current);
                session.menu.getSlot(2).set(new ItemStack(ingot(material), required - 1));
                h.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "An underfunded pickaxe upgrade has no result");
                session.menu.getSlot(2).set(new ItemStack(ingot(material), required));
                h.assertTrue(session.menu.getSlot(3).getItem().is(target), "Each step has a valid native smithing preview");
                int period = TimedSmithingController.period(session.player, session.menu);
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                h.assertTrue(period > 0 && TimedSmithingController.isActive(session.player), "Taking a tool preview starts timed smithing");
                for (int tick = 1; tick < period; tick++) TimedSmithingController.tick(session.player);
                h.assertTrue(session.menu.getCarried().isEmpty() && ItemStack.matches(session.menu.getSlot(1).getItem(), before)
                                && session.menu.getSlot(0).getItem().getCount() == 1
                                && session.menu.getSlot(2).getItem().getCount() == required,
                        "No equipment, template or ingot commits before the final smithing tick");
                TimedSmithingController.tick(session.player);
                current = session.menu.getCarried().copy();
                h.assertTrue(current.is(target) && current.getCount() == 1 && session.menu.getSlot(0).getItem().isEmpty()
                                && session.menu.getSlot(1).getItem().isEmpty() && session.menu.getSlot(2).getItem().isEmpty(),
                        "Completion delivers one tool and consumes exactly one template, source and full material batch");
                assertPreserved(h, before, current);
                h.assertTrue(session.menu.quickMoveStack(session.player, 3).isEmpty() && session.player.getInventory().isEmpty(),
                        "The committed preview cannot be taken a second time");
            }
            h.assertTrue(current.is(item("mite:vibranium_pickaxe")), "Four consecutive native upgrades reach vibranium");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void recipeReplacementFiltersBothIndexesAndCrafterCannotFindTheOldPickaxeShape(GameTestHelper h) {
        RecipeManager manager = new RecipeManager(h.getLevel().registryAccess());
        List<RecipeHolder<?>> fixtures = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            var definition = MiteTools.definition(new ItemStack(item));
            if (definition != null && HIGH_METALS.contains(definition.material()))
                fixtures.add(fixture(BuiltInRegistries.ITEM.getKey(item).toString().replace(':', '/'), item));
        }
        fixtures.add(fixture("ancient_tool", item("mite:ancient_metal_pickaxe")));
        fixtures.add(fixture("high_armor", item("mite:mithril_chestplate")));
        fixtures.add(fixture("starting_tool", Items.IRON_PICKAXE));
        var smithing = h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("mite:equipment_upgrade/ancient_metal_pickaxe_to_mithril")).orElseThrow();
        fixtures.add(smithing);
        manager.replaceRecipes(fixtures);
        h.assertTrue(manager.getRecipes().size() == 2 && manager.getAllRecipesFor(RecipeType.CRAFTING).size() == 1
                        && manager.getAllRecipesFor(RecipeType.SMITHING).size() == 1,
                "Runtime recipe replacement applies the same tool restriction to both indexes and retains smithing");
        for (var holder : fixtures) {
            ItemStack result = holder.value().getResultItem(h.getLevel().registryAccess());
            var definition = MiteTools.definition(result);
            if (holder.value().getType() == RecipeType.CRAFTING && definition != null && HIGH_METALS.contains(definition.material()))
                h.assertTrue(manager.byKey(holder.id()).isEmpty(), "A dynamically replaced high-tier tool is inaccessible by ID");
        }
        BlockPos position = new BlockPos(1, 1, 1);
        h.setBlock(position, Blocks.CRAFTER);
        CrafterBlockEntity crafter = (CrafterBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(position));
        for (String material : HIGH_METALS) {
            crafter.clearContent();
            for (int slot = 0; slot < 3; slot++) crafter.setItem(slot, new ItemStack(ingot(material)));
            crafter.setItem(4, new ItemStack(Items.STICK));
            crafter.setItem(7, new ItemStack(Items.STICK));
            h.assertTrue(CrafterBlock.getPotentialResults(h.getLevel(), crafter.asCraftInput()).isEmpty(),
                    "The automatic crafter cannot match the retired direct high-tier pickaxe shape: " + material);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void completeKnifeFamilySupportsMaterialDurabilityGrowthGemsAndExistingRepair(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (String material : CHAIN) {
            String namespace = material.equals("vibranium") ? "mite:" : "modern_ite:";
            ItemStack knife = new ItemStack(item(namespace + material + "_knife"));
            var definition = MiteTools.definition(knife);
            var profile = EquipmentDurability.toolProfile(knife);
            h.assertTrue(definition != null && definition.family() == ToolFamily.KNIFE && material.equals(definition.material())
                            && profile != null && profile.family() == ToolFamily.KNIFE,
                    "Each registered metal knife participates in the existing knife profile: " + material);
            h.assertTrue(knife.getOrDefault(DataComponents.MAX_DAMAGE, 0) == EquipmentDurabilityRules.baseToolDurability(profile),
                    "The material supplies the existing knife durability formula: " + material);
            h.assertTrue(material.equals(GearProgression.material(knife)) && GearProgression.maxLevel(knife) == 15
                            && ExtremeGear.socketable(knife) && ExtremeGear.acceptsGem(knife, GemType.DAMAGE)
                            && ExtremeGear.acceptsGem(knife, GemType.EFFICIENCY) && ExtremeGear.acceptsGem(knife, GemType.DURABILITY),
                    "Every metal knife has its expected material, growth cap and usable tool gem slots: " + material);
            h.assertTrue(MaterialUpgradeRequirements.ingots(knife) == (material.equals("ancient_metal") ? 2 : 1),
                    "Knife upgrades retain the shared ancient-metal and one-component material costs: " + material);
            h.assertTrue(knife.is(TagKey.create(Registries.ITEM, ResourceLocation.parse("modern_ite:enchantable/extend"))),
                    "New metal knives support the same reach enchantment as existing knives: " + material);
            if (!HIGH_METALS.contains(material)) {
                h.assertTrue(manager.byKey(ResourceLocation.parse(namespace + material + "_knife"))
                                .map(holder -> holder.value().getType() == RecipeType.CRAFTING).orElse(false),
                        "Iron knives retain their direct starting recipe: " + material);
                var input = CraftingInput.of(2, 1, List.of(new ItemStack(ingot(material)), new ItemStack(Items.STICK)));
                var recipe = manager.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
                ItemStack result = recipe.value().assemble(input, h.getLevel().registryAccess());
                var rule = CraftingQuality.rule(recipe, result, input);
                h.assertTrue(result.is(knife.getItem()) && rule != null && rule.difficulty() > 0F,
                        "The distinct knife shape resolves to a timed knife recipe through normal lookup: " + material);
            }
            var repairMaterial = MaterialAnvilRules.material(knife);
            h.assertTrue(repairMaterial != null && material.equals(repairMaterial.name()) && repairMaterial.components() == 1,
                    "Material repair identifies each knife and its single component: " + material);
            knife.setDamageValue(knife.getMaxDamage() * 3 / 4);
            BlockState anvil = material.equals("iron") ? Blocks.ANVIL.defaultBlockState()
                    : BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mite:" + material + "_anvil")).defaultBlockState();
            var plan = MaterialAnvilRules.plan(player, anvil, knife,
                    new ItemStack(MaterialAnvilRules.repairItem(material)));
            h.assertTrue(plan != null && plan.allowed() && plan.consumed() == 1
                            && plan.output().is(knife.getItem()) && plan.output().getDamageValue() < knife.getDamageValue(),
                    "One matching nugget repairs each damaged knife through the existing material-anvil rules: " + material);
            var input = CraftingInput.of(2, 1, List.of(knife, knife.copy()));
            var repair = manager.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
            h.assertTrue(repair.value() instanceof RepairItemRecipe
                            && repair.value().assemble(input, h.getLevel().registryAccess()).is(knife.getItem()),
                    "The high-tier acquisition restriction keeps native two-item knife repair available: " + material);
        }
        h.succeed();
    }

    private static void assertPreserved(GameTestHelper h, ItemStack source, ItemStack result) {
        h.assertTrue(EquipmentDurability.quality(source) == EquipmentDurability.quality(result)
                        && source.getComponentsPatch().forget(ToolSmithingGameTests::intrinsic)
                        .equals(result.getComponentsPatch().forget(ToolSmithingGameTests::intrinsic)),
                "Quality, personal identity, growth and all addon components survive material changes");
        int damage = (int)Math.clamp(Math.round((double)source.getDamageValue() * result.getMaxDamage() / source.getMaxDamage()),
                0L, Math.max(result.getMaxDamage() - 1L, 0L));
        h.assertTrue(result.getDamageValue() == damage, "Material upgrades preserve proportional durability wear");
    }

    private static boolean intrinsic(DataComponentType<?> type) {
        return type == DataComponents.MAX_DAMAGE || type == DataComponents.DAMAGE || type == DataComponents.MAX_STACK_SIZE
                || type == DataComponents.ATTRIBUTE_MODIFIERS || type == DataComponents.TOOL || type == DataComponents.FIRE_RESISTANT;
    }

    private static RecipeHolder<?> fixture(String path, Item result) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("modern_ite", "tool_smithing_test/" + path),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(result),
                        NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.COBBLESTONE))));
    }

    private static List<MaterialUpgradeRecipes.Upgrade> toolRoutes() {
        var routes = new ArrayList<>(MaterialUpgradeRecipes.smithingUpgrades());
        for (int index = 0; index < CHAIN.size() - 1; index++) {
            String from = CHAIN.get(index), to = CHAIN.get(index + 1);
            Item target = item((to.equals("vibranium") ? "mite:" : "modern_ite:") + to + "_knife");
            routes.add(new MaterialUpgradeRecipes.Upgrade("modern_ite:equipment_upgrade/" + from + "_knife_to_" + to,
                    from, to, item("modern_ite:" + from + "_knife"), ingot(to), target));
        }
        return routes;
    }

    private static ItemStack template() { return new ItemStack(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()); }
    private static Item ingot(String material) { return item((material.equals("iron") ? "minecraft:" : "mite:") + material + "_ingot"); }
    private static Item item(String id) { return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).filter(item -> item != Items.AIR).orElseThrow(); }

    private static final class SmithingSession implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final SmithingMenu menu;
        private final BlockPos table;
        private final BlockState previous;

        private SmithingSession(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "tool-smithing-ui"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            previous = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, SmithingTableTier.VIBRANIUM.block().defaultBlockState());
            menu = new SmithingMenu(38, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
        }

        @Override public void close() {
            try {
                TimedSmithingController.cancel(player);
                for (int slot = 0; slot < 3; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
                menu.setCarried(ItemStack.EMPTY);
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, previous);
                channel.finishAndReleaseAll();
            }
        }
    }
}
