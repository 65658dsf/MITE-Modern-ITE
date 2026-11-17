package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.item.R196CarrotOnAStickItem;
import io.github.ningmeng123.mite.item.R196FishingRodItem;
import io.github.ningmeng123.mite.phantom.PhantomBladeItem;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Netherite armor trim protects dropped armor, including equipment from existing saves. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class NetheriteTrimProtection {
    private NetheriteTrimProtection() {}

    public static boolean trimmedArmor(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem) || stack.getItem() instanceof AnimalArmorItem) return false;
        var trim = stack.get(DataComponents.TRIM);
        return trim != null && trim.material().is(TrimMaterials.NETHERITE);
    }

    public static boolean removesInnateProtection(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var armor = MiteArmorContent.definition(stack.getItem());
        String material = switch (stack.getItem()) {
            case PhantomBladeItem blade -> blade.material();
            case R196FishingRodItem rod -> rod.material().id;
            case R196CarrotOnAStickItem rod -> rod.material().id;
            default -> armor != null ? armor.material() : GearProgression.material(stack);
        };
        return "adamantium".equals(material) || "vibranium".equals(material);
    }

    public static void defaults(ModifyDefaultComponentsEvent event) {
        event.modifyMatching(item -> removesInnateProtection(new ItemStack(item)),
                components -> components.remove(DataComponents.FIRE_RESISTANT));
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (trimmedArmor(event.getItemStack()))
            event.getToolTip().add(Component.translatable("tooltip.modern_ite.netherite_trim_fireproof")
                    .withStyle(ChatFormatting.GOLD));
    }
}
