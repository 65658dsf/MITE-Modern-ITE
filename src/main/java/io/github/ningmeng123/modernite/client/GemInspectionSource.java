package io.github.ningmeng123.modernite.client;

import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.gear.EnhanceGemItem;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Preserve the inspected stack while Punchy visually transfers it between hands. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class GemInspectionSource {
    public static final String SENTINEL = "modern_ite:inspection_source";
    private static final String CLIP = "modern_ite_gem_inspect";
    private static PunchyAccess access;
    private static boolean unavailable;
    private static ItemStack snapshot = ItemStack.EMPTY;
    private static UUID owner;
    private static long playback = Long.MIN_VALUE;

    private GemInspectionSource() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /** Called before native item overrides are rebuilt, including stop/world-exit cleanup. */
    public static void beforeOverrideUpdate(Minecraft minecraft, HumanoidArm sourceArm) {
        if (unavailable || minecraft == null || minecraft.player == null || sourceArm == null) {
            clear();
            return;
        }
        try {
            if (access == null) access = PunchyAccess.find();
            Object clip = access.currentClip.invoke(access.handler);
            if (clip == null || !Boolean.TRUE.equals(access.playing.invoke(access.handler))
                    || !isInspectionClip((String) access.clipName.invoke(clip))) {
                clear();
                return;
            }
            long currentPlayback = ((Number) access.playbackId.invoke(access.handler)).longValue();
            UUID playerId = minecraft.player.getUUID();
            if (currentPlayback == playback && playerId.equals(owner)) return;

            // The source is the physical arm chosen by Punchy. This also handles
            // left-dominant players and a gem inspected from the offhand.
            InteractionHand hand = sourceArm == minecraft.player.getMainArm()
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack held = minecraft.player.getItemInHand(hand);
            if (!isGem(held)) {
                clear();
                return;
            }
            snapshot = held.copy();
            owner = playerId;
            playback = currentPlayback;
        } catch (ReflectiveOperationException | LinkageError failure) {
            unavailable = true;
            clear();
            LogUtils.getLogger().warn("Modern ITE could not read Punchy's gemstone inspection state", failure);
        }
    }

    /** Null means this is not our active sentinel; native resolution remains untouched. */
    public static ItemStack resolve(String itemId) {
        return SENTINEL.equals(itemId) && !snapshot.isEmpty() ? snapshot.copy() : null;
    }

    private static boolean isInspectionClip(String name) {
        return CLIP.equals(name) || (CLIP + "_right").equals(name) || (CLIP + "_left").equals(name);
    }

    private static boolean isGem(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof EnhanceGemItem
                || ExtremeGear.SHARDS.stream().anyMatch(shard -> stack.is(shard.get())));
    }

    private static void clear() {
        snapshot = ItemStack.EMPTY;
        owner = null;
        playback = Long.MIN_VALUE;
    }

    /** Reflection keeps the optional Punchy classes out of the mod's compile/runtime dependencies. */
    private record PunchyAccess(Object handler, Method currentClip, Method playing, Method playbackId, Method clipName) {
        private static PunchyAccess find() throws ReflectiveOperationException {
            Class<?> manager = Class.forName("punchy.client.animation.PunchyAnimationManager");
            Object handler = manager.getField("POSE_HANDLER").get(null);
            Class<?> handlerClass = handler.getClass();
            Method currentClip = handlerClass.getMethod("getCurrentClip");
            return new PunchyAccess(handler, currentClip, handlerClass.getMethod("isPlaying"),
                    handlerClass.getMethod("getPlaybackId"), currentClip.getReturnType().getMethod("getName"));
        }
    }
}
