package io.github.ningmeng123.modernite.mixin.entity;

import io.github.ningmeng123.mite.world.SourceExplosionItems;
import io.github.ningmeng123.modernite.entity.InfernalExplosionLoot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep existing items intact instead of creating material fragments during an infernal detonation. @author NingMeng123 */
@Mixin(SourceExplosionItems.class)
public abstract class InfernalExplosionItemLootMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void modernIte$noInfernalFragments(ItemEntity entity, Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
        // "Handled" also removes this item from vanilla's subsequent damage and knockback pass.
        if (InfernalExplosionLoot.isInfernalExplosion(explosion)) cir.setReturnValue(true);
    }
}
