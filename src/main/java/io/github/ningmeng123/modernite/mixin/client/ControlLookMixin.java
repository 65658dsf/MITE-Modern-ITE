package io.github.ningmeng123.modernite.mixin.client;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
/** Fast sensitivity is an input multiplier, not a permanent settings mutation. @author NingMeng123 */
@Mixin(MouseHandler.class)
public abstract class ControlLookMixin {
    @ModifyArgs(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void modernite$sensitivity(Args args){
        var player=Minecraft.getInstance().player;
        if(io.github.ningmeng123.modernite.content.Rabies.active(player)){args.set(0,0D);args.set(1,0D);return;}
        if(player!=null&&player.hasEffect(ExtremeEffects.VALUES.get("fast_sensitivity"))){args.set(0,(double)args.get(0)*12);args.set(1,(double)args.get(1)*12);}
        if(io.github.ningmeng123.modernite.content.TimeDilation.active(player)){args.set(0,(double)args.get(0)/io.github.ningmeng123.modernite.content.TimeDilation.TICK_DIVISOR);args.set(1,(double)args.get(1)/io.github.ningmeng123.modernite.content.TimeDilation.TICK_DIVISOR);}
    }
}
