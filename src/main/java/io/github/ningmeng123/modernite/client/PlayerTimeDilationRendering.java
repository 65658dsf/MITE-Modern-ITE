package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.content.PlayerTimeDilationState;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Interpolate across a full slow-player step while the rest of the scene renders normally. @author NingMeng123 */
public final class PlayerTimeDilationRendering {
    private PlayerTimeDilationRendering() {}

    public static float partial(Entity entity, float partial) {
        if (!(entity instanceof Player player) || !TimeDilation.active(player)) return partial;
        return (((PlayerTimeDilationState) player).modernite$timePhase() + partial) / TimeDilation.TICK_DIVISOR;
    }

    public static Vec3 position(Player player, float partial) {
        Vec3 previous = ((PlayerTimeDilationState) player).modernite$previousTimePosition();
        // Teleports and dimension changes snap instead of drawing a path across the world.
        if (previous == null || previous.distanceToSqr(player.position()) > 64) return player.position();
        return previous.lerp(player.position(), partial(player, partial));
    }
}
