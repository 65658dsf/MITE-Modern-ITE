package io.github.ningmeng123.modernite.mixin;
import io.github.ningmeng123.modernite.survival.SourceUtilityRules;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The source near-player bonus advances only an already active native countdown. @author NingMeng123 */
@Mixin(BaseSpawner.class)
public abstract class SourceSpawnerSpeedMixin {
    @Shadow private int spawnDelay;
    @Shadow private int spawnRange;
    @Inject(method="serverTick",at=@At(value="FIELD",target="Lnet/minecraft/world/level/BaseSpawner;spawnDelay:I",opcode=Opcodes.PUTFIELD,ordinal=0,shift=At.Shift.AFTER))
    private void modernite$nearby(ServerLevel level,BlockPos pos,CallbackInfo ci) {
        if(IteConfig.bool("mob_spawner_speed_up_with_players")&&SourceUtilityRules.closeToSpawner(level,pos,spawnRange))spawnDelay=Math.max(0,spawnDelay-2);
    }
}
