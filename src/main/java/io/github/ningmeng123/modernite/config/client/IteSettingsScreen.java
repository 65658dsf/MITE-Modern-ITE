package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.mite.client.config.MonsterSpawnSettingsScreen;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;

/** Entry point for local editing and the authoritative settings browser. @author NingMeng123 */
public final class IteSettingsScreen extends Screen {
    private final ModContainer container;
    private final Screen parent;

    public IteSettingsScreen(ModContainer container, Screen parent) {
        super(Component.translatable("modern_ite.configuration.title"));
        this.container = container;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(260, width - 32);
        int y = Math.max(100, height / 2 - 24);
        var spawns = addRenderableWidget(Button.builder(Component.translatable("modern_ite.config.monster_spawns"),
                ignored -> minecraft.setScreen(new MonsterSpawnSettingsScreen(this, ModernIte.MOD_ID,
                        IteSpawnConfig::effectiveSnapshot, values -> {
                            if(IteConfigClient.editable())IteSpawnConfig.save(values);
                        }, IteSettingsScreen::nativeSpawnWeights)))
                .bounds((width - buttonWidth) / 2, y, buttonWidth, 20).build());
        spawns.setTooltip(Tooltip.create(Component.translatable("modern_ite.config.monster_spawns.tooltip")));
        var edit = addRenderableWidget(Button.builder(Component.translatable("modern_ite.config.edit"),
                ignored -> {
                    if (IteConfigClient.editable()) minecraft.setScreen(IteConfigurationScreen.create(container, this));
                }).bounds((width - buttonWidth) / 2, y + 28, buttonWidth, 20).build());
        edit.active = IteConfigClient.editable();
        edit.setTooltip(Tooltip.create(Component.translatable(edit.active
                ? "modern_ite.config.edit.tooltip" : "modern_ite.config.server_owned")));
        addRenderableWidget(Button.builder(Component.translatable("modern_ite.config.values"),
                ignored -> minecraft.setScreen(new IteConfigValuesScreen(this)))
                .bounds((width - buttonWidth) / 2, y + 56, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        int textWidth = Math.min(360, width - 32);
        graphics.drawWordWrap(font, Component.translatable(IteConfigClient.editable()
                        ? "modern_ite.config.scope" : "modern_ite.config.server_owned"),
                (width - textWidth) / 2, 42, textWidth, 0xCCCCCC);
        graphics.drawCenteredString(font, "config/modern_ite-common.toml", width / 2, height - 48, 0x999999);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private static List<Integer> nativeSpawnWeights(EntityType<?> type) {
        for(var kind:ExtremeKind.values())if(ExtremeMobs.type(kind)==type)return List.of(ExtremeMobs.spawnWeight(kind));
        return List.of();
    }
}
