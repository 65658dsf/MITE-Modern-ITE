package io.github.ningmeng123.modernite.client.gear;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Register socket decorations after native armor and its starlight for both skins and humanoid wearers. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ChestGemRendering {
    private ChestGemRendering() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void layers(EntityRenderersEvent.AddLayers event) {
        Set<EntityRenderer<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var type : event.getEntityTypes()) attach(event.getRenderer(type), seen);
        for (var skin : event.getSkins()) attach(event.getSkin(skin), seen);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void attach(EntityRenderer<?> renderer, Set<EntityRenderer<?>> seen) {
        if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel<?>
                && seen.add(renderer)) living.addLayer(new ChestGemLayer(living));
    }
}
