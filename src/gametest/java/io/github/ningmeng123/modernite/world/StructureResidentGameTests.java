package io.github.ningmeng123.modernite.world;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native sentry markers and igloo templates survive rejected one-time admission. @author NingMeng123 */
@GameTestHolder("modern_ite_structure_spawns")
@PrefixGameTestTemplate(false)
public final class StructureResidentGameTests {
    private StructureResidentGameTests() {}

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void endCitySentrySurvivesProtectionSavingAndReplaysWithoutFinalization(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(false);
            site.sentry(site.level, site.pos);
            helper.assertTrue(site.queued().size() == 1, "The actual native Sentry marker retains its rejected shulker");
            UUID uuid = site.queued().getFirst().getCompound("entity").getUUID("UUID");
            helper.assertFalse(site.queued().getFirst().getBoolean("finalize"), "The sentry keeps native unfinalized state");
            site.roundTrip();
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().size() == 1, "Saving cannot bypass opening protection");
            site.date(true);
            DeferredStructureSpawns.replay(site.level);
            Entity resident = site.level.getEntity(uuid);
            helper.assertTrue(resident instanceof Shulker && resident.isAddedToLevel(), "The same native sentry UUID actually joins after protection");
            helper.assertTrue(((Mob)resident).getSpawnType() == MobSpawnType.STRUCTURE && site.finalizations == 0,
                    "Sentry provenance never introduces a finalization callback");
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().isEmpty() && site.level.getEntity(uuid) == resident, "A committed resident replays exactly once");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void endCitySentryWaitsForLocalRoomAndLateCancellationKeepsItsEntry(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(true);
            var player = site.player(helper);
            var volume = player.getBoundingBox().inflate(32, 8, 32);
            int baseline = SpawnPopulation.localCount(site.level, volume);
            site.setting("local_hostile_limit", baseline + 1);
            Mob occupant = EntityType.ZOMBIE.create(site.level);
            occupant.moveTo(site.pos.getX() + 3.5, site.pos.getY(), site.pos.getZ() + .5, 0, 0);
            occupant.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true);
            site.entities.add(occupant);
            helper.assertTrue(site.level.addFreshEntity(occupant), "The real local population occupies the final available slot");
            site.sentry(site.level, site.pos);
            helper.assertTrue(site.queued().size() == 1, "A full local area retains the one-time sentry");
            UUID uuid = site.queued().getFirst().getCompound("entity").getUUID("UUID");
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().size() == 1 && site.level.getEntity(uuid) == null, "A crowded replay keeps its original queue entry");
            occupant.discard();
            Consumer<EntityJoinLevelEvent> cancel = event -> { if (event.getEntity().getUUID().equals(uuid)) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, cancel);
            try { DeferredStructureSpawns.replay(site.level); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(site.queued().size() == 1 && site.level.getEntity(uuid) == null, "Late join rejection also keeps the original entry");
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().isEmpty() && site.level.getEntity(uuid) instanceof Shulker,
                    "Releasing population and cancellation admits the original sentry");
            helper.assertTrue(site.finalizations == 0, "Repeated admission attempts never finalize the native sentry");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void lateInitialSentryCancellationCannotPersistAProvisionalAdmissionBypass(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(true);
            var player = site.player(helper);
            int baseline = SpawnPopulation.localCount(site.level, player.getBoundingBox().inflate(32, 8, 32));
            site.setting("local_hostile_limit", baseline + 1);
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getLevel() == site.level && event.getEntity() instanceof Shulker && event.getEntity().blockPosition().equals(site.pos)) {
                    helper.assertTrue(event.getEntity().getPersistentData().getBoolean("modern_ite:spawn_admitted"),
                            "The late listener follows the fresh sentry's provisional population admission");
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, cancel);
            try { site.sentry(site.level, site.pos); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(site.queued().size() == 1, "The initially canceled native sentry is retained");
            UUID uuid = site.queued().getFirst().getCompound("entity").getUUID("UUID");
            site.roundTrip();

            Mob occupant = EntityType.ZOMBIE.create(site.level);
            occupant.moveTo(site.pos.getX() + 3.5, site.pos.getY(), site.pos.getZ() + .5, 0, 0);
            occupant.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true);
            site.entities.add(occupant);
            helper.assertTrue(site.level.addFreshEntity(occupant), "An actual admitted hostile fills the newly occupied local slot");
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().size() == 1 && site.level.getEntity(uuid) == null,
                    "The first replay rechecks population after an initial late cancellation");
            helper.assertTrue(occupant.getPersistentData().getBoolean("modern_ite:spawn_admitted"),
                    "Existing admitted inhabitants retain their own saved admission marker");
            occupant.discard();
            DeferredStructureSpawns.replay(site.level);
            Entity resident = site.level.getEntity(uuid);
            helper.assertTrue(resident instanceof Shulker && resident.isAddedToLevel() && site.queued().isEmpty(),
                    "The original sentry joins after the occupied slot becomes available");
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.level.getEntity(uuid) == resident && site.finalizations == 0,
                    "Late initial cancellation preserves exactly-once admission and native no-finalization behavior");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void nativeIglooBottomRetainsItsZombieVillagerOnlyInsideStructurePlacement(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(false);
            site.placeIgloo(site.level, true);
            helper.assertTrue(site.queued().size() == 1
                            && site.queued().getFirst().getCompound("entity").getString("id").equals("minecraft:zombie_villager"),
                    "The native igloo bottom retains the protected zombie villager");
            helper.assertTrue(site.entities.stream().anyMatch(entity -> entity.getType() == EntityType.VILLAGER && entity.isAddedToLevel()),
                    "The native igloo villager retains its ordinary uncapped placement");
            UUID uuid = site.queued().getFirst().getCompound("entity").getUUID("UUID");
            site.roundTrip();
            site.date(true);
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().isEmpty() && site.level.getEntity(uuid) != null
                            && site.level.getEntity(uuid).getType() == EntityType.ZOMBIE_VILLAGER,
                    "The native saved igloo inhabitant joins with its original UUID");
            helper.assertTrue(site.finalizations == 0 && !StructureResidents.active(site.level),
                    "The igloo preserves unfinalized data and releases its actual structure scope");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void ordinaryTemplateCallsKeepTheirNativeUnfinalizedOrigin(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(true);
            site.placeIgloo(site.level, false);
            helper.assertTrue(site.queued().isEmpty() && site.entities.stream().anyMatch(entity -> entity instanceof Mob mob
                            && entity.getType() == EntityType.ZOMBIE_VILLAGER && entity.isAddedToLevel() && mob.getSpawnType() == null),
                    "An independent native template keeps its ordinary origin outside structure placement");
            helper.assertTrue(site.finalizations == 0, "The independent template receives no new finalization");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void nativeSentryAndIglooJoinImmediatelyWhenAdmissionAllows(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(true);
            site.sentry(site.level, site.pos);
            site.placeIgloo(site.level, true);
            for (var type : List.of(EntityType.SHULKER, EntityType.ZOMBIE_VILLAGER)) {
                helper.assertTrue(site.entities.stream().anyMatch(entity -> entity instanceof Mob mob && entity.getType() == type
                                && entity.isAddedToLevel() && mob.getSpawnType() == MobSpawnType.STRUCTURE),
                        "Allowed native structure inhabitants join immediately with their structure provenance: " + type);
            }
            helper.assertTrue(site.queued().isEmpty() && site.finalizations == 0,
                    "Allowed main-thread inhabitants need neither a queue nor a new finalization callback");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void workerSentryAndIglooResidentsReachMainThreadAdmissionWithoutProtoChunkBypass(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(true);
            var worker = new WorkerRegion(site.level, new ChunkPos(site.pos));
            var sentry = new SentryPiece(site.level, site.pos);
            CompletableFuture.runAsync(() -> {
                sentry.spawn(worker.region, site.pos);
                site.placeIgloo(worker.region, true);
            }).join();
            for (int tasks = 0; tasks < 64 && site.level.getServer().pollTask(); tasks++) {}
            helper.assertTrue(site.queued().size() == 2, "Both actual worker structure paths persist their hostile inhabitants for main-thread admission");
            helper.assertTrue(worker.entities().stream().noneMatch(tag -> tag.getString("id").equals("minecraft:shulker")
                            || tag.getString("id").equals("minecraft:zombie_villager")),
                    "Fresh hostile residents cannot become existing disk entities by entering the proto-chunk");
            helper.assertTrue(worker.entities().stream().anyMatch(tag -> tag.getString("id").equals("minecraft:villager")),
                    "The native non-hostile template resident still reaches its proto-chunk");
            site.roundTrip();
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().isEmpty(), "The two worker inhabitants complete actual main-thread admission");
            helper.assertTrue(site.finalizations == 0, "Worker residents retain native no-finalization semantics through saving and replay");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void worldGenRegionCannotMarkFreshSentriesAsSavedResidentsOnTheMainThread(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(true);
            var region = new WorkerRegion(site.level, new ChunkPos(site.pos));
            site.sentry(region.region, site.pos);
            helper.assertTrue(site.queued().size() == 1 && region.entities().isEmpty(),
                    "WorldGenRegion still defers fresh residents when its caller happens to run on the main thread");
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().isEmpty() && site.finalizations == 0, "The region resident joins through normal main-thread admission");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void finalizedMansionWorkerResidentWaitsForRoomWithoutRepeatingFinalization(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(true);
            var player = site.player(helper);
            int baseline = SpawnPopulation.localCount(site.level, player.getBoundingBox().inflate(32, 8, 32));
            site.setting("local_hostile_limit", baseline + 1);
            Mob occupant = EntityType.ZOMBIE.create(site.level);
            occupant.moveTo(site.pos.getX() + 3.5, site.pos.getY(), site.pos.getZ() + .5, 0, 0);
            occupant.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true);
            site.entities.add(occupant);
            helper.assertTrue(site.level.addFreshEntity(occupant), "The population fixture fills the native structure resident's last slot");
            var worker = new WorkerRegion(site.level, new ChunkPos(site.pos));
            var piece = new MansionMarkerPiece(site.level, site.pos);
            var identity = site.level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.WOODLAND_MANSION);
            var start = new StructureStart(identity, new ChunkPos(site.pos), 0, new PiecesContainer(List.of(piece)));
            CompletableFuture.runAsync(() -> start.placeInChunk(worker.region, site.level.structureManager(),
                    site.level.getChunkSource().getGenerator(), RandomSource.create(29), piece.getBoundingBox(), new ChunkPos(site.pos))).join();
            for (int tasks = 0; tasks < 64 && site.level.getServer().pollTask(); tasks++) {}
            helper.assertTrue(site.queued().size() == 1 && site.queued().getFirst().getCompound("entity").getString("id").equals("minecraft:evoker"),
                    "The actual worker Mage marker queues its completed evoker state");
            helper.assertTrue(site.finalizations == 1 && !site.queued().getFirst().getBoolean("finalize")
                            && worker.entities().stream().noneMatch(tag -> tag.getString("id").equals("minecraft:evoker")),
                    "The native structure callback executes once and cannot bypass population through a proto-chunk");
            UUID uuid = site.queued().getFirst().getCompound("entity").getUUID("UUID");
            site.roundTrip();
            DeferredStructureSpawns.replay(site.level);
            helper.assertTrue(site.queued().size() == 1 && site.level.getEntity(uuid) == null, "A full local area keeps the finalized inhabitant pending");
            occupant.discard();
            DeferredStructureSpawns.replay(site.level);
            Entity resident = site.level.getEntity(uuid);
            helper.assertTrue(site.queued().isEmpty() && resident instanceof Mob mob && mob.isAddedToLevel()
                            && mob.isPersistenceRequired() && mob.getSpawnType() == MobSpawnType.STRUCTURE && site.finalizations == 1,
                    "The same finalized mansion inhabitant joins after room opens without rerolling its native initialization");
        }
        helper.succeed();
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final long time, highest;
        final boolean historyDirty, grace, queueDirty;
        final CompoundTag originalQueue;
        final Map<String, Object> previousSettings = IteConfig.effectiveSnapshot();
        final boolean remoteSettings = previousSettings != IteConfig.localSnapshot();
        final List<Entity> entities = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final Consumer<EntityJoinLevelEvent> capture;
        final Consumer<FinalizeSpawnEvent> finalized;
        int finalizations;

        Site(GameTestHelper helper) {
            level = helper.getLevel();
            pos = helper.absolutePos(new BlockPos(2, 2, 2)).above(150);
            time = level.getDayTime();
            var history = BloodMoonInvasionData.get(level);
            highest = history.highestDay(); historyDirty = history.isDirty(); grace = ExtremeWorldState.graceEnded(level);
            var queue = DeferredStructureSpawns.get(level);
            originalQueue = queue.save(new CompoundTag(), level.registryAccess()); queueDirty = queue.isDirty();
            level.getDataStorage().set("modern_ite_deferred_structure_spawns", DeferredStructureSpawns.load(new CompoundTag(), level.registryAccess()));
            capture = event -> {
                if (event.getLevel() == level && event.getEntity().distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 1024)
                    entities.add(event.getEntity());
            };
            finalized = event -> {
                if (event.getLevel().getLevel() == level && event.getEntity().distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 1024)
                    finalizations++;
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, capture);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, FinalizeSpawnEvent.class, finalized);
            SpawnPopulation.clearReservations(level);
        }

        void date(boolean ended) {
            level.setDayTime(ended ? 73_000 : 1_000);
            BloodMoonInvasionData.get(level).restoreProgress(ended ? 4 : 1);
            ExtremeWorldState.restoreGraceEnded(level, ended);
        }

        void setting(String key, Object value) {
            var settings = new LinkedHashMap<>(IteConfig.effectiveSnapshot());
            settings.put(key, value); IteConfig.receiveServerSnapshot(settings);
        }

        @SuppressWarnings("removal")
        ServerPlayer player(GameTestHelper helper) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.moveTo(pos.getCenter()); players.add(player); return player;
        }

        List<CompoundTag> queued() {
            return DeferredStructureSpawns.get(level).save(new CompoundTag(), level.registryAccess())
                    .getList("spawns", Tag.TAG_COMPOUND).stream().map(tag -> (CompoundTag)tag).toList();
        }

        void roundTrip() {
            CompoundTag saved = DeferredStructureSpawns.get(level).save(new CompoundTag(), level.registryAccess());
            level.getDataStorage().set("modern_ite_deferred_structure_spawns", DeferredStructureSpawns.load(saved, level.registryAccess()));
        }

        void sentry(ServerLevelAccessor access, BlockPos position) { new SentryPiece(level, position).spawn(access, position); }

        void placeIgloo(WorldGenLevel access, boolean structure) {
            StructureTemplate template = level.getStructureManager().getOrCreate(ResourceLocation.withDefaultNamespace("igloo/bottom"));
            var size = template.getSize();
            BoundingBox bounds = new BoundingBox(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + size.getX() - 1, pos.getY() + size.getY() - 1, pos.getZ() + size.getZ() - 1);
            if (access == level) for (BlockPos point : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ())) blocks.putIfAbsent(point.immutable(), level.getBlockState(point));
            if (structure) {
                var identity = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.IGLOO);
                var start = new StructureStart(identity, new ChunkPos(pos), 0, new PiecesContainer(List.of(new TemplatePiece(template, pos, bounds))));
                start.placeInChunk(access, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(19), bounds, new ChunkPos(pos));
            } else template.placeInWorld(access, pos, pos, new StructurePlaceSettings(), RandomSource.create(19), 2);
        }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(capture); NeoForge.EVENT_BUS.unregister(finalized);
            entities.forEach(Entity::discard);
            players.forEach(player -> level.getServer().getPlayerList().remove(player));
            blocks.forEach((point, state) -> level.setBlock(point, state, 2));
            SpawnPopulation.clearReservations(level);
            var queue = DeferredStructureSpawns.load(originalQueue, level.registryAccess()); queue.setDirty(queueDirty);
            level.getDataStorage().set("modern_ite_deferred_structure_spawns", queue);
            level.setDayTime(time);
            var history = BloodMoonInvasionData.get(level); history.restoreProgress(highest); history.setDirty(historyDirty);
            ExtremeWorldState.restoreGraceEnded(level, grace);
            if (remoteSettings) IteConfig.receiveServerSnapshot(previousSettings); else IteConfig.clearServerSnapshot();
        }
    }

    private static final class SentryPiece extends EndCityPieces.EndCityPiece {
        SentryPiece(ServerLevel level, BlockPos origin) { super(level.getStructureManager(), "base_floor", origin, Rotation.NONE, true); }
        void spawn(ServerLevelAccessor level, BlockPos pos) {
            handleDataMarker("Sentry", pos, level, RandomSource.create(7), BoundingBox.fromCorners(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)));
        }
    }

    private static final class TemplatePiece extends StructurePiece {
        final StructureTemplate template;
        final BlockPos origin;
        TemplatePiece(StructureTemplate template, BlockPos origin, BoundingBox bounds) {
            super(StructurePieceType.IGLOO, 0, bounds); this.template = template; this.origin = origin;
        }
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}
        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox bounds, ChunkPos chunk, BlockPos center) {
            template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setBoundingBox(bounds), random, 2);
        }
    }

    private static final class MansionMarkerPiece extends WoodlandMansionPieces.WoodlandMansionPiece {
        final BlockPos origin;
        MansionMarkerPiece(ServerLevel level, BlockPos origin) {
            super(level.getStructureManager(), "1x1_a1", origin, Rotation.NONE); this.origin = origin;
        }
        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox bounds, ChunkPos chunk, BlockPos center) {
            handleDataMarker("Mage", origin, level, random, bounds);
        }
    }

    private static final class WorkerRegion {
        final WorldGenRegion region;
        final ProtoChunk center;
        final List<ProtoChunk> generatedChunks = new ArrayList<>();
        WorkerRegion(ServerLevel level, ChunkPos coordinate) {
            var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            var biome = level.getBiome(coordinate.getMiddleBlockPosition(100));
            StaticCache2D<GenerationChunkHolder> chunks = StaticCache2D.create(coordinate.x, coordinate.z, 1, (x, z) -> {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, level, biomes, null);
                generatedChunks.add(chunk);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> biome, level.getChunkSource().randomState().sampler());
                return new GenerationChunkHolder(chunk.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
                        return chunk.getPersistedStatus().isOrAfter(status) ? chunk : null;
                    }
                };
            });
            center = (ProtoChunk)chunks.get(coordinate.x, coordinate.z).getChunkIfPresentUnchecked(ChunkStatus.CARVERS);
            region = new WorldGenRegion(level, chunks, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
        }

        List<CompoundTag> entities() {
            return generatedChunks.stream().flatMap(chunk -> chunk.getEntities().stream()).toList();
        }
    }
}
