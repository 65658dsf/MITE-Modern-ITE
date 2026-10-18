package io.github.ningmeng123.modernite.integration.jei;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.furnace.FurnaceThermalMenuAccess;
import io.github.ningmeng123.mite.integration.jei.FurnaceJeiRecipe;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import java.util.stream.Stream;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Uses the base furnace category, batches and catalysts rather than registering a duplicate. @author NingMeng123 */
public final class VibraniumFurnaceJei {
    public static final String FURNACE_ID = "modern_ite:vibranium_furnace";
    public static final RecipeType<FurnaceJeiRecipe> TYPE = RecipeType.create("mite",
            "furnace_modern_ite_vibranium_furnace", FurnaceJeiRecipe.class);
    private VibraniumFurnaceJei() {}

    /** The existing menu protocol synchronizes capacity; this is the only native furnace screen with heat five. */
    public static boolean isVibraniumFurnaceScreen(AbstractContainerScreen<?> screen) {
        if (!(screen instanceof FurnaceScreen) || !(screen.getMenu() instanceof FurnaceThermalMenuAccess access))
            return false;
        var profile = FurnaceThermalController.profile(ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState());
        return profile != null && access.mite$getMaxHeat() == profile.maxHeat();
    }

    /** JEI's vanilla handler is registered first, so its hidden smelting category must not win this arrow click. */
    public static Stream<IGuiClickableArea> prioritizeClickArea(Stream<IGuiClickableArea> original,
            AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (!isVibraniumFurnaceScreen(screen) || mouseX < 78 || mouseX >= 106 || mouseY < 32 || mouseY >= 55)
            return original;
        return Stream.concat(Stream.of(IGuiClickableArea.createBasic(78, 32, 28, 23, TYPE)), original);
    }

    public static void registerInformation(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(ExtremeContent.VIBRANIUM_FURNACE.get()),
                Component.translatable("jei.modern_ite.vibranium_furnace_bonus"));
    }
}
