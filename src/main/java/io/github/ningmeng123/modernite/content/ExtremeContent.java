package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.item.R196ToolItem;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Active Extreme and ITE content, sharing existing MITE material identities. @author NingMeng123 */
public final class ExtremeContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModernIte.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModernIte.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModernIte.MOD_ID);
    public static final Map<String, DeferredItem<? extends Item>> ITEM_ENTRIES = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<? extends Block>> BLOCK_ENTRIES = new LinkedHashMap<>();
    public static final DeferredItem<Item> FANCY_RED = item("fancy_red", new Item.Properties().stacksTo(8));
    public static final DeferredItem<Item> DIAMOND_CHUNK = item("diamond_chunk", new Item.Properties().stacksTo(32));
    public static final DeferredItem<Item> BLAZE_COAL = item("blaze_coal_powder", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> FISHING_VOUCHER = item("voucher_fishing", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> PLANTING_VOUCHER = item("voucher_planting", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> VILLAGER_VOUCHER = item("voucher_villager", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> CLUB_CORE = item("voucher_club_core", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> TORCH_BUNDLE = item("stack_torch", new Item.Properties().stacksTo(16));
    public static final DeferredItem<CrossbowItem> CROSSBOW = ITEMS.registerItem("crossbow", CrossbowItem::new, new Item.Properties().durability(326));
    public static final DeferredItem<FortressEyeItem> FORTRESS_EYE = ITEMS.registerItem("nether_ender_eye", FortressEyeItem::new);
    // Keep the legacy mithril identity for existing stacks, without exposing new ones in the catalog.
    public static final DeferredItem<DynamicCoreItem> MITHRIL_CORE = lantern("mithril", DynamicCoreItem.Material.MITHRIL);
    public static final DeferredItem<DynamicCoreItem> ADAMANTIUM_CORE = lantern("adamantium", DynamicCoreItem.Material.ADAMANTIUM);
    public static final DeferredItem<DynamicCoreItem> VIBRANIUM_CORE = lantern("vibranium", DynamicCoreItem.Material.VIBRANIUM);
    public static final DeferredItem<ArmorItem> BUCKET_HELMET = ITEMS.registerItem("iron_bucket_helmet", p -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET, p.durability(1280)) {
        @Override public ResourceLocation getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity,
                net.minecraft.world.entity.EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
            return ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
        }
    });
    public static final DeferredBlock<Block> RESURRECTION = block("resurrection_array", () -> new ResurrectionBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(2F, 60000F).noOcclusion()));
    public static final DeferredBlock<RedDiamondOreBlock> RED_ORE = block("fancy_red_ore", () -> new RedDiamondOreBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE).strength(3F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<DropExperienceBlock> NETHER_ADAMANTIUM = block("nether_adamantium_ore", () -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.ANCIENT_DEBRIS).strength(4F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<RotatedPillarBlock> MAPLE_LOG = block("maple_log", () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)));
    public static final DeferredBlock<Block> MAPLE_PLANKS = block("maple_planks", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredBlock<LeavesBlock> MAPLE_LEAVES = block("maple_leaves", () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    private static final TreeGrower MAPLE_GROWER = new TreeGrower("modern_ite:maple", Optional.empty(), Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE, id("maple"))), Optional.empty());
    public static final DeferredBlock<SaplingBlock> MAPLE_SAPLING = block("maple_sapling", () -> new SaplingBlock(MAPLE_GROWER, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING)));
    public static final DeferredBlock<SlabBlock> MAPLE_SLAB = block("maple_slab", () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SLAB)));
    public static final DeferredBlock<StairBlock> MAPLE_STAIRS = block("maple_stairs", () -> new StairBlock(MAPLE_PLANKS.get().defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_STAIRS)));
    public static final DeferredBlock<VibraniumFurnaceBlock> VIBRANIUM_FURNACE = block("vibranium_furnace", () -> new VibraniumFurnaceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE).strength(8F)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VibraniumFurnaceEntity>> FURNACE_ENTITY = TYPES.register("vibranium_furnace", () -> BlockEntityType.Builder.of(VibraniumFurnaceEntity::new, VIBRANIUM_FURNACE.get()).build(null));
    public static final DeferredBlock<VibraniumChestBlock> VIBRANIUM_CHEST = block("vibranium_chest", () -> new VibraniumChestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).strength(8F)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VibraniumChestEntity>> CHEST_ENTITY = TYPES.register("vibranium_chest", () -> BlockEntityType.Builder.of(VibraniumChestEntity::new, VIBRANIUM_CHEST.get()).build(null));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModernIte.MOD_ID);
    static {
        ITEM_ENTRIES.put("crossbow", CROSSBOW); ITEM_ENTRIES.put("nether_ender_eye", FORTRESS_EYE);
        ITEM_ENTRIES.put("iron_bucket_helmet", BUCKET_HELMET);
        String[] metals = {"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"};
        float[] durability = {4,4,4,8,16,64,256,512}; float[] damages = {6,6,5,7,7,8,9,12};
        int[] enchantability = {30,30,50,30,40,100,40,120};
        for (int i=0;i<metals.length;i++) {
            String material=metals[i]; Quality quality = i<2?Quality.EXCELLENT:i==2?Quality.SUPERB:i<5?Quality.MASTERWORK:Quality.LEGENDARY;
            var definition = new MiteTools.Definition("modern_ite:club_"+material, EquipmentDurabilityRules.ToolFamily.CLUB, material, -1, 2F, damages[i], enchantability[i], i<3?2:i<5?3:i==5?4:5, .75F, durability[i], quality);
            MiteExtensions.registerExternalTool(definition);
            ITEM_ENTRIES.put("club_"+material, ITEMS.registerItem("club_"+material, p -> new R196ToolItem(definition,p)));
        }
        MiteExtensions.registerExternalArmor(new MiteArmorContent.Definition("modern_ite:iron_bucket_helmet",-1,"iron",ArmorItem.Type.HELMET,5,false,false,256,15,30,Quality.LEGENDARY,0,1280));
        TABS.register("content", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.modern_ite")).icon(() -> new ItemStack(FANCY_RED.get())).displayItems((p,out) -> {
            var seen = new HashSet<Item>();
            net.minecraft.core.registries.BuiltInRegistries.ITEM.forEach(item -> {
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ModernIte.MOD_ID)
                        && item != MITHRIL_CORE.get() && seen.add(item)) out.accept(item);
            });
        }).build());
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,path); }
    private static DeferredItem<DynamicCoreItem> lantern(String material, DynamicCoreItem.Material tier) {
        String name = "dynamic_core_" + material;
        var item = ITEMS.registerItem(name, p -> new DynamicCoreItem(tier, p));
        ITEM_ENTRIES.put(name, item);
        return item;
    }
    private static DeferredItem<Item> item(String name,Item.Properties properties) { var item=ITEMS.registerSimpleItem(name,properties); ITEM_ENTRIES.put(name,item); return item; }
    private static <T extends Block> DeferredBlock<T> block(String name,Supplier<T> factory) { var block=BLOCKS.register(name,factory); BLOCK_ENTRIES.put(name,block); ITEM_ENTRIES.put(name, ITEMS.registerSimpleBlockItem(block)); return block; }
    public static void register(IEventBus bus) { ITEMS.register(bus); BLOCKS.register(bus); TYPES.register(bus); TABS.register(bus); ExtremeEffects.register(bus); bus.addListener(ExtremeCapabilities::register); }
}
