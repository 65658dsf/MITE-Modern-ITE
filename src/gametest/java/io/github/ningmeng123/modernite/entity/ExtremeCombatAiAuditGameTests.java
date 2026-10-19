package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.entity.ai.SourceCreeperSwellGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** All registered Extreme identities must retain target acquisition and an eligible attack after joining/loading. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ExtremeCombatAiAuditGameTests {
    private ExtremeCombatAiAuditGameTests() {}

    private static boolean attack(ExtremeKind kind, Goal goal) {
        return switch (kind.family) {
            case ZOMBIE, PIGMAN -> goal instanceof SourceMeleeGoal;
            case SKELETON -> goal instanceof SourceMeleeGoal || goal instanceof SourceRangedAttackGoal;
            case WITCH -> goal instanceof SourceRangedAttackGoal;
            case CREEPER -> goal instanceof SourceCreeperSwellGoal;
            case GOLEM -> goal instanceof MeleeAttackGoal;
            default -> false;
        };
    }

    private static void check(GameTestHelper h, Mob mob, ExtremeKind kind, Player player, List<String> failures, String phase) {
        mob.getRandom().setSeed(1357L + kind.ordinal());
        for (int i = 0; i < 80 && mob.getTarget() == null; i++) mob.targetSelector.tick();
        if (mob.getTarget() != player) failures.add(kind + ": no acquired player after " + phase);
        boolean usable = mob.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal())
                .anyMatch(goal -> attack(kind, goal) && goal.canUse());
        if (!usable) failures.add(kind + ": no usable attack after " + phase);
    }

    @GameTest(template = "gear_empty")
    public static void allFortyFiveSpeciesAcquirePlayersAndKeepUsableAttacksAcrossReload(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var origin = new Vec3(pos.getX() + .5D, 160D, pos.getZ() + .5D);
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "extreme-ai-audit"), false);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        // Register an actual survival player without a client login or optional-mod networking handshake.
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player, cookie) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(origin.add(0D, 0D, 1.1D));
        player.setNoGravity(true);
        h.getLevel().addNewPlayer(player);
        h.assertTrue(h.getLevel().players().contains(player), "The survival target enters both entity and native player lookups");
        h.assertTrue(!player.isCreative() && !player.isSpectator(), "The real survival player remains eligible for hostile targeting");
        var failures = new ArrayList<String>();
        try {
            for (var kind : ExtremeKind.values()) {
                Mob mob = ExtremeMobs.type(kind).create(h.getLevel());
                h.assertTrue(mob != null, "The registered mob creates: " + kind);
                mob.moveTo(origin);
                mob.setNoGravity(true);
                h.assertTrue(h.getLevel().addFreshEntity(mob), "The entity executes its real join handlers: " + kind);
                try {
                    check(h, mob, kind, player, failures, "spawn");
                    var saved = new CompoundTag();
                    mob.saveWithoutId(saved);
                    saved.remove("UUID"); // A distinct test instance, while preserving the actual combat save data.
                    mob.discard();
                    Mob restored = ExtremeMobs.type(kind).create(h.getLevel());
                    h.assertTrue(restored != null, "The saved mob creates: " + kind);
                    restored.load(saved);
                    restored.moveTo(origin);
                    restored.setNoGravity(true);
                    h.assertTrue(h.getLevel().addFreshEntity(restored), "The loaded entity executes its real join handlers: " + kind);
                    try { check(h, restored, kind, player, failures, "reload"); }
                    finally { restored.discard(); }
                } finally { mob.discard(); }
            }
        } finally { player.discard(); channel.finishAndReleaseAll(); }
        h.assertTrue(failures.isEmpty(), "All 45 species acquire a target and can schedule an attack: " + String.join("; ", failures));
        h.succeed();
    }
}
