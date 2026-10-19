package io.github.ningmeng123.modernite.content.metal;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A separate machine and recipe namespace keep the original gem-smelting route intact. @author NingMeng123 */
public final class MetalUpgradingContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModernIte.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModernIte.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModernIte.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ModernIte.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ModernIte.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ModernIte.MOD_ID);
    public static final DeferredBlock<MetalUpgradingBlock> FURNACE = BLOCKS.register("metal_upgrading_furnace",
            () -> new MetalUpgradingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE).strength(8F)
                    .lightLevel(state -> state.getValue(MetalUpgradingBlock.LIT) ? 13 : 0)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FURNACE_ITEM = ITEMS.registerSimpleBlockItem(FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetalUpgradingEntity>> ENTITY =
            ENTITIES.register("metal_upgrading_furnace", () -> BlockEntityType.Builder.of(MetalUpgradingEntity::new, FURNACE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<MetalUpgradingMenu>> MENU =
            MENUS.register("metal_upgrading", () -> new MenuType<>(MetalUpgradingMenu::client, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<RecipeType<?>, RecipeType<MetalUpgradingRecipe>> RECIPE_TYPE =
            TYPES.register("metal_upgrading", () -> new RecipeType<>() {
                @Override public String toString() { return "modern_ite:metal_upgrading"; }
            });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MetalUpgradingRecipe>> SERIALIZER =
            SERIALIZERS.register("metal_upgrading", MetalUpgradingRecipe.Serializer::new);
    private MetalUpgradingContent() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus);
        TYPES.register(bus); SERIALIZERS.register(bus);
        bus.addListener(MetalUpgradingContent::capabilities);
    }
    private static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ENTITY.get(),
                (entity, side) -> side == null ? new net.neoforged.neoforge.items.wrapper.InvWrapper(entity)
                        : new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(entity, side));
    }
}
