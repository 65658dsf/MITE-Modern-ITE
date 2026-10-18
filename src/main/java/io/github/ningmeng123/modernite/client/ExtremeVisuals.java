package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Local control and screen effects follow synchronized server potion state. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT)
public final class ExtremeVisuals {
    private static boolean has(LivingEntity entity,String id) { var effect=ExtremeEffects.VALUES.get(id);return entity!=null&&effect!=null&&entity.hasEffect(effect); }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var player=Minecraft.getInstance().player;if(player==null)return;
        if(has(player,"reversed"))event.setRoll(event.getRoll()+180);
        if(has(player,"chaos")){float time=player.tickCount+(float)event.getPartialTick();event.setRoll(event.getRoll()+(float)Math.sin(time*.2)*18);event.setYaw(event.getYaw()+(float)Math.sin(time*.13)*12);}
        if(has(player,"look_up")){player.setXRot(-90);event.setPitch(-90);}
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
        if(has(event.getEntity(),"reversed")){event.getInput().forwardImpulse=-event.getInput().forwardImpulse;event.getInput().leftImpulse=-event.getInput().leftImpulse;}
        if(has(event.getEntity(),"chaos")){float f=event.getInput().forwardImpulse;event.getInput().forwardImpulse=event.getInput().leftImpulse;event.getInput().leftImpulse=-f;}
    }
    @SubscribeEvent public static void overlay(RenderGuiLayerEvent.Post event) {
        if(!event.getName().equals(VanillaGuiLayers.HOTBAR))return;var player=Minecraft.getInstance().player;if(player==null)return;
        var gui=event.getGuiGraphics();int w=gui.guiWidth(),h=gui.guiHeight();
        if(has(player,"flash")){var effect=player.getEffect(ExtremeEffects.VALUES.get("flash"));int alpha=(int)(235F*Math.min(1F,effect.getDuration()/30F));gui.fill(0,0,w,h,(alpha<<24)|0xffffff);}
        if(has(player,"occlusion")){gui.fill(0,0,w,h/3,0xee1e0e00);gui.fill(0,h*2/3,w,h,0xee1e0e00);gui.fill(0,h/3,w/4,h*2/3,0xee1e0e00);gui.fill(w*3/4,h/3,w,h*2/3,0xee1e0e00);}
    }
    @SubscribeEvent public static void before(RenderLivingEvent.Pre<?,?> event) { if(has(event.getEntity(),"big_head")&&event.getRenderer().getModel() instanceof HumanoidModel<?> model){model.head.xScale=2;model.head.yScale=2;model.head.zScale=2;model.hat.xScale=2;model.hat.yScale=2;model.hat.zScale=2;} }
    @SubscribeEvent public static void after(RenderLivingEvent.Post<?,?> event) { if(event.getRenderer().getModel() instanceof HumanoidModel<?> model){model.head.xScale=1;model.head.yScale=1;model.head.zScale=1;model.hat.xScale=1;model.hat.yScale=1;model.hat.zScale=1;} }
}
