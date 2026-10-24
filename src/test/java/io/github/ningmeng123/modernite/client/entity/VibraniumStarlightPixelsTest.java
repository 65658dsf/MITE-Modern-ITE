package io.github.ningmeng123.modernite.client.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Pixel contracts for the optional shader-pack path. @author NingMeng123 */
class VibraniumStarlightPixelsTest {
    @Test void armorOpeningsNeverAcquireStars() {
        for (int rgb : new int[] {0, 0xFFFFFF, 0xA4C8EF})
            assertEquals(0, VibraniumStarlightPixels.star(rgb, .8D));
    }

    @Test void emissionRemainsTranslucentAndSurvivesTheIrisOneTenthAlphaTest() {
        int star = VibraniumStarlightPixels.star(0xFF806040, .5D);
        assertEquals(128, star >>> 24);
        assertTrue((star & 255) > 64 && (star >>> 8 & 255) > 96 && (star >>> 16 & 255) > 128,
                "Stars brighten the native material without replacing opaque armor");
        assertEquals(0, VibraniumStarlightPixels.star(0xFF806040, .05D), "Invisible sub-threshold texels stay transparent");
        assertEquals(26, VibraniumStarlightPixels.star(0xFF806040, .1D) >>> 24);
    }

    @Test void partialArmorAlphaAndStrengthLimitRemainMultiplicative() {
        assertEquals(64, VibraniumStarlightPixels.star(0x80806040, .5D) >>> 24);
        assertEquals(204, VibraniumStarlightPixels.star(0xFF806040, 10D) >>> 24);
        assertEquals(0, VibraniumStarlightPixels.star(0xFF806040, -1D));
    }
}
