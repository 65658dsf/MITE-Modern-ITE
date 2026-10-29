package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.gear.GemBoxItem;
import net.minecraft.util.Mth;

/** Shared phase weights for reaching inside, searching, withdrawing and showing the reward. @author NingMeng123 */
public final class GemBoxOpeningMotion {
    private GemBoxOpeningMotion() {}

    public static float elapsed(float progress) {
        return Mth.clamp(progress, 0F, 1F) * GemBoxItem.OPEN_TICKS;
    }

    public static float reach(float tick) {
        return smooth((tick - 16F) / 5F);
    }

    public static float rummage(float tick) {
        return reach(tick) * (1F - smooth((tick - (GemBoxItem.REVEAL_TICK - 3F)) / 3F));
    }

    public static float extraction(float tick) {
        return smooth((tick - GemBoxItem.REVEAL_TICK) / 12F);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
