package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.curse.WitchCurseData;
import io.github.ningmeng123.mite.curse.WitchCursing;
import io.github.ningmeng123.mite.network.CurseNotificationPayload;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.Witch;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** The seventeenth witch curse shares the original timing and lifting lifecycle. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class DwarfCurse {
    private DwarfCurse() {}

    public static boolean selected(Witch witch, String username) {
        int hash = 0;
        for (int i = 0; i < username.length(); i++) hash += username.charAt(i) * i;
        // Keep a stable witch/player pairing across ticks and reloads, like the base selection.
        return new Random((long) (WitchCursing.seed(witch) + hash) ^ 0x44574152464CL).nextInt(17) == 16;
    }

    public static boolean scheduleSelected(ServerPlayer player, Witch witch, R196Curse original, int delay) {
        if (original == null || player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable) return false;
        return selected(witch, player.getGameProfile().getName()) ? schedule(player, witch, delay)
                : PlayerCurses.schedule(player, witch, original, delay);
    }

    public static boolean schedule(ServerPlayer player, Witch witch, int delay) {
        if (!witch.isAlive() || player.isDeadOrDying() || witch.level() != player.level() || delay < 0
                || WitchCurseData.get(player.serverLevel()).entry(player.getUUID()) != null) return false;
        return DwarfCurseData.get(player.serverLevel()).add(new DwarfCurseData.Entry(player.getUUID(), witch.getUUID(),
                player.server.overworld().getGameTime() + delay, false));
    }

    public static boolean pendingOrActive(ServerPlayer player) {
        return DwarfCurseData.get(player.serverLevel()).entry(player.getUUID()) != null;
    }

    /** MITE milk bowls and buckets call removeAllEffects, bypassing NeoForge's cure lists. */
    @SubscribeEvent public static void keepUntilDispelled(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffect().equals(ExtremeEffects.VALUES.get("dwarf_curse")) && pendingOrActive(player)) {
            event.setCanceled(true);
        }
    }

    public static void reconcile(ServerPlayer player) {
        var data = DwarfCurseData.get(player.serverLevel());
        var entry = data.entry(player.getUUID());
        boolean realize = entry != null && !entry.realized() && player.isAlive()
                && entry.realizationTime() <= player.server.overworld().getGameTime();
        if (realize) {
            entry = entry.realize();
            data.replace(entry);
        }
        var effect = ExtremeEffects.VALUES.get("dwarf_curse");
        if (entry != null && entry.realized() && player.isAlive()) {
            float healthBefore = player.getHealth();
            if (!player.hasEffect(effect)) {
                player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
                player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
                player.refreshDimensions();
            }
            if (realize) {
                // Exactly once, at realization; reloads and ordinary reconciliation never hurt or heal again.
                player.setHealth(Math.min(healthBefore * .5F, player.getMaxHealth()));
                CurseNotificationPayload.send(player, true);
                player.sendSystemMessage(Component.translatable("message.modern_ite.dwarf_curse"));
            }
        } else if (entry == null && player.hasEffect(effect)) {
            player.removeEffect(effect);
            player.refreshDimensions();
        }
    }

    public static boolean cure(ServerPlayer player) {
        boolean removed = DwarfCurseData.get(player.serverLevel()).remove(player.getUUID());
        reconcile(player);
        if (removed) {
            CurseNotificationPayload.send(player, false);
            player.sendSystemMessage(Component.translatable("message.modern_ite.dwarf_curse_lifted"));
        }
        return removed;
    }

    public static void removeForWitch(Witch witch) {
        if (witch.level().isClientSide || witch.getServer() == null) return;
        if (DwarfCurseData.get(witch.getServer().overworld()).removeForWitch(witch.getUUID())) {
            for (ServerPlayer player : witch.getServer().getPlayerList().getPlayers()) {
                boolean active = player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse"));
                reconcile(player);
                if (active && !player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse"))) {
                    CurseNotificationPayload.send(player, false);
                    player.sendSystemMessage(Component.translatable("message.modern_ite.dwarf_curse_lifted"));
                }
            }
        }
    }
}
