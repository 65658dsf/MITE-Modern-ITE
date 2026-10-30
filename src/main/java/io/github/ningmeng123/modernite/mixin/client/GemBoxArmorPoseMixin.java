package io.github.ningmeng123.modernite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.modernite.client.GemBoxArmPose;
import io.github.ningmeng123.modernite.client.GemBoxEmfPoseCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the final chosen armor model attached to the carrying skin arms. @author NingMeng123 */
@Mixin(HumanoidArmorLayer.class)
public abstract class GemBoxArmorPoseMixin<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    protected GemBoxArmorPoseMixin(RenderLayerParent<T, M> parent) { super(parent); }

    @WrapOperation(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/extensions/common/IClientItemExtensions;setupModelAnimations(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/client/model/Model;FFFFFF)V"))
    private void modernite$alignCarriedArmor(IClientItemExtensions extensions, LivingEntity entity, ItemStack stack,
            EquipmentSlot slot, Model model, float walk, float amount, float partial, float age, float yaw, float pitch,
            Operation<Void> original) {
        original.call(extensions, entity, stack, slot, model, walk, amount, partial, age, yaw, pitch);
        if (!GemBoxArmPose.active(entity) || !(model instanceof HumanoidModel<?> armor)) return;
        armor.rightArm.copyFrom(getParentModel().rightArm);
        armor.leftArm.copyFrom(getParentModel().leftArm);
        GemBoxEmfPoseCompat.captureArmor(armor, entity);
    }
}
