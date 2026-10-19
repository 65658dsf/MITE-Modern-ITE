package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Starlight over native vibranium armor for both player skins and humanoid creatures. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class ProtectionAuraRendering {
    private ProtectionAuraRendering() {}

    @SubscribeEvent public static void definitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VibraniumCosmicModel.OUTER_LAYER, VibraniumCosmicModel::outerLayer);
        event.registerLayerDefinition(VibraniumCosmicModel.INNER_LAYER, VibraniumCosmicModel::innerLayer);
    }

    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
        Set<EntityRenderer<?>> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for (var type : event.getEntityTypes()) attach(event,event.getRenderer(type),seen);
        for (var skin : event.getSkins()) attach(event,event.getSkin(skin),seen);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void attach(EntityRenderersEvent.AddLayers event, EntityRenderer<?> renderer, Set<EntityRenderer<?>> seen) {
        if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel<?> && seen.add(renderer))
            living.addLayer(new VibraniumGildingLayer(living,
                    event.getEntityModels().bakeLayer(VibraniumCosmicModel.OUTER_LAYER),
                    event.getEntityModels().bakeLayer(VibraniumCosmicModel.INNER_LAYER)));
    }
}
