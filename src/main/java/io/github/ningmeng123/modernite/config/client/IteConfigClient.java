package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.mite.client.config.MiteSettingsScreen;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Native Mod Menu integration and the entry in MITE's settings. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class IteConfigClient {
    private static ModContainer container;

    private IteConfigClient() {}

    public static void register(ModContainer mod) {
        container = mod;
        mod.registerExtensionPoint(IConfigScreenFactory.class, IteSettingsScreen::new);
    }

    public static boolean editable() {
        var minecraft = Minecraft.getInstance();
        return minecraft.getConnection() == null || minecraft.hasSingleplayerServer();
    }

    public static void receive(Map<String, Object> values) {
        var minecraft = Minecraft.getInstance();
        // The integrated server shares local configuration with its client.
        if (minecraft.getConnection() != null && !minecraft.hasSingleplayerServer())
            IteConfig.receiveServerSnapshot(values);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        IteConfig.clearServerSnapshot();
    }

    @SubscribeEvent
    public static void addMiteSettingsEntry(ScreenEvent.Init.Post event) {
        if (container == null || !(event.getScreen() instanceof MiteSettingsScreen screen)) return;
        int width = Math.min(130, Math.max(90, (screen.width - 24) / 2));
        var minecraft = Minecraft.getInstance();
        int titleLeft = (screen.width - minecraft.font.width(screen.getTitle())) / 2;
        boolean compactHeader = 16 + width > titleLeft;
        // Give the title its own line on the minimum 320px GUI, including MITE's export control.
        if (compactHeader) for (var child : screen.children())
            if (child instanceof Button existing && existing.getY() == 8) existing.setY(34);
        var button = Button.builder(Component.translatable("modern_ite.config.open"),
                ignored -> Minecraft.getInstance().setScreen(new IteSettingsScreen(container, screen)))
                .bounds(8, compactHeader ? 34 : 8, width, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("modern_ite.config.open.tooltip")));
        event.addListener(button);
    }
}
