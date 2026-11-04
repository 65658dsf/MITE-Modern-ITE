package io.github.ningmeng123.modernite.content.wither;

import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemType;
import io.github.ningmeng123.modernite.gear.GemBoxItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/** A server-authoritative opening exchanges one treasure for one reward. @author NingMeng123 */
public final class WitherTreasureItem extends GemBoxItem {
    public WitherTreasureItem(Properties properties) { super(properties.fireResistant().rarity(Rarity.EPIC)); }

    @Override protected ItemStack createOpeningReward(Player player) {
        ItemStack reward = createReward(player.level(), player.getRandom(), WitherTreasureRewards.reward(player.getRandom().nextInt(100)));
        if (reward.isEmpty())
            player.displayClientMessage(Component.translatable("message.modern_ite.wither_treasure_no_enchantments"), true);
        return reward;
    }

    @Override protected boolean consumesInCreative() { return true; }

    static ItemStack createReward(Level level, RandomSource random, WitherTreasureRewards.Reward reward) {
        return switch (reward) {
            case GEM_3 -> gem(random, 3);
            case GEM_4 -> gem(random, 4);
            case GEM_5 -> gem(random, 5);
            case GEM_6 -> gem(random, 6);
            case DIAMONDS -> new ItemStack(Items.DIAMOND, 10 + random.nextInt(11));
            case ENCHANTED_BOOK -> maximumBook(level, random);
        };
    }

    private static ItemStack gem(RandomSource random, int rank) {
        GemType[] types = GemType.values();
        return ExtremeGear.gem(types[random.nextInt(types.length)], rank);
    }

    private static ItemStack maximumBook(Level level, RandomSource random) {
        var pool = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                .filter(ConfiguredEnchantments::enabled)
                .filter(holder -> !holder.is(EnchantmentTags.CURSE) && holder.value().getMaxLevel() > 0).toList();
        if (pool.isEmpty()) return ItemStack.EMPTY;
        var chosen = pool.get(random.nextInt(pool.size()));
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(chosen, chosen.value().getMaxLevel());
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return book;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.modern_ite.wither_treasure_open").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.modern_ite.wither_treasure_gems").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.modern_ite.wither_treasure_other").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.modern_ite.wither_treasure_source").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
