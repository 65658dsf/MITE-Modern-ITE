package io.github.ningmeng123.modernite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.entity.VibraniumCosmicShaders;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Defer one native glint until after the vibranium starlight. @author NingMeng123 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorGlintMixin {
    // NeoForge 21.1.259's six-float overload contains hasFoil; the older overload only delegates.
    @WrapOperation(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"),
            remap = false, require = 1)
    private boolean modernite$deferProtectionGlint(ItemStack stack, Operation<Boolean> original,
            @Local(argsOnly = true) LivingEntity entity,
            @Local(argsOnly = true) PoseStack pose,
            @Local(argsOnly = true) MultiBufferSource buffers,
            @Local(argsOnly = true) int light,
            @Local Model model) {
        boolean actual = original.call(stack);
        // Respect explicit suppression, including fixed-clock captures and player-hidden foil.
        if (!actual && Boolean.FALSE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) return false;
        // The configured native model is captured before its slot visibility or pose changes.
        // Non-qualifying armor and unsupported wrappers retain their exact original hasFoil value.
        var armorLayer = (HumanoidArmorLayer<?, ?, ?>)(Object)this;
        if (VibraniumCosmicShaders.deferGlint(entity, pose, buffers, model, light, armorLayer)) return false;
        return actual;
    }
}
