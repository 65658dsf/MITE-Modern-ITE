package io.github.ningmeng123.modernite.crafting;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Whole-batch ITE/Extreme manufacturing rules shared by players, automation and JEI. @author NingMeng123 */
public final class SourceCraftingRecipes {
    private static final Map<ResourceLocation, TimedCraftingRecipes.Rule> RULES = load();

    private SourceCraftingRecipes() {}

    public static TimedCraftingRecipes.Rule get(ResourceLocation recipe) {
        return RULES.get(recipe);
    }

    private static Map<ResourceLocation, TimedCraftingRecipes.Rule> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                SourceCraftingRecipes.class.getResourceAsStream("/modern_ite/timed-crafting.json")), StandardCharsets.UTF_8)) {
            var recipes = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("recipes");
            Map<ResourceLocation, TimedCraftingRecipes.Rule> rules = new HashMap<>();
            for (var entry : recipes.entrySet()) {
                var id = ResourceLocation.parse(entry.getKey());
                var value = entry.getValue().getAsJsonObject();
                float difficulty = value.get("difficulty").getAsFloat();
                if (!(id.getNamespace().equals("modern_ite") || id.getNamespace().equals("minecraft"))
                        || !Float.isFinite(difficulty) || difficulty < 0F)
                    throw new IllegalArgumentException("Invalid extension manufacturing rule: " + id);
                String bench = value.get("required_bench").isJsonNull() ? null : value.get("required_bench").getAsString();
                var rule = new TimedCraftingRecipes.Rule(difficulty, bench, value.get("source_order").getAsInt());
                if (bench != null && rule.requiredCapacity() <= 0F)
                    throw new IllegalArgumentException("Unknown manufacturing workbench: " + bench + " for " + id);
                rules.put(id, rule);
            }
            return Map.copyOf(rules);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load ITE/Extreme manufacturing rules", exception);
        }
    }
}
