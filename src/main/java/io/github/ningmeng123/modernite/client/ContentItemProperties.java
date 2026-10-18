package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Native crossbow state predicates for the source's independent crossbow identity. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ContentItemProperties {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        var item=ExtremeContent.CROSSBOW.get();
        ItemProperties.register(item,ResourceLocation.withDefaultNamespace("pull"),(s,l,e,n)->e==null||CrossbowItem.isCharged(s)?0F:(s.getUseDuration(e)-e.getUseItemRemainingTicks())/(float)CrossbowItem.getChargeDuration(s,e));
        ItemProperties.register(item,ResourceLocation.withDefaultNamespace("pulling"),(s,l,e,n)->e!=null&&e.isUsingItem()&&e.getUseItem()==s&&!CrossbowItem.isCharged(s)?1F:0F);
        ItemProperties.register(item,ResourceLocation.withDefaultNamespace("charged"),(s,l,e,n)->CrossbowItem.isCharged(s)?1F:0F);
        ItemProperties.register(item,ResourceLocation.withDefaultNamespace("firework"),(s,l,e,n)->s.getOrDefault(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.EMPTY).contains(Items.FIREWORK_ROCKET)?1F:0F);
    });}
}
