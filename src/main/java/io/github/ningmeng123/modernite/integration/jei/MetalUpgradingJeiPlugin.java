package io.github.ningmeng123.modernite.integration.jei;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.client.MetalUpgradingScreen;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingContent;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingRecipe;
import java.math.BigDecimal;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Independent searchable conversion pages never hide or override normal gem-shard recipes. @author NingMeng123 */
@JeiPlugin
public final class MetalUpgradingJeiPlugin implements IModPlugin {
    public record Entry(ResourceLocation id, MetalUpgradingRecipe recipe) {}
    public static final RecipeType<Entry> TYPE = RecipeType.create(ModernIte.MOD_ID, "metal_upgrading", Entry.class);
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "metal_upgrading"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) registration.addRecipes(TYPE, level.getRecipeManager().getAllRecipesFor(MetalUpgradingContent.RECIPE_TYPE.get()).stream()
                .map(holder -> new Entry(holder.id(), holder.value())).toList());
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(MetalUpgradingContent.FURNACE_ITEM.get()), TYPE);
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(MetalUpgradingScreen.class, new IGuiContainerHandler<MetalUpgradingScreen>() {
            @Override public java.util.Collection<IGuiClickableArea> getGuiClickableAreas(MetalUpgradingScreen screen, double mouseX, double mouseY) {
                return List.of(IGuiClickableArea.createBasic(78, 36, 30, 16, TYPE));
            }
        });
    }
    private static final class Category implements IRecipeCategory<Entry> {
        private final IDrawable icon, arrow;
        Category(IGuiHelper gui) { icon = gui.createDrawableItemStack(new ItemStack(MetalUpgradingContent.FURNACE_ITEM.get())); arrow = gui.getRecipeArrow(); }
        @Override public RecipeType<Entry> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.translatable("block.modern_ite.metal_upgrading_furnace"); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 162; }
        @Override public int getHeight() { return 80; }
        @Override public ResourceLocation getRegistryName(Entry entry) { return entry.id(); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
            builder.addInputSlot(22, 9).setStandardSlotBackground().addItemStack(entry.recipe().input());
            builder.addInputSlot(22, 38).setStandardSlotBackground().addItemStacks(entry.recipe().fuelOptions());
            builder.addOutputSlot(116, 15).setOutputSlotBackground().addItemStack(entry.recipe().output());
        }
        @Override public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 70, 17);
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, Component.translatable("container.modern_ite.metal_upgrade.fuel"), 45, 42, 0xFF404040, false);
            graphics.drawString(font, Component.translatable("jei.modern_ite.metal_upgrade.time",
                    BigDecimal.valueOf(entry.recipe().cookingTicks()).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString(),
                    entry.recipe().requiredHeat()), 3, 64, 0xFF404040, false);
        }
    }
}
