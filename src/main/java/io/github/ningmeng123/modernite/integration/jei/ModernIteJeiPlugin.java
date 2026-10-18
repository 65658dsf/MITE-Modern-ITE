package io.github.ningmeng123.modernite.integration.jei;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.client.gear.GearStationScreen;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

/** JEI discovers this optional client integration; the common mod never links to its API. @author NingMeng123 */
@JeiPlugin
public final class ModernIteJeiPlugin implements IModPlugin {
    public static final RecipeType<ForgeJeiRecipe> TYPE =
            RecipeType.create(ModernIte.MOD_ID, "forging", ForgeJeiRecipe.class);

    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "forging");
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ForgeJeiCategory(TYPE,
                new ItemStack(BuiltInRegistries.ITEM.get(ForgeJeiRecipes.profiles().values().iterator().next().id())),
                registration.getJeiHelpers().getGuiHelper()));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        VibraniumFurnaceJei.registerInformation(registration);
        try {
            registration.addRecipes(TYPE, ForgeJeiRecipes.build());
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger(ModernIte.MOD_ID).error("Unable to build Modern ITE forging recipes for JEI", failure);
        }
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GearStationScreen.class, new IGuiContainerHandler<GearStationScreen>() {
            @Override public Collection<IGuiClickableArea> getGuiClickableAreas(GearStationScreen screen,
                    double mouseX, double mouseY) {
                if (screen.getMenu().gemMode) return List.of();
                return List.of(IGuiClickableArea.createBasic(108, 52, 28, 16, TYPE));
            }
        });
    }
}
