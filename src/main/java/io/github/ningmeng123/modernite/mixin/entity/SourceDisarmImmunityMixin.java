package io.github.ningmeng123.modernite.mixin.entity;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.modernite.entity.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Source canBeDisarmed overrides at the single authoritative disarm transaction. @author NingMeng123 */
@Mixin(SourceEnchantmentCombat.class)
public class SourceDisarmImmunityMixin {
    @Inject(method="disarm",at=@At("HEAD"),cancellable=true)
    private static void modernIte$immune(LivingEntity attacker,LivingEntity target,CallbackInfo ci){
        if(!IteMobSettingsRules.disarmable(target))ci.cancel();
    }
}
