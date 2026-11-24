package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Edits a single ITE value and applies it only when the player saves. @author NingMeng123 */
public final class IteConfigOptionScreen extends Screen {
    private final Screen parent;
    private final IteConfig.Option option;
    private Object draft;
    private String inputText;
    private Component failure;
    private Button save;

    IteConfigOptionScreen(Screen parent, IteConfig.Option option) {
        super(Component.translatable("modern_ite.config." + option.key()));
        this.parent = parent;
        this.option = option;
        draft = IteConfig.value(option.key());
        inputText = draft instanceof Number number ? number.toString() : "";
    }

    @Override
    protected void init() {
        boolean editable = IteConfigClient.editable() && IteConfig.SPEC.isLoaded();
        int inputWidth = Math.min(220, width - 24);
        int inputX = (width - inputWidth) / 2;
        var description = Component.translatable("modern_ite.config." + option.key() + ".tooltip");
        int actionWidth = Math.min(100, (width - 32) / 3);
        int left = (width - actionWidth * 3 - 8) / 2;
        int footerY = height - 28;

        Runnable reset;
        if (option.defaultValue() instanceof Boolean factory) {
            var toggle = addRenderableWidget(CycleButton.onOffBuilder((Boolean) draft).displayOnlyValue()
                    .withTooltip(value -> Tooltip.create(description))
                    .create(inputX, 76, inputWidth, 20, title, (button, value) -> updateDraft(value)));
            toggle.active = editable;
            reset = () -> {
                toggle.setValue(factory);
                updateDraft(factory);
            };
        } else {
            var field = addRenderableWidget(new EditBox(font, inputX, 76, inputWidth, 20, title));
            field.setMaxLength(64);
            field.setValue(inputText);
            field.setEditable(editable);
            field.setTooltip(Tooltip.create(description));
            field.setTextColor(validDraft() ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF5555);
            field.setResponder(text -> {
                inputText = text;
                updateDraft(parseNumber(text));
                field.setTextColor(validDraft() ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF5555);
            });
            if (editable) setInitialFocus(field);
            reset = () -> field.setValue(option.defaultValue().toString());
        }

        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), ignored -> save())
                .bounds(left, footerY, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), ignored -> reset.run())
                .bounds(left + actionWidth + 4, footerY, actionWidth, 20).build()).active = editable;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> onClose())
                .bounds(left + 2 * (actionWidth + 4), footerY, actionWidth, 20).build());
        updateSaveState();
    }

    private Object parseNumber(String text) {
        try {
            String value = text.trim();
            if (option.defaultValue() instanceof Integer) return Integer.valueOf(value);
            return Double.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean validDraft() {
        return draft != null && Objects.equals(draft, option.validated(draft))
                && IteConfig.setting(option.key()).getSpec().test(draft);
    }

    private void updateDraft(Object value) {
        draft = value;
        failure = null;
        updateSaveState();
    }

    private void updateSaveState() {
        if (save != null) save.active = IteConfigClient.editable() && IteConfig.SPEC.isLoaded() && validDraft();
    }

    private void save() {
        if (!IteConfigClient.editable() || !validDraft()) return;
        if (IteConfig.saveOption(option, draft)) onClose();
        else failure = Component.translatable("modern_ite.config.settings.save_failed");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(title.getString(), width - 24), width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("modern_ite.config.default", IteConfigValuesScreen.format(option.defaultValue())),
                width / 2, 39, 0xCCCCCC);
        if (option.defaultValue() instanceof Number) {
            graphics.drawCenteredString(font, Component.translatable("modern_ite.config.range",
                            IteConfigValuesScreen.format(option.minimum()), IteConfigValuesScreen.format(option.maximum())),
                    width / 2, 54, 0xCCCCCC);
        }

        int textWidth = Math.min(400, width - 24);
        int y = 108;
        var description = Component.translatable("modern_ite.config." + option.key() + ".tooltip");
        for (var line : font.split(description, textWidth)) {
            if (y + font.lineHeight > height - 50) break;
            graphics.drawString(font, line, (width - textWidth) / 2, y, 0xBBBBBB);
            y += font.lineHeight;
        }
        Component status = failure;
        if (status == null && !validDraft()) status = Component.translatable("modern_ite.config.settings.invalid_value");
        if (status != null) graphics.drawCenteredString(font,
                font.plainSubstrByWidth(status.getString(), width - 24), width / 2, height - 44, 0xFF7777);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
