package io.github.ningmeng123.modernite.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.registry.MiteTools;
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

/** Extreme tools from mithril onward advance through the shared smithing routes. @author NingMeng123 */
@Mixin(RecipeManager.class)
public abstract class ToolSmithingRecipesMixin {
    @Shadow @Final private HolderLookup.Provider registries;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;
    @Shadow private Multimap<RecipeType<?>, RecipeHolder<?>> byType;

    @Inject(method = {"apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            "replaceRecipes"}, at = @At("TAIL"))
    private void modernite$smithingOnlyTools(CallbackInfo ci) {
        var names = ImmutableMap.<ResourceLocation, RecipeHolder<?>>builder();
        var types = ImmutableMultimap.<RecipeType<?>, RecipeHolder<?>>builder();
        for (var holder : byName.values()) {
            if (holder.value() instanceof CraftingRecipe) {
                var result = holder.value().getResultItem(registries);
                var tool = MiteTools.definition(result);
                String material = tool == null ? SimplySwordsCompat.material(result) : tool.material();
                if ("mithril".equals(material) || "adamantium".equals(material) || "vibranium".equals(material)) continue;
            }
            names.put(holder.id(), holder);
            types.put(holder.value().getType(), holder);
        }
        byName = names.build();
        byType = types.build();
    }
}
