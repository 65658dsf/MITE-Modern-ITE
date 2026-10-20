package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual item, container, recipe and persistent forge integration. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class GearGameTests {
    private GearGameTests() {}
    private static GearStationEntity station(GameTestHelper h, boolean forge) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, (forge ? ExtremeGear.FORGES.get(3).get() : ExtremeGear.GEM_SETTING.get()).defaultBlockState());
        return (GearStationEntity)h.getLevel().getBlockEntity(pos);
    }
    @GameTest(template = "gear_empty")
    public static void takingSocketedEquipmentCannotAlsoTakeProjectedGems(GameTestHelper h) {
        var station = station(h, false);
        ItemStack gear = new ItemStack(Items.IRON_CHESTPLATE);
        station.setItem(0, gear); station.setItem(1, ExtremeGear.gem(GemType.HEALTH, 6));
        station.setItem(2, ExtremeGear.gem(GemType.HEALTH, 1));
        h.assertTrue(ExtremeGear.rank(gear, GemType.HEALTH) == 6, "Repeated types only use the best gem");
        ItemStack taken = station.removeItem(0, 1);
        h.assertTrue(station.getItem(1).isEmpty() && station.removeItem(1, 1).isEmpty(), "Projected gems disappear with the actual equipment");
        h.assertTrue(ExtremeGear.rank(taken, GemType.HEALTH) == 6, "Taking the item retains its gems");
        station.setItem(0, taken);
        ItemStack removedGem = station.removeItem(1, 1);
        h.assertTrue(removedGem.is(ExtremeGear.gem(GemType.HEALTH, 6).getItem()) && ExtremeGear.rank(taken, GemType.HEALTH) == 1,
                "Removing a gem consumes the matching component entry immediately");
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void protectionGemsFromAllFourArmorSlotsHaveIndependentModifiers(GameTestHelper h) {
        var identifiers = new java.util.HashSet<ResourceLocation>();
        ItemStack[] armor = {new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        double[] total = {0};
        for (int i = 0; i < armor.length; i++) {
            armor[i].set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, GemType.PROTECTION.ordinal() * 6 + 6));
            armor[i].forEachModifier(slots[i], (attribute, modifier) -> {
                if (attribute.equals(Attributes.ARMOR) && modifier.id().getNamespace().equals("modern_ite")) {
                    h.assertTrue(identifiers.add(modifier.id()), "Every worn piece has its own armor modifier ID");
                    total[0] += modifier.amount();
                }
            });
        }
        h.assertTrue(identifiers.size() == 4 && Math.abs(total[0] - 6) < .00001,
                "Four rank-six protection gems contribute all four independent armor bonuses");
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void socketsGrindingAndForgeGradeRoundTripTogether(GameTestHelper h) {
        ItemStack stack = new ItemStack(MiteTools.get("vibranium_sword"));
        stack.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, 6).with(7, 12));
        stack.set(ExtremeGear.GRINDINGS.get(), 3); stack.set(ExtremeGear.FORGE_GRADE.get(), 10);
        UUID id = GearProgression.ensureId(stack);
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(id, 28, 0, Map.of(), 1));
        ItemStack saved = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(ExtremeGear.rank(saved, GemType.HEALTH) == 6 && ExtremeGear.grade(saved) == 10, "Gem and forge components survive item transport");
        h.assertTrue(GearProgression.maxLevel(saved) == 25 && GearProgression.state(saved).level() == 28,
                "Forge grade sets the current cap without deleting historical over-cap growth or grinding data");
        h.assertTrue(saved.getOrDefault(ExtremeGear.GRINDINGS.get(), 0) == 3, "Historical grinding uses survive transport");
        ItemStack repaired = new ItemStack(saved.getItem()); GearProgression.preserve(saved, repaired);
        h.assertTrue(ExtremeGear.sockets(repaired).equals(ExtremeGear.sockets(saved)) && ExtremeGear.grade(repaired) == 10, "Baseline maintenance preserves addon components");
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void installedIteCapsFollowLegacyMaterialAndForgeRules(GameTestHelper h) {
        for (String material : new String[]{"copper", "silver", "rusted_iron", "mithril", "adamantium", "vibranium"}) {
            ItemStack tool = new ItemStack(MiteTools.get(material + "_sword"));
            h.assertTrue(GearProgression.maxLevel(tool) == 15, "Every supported weapon starts at 15: " + material);
            tool.set(ExtremeGear.GRINDINGS.get(), 3);
            h.assertTrue(GearProgression.maxLevel(tool) == 15, "Legacy grinding history cannot raise the cap");
            tool.set(ExtremeGear.FORGE_GRADE.get(), 10);
            h.assertTrue(GearProgression.maxLevel(tool) == 25, "Forge grade is added to weapon growth: " + material);
        }
        for (var entry : Map.of("copper", 6, "silver", 6, "rusted_iron", 6, "ancient_metal", 9,
                "mithril", 12, "adamantium", 15, "vibranium", 18).entrySet()) {
            ItemStack armor = new ItemStack(MiteArmorContent.get("mite:" + entry.getKey() + "_chestplate"));
            h.assertTrue(GearProgression.maxLevel(armor) == entry.getValue(), "Armor uses its material harvest level: " + entry.getKey());
            armor.set(ExtremeGear.FORGE_GRADE.get(), 10);
            armor.set(ExtremeGear.GRINDINGS.get(), 3);
            h.assertTrue(GearProgression.maxLevel(armor) == entry.getValue() + 10, "Grinding does not stack above the forged armor cap");
            if (entry.getKey().equals("vibranium")) {
                UUID id = GearProgression.ensureId(armor);
                armor.set(GearProgression.PROGRESS.get(), new GearProgress(id, 28, 0, Map.of(), 1));
                ItemStack loaded = ItemStack.parse(h.getLevel().registryAccess(), armor.save(h.getLevel().registryAccess())).orElseThrow();
                h.assertTrue(GearProgression.maxLevel(loaded) == 28 && GearProgression.state(loaded).level() == 28,
                        "The highest forged armor cap survives the baseline progress codec");
            }
        }
        h.assertTrue(GearProgression.maxLevel(new ItemStack(Items.IRON_CHESTPLATE)) == 9, "Iron harvest level is three");
        h.assertTrue(GearProgression.maxLevel(new ItemStack(Items.GOLDEN_CHESTPLATE)) == 6, "Gold harvest level is two");
        h.assertTrue(GearProgression.maxLevel(new ItemStack(Items.CHAINMAIL_CHESTPLATE)) == 9, "Chainmail shares its iron material");
        h.assertTrue(GearProgression.maxLevel(new ItemStack(Items.LEATHER_CHESTPLATE)) == 0, "Leather has no old-style harvest level");
        h.assertTrue(GearProgression.maxLevel(new ItemStack(Items.BOW)) == 15
                && GearProgression.maxLevel(new ItemStack(Items.SHIELD)) == 15, "Modern ranged weapons and shields use the non-armor rule");
        h.assertTrue(GearProgression.maxLevel(new ItemStack(Items.DIRT)) == 0
                && ExtremeGear.maximumGearLevel(new ItemStack(Items.DIRT), 25) == 0
                && ExtremeGear.maximumGearLevel(new ItemStack(Items.IRON_SWORD), 0) == 0,
                "Unsupported items cannot gain progression through the extension policy");
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void legacyGrindingBlockDoesNotConsumeItselfOrChangeEquipment(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        var block = ExtremeGear.GRINDER.get();
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        stack.set(ExtremeGear.GRINDINGS.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ItemStack before = stack.copy();
        block.useWithoutItem(block.defaultBlockState(), h.getLevel(), pos, player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        h.assertTrue(h.getLevel().getBlockState(pos).is(block), "The obsolete grinding operation preserves its block");
        h.assertTrue(ItemStack.matches(stack, before), "It does not consume equipment, write new history or allocate growth identity");
        h.assertTrue(GearProgression.maxLevel(stack) == 15, "A grinding interaction cannot raise the growth cap");
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void zeroGrowthLeatherRetainsGemSocketsAndMaintenance(GameTestHelper h) {
        ItemStack leather = new ItemStack(Items.LEATHER_CHESTPLATE);
        leather.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, GemType.HEALTH.ordinal() * 6 + 6));
        leather.set(ExtremeGear.GRINDINGS.get(), 2);
        h.assertTrue(GearProgression.maxLevel(leather) == 0 && ExtremeGear.socketable(leather),
                "Leather's zero legacy growth cap does not disable its gem sockets");
        ItemStack repaired = new ItemStack(Items.LEATHER_CHESTPLATE);
        GearProgression.preserve(leather, repaired);
        h.assertTrue(ExtremeGear.rank(repaired, GemType.HEALTH) == 6
                && repaired.getOrDefault(ExtremeGear.GRINDINGS.get(), 0) == 2,
                "Baseline maintenance preserves leather gems and historical grinding data at cap zero");
        h.assertTrue(!ExtremeGear.socketable(new ItemStack(Items.IRON_HORSE_ARMOR))
                && !ExtremeGear.socketable(new ItemStack(Items.DIRT)), "Horse armor and unknown items remain outside gem support");
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void forgeExperiencePolicyKeepsOldFloatAndWholePointAwards(GameTestHelper h) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        var policy = io.github.ningmeng123.mite.api.MiteExtensions.policy();
        stack.set(ExtremeGear.FORGE_GRADE.get(), 1);
        h.assertTrue(policy.gearExperience(stack, 1000) == 1000
                && policy.gearExperience(stack, 60_000) == 62_000,
                "Grade one truncates one-point awards and preserves the old float boundary below 63");
        stack.set(ExtremeGear.FORGE_GRADE.get(), 2);
        h.assertTrue(policy.gearExperience(stack, 1000) == 1000, "Grade two's fractional bonus is not stored as partial experience");
        stack.set(ExtremeGear.FORGE_GRADE.get(), 3);
        h.assertTrue(policy.gearExperience(stack, 90_000) == 116_000, "Grade three preserves the source float boundary below 117");
        stack.set(ExtremeGear.FORGE_GRADE.get(), 10);
        h.assertTrue(policy.gearExperience(stack, 1000) == 3000
                && policy.gearExperience(stack, 1999) == 3000, "Grade ten truncates raw and awarded whole points before returning millis");
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void protectionTomeConvertsOnlyGrowthAffixesAndPreservesOrdinaryEnchantments(GameTestHelper h) {
        ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
        var ordinary = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FIRE_PROTECTION);
        armor.enchant(ordinary, 2); armor.setDamageValue(23);
        EquipmentDurability.setQuality(armor, Quality.MASTERWORK);
        UUID id = GearProgression.ensureId(armor);
        armor.set(GearProgression.PROGRESS.get(), new GearProgress(id, GearProgression.maxLevel(armor), 713,
                Map.of(ResourceLocation.withDefaultNamespace("fire_protection"), 4, ResourceLocation.withDefaultNamespace("unbreaking"), 2), 1));
        var recipe = new ProtectionTomeRecipe(CraftingBookCategory.EQUIPMENT);
        var input = CraftingInput.of(2, 1, java.util.List.of(armor, new ItemStack(ExtremeGear.PROTECTION_TOME.get())));
        h.assertTrue(recipe.matches(input, h.getLevel()), "An already full armor item can convert an existing protection type");
        ItemStack result = recipe.assemble(input, h.getLevel().registryAccess());
        var progress = GearProgression.state(result);
        h.assertTrue(progress.level() == GearProgression.state(armor).level() && progress.experienceMillis() == 713,
                "Conversion does not consume another growth level or reset experience");
        h.assertTrue(progress.affixes().get(ResourceLocation.withDefaultNamespace("protection")) == 4
                && !progress.affixes().containsKey(ResourceLocation.withDefaultNamespace("fire_protection")), "The highest old protection rank is converted");
        h.assertTrue(result.get(DataComponents.ENCHANTMENTS).equals(armor.get(DataComponents.ENCHANTMENTS)) && result.getDamageValue() == 23,
                "Normal enchantments and actual wear are preserved");
        h.assertTrue(!recipe.matches(CraftingInput.of(2, 1, java.util.List.of(result, new ItemStack(ExtremeGear.PROTECTION_TOME.get()))), h.getLevel()),
                "Protection cannot be added twice");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = new CraftingMenu(0, player.getInventory());
        var grid = (CraftingContainer)menu.getSlot(1).container;
        grid.setItem(0, armor); grid.setItem(1, new ItemStack(ExtremeGear.PROTECTION_TOME.get()));
        RecipeHolder<CraftingRecipe> holder = new RecipeHolder<>(ExtremeGear.id("apply_protection_tome"), recipe);
        var manufactured = CraftingQuality.resolve(player, menu, grid, holder, result);
        h.assertTrue(manufactured != null && manufactured.fixed() && manufactured.exempt() && manufactured.cost() == 0
                && EquipmentDurability.quality(manufactured.stack()) == EquipmentDurability.quality(armor),
                "Actual timed crafting treats tome application as fixed-quality, XP-exempt maintenance");
        h.succeed();
    }
    private static GearStationEntity preparedForge(GameTestHelper h) {
        var station = station(h, true);
        station.setItem(5, new ItemStack(Items.IRON_SWORD)); station.setItem(6, new ItemStack(MiteTools.get("vibranium_axe")));
        station.setItem(7, new ItemStack(MiteTools.get("vibranium_war_hammer")));
        station.setItem(0, new ItemStack(Items.GOLD_INGOT)); station.setItem(1, new ItemStack(Items.IRON_INGOT));
        station.setItem(2, new ItemStack(ExtremeGear.STONES.get(0).get())); return station;
    }
    @GameTest(template = "gear_empty")
    public static void savedForgeSuccessConsumesMaterialsAndLocksSlots(GameTestHelper h) {
        var station = preparedForge(h); station.start();
        h.assertTrue(station.running() && station.removeItem(5, 1).isEmpty() && !station.canPlaceItem(0, new ItemStack(Items.DIRT)), "Active operations lock equipment and material slots");
        var saved = station.saveWithoutMetadata(h.getLevel().registryAccess()); saved.putBoolean("doomed", false);
        station.loadWithComponents(saved, h.getLevel().registryAccess());
        for (int i = 0; i < 2500 && station.running(); i++) GearStationEntity.serverTick(h.getLevel(), station.getBlockPos(), station.getBlockState(), station);
        h.assertTrue(station.getItem(5).isEmpty() && ExtremeGear.grade(station.getItem(8)) == 1, "Successful saved operation produces one actual upgraded item");
        h.assertTrue(station.getItem(0).isEmpty() && station.getItem(1).isEmpty() && station.getItem(2).isEmpty(), "Materials are consumed exactly once");
        h.assertTrue(station.getItem(6).getDamageValue() == 2048 && station.getItem(7).getDamageValue() == 2048, "Source tool durability costs are paid");
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void forgeFailureKeepsGradeAndCostsRemainingDurability(GameTestHelper h) {
        var station = preparedForge(h); ItemStack target = station.getItem(5); target.setDamageValue(100);
        int remaining = target.getMaxDamage() - target.getDamageValue(); station.start();
        var saved = station.saveWithoutMetadata(h.getLevel().registryAccess()); saved.putBoolean("doomed", true); saved.putInt("fail_at", 1);
        station.loadWithComponents(saved, h.getLevel().registryAccess());
        GearStationEntity.serverTick(h.getLevel(), station.getBlockPos(), station.getBlockState(), station);
        ItemStack failed = station.getItem(5);
        h.assertTrue(!station.running() && station.getItem(8).isEmpty() && ExtremeGear.grade(failed) == 0, "Failure retains the forging grade and actual input");
        h.assertTrue(failed.getMaxDamage() - failed.getDamageValue() == Math.round(remaining * .95F), "Failure takes five percent of remaining durability");
        h.assertTrue(station.getItem(6).getDamageValue() == 1024 && station.getItem(7).getDamageValue() == 1024, "Failed operations pay half tool wear");
        h.succeed();
    }
}
