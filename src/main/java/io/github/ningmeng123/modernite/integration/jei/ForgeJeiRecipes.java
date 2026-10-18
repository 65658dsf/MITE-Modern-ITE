package io.github.ningmeng123.modernite.integration.jei;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.ForgeRecipes;
import io.github.ningmeng123.modernite.gear.GearMath;
import io.github.ningmeng123.modernite.gear.GearStationEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Registry-backed display model, also usable on a dedicated server without JEI. @author NingMeng123 */
public final class ForgeJeiRecipes {
    public record Profile(int tier, ResourceLocation id) {}
    public record ToolStats(int minimumTicks, int maximumTicks, int minimumFailure, int maximumFailure) {}
    private ForgeJeiRecipes() {}

    /** Safe during plugin discovery, before deferred blocks have been bound. */
    public static List<Integer> categoryTiers() {
        return List.of("iron", "mithril", "adamantium", "vibranium").stream()
                .map(GearMath::tableTier).distinct().sorted().toList();
    }

    public static Map<Integer, Profile> profiles() {
        Map<Integer, Profile> result = new LinkedHashMap<>();
        ExtremeGear.FORGES.stream().sorted(Comparator.comparingInt(forge -> forge.get().tier)).forEach(forge ->
                result.put(forge.get().tier, new Profile(forge.get().tier, forge.getId())));
        return Collections.unmodifiableMap(result);
    }

    /** All entries here can perform a page requiring this minimum tier. */
    public static List<ItemStack> catalysts(int minimumTier) {
        if (!profiles().containsKey(minimumTier)) return List.of();
        return profiles().values().stream().filter(profile -> profile.tier() >= minimumTier)
                .map(profile -> new ItemStack(BuiltInRegistries.ITEM.get(profile.id()))).toList();
    }

    public static boolean supports(ItemStack input) {
        return ExtremeGear.socketable(input) && GearMath.maximumGrade(GearProgression.material(input)) > 0;
    }

    public static int minimumTier(ItemStack input) { return GearMath.tableTier(GearProgression.material(input)); }

    /** Mirrors the equipment insertion and strengthening gates, excluding transient inventory/tool availability. */
    public static boolean canForge(int tableTier, ItemStack input) {
        int grade = ExtremeGear.grade(input);
        return supports(input) && grade >= 0 && grade < GearMath.maximumGrade(GearProgression.material(input))
                && tableTier >= minimumTier(input) && !ForgeRecipes.costs(grade).isEmpty();
    }

    /** Includes precisely the damageable axe/war-hammer families accepted by the station. */
    public static List<ItemStack> tools(ToolFamily family) {
        if (family != ToolFamily.AXE && family != ToolFamily.WAR_HAMMER) return List.of();
        return BuiltInRegistries.ITEM.stream().sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .map(item -> item.getDefaultInstance()).filter(stack -> !stack.isEmpty() && stack.isDamageableItem()
                        && R196EnchantmentCatalog.family(stack) == family).toList();
    }

    public static List<ItemStack> materials(int inputGrade, boolean universal) {
        List<ItemStack> result = new ArrayList<>();
        for (var cost : ForgeRecipes.costs(inputGrade)) {
            var item = universal && cost.id().endsWith("_enhance_stone") ? ExtremeGear.STONES.get(3).get()
                    : BuiltInRegistries.ITEM.get(ResourceLocation.parse(cost.id()));
            if (item == Items.AIR) throw new IllegalStateException("Unresolved forging material: " + cost.id());
            result.add(new ItemStack(item, cost.count()));
        }
        return List.copyOf(result);
    }

    public static List<ForgeJeiRecipe> build() {
        if (tools(ToolFamily.AXE).isEmpty() || tools(ToolFamily.WAR_HAMMER).isEmpty()) return List.of();
        Map<Integer, ToolStats> normal = new LinkedHashMap<>(), universal = new LinkedHashMap<>();
        List<ForgeJeiRecipe> result = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM.stream()
                .sorted(Comparator.comparing(value -> BuiltInRegistries.ITEM.getKey(value).toString())).toList()) {
            ItemStack base = item.getDefaultInstance();
            if (!supports(base)) continue;
            int minimumTier = minimumTier(base);
            if (!profiles().containsKey(minimumTier)) continue;
            for (int grade = 0; grade < GearMath.maximumGrade(GearProgression.material(base)); grade++) {
                ItemStack input = base.copy();
                if (grade == 0) input.remove(ExtremeGear.FORGE_GRADE.get());
                else input.set(ExtremeGear.FORGE_GRADE.get(), grade);
                if (!canForge(minimumTier, input)) continue;
                ItemStack output = input.copy();
                output.set(ExtremeGear.FORGE_GRADE.get(), grade + 1);
                var reward = ForgeRecipes.reward(grade);
                if (reward != null && reward.ordinal() > EquipmentDurability.quality(output).ordinal())
                    EquipmentDurability.setQuality(output, reward);
                for (boolean useUniversal : new boolean[]{false, true}) {
                    var cache = useUniversal ? universal : normal;
                    ToolStats toolStats = cache.computeIfAbsent(grade, value -> stats(value, useUniversal));
                    result.add(new ForgeJeiRecipe(minimumTier, BuiltInRegistries.ITEM.getKey(item), input, output,
                            materials(grade, useUniversal), useUniversal, toolStats));
                }
            }
        }
        return List.copyOf(result);
    }

    /** Uses real legal tool combinations and the station's own failure calculation, including universal stones. */
    public static ToolStats stats(int inputGrade, boolean universal) {
        var axes = representativeTools(ToolFamily.AXE);
        var hammers = representativeTools(ToolFamily.WAR_HAMMER);
        if (axes.isEmpty() || hammers.isEmpty() || ForgeRecipes.costs(inputGrade).isEmpty())
            throw new IllegalArgumentException("No usable forging tools or materials for grade " + inputGrade);
        var forge = ExtremeGear.FORGES.getLast().get();
        GearStationEntity station = new GearStationEntity(BlockPos.ZERO, forge.defaultBlockState());
        ItemStack target = new ItemStack(Items.IRON_SWORD);
        target.set(ExtremeGear.FORGE_GRADE.get(), inputGrade);
        station.setItem(5, target);
        var payment = materials(inputGrade, universal);
        for (int i = 0; i < payment.size(); i++) station.setItem(i, payment.get(i).copy());
        int minimumTicks = Integer.MAX_VALUE, maximumTicks = 0;
        int minimumFailure = 100, maximumFailure = 0;
        for (ItemStack axe : axes) for (ItemStack hammer : hammers) {
            station.setItem(6, axe.copy()); station.setItem(7, hammer.copy());
            int ticks = GearMath.forgeTicks(inputGrade, GearMath.materialTier(GearProgression.material(axe)),
                    GearMath.materialTier(GearProgression.material(hammer)));
            int failure = station.data.get(4);
            minimumTicks = Math.min(minimumTicks, ticks); maximumTicks = Math.max(maximumTicks, ticks);
            minimumFailure = Math.min(minimumFailure, failure); maximumFailure = Math.max(maximumFailure, failure);
        }
        return new ToolStats(minimumTicks, maximumTicks, minimumFailure, maximumFailure);
    }

    private static List<ItemStack> representativeTools(ToolFamily family) {
        Map<Integer, ItemStack> result = new LinkedHashMap<>();
        for (ItemStack stack : tools(family))
            result.putIfAbsent(GearMath.materialTier(GearProgression.material(stack)), stack);
        return List.copyOf(result.values());
    }
}
