package io.github.ningmeng123.modernite.economy;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

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
                Item input = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.input()));
                int expected = RandomSource.create(seed).nextIntBetweenInclusive(row.minimum(), row.maximum());
                h.assertValueEqual(ingotValue(offer.getBaseCostA(), input) + ingotValue(offer.getCostB(), input), expected,
                        "Legal denomination changes preserve the complete original material price: " + row.id());
            }
        }
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void fourIngotLimitKeepsEveryMetalPricePayableThroughRealNativeMerchantTransactions(GameTestHelper h) {
        try (MerchantSession session = new MerchantSession(h)) {
            for (String metal : List.of("minecraft:copper", "mite:silver", "minecraft:gold", "minecraft:iron", "mite:ancient_metal")) {
                Item ingot = BuiltInRegistries.ITEM.get(ResourceLocation.parse(metal + "_ingot"));
                for (int count = 3; count <= 12; count++) {
                    MerchantOffer offer = ExtremeEconomy.exchange(ingot, count, new ItemStack(Items.EMERALD));
                    h.assertValueEqual(ingotValue(offer.getBaseCostA(), ingot) + ingotValue(offer.getCostB(), ingot), count,
                            "Every payment quotes the same full ingot value");
                    var offers = new MerchantOffers(); offers.add(offer); session.villager.setOffers(offers);
                    session.player.getInventory().clearContent(); session.menu.setCarried(ItemStack.EMPTY);
                    session.menu.getSlot(0).set(ItemStack.EMPTY); session.menu.getSlot(1).set(ItemStack.EMPTY);
                    session.menu.setSelectionHint(0);
                    ItemStack shortPayment = offer.getBaseCostA().copy(); shortPayment.shrink(1);
                    session.menu.getSlot(0).set(shortPayment); session.menu.getSlot(1).set(offer.getCostB().copy());
                    h.assertTrue(session.menu.getSlot(2).getItem().isEmpty(), "A missing ingot or storage block cannot preview the trade");
                    session.menu.getSlot(0).set(offer.getBaseCostA().copy());
                    h.assertTrue(session.menu.getSlot(2).getItem().is(Items.EMERALD), "Two physically legal payment slots preview the real quote");
                    if (count % 2 == 0) session.menu.clicked(2, 0, ClickType.PICKUP, session.player);
                    else session.menu.quickMoveStack(session.player, 2);
                    int delivered = session.player.getInventory().countItem(Items.EMERALD)
                            + (session.menu.getCarried().is(Items.EMERALD) ? session.menu.getCarried().getCount() : 0);
                    h.assertTrue(delivered == 1 && offer.getUses() == 1 && session.menu.getSlot(0).getItem().isEmpty()
                                    && session.menu.getSlot(1).getItem().isEmpty() && session.menu.getSlot(2).getItem().isEmpty(),
                            "Pickup and shift-click each consume the full price once for one result");
                }
            }
        }
        h.succeed();
    }
    @GameTest(template = "gear_empty")
    public static void savedOversizedSourcePaymentsMigrateWithoutRerollingPriceOrStock(GameTestHelper h) {
        var villager = new Villager(EntityType.VILLAGER, h.getLevel());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        var offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.GOLD_INGOT, 8), Optional.of(new ItemCost(Items.GOLD_INGOT, 3)),
                new ItemStack(Items.EMERALD), 3, 7, 1, 0F, 4));
        villager.setOffers(offers);
        MerchantOffer migrated = villager.getOffers().getFirst();
        h.assertTrue(migrated.getBaseCostA().is(Items.GOLD_BLOCK) && migrated.getBaseCostA().getCount() == 1
                        && migrated.getCostB().is(Items.GOLD_INGOT) && migrated.getCostB().getCount() == 2,
                "An old eight-plus-three price becomes one block plus two ingots, still eleven total");
        h.assertTrue(migrated.getUses() == 3 && migrated.getMaxUses() == 7 && migrated.getDemand() == 4,
                "Payment migration preserves the original stock uses and demand");
        var tag = new CompoundTag(); villager.saveWithoutId(tag);
        var restored = new Villager(EntityType.VILLAGER, h.getLevel()); restored.load(tag);
        h.assertTrue(restored.getOffers().size() == 1 && restored.getOffers().getFirst().getUses() == 3
                        && ingotValue(restored.getOffers().getFirst().getBaseCostA(), Items.GOLD_INGOT)
                        + ingotValue(restored.getOffers().getFirst().getCostB(), Items.GOLD_INGOT) == 11,
                "The payable price and original stock survive save/load without duplicate offers");
        villager.discard(); restored.discard(); h.succeed();
    }
    private static int ingotValue(ItemStack stack, Item input) {
        if (stack.isEmpty()) return 0;
        if (stack.is(input)) return stack.getCount();
        ResourceLocation ingot = BuiltInRegistries.ITEM.getKey(input), denomination = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ingot.getPath().endsWith("_ingot") && denomination.getNamespace().equals(ingot.getNamespace())
                && denomination.getPath().equals(ingot.getPath().replace("_ingot", "_block"))) return stack.getCount() * 9;
        throw new AssertionError("Unrelated payment denomination " + stack + " for " + input);
    }
    private static final class MerchantSession implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final Villager villager;
        final MerchantMenu menu;
        MerchantSession(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "NingMeng123"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie); player.setGameMode(GameType.SURVIVAL);
            player.moveTo(h.absolutePos(new BlockPos(2, 3, 2)).getCenter()); player.setNoGravity(true);
            player.getInventory().clearContent();
            villager = new Villager(EntityType.VILLAGER, h.getLevel()); villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
            villager.moveTo(player.position().add(1, 0, 0)); h.getLevel().addFreshEntity(villager);
            villager.setOffers(new MerchantOffers()); villager.setTradingPlayer(player);
            menu = new MerchantMenu(92, player.getInventory(), villager); player.containerMenu = menu;
        }
        @Override public void close() {
            try {
                menu.setCarried(ItemStack.EMPTY); menu.getSlot(0).set(ItemStack.EMPTY); menu.getSlot(1).set(ItemStack.EMPTY);
                player.getInventory().clearContent(); villager.setTradingPlayer(null); villager.discard();
                player.containerMenu = player.inventoryMenu; player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
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
