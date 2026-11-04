package io.github.ningmeng123.modernite.content.wither;

import io.github.ningmeng123.modernite.ModernIte;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Enhanced Wither treasure belongs to the ITE item registry. @author NingMeng123 */
public final class WitherTreasureContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModernIte.MOD_ID);
    public static final DeferredItem<WitherTreasureItem> TREASURE = ITEMS.registerItem("wither_treasure", WitherTreasureItem::new);

    private WitherTreasureContent() {}

    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
