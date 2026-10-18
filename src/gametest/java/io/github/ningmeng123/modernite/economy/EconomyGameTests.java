package io.github.ningmeng123.modernite.economy;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real modern offers, source payments and stock lifecycle. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private EconomyGameTests() {}
    @GameTest(template = "gear_empty")
    public static void actualFishingLootTableRunsTheRegisteredWeightedModifier(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
        var hook = new FishingHook(player, h.getLevel(), 0, 0);
        hook.setPos(h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)).getCenter());
        var params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, player.getMainHandItem()).withParameter(LootContextParams.THIS_ENTITY, hook)
                .withParameter(LootContextParams.ATTACKING_ENTITY, player).create(LootContextParamSets.FISHING);
        var table = h.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        int vouchers = 0, originalFish = 0;
        for (int roll = 0; roll < 400; roll++) for (ItemStack stack : table.getRandomItems(params, roll + 1L)) {
            if (stack.is(ExtremeContent.FISHING_VOUCHER.get())) vouchers++;
            if (stack.is(Items.COD) || stack.is(Items.SALMON)) originalFish++;
            h.assertTrue(!stack.isEmpty(), "Weighted additions resolve actual registered items");
        }
        h.assertTrue(vouchers > 0 && originalFish > 0, "The real main fishing loot table produces vouchers while retaining its original fish");
        hook.discard(); h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void everySourceEconomyRowBuildsPayableRealItemCosts(GameTestHelper h) {
        for (var row : ExtremeEconomy.ROWS) {
            if (row.id().equals("book")) continue;
            for (int seed = 0; seed < 16; seed++) {
                var offer = ExtremeEconomy.create(row, RandomSource.create(seed));
                h.assertTrue(!offer.getResult().isEmpty() && offer.getMaxUses() == 7, "Source stock resolves its real item and seven uses: " + row.id());
                h.assertTrue(offer.getBaseCostA().getCount() <= offer.getBaseCostA().getMaxStackSize()
                        && (offer.getCostB().isEmpty() || offer.getCostB().getCount() <= offer.getCostB().getMaxStackSize()),
                        "Authored multi-stack source costs are payable: " + row.id());
            }
        }
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void nativeStocksAndOfferUsesSurviveRepeatedReadsSaveAndRestock(GameTestHelper h) {
        var villager = new Villager(EntityType.VILLAGER, h.getLevel());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(5));
        var marker = new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.STICK), 12, 1, 0);
        var offers = new MerchantOffers(); offers.add(marker); villager.setOffers(offers);
        int count = villager.getOffers().size();
        h.assertTrue(villager.getOffers().getFirst() == marker && count > 1, "Existing offers remain while source supplements are added");
        villager.getOffers().getLast().increaseUses();
        for (int i = 0; i < 10; i++) h.assertTrue(villager.getOffers().size() == count, "Reading offers never rerolls or duplicates source stock");
        var saved = new CompoundTag(); villager.saveWithoutId(saved);
        var restored = new Villager(EntityType.VILLAGER, h.getLevel()); restored.load(saved);
        h.assertTrue(restored.getOffers().size() == count && restored.getOffers().getLast().getUses() == 1, "Saved source stock and uses survive real entity NBT");
        restored.restock(); h.assertTrue(restored.getOffers().size() == count && restored.getOffers().getLast().getUses() == 0, "Native restock refreshes the source offers without duplicating them");
        villager.discard(); restored.discard(); h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void jobLossInvalidatesOnlyAddonStockAndCropChecksNeverLeak(GameTestHelper h) {
        var villager = new Villager(EntityType.VILLAGER, h.getLevel());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        villager.getOffers(); h.assertTrue(villager.getPersistentData().contains(ExtremeEconomy.STATE), "Source supplement records the actual job");
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        h.assertTrue(!villager.getPersistentData().contains(ExtremeEconomy.STATE), "Unemployment invalidates addon stock even without an offer read");
        h.assertTrue(!ExtremeEconomy.suppressCropVoucher(), "Physical break drops are normally active");
        ExtremeEconomy.cropCheck(() -> { h.assertTrue(ExtremeEconomy.suppressCropVoucher(), "Artificial permission checks suppress premature vouchers"); return true; });
        h.assertTrue(!ExtremeEconomy.suppressCropVoucher(), "The artificial check scope is always cleared");
        villager.discard(); h.succeed();
    }
}
