package io.github.ningmeng123.modernite.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.modernite.client.PlayerTimeDilationRendering;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Third-person player position uses the same continuous clock as its camera. @author NingMeng123 */
@Mixin(LevelRenderer.class)
public abstract class PlayerTimeDilationWorldRenderMixin {
    @ModifyArgs(method = "renderEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void modernite$position(Args args, Entity entity, double cameraX, double cameraY, double cameraZ, float partial, PoseStack pose, MultiBufferSource buffers) {
        if (!(entity instanceof Player player) || !TimeDilation.active(player)) return;
        var position = PlayerTimeDilationRendering.position(player, partial);
        args.set(1, position.x - cameraX); args.set(2, position.y - cameraY); args.set(3, position.z - cameraZ);
    }
}
