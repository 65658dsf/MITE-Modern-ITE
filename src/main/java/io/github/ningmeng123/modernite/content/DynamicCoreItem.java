package io.github.ningmeng123.modernite.content;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Portable offhand lantern; legacy core registry IDs remain valid. @author NingMeng123 */
public final class DynamicCoreItem extends Item {
    public enum Material {
        COPPER(1600), SILVER(2400), GOLD(3200), IRON(4800), ANCIENT_METAL(6400),
        MITHRIL(9600), ADAMANTIUM(16000), VIBRANIUM(25600);

        public final int durability;
        Material(int durability) { this.durability = durability; }
    }

    public final Material material;
    public final int level = 5;

    public DynamicCoreItem(Material material, Properties properties) {
        super(properties.durability(material.durability).fireResistant());
        this.material = material;
    }

    public static boolean hasFuel(ItemStack stack) {
        return stack.getItem() instanceof DynamicCoreItem && stack.getMaxDamage() > 0
                && stack.getDamageValue() < stack.getMaxDamage();
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.modern_ite.lantern.offhand").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.modern_ite.lantern.durability").withStyle(ChatFormatting.GRAY));
    }
}
