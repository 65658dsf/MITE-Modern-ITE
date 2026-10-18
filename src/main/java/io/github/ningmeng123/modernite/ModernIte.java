package io.github.ningmeng123.modernite;

import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Modern implementation of the selected Extreme LH rules. @author NingMeng123 */
@Mod(ModernIte.MOD_ID)
public final class ModernIte {
    public static final String MOD_ID = "modern_ite";
    public ModernIte(IEventBus bus) {
        ExtremeContent.register(bus);
        ExtremeGear.register(bus);
        ExtremeMobs.register(bus);
        io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments.register(bus);
        io.github.ningmeng123.modernite.economy.ExtremeEconomy.register(bus);
        ExtremeRules.register(bus);
    }
}
