package io.github.ningmeng123.modernite.gear;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.github.ningmeng123.mite.survival.PlayerHealthEvents;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live gem components pass through actual capacity, item attributes, healing and damage entry points. @author NingMeng123 */
@GameTestHolder("modern_ite_gem_effects")
@PrefixGameTestTemplate(false)
public final class GemEffectsGameTests {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Item[] ARMOR_ITEMS = {
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};

    private GemEffectsGameTests() {}

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void nutritionUsesEveryArmorPartAndHighestPerItemWithTwentyExtraPoints(GameTestHelper helper) {
        Player player = player(helper);
        try {
            player.experienceLevel = 35;
            for (int rank = 1; rank <= 6; rank++) {
                for (int index = 0; index < ARMOR_SLOTS.length; index++) {
                    clearArmor(player);
                    player.setItemSlot(ARMOR_SLOTS[index], gemmed(ARMOR_ITEMS[index], GemType.NUTRITION, rank));
                    helper.assertValueEqual(MiteExtensions.foodCapacity(player), 20 + rank,
                            "Each worn part contributes only its highest nutrition gem rank");
                }
                equipAll(player, GemType.NUTRITION, rank);
                helper.assertValueEqual(MiteExtensions.foodCapacity(player), 20 + Math.min(20, rank * 4),
                        "The four actual armor pieces add their ranks up to twenty extra points");
            }
            player.experienceLevel = 0;
            helper.assertValueEqual(MiteExtensions.foodCapacity(player), 26,
                    "Four rank-six gems add twenty to the level-zero base of six");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    gemmed(Items.IRON_SWORD, GemType.NUTRITION, 6));
            helper.assertValueEqual(MiteExtensions.foodCapacity(player), 26,
                    "A historical nutrition gem on a weapon cannot raise worn-armor capacity");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void realFoodReservesFillAboveTwentyAndClampWhenNutritionArmorIsRemoved(GameTestHelper helper) {
        Player player = player(helper);
        try {
            player.experienceLevel = 35;
            DualFoodData food = (DualFoodData) player.getFoodData();
            food.mite$bindPlayer(player);
            equipAll(player, GemType.NUTRITION, 6);
            player.getFoodData().setFoodLevel(100);
            food.mite$setNutrition(100);
            helper.assertValueEqual(player.getFoodData().getFoodLevel(), 40,
                    "The actual satiation setter retains the full gem capacity of forty");
            helper.assertValueEqual(food.mite$getNutrition(), 40,
                    "The actual nutrition setter retains the full gem capacity of forty");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            food.mite$clampToLimit();
            helper.assertValueEqual(player.getFoodData().getFoodLevel(), 38,
                    "Removing one rank-six piece clips satiation to base twenty plus eighteen");
            helper.assertValueEqual(food.mite$getNutrition(), 38,
                    "Removing one rank-six piece clips nutrition to the same current capacity");
            clearArmor(player);
            food.mite$clampToLimit();
            helper.assertValueEqual(player.getFoodData().getFoodLevel(), 20, "Removing all nutrition armor restores the base satiation cap");
            helper.assertValueEqual(food.mite$getNutrition(), 20, "Removing all nutrition armor restores the base nutrition cap");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void damageGemsReachTheActualMainhandAttributeForEveryRank(GameTestHelper helper) {
        for (int rank = 1; rank <= 6; rank++) {
            ItemStack sword = gemmed(Items.IRON_SWORD, GemType.DAMAGE, rank);
            close(helper, modifier(sword, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE, ExtremeGear.id("socket_damage")),
                    rank, "The real item-attribute event exposes the highest damage gem rank once");
            close(helper, modifier(sword, EquipmentSlot.OFFHAND, Attributes.ATTACK_DAMAGE, ExtremeGear.id("socket_damage")),
                    0D, "The damage gem modifier applies to the main hand");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void attackSpeedGemsShortenTheNativeMainhandCooldownWithoutStacking(GameTestHelper helper) {
        Player player = player(helper);
        try {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            player.tick();
            double baseSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
            double baseDelay = player.getCurrentItemAttackStrengthDelay();

            int firstCode = ((EnhanceGemItem) ExtremeGear.gem(GemType.ATTACK_SPEED, 1).getItem()).code();
            for (int rank = 1; rank <= 6; rank++) {
                sword.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, firstCode + rank - 1));
                player.tick();
                double multiplier = 1D + rank * .05D;
                close(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), baseSpeed * multiplier,
                        "Each rank increases the equipped weapon's attack speed by five percent");
                close(helper, player.getCurrentItemAttackStrengthDelay(), baseDelay / multiplier,
                        "The native charge time uses the modified attack speed");
            }

            sword.set(ExtremeGear.SOCKETS.get(), ExtremeGear.sockets(sword).with(1, firstCode));
            player.tick();
            close(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), baseSpeed * 1.3D,
                    "An extra attack-speed gem in a loaded component does not stack");
            player.resetAttackStrengthTicker();
            int rechargeTicks = (int) Math.ceil(baseDelay / 1.3D);
            for (int tick = 1; tick < rechargeTicks; tick++) player.tick();
            helper.assertTrue(player.getAttackStrengthScale(0F) < 1F, "The weapon still needs one more tick to recharge");
            player.tick();
            close(helper, player.getAttackStrengthScale(0F), 1D, "The weapon recharges at the shortened interval");

            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            player.setItemSlot(EquipmentSlot.OFFHAND, sword);
            player.setItemSlot(EquipmentSlot.CHEST, gemmed(Items.IRON_CHESTPLATE, GemType.ATTACK_SPEED, 6));
            player.tick();
            close(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), baseSpeed,
                    "An offhand weapon and an invalid armor socket cannot raise attack speed");

            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            player.tick();
            close(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), baseSpeed * 1.3D,
                    "Returning the weapon to the main hand restores the bonus");
            sword.remove(ExtremeGear.SOCKETS.get());
            player.tick();
            close(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), baseSpeed,
                    "Removing the gem from the still-equipped weapon clears the bonus");
            close(helper, player.getCurrentItemAttackStrengthDelay(), baseDelay,
                    "Removing the gem restores the original cooldown");

            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            player.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
            player.tick();
            double toolSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
            pickaxe.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, firstCode + 5));
            player.tick();
            close(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), toolSpeed,
                    "An invalid attack-speed gem component on a pickaxe has no effect");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void protectionGemsReachAllFourSlotSpecificAttributesForEveryRank(GameTestHelper helper) {
        for (int rank = 1; rank <= 6; rank++) for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            EquipmentSlot slot = ARMOR_SLOTS[index];
            ItemStack armor = gemmed(ARMOR_ITEMS[index], GemType.PROTECTION, rank);
            close(helper, modifier(armor, slot, Attributes.ARMOR, ExtremeGear.id("socket_armor_" + slot.getName())),
                    rank * .25D, "Every armor slot exposes its own rank-scaled protection modifier");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void speedGemsReachOnlyTheBootsMovementAttributeForEveryRank(GameTestHelper helper) {
        Player player = player(helper);
        try {
            double base = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            for (int rank = 1; rank <= 6; rank++) for (int index = 0; index < ARMOR_SLOTS.length; index++) {
                EquipmentSlot slot = ARMOR_SLOTS[index];
                clearArmor(player);
                player.setItemSlot(slot, gemmed(ARMOR_ITEMS[index], GemType.SPEED, rank));
                SourceEnchantmentCombat.refreshMovement(player);
                close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED),
                        base * (slot == EquipmentSlot.FEET ? 1D + rank * .05D : 1D),
                        "The actual movement attribute reads only the highest speed gem on worn boots");
                player.setItemSlot(slot, ItemStack.EMPTY);
                SourceEnchantmentCombat.refreshMovement(player);
                close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base,
                        "Removing the gem-bearing equipment immediately restores movement speed");
            }
            ItemStack boots = gemmed(Items.IRON_BOOTS, GemType.SPEED, 6);
            var speed = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(R196Enchantments.key("speed"));
            boots.enchant(speed, 5);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            SourceEnchantmentCombat.refreshMovement(player);
            close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.55D,
                    "Ordinary Speed V and gem VI add their two bonuses in the shared movement modifier");
            boots.enchant(speed, 6);
            SourceEnchantmentCombat.refreshMovement(player);
            close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.6D,
                    "An existing over-level Speed VI and gem VI retain their additive sixty-percent total");
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
            SourceEnchantmentCombat.refreshMovement(player);
            close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.6D * 1.4D,
                    "The existing Speed II potion multiplier follows the combined equipment bonus");
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
            SourceEnchantmentCombat.refreshMovement(player);
            close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.6D * 1.2D,
                    "Source Speed II and Slowness I retain their net potion factor with speed gems equipped");
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
            SourceEnchantmentCombat.refreshMovement(player);
            close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.6D * .8D,
                    "Removing the speed potion leaves the original slowness multiplier");
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            boots.remove(ExtremeGear.SOCKETS.get());
            SourceEnchantmentCombat.refreshMovement(player);
            close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.3D,
                    "Removing the gems preserves the existing armor-speed enchantment");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            SourceEnchantmentCombat.refreshMovement(player);
            close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base,
                    "Unequipping the boots removes the complete shared equipment bonus");
        } finally { player.removeAllEffects(); player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void healthGemsReachTheServerHealthPolicyFromEachArmorSlot(GameTestHelper helper) {
        var history = BloodMoonInvasionData.get(helper.getLevel().getServer().overworld());
        long previousDay = history.highestDay();
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "gem-health-effects"));
        try {
            history.restoreProgress(170);
            player.experienceLevel = 35;
            for (int rank = 1; rank <= 6; rank++) for (int index = 0; index < ARMOR_SLOTS.length; index++) {
                clearArmor(player);
                player.setItemSlot(ARMOR_SLOTS[index], gemmed(ARMOR_ITEMS[index], GemType.HEALTH, rank));
                player.setHealth(5F);
                PlayerHealthEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                close(helper, player.getMaxHealth(), 20D + rank, "Each armor part's highest health gem reaches the real server health attribute");
                close(helper, player.getHealth(), 5D, "Applying additional health capacity preserves current health");
            }
            equipAll(player, GemType.HEALTH, 6);
            PlayerHealthEvents.onPlayerTick(new PlayerTickEvent.Post(player));
            close(helper, player.getMaxHealth(), 44D, "Four rank-six health gems retain their separate twenty-four-point total");
        } finally { clearArmor(player); player.discard(); history.restoreProgress(previousDay); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void recoveryGemsFromEveryArmorSlotAccelerateActualFoodDataHealing(GameTestHelper helper) {
        boolean previous = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        helper.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(true, helper.getLevel().getServer());
        try {
            close(helper, healed(helper, List.of()), 0D, "Baseline nutrition does not complete one healing point within one thousand ticks");
            for (EquipmentSlot slot : ARMOR_SLOTS)
                close(helper, healed(helper, List.of(slot)), 1D, "Each worn rank-six recovery gem completes one actual healing point in the same interval");
            close(helper, healed(helper, List.of(ARMOR_SLOTS)), 3D,
                    "Four rank-six recovery gems combine to complete three actual healing points");
        } finally {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(previous, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "modern_ite_gem_effects", template = "gear_empty")
    public static void fireAndExplosionGemsModifyTheRealIncomingDamageChain(GameTestHelper helper) {
        Player player = player(helper);
        float[] received = {Float.NaN};
        Consumer<LivingIncomingDamageEvent> observe = event -> {
            if (event.getEntity() == player) received[0] = event.getAmount();
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, observe);
        try {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            for (GemType type : List.of(GemType.FIREPROOF, GemType.EXPLOSION_PROOF)) {
                DamageSource source = type == GemType.FIREPROOF ? player.damageSources().inFire() : player.damageSources().explosion(null, null);
                for (int rank = 1; rank <= 6; rank++) for (int index : new int[]{1, 2}) {
                    clearArmor(player);
                    player.setItemSlot(ARMOR_SLOTS[index], new ItemStack(ARMOR_ITEMS[index]));
                    float baseline = incomingDamage(helper, player, source, received);
                    player.setItemSlot(ARMOR_SLOTS[index], gemmed(ARMOR_ITEMS[index], type, rank));
                    float actual = incomingDamage(helper, player, source, received);
                    close(helper, actual, baseline * (1D - rank * .125D),
                            "Each eligible piece changes the real incoming damage event by its highest rank");
                }
                clearArmor(player);
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                float baseline = incomingDamage(helper, player, source, received);
                player.setItemSlot(EquipmentSlot.CHEST, gemmed(Items.IRON_CHESTPLATE, type, 6));
                player.setItemSlot(EquipmentSlot.LEGS, gemmed(Items.IRON_LEGGINGS, type, 6));
                float actual = incomingDamage(helper, player, source, received);
                close(helper, actual, baseline * .25D * .25D, "Two rank-six pieces compose their two actual reductions");
            }
        } finally { NeoForge.EVENT_BUS.unregister(observe); player.discard(); }
        helper.succeed();
    }

    private static float incomingDamage(GameTestHelper helper, Player player, DamageSource source, float[] received) {
        player.invulnerableTime = 0;
        player.setHealth(100F);
        received[0] = Float.NaN;
        helper.assertTrue(player.hurt(source, 16F), "The actual native damage transaction succeeds");
        helper.assertTrue(Float.isFinite(received[0]) && received[0] > 0F,
                "The actual incoming-damage observer records a positive finite amount");
        return received[0];
    }

    private static float healed(GameTestHelper helper, List<EquipmentSlot> slots) {
        Player player = player(helper);
        try {
            player.experienceLevel = 35;
            for (int index = 0; index < ARMOR_SLOTS.length; index++)
                if (slots.contains(ARMOR_SLOTS[index])) player.setItemSlot(ARMOR_SLOTS[index], gemmed(ARMOR_ITEMS[index], GemType.RECOVER, 6));
            DualFoodData food = (DualFoodData) player.getFoodData();
            food.mite$bindPlayer(player);
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20F);
            food.mite$setNutrition(20);
            NutritionState.set(player, NutritionState.INITIAL);
            player.setHealth(5F);
            for (int tick = 0; tick < 1000; tick++) player.getFoodData().tick(player);
            return player.getHealth() - 5F;
        } finally { player.discard(); }
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(new BlockPos(2, 12, 2)).getCenter());
        player.setNoGravity(true);
        return player;
    }

    private static ItemStack gemmed(Item item, GemType type, int rank) {
        ItemStack stack = new ItemStack(item);
        int lower = Math.max(1, rank - 1);
        stack.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, type.ordinal() * 6 + rank)
                .with(1, type.ordinal() * 6 + lower));
        return stack;
    }

    private static void equipAll(Player player, GemType type, int rank) {
        for (int index = 0; index < ARMOR_SLOTS.length; index++)
            player.setItemSlot(ARMOR_SLOTS[index], gemmed(ARMOR_ITEMS[index], type, rank));
    }

    private static void clearArmor(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
    }

    private static double modifier(ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute, ResourceLocation id) {
        double[] amount = {0D};
        stack.forEachModifier(slot, (current, modifier) -> {
            if (current.equals(attribute) && modifier.id().equals(id)) amount[0] += modifier.amount();
        });
        return amount[0];
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001D, message + " expected " + expected + " got " + actual);
    }
}
