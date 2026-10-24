package io.github.ningmeng123.modernite.client.entity;

/** Pure RGBA-mask arithmetic shared by the shader-pack starlight texture and its tests. @author NingMeng123 */
final class VibraniumStarlightPixels {
    private VibraniumStarlightPixels() {}

    static int seed(int x, int y, int salt) {
        int value = x * 0x1f123bb5 ^ y * 0x5f356495 ^ salt;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        return value;
    }

    /** NativeImage uses ABGR. Iris's emissive entity program discards alpha below one tenth. */
    static int star(int armor, double strength) {
        int alpha = (int)Math.round((armor >>> 24) * Math.max(0D, Math.min(.8D, strength)));
        if (alpha < 26) return 0;
        int red = (int)Math.round((armor & 255) * .15D + 190D * .85D);
        int green = (int)Math.round((armor >>> 8 & 255) * .15D + 226D * .85D);
        int blue = (int)Math.round((armor >>> 16 & 255) * .15D + 255D * .85D);
        return alpha << 24 | blue << 16 | green << 8 | red;
    }
}
