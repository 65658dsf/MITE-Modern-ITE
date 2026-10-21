package io.github.ningmeng123.modernite.config;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Addon ownership, synchronized settings and final-identity spawn behavior. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IteSpawnConfigGameTests {
    private static final String LORD = "modern_ite:zombie_pigman_lord";

    private IteSpawnConfigGameTests() {}

    @GameTest(template="gear_empty", batch="ite_natural_spawn_config")
    public static void registryOwnershipIncludesEveryRegisteredIteMonsterAndExcludesProjectiles(GameTestHelper h) {
        var actual = new HashSet<String>();
        BuiltInRegistries.ENTITY_TYPE.forEach(type -> {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (IteSpawnConfig.owns(id) && type.getCategory() == MobCategory.MONSTER) actual.add(id);
        });
        var expected = new HashSet<String>();
        for (var kind : ExtremeKind.values()) expected.add(BuiltInRegistries.ENTITY_TYPE.getKey(ExtremeMobs.type(kind)).toString());
        expected.add(BuiltInRegistries.ENTITY_TYPE.getKey(io.github.ningmeng123.modernite.entity.RabidDogContent.RABID_DOG.get()).toString());
        h.assertTrue(actual.equals(expected), "The live registry discovers every ITE monster without a second UI list");
        for (String id : actual) h.assertTrue(MiteExtensions.policy().ownsMonsterSpawnSettings(id), "The ITE menu owns " + id);
        h.assertFalse(MiteExtensions.policy().ownsMonsterSpawnSettings("minecraft:zombie"), "Vanilla monsters belong to the base menu");
        h.assertFalse(MiteExtensions.policy().ownsMonsterSpawnSettings("mite:bone_lord"), "MITE monsters belong to the base menu");
        h.assertFalse(actual.contains("modern_ite:nb_fireball"), "A registered projectile is not a monster setting");
        h.succeed();
    }

    @GameTest(template="gear_empty", batch="ite_natural_spawn_config")
    public static void typedPayloadPreservesAddonRatesAndDisconnectRestoresLocalRates(GameTestHelper h) {
        var previous = IteSpawnConfig.effectiveSnapshot();
        boolean wasLocal = previous == IteSpawnConfig.localSnapshot();
        var scalarPrevious = IteConfig.effectiveSnapshot();
        boolean scalarWasLocal = scalarPrevious == IteConfig.localSnapshot();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            var rates = Map.of(LORD, 0D, "modern_ite:future_monster", .125D);
            IteConfigSync.CODEC.encode(buffer, new IteConfigSync(IteConfig.localSnapshot(), rates));
            var decoded = IteConfigSync.CODEC.decode(buffer);
            h.assertTrue(decoded.naturalSpawnRates().equals(rates), "Zero, fractions and currently unknown ITE IDs round trip");
            h.assertTrue(decoded.values().keySet().equals(IteConfig.defaults().keySet()), "The existing scalar schema is preserved");
            IteSpawnConfig.receiveServerSnapshot(decoded.naturalSpawnRates());
            h.assertTrue(IteSpawnConfig.multiplier(LORD) == 0D && IteSpawnConfig.multiplier("modern_ite:future_monster") == .125D,
                    "Server rates become effective without rewriting local settings");
            h.assertTrue(immutable(IteSpawnConfig.effectiveSnapshot()), "Received snapshots cannot be modified");
            IteConfig.clearServerSnapshot();
            h.assertTrue(IteSpawnConfig.effectiveSnapshot() == IteSpawnConfig.localSnapshot(), "The actual logout clear restores local rates");
            var validated = IteSpawnConfig.validated(Map.of("minecraft:zombie", 0D, LORD, -1D,
                    "modern_ite:not_a_number", Double.NaN, "modern_ite:future_monster", 100D));
            h.assertTrue(validated.equals(Map.of("modern_ite:future_monster", 100D)), "Foreign IDs and invalid rates cannot enter the addon snapshot");
        } finally {
            buffer.release();
            if (wasLocal) IteSpawnConfig.clearServerSnapshot(); else IteSpawnConfig.receiveServerSnapshot(previous);
            if (!scalarWasLocal) IteConfig.receiveServerSnapshot(scalarPrevious);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty", batch="ite_natural_spawn_config")
    public static void addonRatesStayIndependentAndMultiplyTheActualNaturalPool(GameTestHelper h) {
        try (Settings settings = new Settings(h)) {
            settings.ite(List.of("modern_ite:zombie_chaos=0.5", "modern_ite:future_monster=0.25"));
            settings.base(List.of("modern_ite:zombie_chaos=0.01", "minecraft:zombie=0.25"));
            var chaos = ExtremeMobs.type(ExtremeKind.ZOMBIE_CHAOS);
            h.assertTrue(ConfiguredMonsterSpawns.multiplier(chaos) == .5D, "ITE's owned setting overrides a stale base-file entry");
            h.assertTrue(ConfiguredMonsterSpawns.multiplier(EntityType.ZOMBIE) == .25D, "ITE does not override the base monster setting");
            h.assertTrue(IteSpawnConfig.multiplier("modern_ite:future_monster") == .25D, "Registry absence does not erase an outside-game override");
            var context = new MiteExtensions.SpawnContext(h.getLevel(), R196SpawnRules.Category.MONSTER, false);
            var entries = new ArrayList<R196MobSpawning.Entry>();
            ExtremeMobs.addSpawnEntries(context, settings.position, entries);
            var entry = entries.stream().filter(candidate -> candidate.type() == chaos).findFirst().orElseThrow();
            h.assertTrue(entry.weight() == ExtremeMobs.spawnWeight(ExtremeKind.ZOMBIE_CHAOS), "The actual existing raw pool weight is retained");
            h.assertTrue(ConfiguredMonsterSpawns.familyMultiplier(h.getLevel(), settings.position, entry, true) == .5D,
                    "The natural scheduler receives the configured fractional rate for that real entry");
            settings.ite(List.of("modern_ite:zombie_chaos=0.0"));
            h.assertTrue(ConfiguredMonsterSpawns.choose(h.getLevel(), settings.position, R196SpawnRules.Category.MONSTER,
                    List.of(entry), RandomSource.create(7), true) == null, "Zero excludes the real ITE pool entry");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty", batch="ite_natural_spawn_config")
    public static void pigmanFamilyWeightsAndNaturalReplacementRespectTheFinalMonsterRate(GameTestHelper h) {
        try (Settings settings = new Settings(h)) {
            settings.player();
            h.assertTrue(!R196MobSpawning.eligibleChunks(h.getLevel(), true).isEmpty(),
                    "A natural-spawn fixture needs a real survival player to provide a positive world allowance");
            var context = new MiteExtensions.SpawnContext(h.getLevel(), R196SpawnRules.Category.MONSTER, false);
            settings.base(List.of());
            settings.ite(List.of(LORD + "=0.0"));
            double weight = MiteExtensions.policy().monsterSpawnWeightMultiplier(context, settings.position, EntityType.ZOMBIFIED_PIGLIN, 1D);
            h.assertTrue(Math.abs(weight - .8D) < 1E-12, "Disabling the lord removes only its one-fifth family share");
            for (int attempt = 0; attempt < 12; attempt++) {
                Mob pigman = settings.mob(EntityType.ZOMBIFIED_PIGLIN);
                EventHooks.finalizeMobSpawn(pigman, h.getLevel(), h.getLevel().getCurrentDifficultyAt(settings.position), MobSpawnType.NATURAL, null);
                h.assertFalse(pigman.isSpawnCancelled(), "A natural pigman with world allowance remains unconverted when its lord rate is zero");
            }
            settings.base(List.of("minecraft:zombified_piglin=0.0"));
            settings.ite(List.of(LORD + "=1.0"));
            weight = MiteExtensions.policy().monsterSpawnWeightMultiplier(context, settings.position, EntityType.ZOMBIFIED_PIGLIN, 0D);
            h.assertTrue(Math.abs(weight - .2D) < 1E-12, "An enabled lord retains its natural family entry when its parent is disabled");
            Mob parent = settings.mob(EntityType.ZOMBIFIED_PIGLIN);
            EventHooks.finalizeMobSpawn(parent, h.getLevel(), h.getLevel().getCurrentDifficultyAt(settings.position), MobSpawnType.NATURAL, null);
            h.assertTrue(parent.isSpawnCancelled(), "The surviving natural family share converts to the enabled lord with probability one");
            h.assertTrue(settings.newLords() == 1, "The enabled replacement actually joins the level");
            settings.ite(List.of(LORD + "=0.0"));
            Mob egg = settings.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_PIGMAN_LORD));
            EventHooks.finalizeMobSpawn(egg, h.getLevel(), h.getLevel().getCurrentDifficultyAt(settings.position), MobSpawnType.SPAWN_EGG, null);
            h.assertTrue(!egg.isSpawnCancelled() && h.getLevel().addFreshEntity(egg), "Zero natural rate still permits an explicit spawn egg");
        }
        h.succeed();
    }

    private static boolean immutable(Map<String, Double> values) {
        try { values.put("modern_ite:future_monster", 2D); return false; }
        catch (UnsupportedOperationException expected) { return true; }
    }

    private static final class Settings implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos position;
        final List<? extends String> ite = IteSpawnConfig.NATURAL_SPAWN_RATES.get();
        final List<? extends String> base = MiteConfig.NATURAL_SPAWN_RATES.get();
        final Map<String, Double> effective = IteSpawnConfig.effectiveSnapshot();
        final MiteConfig.Snapshot baseEffective = MiteConfig.effectiveSnapshot();
        final boolean local = effective == IteSpawnConfig.localSnapshot(), baseLocal = baseEffective == MiteConfig.localSnapshot();
        final long time, highest;
        final boolean dirty, grace;
        final List<Mob> mobs = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final HashSet<java.util.UUID> previousLords = new HashSet<>();

        Settings(GameTestHelper helper) {
            this.helper = helper;
            position = helper.absolutePos(new BlockPos(1,160,1));
            var level = helper.getLevel();
            time = level.getServer().overworld().getDayTime();
            var history = BloodMoonInvasionData.get(level);
            highest = history.highestDay(); dirty = history.isDirty(); grace = ExtremeWorldState.graceEnded(level);
            level.getServer().overworld().setDayTime(32L * 24000 + 1000);
            history.restoreProgress(33); ExtremeWorldState.restoreGraceEnded(level, true);
            IteSpawnConfig.clearServerSnapshot(); MiteConfig.clearServerSnapshot(); SpawnPopulation.clearReservations(level);
            for (Entity entity : level.getAllEntities()) if (entity.getType() == ExtremeMobs.type(ExtremeKind.ZOMBIE_PIGMAN_LORD)) previousLords.add(entity.getUUID());
        }
        void ite(List<String> values) { IteSpawnConfig.NATURAL_SPAWN_RATES.set(values); IteSpawnConfig.refresh(); }
        void base(List<String> values) { MiteConfig.NATURAL_SPAWN_RATES.set(values); MiteConfig.refreshEnchantmentRules(); }
        Mob mob(EntityType<?> type) {
            Mob mob = (Mob)type.create(helper.getLevel());
            if (mob == null) throw new AssertionError("Registered entity must create");
            mob.moveTo(position.getCenter()); mob.setNoAi(true); mob.setNoGravity(true); mobs.add(mob); return mob;
        }
        void player() {
            var level = helper.getLevel();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(java.util.UUID.randomUUID(), "ite-spawn-player"), false);
            var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection); channels.add(channel);
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener listener) {}
            };
            player.setGameMode(GameType.SURVIVAL); player.moveTo(position.getCenter().add(28, 0, 0)); player.setNoGravity(true);
            level.addNewPlayer(player); players.add(player);
            SpawnPopulation.clearReservations(level);
        }
        int newLords() {
            int count = 0;
            for (Entity entity : helper.getLevel().getAllEntities()) if (entity.getType() == ExtremeMobs.type(ExtremeKind.ZOMBIE_PIGMAN_LORD)
                    && !previousLords.contains(entity.getUUID())) count++;
            return count;
        }
        @Override public void close() {
            var level = helper.getLevel();
            for (Entity entity : level.getAllEntities()) if (entity.getType() == ExtremeMobs.type(ExtremeKind.ZOMBIE_PIGMAN_LORD)
                    && !previousLords.contains(entity.getUUID())) entity.discard();
            mobs.forEach(Mob::discard); players.forEach(ServerPlayer::discard); channels.forEach(EmbeddedChannel::finishAndReleaseAll);
            SpawnPopulation.clearReservations(level);
            IteSpawnConfig.NATURAL_SPAWN_RATES.set(ite); IteSpawnConfig.refresh();
            MiteConfig.NATURAL_SPAWN_RATES.set(base); MiteConfig.refreshEnchantmentRules();
            if (!local) IteSpawnConfig.receiveServerSnapshot(effective);
            if (!baseLocal) MiteConfig.receiveServerSnapshot(baseEffective);
            level.getServer().overworld().setDayTime(time);
            var history = BloodMoonInvasionData.get(level); history.restoreProgress(highest); history.setDirty(dirty);
            ExtremeWorldState.restoreGraceEnded(level, grace);
        }
    }
}
