package io.github.ningmeng123.modernite.content;

import java.util.List;
import java.util.Locale;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Portable offhand lantern; legacy core registry IDs remain valid. @author NingMeng123 */
public final class DynamicCoreItem extends Item {
    public static final int REPAIR_COMPONENTS = 5;

    public enum Material {
        COPPER(800), SILVER(1200), GOLD(1600), IRON(2400), ANCIENT_METAL(3200),
        MITHRIL(4800), ADAMANTIUM(8000), VIBRANIUM(12800);

        public final int durability;
        public final String id;
        Material(int durability) {
            this.durability = durability;
            this.id = name().toLowerCase(Locale.ROOT);
        }
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

    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        Item nugget = MaterialAnvilRules.repairItem(material.id);
        return nugget != null && ingredient.is(nugget);
    }

    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || !ItemStack.isSameItem(oldStack, newStack)) return true;
        if (ItemStack.matches(oldStack, newStack)) return false;
        // Fuel updates replace the client stack, but should not lower and raise the carrying hand.
        ItemStack previous = oldStack.copy();
        previous.setDamageValue(newStack.getDamageValue());
        return !ItemStack.matches(previous, newStack);
    }

    @Override public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.modern_ite.lantern.offhand").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.modern_ite.lantern.remaining",
                Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage())
                .withStyle(hasFuel(stack) ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.modern_ite.lantern.durability").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.modern_ite.lantern.repair").withStyle(ChatFormatting.GRAY));
    }
}
