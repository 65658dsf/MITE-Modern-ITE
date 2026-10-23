package io.github.ningmeng123.modernite;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Legacy fixtures explicitly use custom rules; reference suites select and restore their own preset. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class SpawnBalanceTestSetup {
    private SpawnBalanceTestSetup() {}
    @SubscribeEvent
    public static void prepare(ServerStartedEvent event) {
        if(!(event.getServer() instanceof GameTestServer))return;
        // Export/reload fixtures restore this isolated file. Keep its disk and live baselines identical.
        IteConfig.setting("reference_spawning").set(false);IteConfig.SPEC.save();IteConfig.refresh();
        var level=event.getServer().overworld();
        level.setDayTime(32*24_000L+18_000L);
        BloodMoonInvasionData.get(level).restoreProgress(33);
        ExtremeWorldState.restoreGraceEnded(level,true);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
    }
}
