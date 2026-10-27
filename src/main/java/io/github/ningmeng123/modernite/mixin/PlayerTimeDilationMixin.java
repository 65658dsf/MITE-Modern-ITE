package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.modernite.content.TimeDilation;
import io.github.ningmeng123.modernite.content.PlayerTimeDilationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared client/server player clock, including physics, item use and attack recovery. @author NingMeng123 */
@Mixin(Player.class)
public abstract class PlayerTimeDilationMixin extends LivingEntity implements PlayerTimeDilationState {
    @Unique private int modernite$timePhase;
    @Unique private Vec3 modernite$previousTimePosition;
    @Unique private int modernite$timeVisualAge;

    public int modernite$timePhase() { return modernite$timePhase; }
    public Vec3 modernite$previousTimePosition() { return modernite$previousTimePosition; }
    public int modernite$timeVisualAge() { return modernite$timeVisualAge; }

    protected PlayerTimeDilationMixin(EntityType<? extends LivingEntity> type, Level level) { super(type, level); }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void modernite$slowPlayerClock(CallbackInfo ci) {
        if (!TimeDilation.active((Player) (Object) this)) {
            modernite$timePhase = 0;
            modernite$previousTimePosition = null;
            return;
        }
        if (modernite$previousTimePosition == null) {
            modernite$previousTimePosition = position();
            modernite$timeVisualAge = tickCount;
        }
        modernite$timePhase = (modernite$timePhase + 1) % TimeDilation.TICK_DIVISOR;
        if (modernite$timePhase == 0) {
            modernite$previousTimePosition = position();
            modernite$timeVisualAge++;
            return;
        }
        // Effects keep real-world duration, including this effect's own expiry and native cures.
        // Never cancel the network connection tick or the server's health/menu synchronization.
        tickEffects();
        ci.cancel();
    }
}
