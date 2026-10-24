package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.combat.WeaponEncumbrance;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearModifier;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered external clubs share base balance without double-scaling forge, gems or old attribute components. @author NingMeng123 */
@GameTestHolder("modern_ite_weapon_balance")
@PrefixGameTestTemplate(false)
public final class WeaponBalanceGameTests {
    @GameTest(template = "gear_empty")
    public static void allEightRegisteredClubsUseOneSharedDamageReductionAndPlayerOnlyWeight(GameTestHelper h) {
        String[] metals = {"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"};
        double[] expected = {5.25, 5.6, 4.8, 6.4, 6.4, 7.2, 8.0, 10.4};
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        double normalMovement = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        for (int index = 0; index < metals.length; index++) {
            ItemStack club = new ItemStack(ExtremeContent.ITEM_ENTRIES.get("club_"+metals[index]).get());
            close(h, 1D+addition(club, Attributes.ATTACK_DAMAGE), expected[index], "Registered "+metals[index]+" club uses the shared balanced total base damage");
            close(h, 4D+addition(club, Attributes.ATTACK_SPEED), 1.1D, "Every metal club has the deliberate1.1/s heavy attack cadence");
            close(h, WeaponEncumbrance.penalty(club), .08D, "ITE external tool registration participates in the centralized8% player weight");
            player.setItemInHand(InteractionHand.MAIN_HAND, club); WeaponEncumbrance.refresh(player);
            close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), normalMovement*.92D, "Changing club materials cannot stack player weight");
            club.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> h.assertFalse(attribute.equals(Attributes.MOVEMENT_SPEED),
                    "The club carries no item-level movement debuff that would also affect monsters"));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); WeaponEncumbrance.refresh(player);
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), normalMovement, "Unequipping an ITE club leaves no movement residue");
        player.discard(); h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void oldClubAttributesNormalizeBeforeForgeAndKeepGemsGrowthQualityNamesAndOrdinaryEnchantments(GameTestHelper h) {
        assertSavedWeapon(h, new ItemStack(ExtremeContent.ITEM_ENTRIES.get("club_vibranium").get()),
                10.4D, 1.1D, .08D, "Saved ITE club");
        if (SimplySwordsCompat.enabled()) {
            ItemStack hammer = new ItemStack(SimplySwordsCompat.weapon("vibranium", "greathammer"));
            h.assertFalse(hammer.isEmpty(), "The combined-mod fixture uses the real registered Simply Swords vibranium greathammer");
            close(h, 1D+addition(hammer, Attributes.ATTACK_DAMAGE), 12.32D, "The plain greathammer has the independently balanced total base damage");
            close(h, SimplySwordsCompat.damageBonus(hammer), 11.32D, "The greathammer canonical base bonus excludes the player's inherent damage");
            assertSavedWeapon(h, hammer, 12.32D, .7D, .25D, "Saved Simply Swords greathammer");
        }
        h.succeed();
    }

    private static void assertSavedWeapon(GameTestHelper h, ItemStack legacy, double balancedTotal,
            double attackSpeed, double movementPenalty, String savedName) {
        var customDamage = ResourceLocation.fromNamespaceAndPath("other_mod", "kept_damage");
        legacy.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 12D, AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4D, AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(customDamage, 2D, AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND).build());
        legacy.set(ExtremeGear.FORGE_GRADE.get(), 10);
        legacy.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, GemType.DAMAGE.ordinal()*6+6));
        legacy.set(ExtremeGear.GRINDINGS.get(), 2);
        legacy.set(DataComponents.CUSTOM_NAME, Component.literal(savedName));
        EquipmentDurability.setQuality(legacy, Quality.LEGENDARY);
        var progress = new GearProgress(UUID.randomUUID(), 12, 3210L, Map.of(GearModifier.TOOL_DAMAGE.id(), 2), 1);
        legacy.set(GearProgression.PROGRESS.get(), progress);
        var unbreaking = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
        legacy.enchant(unbreaking, 3);
        ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), legacy.save(h.getLevel().registryAccess())).orElseThrow();
        // HIGHEST base normalization must run before HIGH legacy growth and LOWEST ITE forging.
        double forgeBase = balancedTotal + 2D + 2D; // Balanced total + explicit external modifier + retained historical growth.
        double expected = forgeBase*(1D+GearMath.enhanceFactor(10))+6D;
        for (int read = 0; read < 8; read++) close(h, 1D+addition(restored, Attributes.ATTACK_DAMAGE), expected,
                savedName+": old raw base13 is replaced before forging; each gem/growth/forge effect contributes exactly once");
        close(h, attributeValue(restored, Attributes.ATTACK_SPEED, 4D), attackSpeed,
                savedName+": saved speed components normalize before the real two-handed multiplier");
        close(h, WeaponEncumbrance.penalty(restored), movementPenalty,
                savedName+": saved components retain exactly one centralized player movement penalty");
        ItemStack reloaded = ItemStack.parse(h.getLevel().registryAccess(), restored.save(h.getLevel().registryAccess())).orElseThrow();
        close(h, 1D+addition(reloaded, Attributes.ATTACK_DAMAGE), expected,
                savedName+": saving after repeated attribute reads does not bake forge, gem or growth effects into the old base");
        h.assertValueEqual(ExtremeGear.rank(restored, GemType.DAMAGE), 6, "Damage gem rank is preserved");
        h.assertValueEqual(ExtremeGear.grade(restored), 10, "Forge grade is preserved");
        h.assertValueEqual(restored.getOrDefault(ExtremeGear.GRINDINGS.get(), 0), 2, "Historical grinding data is preserved");
        h.assertValueEqual(EquipmentDurability.quality(restored), Quality.LEGENDARY, "Quality is preserved independently of damage");
        h.assertValueEqual(GearProgression.state(restored), progress, "Saved growth identity, experience and legacy affix ranks are not rewritten");
        h.assertValueEqual(restored.getEnchantmentLevel(unbreaking), 3, "Ordinary enchantments are preserved");
        h.assertValueEqual(restored.getHoverName().getString(), savedName, "Saved names are preserved");
    }

    @GameTest(template = "gear_empty")
    public static void theIteCrossbowRemainsAnUnmodifiedNativeRangedItemWithoutMeleeWeight(GameTestHelper h) {
        ItemStack crossbow = new ItemStack(ExtremeContent.CROSSBOW.get());
        h.assertTrue(crossbow.getItem() instanceof net.minecraft.world.item.CrossbowItem, "ITE uses the native CrossbowItem projectile path");
        close(h, addition(crossbow, Attributes.ATTACK_DAMAGE), 0D, "The addon crossbow has no hidden melee damage boost");
        close(h, WeaponEncumbrance.penalty(crossbow), 0D, "Native crossbow charging retains its own movement behavior");
        h.succeed();
    }

    private static double addition(ItemStack stack, net.minecraft.core.Holder<Attribute> attribute) {
        double[] result = {0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (candidate, modifier) -> {
            if (candidate.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) result[0] += modifier.amount();
        });
        return result[0];
    }
    private static double attributeValue(ItemStack stack, net.minecraft.core.Holder<Attribute> attribute, double base) {
        AttributeInstance instance = new AttributeInstance(attribute, ignored -> {});
        instance.setBaseValue(base);
        stack.forEachModifier(EquipmentSlot.MAINHAND, (candidate, modifier) -> {
            if (candidate.equals(attribute)) instance.addTransientModifier(modifier);
        });
        return instance.getValue();
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual-expected) < .00001D, message+": actual="+actual+", expected="+expected);
    }
}
