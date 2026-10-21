package io.github.ningmeng123.modernite.content;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.Comparator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Witch;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Adds the new curse to the existing operator diagnostics. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class DwarfCurseCommands {
    private DwarfCurseCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var dwarf = Commands.literal("dwarf_curse")
                .executes(context -> apply(context.getSource(), EntityArgument.getPlayer(context, "player"), 0))
                .then(Commands.argument("delay", IntegerArgumentType.integer(0))
                        .executes(context -> apply(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                IntegerArgumentType.getInteger(context, "delay"))));
        event.getDispatcher().register(Commands.literal("mite").requires(source -> source.hasPermission(2))
                .then(Commands.literal("curse").then(Commands.literal("apply")
                        .then(Commands.argument("player", EntityArgument.player()).then(dwarf)))));
    }

    private static int apply(CommandSourceStack source, ServerPlayer player, int delay) throws CommandSyntaxException {
        Witch witch = player.serverLevel().getEntitiesOfClass(Witch.class, player.getBoundingBox().inflate(64), Witch::isAlive)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (witch == null) throw new SimpleCommandExceptionType(Component.translatable("commands.mite.curse.no_witch")).create();
        if (!DwarfCurse.schedule(player, witch, delay))
            throw new SimpleCommandExceptionType(Component.translatable("commands.mite.curse.already_cursed")).create();
        PlayerCurses.reconcile(player);
        source.sendSuccess(() -> Component.translatable("commands.mite.curse.applied", player.getDisplayName(),
                ExtremeEffects.VALUES.get("dwarf_curse").value().getDisplayName(), delay), true);
        return 1;
    }

    public static int status(CommandSourceStack source, ServerPlayer player) {
        var entry = DwarfCurseData.get(player.serverLevel()).entry(player.getUUID());
        if (entry == null) return 0;
        source.sendSuccess(() -> Component.translatable("commands.mite.curse.status", player.getDisplayName(),
                ExtremeEffects.VALUES.get("dwarf_curse").value().getDisplayName(), entry.realized(), entry.realized(),
                Math.max(0L, entry.realizationTime() - player.server.overworld().getGameTime()), entry.witch().toString()), false);
        return 17;
    }
}
