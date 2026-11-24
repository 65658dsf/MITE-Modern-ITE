package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.modernite.config.IteConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Browse effective gameplay settings and edit local values. @author NingMeng123 */
public final class IteConfigValuesScreen extends Screen {
    enum Group {
        NETHER_ORES("nether_ores", "world"), CREATURE_BEHAVIOR("creature_behavior", "creatures"),
        COMBAT("combat", "combat"), ENCHANTMENT_EFFECTS("enchantment_effects", "combat"),
        SURVIVAL("survival", "survival"), CRAFTING("crafting", "survival"), SYSTEM("system", "system");

        private final String translationKey;
        private final String category;

        Group(String key, String category) {
            translationKey = "modern_ite.config.settings." + key;
            this.category = category;
        }

        String translationKey() { return translationKey; }
        String category() { return category; }
        Component title() { return Component.translatable(translationKey); }

        static Group forOption(IteConfig.Option option) {
            String key = option.key();
            var path = option.path();
            return switch (path.getFirst()) {
                case "WorldGen" -> NETHER_ORES;
                case "Block" -> CREATURE_BEHAVIOR;
                case "Entities" -> path.contains("Animals") || key.startsWith("villager_") ? SURVIVAL : CREATURE_BEHAVIOR;
                case "Item" -> path.contains("Enchantment") ? ENCHANTMENT_EFFECTS
                        : path.contains("Recipes") ? CRAFTING : SURVIVAL;
                case "GameMechanics" -> path.contains("MobSpawning") ? CREATURE_BEHAVIOR
                        : key.startsWith("player_") || key.startsWith("stepped_") ? COMBAT : SURVIVAL;
                case "Misc" -> SYSTEM;
                default -> null;
            };
        }
    }

    private final Screen parent;
    private final Group group;
    private String query = "";
    private ValuesList values;
    private boolean editable;
    private IteConfig.Option hovered;
    private int matchCount;

    public IteConfigValuesScreen(Screen parent) {
        this(parent, "");
    }

    /** Start the authoritative values browser at the setting selected by the global search. */
    public IteConfigValuesScreen(Screen parent, String query) {
        this(parent, null, query);
    }

    IteConfigValuesScreen(Screen parent, Group group) {
        this(parent, group, "");
    }

    private IteConfigValuesScreen(Screen parent, Group group, String query) {
        super(group == null ? Component.translatable("modern_ite.config.values") : group.title());
        this.parent = parent;
        this.group = group;
        this.query = query == null ? "" : query;
    }

