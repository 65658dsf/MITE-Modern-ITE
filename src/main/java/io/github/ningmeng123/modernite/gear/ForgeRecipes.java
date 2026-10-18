package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** ITE 2.0.28 material costs, levels and quality rewards. @author NingMeng123 */
public final class ForgeRecipes {
    public record Cost(String id, int count) {}
    private ForgeRecipes() {}
    public static List<Cost> costs(int grade) {
        return switch (grade) {
            case 0 -> List.of(c("minecraft:gold_ingot", 1), c("minecraft:iron_ingot", 1), stone(0, 1));
            case 1 -> List.of(c("minecraft:gold_ingot", 1), c("minecraft:iron_ingot", 2), stone(0, 1));
            case 2 -> List.of(c("minecraft:gold_ingot", 2), c("minecraft:iron_ingot", 2), stone(0, 1));
            case 3 -> List.of(c("minecraft:gold_ingot", 2), c("minecraft:iron_ingot", 2), stone(0, 2));
            case 4 -> List.of(c("minecraft:gold_ingot", 1), c("mite:mithril_ingot", 1), c("minecraft:redstone", 1), stone(1, 1));
            case 5 -> List.of(c("minecraft:gold_ingot", 2), c("mite:mithril_ingot", 1), c("minecraft:redstone", 2), stone(1, 1));
            case 6 -> List.of(c("minecraft:gold_ingot", 2), c("mite:mithril_ingot", 2), c("minecraft:redstone_block", 1), stone(1, 1));
            case 7 -> List.of(c("minecraft:ghast_tear", 1), c("mite:adamantium_ingot", 1), c("mite:mithril_ingot", 2), stone(2, 1));
            case 8 -> List.of(c("minecraft:ghast_tear", 2), c("mite:adamantium_ingot", 1), c("mite:mithril_ingot", 2), stone(2, 1));
            case 9 -> List.of(c("minecraft:ghast_tear", 2), c("mite:adamantium_ingot", 2), c("mite:mithril_ingot", 2), stone(2, 1));
            default -> List.of();
        };
    }
    private static Cost c(String id, int count) { return new Cost(id, count); }
    private static Cost stone(int tier, int count) { return c("modern_ite:" + List.of("iron", "mithril", "adamantium").get(tier) + "_enhance_stone", count); }
    private record Payment(int[] remaining, boolean universal) {}
    public static boolean has(GearStationEntity station) { return allocation(station) != null; }
    public static boolean usesUniversal(GearStationEntity station) {
        Payment payment = allocation(station); return payment != null && payment.universal();
    }
    public static boolean consume(GearStationEntity station, boolean commit) {
        Payment payment = allocation(station);
        if (payment == null) return false;
        if (commit) for (int slot = 0; slot < 5; slot++) station.getItem(slot).setCount(payment.remaining()[slot]);
        return true;
    }
    private static Payment allocation(GearStationEntity station) {
        List<Cost> needs = costs(ExtremeGear.grade(station.getItem(5)));
        if (needs.isEmpty()) return null;
        int[] remaining = new int[5];
        boolean universalUsed = false;
        for (int i = 0; i < 5; i++) remaining[i] = station.getItem(i).getCount();
        for (Cost cost : needs) {
            int count = cost.count();
            for (int slot = 0; slot < 5 && count > 0; slot++) {
                ItemStack stack = station.getItem(slot);
                boolean matches = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(cost.id());
                if (cost.id().endsWith("_enhance_stone") && stack.is(ExtremeGear.STONES.get(3).get())) matches = true;
                if (!matches) continue;
                int used = Math.min(count, remaining[slot]); remaining[slot] -= used; count -= used;
                if (used > 0 && stack.is(ExtremeGear.STONES.get(3).get())) universalUsed = true;
            }
            if (count > 0) return null;
        }
        return new Payment(remaining, universalUsed);
    }
    public static Quality reward(int grade) {
        return switch (grade) {
            case 1, 2 -> Quality.FINE; case 3, 4 -> Quality.EXCELLENT; case 5, 6 -> Quality.SUPERB;
            case 7, 8 -> Quality.MASTERWORK; case 9 -> Quality.LEGENDARY; default -> null;
        };
    }
}
