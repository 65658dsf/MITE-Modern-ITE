package io.github.ningmeng123.modernite;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** A single source-rules baseline is shared by the isolated GameTest server. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class SpawnBalanceTestSetup {
    private SpawnBalanceTestSetup() {}
    @SubscribeEvent
    public static void prepare(ServerStartedEvent event) {
        if(!(event.getServer() instanceof GameTestServer))return;
        // Export/reload fixtures restore this isolated file. Keep its disk and live baselines identical.
        IteConfig.SPEC.save();IteConfig.refresh();
        var level=event.getServer().overworld();
        level.setDayTime(64*24_000L+18_000L);
        BloodMoonInvasionData.get(level).restoreProgress(65);
        ExtremeWorldState.restoreGraceEnded(level,true);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
    }
}
