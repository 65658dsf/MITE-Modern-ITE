package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Legacy vibranium kits, with an ordinary lord's hammer requested by the user. @author NingMeng123 */
public final class ZombieLordEquipment {
    private static final String REVISION = "modern_ite:zombie_lord_equipment_revision";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

    private ZombieLordEquipment() {}

    static void initialize(Mob lord, ExtremeKind kind) {
        int day = MobAbilities.day(lord.level());
        if (kind == ExtremeKind.ZOMBIE_LORD) day = Math.min(day, IteConfig.integer("Entities.enhance_limit"));
        boolean enchant = day > 64;
        lord.setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(lord, "mite:vibranium_war_hammer", enchant));
        for (int i = 0; i < ARMOR.length; i++)
            lord.setItemSlot(ARMOR[i], R196MobEquipment.generated(lord, "mite:vibranium_" + PIECES[i], enchant));
        lord.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        // The legacy kit has no issued shield; suppress only the random newborn shield roll.
        lord.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
        if (MobAbilities.underground(lord.level())) {
            var registry = lord.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            increase(lord, lord.getMainHandItem(), registry.getHolderOrThrow(Enchantments.SHARPNESS));
            for (var slot : ARMOR) increase(lord, lord.getItemBySlot(slot), registry.getHolderOrThrow(Enchantments.PROTECTION));
        }
        lord.getPersistentData().putInt(REVISION, 1);
    }

    private static void increase(Mob lord, ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        int level = lord.getRandom().nextInt(6);
        if (level > stack.getEnchantmentLevel(enchantment)) stack.enchant(enchantment, level);
    }

    static boolean canAcceptPickup(Mob lord, ItemStack incoming) {
        ItemStack current = lord.getMainHandItem();
        if (incoming.isEmpty() || current.isEmpty() || lord.getEquipmentSlotForItem(incoming) != EquipmentSlot.MAINHAND) return true;
        var next = MiteTools.definition(incoming);
        var previous = MiteTools.definition(current);
        if (next != null && previous != null) {
            int nextTier = materialTier(next.material()), previousTier = materialTier(previous.material());
            if (nextTier >= 0 && previousTier >= 0 && nextTier < previousTier) return false;
        }
        for (var entry : current.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet())
            if ((entry.getKey().is(Enchantments.SHARPNESS) || !entry.getKey().value().getEffects(EnchantmentEffectComponents.DAMAGE).isEmpty())
                    && incoming.getEnchantmentLevel(entry.getKey()) < entry.getIntValue()) return false;
        return attackDamage(lord, incoming) + 1E-8 >= attackDamage(lord, current);
    }

    private static int materialTier(String material) {
        return switch (material) {
            case "wood", "leather" -> 0;
            case "flint", "rusted_iron" -> 1;
            case "obsidian", "gold" -> 2;
            case "copper", "silver" -> 3;
            case "iron" -> 4;
            case "ancient_metal" -> 5;
            case "mithril" -> 6;
            case "adamantium", "netherite" -> 7;
            case "vibranium" -> 8;
            default -> -1;
        };
    }

    private static double attackDamage(Mob lord, ItemStack stack) {
        AttributeInstance attack = new AttributeInstance(Attributes.ATTACK_DAMAGE, ignored -> {});
        attack.setBaseValue(lord.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
        // ItemAttributeModifiers.compute also combines attack speed; only damage belongs in this comparison.
        for (var entry : stack.getAttributeModifiers().modifiers())
            if (entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND))
                attack.addOrUpdateTransientModifier(entry.modifier());
        return attack.getValue();
    }

    static void upgradeSaved(Mob lord, ExtremeKind kind) {
        if (!ZombieLordAbilities.handles(kind) || lord.getPersistentData().getInt(REVISION) >= 1) return;
        ItemStack held = lord.getMainHandItem();
        if (kind == ExtremeKind.ZOMBIE_LORD && held.getCount() == 1
                && BuiltInRegistries.ITEM.getKey(held.getItem()).equals(ResourceLocation.parse("mite:vibranium_sword"))
                && ((SourceMobDropChanceAccess) lord).mite$equipmentDropChance(EquipmentSlot.MAINHAND) <= 1F
                && held.getComponentsPatch().entrySet().stream().allMatch(entry ->
                        Set.of(DataComponents.DAMAGE, DataComponents.ENCHANTMENTS, EquipmentDurability.QUALITY.get()).contains(entry.getKey()))) {
            ItemStack hammer = held.transmuteCopy(R196MobEquipment.item("mite:vibranium_war_hammer"));
            if (held.isDamageableItem() && hammer.isDamageableItem())
                hammer.setDamageValue(Math.clamp((int)Math.round((double)held.getDamageValue() / held.getMaxDamage() * hammer.getMaxDamage()), 0, hammer.getMaxDamage() - 1));
            lord.setItemSlot(EquipmentSlot.MAINHAND, hammer);
        }
        // Existing armor, custom or picked-up weapons, and completed kits never reroll on load.
        lord.getPersistentData().putInt(REVISION, 1);
    }
}
