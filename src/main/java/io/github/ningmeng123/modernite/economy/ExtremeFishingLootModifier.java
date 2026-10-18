package io.github.ningmeng123.modernite.economy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Runs on the actual loot list, before Minecraft creates the caught item entities. @author NingMeng123 */
public final class ExtremeFishingLootModifier extends LootModifier {
    public static final MapCodec<ExtremeFishingLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, ExtremeFishingLootModifier::new));
    public ExtremeFishingLootModifier(LootItemCondition[] conditions) { super(conditions); }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof FishingHook)
                || !BuiltInLootTables.FISHING.location().equals(context.getQueriedLootTableId())) return loot;
        for (int slot = 0; slot < loot.size(); slot++) {
            ItemStack old = loot.get(slot);
            String id = BuiltInRegistries.ITEM.getKey(old.getItem()).toString();
            // Modern treasure, junk and uncommon fish retain their existing pipeline.
            if (!old.is(Items.COD) && !old.is(Items.SALMON) && !id.equals("mite:raw_large_fish")) continue;
            String selected = EconomyRules.catchAt(context.getRandom().nextInt(EconomyRules.TOTAL_CATCH_WEIGHT));
            if (selected != null) loot.set(slot, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(selected))));
        }
        return loot;
    }
}
