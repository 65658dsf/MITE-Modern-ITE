package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.mite.crafting.SourceVillagerTrades;
import io.github.ningmeng123.modernite.economy.ExtremeEconomy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends the single existing source stock hook, after baseline offers are ensured. @author NingMeng123 */
@Mixin(value = SourceVillagerTrades.class, remap = false)
public abstract class ExtremeEconomyMixin {
    @Inject(method = "ensure", at = @At("RETURN"))
    private static void modernite$extremeStock(Villager villager, MerchantOffers offers, CallbackInfo ci) {
        ExtremeEconomy.ensure(villager, offers);
    }
}
