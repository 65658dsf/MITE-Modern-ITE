package io.github.ningmeng123.modernite.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.github.ningmeng123.modernite.crafting.EquipmentSmithingRules;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extreme equipment above iron advances through the shared smithing routes. @author NingMeng123 */
@Mixin(RecipeManager.class)
public abstract class EquipmentSmithingRecipesMixin {
    @Shadow @Final private HolderLookup.Provider registries;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;
    @Shadow private Multimap<RecipeType<?>, RecipeHolder<?>> byType;

    @Inject(method = {"apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            "replaceRecipes"}, at = @At("TAIL"))
    private void modernite$smithingOnlyEquipment(CallbackInfo ci) {
        var names = ImmutableMap.<ResourceLocation, RecipeHolder<?>>builder();
        var types = ImmutableMultimap.<RecipeType<?>, RecipeHolder<?>>builder();
        for (var holder : byName.values()) {
            if (holder.value() instanceof CraftingRecipe
                    && EquipmentSmithingRules.requiresSmithing(holder.value().getResultItem(registries))) continue;
            names.put(holder.id(), holder);
            types.put(holder.value().getType(), holder);
        }
        byName = names.build();
        byType = types.build();
    }
}
