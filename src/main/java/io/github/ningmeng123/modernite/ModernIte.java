package io.github.ningmeng123.modernite;

import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.world.ConfigurableNetherOre;

/** Modern implementation of the selected Extreme LH rules. @author NingMeng123 */
@Mod(ModernIte.MOD_ID)
public final class ModernIte {
    public static final String MOD_ID = "modern_ite";
    public ModernIte(IEventBus bus, ModContainer container) {
        IteConfig.migrateLegacySpawningConfig(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("modern_ite-common.toml"));
        container.registerConfig(ModConfig.Type.COMMON, IteConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) io.github.ningmeng123.modernite.config.client.IteConfigClient.register(container);
        ConfigurableNetherOre.register(bus);
        ExtremeContent.register(bus);
        io.github.ningmeng123.modernite.content.metal.MetalUpgradingContent.register(bus);
        ExtremeGear.register(bus);
        ExtremeMobs.register(bus);
        io.github.ningmeng123.modernite.entity.InfernalExplosionPayload.register(bus);
        io.github.ningmeng123.modernite.entity.RabidDogContent.register(bus);
        io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments.register(bus);
        io.github.ningmeng123.modernite.economy.ExtremeEconomy.register(bus);
        ExtremeRules.register(bus);
        bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, io.github.ningmeng123.modernite.item.WholeFlint::creative);
    }
}
