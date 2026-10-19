package io.github.ningmeng123.modernite;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Legacy combat fixtures run after opening progression; balance tests isolate their own dates. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class SpawnBalanceTestSetup {
    private SpawnBalanceTestSetup() {}
    @SubscribeEvent
    public static void prepare(ServerStartedEvent event) {
        if(!(event.getServer() instanceof GameTestServer))return;
        var level=event.getServer().overworld();
        level.setDayTime(32*24_000L+18_000L);
        BloodMoonInvasionData.get(level).restoreProgress(33);
        ExtremeWorldState.restoreGraceEnded(level,true);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
    }
}
