package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.config.IteConfigSync;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Focused live settings editor; paging and resizing preserve every draft. @author NingMeng123 */
public final class IteSpawningSettingsScreen extends Screen {
    private static final List<String> PRIORITY = List.of("reference_spawning", "local_hostile_limit", "blood_moon_local_hostile_limit",
            "mob_max_spawn_count_chunk_base", "mob_max_spawn_count_day_minimum",
            "mob_max_spawn_count_day_maximum", "mob_max_spawn_count_increase_per_day", "blood_moon_max_hostile_fraction",
            "hostile_spawn_groups", "blood_moon_hostile_spawn_groups", "hostile_spawn_attempts");
    private static final Set<String> CUSTOM_ONLY = Set.of("mob_max_spawn_count_chunk_base", "mob_max_spawn_count_day_minimum",
            "mob_max_spawn_count_day_maximum", "mob_max_spawn_count_increase_per_day", "blood_moon_max_hostile_fraction",
            "hostile_spawn_groups", "blood_moon_hostile_spawn_groups", "hostile_spawn_attempts", "ghast_spawn_limit_day",
            "annihilation_skeleton_spawn_in_light", "wandering_witch_spawn_chance_overworld",
            "wandering_witch_spawn_limit_day_overworld", "wandering_witch_spawn_limit_day_other",
            "wandering_witch_spawn_chance_underworld", "ancient_bone_lord_spawn_limit_day");
    private record Row(IteConfig.Option option, int y) {}
    private final Screen parent;
    private final boolean editable = IteConfigClient.editable();
    private final List<IteConfig.Option> options = IteConfig.options().stream().filter(option ->
            option.path().contains("MobSpawning") || Set.of("mob_spawner_speed_up_with_players",
                    "mob_spawner_extra_delay_reduction", "giant_zombie_spawn_zombie_cooldown").contains(option.key()))
            .sorted(Comparator.comparingInt(option -> PRIORITY.contains(option.key()) ? PRIORITY.indexOf(option.key()) : PRIORITY.size())).toList();
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, String> drafts = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
    private final List<Row> visible = new ArrayList<>();
    private Button save;
    private int page;
    private int pages;
    private Component failure;

