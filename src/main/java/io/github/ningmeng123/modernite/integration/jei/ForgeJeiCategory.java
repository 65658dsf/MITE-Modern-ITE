package io.github.ningmeng123.modernite.integration.jei;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.modernite.gear.ForgeRecipes;
import io.github.ningmeng123.modernite.gear.GearMath;
import java.math.BigDecimal;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One strengthening tab with recipe-specific station capabilities. @author NingMeng123 */
public final class ForgeJeiCategory implements IRecipeCategory<ForgeJeiRecipe> {
    private static final int WIDTH = 170, HEIGHT = 128;
    private final RecipeType<ForgeJeiRecipe> type;
    private final Component title;
    private final IDrawable icon, arrow;

    public ForgeJeiCategory(RecipeType<ForgeJeiRecipe> type, ItemStack table, IGuiHelper gui) {
        this.type = type; title = Component.translatable("jei.modern_ite.forging");
        icon = gui.createDrawableItemStack(table); arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<ForgeJeiRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ForgeJeiRecipe recipe, IFocusGroup focuses) {
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemStacks(ForgeJeiRecipes.catalysts(recipe.minimumTier()));
        for (int slot = 0; slot < recipe.materials().size(); slot++)
            builder.addInputSlot(5 + slot * 18, 18).setStandardSlotBackground().setSlotName("material_" + slot)
                    .addItemStack(recipe.materials().get(slot));
        builder.addInputSlot(52, 48).setStandardSlotBackground().setSlotName("equipment").addItemStack(recipe.input())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(step(recipe)));
        builder.addOutputSlot(143, 48).setOutputSlotBackground().setSlotName("strengthened_equipment")
                .addItemStack(recipe.output()).addRichTooltipCallback((slot, tooltip) -> {
                    tooltip.add(step(recipe));
                    tooltip.add(Component.translatable("jei.modern_ite.forge_preservation"));
                    tooltip.add(Component.translatable("jei.modern_ite.forge_failure_rules"));
                    var reward = ForgeRecipes.reward(recipe.inputGrade());
                    if (reward != null) tooltip.add(Component.translatable("jei.modern_ite.forge_quality",
                            Component.translatable("quality.mite." + reward.name().toLowerCase(Locale.ROOT))));
                });
        builder.addSlot(RecipeIngredientRole.CATALYST, 5, 79).setStandardSlotBackground().setSlotName("axe")
                .addItemStacks(ForgeJeiRecipes.tools(ToolFamily.AXE)).addRichTooltipCallback((slot, tooltip) -> {
                    tooltip.add(Component.translatable("jei.modern_ite.forge_tools"));
                    tooltip.add(Component.translatable("jei.modern_ite.forge_wear", GearMath.axeCost(recipe.inputGrade()),
                            GearMath.axeCost(recipe.inputGrade()) / 2));
                });
        builder.addSlot(RecipeIngredientRole.CATALYST, 70, 79).setStandardSlotBackground().setSlotName("hammer")
                .addItemStacks(ForgeJeiRecipes.tools(ToolFamily.WAR_HAMMER)).addRichTooltipCallback((slot, tooltip) -> {
                    tooltip.add(Component.translatable("jei.modern_ite.forge_tools"));
                    tooltip.add(Component.translatable("jei.modern_ite.forge_wear", GearMath.hammerCost(recipe.inputGrade()),
                            GearMath.hammerCost(recipe.inputGrade()) / 2));
                });
    }

    @Override
    public void draw(ForgeJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        drawFit(graphics, step(recipe), 5, 2);
        arrow.draw(graphics, 109, 49);
        drawFit(graphics, Component.translatable("container.modern_ite.axe"), 24, 83, 42);
        drawFit(graphics, Component.translatable("container.modern_ite.hammer"), 89, 83, 75);
        drawFit(graphics, Component.translatable("jei.modern_ite.forge_time", seconds(recipe.stats().minimumTicks()),
                seconds(recipe.stats().maximumTicks())), 5, 103);
        drawFit(graphics, Component.translatable("jei.modern_ite.forge_failure", recipe.stats().minimumFailure(),
                recipe.stats().maximumFailure()), 5, 116);
    }

    @Override public ResourceLocation getRegistryName(ForgeJeiRecipe recipe) { return recipe.id(); }

    private static Component step(ForgeJeiRecipe recipe) {
        return Component.translatable("jei.modern_ite.forging_step", recipe.inputGrade(), recipe.outputGrade())
                .append(" · ").append(Component.translatable(recipe.universal()
                        ? "item.modern_ite.universal_enhance_stone" : "jei.modern_ite.forge_normal"));
    }

    private static String seconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    private static void drawFit(GuiGraphics graphics, Component text, int x, int y) {
        drawFit(graphics, text, x, y, WIDTH - x - 5);
    }

    private static void drawFit(GuiGraphics graphics, Component text, int x, int y, int width) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, width / (float)Math.max(1, font.width(text)));
        graphics.pose().pushPose(); graphics.pose().translate(x, y, 0); graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, 0xFF404040, false); graphics.pose().popPose();
    }
}
