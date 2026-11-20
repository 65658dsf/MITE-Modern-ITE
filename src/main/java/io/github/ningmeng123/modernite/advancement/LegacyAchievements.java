package io.github.ningmeng123.modernite.advancement;

import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Player actions shared by the ITE and Extreme LH achievement branches. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class LegacyAchievements {
    private LegacyAchievements() {}

    public static void award(ServerPlayer player, String path) {
        var id = ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "legacy/" + path);
        var advancement = player.server.getAdvancements().get(id);
        if (advancement != null) player.getAdvancements().award(advancement, "earned");
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack result = event.getCrafting();
        if (result.is(ExtremeContent.RESURRECTION.get().asItem())) award(player, "resurrection_array");
        else if (result.is(ExtremeGear.GEM_SETTING.get().asItem())) award(player, "gem_setting");
        else if (result.is(ExtremeContent.VIBRANIUM_FURNACE.get().asItem())) award(player, "vibranium_furnace");
        else checkVibranium(player, result);
    }

    @SubscribeEvent
    public static void smelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) checkVibranium(player, event.getSmelting());
    }

    private static void checkVibranium(ServerPlayer player, ItemStack result) {
        if (result.is(MiteMaterials.VIBRANIUM_INGOT.get())) award(player, "vibranium_ingot");
        else if (result.is(MiteFunctionalBlocks.ANVILS.get("vibranium").get().asItem())) award(player, "vibranium_anvil");
    }
}
