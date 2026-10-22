package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.furnace.FurnaceThermalMenuAccess;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real block interaction, registered ticker and native thermal menu regression. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class VibraniumFurnaceGameTests {
    private VibraniumFurnaceGameTests() {}

    @GameTest(templateNamespace = "modern_ite", template = "gear_empty")
    public static void rightClickOpensTheRealMenuAndItsRegisteredTickerSmeltsAtHeatFive(GameTestHelper h) {
        try (Session session = new Session(h)) {
            var level = h.getLevel();
            BlockPos pos = h.absolutePos(new BlockPos(3, 2, 3));
            BlockState state = ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState();
            level.setBlockAndUpdate(pos.relative(state.getValue(AbstractFurnaceBlock.FACING)), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, state);
            var furnace = (VibraniumFurnaceEntity)level.getBlockEntity(pos);
            int before = uses(session.player);

            h.assertTrue(use(state, h, pos, session.player).consumesAction(), "A real right click consumes the furnace interaction");
            h.assertTrue(session.player.containerMenu instanceof FurnaceMenu,
                    "Right clicking the registered vibranium block opens the actual server furnace menu");
            h.assertValueEqual(uses(session.player), before + 1, "Opening the furnace awards exactly one native interaction statistic");
            var menu = (FurnaceMenu)session.player.containerMenu;
            h.assertTrue(menu.getSlot(0).container == furnace && menu.getSlot(1).container == furnace
                    && menu.getSlot(2).container == furnace, "All three opened menu slots use the placed furnace inventory");
            h.assertTrue(menu.stillValid(session.player), "The opened menu remains usable near the placed block");

            ItemStack input = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:vibranium_block")));
            ItemStack fuel = new ItemStack(ExtremeContent.BLAZE_COAL.get(), 2);
            h.assertTrue(menu.getSlot(0).mayPlace(input) && menu.getSlot(1).mayPlace(fuel),
                    "The opened native slots accept the real heat-five input and its blaze coal fuel");
            menu.getSlot(0).set(input);
            menu.getSlot(1).set(fuel);

            FurnaceMenu clientCopy = new FurnaceMenu(menu.containerId, session.player.getInventory());
            menu.setSynchronizer(new ContainerSynchronizer() {
                @Override public void sendInitialData(AbstractContainerMenu source, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
                    h.assertTrue(data.length >= 9, "The native menu stream includes progress, heat, capacity and input restrictions");
                    for (int index = 0; index < data.length; index++) clientCopy.setData(index, data[index]);
                }
                @Override public void sendSlotChange(AbstractContainerMenu source, int slot, ItemStack stack) {}
                @Override public void sendCarriedChange(AbstractContainerMenu source, ItemStack stack) {}
                @Override public void sendDataChange(AbstractContainerMenu source, int index, int value) { clientCopy.setData(index, value); }
            });
            var clientHeat = (FurnaceThermalMenuAccess)clientCopy;
            h.assertValueEqual(clientHeat.mite$getMaxHeat(), 5, "The native client menu receives the vibranium capacity of five");
            h.assertValueEqual(clientHeat.mite$getRequiredHeat(), 5, "The native client menu receives the actual input requirement of five");
            h.assertValueEqual(clientHeat.mite$getHeat(), 0, "A newly opened unlit furnace synchronizes zero current heat");
            h.assertTrue(clientCopy.getSlot(1).mayPlace(new ItemStack(ExtremeContent.BLAZE_COAL.get())),
                    "The synchronized client fuel slot permits the actual heat-five fuel");

            var ticker = state.getTicker(level, ExtremeContent.FURNACE_ENTITY.get());
            h.assertTrue(ticker != null, "The placed block provides a server ticker for its registered block entity type");
            ticker.tick(level, pos, level.getBlockState(pos), furnace);
            menu.broadcastChanges();
            h.assertValueEqual(clientHeat.mite$getHeat(), 5, "The actual registered ticker ignites the furnace and synchronizes heat five");
            h.assertTrue(level.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT), "Ignition updates the placed block's lit state");
            for (int tick = 1; tick < 199; tick++) ticker.tick(level, pos, level.getBlockState(pos), furnace);
            h.assertTrue(furnace.getItem(2).isEmpty() && furnace.getItem(0).getCount() == 1,
                    "The registered ticker cannot finish before the complete 200-tick cycle");
            ticker.tick(level, pos, level.getBlockState(pos), furnace);
            var output = furnace.getItem(2);
            h.assertTrue(furnace.getItem(0).isEmpty()
                    && output.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("modern_ite:gem_shard_6")))
                    && output.getCount() >= 1 && output.getCount() <= 2,
                    "The registered ticker consumes the heat-five input and produces its guaranteed shard plus at most one bonus");
            h.assertTrue(furnace.getItem(1).is(ExtremeContent.BLAZE_COAL.get()) && furnace.getItem(1).getCount() == 1,
                    "The complete cycle consumes exactly one blaze coal from the opened inventory");
            h.assertTrue(menu.getSlot(2).getItem() == output, "The output is available through the same real menu opened by right click");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "modern_ite", template = "gear_empty")
    public static void aBlockedFrontRefusesTheMenuAndClearingItRestoresUseInEveryDirection(GameTestHelper h) {
        try (Session session = new Session(h)) {
            BlockPos pos = h.absolutePos(new BlockPos(3, 2, 3));
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockState state = ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState().setValue(AbstractFurnaceBlock.FACING, direction);
                h.getLevel().setBlockAndUpdate(pos, state);
                h.getLevel().setBlockAndUpdate(pos.relative(direction), Blocks.STONE.defaultBlockState());
                int before = uses(session.player);
                h.assertTrue(use(state, h, pos, session.player) == InteractionResult.FAIL,
                        "The shared front-access rule rejects a solid obstruction when facing " + direction);
                h.assertTrue(session.player.containerMenu == session.player.inventoryMenu,
                        "A blocked front cannot open a furnace menu when facing " + direction);
                h.assertValueEqual(uses(session.player), before, "Blocked interaction cannot award a furnace-use statistic");

                h.getLevel().setBlockAndUpdate(pos.relative(direction), Blocks.AIR.defaultBlockState());
                h.assertTrue(use(state, h, pos, session.player).consumesAction()
                        && session.player.containerMenu instanceof FurnaceMenu,
                        "Removing the front obstruction restores the same placed furnace when facing " + direction);
                h.assertValueEqual(uses(session.player), before + 1, "A successful retry awards exactly one furnace use");
                session.player.closeContainer();
            }
        }
        h.succeed();
    }

    private static InteractionResult use(BlockState state, GameTestHelper h, BlockPos pos, ServerPlayer player) {
        return state.useWithoutItem(h.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), state.getValue(AbstractFurnaceBlock.FACING), pos, false));
    }

    private static int uses(ServerPlayer player) { return player.getStats().getValue(Stats.CUSTOM.get(Stats.INTERACT_WITH_FURNACE)); }

    private static final class Session implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;

        private Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ite-furnace"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 4))));
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            player.removeAllEffects();
        }

        @Override public void close() {
            player.closeContainer();
            player.getInventory().clearContent();
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
