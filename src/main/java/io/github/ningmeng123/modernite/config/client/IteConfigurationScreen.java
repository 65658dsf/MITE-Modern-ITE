package io.github.ningmeng123.modernite.config.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/** Native config behavior with a footer that fits the minimum GUI width. @author NingMeng123 */
public final class IteConfigurationScreen {
    private IteConfigurationScreen() {}
    public static ConfigurationScreen create(ModContainer container, Screen parent) {
        return new ConfigurationScreen(container, parent, Section::new);
    }

    private static final class Section extends ConfigurationScreen.ConfigurationSectionScreen {
        private Section(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
            super(parent, type, config, title);
        }

        private Section(Context parentContext, Screen parent, UnmodifiableConfig definitions,
                        String key, UnmodifiableConfig values, Component title) {
            super(parentContext, parent, definitions.valueMap(), key, values.entrySet(), title);
        }

        @Override
        protected Element createSection(String key, UnmodifiableConfig definitions, UnmodifiableConfig values) {
            if (definitions.isEmpty()) return null;
            Component tooltip = getTooltipComponent(key, null);
            String section = "neoforge.configuration.uitext.section";
            return new Element(Component.translatable(section, getTranslationComponent(key)), tooltip,
                    Button.builder(Component.translatable(section,
                                    Component.translatable("neoforge.configuration.uitext.sectiontext")),
                            ignored -> minecraft.setScreen(sectionCache.computeIfAbsent(key,
                                    unused -> new Section(context, this, definitions, key, values,
                                            getTranslationComponent(key)).rebuild())))
                            .tooltip(Tooltip.create(tooltip)).width(Button.DEFAULT_WIDTH).build(), false);
        }

        @Override
        protected void repositionElements() {
            if (!ModernIte.MOD_ID.equals(context.modId()) || undoButton == null || resetButton == null) {
                super.repositionElements();
                return;
            }
            List<Button> footer = List.of(undoButton, resetButton, doneButton);
            footer.forEach(button -> button.setWidth(Button.SMALL_WIDTH));
            super.repositionElements();
            int gap = 8;
            int available = width - 24;
            int naturalWidth = footer.size() * Button.SMALL_WIDTH + gap * (footer.size() - 1);
            if (naturalWidth <= available) return;
            int buttonWidth = Math.max(1, (available - gap * (footer.size() - 1)) / footer.size());
            int x = (width - buttonWidth * footer.size() - gap * (footer.size() - 1)) / 2;
            for (Button button : footer) {
                button.setWidth(buttonWidth);
                button.setX(x);
                x += buttonWidth + gap;
            }
        }
    }
}
