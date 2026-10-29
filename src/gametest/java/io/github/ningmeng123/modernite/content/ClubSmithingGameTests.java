package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemSockets;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real native smithing transactions preserve club identity while advancing exactly one material. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ClubSmithingGameTests {
    private record Route(String from, String to, String recipe) {}
    private static final List<Route> ROUTES = List.of(
            new Route("silver", "iron", "equipment_upgrade/club_silver_to_iron"),
            new Route("gold", "iron", "equipment_upgrade/club_gold_to_iron"),
            new Route("iron", "ancient_metal", "club_ancient_metal"),
            new Route("ancient_metal", "mithril", "club_mithril"),
            new Route("mithril", "adamantium", "club_adamantium"),
            new Route("adamantium", "vibranium", "club_vibranium"));
    private static final List<String> MATERIALS = List.of("copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium");
    private ClubSmithingGameTests() {}

    @GameTest(template = "gear_empty")
    public static void exactlySixClubRoutesLoadAsNativeSmithingWithoutHighTierCraftingBypasses(GameTestHelper h) {
        Set<ResourceLocation> expected = new HashSet<>();
        for (Route route : ROUTES) {
            expected.add(id("modern_ite:" + route.recipe()));
            var recipe = recipe(h, route);
            h.assertTrue(recipe.getSerializer() == RecipeSerializer.SMITHING_TRANSFORM, "Club upgrades use the native smithing recipe and network format");
            ItemStack source = new ItemStack(club(route.from())), before = source.copy();
            var input = input(source, ingot(route.to()));
            h.assertTrue(recipe.matches(input, h.getLevel()), "Each authored route accepts the shared upgrade template and its next metal");
            var selected = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, h.getLevel()).orElseThrow();
            h.assertTrue(selected.id().equals(id("modern_ite:" + route.recipe())), "The real recipe manager selects the intended one-stage club upgrade");
            ItemStack output = selected.value().assemble(input, h.getLevel().registryAccess());
            h.assertTrue(output.is(club(route.to())) && output.getCount() == 1, "One old club becomes exactly one next-material club");
            assertMaterialDefaults(h, output, new ItemStack(club(route.to())));
            h.assertTrue(!output.has(GearProgression.PROGRESS.get()) && ItemStack.matches(source, before),
                    "A fresh preview invents no growth identity and never changes the source");
        }
        var actual = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.SMITHING).stream()
                .filter(holder -> isClub(holder.value().getResultItem(h.getLevel().registryAccess())))
                .map(holder -> holder.id()).collect(java.util.stream.Collectors.toSet());
        h.assertTrue(actual.equals(expected), "The loaded club family has exactly six smithing routes; copper upgrades only at a workbench");
        for (String material : MATERIALS) {
            boolean directlyCrafted = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .anyMatch(holder -> holder.value().getResultItem(h.getLevel().registryAccess()).is(club(material)));
            h.assertTrue(directlyCrafted == Set.of("copper", "silver", "gold", "iron").contains(material),
                    "Only the four initial metals retain direct crafting; high tiers cannot bypass smithing: " + material);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void allClubMaterialsRejectMissingWrongSkippedReversedAndTopTierUpgrades(GameTestHelper h) {
        for (String from : MATERIALS) {
            String allowed = ROUTES.stream().filter(route -> route.from().equals(from)).map(Route::to).findFirst().orElse(null);
            for (String addition : MATERIALS) {
                ItemStack base = new ItemStack(club(from));
                var selected = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input(base, ingot(addition)), h.getLevel());
                h.assertTrue(selected.isPresent() == addition.equals(allowed),
                        "Only the immediate authorized material works, including no upgrade beyond vibranium: " + from + " / " + addition);
            }
        }
        for (Route route : ROUTES) {
            var recipe = recipe(h, route);
            ItemStack base = new ItemStack(club(route.from())), addition = new ItemStack(ingot(route.to()), 4);
            for (var invalid : List.of(new SmithingRecipeInput(ItemStack.EMPTY, base, addition),
                    new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), base, addition),
                    new SmithingRecipeInput(new ItemStack(Items.PAPER), base, addition),
                    new SmithingRecipeInput(template(), ItemStack.EMPTY, addition),
                    new SmithingRecipeInput(template(), base, ItemStack.EMPTY),
                    new SmithingRecipeInput(template(), base, new ItemStack(Items.STICK))))
                h.assertTrue(!recipe.matches(invalid, h.getLevel())
                        && h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, invalid, h.getLevel()).isEmpty(),
                        "Missing or wrong ingredients cannot match a club upgrade or another smithing recipe");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void allSixUpgradesPreserveEveryPersonalComponentAndConvertWearToTargetDefaults(GameTestHelper h) {
        for (Route route : ROUTES) {
            ItemStack source = personalClub(h, route.from());
            ItemStack before = source.copy();
            ItemStack output = recipe(h, route).assemble(input(source, ingot(route.to())), h.getLevel().registryAccess());
            assertPersonalComponents(h, before, output);
            ItemStack target = new ItemStack(club(route.to()));
            EquipmentDurability.setQuality(target, EquipmentDurability.quality(source));
            assertMaterialDefaults(h, output, target);
            assertProportionalWear(h, before, output);
            h.assertTrue(ItemStack.matches(source, before), "Preview keeps source components, damage and item count unchanged");
            ItemStack loaded = ItemStack.parse(h.getLevel().registryAccess(), output.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(ItemStack.matches(output, loaded), "All inherited components and target material defaults survive save/load together");
            ItemStack detached = output.copy();
            detached.set(ExtremeGear.FORGE_GRADE.get(), 0);
            detached.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY);
            detached.setDamageValue(0);
            h.assertTrue(ItemStack.matches(source, before), "Changing an independent result cannot mutate or clear source components");
        }
        ItemStack current = personalClub(h, "iron");
        GearProgress identity = GearProgression.state(current);
        for (var step : List.of(Map.entry("ancient_metal", 17), Map.entry("mithril", 17),
                Map.entry("adamantium", 17), Map.entry("vibranium", 17))) {
            String from = GearProgression.material(current);
            Route route = ROUTES.stream().filter(value -> value.from().equals(from) && value.to().equals(step.getKey())).findFirst().orElseThrow();
            ItemStack before = current.copy();
            current = recipe(h, route).assemble(input(current, ingot(route.to())), h.getLevel().registryAccess());
            assertPersonalComponents(h, before, current);
            assertProportionalWear(h, before, current);
            h.assertTrue(GearProgression.state(current).equals(identity) && GearProgression.maxLevel(current) == step.getValue(),
                    "Four consecutive smithing upgrades retain UUID, XP, levels and affixes while preserved forge grade two sets the growth cap to 17");
        }
        h.assertTrue(current.is(club("vibranium")), "The complete iron-to-vibranium smithing route ends at the intended top-tier club");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void timedSmithingPickupAndShiftClickConsumeOneTemplateAndExactLegalMaterialStacks(GameTestHelper h) {
        try (SmithingSession session = new SmithingSession(h)) {
            for (Route route : ROUTES) {
                session.clear();
                int required = route.to().equals("ancient_metal") ? 2 : 4;
                ItemStack source = personalClub(h, route.from()), before = source.copy();
                ItemStack templates = template(); templates.setCount(3);
                session.menu.getSlot(0).set(templates);
                session.menu.getSlot(1).set(source);
                session.menu.getSlot(2).set(new ItemStack(ingot(route.to()), required - 1));
                h.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "A payment below the target material cost cannot preview an upgrade");
                session.menu.getSlot(2).set(new ItemStack(ingot(route.to()), 4));
                h.assertTrue(session.menu.stillValid(session.player) && session.menu.getSlot(3).getItem().is(club(route.to())),
                        "Every route previews with a legal four-ingot stack and retains its full material cost");
                assertPersonalComponents(h, before, session.menu.getSlot(3).getItem());
                h.assertTrue(ItemStack.matches(source, before) && session.menu.getSlot(0).getItem().getCount() == 3
                        && session.menu.getSlot(2).getItem().getCount() == 4, "Preview consumes or modifies no input");
                int period = TimedSmithingController.period(session.player, session.menu);
                h.assertTrue(period >= 25, "Material upgrades require a real production period");
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                h.assertTrue(TimedSmithingController.isActive(session.player) && session.menu.getCarried().isEmpty(),
                        "Picking up the preview starts manufacturing rather than delivering an instant upgrade");
                for (int tick = 1; tick < period; tick++) TimedSmithingController.tick(session.player);
                h.assertTrue(TimedSmithingController.progress(session.player) == period - 1
                        && session.menu.getCarried().isEmpty() && session.player.getInventory().isEmpty()
                        && ItemStack.matches(session.menu.getSlot(1).getItem(), before)
                        && session.menu.getSlot(0).getItem().getCount() == 3 && session.menu.getSlot(2).getItem().getCount() == 4,
                        "No output or payment commits before the final production tick");
                TimedSmithingController.tick(session.player);
                ItemStack taken = session.menu.getCarried().copy();
                h.assertTrue(taken.is(club(route.to())) && taken.getCount() == 1 && session.menu.getSlot(1).getItem().isEmpty()
                        && session.menu.getSlot(0).getItem().getCount() == 2 && session.menu.getSlot(2).getItem().getCount() == 4 - required,
                        "Completed pickup consumes one source, one template and exactly the target material cost");
                assertPersonalComponents(h, before, taken); assertProportionalWear(h, before, taken);
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                h.assertTrue(ItemStack.matches(session.menu.getCarried(), taken) && session.menu.getSlot(3).getItem().isEmpty()
                        && session.menu.getSlot(0).getItem().getCount() == 2 && session.menu.getSlot(2).getItem().getCount() == 4 - required,
                        "A second pickup cannot retrieve the previous preview or consume another payment");
                session.menu.setCarried(ItemStack.EMPTY);
                h.assertTrue(session.player.getInventory().add(taken.copy()), "The first result enters the player inventory exactly once");
                session.menu.getSlot(2).set(new ItemStack(ingot(route.to()), 4));
                session.menu.getSlot(1).set(before.copy());
                ItemStack moved = session.menu.quickMoveStack(session.player, 3);
                h.assertTrue(moved.isEmpty() && TimedSmithingController.isActive(session.player)
                        && session.player.getInventory().countItem(club(route.to())) == 1,
                        "Shift-click starts another complete period without returning an immediate item");
                for (int tick = 0; tick < period; tick++) TimedSmithingController.tick(session.player);
                h.assertTrue(session.player.getInventory().countItem(club(route.to())) == 2
                        && session.menu.getSlot(1).getItem().isEmpty() && session.menu.getSlot(0).getItem().getCount() == 1
                        && session.menu.getSlot(2).getItem().getCount() == 4 - required,
                        "Completed shift-click consumes the full material cost from a fresh legal stack for exactly one item");
                for (ItemStack delivered : session.player.getInventory().items) if (delivered.is(club(route.to()))) {
                    assertPersonalComponents(h, before, delivered); assertProportionalWear(h, before, delivered);
                }
                h.assertTrue(session.menu.quickMoveStack(session.player, 3).isEmpty()
                        && session.player.getInventory().countItem(club(route.to())) == 2,
                        "An empty source slot cannot be shift-clicked into a duplicate output");
                h.assertTrue(session.player.totalExperience == 0, "Smithing inherited quality does not charge a second manufacturing XP cost");
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void changedOrMissingSmithingInputsInvalidateThePreviewWithoutChargingOrDuplicating(GameTestHelper h) {
        try (SmithingSession session = new SmithingSession(h)) {
            for (Route route : ROUTES) {
                session.clear();
                int required = route.to().equals("ancient_metal") ? 2 : 4;
                ItemStack source = personalClub(h, route.from()), before = source.copy();
                session.menu.getSlot(1).set(source);
                session.menu.getSlot(2).set(new ItemStack(ingot(route.to()), required - 1));
                h.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "The native menu cannot omit the shared upgrade template");
                session.menu.getSlot(0).set(template());
                h.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "The template cannot bypass a payment below the required material amount");
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                h.assertTrue(session.menu.quickMoveStack(session.player, 3).isEmpty() && !TimedSmithingController.isActive(session.player),
                        "Neither take path can begin an underfunded upgrade");
                session.menu.getSlot(2).set(new ItemStack(ingot(route.to()), 4));
                h.assertTrue(session.menu.getSlot(3).getItem().is(club(route.to())), "The matching template creates the intended preview");
                int period = TimedSmithingController.period(session.player, session.menu);
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                TimedSmithingController.tick(session.player);
                h.assertTrue(TimedSmithingController.isActive(session.player) && TimedSmithingController.progress(session.player) == 1,
                        "A fully funded transaction begins a real production session");
                session.menu.getSlot(2).set(new ItemStack(Items.STICK, 4));
                h.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "Changing the ingot immediately removes the old result");
                for (int tick = 0; tick < period; tick++) TimedSmithingController.tick(session.player);
                h.assertTrue(!TimedSmithingController.isActive(session.player), "Changing an ingredient cancels the unfinished production session");
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                h.assertTrue(session.menu.getCarried().isEmpty() && session.menu.quickMoveStack(session.player, 3).isEmpty()
                        && session.player.getInventory().isEmpty(), "Neither take path can retrieve a stale club or create a duplicate");
                h.assertTrue(session.menu.getSlot(0).getItem().getCount() == 1 && ItemStack.matches(session.menu.getSlot(1).getItem(), before)
                        && session.menu.getSlot(2).getItem().is(Items.STICK) && session.menu.getSlot(2).getItem().getCount() == 4,
                        "An invalid transaction preserves all inputs, including every equipment component");
            }
        }
        h.succeed();
    }

    private static ItemStack personalClub(GameTestHelper h, String material) {
        ItemStack source = new ItemStack(club(material));
        EquipmentDurability.setQuality(source, Quality.FINE);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 preserved club"));
        source.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("NingMeng123 smithing history"))));
        source.set(DataComponents.REPAIR_COST, 7);
        CompoundTag data = new CompoundTag(); data.putString("owner", "NingMeng123"); data.putInt("custom_revision", 23);
        source.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        var enchantments = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        source.enchant(enchantments.getHolderOrThrow(Enchantments.UNBREAKING), 7);
        source.enchant(enchantments.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id("modern_ite:crit"))), 3);
        source.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 4, 123_456L,
                Map.of(Enchantments.UNBREAKING.location(), 3, Enchantments.MENDING.location(), 2,
                        id("modern_ite:crit"), 2, id("missing:legacy_preserved_affix"), 231), 1));
        source.set(ExtremeGear.SOCKETS.get(), new GemSockets(List.of(1, 12, 18, 24, 30, 36, 42, 60)));
        source.set(ExtremeGear.GRINDINGS.get(), 3);
        source.set(ExtremeGear.FORGE_GRADE.get(), 2);
        source.set(ExtremeEnchantments.EMERGENCY_COOLDOWN.get(), 900);
        source.set(DataComponents.MAX_DAMAGE, 400);
        source.set(DataComponents.MAX_STACK_SIZE, 2);
        source.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        source.remove(DataComponents.TOOL); source.remove(DataComponents.FIRE_RESISTANT);
        source.setDamageValue(source.getMaxDamage() / 4);
        return source;
    }

    private static void assertPersonalComponents(GameTestHelper h, ItemStack source, ItemStack output) {
        h.assertTrue(EquipmentDurability.quality(output) == EquipmentDurability.quality(source), "Quality is preserved exactly once");
        h.assertTrue(Objects.equals(output.get(DataComponents.CUSTOM_NAME), source.get(DataComponents.CUSTOM_NAME))
                && Objects.equals(output.get(DataComponents.LORE), source.get(DataComponents.LORE))
                && Objects.equals(output.get(DataComponents.CUSTOM_DATA), source.get(DataComponents.CUSTOM_DATA))
                && Objects.equals(output.get(DataComponents.REPAIR_COST), source.get(DataComponents.REPAIR_COST))
                && Objects.equals(output.get(DataComponents.ENCHANTMENTS), source.get(DataComponents.ENCHANTMENTS)),
                "Names, lore, third-party data, prior repair history and ordinary enchantments all survive");
        h.assertTrue(GearProgression.state(output).equals(GearProgression.state(source)),
                "Growth UUID, level, XP and all ordinary, addon and unknown historical affix ranks survive without rerolling");
        h.assertTrue(Objects.equals(output.get(ExtremeGear.SOCKETS.get()), source.get(ExtremeGear.SOCKETS.get()))
                && Objects.equals(output.get(ExtremeGear.GRINDINGS.get()), source.get(ExtremeGear.GRINDINGS.get()))
                && Objects.equals(output.get(ExtremeGear.FORGE_GRADE.get()), source.get(ExtremeGear.FORGE_GRADE.get()))
                && Objects.equals(output.get(ExtremeEnchantments.EMERGENCY_COOLDOWN.get()), source.get(ExtremeEnchantments.EMERGENCY_COOLDOWN.get())),
                "All eight gem sockets, grinder count, independent forge grade and addon cooldown components survive");
        h.assertTrue(source.getComponentsPatch().forget(ClubSmithingGameTests::intrinsic)
                .equals(output.getComponentsPatch().forget(ClubSmithingGameTests::intrinsic)),
                "Every non-intrinsic custom component patch is preserved, rather than only an enumerated subset");
    }

    private static boolean intrinsic(DataComponentType<?> type) {
        return type == DataComponents.MAX_DAMAGE || type == DataComponents.DAMAGE || type == DataComponents.MAX_STACK_SIZE
                || type == DataComponents.ATTRIBUTE_MODIFIERS || type == DataComponents.TOOL || type == DataComponents.FIRE_RESISTANT;
    }

    private static void assertMaterialDefaults(GameTestHelper h, ItemStack output, ItemStack target) {
        h.assertTrue(Objects.equals(output.get(DataComponents.MAX_DAMAGE), target.get(DataComponents.MAX_DAMAGE))
                && output.getMaxStackSize() == target.getMaxStackSize()
                && Objects.equals(output.get(DataComponents.ATTRIBUTE_MODIFIERS), target.get(DataComponents.ATTRIBUTE_MODIFIERS))
                && Objects.equals(output.get(DataComponents.TOOL), target.get(DataComponents.TOOL))
                && Objects.equals(output.get(DataComponents.FIRE_RESISTANT), target.get(DataComponents.FIRE_RESISTANT)),
                "The destination material supplies durability base, stack limit, attack attributes, tool rules and fire resistance");
    }

    private static void assertProportionalWear(GameTestHelper h, ItemStack source, ItemStack output) {
        int expected = (int)Math.clamp(Math.round((double)source.getDamageValue() * output.getMaxDamage() / source.getMaxDamage()),
                0L, Math.max(output.getMaxDamage() - 1L, 0L));
        h.assertTrue(output.getDamageValue() == expected, "Damage keeps its previous proportion using the new quality-adjusted maximum durability");
    }

    private static SmithingTransformRecipe recipe(GameTestHelper h, Route route) {
        var holder = h.getLevel().getRecipeManager().byKey(id("modern_ite:" + route.recipe())).orElseThrow();
        h.assertTrue(holder.value() instanceof SmithingTransformRecipe, "A real native smithing-transform recipe loads at the authored club route ID");
        return (SmithingTransformRecipe)holder.value();
    }
    private static SmithingRecipeInput input(ItemStack source, Item ingot) { return new SmithingRecipeInput(template(), source, new ItemStack(ingot, 4)); }
    private static ItemStack template() { return new ItemStack(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()); }
    private static Item club(String material) { return item("modern_ite:club_" + material); }
    private static boolean isClub(ItemStack stack) { return !stack.isEmpty() && MATERIALS.stream().anyMatch(material -> stack.is(club(material))); }
    private static Item ingot(String material) { return item((Set.of("copper", "iron", "gold").contains(material) ? "minecraft:" : "mite:") + material + "_ingot"); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static Item item(String value) { return BuiltInRegistries.ITEM.getOptional(id(value)).filter(item -> item != Items.AIR).orElseThrow(); }

    private static final class SmithingSession implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final SmithingMenu menu;
        private final BlockPos table;
        private final BlockState previous;
        private SmithingSession(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "club-smithing-ui"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true); player.getInventory().clearContent(); PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            table = helper.absolutePos(new BlockPos(1, 1, 1)); previous = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, Blocks.SMITHING_TABLE.defaultBlockState());
            menu = new SmithingMenu(37, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
        }
        private void clear() {
            TimedSmithingController.cancel(player);
            for (int slot = 0; slot < 3; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
            menu.setCarried(ItemStack.EMPTY); player.getInventory().clearContent();
        }
        @Override public void close() {
            try {
                clear(); player.containerMenu = player.inventoryMenu; player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, previous); channel.finishAndReleaseAll();
            }
        }
    }
}
