package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Displays the server's remaining Underworld fatigue recovery time. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class UnderworldCooldownHud {
    private static ClientPacketListener connection;
    private static Component countdown;

    private UnderworldCooldownHud() {}

    public static void receive(Connection source, int secondsRemaining) {
        LocalPlayer local = Minecraft.getInstance().player;
        if (local == null || local.connection.getConnection() != source) return;
        if (secondsRemaining <= 0) {
            clear();
            return;
        }
        connection = local.connection;
        String time = String.format(Locale.ROOT, "%02d:%02d", secondsRemaining / 60, secondsRemaining % 60);
        countdown = Component.translatable("hud.modern_ite.underworld_cooldown", time);
    }

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR) || countdown == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null || player.connection != connection || !player.isAlive()
                || player.isCreative() || player.isSpectator()
                || player.level().dimension().equals(MiteWorldDimensions.UNDERWORLD)) return;

        // MITE's F3 overlay uses the first four rows in the top-left corner.
        int y = minecraft.gui.getDebugOverlay().showDebugScreen() ? 46 : 6;
        event.getGuiGraphics().drawString(minecraft.font, countdown, 6, y, 0xFFFFFF, true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        connection = null;
        countdown = null;
    }
}
