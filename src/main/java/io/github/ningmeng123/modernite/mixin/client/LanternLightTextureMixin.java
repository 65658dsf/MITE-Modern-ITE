package io.github.ningmeng123.modernite.mixin.client;

import io.github.ningmeng123.modernite.content.ExtremeLifeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Offhand lanterns use the vanilla night-vision lightmap without adding a potion effect. @author NingMeng123 */
@Mixin(LightTexture.class)
public abstract class LanternLightTextureMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean updateLightTexture;
    @Unique private boolean modernite$lanternLightActive;

    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void modernite$refreshLanternLight(float partialTick, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        boolean active = player != null && !player.isSpectator() && ExtremeLifeEvents.coreLevel(player) > 0;
        if (active != this.modernite$lanternLightActive) {
            this.modernite$lanternLightActive = active;
            // Equipment can change between vanilla's lightmap ticks; restore darkness on the next rendered frame.
            this.updateLightTexture = true;
        }
    }

    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z", ordinal = 0))
    private boolean modernite$includeLanternLight(LocalPlayer player, Holder<MobEffect> effect) {
        return player.hasEffect(effect) || (effect == MobEffects.NIGHT_VISION && this.modernite$lanternLightActive);
    }

    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
    private float modernite$lanternBrightness(LivingEntity entity, float partialTick) {
        return this.modernite$lanternLightActive ? 1.0F : GameRenderer.getNightVisionScale(entity, partialTick);
    }
}
