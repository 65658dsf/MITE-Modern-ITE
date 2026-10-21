package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.RabidDogContent;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Reuses wolf artwork with an enlarged, twitching head and an open muzzle. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RabidDogRendering {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "rabid_dog"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_angry.png");

    private RabidDogRendering() {}

    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, RabidDogRendering::layer);
    }

    public static LayerDefinition layer() {
        var mesh = WolfModel.createMeshDefinition(CubeDeformation.NONE);
        var head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("real_head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2, -3, -2, 6, 6, 4)
                .texOffs(16, 14).addBox(-2, -5, 0, 2, 2, 1)
                .texOffs(16, 14).addBox(2, -4.5F, 0, 2, 2, 1)
                .texOffs(0, 10).addBox(-.5F, -.5F, -5, 3, 2, 4)
                .texOffs(0, 12).addBox(-.5F, 2.2F, -4.5F, 3, 1, 3), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RabidDogContent.RABID_DOG.get(), context -> new WolfRenderer(context) {
            { this.model = new Model(context.bakeLayer(LAYER)); }
            @Override public ResourceLocation getTextureLocation(Wolf wolf) { return TEXTURE; }
        });
    }

    public static final class Model extends WolfModel<Wolf> {
        private final ModelPart head;

        public Model(ModelPart root) {
            super(root);
            head = root.getChild("head");
            head.xScale = head.yScale = head.zScale = 1.2F;
        }

        @Override public void setupAnim(Wolf wolf, float limb, float amount, float age, float yaw, float pitch) {
            super.setupAnim(wolf, limb, amount, age, yaw, pitch);
            head.zRot = Mth.sin(wolf.tickCount * .8F) * .06F;
        }
    }
}
