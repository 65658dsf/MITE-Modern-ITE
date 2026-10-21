package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.Rabies;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/** Camera and input follow the synchronized effect, without changing user settings. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class RabiesControls {
    private RabiesControls() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (minecraft.isPaused() || !Rabies.active(player)) return;
        if (minecraft.gameMode != null) minecraft.gameMode.stopDestroyBlock();
        Rabies.turn(player, Rabies.target(player));
        // The server broadcasts its swings to the affected player as well.
        // Never send an extra client attack or mutate the attack key binding.
    }

    @SubscribeEvent
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && Rabies.active(Minecraft.getInstance().player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
