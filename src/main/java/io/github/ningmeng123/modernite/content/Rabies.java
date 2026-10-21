package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.Comparator;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Six seconds of involuntary melee, settled by the server's normal attack path. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class Rabies {
    public static final int DURATION = 120;

    private Rabies() {}

    public static boolean active(Player player) {
        return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.hasEffect(ExtremeEffects.VALUES.get("rabies"));
    }

    public static LivingEntity target(Player player) {
        double reach = player.entityInteractionRange();
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                        entity -> eligible(player, entity))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static boolean eligible(Player player, LivingEntity entity) {
        if (entity == player || !entity.isAlive() || !entity.isAttackable() || entity.isSpectator()
                || entity.isPassengerOfSameVehicle(player) || !player.canInteractWithEntity(entity, 0)
                || !player.hasLineOfSight(entity)) return false;
        if (entity instanceof Player other) {
            if (other.isCreative() || !player.canHarmPlayer(other)) return false;
            if (player.getServer() != null && !player.getServer().isPvpAllowed()) return false;
        }
        return true;
    }

    public static void turn(Player player, LivingEntity target) {
        if (target == null) {
            player.setYRot(Mth.wrapDegrees(player.getYRot() + 30F));
            player.setXRot((float)Math.sin(player.tickCount * .7D) * 25F);
        } else {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
            player.setYRot((float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90F);
            player.setXRot((float)(-Mth.atan2(delta.y, delta.horizontalDistance()) * Mth.RAD_TO_DEG));
        }
        player.setYHeadRot(player.getYRot());
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        tick(event.getEntity());
    }

    public static void tick(Player player) {
        if (player.level().isClientSide || !active(player)) return;
        LivingEntity target = target(player);
        turn(player, target);
        // Frequent empty swings give visible loss of control. Damage still follows
        // vanilla cooldown, weapon, durability, enchantment and PvP rules.
        if (player.tickCount % 5 == 0) player.swing(InteractionHand.MAIN_HAND, true);
        if (target != null && player.tickCount % 10 == 0) {
            player.stopUsingItem();
            player.attack(target);
        }
    }
}
