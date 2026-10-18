package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgression;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Live socket and forging effects; neither system writes ordinary enchantments. @author NingMeng123 */
public final class GearEvents {
    private GearEvents() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!ExtremeGear.socketable(stack)) return;
        double factor = GearMath.enhanceFactor(ExtremeGear.grade(stack));
        double attack = ExtremeGear.rank(stack, GemType.DAMAGE);
        if (GearProgression.canKill(stack)) {
            double base = 1;
            for (var entry : List.copyOf(event.getModifiers())) if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) base += entry.modifier().amount();
            attack += Math.max(0, base) * factor;
        }
        if (EquipmentDurability.toolProfile(stack) != null && attack > 0) event.addModifier(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(ExtremeGear.id("socket_damage"), attack, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (stack.getItem() instanceof ArmorItem armor) {
            double base = 0;
            for (var entry : List.copyOf(event.getModifiers())) if (entry.attribute().equals(Attributes.ARMOR)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) base += entry.modifier().amount();
            double protection = ExtremeGear.rank(stack, GemType.PROTECTION) * .25
                    + (factor * base * .68 + ExtremeGear.grade(stack) / 3D) * .75;
            if (protection > 0) event.addModifier(Attributes.ARMOR, new AttributeModifier(ExtremeGear.id("socket_armor_" + armor.getEquipmentSlot().getName()), protection,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.bySlot(armor.getEquipmentSlot()));
            if (armor.getEquipmentSlot() == EquipmentSlot.FEET && ExtremeGear.rank(stack, GemType.SPEED) > 0)
                event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(ExtremeGear.id("socket_speed"),
                        ExtremeGear.rank(stack, GemType.SPEED) * .05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.FEET);
        }
    }
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void mining(PlayerEvent.BreakSpeed event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (!ExtremeGear.socketable(stack) || event.getNewSpeed() <= 0) return;
        int rank = ExtremeGear.rank(stack, GemType.EFFICIENCY);
        float speed = event.getNewSpeed();
        if (rank > 0 && stack.getDestroySpeed(event.getState()) > 1F) speed += rank * rank + 1;
        if (!GearProgression.canKill(stack) && !event.getEntity().isInWaterRainOrBubble()) speed *= 1 + GearMath.enhanceFactor(ExtremeGear.grade(stack));
        event.setNewSpeed(speed);
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.isCanceled()) return;
        int rank = event.getSource().is(DamageTypeTags.IS_FIRE) ? ExtremeGear.rank(player.getItemBySlot(EquipmentSlot.CHEST), GemType.FIREPROOF)
                : event.getSource().is(DamageTypeTags.IS_EXPLOSION) ? ExtremeGear.rank(player.getItemBySlot(EquipmentSlot.LEGS), GemType.EXPLOSION_PROOF) : 0;
        if (rank > 0 && !event.getSource().is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) event.setAmount(event.getAmount() * (1F - rank * .125F));
    }
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!ExtremeGear.socketable(stack)) return;
        int grade = ExtremeGear.grade(stack), grindings = stack.getOrDefault(ExtremeGear.GRINDINGS.get(), 0);
        if (grade > 0) event.getToolTip().add(Component.translatable("tooltip.modern_ite.forge_grade", grade,
                Math.round(GearMath.enhanceFactor(grade) * 100)).withStyle(ChatFormatting.GOLD));
        if (grindings > 0) event.getToolTip().add(Component.translatable("tooltip.modern_ite.grindings", grindings).withStyle(ChatFormatting.AQUA));
        for (GemType type : GemType.values()) {
            int rank = ExtremeGear.rank(stack, type);
            if (rank > 0) event.getToolTip().add(Component.translatable("tooltip.modern_ite.socket", Component.translatable("gem.modern_ite." + type.id), rank)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
