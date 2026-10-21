package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Install the bucket effect layer for both regular and slim player skins. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BucketHeadRendering {
    private BucketHeadRendering() {}

    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
        Set<PlayerRenderer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer && seen.add(renderer))
                renderer.addLayer(new BucketHeadLayer(renderer));
        }
    }
}
