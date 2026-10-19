package io.github.ningmeng123.modernite.client;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingContent;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingMenu;
import java.math.BigDecimal;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Explicit metal-upgrade slots, live batch cost, heat and duration. @author NingMeng123 */
public final class MetalUpgradingScreen extends AbstractContainerScreen<MetalUpgradingMenu> {
    public MetalUpgradingScreen(MetalUpgradingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 176; imageHeight = 195; inventoryLabelY = 100;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF19232B);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFCFD4D8);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF4B5967);
            graphics.fill(x, y, x + 16, y + 16, 0xFF93A1AC);
        }
        graphics.fill(leftPos + 80, topPos + 40, leftPos + 106, topPos + 48, 0xFF64717D);
        if (menu.total() > 0) graphics.fill(leftPos + 80, topPos + 40,
                leftPos + 80 + (int)Math.min(26L, 26L * menu.progress() / menu.total()), topPos + 48, 0xFFCF9E4A);
        if (menu.burnDuration() > 0) graphics.fill(leftPos + 77, topPos + 62, leftPos + 81,
                topPos + 62 + (int)Math.min(12L, 12L * menu.burnRemaining() / menu.burnDuration()), 0xFFDA6D28);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 7, 0x263A48, false);
        graphics.drawString(font, Component.translatable("container.modern_ite.metal_upgrade.input"), 8, 31, 0x3C4F5C, false);
        graphics.drawString(font, Component.translatable("container.modern_ite.metal_upgrade.fuel"), 8, 63, 0x3C4F5C, false);
        graphics.drawString(font, Component.translatable("container.modern_ite.metal_upgrade.output"), 111, 23, 0x3C4F5C, false);
        graphics.drawString(font, Component.translatable("container.modern_ite.metal_upgrade.heat", menu.data.get(8), menu.data.get(9)),
                88, 63, menu.data.get(8) < menu.data.get(9) ? 0x9B3628 : 0x3C4F5C, false);
        if (menu.total() > 0) graphics.drawString(font, Component.translatable("container.modern_ite.metal_upgrade.batch",
                menu.data.get(10), seconds(menu.total())), 8, 82, 0x3C4F5C, false);
        else graphics.drawString(font, Component.translatable("container.modern_ite.metal_upgrade.hint"), 8, 82, 0x3C4F5C, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x3C4F5C, false);
    }
    private static String seconds(int ticks) { return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
    }
    @EventBusSubscriber(modid = ModernIte.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(MetalUpgradingContent.MENU.get(), MetalUpgradingScreen::new); }
    }
}
