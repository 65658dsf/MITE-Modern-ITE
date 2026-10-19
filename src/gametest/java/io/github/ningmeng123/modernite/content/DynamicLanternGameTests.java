package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Offhand lighting, material lifetime and native potion compatibility. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class DynamicLanternGameTests {
    private static final String[] MATERIALS = {
            "copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"
    };

    private DynamicLanternGameTests() {}

    private static ItemStack lantern(String material) {
        return new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "dynamic_core_" + material)));
    }

    private static void tick(Player player, int tick) {
        player.tickCount = tick;
        ExtremeLifeEvents.tickLantern(player);
    }

    @GameTest(template = "gear_empty")
    public static void everyMaterialHasTheSameLightAndIncreasingLifetime(GameTestHelper h) {
        int[] durability = {800, 1200, 1600, 2400, 3200, 4800, 8000, 12800};
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        int previous = 0;
        for (int i = 0; i < MATERIALS.length; i++) {
            ItemStack stack = lantern(MATERIALS[i]);
            h.assertTrue(stack.getItem() instanceof DynamicCoreItem,
                    "Every material resolves to an actual lantern: " + MATERIALS[i]);
            h.assertValueEqual(stack.getMaxDamage(), durability[i],
                    "The material has its intended lifetime: " + MATERIALS[i]);
            h.assertTrue(stack.getMaxDamage() > previous,
                    "Each higher material lasts longer than the preceding tier: " + MATERIALS[i]);
            previous = stack.getMaxDamage();
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
            h.assertValueEqual(ExtremeLifeEvents.coreLevel(player), 5,
                    "All lantern materials provide the same equipped illumination: " + MATERIALS[i]);
        }
        player.discard();
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void durabilityDisplayCoversFullPartialAndExhaustedLanterns(GameTestHelper h) {
        for (String material : MATERIALS) {
            ItemStack stack = lantern(material);
            DynamicCoreItem item = (DynamicCoreItem) stack.getItem();
            int maximum = stack.getMaxDamage();
            for (int damage : new int[]{0, maximum / 2, maximum}) {
                stack.setDamageValue(damage);
                h.assertTrue(item.isBarVisible(stack),
                        "Lantern durability remains visible at every fuel level: " + material);
                h.assertValueEqual(item.getBarWidth(stack), Math.round(13F * (maximum - damage) / maximum),
                        "The visible bar represents the remaining durability: " + material);
                List<Component> tooltip = new ArrayList<>();
                item.appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
                h.assertTrue(tooltip.stream().anyMatch(line ->
                                line.getContents() instanceof TranslatableContents translated
                                        && translated.getKey().equals("tooltip.modern_ite.lantern.remaining")
                                        && Arrays.equals(translated.getArgs(), new Object[]{maximum - damage, maximum})),
                        "The tooltip reports the remaining and maximum durability: " + material);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void backpackAndMainHandLanternsNeverLightOrLoseDurability(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        for (String material : MATERIALS) {
            player.getInventory().clearContent();
            ItemStack backpack = lantern(material);
            player.getInventory().setItem(9, backpack);
            for (int tick = 1; tick <= 20; tick++) {
                tick(player, tick);
                h.assertValueEqual(ExtremeLifeEvents.coreLevel(player), 0,
                        "A backpack lantern cannot activate: " + material);
            }
            h.assertValueEqual(backpack.getDamageValue(), 0,
                    "A stored lantern does not consume its lifetime: " + material);
            ItemStack mainHand = lantern(material);
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            for (int tick = 21; tick <= 40; tick++) {
                tick(player, tick);
                h.assertValueEqual(ExtremeLifeEvents.coreLevel(player), 0,
                        "A main-hand lantern cannot activate: " + material);
            }
            h.assertTrue(backpack.getDamageValue() == 0 && mainHand.getDamageValue() == 0,
                    "Inactive lamps in both locations retain their durability: " + material);
            h.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION),
                    "Carrying a lantern cannot leave a night-vision effect: " + material);
        }
        player.discard();
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void onlyTheEquippedOffhandLanternConsumesItsLifetime(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack offhand = lantern("copper"), mainHand = lantern("adamantium"), backpack = lantern("vibranium");
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        player.getInventory().setItem(9, backpack);
        for (int tick = 1; tick <= 20; tick++) tick(player, tick);
        h.assertValueEqual(offhand.getDamageValue(), 4, "Equipped lighting spends two durability every ten ticks");
        h.assertTrue(mainHand.getDamageValue() == 0 && backpack.getDamageValue() == 0,
                "Higher-quality inactive lanterns cannot be selected as fuel");
        h.assertValueEqual(ExtremeLifeEvents.coreLevel(player), 5, "The lower-quality offhand lantern supplies the light");
        h.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "Lantern illumination does not inject a potion effect");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        h.assertValueEqual(ExtremeLifeEvents.coreLevel(player), 0, "Removing the offhand lantern immediately stops illumination");
        tick(player, 30);
        h.assertValueEqual(offhand.getDamageValue(), 4, "Removing the lantern immediately stops its wear");
        h.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "Unequipping a lantern leaves no residual night vision");
        player.discard();
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void theFinalDurabilityPointStopsLightWithoutDestroyingTheLantern(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        for (String material : MATERIALS) for (int remaining : new int[]{1, 2}) {
            ItemStack stack = lantern(material);
            stack.setDamageValue(stack.getMaxDamage() - remaining);
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
            h.assertValueEqual(ExtremeLifeEvents.coreLevel(player), 5,
                    "The final durability points still provide illumination: " + material);
            tick(player, 10);
            h.assertValueEqual(stack.getDamageValue(), stack.getMaxDamage(),
                    "The last wear payment reaches the durability limit without overflowing: " + material);
            h.assertTrue(!stack.isEmpty() && player.getOffhandItem() == stack,
                    "An exhausted lantern remains available in the offhand: " + material);
            h.assertValueEqual(ExtremeLifeEvents.coreLevel(player), 0,
                    "Reaching the durability limit immediately turns off illumination: " + material);
            tick(player, 20);
            h.assertValueEqual(stack.getDamageValue(), stack.getMaxDamage(),
                    "An exhausted lantern cannot consume additional durability: " + material);
        }
        player.discard();
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void equippingAndRemovingLanternsPreservesRealNightVisionPotions(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        for (int duration : new int[]{100, 1200}) {
            player.removeAllEffects();
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 1, false, true, true));
            MobEffectInstance potion = player.getEffect(MobEffects.NIGHT_VISION);
            player.setItemInHand(InteractionHand.OFF_HAND, lantern("mithril"));
            tick(player, 10);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            h.assertValueEqual(ExtremeLifeEvents.coreLevel(player), 0, "Unequipping immediately removes lantern lighting");
            tick(player, 20);
            h.assertTrue(player.getEffect(MobEffects.NIGHT_VISION) == potion,
                    "Equipping and removing a lantern retains the original potion instance");
            h.assertTrue(potion.getDuration() == duration && potion.getAmplifier() == 1
                            && !potion.isAmbient() && potion.isVisible() && potion.showIcon(),
                    "Lantern use preserves the potion duration, amplifier and presentation flags");
        }
        player.discard();
        h.succeed();
    }
}
