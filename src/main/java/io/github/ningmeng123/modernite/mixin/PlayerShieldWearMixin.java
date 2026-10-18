package io.github.ningmeng123.modernite.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** ITE triples accepted player shield wear before native enchantments and break callbacks. @author NingMeng123 */
@Mixin(Player.class)
public abstract class PlayerShieldWearMixin {
    @ModifyArg(method = "hurtCurrentlyUsedShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"), index = 0)
    private int modernite$tripleShieldWear(int amount) {
        return Math.clamp((long)amount * 3L, 0, Integer.MAX_VALUE);
    }
}
