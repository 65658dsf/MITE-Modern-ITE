package io.github.ningmeng123.modernite.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise owned-arrow damage through the real living-damage event chain. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ArrowAbilityGameTests {
    @GameTest(template = "gear_empty")
    public static void lightningArrowDealsExactlyThreeHeartsThroughArmorAndResistance(GameTestHelper h) {
        var shooter = ExtremeMobs.type(ExtremeKind.SKELETON_LIGHTNING).create(h.getLevel());
        shooter.moveTo(h.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); player.setHealth(40);
        player.moveTo(shooter.position().add(2, 0, 0));
        var protection = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        var armor = new ItemStack(Items.IRON_CHESTPLATE); armor.enchant(protection, 5);
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
        var arrow = EntityType.ARROW.create(h.getLevel()); arrow.setOwner(shooter);
        float before = player.getHealth();
        player.hurt(h.getLevel().damageSources().arrow(arrow, shooter), 2);
        h.assertTrue(Math.abs(before - player.getHealth() - 6) < .001, "Lightning arrows deal exactly six health points despite armor and resistance");
        h.assertTrue(!h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, player.getBoundingBox().inflate(2)).isEmpty(), "The hit produces a visual lightning strike");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void floatingSkeletonArrowAppliesLevitationThroughDamageEvents(GameTestHelper h) {
        var shooter = ExtremeMobs.type(ExtremeKind.SKELETON_FLOATING).create(h.getLevel());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var arrow = EntityType.ARROW.create(h.getLevel()); arrow.setOwner(shooter);
        player.hurt(h.getLevel().damageSources().arrow(arrow, shooter), 1);
        h.assertTrue(player.hasEffect(MobEffects.LEVITATION), "An actual owned arrow hit applies levitation to its player victim");
        h.succeed();
    }
}
