package io.github.ningmeng123.modernite.client.gear;

import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.gear.ForgingAnimation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A compact native-item forging vignette, driven by the server's recipe clock. @author NingMeng123 */
final class ForgingScreenAnimation {
    private static final ItemStack ANVIL = new ItemStack(Items.ANVIL);

    private ForgingScreenAnimation() {}

    static void render(GuiGraphics graphics, int x, int y, ItemStack equipment, ItemStack hammer, float elapsed) {
        graphics.fill(x, y, x + 58, y + 33, 0xFF7A8980);
        graphics.fill(x + 1, y + 1, x + 57, y + 32, 0xFF263A32);
        graphics.enableScissor(x + 1, y + 1, x + 57, y + 32);
        graphics.fill(x + 13, y + 29, x + 43, y + 31, 0xFF172820);
        graphics.renderItem(ANVIL, x + 18, y + 15);

        // The input rests on the anvil while the actual supplied hammer swings into it.
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 26, y + 17, 20);
        pose.mulPose(Axis.ZP.rotationDegrees(45));
        pose.scale(.5F, .5F, 1);
        graphics.renderItem(equipment, -8, -8);
        pose.popPose();

        float lift = ForgingAnimation.hammerLift(elapsed);
        pose.pushPose();
        pose.translate(x + 32, y + 11 - lift * 2, 40);
        pose.mulPose(Axis.ZP.rotationDegrees(180 + lift * 90));
        pose.scale(1.1F, 1.1F, 1.1F);
        graphics.renderItem(hammer, -8, -8);
        pose.popPose();

        renderImpact(graphics, x + 27, y + 17, elapsed);
        graphics.disableScissor();
    }

    private static void renderImpact(GuiGraphics graphics, int x, int y, float elapsed) {
        float strength = ForgingAnimation.impactStrength(elapsed);
        if (strength <= 0) return;
        float age = ForgingAnimation.cycleTicks(elapsed) - ForgingAnimation.IMPACT_TICK;
        int spread = Math.round(age * 2);
        int color = ((int)(strength * 255) << 24) | 0xFFE49A;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 250);
        graphics.fill(x - 3, y, x + 4, y + 1, ((int)(strength * 220) << 24) | 0xFFF4CD);
        spark(graphics, x - 4 - spread, y - 2 - spread / 2, color);
        spark(graphics, x + 4 + spread, y - 3 - spread / 2, color);
        spark(graphics, x - 1 + spread / 2, y - 5 - spread, color);
        pose.popPose();
    }

    private static void spark(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 2, y + 1, color);
        graphics.fill(x + 1, y - 1, x + 2, y + 2, color);
    }
}
