package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Show one totem on the player's back while any equipped Emergency enchantment is ready. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EmergencyTotemLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ItemStack TOTEM = new ItemStack(Items.TOTEM_OF_UNDYING);
    private final ItemRenderer itemRenderer;

    private EmergencyTotemLayer(PlayerRenderer playerRenderer, ItemRenderer itemRenderer) {
        super(playerRenderer);
        this.itemRenderer = itemRenderer;
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        Set<PlayerRenderer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer && seen.add(renderer)) {
                renderer.addLayer(new EmergencyTotemLayer(renderer, event.getContext().getItemRenderer()));
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float walk, float amount, float partialTick, float age, float yaw, float pitch) {
        var body = getParentModel().body;
        if (!body.visible || player.isInvisible() || !hasReadyArmor(player)) return;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean chestplate = chest.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST;
        float backDepth = chestplate ? 3.5F : 2.6F;
        pose.pushPose();
        try {
            body.translateAndRotate(pose);
            pose.translate(0, 6F / 16F, backDepth / 16F);
            pose.mulPose(Axis.ZP.rotationDegrees(180F));
            pose.scale(.5F, .5F, .5F);
            itemRenderer.renderStatic(player, TOTEM, ItemDisplayContext.FIXED, false, pose, buffers,
                    player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        } finally {
            pose.popPose();
        }
    }

    private static boolean hasReadyArmor(AbstractClientPlayer player) {
        for (ItemStack stack : player.getArmorSlots()) {
            if (ExtremeEnchantments.isEmergencyReady(stack)) return true;
        }
        return false;
    }
}
