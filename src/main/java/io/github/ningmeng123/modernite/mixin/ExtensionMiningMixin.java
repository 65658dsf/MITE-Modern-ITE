package io.github.ningmeng123.modernite.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments;
import io.github.ningmeng123.modernite.content.ExtremeLifeEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.level.block.state.BlockState;
/** Chain mining starts only after the primary native break succeeds. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ExtensionMiningMixin {
 @Shadow @Final protected ServerPlayer player;
 @WrapMethod(method="destroyBlock") private boolean modernite$chain(BlockPos pos,Operation<Boolean> original) {
   var state=player.level().getBlockState(pos);boolean removed=original.call(pos);
   if(removed&&!player.level().getBlockState(pos).is(state.getBlock())) ExtremeEnchantments.afterMined(player,pos,state);return removed;
 }
 @WrapMethod(method="removeBlock") private boolean modernite$commitRemoval(BlockPos pos,BlockState state,boolean canHarvest,Operation<Boolean> original) {
   String owner=ExtremeLifeEvents.foreignStrongboxOwner(player,pos);
   boolean removed=original.call(pos,state,canHarvest);
   if(removed)ExtremeLifeEvents.afterBlockRemoved(player,pos,state,owner);
   return removed;
 }
}