    @Override
    protected void init() {
        double scroll = values == null ? 0D : values.getScrollAmount();
        editable = IteConfigClient.editable() && IteConfig.SPEC.isLoaded();
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
        var categories = new LinkedHashMap<String, List<IteConfig.Option>>();
        Set<String> sharedSpawning = group == Group.CREATURE_BEHAVIOR ? IteConfigClient.sharedSpawningKeys() : Set.of();
        matchCount = 0;
        for (var option : IteConfig.options()) {
            if (option.key().equals("stepped_mob_damage_progress_increase_day")) continue;
            if (group != null && (Group.forOption(option) != group || sharedSpawning.contains(option.key()))) continue;
            String category = option.path().getFirst();
            if (group == Group.SURVIVAL && option.path().size() > 2 && category.equals("GameMechanics")) {
                category = option.path().get(1);
            }
            if (!filter.isEmpty() && !option.key().toLowerCase(Locale.ROOT).contains(filter)
                    && !String.join(".", option.path()).toLowerCase(Locale.ROOT).contains(filter)
                    && !label(option).getString().toLowerCase(Locale.ROOT).contains(filter)
                    && !categoryLabel(category).getString().toLowerCase(Locale.ROOT).contains(filter)) continue;
            categories.computeIfAbsent(category, ignored -> new ArrayList<>()).add(option);
            matchCount++;
        }
        categories.forEach((category, options) -> {
            if (group == null || categories.size() > 1) values.addRow(new ValueRow(categoryLabel(category), null));
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

    static String format(Object value) {
        if (value instanceof Boolean enabled)
            return Component.translatable(enabled ? "modern_ite.config.enabled" : "modern_ite.config.disabled").getString();
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            if (!Double.isFinite(decimal)) return number.toString();
            BigDecimal normalized = BigDecimal.valueOf(decimal).stripTrailingZeros();
            String plain = normalized.toPlainString();
            return plain.length() <= 24 ? plain : normalized.toString();
        }
        return String.valueOf(value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hovered = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        String hint = editable ? "modern_ite.config.settings.edit_hint" : "modern_ite.config.settings.view_hint";
        graphics.drawCenteredString(font, Component.translatable(hint), width / 2, 61, 0xBBBBBB);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(
                        Component.translatable("modern_ite.config.values.count", matchCount).getString(), width - 24),
                width / 2, height - 44, 0xBBBBBB);
        if (matchCount == 0) graphics.drawCenteredString(font,
                Component.translatable("modern_ite.config.no_results"), width / 2, 88, 0xBBBBBB);
        if (hovered != null) {
            var lines = new ArrayList<Component>();
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
                    Math.max(32, IteConfigValuesScreen.this.height - 127), 75, 36);
        }

        private void clearRows() { clearEntries(); }
        private void addRow(ValueRow row) { addEntry(row); }
        @Override public int getRowWidth() { return Math.min(520, getWidth() - 44); }
        @Override protected int getScrollbarPosition() { return getRowRight() + 6; }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            var selected = getSelected();
            if (CommonInputs.selected(keyCode) && selected != null && selected.openEditor()) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    private final class ValueRow extends ObjectSelectionList.Entry<ValueRow> {
        private final Component name;
        private final IteConfig.Option option;
        private final Button editButton;

        private ValueRow(Component name, IteConfig.Option option) {
            this.name = name;
            this.option = option;
            editButton = option != null && editable
                    ? Button.builder(Component.empty(), ignored -> openEditor()).size(60, 20).build()
                    : null;
        }

        @Override
        public Component getNarration() {
            return option == null ? name : Component.translatable("modern_ite.config.value_narration", name,
                    format(IteConfig.value(option.key())));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && editButton != null) {
                return editButton.mouseClicked(mouseX, mouseY, button) || openEditor();
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private boolean openEditor() {
            if (option == null || !editable || !IteConfigClient.editable() || !IteConfig.SPEC.isLoaded()) return false;
            minecraft.setScreen(new IteConfigOptionScreen(IteConfigValuesScreen.this, option));
            return true;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (option == null) {
                graphics.drawString(font, font.plainSubstrByWidth(name.getString(), rowWidth - 8), x + 4, y + 12, 0xFFDD77);
                return;
            }
            String current = format(IteConfig.value(option.key()));
            var action = Component.translatable("modern_ite.config.settings.edit_value", current);
            int valueWidth = editButton == null
                    ? Math.min(Math.max(54, font.width(current)), rowWidth / 3)
                    : Math.min(Math.max(62, font.width(action) + 12), rowWidth / 2);
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), rowWidth - valueWidth - 16), x + 4, y + 3, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(option.key(), rowWidth - valueWidth - 16),
                    x + 4, y + 16, 0x999999);
            if (editButton != null) {
                editButton.setMessage(action);
                editButton.setWidth(valueWidth);
                editButton.setPosition(x + rowWidth - valueWidth - 4, y + 5);
                editButton.render(graphics, mouseX, mouseY, partialTick);
            } else {
                String shortValue = font.plainSubstrByWidth(current, valueWidth);
                graphics.drawString(font, shortValue, x + rowWidth - font.width(shortValue) - 4, y + 9, 0xAADDFF);
            }
            if (values.isMouseOver(mouseX, mouseY) && mouseX >= x && mouseX < x + rowWidth
                    && mouseY >= y && mouseY < y + rowHeight)
                hovered = option;
        }
    }
}
