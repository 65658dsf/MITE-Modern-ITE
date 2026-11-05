package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.modernite.gear.ForgeRecipes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Forged quality rewards remain maintainable without paying their unrelated crafting XP threshold. @author NingMeng123 */
@Mixin(value = CraftingQuality.class, remap = false)
public abstract class ForgeRepairMixin {
    @ModifyReturnValue(method = "canRepair", at = @At("RETURN"))
    private static boolean modernite$maintainEarnedForgeQuality(boolean original, Player player, ItemStack stack) {
        return original || ForgeRecipes.earnedRepairQuality(stack);
    }
}
