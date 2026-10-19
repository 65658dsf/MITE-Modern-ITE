package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Share equipment nugget repairs, anvil capacity and exact wear with material lanterns. @author NingMeng123 */
@Mixin(value = MaterialAnvilRules.class, remap = false)
public abstract class LanternAnvilMaterialMixin {
    @ModifyExpressionValue(method = "previewRepair", at = @At(value = "INVOKE", target =
        "Lio/github/ningmeng123/mite/crafting/MaterialAnvilRules;material(Lnet/minecraft/world/item/ItemStack;)Lio/github/ningmeng123/mite/crafting/MaterialAnvilRules$Material;"))
    private static MaterialAnvilRules.Material modernite$lanternRepairMaterial(MaterialAnvilRules.Material original,
            BlockState anvil, ItemStack first, ItemStack second) {
        if (first.getItem() instanceof DynamicCoreItem lantern) {
            return new MaterialAnvilRules.Material(lantern.material.id,
                DynamicCoreItem.REPAIR_COMPONENTS, false, false, false, false);
        }
        return original;
    }
}
