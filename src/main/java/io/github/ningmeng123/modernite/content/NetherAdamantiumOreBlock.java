package io.github.ningmeng123.modernite.content;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Raw adamantium uses source fortune while retaining the ore's native harvest XP. @author NingMeng123 */
public final class NetherAdamantiumOreBlock extends DropExperienceBlock {
    public static final MapCodec<NetherAdamantiumOreBlock> CODEC = simpleCodec(NetherAdamantiumOreBlock::new);

    public NetherAdamantiumOreBlock(Properties properties) {
        super(UniformInt.of(3, 7), properties);
    }

    @Override public MapCodec<NetherAdamantiumOreBlock> codec() { return CODEC; }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder context) {
        var drops = super.getDrops(state, context);
        ItemStack tool = context.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || tool.isEmpty() || context.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null)
            return drops;
        var enchantments = context.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var fortune = EnchantmentSources.levels(tool, enchantments.getHolderOrThrow(Enchantments.FORTUNE));
        if (!EnchantmentSources.present(fortune)) return drops;
        float chance = (float)EnchantmentSources.probability(fortune, level -> (1F + level * .1F) - 1F);
        for (ItemStack drop : drops) {
            if (!drop.is(MiteMaterials.RAW_ADAMANTIUM.get())) continue;
            int base = drop.getCount();
            int extra = 0;
            for (int unit = 0; unit < base; unit++)
                if (context.getLevel().random.nextFloat() < chance) extra++;
            drop.grow(extra);
        }
        return drops;
    }
}
