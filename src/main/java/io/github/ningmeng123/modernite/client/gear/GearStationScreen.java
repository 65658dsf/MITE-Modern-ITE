package io.github.ningmeng123.modernite.client.gear;

import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.ForgeRecipes;
import io.github.ningmeng123.modernite.gear.GearStationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Crisp native slots and live server progress, with explicit recipe costs. @author NingMeng123 */
public final class GearStationScreen extends AbstractContainerScreen<GearStationMenu> {
    private Button start;
    public GearStationScreen(GearStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 176; imageHeight = menu.gemMode ? 173 : 208;
        inventoryLabelY = menu.gemMode ? 80 : 115;
    }
    @Override protected void init() {
        super.init();
        if (!menu.gemMode) start = addRenderableWidget(Button.builder(Component.translatable("button.modern_ite.forge"), button -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 117, topPos + 78, 50, 20).build());
    }
    @Override protected void containerTick() { super.containerTick(); if (start != null) start.active = !menu.locked(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF17252B);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFD2D5CC);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF596861);
            graphics.fill(x, y, x + 16, y + 16, 0xFF97A69D);
        }
        if (!menu.gemMode) {
            int total = menu.data.get(2), progress = menu.data.get(1);
            graphics.fill(leftPos + 111, topPos + 57, leftPos + 134, topPos + 63, 0xFF66736A);
            if (total > 0) graphics.fill(leftPos + 111, topPos + 57, leftPos + 111 + Math.min(23, 23 * progress / total), topPos + 63, 0xFFBB8B38);
            if (menu.locked() && total > 0) ForgingScreenAnimation.render(graphics, leftPos + 111, topPos + 16,
                    menu.getSlot(5).getItem(), menu.getSlot(7).getItem(), progress + partialTick);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x263A32, false);
        if (menu.gemMode) {
            graphics.drawString(font, Component.translatable("container.modern_ite.equipment"), 14, 22, 0x45544B, false);
            graphics.drawString(font, Component.translatable("container.modern_ite.sockets"), 65, 69, 0x45544B, false);
        } else {
            graphics.drawString(font, Component.translatable("container.modern_ite.equipment"), 48, 40, 0x45544B, false);
            graphics.drawString(font, Component.translatable("container.modern_ite.axe"), 15, 97, 0x45544B, false);
            graphics.drawString(font, Component.translatable("container.modern_ite.hammer"), 83, 97, 0x45544B, false);
            int status = menu.data.get(3);
            graphics.drawString(font, Component.translatable("forge.modern_ite.status_" + status), 10, 106, status == 3 || status == 4 ? 0xA92E26 : 0x45544B, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x45544B, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
        if (!menu.gemMode && start != null && start.isHovered()) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.translatable("tooltip.modern_ite.forge_failure", menu.data.get(4)));
            for (var cost : ForgeRecipes.costs(ExtremeGear.grade(menu.getSlot(5).getItem()))) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(cost.id()));
                lines.add(Component.literal(cost.count() + " × ").append(item.getDescription()));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }
}
