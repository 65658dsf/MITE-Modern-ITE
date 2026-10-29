package io.github.ningmeng123.modernite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.gear.GemBoxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Share the same physically hinged lid between both hands and world player renders. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GemBoxModelRendering {
    public static final ModelResourceLocation BODY_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "item/gem_box_body"));
    public static final ModelResourceLocation LID_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "item/gem_box_lid"));
    private static final float HINGE_Y = 8.1F / 16F - .5F;
    private static final float HINGE_Z = 13F / 16F - .5F;

    private GemBoxModelRendering() {}

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(BODY_MODEL);
        event.register(LID_MODEL);
    }

    /** The lid is fully open before the reaching/searching/withdrawal phases start. */
    public static float lidAngle(float progress) {
        float opening = Mth.clamp((progress * GemBoxItem.OPEN_TICKS - 4F) / 12F, 0F, 1F);
        return 100F * opening * opening * (3F - 2F * opening);
    }

    /** Uses the caller's unscaled item coordinate frame; no camera or hand transforms are added. */
    public static void render(LivingEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float progress = GemBoxItem.openingProgress(stack, entity.level(), partialTick);
        var body = mc.getModelManager().getModel(BODY_MODEL);
        var lid = mc.getModelManager().getModel(LID_MODEL);
        if (progress <= 0F || body == mc.getModelManager().getMissingModel() || lid == mc.getModelManager().getMissingModel()) {
            mc.getItemRenderer().renderStatic(entity, stack, ItemDisplayContext.NONE, false, pose, buffers,
                    entity.level(), light, OverlayTexture.NO_OVERLAY, entity.getId());
            return;
        }
        mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers,
                light, OverlayTexture.NO_OVERLAY, body);
        pose.pushPose();
        try {
            // ItemRenderer centres each submodel by -.5. Rotate around the rear edge in that frame.
            pose.translate(0F, HINGE_Y, HINGE_Z);
            pose.mulPose(Axis.XP.rotationDegrees(lidAngle(progress)));
            pose.translate(0F, -HINGE_Y, -HINGE_Z);
            mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers,
                    light, OverlayTexture.NO_OVERLAY, lid);
        } finally {
            pose.popPose();
        }
    }
}