    public IteSpawningSettingsScreen(Screen parent) {
        super(Component.translatable("modern_ite.config.spawning_rules"));
        this.parent = parent;
        options.forEach(option -> values.put(option.key(), IteConfig.effectiveSnapshot().get(option.key())));
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        visible.clear();
        int rows = Math.max(1, (height - 126) / 34);
        pages = Math.max(1, (options.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int index = page * rows; index < Math.min(options.size(), (page + 1) * rows); index++) {
            var option = options.get(index);
            int y = 54 + (index - page * rows) * 34;
            visible.add(new Row(option, y));
            String key = option.key();
            boolean canEdit = editable && !referenceOnlyDisabled(key);
            Tooltip tooltip = Tooltip.create(optionTooltip(key));
            if (option.defaultValue() instanceof Boolean) {
                var toggle = addRenderableWidget(Button.builder(booleanLabel(key, (Boolean) values.get(key)), button -> {
                    boolean value = !(Boolean) values.get(key);
                    values.put(key, value);
                    button.setMessage(booleanLabel(key, value));
                    failure = null;
                    if (key.equals("reference_spawning")) rebuild(); else updateSave();
                }).bounds(width - 100, y, 84, 20).build());
                toggle.active = canEdit;
                toggle.setTooltip(tooltip);
            } else {
                var field = new EditBox(font, width - 100, y, 84, 20, Component.translatable("modern_ite.config." + key));
                field.setMaxLength(32);
                field.setValue(drafts.getOrDefault(key, number((Number) values.get(key))));
                field.setEditable(canEdit);
                field.active = canEdit;
                field.setTooltip(tooltip);
                field.setTextColor(invalid.contains(key) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
                field.setResponder(text -> {
                    drafts.put(key, text);
                    failure = null;
                    try {
                        double value = Double.parseDouble(text);
                        if (!Double.isFinite(value) || value < option.minimum() || value > option.maximum()
                                || option.defaultValue() instanceof Integer && value != Math.rint(value)) throw new NumberFormatException();
                        values.put(key, option.validated(value));
                        invalid.remove(key);
                        field.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                    } catch (NumberFormatException exception) {
                        invalid.add(key);
                        field.setTextColor(0xFFFF5555);
                    }
                    updateSave();
                });
                addRenderableWidget(field);
            }
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuild(); })
                .bounds(16, height - 54, 52, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuild(); })
                .bounds(width - 68, height - 54, 52, 20).build());
        next.active = page + 1 < pages;
        int actionWidth = Math.min(100, (width - 40) / 3);
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> save())
                .bounds(width / 2 - actionWidth * 3 / 2 - 4, height - 28, actionWidth, 20).build());
        var reset = addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
            options.forEach(option -> values.put(option.key(), option.defaultValue()));
            drafts.clear(); invalid.clear(); failure = null;
            rebuild();
        }).bounds(width / 2 - actionWidth / 2, height - 28, actionWidth, 20).build());
        reset.active = editable;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + actionWidth / 2 + 4, height - 28, actionWidth, 20).build());
        updateSave();
    }

    private void updateSave() {
        if (save == null) return;
        save.active = editable && !hasInvalidValues() && failure == null;
        save.setTooltip(Tooltip.create(failure != null ? failure : Component.translatable(!editable
                ? "modern_ite.config.server_owned" : !hasInvalidValues() ? "modern_ite.config.spawning_rules.tooltip" : "modern_ite.config.spawning_invalid")));
    }

    private void save() {
        if (!IteConfigClient.editable() || hasInvalidValues()) return;
        var before = IteConfig.localSnapshot();
        try {
            values.forEach((key, value) -> IteConfig.setting(key).set(value));
            IteConfig.SPEC.save();
            IteConfig.refresh();
            IteConfigSync.broadcast();
            onClose();
        } catch (RuntimeException exception) {
            values.keySet().forEach(key -> IteConfig.setting(key).set(before.get(key)));
            IteConfig.refresh();
            failure = Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            updateSave();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(editable ? "modern_ite.config.spawning_live" : "modern_ite.config.server_owned"),
                width / 2, 33, 0xAAAAAA);
        for (var row : visible) {
            Component label = Component.translatable("modern_ite.config." + row.option().key());
            graphics.drawString(font, font.plainSubstrByWidth(label.getString(), width - 128), 16, row.y() + 6,
                    referenceOnlyDisabled(row.option().key()) ? 0x888888 : 0xFFFFFF);
        }
        graphics.drawCenteredString(font, Component.translatable("modern_ite.config.spawning_page", page + 1, pages), width / 2, height - 48, 0xCCCCCC);
        if (hasInvalidValues()) graphics.drawCenteredString(font, Component.translatable("modern_ite.config.spawning_invalid"), width / 2, height - 70, 0xFFFF5555);
        for (var row : visible) if (mouseX >= 16 && mouseX < width - 112 && mouseY >= row.y() && mouseY < row.y() + 24) {
            var option = row.option();
            Component tooltip = Component.translatable("modern_ite.config." + option.key()).append("\n")
                    .append(optionTooltip(option.key()));
            graphics.renderTooltip(font, font.split(tooltip, Math.max(100, width - 32)), mouseX, mouseY);
        }
    }

    private boolean referenceOnlyDisabled(String key) { return Boolean.TRUE.equals(values.get("reference_spawning")) && CUSTOM_ONLY.contains(key); }
    private boolean hasInvalidValues() { return invalid.stream().anyMatch(key -> !referenceOnlyDisabled(key)); }
    private Component optionTooltip(String key) {
        var tooltip = Component.translatable("modern_ite.config." + key + ".tooltip");
        if (referenceOnlyDisabled(key)) tooltip.append("\n").append(Component.translatable("modern_ite.config.spawning_custom_only"));
        return tooltip;
    }
    private static Component booleanLabel(String key, boolean value) {
        return Component.translatable(key.equals("reference_spawning")
                ? value ? "modern_ite.config.spawning_reference" : "modern_ite.config.spawning_custom"
                : value ? "options.on" : "options.off");
    }
    private static String number(Number value) { return BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
