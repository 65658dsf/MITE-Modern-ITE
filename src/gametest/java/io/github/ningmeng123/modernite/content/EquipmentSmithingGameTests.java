package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import io.github.ningmeng123.mite.data.MaterialUpgradeRecipes;
import io.github.ningmeng123.mite.integration.jei.SmithingJeiRecipes;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemSockets;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
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
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Armor and shields share the Extreme material chain and exact executable smithing costs. @author NingMeng123 */
@GameTestHolder("modern_ite_tool_smithing")
@PrefixGameTestTemplate(false)
public final class EquipmentSmithingGameTests {
    private static final List<String> CHAIN = List.of("iron", "ancient_metal", "mithril", "adamantium", "vibranium");
    private static final Set<String> UPGRADED_METALS = Set.of("ancient_metal", "mithril", "adamantium", "vibranium");
    private static final List<String> ARMOR_SLOTS = List.of("helmet", "chestplate", "leggings", "boots");
    private static final List<Integer> ARMOR_COSTS = List.of(5, 8, 7, 4);
    private static final List<Integer> VIBRANIUM_COSTS = List.of(4, 7, 6, 3);

    private EquipmentSmithingGameTests() {}

    @GameTest(template = "gear_empty")
    public static void armorAndShieldCatalogsKeepEveryAvailableSingleMaterialStage(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        int checked = 0;
        boolean chainmail = false, horse = false, shield = false;
        for (var route : MaterialUpgradeRecipes.smithingUpgrades()) {
            if (!CHAIN.contains(route.fromMaterial()) || !UPGRADED_METALS.contains(route.toMaterial())) continue;
            var armor = MiteArmorContent.definition(route.result());
            var shieldDefinition = MiteShields.definition(new ItemStack(route.result()));
            if (armor == null && shieldDefinition == null) continue;
            chainmail |= armor != null && armor.chainmail();
            horse |= armor != null && armor.horse();
            shield |= shieldDefinition != null;
            h.assertTrue(CHAIN.indexOf(route.toMaterial()) == CHAIN.indexOf(route.fromMaterial()) + 1,
                    "The catalog advances one material at a time: " + route.id());
            ItemStack source = personalEquipment(route.base());
            ItemStack before = source.copy();
            int cost = MaterialUpgradeRequirements.ingots(new ItemStack(route.result()));
            h.assertTrue(!route.toMaterial().equals("ancient_metal") || cost == 2,
                    "Every iron armor, chainmail, horse-armor and shield upgrade uses exactly two ancient-metal ingots: " + route.id());
            var input = new SmithingRecipeInput(template(), source, new ItemStack(route.addition(), cost));
            var selected = manager.getRecipeFor(RecipeType.SMITHING, input, h.getLevel()).orElseThrow();
            h.assertTrue(selected.id().equals(ResourceLocation.parse(route.id())), "The native recipe manager selects the intended equipment route: " + route.id());
            ItemStack output = selected.value().assemble(input, h.getLevel().registryAccess());
            h.assertTrue(output.is(route.result()) && output.getCount() == 1, "One previous-material equipment item becomes one next-material item");
            assertPreserved(h, before, output);
            h.assertTrue(ItemStack.matches(source, before), "A preview cannot consume or mutate personal equipment");
            for (String material : CHAIN) {
                var candidate = new SmithingRecipeInput(template(), source, new ItemStack(ingot(material), 64));
                h.assertTrue(manager.getRecipeFor(RecipeType.SMITHING, candidate, h.getLevel()).isPresent() == material.equals(route.toMaterial()),
                        "Armor and shields reject skipped, reversed and same-material additions: " + route.id() + " / " + material);
            }
            h.assertTrue(manager.getRecipeFor(RecipeType.SMITHING,
                    new SmithingRecipeInput(ItemStack.EMPTY, source, new ItemStack(route.addition(), cost)), h.getLevel()).isEmpty(),
                    "Every equipment material upgrade needs the shared template");
            checked++;
        }
        h.assertTrue(checked >= 20 && chainmail && horse && shield, "The audit includes normal armor, chainmail, horse armor and shields throughout their available chains");
        for (String slot : ARMOR_SLOTS) {
            ItemStack current = personalEquipment(item("minecraft:iron_" + slot));
            for (String material : CHAIN.subList(1, CHAIN.size())) {
                Item target = item("mite:" + material + "_" + slot);
                int cost = MaterialUpgradeRequirements.ingots(new ItemStack(target));
                var input = new SmithingRecipeInput(template(), current, new ItemStack(ingot(material), cost));
                ItemStack result = manager.getRecipeFor(RecipeType.SMITHING, input, h.getLevel()).orElseThrow()
                        .value().assemble(input, h.getLevel().registryAccess());
                h.assertTrue(result.is(target), "Each ordinary armor slot follows the complete four-stage chain: " + slot + " / " + material);
                assertPreserved(h, current, result);
                current = result;
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void ingredientCountsAndJeiAgreeWithOriginalArmorShapesAndTwoIngotAncientMetal(GameTestHelper h) {
        var pages = SmithingJeiRecipes.index(h.getLevel()).recipes();
        for (String material : List.of("mithril", "adamantium", "vibranium")) {
            List<Integer> expected = material.equals("vibranium") ? VIBRANIUM_COSTS : ARMOR_COSTS;
            for (int index = 0; index < ARMOR_SLOTS.size(); index++) {
                Item armor = item("mite:" + material + "_" + ARMOR_SLOTS.get(index));
                int cost = expected.get(index);
                h.assertTrue(MaterialUpgradeRequirements.ingots(new ItemStack(armor)) == cost,
                        "Smithing retains the authored material count for each ordinary armor shape: " + material + " / " + ARMOR_SLOTS.get(index));
                var matching = pages.stream().filter(page -> page.output().is(armor)).toList();
                h.assertTrue(matching.size() == 1 && matching.getFirst().additions().size() == 1
                                && matching.getFirst().additions().getFirst().is(ingot(material))
                                && matching.getFirst().additions().getFirst().getCount() == cost,
                        "JEI displays exactly the executable counted ingot payment: " + material + " / " + ARMOR_SLOTS.get(index));
            }
        }
        for (var route : MaterialUpgradeRecipes.smithingUpgrades()) {
            if (!route.fromMaterial().equals("iron") || !route.toMaterial().equals("ancient_metal")) continue;
            ItemStack output = new ItemStack(route.result());
            if (MiteTools.definition(output) == null && MiteArmorContent.definition(route.result()) == null
                    && MiteShields.definition(output) == null) continue;
            h.assertTrue(MaterialUpgradeRequirements.ingots(output) == 2,
                    "The iron-to-ancient-metal payment is uniformly two ingots across tools, armor and shields: " + route.id());
            var page = pages.stream().filter(value -> value.source().id().equals(ResourceLocation.parse(route.id()))).findFirst().orElseThrow();
            h.assertTrue(page.additions().size() == 1 && page.additions().getFirst().getCount() == 2,
                    "The recipe viewer uses the same uniform ancient-metal payment: " + route.id());
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void nativeSmithingPaysFourForVibraniumHelmetAndSevenForChestplateAtCompletion(GameTestHelper h) {
        try (SmithingSession session = new SmithingSession(h)) {
            for (String slot : List.of("helmet", "chestplate")) {
                session.clear();
                boolean shift = slot.equals("chestplate");
                int cost = shift ? 7 : 4;
                int slotPayment = Math.min(cost, 4), reserve = cost - slotPayment;
                Item target = item("mite:vibranium_" + slot);
                ItemStack source = personalEquipment(item("mite:adamantium_" + slot));
                ItemStack before = source.copy();
                if (reserve > 0) session.player.getInventory().setItem(0, new ItemStack(ingot("vibranium"), reserve - 1));
                session.player.getInventory().setItem(1, new ItemStack(Items.IRON_NUGGET, 3));
                session.menu.getSlot(0).set(template());
                session.menu.getSlot(1).set(source);
                session.menu.getSlot(2).set(new ItemStack(ingot("vibranium"), reserve > 0 ? slotPayment : slotPayment - 1));
                h.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "A payment one ingot below the authored count has no smithing result: " + slot);
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                h.assertTrue(session.menu.quickMoveStack(session.player, 3).isEmpty() && !TimedSmithingController.isActive(session.player),
                        "Underfunded normal and shift-click attempts cannot start an armor upgrade");
                if (reserve > 0) session.player.getInventory().setItem(0, new ItemStack(ingot("vibranium"), reserve));
                session.menu.getSlot(2).set(new ItemStack(ingot("vibranium"), slotPayment));
                h.assertTrue(session.menu.getSlot(3).getItem().is(target), "Legal input stacks plus the exact matching inventory reserve create the armor preview");
                assertPreserved(h, before, session.menu.getSlot(3).getItem());
                int period = TimedSmithingController.period(session.player, session.menu);
                if (shift) session.menu.quickMoveStack(session.player, 3);
                else session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                h.assertTrue(period >= 25 && TimedSmithingController.isActive(session.player), "The real menu starts a full manufacturing period");
                for (int tick = 1; tick < period; tick++) TimedSmithingController.tick(session.player);
                h.assertTrue(session.menu.getCarried().isEmpty() && session.player.getInventory().countItem(target) == 0
                                && ItemStack.matches(session.menu.getSlot(1).getItem(), before)
                                && session.menu.getSlot(0).getItem().getCount() == 1 && session.menu.getSlot(2).getItem().getCount() == slotPayment
                                && session.player.getInventory().countItem(ingot("vibranium")) == reserve,
                        "Before the final tick the source, template, slot ingots and reserved ingots remain untouched");
                TimedSmithingController.tick(session.player);
                ItemStack output = shift ? session.player.getInventory().items.stream().filter(stack -> stack.is(target)).findFirst().orElseThrow()
                        : session.menu.getCarried();
                h.assertTrue(output.is(target) && output.getCount() == 1 && session.menu.getSlot(0).getItem().isEmpty()
                                && session.menu.getSlot(1).getItem().isEmpty() && session.menu.getSlot(2).getItem().isEmpty()
                                && session.player.getInventory().countItem(ingot("vibranium")) == 0
                                && session.player.getInventory().countItem(Items.IRON_NUGGET) == 3,
                        "Completion consumes exactly four or seven vibranium ingots and one template/source while retaining unrelated resources");
                assertPreserved(h, before, output);
                h.assertTrue(session.menu.quickMoveStack(session.player, 3).isEmpty()
                                && session.player.getInventory().countItem(target) == (shift ? 1 : 0),
                        "A paid armor upgrade cannot duplicate its delivered result");
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void armorShieldCraftingAndTheFormerVibraniumWorkbenchUpgradesAreUnavailableToPlayersAndCrafters(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING))
            h.assertTrue(!advancedEquipment(holder.value().getResultItem(h.getLevel().registryAccess())),
                    "The crafting index cannot produce ancient-metal or higher tools, armor or shields: " + holder.id());
        for (var holder : WorkbenchJeiRecipes.index(h.getLevel()).originals())
            h.assertTrue(!advancedEquipment(holder.value().getResultItem(h.getLevel().registryAccess())),
                    "JEI has no workbench equipment page that bypasses material smithing: " + holder.id());
        for (var holder : WorkbenchJeiRecipes.groupedIndex(h.getLevel()).originals())
            h.assertTrue(!advancedEquipment(holder.value().getResultItem(h.getLevel().registryAccess())),
                    "The grouped JEI workbench index also omits every controlled equipment result: " + holder.id());
        BlockPos position = new BlockPos(1, 1, 1);
        h.setBlock(position, Blocks.CRAFTER);
        var crafter = (CrafterBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(position));
        List<String[]> directPatterns = List.of(new String[]{"III", "I I"}, new String[]{"I I", "III", "III"},
                new String[]{"III", "I I", "I I"}, new String[]{"I I", "I I"});
        List<String[]> upgradePatterns = List.of(new String[]{"IAI", "I I"}, new String[]{"I I", "IAI", "III"},
                new String[]{"IAI", "I I", "I I"}, new String[]{"A I", "I I"});
        for (String material : UPGRADED_METALS) {
            for (int index = 0; index < ARMOR_SLOTS.size(); index++) {
                assertBlockedShape(h, crafter, directPatterns.get(index), material, ItemStack.EMPTY);
                if (material.equals("vibranium"))
                    assertBlockedShape(h, crafter, upgradePatterns.get(index), material,
                            new ItemStack(item("mite:adamantium_" + ARMOR_SLOTS.get(index))));
            }
        }
        h.succeed();
    }

    private static void assertBlockedShape(GameTestHelper h, CrafterBlockEntity crafter, String[] pattern, String material, ItemStack base) {
        List<ItemStack> inputs = new ArrayList<>();
        crafter.clearContent();
        for (int row = 0; row < pattern.length; row++) for (int column = 0; column < 3; column++) {
            ItemStack stack = switch (pattern[row].charAt(column)) {
                case 'I' -> new ItemStack(ingot(material));
                case 'A' -> base.copy();
                default -> ItemStack.EMPTY;
            };
            inputs.add(stack);
            crafter.setItem(row * 3 + column, stack.copy());
        }
        var input = CraftingInput.of(3, pattern.length, inputs);
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).isEmpty()
                        && CrafterBlock.getPotentialResults(h.getLevel(), crafter.asCraftInput()).isEmpty(),
                "The old armor shape cannot manufacture through player or automatic crafting: " + material + " / " + String.join("/", pattern));
    }

    private static boolean advancedEquipment(ItemStack stack) {
        var armor = MiteArmorContent.definition(stack.getItem());
        var shield = MiteShields.definition(stack);
        var tool = MiteTools.definition(stack);
        var simply = SimplySwordsCompat.definition(stack);
        return armor != null && UPGRADED_METALS.contains(armor.material())
                || shield != null && UPGRADED_METALS.contains(shield.material())
                || tool != null && UPGRADED_METALS.contains(tool.material())
                || simply != null && UPGRADED_METALS.contains(simply.material());
    }

    private static ItemStack personalEquipment(Item item) {
        ItemStack stack = new ItemStack(item);
        EquipmentDurability.setQuality(stack, Quality.FINE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 inherited equipment"));
        stack.set(DataComponents.REPAIR_COST, 7);
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 3, 123_456L, Map.of(), 1));
        stack.set(ExtremeGear.SOCKETS.get(), new GemSockets(List.of(1, 12, 18, 24, 30, 36, 42, 60)));
        stack.set(ExtremeGear.FORGE_GRADE.get(), 2);
        if (stack.isDamageableItem()) stack.setDamageValue(stack.getMaxDamage() / 3);
        return stack;
    }

    private static void assertPreserved(GameTestHelper h, ItemStack source, ItemStack result) {
        h.assertTrue(EquipmentDurability.quality(source) == EquipmentDurability.quality(result)
                        && source.getComponentsPatch().forget(EquipmentSmithingGameTests::intrinsic)
                        .equals(result.getComponentsPatch().forget(EquipmentSmithingGameTests::intrinsic)),
                "Material changes preserve quality, custom name, repair history, growth identity, gems and forge grade");
        if (source.getMaxDamage() > 0) {
            int damage = (int)Math.clamp(Math.round((double)source.getDamageValue() * result.getMaxDamage() / source.getMaxDamage()),
                    0L, Math.max(result.getMaxDamage() - 1L, 0L));
            h.assertTrue(result.getDamageValue() == damage, "Equipment wear retains its previous proportion using the destination durability");
        }
    }

    private static boolean intrinsic(DataComponentType<?> type) {
        return type == DataComponents.MAX_DAMAGE || type == DataComponents.DAMAGE || type == DataComponents.MAX_STACK_SIZE
                || type == DataComponents.ATTRIBUTE_MODIFIERS || type == DataComponents.TOOL || type == DataComponents.FIRE_RESISTANT;
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
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "armor-smithing-ui"), false);
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
            helper.getLevel().setBlockAndUpdate(table, Blocks.SMITHING_TABLE.defaultBlockState());
            menu = new SmithingMenu(39, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
        }

        private void clear() {
            TimedSmithingController.cancel(player);
            for (int slot = 0; slot < 3; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().clearContent();
        }

        @Override public void close() {
            try {
                clear();
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, previous);
                channel.finishAndReleaseAll();
            }
        }
    }
}
