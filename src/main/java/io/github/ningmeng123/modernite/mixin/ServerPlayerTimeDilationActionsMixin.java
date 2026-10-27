package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.modernite.content.TimeDilation;
import java.util.ArrayDeque;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defer gameplay packets together while connection, chat and teleport acknowledgements stay live. @author NingMeng123 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerTimeDilationActionsMixin {
    @Shadow public ServerPlayer player;
    @Unique private final ArrayDeque<Packet<ServerGamePacketListener>> modernite$pendingActions = new ArrayDeque<>();
    @Unique private boolean modernite$replayingActions;
    @Unique private ServerPlayer modernite$actionOwner;
    @Unique private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> modernite$actionDimension;

    @Inject(method = "tick", at = @At("HEAD"))
    private void modernite$actionTick(CallbackInfo ci) {
        modernite$reconcileOwner();
        if (TimeDilation.playerStep(player)) modernite$flushActions();
    }

    @Inject(method = {"handlePlayerAction", "handleUseItem", "handleUseItemOn", "handleInteract",
            "handleSetCarriedItem", "handleAnimate", "handlePlayerCommand", "handlePickItem",
            "handleContainerClick", "handleContainerButtonClick", "handleContainerClose", "handlePlaceRecipe",
            "handleSelectTrade", "handleSetBeaconPacket", "handleRenameItem", "handleContainerSlotStateChanged",
            "handleEditBook", "handleSignUpdate", "handlePlayerAbilities", "handlePaddleBoat", "handlePlayerInput"},
            at = @At("HEAD"), cancellable = true)
    private void modernite$queueAction(@Coerce Packet<ServerGamePacketListener> packet, CallbackInfo ci) {
        // The native handler schedules the network-thread invocation onto the server first.
        if (!player.server.isSameThread() || modernite$replayingActions) return;
        modernite$reconcileOwner();
        if (TimeDilation.playerStep(player)) {
            modernite$flushActions();
            return;
        }
        modernite$pendingActions.addLast(packet);
        ci.cancel();
    }

    @Unique private void modernite$flushActions() {
        if (modernite$replayingActions || modernite$pendingActions.isEmpty()) return;
        modernite$replayingActions = true;
        try {
            while (!modernite$pendingActions.isEmpty()) {
                modernite$pendingActions.removeFirst().handle((ServerGamePacketListener) (Object) this);
                modernite$reconcileOwner();
            }
        } finally { modernite$replayingActions = false; }
    }

    @Unique private void modernite$reconcileOwner() {
        if (modernite$actionOwner != player || modernite$actionDimension != player.level().dimension()) {
            modernite$pendingActions.clear();
            modernite$actionOwner = player;
            modernite$actionDimension = player.level().dimension();
        }
    }
}
