package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.content.TimeDilation;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

/** Buffer input until the next player step, preserving clicks and their modifier keys. @author NingMeng123 */
public final class PlayerTimeDilationActions {
    private static final ArrayDeque<Action> PENDING = new ArrayDeque<>();
    private static final Set<KeyMapping> CONSUMED = new HashSet<>();
    private static LocalPlayer owner;
    private static boolean replaying;
    private static int modifiers;

    private PlayerTimeDilationActions() {}

    public static void beforeTick() {
        var mc = Minecraft.getInstance();
        if (owner != mc.player) { PENDING.clear(); owner = mc.player; }
        CONSUMED.clear();
        if (!TimeDilation.playerStep(mc.player)) return;
        boolean limited = TimeDilation.active(mc.player), started = false;
        while (!PENDING.isEmpty()) {
            Action next = PENDING.getFirst();
            if (limited && started && next.starts()) break;
            PENDING.removeFirst();
            if (next.screen() != mc.screen) continue;
            started |= next.starts();
            replaying = true; modifiers = next.modifiers();
            try { next.run().run(); } finally { replaying = false; }
        }
    }

    public static boolean defer(Screen screen, boolean starts, Runnable action) {
        var player = Minecraft.getInstance().player;
        if (replaying || !TimeDilation.active(player)) return false;
        // Record identity when the first input arrives, before the next tick's ownership reset.
        if (owner != player) { PENDING.clear(); owner = player; }
        int keys = (Screen.hasShiftDown() ? 1 : 0) | (Screen.hasControlDown() ? 2 : 0) | (Screen.hasAltDown() ? 4 : 0);
        PENDING.addLast(new Action(screen, action, keys, starts));
        return true;
    }

    public static boolean consumeClick(KeyMapping mapping) {
        if (TimeDilation.active(Minecraft.getInstance().player) && !CONSUMED.add(mapping)) return false;
        return mapping.consumeClick();
    }

    public static Boolean modifier(int mask) { return replaying ? (modifiers & mask) != 0 : null; }
    private record Action(Screen screen, Runnable run, int modifiers, boolean starts) {}
}
