package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exhausted lanterns use the same physical material-anvil transactions as equipment. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class LanternRepairGameTests {
    private static final BlockPos SITE = new BlockPos(1, 1, 1);
    private static final List<String> MATERIALS = List.of(
            "mithril", "adamantium", "vibranium");
    private static final List<String> REPAIR_METALS = List.of(
            "copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium");
    private static final List<Anvil> ANVILS = List.of(
            new Anvil("mite:copper_anvil", 4F), new Anvil("mite:silver_anvil", 4F),
            new Anvil("mite:golden_anvil", 4F), new Anvil("minecraft:anvil", 8F),
            new Anvil("minecraft:chipped_anvil", 8F), new Anvil("minecraft:damaged_anvil", 8F),
            new Anvil("mite:ancient_metal_anvil", 16F), new Anvil("mite:mithril_anvil", 64F),
            new Anvil("mite:adamantium_anvil", 256F), new Anvil("mite:vibranium_anvil", 512F));
    private static final float[] REQUIRED_CAPACITIES = {64F, 256F, 512F};
    private record Anvil(String id, float capacity) {
        BlockState state() { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState(); }
    }

    private LanternRepairGameTests() {}

    @GameTest(template = "gear_empty")
    public static void allRetainedExhaustedAndPartiallyWornLanternsRepairAndLightAgain(GameTestHelper h) {
        Player owner = player(h);
        try {
            for (String material : MATERIALS) for (boolean exhausted : new boolean[]{true, false}) {
                owner.getInventory().clearContent();
                ItemStack source = personalLantern(h, material);
                h.assertTrue(MaterialAnvilRules.material(source) == null,
                        "Anvil repair support cannot classify lanterns as general recyclable equipment: " + material);
                int maximum = source.getMaxDamage(), unit = maximum / 10;
                int damage = exhausted ? maximum : unit * 2 + unit / 2;
                source.setDamageValue(damage);
                owner.setItemInHand(InteractionHand.OFF_HAND, source);
                h.assertValueEqual(ExtremeLifeEvents.coreLevel(owner), exhausted ? 0 : 5,
                        "Only the exhausted source starts without light: " + material);
                owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                AnvilMenu menu = menu(h, owner, anvil(material));
                menu.getSlot(0).set(source);
                menu.getSlot(1).set(nuggets(material, 64));
                menu.setItemName(source.getHoverName().getString());
                ItemStack before = source.copy(), expected = source.copy();
                int consumed = exhausted ? 10 : 2;
                int expectedDamage = damage - consumed * unit;
                expected.setDamageValue(expectedDamage);
                h.assertTrue(ItemStack.matches(menu.getSlot(2).getItem(), expected),
                        "Real preview preserves every component and repairs by one tenth per nugget: " + material);
                h.assertTrue(ItemStack.matches(source, before) && menu.getSlot(1).getItem().getCount() == 64,
                        "Preview neither changes the lantern nor consumes its nuggets: " + material);
                h.assertValueEqual(menu.repairItemCountCost, consumed,
                        "Repair follows the equipment rule for an unequal final fraction: " + material);
                h.assertValueEqual(menu.getCost(), 0, "Material repair has the same zero XP fee as equipment");
                h.assertValueEqual(anvilDamage(h), 0, "A preview cannot wear the physical anvil");
                menu.clicked(2, 0, ClickType.PICKUP, owner);
                ItemStack taken = menu.getCarried().copy();
                h.assertTrue(ItemStack.matches(taken, expected) && menu.getSlot(0).getItem().isEmpty(),
                        "Normal pickup returns the exact restored lantern once: " + material);
                h.assertValueEqual(menu.getSlot(1).getItem().getCount(), 64 - consumed,
                        "Normal pickup spends only the quoted number of matching nuggets: " + material);
                h.assertValueEqual(anvilDamage(h), consumed * unit,
                        "Lantern repair wears the anvil by the durability actually restored: " + material);
                h.assertValueEqual(owner.totalExperience, 0, "Ordinary lantern repair cannot charge XP");
                owner.setItemInHand(InteractionHand.OFF_HAND, taken);
                h.assertValueEqual(ExtremeLifeEvents.coreLevel(owner), 5,
                        "An exhausted lantern resumes offhand illumination after repair: " + material);
                owner.tickCount = 10;
                ExtremeLifeEvents.tickLantern(owner);
                h.assertValueEqual(taken.getDamageValue(), expectedDamage + 2,
                        "A repaired lantern resumes the existing fuel consumption schedule: " + material);
            }
        } finally { owner.discard(); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void everyLanternRequiresAnEqualOrStrongerPhysicalAnvilIncludingWornIronStates(GameTestHelper h) {
        Player owner = player(h);
        try {
            for (int i = 0; i < MATERIALS.size(); i++) {
                String material = MATERIALS.get(i);
                h.assertValueEqual(WorkbenchRules.materialCapacity(material), REQUIRED_CAPACITIES[i],
                        "Each retained lantern requires its equipment material's anvil capacity");
                for (Anvil anvil : ANVILS) {
                    owner.getInventory().clearContent();
                    AnvilMenu menu = menu(h, owner, anvil.state());
                    int wearBefore = anvilDamage(h);
                    ItemStack source = lantern(material);
                    source.setDamageValue(source.getMaxDamage());
                    ItemStack resource = nuggets(material, 2);
                    ItemStack before = source.copy(), resourceBefore = resource.copy();
                    boolean allowed = anvil.capacity() >= REQUIRED_CAPACITIES[i];
                    var preview = MaterialAnvilRules.previewRepair(anvil.state(), source, resource);
                    h.assertTrue(preview != null && preview.allowed() == allowed,
                            "The repair preview enforces material capacity: " + material + " / " + anvil.id());
                    menu.getSlot(0).set(source);
                    menu.getSlot(1).set(resource);
                    h.assertTrue(menu.getSlot(2).hasItem() == allowed,
                            "The physical menu agrees with capacity rules: " + material + " / " + anvil.id());
                    if (!allowed) {
                        assertRejectedWithoutCost(h, owner, menu, before, resourceBefore, wearBefore);
                        continue;
                    }
                    int unit = source.getMaxDamage() / 10;
                    ItemStack expected = source.copy(); expected.setDamageValue(source.getMaxDamage() - unit * 2);
                    h.assertTrue(ItemStack.matches(menu.getSlot(2).getItem(), expected),
                            "Every eligible anvil offers the same two-nugget repair");
                    menu.clicked(2, 0, ClickType.PICKUP, owner);
                    h.assertTrue(ItemStack.matches(menu.getCarried(), expected)
                                    && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
                            "Every equal or stronger physical anvil completes the advertised repair");
                    h.assertValueEqual(anvilDamage(h) - wearBefore, unit * 2,
                            "The source wear is exact even on chipped and damaged iron anvils");
                }
            }
        } finally { owner.discard(); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void wrongMetalNuggetsIngotsAndPristineLanternsCannotConsumeAnything(GameTestHelper h) {
        Player owner = player(h);
        try {
            for (String material : MATERIALS) {
                List<ItemStack> invalid = new ArrayList<>();
                for (String wrong : REPAIR_METALS) if (!wrong.equals(material)) invalid.add(nuggets(wrong, 3));
                invalid.add(ingots(material, 3));
                invalid.add(ItemStack.EMPTY);
                for (ItemStack resource : invalid) {
                    AnvilMenu menu = menu(h, owner, anvil("vibranium"));
                    ItemStack source = personalLantern(h, material);
                    source.setDamageValue(source.getMaxDamage());
                    menu.getSlot(0).set(source);
                    menu.getSlot(1).set(resource);
                    menu.setItemName(source.getHoverName().getString());
                    assertRejectedWithoutCost(h, owner, menu, source.copy(), resource.copy(), 0);
                }
                AnvilMenu menu = menu(h, owner, anvil(material));
                ItemStack pristine = lantern(material), resource = nuggets(material, 3);
                menu.getSlot(0).set(pristine); menu.getSlot(1).set(resource);
                assertRejectedWithoutCost(h, owner, menu, pristine.copy(), resource.copy(), 0);
            }
        } finally { owner.discard(); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void shiftClickRepairsPreserveComponentsSupportRenamingAndCannotDuplicate(GameTestHelper h) {
        Player owner = player(h);
        try {
            for (String material : MATERIALS) {
                owner.getInventory().clearContent();
                AnvilMenu menu = menu(h, owner, anvil(material));
                ItemStack source = personalLantern(h, material);
                source.setDamageValue(source.getMaxDamage());
                int unit = source.getMaxDamage() / 10;
                ItemStack expected = source.copy();
                expected.setDamageValue(source.getMaxDamage() - 3 * unit);
                expected.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 repaired " + material));
                menu.getSlot(0).set(source); menu.getSlot(1).set(nuggets(material, 3));
                menu.setItemName(expected.getHoverName().getString());
                h.assertTrue(ItemStack.matches(menu.getSlot(2).getItem(), expected),
                        "Renaming changes only the name while repair preserves all other components: " + material);
                ItemStack moved = menu.quickMoveStack(owner, 2);
                h.assertTrue(ItemStack.matches(moved, expected), "Native quick-move returns the quoted repaired lantern");
                h.assertValueEqual(inventoryCount(owner, expected), 1, "Native quick-move puts exactly one repaired lantern in inventory");
                h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                                && menu.getSlot(2).getItem().isEmpty(),
                        "Shift-click spends the source lantern and exactly three nuggets");
                h.assertValueEqual(anvilDamage(h), 3 * unit, "Shift-click charges exact anvil wear even after the output slot empties");
                h.assertTrue(menu.quickMoveStack(owner, 2).isEmpty(), "A repeated quick-move cannot retrieve the previous preview");
                menu.clicked(2, 0, ClickType.PICKUP, owner);
                h.assertTrue(menu.getCarried().isEmpty() && inventoryCount(owner, expected) == 1,
                        "A later normal pickup also cannot duplicate the repaired lantern");
                h.assertValueEqual(anvilDamage(h), 3 * unit, "Failed repeat pickup cannot wear the anvil again");
                h.assertValueEqual(owner.totalExperience, 0, "The source material repair and rename retain equipment's XP behavior");
            }
        } finally { owner.discard(); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void changedInputsAndFullInventoryLeaveTheLanternNuggetsAndAnvilUntouched(GameTestHelper h) {
        Player owner = player(h);
        try {
            for (String material : MATERIALS) {
                owner.getInventory().clearContent();
                AnvilMenu menu = menu(h, owner, anvil(material));
                ItemStack source = lantern(material); source.setDamageValue(source.getMaxDamage());
                ItemStack resource = nuggets(material, 3);
                menu.getSlot(0).set(source); menu.getSlot(1).set(resource);
                h.assertTrue(menu.getSlot(2).hasItem(), "Matching ingredients first produce a valid repair preview");
                ItemStack wrong = nuggets("copper", 3);
                menu.getSlot(1).set(wrong);
                assertRejectedWithoutCost(h, owner, menu, source.copy(), wrong.copy(), 0);
                menu.getSlot(1).set(resource.copy());
                for (int slot = 0; slot < 36; slot++) owner.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                ItemStack before = menu.getSlot(0).getItem().copy(), resourceBefore = menu.getSlot(1).getItem().copy();
                h.assertTrue(menu.quickMoveStack(owner, 2).isEmpty(), "A full inventory prevents the real shift-click transfer");
                h.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), before)
                                && ItemStack.matches(menu.getSlot(1).getItem(), resourceBefore),
                        "A failed shift-click transfer cannot consume either repair input");
                h.assertValueEqual(anvilDamage(h), 0, "A failed shift-click transfer cannot wear the anvil");
                h.assertValueEqual(owner.totalExperience, 0, "A rejected repair cannot charge XP");
            }
        } finally { owner.discard(); }
        h.succeed();
    }

    private static void assertRejectedWithoutCost(GameTestHelper h, Player owner, AnvilMenu menu,
            ItemStack source, ItemStack resource, int wearBefore) {
        h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "An invalid repair has no output");
        menu.clicked(2, 0, ClickType.PICKUP, owner);
        h.assertTrue(menu.getCarried().isEmpty() && menu.quickMoveStack(owner, 2).isEmpty(),
                "Neither normal pickup nor shift-click can take an invalid or stale repair");
        h.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), source)
                        && ItemStack.matches(menu.getSlot(1).getItem(), resource),
                "A rejected repair leaves all source components and material counts untouched");
        h.assertValueEqual(anvilDamage(h), wearBefore, "A rejected repair cannot consume anvil durability");
        h.assertValueEqual(owner.totalExperience, 0, "A rejected repair cannot consume experience");
    }

    private static Player player(GameTestHelper h) {
        Player owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.moveTo(Vec3.atCenterOf(h.absolutePos(SITE)));
        PlayerExperience.setTotal(owner, 0);
        return owner;
    }

    private static AnvilMenu menu(GameTestHelper h, Player owner, BlockState state) {
        h.setBlock(SITE, Blocks.AIR);
        h.setBlock(SITE.below(), Blocks.STONE);
        h.getLevel().setBlockAndUpdate(h.absolutePos(SITE), state);
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(SITE)) instanceof MaterialAnvilBlockEntity,
                "The repair uses a real physical material anvil with persistent durability");
        return new AnvilMenu(1, owner.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(SITE)));
    }

    private static int anvilDamage(GameTestHelper h) {
        return ((MaterialAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SITE))).damage();
    }

    private static BlockState anvil(String material) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(material.equals("iron") ? "minecraft:anvil"
                : "mite:" + (material.equals("gold") ? "golden" : material) + "_anvil")).defaultBlockState();
    }

    private static ItemStack lantern(String material) {
        return stack("modern_ite:dynamic_core_" + material, 1);
    }

    private static ItemStack personalLantern(GameTestHelper h, String material) {
        ItemStack source = lantern(material);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 lantern " + material));
        source.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Preserve this lantern's history"))));
        source.set(DataComponents.REPAIR_COST, 7);
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        CompoundTag data = new CompoundTag(); data.putString("owner", "NingMeng123"); data.putInt("revision", 23);
        source.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        source.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING), 2);
        return source;
    }

    private static ItemStack nuggets(String material, int count) {
        return stack((material.equals("iron") || material.equals("gold") ? "minecraft:" : "mite:")
                + material + "_nugget", count);
    }

    private static ItemStack ingots(String material, int count) {
        return stack((material.equals("copper") || material.equals("iron") || material.equals("gold") ? "minecraft:" : "mite:")
                + material + "_ingot", count);
    }

    private static ItemStack stack(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count);
    }

    private static int inventoryCount(Player owner, ItemStack expected) {
        int count = 0;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            ItemStack item = owner.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(item, expected)) count += item.getCount();
        }
        return count;
    }
}
