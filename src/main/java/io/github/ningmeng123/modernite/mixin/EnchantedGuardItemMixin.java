package io.github.ningmeng123.modernite.mixin;
import io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** ITE timed tool guard remains separate from the modern shield system. @author NingMeng123 */
@Mixin(Item.class)
public abstract class EnchantedGuardItemMixin {
 private static boolean modernite$club(ItemStack stack) {
  var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
  return id.getNamespace().equals("modern_ite")&&id.getPath().startsWith("club_");
 }
 @Inject(method="getUseAnimation",at=@At("HEAD"),cancellable=true) private void modernite$animation(ItemStack stack,CallbackInfoReturnable<UseAnim> cir) {
  if(ExtremeEnchantments.guard(stack)||modernite$club(stack)) cir.setReturnValue(UseAnim.BLOCK);
 }
 @Inject(method="getUseDuration",at=@At("HEAD"),cancellable=true) private void modernite$duration(ItemStack stack,LivingEntity user,CallbackInfoReturnable<Integer> cir) {
  if(ExtremeEnchantments.guard(stack)) cir.setReturnValue(ExtremeEnchantments.guardDuration());
  else if(modernite$club(stack)) cir.setReturnValue(72000);
 }
 @Inject(method="use",at=@At("HEAD"),cancellable=true) private void modernite$guard(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
  if(ExtremeEnchantments.beginGuard(player,hand)) cir.setReturnValue(InteractionResultHolder.consume(player.getItemInHand(hand)));
  else if(modernite$club(player.getItemInHand(hand))&&!ExtremeEnchantments.guard(player.getItemInHand(hand))) {
   player.startUsingItem(hand);cir.setReturnValue(InteractionResultHolder.consume(player.getItemInHand(hand)));
  }
 }
}
