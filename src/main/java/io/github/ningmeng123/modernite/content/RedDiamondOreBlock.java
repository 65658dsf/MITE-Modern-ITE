package io.github.ningmeng123.modernite.content;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Natural red diamonds use source 10%-per-fortune drops; placed ore cannot multiply. @author NingMeng123 */
public final class RedDiamondOreBlock extends DropExperienceBlock {
    public static final MapCodec<RedDiamondOreBlock> CODEC=simpleCodec(RedDiamondOreBlock::new);
    public static final BooleanProperty PLACED=BooleanProperty.create("placed");
    public RedDiamondOreBlock(Properties properties) {
        super(UniformInt.of(2,5),properties);registerDefaultState(stateDefinition.any().setValue(PLACED,false));
    }
    @Override public MapCodec<RedDiamondOreBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(PLACED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(PLACED,true);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context) {
        var ordinary=super.getDrops(state,context);if(ordinary.isEmpty())return ordinary;
        ItemStack tool=context.getOptionalParameter(LootContextParams.TOOL);if(tool==null)tool=ItemStack.EMPTY;
        var registry=context.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        if(tool.getEnchantmentLevel(registry.getHolderOrThrow(Enchantments.SILK_TOUCH))>0)return List.of(new ItemStack(this));
        int fortune=state.getValue(PLACED)?0:tool.getEnchantmentLevel(registry.getHolderOrThrow(Enchantments.FORTUNE));
        double amount=1+Math.max(0,fortune)*.1D;int count=(int)amount;
        if(context.getLevel().random.nextDouble()<amount-count)count++;
        return List.of(new ItemStack(ExtremeContent.FANCY_RED.get(),Math.min(8,count)));
    }
}
