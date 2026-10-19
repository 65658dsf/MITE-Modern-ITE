package io.github.ningmeng123.modernite.world;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Read immutable settings on generation workers; existing chunks are retained. @author NingMeng123 */
public final class ConfigurableNetherOre extends OreFeature {
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,ModernIte.MOD_ID);
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS=DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE,ModernIte.MOD_ID);
    public static final DeferredHolder<Feature<?>,ConfigurableNetherOre> FEATURE=FEATURES.register("nether_adamantium_ore",ConfigurableNetherOre::new);
    public static final DeferredHolder<PlacementModifierType<?>,PlacementModifierType<Count>> COUNT=
            PLACEMENTS.register("nether_adamantium_count",()->()->Count.CODEC);

    public ConfigurableNetherOre() { super(OreConfiguration.CODEC); }
    public static void register(IEventBus bus) { FEATURES.register(bus);PLACEMENTS.register(bus); }
    public static OreConfiguration configured(OreConfiguration original) {
        return new OreConfiguration(original.targetStates,IteConfig.integer("nether_adamantium_max_count_per_vein"),original.discardChanceOnAirExposure);
    }
    @Override public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        return super.place(new FeaturePlaceContext<>(context.topFeature(),context.level(),context.chunkGenerator(),
                context.random(),context.origin(),configured(context.config())));
    }
    public static final class Count extends RepeatingPlacement {
        public static final MapCodec<Count> CODEC=MapCodec.unit(Count::new);
        @Override protected int count(RandomSource random,BlockPos origin) {
            return IteConfig.integer("nether_adamantium_max_count_per_chunk");
        }
        @Override public PlacementModifierType<?> type() { return COUNT.get(); }
    }
}
