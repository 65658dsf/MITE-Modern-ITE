package io.github.ningmeng123.modernite.client.gear;

import io.github.ningmeng123.modernite.gear.ExtremeGear;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only screen registration. @author NingMeng123 */
@EventBusSubscriber(modid = ExtremeGear.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GearClient {
    private GearClient() {}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ExtremeGear.STATION_MENU.get(), GearStationScreen::new);
        event.register(ExtremeGear.FORGE_MENU.get(), GearStationScreen::new);
    }
}
