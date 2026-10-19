package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Native armor geometry for transparent, alpha-masked starlight. @author NingMeng123 */
public final class VibraniumCosmicModel<T extends LivingEntity> extends HumanoidArmorModel<T> {
    public static final ModelLayerLocation OUTER_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "vibranium_cosmic"), "outer");
    public static final ModelLayerLocation INNER_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "vibranium_cosmic"), "inner");

    public VibraniumCosmicModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition outerLayer() {
        // Match native armor layering, with a tiny .01-pixel surface bias on every face.
        // HumanoidArmorModel also subtracts .1 from leg inflation, giving .91 here.
        return LayerDefinition.create(HumanoidArmorModel.createBodyLayer(new CubeDeformation(1.01F)), 64, 32);
    }

    public static LayerDefinition innerLayer() {
        return LayerDefinition.create(HumanoidArmorModel.createBodyLayer(new CubeDeformation(.51F)), 64, 32);
    }

    public void copyPose(HumanoidModel<T> parent, boolean inner) {
        // Copy the final native pose and child scaling, without another animation pass.
        parent.copyPropertiesTo(this);
        head.visible = !inner && parent.head.visible;
        // This is the native ARMOR hat cube, using armor UVs rather than player-skin UVs.
        // Current vibranium hat UVs are entirely transparent. The shader's real alpha
        // mask keeps them empty, and also preserves face holes in the head cube.
        hat.visible = !inner && parent.hat.visible;
        body.visible = parent.body.visible;
        rightArm.visible = !inner && parent.rightArm.visible;
        leftArm.visible = !inner && parent.leftArm.visible;
        rightLeg.visible = parent.rightLeg.visible;
        leftLeg.visible = parent.leftLeg.visible;
        // Native layer_1/layer_2 alpha masks separate boots, leggings, sleeves and face holes.
        // Keep the inner body: it supplies the waist beneath the chestplate's cutout hem.
    }
}
