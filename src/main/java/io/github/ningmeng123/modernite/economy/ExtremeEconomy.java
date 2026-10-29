package io.github.ningmeng123.modernite.economy;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.rules.SourceVillagerTradeRules;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Adds source economy to existing modern stock without replacing native transactions. @author NingMeng123 */
public final class ExtremeEconomy {
    public static final String STATE = "modern_ite:economy";
    private static final String VOUCHER_TICK = "modern_ite:voucher_roll_time";
    private static final ThreadLocal<Integer> CROP_CHECK_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ModernIte.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ExtremeFishingLootModifier>> FISHING =
            MODIFIERS.register("extreme_fishing", () -> ExtremeFishingLootModifier.CODEC);
    private static final Set<String> SMITHS = Set.of("toolsmith", "weaponsmith", "armorer");
    private static final Set<String> SPECIAL = Set.of("minecraft:protection", "minecraft:sharpness", "minecraft:fortune", "mite:harvesting",
            "modern_ite:extend", "minecraft:efficiency", "mite:vampiric", "mite:butchering", "modern_ite:fixed", "modern_ite:chain", "modern_ite:emergency");
    public record Row(String id, String profession, float chance, String input, int minimum, int maximum, String output, int outputMinimum, int outputMaximum) {}
    public static final List<Row> ROWS = rows();
    private ExtremeEconomy() {}
    public static void register(IEventBus bus) { MODIFIERS.register(bus); NeoForge.EVENT_BUS.register(ExtremeEconomy.class); }
    private static Row row(String id, String profession, float chance, String input, int low, int high, String output, int outLow, int outHigh) {
        return new Row(id, profession, chance, input, low, high, output, outLow, outHigh);
    }
    private static List<Row> rows() {
        var rows = new ArrayList<Row>();
        for (String crop : List.of("mite:onion", "minecraft:potato", "minecraft:carrot"))
            rows.add(row("crop_" + crop, "farmer", .3F, crop, 12, 15, "minecraft:emerald", 1, 1));
        rows.add(row("emerald_diamond", "librarian", .5F, "minecraft:emerald", 4, 4, "minecraft:diamond", 3, 3));
        rows.add(row("diamond_emerald", "librarian", .5F, "minecraft:diamond", 3, 3, "minecraft:emerald", 4, 4));
        rows.add(row("red_emerald", "librarian", .5F, "modern_ite:fancy_red", 3, 3, "minecraft:emerald", 5, 5));
        rows.add(row("book", "librarian", 1F, "minecraft:emerald", 1, 1, "minecraft:enchanted_book", 1, 1));
        rows.add(row("pumpkin", "librarian", .5F, "minecraft:pumpkin", 6, 6, "minecraft:emerald", 1, 1));
        rows.add(row("coal", "smith", .8F, "minecraft:coal", 10, 16, "minecraft:emerald", 1, 1));
        rows.add(row("copper", "smith", .7F, "minecraft:copper_ingot", 7, 11, "minecraft:emerald", 1, 1));
        rows.add(row("silver", "smith", .7F, "mite:silver_ingot", 7, 11, "minecraft:emerald", 1, 1));
        rows.add(row("gold", "smith", .7F, "minecraft:gold_ingot", 8, 12, "minecraft:emerald", 1, 1));
        rows.add(row("iron", "smith", .6F, "minecraft:iron_ingot", 5, 8, "minecraft:emerald", 1, 1));
        rows.add(row("ancient", "smith", .5F, "mite:ancient_metal_ingot", 3, 5, "minecraft:diamond", 1, 1));
        rows.add(row("mithril", "smith", .4F, "mite:mithril_ingot", 1, 2, "minecraft:diamond", 1, 1));
        rows.add(row("adamantium", "smith", .3F, "mite:adamantium_ingot", 1, 1, "minecraft:diamond", 2, 4));
        rows.add(row("vibranium", "smith", .2F, "mite:vibranium_ingot", 1, 1, "minecraft:diamond", 6, 11));
        rows.add(row("buy_mithril_emerald", "smith", .1F, "minecraft:emerald", 16, 16, "mite:mithril_ingot", 1, 1));
        rows.add(row("buy_mithril_diamond", "smith", .1F, "minecraft:diamond", 6, 6, "mite:mithril_ingot", 1, 1));
        rows.add(row("buy_adamantium_emerald", "smith", .1F, "minecraft:emerald", 32, 32, "mite:adamantium_ingot", 1, 1));
        rows.add(row("buy_adamantium_diamond", "smith", .1F, "minecraft:diamond", 16, 16, "mite:adamantium_ingot", 1, 1));
        rows.add(row("buy_red", "smith", .1F, "minecraft:emerald", 5, 5, "modern_ite:fancy_red", 3, 3));
        rows.add(row("apple", "smith", .3F, "minecraft:emerald", 1, 1, "minecraft:apple", 2, 4));
        rows.add(row("planting_voucher", "smith", .5F, "modern_ite:voucher_planting", 16, 16, "minecraft:emerald", 1, 1));
        rows.add(row("villager_voucher", "smith", .5F, "modern_ite:voucher_villager", 4, 4, "minecraft:emerald", 1, 1));
        rows.add(row("red_ghast_tear", "smith", .5F, "modern_ite:fancy_red", 8, 8, "minecraft:ghast_tear", 1, 1));
        return List.copyOf(rows);
    }
    public static void ensure(Villager villager, MerchantOffers offers) {
        if (villager.level().isClientSide || villager.isBaby()) return;
        normalizeSavedPayments(offers);
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        if (key == null || !key.getNamespace().equals("minecraft")) return;
        String profession = SMITHS.contains(key.getPath()) ? "smith" : key.getPath();
        if (!Set.of("farmer", "librarian", "smith").contains(profession)) return;
        CompoundTag state = villager.getPersistentData().getCompound(STATE);
        if (!state.getString("profession").equals(key.toString())) { state = new CompoundTag(); state.putString("profession", key.toString()); }
        int level = villager.getVillagerData().getLevel();
        if (state.getInt("level") >= level) return;
        ListTag known = state.getList("known", 8); RandomSource random = villager.getRandom();
        for (int rank = state.getInt("level") + 1; rank <= level; rank++) {
            var candidates = new ArrayList<Row>();
            for (Row row : ROWS) if (row.profession().equals(profession) && (row.id().equals("book") || !known.contains(StringTag.valueOf(row.id())))
                    && (row.chance() >= 1 || random.nextFloat() < SourceVillagerTradeRules.probability(row.chance(), known.size()))) candidates.add(row);
            if (candidates.isEmpty()) continue;
            Row selected = candidates.get(random.nextInt(candidates.size()));
            if (selected.id().equals("book")) {
                MerchantOffer simple = book(villager, false); if (simple != null && !duplicate(offers, simple)) offers.add(simple);
                if (random.nextFloat() < SourceVillagerTradeRules.probability(.05F, known.size())) {
                    MerchantOffer special = book(villager, true); if (special != null && !duplicate(offers, special)) offers.add(special);
                }
            } else {
                MerchantOffer offer = create(selected, random);
                if (!duplicate(offers, offer)) offers.add(offer);
                known.add(StringTag.valueOf(selected.id()));
            }
        }
        state.putInt("version", 1); state.putInt("level", level); state.put("known", known);
        villager.getPersistentData().put(STATE, state);
    }
    private static boolean duplicate(MerchantOffers offers, MerchantOffer candidate) {
        for (MerchantOffer existing : offers) if (ItemStack.matches(existing.getBaseCostA(), candidate.getBaseCostA())
                && ItemStack.matches(existing.getCostB(), candidate.getCostB()) && ItemStack.matches(existing.getResult(), candidate.getResult())) return true;
        return false;
    }
    public static MerchantOffer create(Row row, RandomSource random) {
        return exchange(item(row.input()), random.nextIntBetweenInclusive(row.minimum(), row.maximum()),
                new ItemStack(item(row.output()), random.nextIntBetweenInclusive(row.outputMinimum(), row.outputMaximum())));
    }
    public static MerchantOffer exchange(Item payment, int count, ItemStack result) {
        int cap = new ItemStack(payment).getMaxStackSize();
        if (count < 1 || result.getCount() > result.getMaxStackSize())
            throw new IllegalArgumentException("Trade exceeds the real material stack limit");
        if (count > cap * 2) {
            // The native merchant has two payment slots. Reversible 9:1 metal blocks keep the
            // source's complete ingot price payable after the four-ingot carrying limit.
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(payment);
            Item block = id.getPath().endsWith("_ingot") ? BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("_ingot", "_block"))) : Items.AIR;
            int blocks = count / 9, remainder = count % 9;
            if (block == Items.AIR || blocks < 1 || blocks > new ItemStack(block).getMaxStackSize() || remainder > cap)
                throw new IllegalArgumentException("Trade cannot express its full material price in two legal stacks");
            return new MerchantOffer(new ItemCost(block, blocks), remainder == 0 ? Optional.empty()
                    : Optional.of(new ItemCost(payment, remainder)), result, 7, 1, 0F);
        }
        int first = Math.min(cap, count), second = count - first;
        return new MerchantOffer(new ItemCost(payment, first), second == 0 ? Optional.empty() : Optional.of(new ItemCost(payment, second)), result, 7, 1, 0F);
    }
    private static void normalizeSavedPayments(MerchantOffers offers) {
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer previous = offers.get(index);
            ItemStack first = previous.getBaseCostA(), second = previous.getCostB();
            if (first.getCount() <= first.getMaxStackSize() && (second.isEmpty() || second.getCount() <= second.getMaxStackSize())) continue;
            if (!second.isEmpty() && !ItemStack.isSameItemSameComponents(first, second)) continue;
            // Specials are transient while a customer is trading. Migrate after vanilla clears
            // them, instead of reinterpreting an ingot adjustment as whole storage blocks.
            if (previous.getSpecialPriceDiff() != 0 || previous.getPriceMultiplier() != 0F || previous.getMaxUses() != 7 || previous.getXp() != 1) continue;
            int count = first.getCount() + second.getCount();
            boolean sourceRow = ROWS.stream().anyMatch(row -> first.is(item(row.input())) && previous.getResult().is(item(row.output()))
                    && count >= row.minimum() && count <= row.maximum()
                    && previous.getResult().getCount() >= row.outputMinimum() && previous.getResult().getCount() <= row.outputMaximum());
            if (!sourceRow) continue;
            MerchantOffer payment = exchange(first.getItem(), count, previous.getResult().copy());
            offers.set(index, new MerchantOffer(payment.getItemCostA(), payment.getItemCostB(), previous.getResult().copy(),
                    previous.getUses(), previous.getMaxUses(), previous.getXp(), previous.getPriceMultiplier(), previous.getDemand()));
        }
    }
    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) throw new IllegalStateException("Missing Extreme economy item " + id);
        return item;
    }
    public static MerchantOffer book(Villager villager, boolean special) {
        var pool = villager.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                .filter(holder -> !holder.is(EnchantmentTags.CURSE))
                .filter(holder -> holder.unwrapKey().map(key -> !key.location().toString().equals("modern_ite:experience")
                        && SPECIAL.contains(key.location().toString()) == special).orElse(false))
                .filter(holder -> special || holder.value().getMaxLevel() > 1).toList();
        if (pool.isEmpty()) return null;
        Holder<Enchantment> enchantment = pool.get(villager.getRandom().nextInt(pool.size()));
        String id = enchantment.unwrapKey().orElseThrow().location().toString();
        int maximum = enchantment.value().getMaxLevel();
        if (id.equals("modern_ite:extend")) maximum = 1;
        if (id.equals("minecraft:fortune")) maximum = Math.min(maximum, 3);
        int level = 1 + villager.getRandom().nextInt(maximum);
        int price = EconomyRules.bookPrice(level, special && enchantment.value().getMaxLevel() == 1, villager.getRandom().nextInt(10));
        ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
        var enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); enchants.set(enchantment, level);
        result.set(DataComponents.STORED_ENCHANTMENTS, enchants.toImmutable());
        return exchange(Items.EMERALD, price, result);
    }
    @SubscribeEvent public static void traded(TradeWithVillagerEvent event) {
        if (!(event.getAbstractVillager() instanceof Villager villager) || villager.isBaby() || villager.level().isClientSide) return;
        long time = villager.level().getGameTime(); CompoundTag data = villager.getPersistentData();
        if (data.contains(VOUCHER_TICK) && time - data.getLong(VOUCHER_TICK) >= 0 && time - data.getLong(VOUCHER_TICK) < 40) return;
        data.putLong(VOUCHER_TICK, time);
        if (villager.getRandom().nextFloat() < .15F) villager.spawnAtLocation(new ItemStack(ExtremeContent.VILLAGER_VOUCHER.get()));
    }
    public static void harvestVoucher(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && !player.isCreative() && level.random.nextInt(50) == 0)
            Block.popResource(level, pos, new ItemStack(ExtremeContent.PLANTING_VOUCHER.get()));
    }
    public static boolean suppressCropVoucher() { return CROP_CHECK_DEPTH.get() > 0; }
    public static <T> T cropCheck(java.util.function.Supplier<T> action) {
        int before = CROP_CHECK_DEPTH.get(); CROP_CHECK_DEPTH.set(before + 1);
        try { return action.get(); }
        finally { if (before == 0) CROP_CHECK_DEPTH.remove(); else CROP_CHECK_DEPTH.set(before); }
    }
}
