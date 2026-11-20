package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureExplorationData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the crafted eye's actual flight against a native bastion locate. @author NingMeng123 */
@GameTestHolder("modern_ite_crafting")
@PrefixGameTestTemplate(false)
public final class BastionEyeGameTests {
    @GameTest(template = "gear_empty", batch = "bastion_locator")
    public static void bastionEyeTracksTheNearestUnlockedBastion(GameTestHelper h) throws ReflectiveOperationException {
        var overworld = h.getLevel().getServer().overworld();
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        var input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.ENDER_PEARL), new ItemStack(MiteMaterials.ADAMANTIUM_INGOT.get())));
        var recipe = overworld.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, overworld).orElseThrow();
        var stack = recipe.value().assemble(input, overworld.registryAccess());
        h.assertTrue(stack.is(ExtremeContent.BASTION_EYE.get()) && stack.getCount() == 1, "One pearl and one adamantium ingot craft one bastion eye");

        var player = FakePlayerFactory.get(nether, new GameProfile(UUID.randomUUID(), "bastion-eye"));
        player.setPos(0.5, 70, 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.assertTrue(stack.use(overworld, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL
                && stack.getCount() == 1, "The eye stays in hand outside the Nether");
        var exploration = StructureExplorationData.get(overworld);
        var saved = exploration.save(new CompoundTag(), overworld.registryAccess());
        boolean dirty = exploration.isDirty();
        long time = overworld.getDayTime();
        var worldData = nether.getServer().getWorldData();
        var savedOptions = worldData.worldGenOptions();
        var options = PrimaryLevelData.class.getDeclaredField("worldOptions");
        options.setAccessible(true);
        try {
            // ServerLevel.findNearestMapStructure requires structure lookup enabled.
            options.set(worldData, savedOptions.withStructures(true));
            overworld.setDayTime(0L);
            R196StructureGeneration.refresh(overworld);
            h.assertTrue(stack.use(nether, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL
                    && stack.getCount() == 1, "Locked structures consume no eye");
            overworld.setDayTime(32L * 24000L);
            exploration.observe(player.getUUID(), Metric.NETHER_WALK_CM, Metric.NETHER_WALK_CM.cap());
            exploration.observe(player.getUUID(), Metric.GOLD_ARMOR_CRAFTED, 1);
            R196StructureGeneration.refresh(overworld);
            verifyBastionFlight(h, nether, player, stack);
        } finally {
            options.set(worldData, savedOptions);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            overworld.setDayTime(time);
            exploration.restore(saved);
            exploration.setDirty(dirty);
            R196StructureGeneration.refresh(overworld);
        }
        h.succeed();
    }

    private static void verifyBastionFlight(GameTestHelper h, ServerLevel nether, Player player, ItemStack stack) {
        // GameTest disables natural structure generation. Supply a real bastion start
        // at the native placement for the thrower's region so locate resolves immediately.
        var bastion = nether.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(BuiltinStructures.BASTION_REMNANT);
        var source = nether.getChunkSource();
        var placement = (RandomSpreadStructurePlacement) source.getGeneratorState().getPlacementsForStructure(bastion).getFirst();
        var candidate = placement.getPotentialStructureChunk(nether.getSeed(), 0, 0);
        var generator = source.getGenerator();
        var start = bastion.value().generate(nether.registryAccess(), generator, generator.getBiomeSource(), source.randomState(),
                nether.getStructureManager(), nether.getSeed(), candidate, 0, nether, biome -> true);
        h.assertTrue(start.isValid(), "The fixture contains a native bastion start");
        var chunk = nether.getChunk(candidate.x, candidate.z);
        var saved = new HashMap<>(chunk.getAllStarts());
        var eyes = new ArrayList<EyeOfEnder>();
        Consumer<EntityJoinLevelEvent> capture = event -> {
            if (event.getLevel() == nether && event.getEntity() instanceof EyeOfEnder eye) eyes.add(eye);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, EntityJoinLevelEvent.class, capture);
        try {
            chunk.setStartForStructure(bastion.value(), start);
            nether.onStructureStartsAvailable(chunk);
            h.assertTrue(stack.use(nether, player, InteractionHand.MAIN_HAND).getResult().consumesAction()
                    && stack.isEmpty(), "A successful survival throw consumes exactly the crafted eye");
            h.assertValueEqual(eyes.size(), 1, "The throw adds exactly one native eye to the Nether");
            var eye = eyes.getFirst();
            h.assertTrue(eye.getItem().is(ExtremeContent.BASTION_EYE.get()), "The native projectile carries the bastion eye");
            var target = placement.getLocatePos(candidate);
            var direction = new Vec3(target.getX() - eye.getX(), 0, target.getZ() - eye.getZ()).normalize();
            eye.tick();
            var flight = eye.getDeltaMovement().multiply(1, 0, 1).normalize();
            h.assertTrue(flight.dot(direction) > .99, "The projectile flies toward the nearest bastion remnant");
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
            eyes.forEach(EyeOfEnder::discard);
            chunk.setAllStarts(saved);
            nether.onStructureStartsAvailable(chunk);
        }
    }
}
