package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Independent hostile registration, sharing the extension's natural spawn budget. @author NingMeng123 */
public final class RabidDogContent {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ModernIte.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModernIte.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<RabidDog>> RABID_DOG = TYPES.register("rabid_dog",
            () -> EntityType.Builder.of(RabidDog::new, MobCategory.MONSTER).sized(.6F, .85F).eyeHeight(.68F)
                    .clientTrackingRange(10).build(ModernIte.MOD_ID + ":rabid_dog"));
    public static final DeferredItem<DeferredSpawnEggItem> SPAWN_EGG = ITEMS.registerItem("rabid_dog_spawn_egg",
            properties -> new DeferredSpawnEggItem(RABID_DOG, 0x65655D, 0xB52229, properties.stacksTo(16)));

    private RabidDogContent() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        ITEMS.register(bus);
        bus.addListener(RabidDogContent::attributes);
        bus.addListener(RabidDogContent::placements);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(RABID_DOG.get(), Wolf.createAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 32).build());
    }

    private static void placements(RegisterSpawnPlacementsEvent event) {
        // The actual entity checks light/difficulty at the scheduler's PositionCheck stage.
        event.register(RABID_DOG.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> true, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static boolean maySpawn(ServerLevel level) {
        return (level.dimension() == Level.OVERWORLD || MobAbilities.underground(level))
                && level.getDifficulty() != Difficulty.PEACEFUL && SpawnPopulation.automaticAllowed(level, RABID_DOG.get());
    }

    public static void addSpawnEntries(MiteExtensions.SpawnContext context, List<R196MobSpawning.Entry> entries) {
        if (context.category() == R196SpawnRules.Category.MONSTER && maySpawn(context.level())) {
            entries.add(new R196MobSpawning.Entry(RABID_DOG.get(), null, context.category(), 5, 1, 1, 1, true));
        }
    }
}
