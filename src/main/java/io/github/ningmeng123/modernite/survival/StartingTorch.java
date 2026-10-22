package io.github.ningmeng123.modernite.survival;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class StartingTorch {
    public static final String DATA = "modern_ite:starting_torch_v1";

    private StartingTorch() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getPersistentData().contains(DATA)) return;
        player.getPersistentData().putBoolean(DATA, player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) place(player);
    }

    public static boolean place(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.contains(DATA) || data.getBoolean(DATA) || player.isSpectator()) return false;
        var level = player.serverLevel();
        var position = player.blockPosition();
        if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
        var previous = level.getBlockState(position);
        var torch = Blocks.TORCH.defaultBlockState();
        if (!previous.canBeReplaced() || !previous.getFluidState().isEmpty()
                || previous.hasBlockEntity() || !torch.canSurvive(level, position)) return false;
        if (!level.setBlockAndUpdate(position, torch)) return false;
        data.putBoolean(DATA, true);
        return true;
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        var previous = event.getOriginal().getPersistentData();
        if (previous.contains(DATA)) event.getEntity().getPersistentData().putBoolean(DATA, previous.getBoolean(DATA));
    }
}
