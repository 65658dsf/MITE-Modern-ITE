package io.github.ningmeng123.modernite.content;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.wrapper.*;
/** Sided item access, with player ownership enforced by chest menus. @author NingMeng123 */
public final class ExtremeCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ExtremeContent.FURNACE_ENTITY.get(),(entity,side)->side==null?new InvWrapper(entity):new SidedInvWrapper(entity,side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ExtremeContent.CHEST_ENTITY.get(),(entity,side)->new InvWrapper(entity));
    }
}
