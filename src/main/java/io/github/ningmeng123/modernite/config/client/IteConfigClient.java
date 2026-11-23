package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.client.config.ConfigSearchIndex;
import io.github.ningmeng123.mite.client.config.MiteSettingsScreen;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Contributes ITE settings to MITE and receives synchronized gameplay values. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class IteConfigClient {
    private IteConfigClient() {}

    public static void register() {
        ConfigSearchIndex.registerProvider(ModernIte.MOD_ID, IteConfigSearch::entries);
        try {
            MiteSettingsScreen.class.getMethod("registerCategoryProvider", Function.class);
        } catch (NoSuchMethodException ignored) {
            // Older builds with the same required version still expose ITE via MITE's global search.
            return;
        }
        IteConfigMenu.register();
    }

    public static boolean editable() {
        var minecraft = Minecraft.getInstance();
        return minecraft.getConnection() == null || minecraft.hasSingleplayerServer();
    }

    static Set<String> sharedSpawningKeys() {
        return MiteExtensions.policy().spawningControls().stream()
                .map(MiteExtensions.SpawningControl::key).collect(Collectors.toUnmodifiableSet());
    }

    public static void receive(Map<String, Object> values, Map<String, Double> naturalSpawnRates) {
        receive(values, naturalSpawnRates, Map.of());
    }

    public static void receive(Map<String, Object> values, Map<String, Double> naturalSpawnRates, Map<String, Double> naturalSpawnWeights) {
        var minecraft = Minecraft.getInstance();
        // The integrated server shares local configuration with its client.
        if (minecraft.getConnection() != null && !minecraft.hasSingleplayerServer()) {
            IteConfig.receiveServerSnapshot(values);
            IteSpawnConfig.receiveServerSnapshot(naturalSpawnRates, naturalSpawnWeights);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        IteConfig.clearServerSnapshot();
    }

}
