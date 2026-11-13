package io.github.ningmeng123.modernite.enchantment;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.R196BookAnvil;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.modernite.ExtremeRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real extension policy through source tables and paid anvil transactions. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class LegacyEnchantmentParityGameTests {
    private LegacyEnchantmentParityGameTests() {}

    @GameTest(template = "gear_empty")
    public static void registeredExtensionUpgradesVibraniumEquipmentThroughPaidAnvilPickup(GameTestHelper helper) {
        helper.assertTrue(MiteExtensions.policy() instanceof ExtremeRules, "The actual registered extension supplies the material rules");
        try (var caps = new Caps()) {
            var upgrades = List.of(
                    new Upgrade("mite:vibranium_pickaxe", "minecraft:fortune", 4, 5),
                    new Upgrade("mite:vibranium_sword", "minecraft:looting", 4, 5),
                    new Upgrade("mite:vibranium_chestplate", "minecraft:thorns", 4, 5),
                    new Upgrade("mite:vibranium_sword", "modern_ite:conqueror", 6, 7));
            for (Upgrade upgrade : upgrades) try (var anvil = new AnvilSession(helper)) {
                var enchantment = enchantment(helper, upgrade.enchantment());
                ItemStack input = item(upgrade.item());
                input.enchant(enchantment, upgrade.previous());
                anvil.menu.getSlot(0).set(input);
                anvil.menu.getSlot(1).set(book(enchantment, upgrade.previous()));
                helper.assertValueEqual(level(anvil.menu.getSlot(2).getItem(), enchantment), upgrade.next(),
                        "The actual extension previews its vibranium-specific anvil limit for " + upgrade.enchantment());
                helper.assertValueEqual(anvil.menu.getCost(), 8000, "The extended enchantment operation quotes 8000 XP");
                anvil.menu.clicked(2, 0, ClickType.PICKUP, anvil.owner);
                helper.assertValueEqual(level(anvil.menu.getCarried(), enchantment), upgrade.next(),
                        "Paid native pickup delivers the selected vibranium rank for " + upgrade.enchantment());
                helper.assertValueEqual(anvil.owner.totalExperience, 2000, "The real result charges its fixed 8000 point fee once");
                var entity = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(anvil.pos);
                helper.assertValueEqual(entity.damage(), upgrade.next() * 100,
                        "Anvil wear uses the upgraded result's actual enchantment rank");
                helper.assertTrue(anvil.menu.getSlot(0).getItem().isEmpty() && anvil.menu.getSlot(1).getItem().isEmpty(),
                        "The paid result consumes one equipment item and one book");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void explicitCapsTakePriorityOverRegisteredVibraniumAnvilLimits(GameTestHelper helper) {
        try (var caps = new Caps()) {
            caps.set(List.of("minecraft:fortune=2", "modern_ite:conqueror=4"));
            for (Upgrade upgrade : List.of(
                    new Upgrade("mite:vibranium_pickaxe", "minecraft:fortune", 5, 2),
                    new Upgrade("mite:vibranium_sword", "modern_ite:conqueror", 7, 4))) {
                var enchantment = enchantment(helper, upgrade.enchantment());
                var plan = R196BookAnvil.plan(item(upgrade.item()), book(enchantment, upgrade.previous()));
                helper.assertTrue(plan != null && !plan.output().isEmpty(), "A configured lower limit still yields a usable result");
                helper.assertValueEqual(level(plan.output(), enchantment), upgrade.next(),
                        "Explicit configuration limits the real material-specific anvil result");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void registeredConflictExemptionRequiresAdamantiumOrVibraniumEquipment(GameTestHelper helper) {
        try (var caps = new Caps()) {
            var protection = enchantment(helper, "minecraft:protection");
            var fire = enchantment(helper, "minecraft:fire_protection");
            for (String material : List.of("adamantium", "vibranium")) {
                ItemStack armor = item("mite:" + material + "_chestplate");
                armor.enchant(protection, 4);
                var plan = R196BookAnvil.plan(armor, book(fire, 4));
                helper.assertTrue(plan != null && !plan.output().isEmpty(), "High-tier equipment receives the real conflict exemption");
                helper.assertValueEqual(level(plan.output(), protection), 4, "The existing protection remains on the result");
                helper.assertValueEqual(level(plan.output(), fire), 4, "The incoming conflicting protection also reaches the result");
            }
            ItemStack iron = new ItemStack(Items.IRON_CHESTPLATE);
            iron.enchant(protection, 4);
            helper.assertTrue(R196BookAnvil.plan(iron, book(fire, 4)).output().isEmpty(),
                    "Iron equipment retains the ordinary enchantment conflict");
            helper.assertTrue(R196BookAnvil.plan(book(protection, 4), book(fire, 4)).output().isEmpty(),
                    "Book merging retains the ordinary enchantment conflict");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void registeredVibraniumTableAllowsAllThreeDamageEnchantmentsTogether(GameTestHelper helper) {
        try (var caps = new Caps()) {
            var candidates = List.of(enchantment(helper, "minecraft:sharpness"), enchantment(helper, "minecraft:smite"),
                    enchantment(helper, "minecraft:bane_of_arthropods"));
            var selected = VanillaTableEnchanting.select(item("mite:vibranium_sword"), 30, new FixedRandom(.75F), candidates, 158);
            helper.assertValueEqual(selected.size(), 3, "The actual vibranium table path retains all three compatible damage candidates");
            for (var candidate : candidates)
                helper.assertTrue(selected.stream().anyMatch(result -> result.enchantment.equals(candidate)),
                        "Each of the three source damage enchantments appears in the vibranium result");
            var ordinary = VanillaTableEnchanting.select(new ItemStack(Items.IRON_SWORD), 30, new FixedRandom(.75F), candidates, 158);
            helper.assertValueEqual(ordinary.size(), 1, "The ordinary iron table path retains damage-enchantment mutual exclusion");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void registeredItePowerVariationUsesTheLegacyThreeQuarterCenter(GameTestHelper helper) {
        helper.assertValueEqual(R196EnchantmentTable.randomizedEnergy(1000, new FixedRandom(0F)), 625,
                "The source ITE lower endpoint is 0.625 times the quoted energy");
        helper.assertValueEqual(R196EnchantmentTable.randomizedEnergy(1000, new FixedRandom(.5F)), 875,
                "The source ITE midpoint produces 0.875 times the quoted energy");
        helper.assertValueEqual(R196EnchantmentTable.randomizedEnergy(1000, new FixedRandom(.75F)), 1000,
                "A random value of 0.75 is the neutral source ITE energy center");
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void bookCandidatesAndExtendTwoRemainAvailableThroughTheActualExtension(GameTestHelper helper) {
        try (var caps = new Caps()) {
            for (String id : List.of("modern_ite:crit", "modern_ite:emergency", "modern_ite:extend"))
                helper.assertTrue(VanillaTableEnchanting.primaryItem(new ItemStack(Items.BOOK), enchantment(helper, id)),
                        "The legacy public book-selection API retains its registered extension candidate " + id);
            var extend = enchantment(helper, "modern_ite:extend");
            helper.assertValueEqual(VanillaTableEnchanting.maximumLevel(new ItemStack(Items.IRON_PICKAXE), extend), 2,
                    "The source Extend definition exposes rank two with the default cap list");
            caps.set(List.of("modern_ite:extend=1"));
            helper.assertValueEqual(VanillaTableEnchanting.maximumLevel(new ItemStack(Items.IRON_PICKAXE), extend), 1,
                    "An explicit saved cap still controls the selected Extend rank");
        }
        helper.succeed();
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, String id) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id)));
    }

    private static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    private static int level(ItemStack stack, Holder<Enchantment> enchantment) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(enchantment);
    }

    private static ItemStack book(Holder<Enchantment> enchantment, int rank) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, rank);
        stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return stack;
    }

    private record Upgrade(String item, String enchantment, int previous, int next) {}

    private static final class FixedRandom extends LegacyRandomSource {
        private final float value;
        FixedRandom(float value) { super(0L); this.value = value; }
        @Override public float nextFloat() { return value; }
        @Override public int nextInt(int bound) { return 0; }
    }

    private static final class Caps implements AutoCloseable {
        private final List<? extends String> previous = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        private final MiteConfig.Snapshot effective = MiteConfig.effectiveSnapshot();
        private final boolean remote = effective != MiteConfig.localSnapshot();
        Caps() { MiteConfig.clearServerSnapshot(); set(List.of()); }
        void set(List<String> values) {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(values);
            MiteConfig.refreshEnchantmentRules();
        }
        @Override public void close() {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(previous);
            MiteConfig.refreshEnchantmentRules();
            if (remote) MiteConfig.receiveServerSnapshot(effective);
        }
    }

    private static final class AnvilSession implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos pos;
        final BlockState original;
        final BlockState below;
        final Player owner;
        final AnvilMenu menu;
        AnvilSession(GameTestHelper helper) {
            this.helper = helper;
            pos = helper.absolutePos(new BlockPos(2, 1, 2));
            original = helper.getLevel().getBlockState(pos);
            below = helper.getLevel().getBlockState(pos.below());
            helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
            owner = helper.makeMockPlayer(GameType.SURVIVAL);
            owner.moveTo(pos.getCenter());
            PlayerExperience.setTotal(owner, 10000);
            menu = new AnvilMenu(71, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        }
        @Override public void close() {
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            owner.getInventory().clearContent();
            helper.getLevel().setBlockAndUpdate(pos, original);
            helper.getLevel().setBlockAndUpdate(pos.below(), below);
        }
    }
}
