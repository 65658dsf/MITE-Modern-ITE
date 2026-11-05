package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Forge-to-anvil regression for the fourth mithril upgrade. @author NingMeng123 */
@GameTestHolder("modern_ite_repair")
@PrefixGameTestTemplate(false)
public final class ForgeRepairGameTests {
    private ForgeRepairGameTests() {}

    @GameTest(template = "gear_empty")
    public static void fourthMithrilUpgradeCanBeRepairedAtZeroXpAndKeepsItsIdentity(GameTestHelper h) {
        Item club = BuiltInRegistries.ITEM.get(ResourceLocation.parse("modern_ite:club_mithril"));
        h.assertTrue(club != Items.AIR, "The actual extension mithril club is registered");
        for (Item item : List.of(MiteTools.get("mithril_pickaxe"), MiteArmorContent.get("mite:mithril_chestplate"), club)) {
            BlockPos forgePos = h.absolutePos(new BlockPos(1, 1, 1));
            h.getLevel().setBlockAndUpdate(forgePos, ExtremeGear.FORGES.get(3).get().defaultBlockState());
            var forge = (GearStationEntity) h.getLevel().getBlockEntity(forgePos);
            ItemStack target = new ItemStack(item);
            target.set(ExtremeGear.FORGE_GRADE.get(), 3);
            EquipmentDurability.setQuality(target, Quality.FINE);
            var identity = GearProgression.ensureId(target);
            target.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, GemType.DURABILITY.ordinal() * 6 + 6));
            forge.setItem(5, target);
            forge.setItem(6, new ItemStack(MiteTools.get("vibranium_axe")));
            forge.setItem(7, new ItemStack(MiteTools.get("vibranium_war_hammer")));
            forge.setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
            forge.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
            forge.setItem(2, new ItemStack(ExtremeGear.STONES.get(0).get(), 2));
            forge.start();
            h.assertTrue(forge.running(), "The actual fourth-upgrade operation starts");
            var saved = forge.saveWithoutMetadata(h.getLevel().registryAccess());
            saved.putBoolean("doomed", false);
            forge.loadWithComponents(saved, h.getLevel().registryAccess());
            for (int tick = 0; tick < 2500 && forge.running(); tick++)
                GearStationEntity.serverTick(h.getLevel(), forgePos, forge.getBlockState(), forge);
            ItemStack forged = forge.removeItem(8, 1);
            h.assertTrue(!forged.isEmpty() && ExtremeGear.grade(forged) == 4
                    && EquipmentDurability.quality(forged) == Quality.EXCELLENT, "The real forge awards grade four and excellent quality");
            forged.setDamageValue(20);
            var owner = h.makeMockPlayer(GameType.SURVIVAL);
            PlayerExperience.setTotal(owner, 0);
            BlockPos anvilPos = h.absolutePos(new BlockPos(2, 1, 1));
            h.getLevel().setBlockAndUpdate(anvilPos.below(), Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(anvilPos, MiteFunctionalBlocks.ANVILS.get("mithril").get().defaultBlockState());
            owner.moveTo(anvilPos.getCenter());
            var menu = new AnvilMenu(1, owner.getInventory(), ContainerLevelAccess.create(h.getLevel(), anvilPos));
            menu.getSlot(0).set(forged);
            menu.getSlot(1).set(new ItemStack(MiteMaterials.MITHRIL_NUGGET.get(), 2));
            h.assertTrue(!menu.getSlot(2).getItem().isEmpty() && menu.getSlot(2).mayPickup(owner),
                    "Earned forge quality exposes a real payable repair result at zero XP");
            menu.clicked(2, 0, ClickType.PICKUP, owner);
            ItemStack repaired = menu.getCarried();
            h.assertTrue(!repaired.isEmpty() && repaired.getDamageValue() == 0, "Actual anvil pickup repairs the equipment");
            h.assertTrue(ExtremeGear.grade(repaired) == 4 && EquipmentDurability.quality(repaired) == Quality.EXCELLENT
                    && identity.equals(GearProgression.id(repaired)) && ExtremeGear.rank(repaired, GemType.DURABILITY) == 6,
                    "Repair preserves forge grade, quality, growth identity and socketed gems");
            h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().getCount() == 1
                    && owner.totalExperience == 0, "One repair consumes exactly one nugget and no XP");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void forgeRepairExemptionRetainsQualityAndAnvilMaterialLimits(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        PlayerExperience.setTotal(owner, 0);
        ItemStack target = new ItemStack(MiteTools.get("mithril_pickaxe"));
        target.setDamageValue(20);
        EquipmentDurability.setQuality(target, Quality.EXCELLENT);
        h.assertTrue(!CraftingQuality.canRepair(owner, target), "Unforged excellent equipment keeps its original XP gate");
        target.set(ExtremeGear.FORGE_GRADE.get(), 3);
        h.assertTrue(!CraftingQuality.canRepair(owner, target), "Grade three has only earned fine quality");
        target.set(ExtremeGear.FORGE_GRADE.get(), 4);
        h.assertTrue(CraftingQuality.canRepair(owner, target), "Grade four has earned excellent-quality maintenance");
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, MiteFunctionalBlocks.ANVILS.get("copper").get().defaultBlockState());
        owner.moveTo(pos.getCenter());
        var menu = new AnvilMenu(1, owner.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        menu.getSlot(0).set(target.copy());
        menu.getSlot(1).set(new ItemStack(MiteMaterials.MITHRIL_NUGGET.get()));
        h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Earned quality cannot bypass the weaker anvil's material capacity");
        EquipmentDurability.setQuality(target, Quality.SUPERB);
        h.assertTrue(!CraftingQuality.canRepair(owner, target), "Quality above the actual forge reward still needs crafting XP");
        PlayerExperience.setTotal(owner, 1_000_000);
        h.assertTrue(CraftingQuality.canRepair(owner, target), "The original XP path still permits higher quality");
        h.succeed();
    }
}
