package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.modernite.config.IteConfig;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Searchable, grouped view of effective local or server settings. @author NingMeng123 */
public final class IteConfigValuesScreen extends Screen {
    private final Screen parent;
    private String query = "";
    private ValuesList values;
    private IteConfig.Option hovered;
    private int matchCount;

    public IteConfigValuesScreen(Screen parent) {
        super(Component.translatable("modern_ite.config.values"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        double scroll = values == null ? 0D : values.getScrollAmount();
        var search = addRenderableWidget(new EditBox(font, 16, 36, width - 32, 20,
                Component.translatable("modern_ite.config.search")));
        search.setMaxLength(200);
        search.setHint(Component.translatable("modern_ite.config.search"));
        search.setValue(query);
        values = addRenderableWidget(new ValuesList());
        rebuildRows();
        values.setScrollAmount(scroll);
        search.setResponder(text -> {
            query = text;
            rebuildRows();
        });
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private void rebuildRows() {
        values.clearRows();
        String filter = query.trim().toLowerCase(Locale.ROOT);
        var categories = new LinkedHashMap<String, java.util.ArrayList<IteConfig.Option>>();
        matchCount = 0;
        for (var option : IteConfig.options()) {
            if (option.key().equals("stepped_mob_damage_progress_increase_day")) continue;
            String category = option.path().size() > 1 ? option.path().getFirst() : "general";
            if (!filter.isEmpty() && !option.key().toLowerCase(Locale.ROOT).contains(filter)
                    && !String.join(".", option.path()).toLowerCase(Locale.ROOT).contains(filter)
                    && !label(option).getString().toLowerCase(Locale.ROOT).contains(filter)
                    && !categoryLabel(category).getString().toLowerCase(Locale.ROOT).contains(filter)) continue;
            categories.computeIfAbsent(category, ignored -> new java.util.ArrayList<>()).add(option);
            matchCount++;
        }
        categories.forEach((category, options) -> {
            values.addRow(new ValueRow(categoryLabel(category), null));
            options.forEach(option -> values.addRow(new ValueRow(label(option), option)));
        });
        values.setScrollAmount(0D);
    }

    private static Component label(IteConfig.Option option) {
        return Component.translatable("modern_ite.config." + option.key());
    }

    private static Component categoryLabel(String category) {
        return Component.translatable("modern_ite.config.category." + category);
    }

    private static String format(Object value) {
        if (value instanceof Boolean enabled)
            return Component.translatable(enabled ? "modern_ite.config.enabled" : "modern_ite.config.disabled").getString();
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            return Double.isFinite(decimal) ? BigDecimal.valueOf(decimal).stripTrailingZeros().toPlainString()
                    : number.toString();
        }
        return String.valueOf(value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hovered = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(
                        Component.translatable("modern_ite.config.values.count", matchCount).getString(), width - 24),
                width / 2, height - 44, 0xBBBBBB);
        if (matchCount == 0) graphics.drawCenteredString(font,
                Component.translatable("modern_ite.config.no_results"), width / 2, 88, 0xBBBBBB);
        if (hovered != null) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(label(hovered));
            lines.add(Component.literal(String.join(".", hovered.path())));
            lines.add(Component.translatable("modern_ite.config.current", format(IteConfig.value(hovered.key()))));
            lines.add(Component.translatable("modern_ite.config.default", format(hovered.defaultValue())));
            if (hovered.defaultValue() instanceof Number)
                lines.add(Component.translatable("modern_ite.config.range", format(hovered.minimum()), format(hovered.maximum())));
            lines.add(Component.translatable("modern_ite.config." + hovered.key() + ".tooltip"));
            graphics.renderTooltip(font, lines.stream().flatMap(line -> font.split(line,
                    Math.min(400, width - 32)).stream()).toList(), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private final class ValuesList extends ObjectSelectionList<ValueRow> {
        private ValuesList() {
            super(IteConfigValuesScreen.this.minecraft, IteConfigValuesScreen.this.width,
                    Math.max(32, IteConfigValuesScreen.this.height - 116), 64, 36);
        }

        private void clearRows() { clearEntries(); }
        private void addRow(ValueRow row) { addEntry(row); }
        @Override public int getRowWidth() { return Math.min(520, getWidth() - 44); }
        @Override protected int getScrollbarPosition() { return getRowRight() + 6; }
    }

    private final class ValueRow extends ObjectSelectionList.Entry<ValueRow> {
        private final Component name;
        private final IteConfig.Option option;

        private ValueRow(Component name, IteConfig.Option option) {
            this.name = name;
            this.option = option;
        }

        @Override
        public Component getNarration() {
            return option == null ? name : Component.translatable("modern_ite.config.value_narration", name,
                    format(IteConfig.value(option.key())));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (option == null) {
                graphics.drawString(font, font.plainSubstrByWidth(name.getString(), rowWidth - 8), x + 4, y + 12, 0xFFDD77);
                return;
            }
            String current = format(IteConfig.value(option.key()));
            int valueWidth = Math.min(Math.max(54, font.width(current)), rowWidth / 3);
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), rowWidth - valueWidth - 16), x + 4, y + 3, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(option.key(), rowWidth - valueWidth - 16),
                    x + 4, y + 16, 0x999999);
            String shortValue = font.plainSubstrByWidth(current, valueWidth);
            graphics.drawString(font, shortValue, x + rowWidth - font.width(shortValue) - 4, y + 9, 0xAADDFF);
            if (values.isMouseOver(mouseX, mouseY) && mouseX >= x && mouseX < x + rowWidth
                    && mouseY >= y && mouseY < y + rowHeight)
                hovered = option;
        }
    }
}
