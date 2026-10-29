package io.github.ningmeng123.modernite.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Actual socketed gem items mounted on the animated chestplate surface. @author NingMeng123 */
public final class ChestGemLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public static final float ITEM_SCALE = .09F;
    public static final float FRONT_Z = -3.14F / 16F;
    // Small centered brooches, ordered by occupied sockets rather than leaving gaps for empty ones.
    // Coordinates use the torso's model pixels, with the center below the collar at y=4.6.
    private static final float[][][] MOUNTS = {
            {},
            {{0F, 4.6F}},
            {{0F, 3.85F}, {0F, 5.35F}},
            {{0F, 3.55F}, {-.9F, 5.15F}, {.9F, 5.15F}},
            {{0F, 3.4F}, {-1.15F, 4.6F}, {1.15F, 4.6F}, {0F, 5.8F}},
            {{0F, 4.6F}, {0F, 2.95F}, {-1.5F, 4.6F}, {1.5F, 4.6F}, {0F, 6.25F}},
            {{0F, 2.95F}, {1.34F, 3.775F}, {1.34F, 5.425F},
                    {0F, 6.25F}, {-1.34F, 5.425F}, {-1.34F, 3.775F}},
            {{0F, 4.6F}, {0F, 2.85F}, {1.43F, 3.725F}, {1.43F, 5.475F},
                    {0F, 6.35F}, {-1.43F, 5.475F}, {-1.43F, 3.725F}},
            {{0F, 2.8F}, {1.17F, 3.33F}, {1.65F, 4.6F}, {1.17F, 5.87F},
                    {0F, 6.4F}, {-1.17F, 5.87F}, {-1.65F, 4.6F}, {-1.17F, 3.33F}}
    };

    public ChestGemLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** Preserve socket order and duplicate gems; visuals describe the stored items, not only active effects. */
    public record SocketGem(int slot, int code, GemType type, int rank) {}

    public static List<SocketGem> visibleSockets(ItemStack chest) {
        if (chest.isEmpty() || !(chest.getItem() instanceof ArmorItem armor)
                || armor.getEquipmentSlot() != EquipmentSlot.CHEST) return List.of();
        var sockets = ExtremeGear.sockets(chest).values();
        List<SocketGem> visible = new ArrayList<>(8);
        for (int slot = 0; slot < sockets.size(); slot++) {
            int code = sockets.get(slot);
            if (code != 0) visible.add(new SocketGem(slot, code,
                    GemType.values()[(code - 1) / 6], (code - 1) % 6 + 1));
        }
        return List.copyOf(visible);
    }

    /** Positions are expressed in the same pixel coordinates as HumanoidModel.body. */
    public static float socketX(int index, int count) { return MOUNTS[count][index][0]; }
    public static float socketY(int index, int count) { return MOUNTS[count][index][1]; }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float age,
                       float headYaw, float headPitch) {
        M parent = getParentModel();
        if (!parent.body.visible) return;
        List<SocketGem> sockets = visibleSockets(entity.getItemBySlot(EquipmentSlot.CHEST));
        if (sockets.isEmpty()) return;
        pose.pushPose();
        try {
            // Match HumanoidModel's body-group transform for a young wearer before applying its torso pose.
            if (parent.young) {
                pose.translate(0, .75D, 0);
                pose.scale(.5F, .5F, .5F);
            }
            parent.body.translateAndRotate(pose);
            var renderer = Minecraft.getInstance().getItemRenderer();
            for (int index = 0; index < sockets.size(); index++) {
                SocketGem socket = sockets.get(index);
                pose.pushPose();
                try {
                    pose.translate(socketX(index, sockets.size()) / 16F, socketY(index, sockets.size()) / 16F, FRONT_Z);
                    // Item models point up and forward; the humanoid torso points down and towards negative Z.
                    pose.mulPose(Axis.XP.rotationDegrees(180));
                    pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
                    renderer.renderStatic(entity, ExtremeGear.fromCode(socket.code()), ItemDisplayContext.NONE,
                            false, pose, buffers, entity.level(), light, OverlayTexture.NO_OVERLAY, entity.getId());
                } finally {
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }
}
