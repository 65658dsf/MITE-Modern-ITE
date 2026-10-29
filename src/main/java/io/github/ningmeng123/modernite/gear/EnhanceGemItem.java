package io.github.ningmeng123.modernite.gear;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** One real registry entry per category and stage. @author NingMeng123 */
public final class EnhanceGemItem extends Item {
    public final GemType type;
    public final int rank;
    public EnhanceGemItem(GemType type, int rank, Properties properties) {
        super(properties.stacksTo(1).fireResistant()); this.type = type; this.rank = rank;
    }
    public int code() { return type.ordinal() * 6 + rank; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        double value = switch (type) {
            case RECOVER, FIREPROOF, EXPLOSION_PROOF -> rank * 12.5;
            case DURABILITY, SPEED -> rank * 5;
            case EFFICIENCY -> rank * rank + 1;
            default -> type.rate * rank;
        };
        tooltip.add(Component.translatable("tooltip.modern_ite.gem_" + type.id,
                String.format(java.util.Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", ""))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.modern_ite.gem_highest").withStyle(ChatFormatting.GRAY));
        String target = switch (type) {
            case DAMAGE, EFFICIENCY -> "tools";
            case HEALTH, PROTECTION, RECOVER, NUTRITION -> "armor";
            case SPEED -> "boots";
            case FIREPROOF, EXPLOSION_PROOF -> "chest_legs";
            case DURABILITY -> "equipment";
        };
        tooltip.add(Component.translatable("tooltip.modern_ite.gem_target_" + target).withStyle(ChatFormatting.GRAY));
    }
}
