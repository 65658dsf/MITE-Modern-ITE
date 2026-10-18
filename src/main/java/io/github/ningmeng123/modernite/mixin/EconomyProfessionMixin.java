package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.modernite.economy.ExtremeEconomy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Job replacement invalidates stock even when no offers were read while unemployed. @author NingMeng123 */
@Mixin(Villager.class)
public abstract class EconomyProfessionMixin {
    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void modernite$newJob(VillagerData data, CallbackInfo ci) {
        Villager self = (Villager)(Object)this;
        if (self.getVillagerData().getProfession() != data.getProfession()) self.getPersistentData().remove(ExtremeEconomy.STATE);
    }
}
