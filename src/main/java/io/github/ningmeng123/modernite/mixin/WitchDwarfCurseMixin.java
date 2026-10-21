package io.github.ningmeng123.modernite.mixin;

import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.curse.WitchCursing;
import io.github.ningmeng123.modernite.content.DwarfCurse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds one outcome to the real witch selection without changing its targeting or delay. @author NingMeng123 */
@Mixin(value = WitchCursing.class, remap = false)
public abstract class WitchDwarfCurseMixin {
    @Redirect(method = "tryCurse", at = @At(value = "INVOKE", target = "Lio/github/ningmeng123/mite/curse/PlayerCurses;schedule(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/monster/Witch;Lio/github/ningmeng123/mite/curse/R196Curse;I)Z"))
    private static boolean modernite$dwarfChoice(ServerPlayer player, Witch witch, R196Curse original, int delay) {
        return DwarfCurse.scheduleSelected(player, witch, original, delay);
    }
}
