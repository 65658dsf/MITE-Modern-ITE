package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** New ores retain the source mithril-or-better threshold. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class ExtremeHarvestRules {
    private static boolean managed(BlockState state){return state.is(ExtremeContent.RED_ORE.get())||state.is(ExtremeContent.NETHER_ADAMANTIUM.get());}
    public static boolean qualified(ItemStack stack){
        var d=MiteTools.definition(stack);
        return d!=null ? d.harvestLevel()>=4&&(d.family()==ToolFamily.PICKAXE||d.family()==ToolFamily.WAR_HAMMER)
                : stack.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void harvest(PlayerEvent.HarvestCheck event){
        if(managed(event.getTargetBlock())&&!qualified(event.getEntity().getMainHandItem()))event.setCanHarvest(false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void speed(PlayerEvent.BreakSpeed event){
        if(managed(event.getState())&&!qualified(event.getEntity().getMainHandItem()))event.setNewSpeed(Math.min(event.getNewSpeed(),1F));
    }
}
