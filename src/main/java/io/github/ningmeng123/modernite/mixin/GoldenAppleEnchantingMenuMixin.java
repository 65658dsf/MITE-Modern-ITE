package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.modernite.item.IteItemRules;
import java.util.Arrays;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Price the core mod's apple offer before its data slots sync and its transaction revalidates. @author NingMeng123 */
@Mixin(value = EnchantmentMenu.class, priority = 900)
public abstract class GoldenAppleEnchantingMenuMixin extends AbstractContainerMenu {
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow @Final private Container enchantSlots;
    // Accessors resolve after MITE has merged its fields into the native menu.
    @Dynamic("Added by MITE's EmeraldEnchantmentMenuMixin")
    @Accessor(value = "mite$pointCosts", remap = false)
    protected abstract int[] modernite$getPointCosts();

    protected GoldenAppleEnchantingMenuMixin(MenuType<?> type, int id) { super(type, id); }

    @Override
    public void broadcastChanges() {
        if (access != ContainerLevelAccess.NULL) {
            var input = enchantSlots.getItem(0);
            if (input.is(Items.GOLDEN_APPLE) && input.getCount() == 1 && !input.isEnchanted())
                Arrays.fill(modernite$getPointCosts(), IteItemRules.GOLDEN_APPLE_ENCHANTING_COST);
        }
        super.broadcastChanges();
    }
}
