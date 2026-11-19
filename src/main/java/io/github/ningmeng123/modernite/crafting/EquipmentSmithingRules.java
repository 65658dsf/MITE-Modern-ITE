package io.github.ningmeng123.modernite.crafting;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.crafting.VibraniumCrafting;
import io.github.ningmeng123.mite.furnace.EquipmentRecycling;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Extreme material progression shares the original manufacturing metal costs. @author NingMeng123 */
public final class EquipmentSmithingRules {
    private static final Map<ResourceLocation, Integer> VIBRANIUM_INGOTS = vibraniumIngots();

    private EquipmentSmithingRules() {}

    public static boolean requiresSmithing(ItemStack stack) {
        if (MiteTools.definition(stack) == null && SimplySwordsCompat.definition(stack) == null
                && MiteArmorContent.definition(stack.getItem()) == null && MiteShields.definition(stack) == null) return false;
        String material = EquipmentRecycling.material(stack);
        return "ancient_metal".equals(material) || "mithril".equals(material)
                || "adamantium".equals(material) || "vibranium".equals(material);
    }

    public static int ingots(ItemStack stack, int original) {
        return VIBRANIUM_INGOTS.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), original);
    }

    private static Map<ResourceLocation, Integer> vibraniumIngots() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                VibraniumCrafting.class.getResourceAsStream("/mite/vibranium-upgrades.json")), StandardCharsets.UTF_8)) {
            var items = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("items");
            Map<ResourceLocation, Integer> costs = new HashMap<>();
            for (var entry : items.entrySet()) {
                int ingots = entry.getValue().getAsJsonObject().get("vibranium_ingots").getAsInt();
                if (ingots < 1) throw new IllegalArgumentException("Invalid vibranium upgrade cost: " + entry.getKey());
                costs.put(ResourceLocation.parse(entry.getKey()), ingots);
            }
            return Map.copyOf(costs);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load equipment smithing costs", exception);
        }
    }
}
