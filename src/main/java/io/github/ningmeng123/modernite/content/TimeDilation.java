package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** One player clock at one fifth speed, independent of the world's clock. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class TimeDilation {
    public static final int TICK_DIVISOR = 5;

    private TimeDilation() {}

    public static boolean active(Player player) {
        if (player == null) return false;
        var effect = player.getEffect(ExtremeEffects.VALUES.get("time_dilation"));
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && effect != null && effect.getDuration() != 0;
    }

    @SubscribeEvent
    public static void applicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().equals(ExtremeEffects.VALUES.get("time_dilation"))
                && (!(event.getEntity() instanceof Player player) || player.isCreative() || player.isSpectator()))
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    public static boolean playerStep(Player player) {
        return !active(player) || ((PlayerTimeDilationState) player).modernite$timePhase() == TICK_DIVISOR - 1;
    }
}
