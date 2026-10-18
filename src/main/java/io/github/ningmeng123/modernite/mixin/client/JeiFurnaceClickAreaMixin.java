package io.github.ningmeng123.modernite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.modernite.integration.jei.VibraniumFurnaceJei;
import java.util.stream.Stream;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Optional JEI hook preserving all other furnace screens and GUI handlers. @author NingMeng123 */
@Pseudo
@Mixin(targets = "mezz.jei.library.gui.helpers.ScreenHelper", remap = false)
public abstract class JeiFurnaceClickAreaMixin {
    @ModifyReturnValue(method = "getGuiClickableArea(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;DD)Ljava/util/stream/Stream;",
            at = @At("RETURN"))
    private Stream<IGuiClickableArea> modernite$vibraniumArrow(Stream<IGuiClickableArea> original,
            AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        return VibraniumFurnaceJei.prioritizeClickArea(original, screen, mouseX, mouseY);
    }
}
