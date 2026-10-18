package io.github.ningmeng123.modernite.gear;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Public gear module entry point and baseline policy adapters. @author NingMeng123 */
public final class ExtremeGear {
    public static final String MOD_ID = "modern_ite";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GemSockets>> SOCKETS =
            COMPONENTS.registerComponentType("gem_sockets_v1", builder -> builder.persistent(GemSockets.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(GemSockets.CODEC)));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GRINDINGS =
            COMPONENTS.registerComponentType("grindings_v1", builder -> builder.persistent(Codec.intRange(0, 3))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FORGE_GRADE =
            COMPONENTS.registerComponentType("forge_grade_v1", builder -> builder.persistent(Codec.intRange(0, 10))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final List<DeferredItem<EnhanceGemItem>> GEMS = new ArrayList<>();
    public static final List<DeferredItem<Item>> SHARDS = new ArrayList<>();
    public static final List<DeferredItem<Item>> STONES = new ArrayList<>();
    static {
        for (GemType type : GemType.values()) for (int rank = 1; rank <= 6; rank++) {
            int stage = rank;
            GEMS.add(ITEMS.registerItem("gem_" + type.id + "_" + rank, p -> new EnhanceGemItem(type, stage, p)));
        }
        for (int rank = 2; rank <= 6; rank++) SHARDS.add(ITEMS.registerSimpleItem("gem_shard_" + rank, new Item.Properties().fireResistant()));
        for (String material : List.of("iron", "mithril", "adamantium", "universal"))
            STONES.add(ITEMS.registerItem(material + "_enhance_stone", p -> new Item(p.fireResistant()) {
                @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.add(net.minecraft.network.chat.Component.translatable(material.equals("universal")
                            ? "tooltip.modern_ite.universal_stone" : "tooltip.modern_ite.enhance_stone").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }));
    }
    public static final DeferredItem<Item> GEM_BOX = ITEMS.registerItem("gem_box", p -> new Item(p) {
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            if (!level.isClientSide) {
                if (!player.isCreative()) held.shrink(1);
                ItemStack reward = gem(GemType.values()[player.getRandom().nextInt(GemType.values().length)], 1);
                if (!player.getInventory().add(reward)) player.drop(reward, false);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    });
    public static final DeferredItem<Item> PROTECTION_TOME = ITEMS.registerSimpleItem("protection_tome", new Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredBlock<GearStationBlock> GEM_SETTING = station("gem_setting", -1);
    public static final DeferredBlock<GrinderBlock> GRINDER = BLOCKS.register("grinder", () -> new GrinderBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(2, 10)));
    public static final List<DeferredBlock<GearStationBlock>> FORGES = new ArrayList<>();
    static {
        ITEMS.registerSimpleBlockItem(GRINDER);
        String[] materials = {"iron", "mithril", "adamantium", "vibranium"};
        for (int i = 0; i < materials.length; i++) FORGES.add(station(materials[i] + "_forging_table", i));
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GearStationEntity>> STATION_ENTITY =
            ENTITIES.register("gear_station", () -> BlockEntityType.Builder.of(GearStationEntity::new, stationBlocks()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<GearStationMenu>> STATION_MENU =
            MENUS.register("gear_station", () -> new MenuType<>(GearStationMenu::gemClient, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<GearStationMenu>> FORGE_MENU =
            MENUS.register("forging_table", () -> new MenuType<>(GearStationMenu::forgeClient, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ProtectionTomeRecipe>> TOME_RECIPE =
            SERIALIZERS.register("protection_tome", () -> new SimpleCraftingRecipeSerializer<>(ProtectionTomeRecipe::new));
    private ExtremeGear() {}
    private static DeferredBlock<GearStationBlock> station(String id, int tier) {
        var block = BLOCKS.register(id, () -> new GearStationBlock(tier, BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE).strength(4, 10)));
        ITEMS.registerSimpleBlockItem(block, new Item.Properties().stacksTo(1)); return block;
    }
    private static Block[] stationBlocks() {
        var all = new ArrayList<Block>(); all.add(GEM_SETTING.get()); FORGES.forEach(s -> all.add(s.get()));
        return all.toArray(Block[]::new);
    }
    public static void register(IEventBus bus) {
        ITEMS.register(bus); BLOCKS.register(bus); COMPONENTS.register(bus); ENTITIES.register(bus);
        MENUS.register(bus); SERIALIZERS.register(bus);
        NeoForge.EVENT_BUS.register(GearEvents.class);
    }
    public static List<ItemStack> creativeItems() {
        var out = new ArrayList<ItemStack>();
        GEMS.forEach(s -> out.add(new ItemStack(s.get()))); SHARDS.forEach(s -> out.add(new ItemStack(s.get())));
        STONES.forEach(s -> out.add(new ItemStack(s.get()))); out.add(new ItemStack(GEM_BOX.get())); out.add(new ItemStack(PROTECTION_TOME.get()));
        out.add(new ItemStack(GEM_SETTING.get())); out.add(new ItemStack(GRINDER.get())); FORGES.forEach(s -> out.add(new ItemStack(s.get())));
        return List.copyOf(out);
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
    public static GemSockets sockets(ItemStack stack) { return stack.getOrDefault(SOCKETS.get(), GemSockets.EMPTY); }
    public static int rank(ItemStack stack, GemType type) { return stack.isEmpty() ? 0 : sockets(stack).level(type); }
    public static int worn(Player player, GemType type) {
        int sum = 0;
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
            sum += rank(player.getItemBySlot(slot), type);
        return sum;
    }
    public static ItemStack gem(GemType type, int rank) { return new ItemStack(GEMS.get(type.ordinal() * 6 + rank - 1).get()); }
    public static ItemStack fromCode(int code) { return code == 0 ? ItemStack.EMPTY : new ItemStack(GEMS.get(code - 1).get()); }
    public static boolean socketable(ItemStack stack) {
        return !stack.isEmpty() && (EquipmentDurability.toolProfile(stack) != null || GearProgression.isArmor(stack));
    }
    public static int grade(ItemStack stack) { return stack.getOrDefault(FORGE_GRADE.get(), 0); }
    public static double health(Player player, double original) {
        if (!(player instanceof ServerPlayer server)) return original;
        return GearMath.health(player.experienceLevel, BloodMoonInvasion.highestDay(server.serverLevel()), worn(player, GemType.HEALTH));
    }
    public static int nutritionCapacity(Player player, int original) { return Math.min(20, original + worn(player, GemType.NUTRITION)); }
    public static float regeneration(Player player, float original) { return original * (1F + worn(player, GemType.RECOVER) * .125F); }
    public static float naturalRegeneration(Player player, float original) { return regeneration(player, original); }
    public static int maximumGearLevel(ItemStack stack, int original) {
        return original == 0 ? 0 : original + Math.clamp(stack.getOrDefault(GRINDINGS.get(), 0), 0, 3);
    }
    public static long experience(ItemStack stack, long original) {
        int grade = grade(stack);
        double total = original * (1D + .025D * grade * (grade + 1));
        return (long)Math.min(Long.MAX_VALUE, total);
    }
    public static long gearExperience(ItemStack stack, long original) { return experience(stack, original); }
    public static void preserve(ItemStack source, ItemStack result) {
        if (!socketable(result)) return;
        copy(SOCKETS.get(), source, result); copy(GRINDINGS.get(), source, result); copy(FORGE_GRADE.get(), source, result);
    }
    private static <T> void copy(DataComponentType<T> type, ItemStack source, ItemStack result) {
        T value = source.get(type); if (value == null) result.remove(type); else result.set(type, value);
    }
}
