package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** The inside of the bucket leaves only the bottom quarter of the view open. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT)
public final class BucketHeadOverlay {
    private BucketHeadOverlay() {}

    public static boolean active(Minecraft mc) {
        var player=mc.player;
        return player!=null&&player.isAlive()&&!player.isSpectator()
                &&mc.getCameraEntity()==player
                &&player.hasEffect(ExtremeEffects.VALUES.get("bucket_head"));
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderGuiEvent.Post event) {
        if(active(Minecraft.getInstance()))draw(event.getGuiGraphics());
    }

    public static void draw(GuiGraphics gui) {
        int width=gui.guiWidth(),bottom=(int)Math.ceil(gui.guiHeight()*.75D);
        // Post-HUD rendering also hides the crosshair and remains active when F1 hides the HUD.
        gui.pose().pushPose();
        gui.pose().translate(0,0,1000);
        gui.fill(0,0,width,bottom,0xff17191c);
        gui.fill(0,Math.max(0,bottom-4),width,bottom-2,0xff363b40);
        gui.fill(0,bottom-2,width,bottom,0xff0c0d0f);
        gui.flush();
        gui.pose().popPose();
    }
}
