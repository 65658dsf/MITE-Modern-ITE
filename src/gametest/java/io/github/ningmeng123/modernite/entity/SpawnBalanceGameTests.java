package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Automatic-spawn admission at the real finalize, join, command and summon entry points. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class SpawnBalanceGameTests {
    private static final String[] STRONG={"mite:bone_lord","mite:ancient_bone_lord",
            "modern_ite:zombie_lord","modern_ite:zombie_pigman_lord","modern_ite:zombie_elite_lord",
            "modern_ite:skeleton_elite_lord","modern_ite:witch_lord","modern_ite:undying_zombie_emperor",
            "modern_ite:annihilation_skeleton","modern_ite:zombie_boss",
            "modern_ite:black_infernal_creeper","modern_ite:dark_iron_golem"};
    private SpawnBalanceGameTests() {}

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void openingPeaceRejectsEveryAutomaticReasonAndReasonlessHostile(GameTestHelper h) {
        try(Site site=new Site(h)) {
            site.date(2,2,false);
            for(var level:site.level.getServer().getAllLevels()) {
                h.assertFalse(SpawnPopulation.automaticAllowed(level,EntityType.ZOMBIE),"Opening peace applies in "+level.dimension().location());
                h.assertTrue(SpawnPopulation.automaticAllowed(level,EntityType.COW),"Opening peace preserves animals");
            }
            for(var reason:List.of(MobSpawnType.NATURAL,MobSpawnType.SPAWNER,MobSpawnType.TRIAL_SPAWNER,
                    MobSpawnType.MOB_SUMMONED,MobSpawnType.JOCKEY,MobSpawnType.REINFORCEMENT,
                    MobSpawnType.EVENT,MobSpawnType.CONVERSION)) {
                Mob mob=site.mob(EntityType.ZOMBIE);
                EventHooks.finalizeMobSpawn(mob,site.level,site.level.getCurrentDifficultyAt(mob.blockPosition()),reason,null);
                h.assertTrue(mob.isSpawnCancelled(),"The real finalize event blocks automatic reason "+reason);
            }
            Mob fresh=site.mob(EntityType.ZOMBIE);
            h.assertFalse(site.level.addFreshEntity(fresh),"Reasonless automatic joins cannot bypass opening peace");
            Mob cow=site.mob(EntityType.COW);
            h.assertTrue(site.level.addFreshEntity(cow),"A normal animal still joins during opening peace");
            Mob owner=site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_CALLER));
            h.assertTrue(MobAbilities.summonId(owner,"minecraft:zombie",1)==null,"Skill summons respect opening peace");
            h.assertFalse(SpawnPopulation.reserveSupport(site.mob(EntityType.ZOMBIE),owner,true),"Scripted encounters also respect opening peace");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void explicitCommandNbtTreesAndEggsRemainAvailableWithoutExemptingTheirSkills(GameTestHelper h) {
        try(Site site=new Site(h)) {
            site.date(1,1,false);
            String tag="balance_"+UUID.randomUUID().toString().replace("-","");
            var source=site.level.getServer().createCommandSourceStack().withPermission(4).withLevel(site.level).withPosition(site.origin);
            site.level.getServer().getCommands().performPrefixedCommand(source,
                    "summon modern_ite:zombie_lord ~ ~ ~ {Tags:[\""+tag+"\"],Passengers:[{id:\"modern_ite:witch_lord\",Tags:[\""+tag+"\"]}]}");
            List<Mob> tree=new ArrayList<>();
            for(Entity entity:site.level.getAllEntities())if(entity instanceof Mob mob&&entity.getTags().contains(tag)) {tree.add(mob);site.mobs.add(mob);}
            h.assertTrue(tree.size()==2,"The real summon command admits its root and NBT passenger during opening peace");
            h.assertTrue(tree.stream().allMatch(SpawnPopulation::explicit),"Only the explicit command tree receives the exemption");
            h.assertTrue(MobAbilities.summonId(tree.getFirst(),"minecraft:zombie",1)==null,"A command-created mob cannot pass its exemption to automatic skill summons");
            Mob egg=site.mob(ExtremeMobs.type(ExtremeKind.UNDYING_ZOMBIE_EMPEROR));
            EventHooks.finalizeMobSpawn(egg,site.level,site.level.getCurrentDifficultyAt(egg.blockPosition()),MobSpawnType.SPAWN_EGG,null);
            h.assertTrue(!egg.isSpawnCancelled()&&site.level.addFreshEntity(egg),"An explicit spawn egg admits a strong enemy before day 33");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void allStrongEntriesShareDayThirtyThreeAcrossDimensionsAndRewinds(GameTestHelper h) {
        try(Site site=new Site(h)) {
            for(long day:new long[]{31,32,33}) {
                site.date(day,day,true);
                for(var level:site.level.getServer().getAllLevels())for(String id:STRONG) {
                    var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
                    h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(id)),"The strong identity really exists: "+id);
                    h.assertTrue(SpawnPopulation.automaticAllowed(level,type)==(day>=33),"Shared strong gate at day "+day+": "+id+" in "+level.dimension().location());
                }
                Mob lord=site.mob(ExtremeMobs.type(ExtremeKind.WITCH_LORD));
                EventHooks.finalizeMobSpawn(lord,site.level,site.level.getCurrentDifficultyAt(lord.blockPosition()),MobSpawnType.MOB_SUMMONED,null);
                h.assertTrue(lord.isSpawnCancelled()==(day<33),"Skill finalization consumes the same strong gate on day "+day);
            }
            site.date(1,33,true);
            for(String id:STRONG)h.assertTrue(SpawnPopulation.automaticAllowed(site.level,BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id))),"Historical day 33 survives current-day rewind: "+id);
            site.date(32,32,true);
            Mob owner=site.mob(EntityType.ENDER_DRAGON);
            h.assertTrue(MobAbilities.summon(owner,ExtremeKind.ZOMBIE_ELITE_LORD,1,true)==null,"Dragon encounter reinforcements cannot bypass the strong date gate");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void naturalDistanceUsesTwentyFourBlocksOnOrdinaryAndBloodDays(GameTestHelper h) {
        try(Site site=new Site(h)) {
            var context=new MiteExtensions.SpawnContext(site.level,R196SpawnRules.Category.MONSTER,false);
            for(long day:new long[]{31,32,33}) {
                site.date(day,33,true);
                h.assertFalse(MiteExtensions.policy().spawnDistance(context,BlockPos.containing(site.origin),23*23,10000,true,true),"A dark or blood-moon candidate at 23 blocks is rejected");
                h.assertTrue(MiteExtensions.policy().spawnDistance(context,BlockPos.containing(site.origin),24*24,10000,true,false),"A candidate at exactly 24 blocks is accepted by the distance rule");
                h.assertFalse(MiteExtensions.policy().spawnDistance(context,BlockPos.containing(site.origin),10000,23*23,false,true),"The world-spawn protection distance remains 24 blocks");
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void sameTickReservationsRespectBothOverlappingPlayersAndBloodLimits(GameTestHelper h) {
        try(Site site=new Site(h)) {
            site.date(33,33,true);var first=site.player(0);var second=site.player(16);
            int union=R196MobSpawning.eligibleChunks(site.level,true).size();
            h.assertTrue(union>289&&union<578,"Two overlapping players contribute a chunk union instead of two full budgets");
            long tick=site.level.getGameTime();
            for(int i=0;i<32;i++)h.assertTrue(SpawnPopulation.reserveSupport(site.mob(EntityType.ZOMBIE),null,false),"The ordinary local limit admits slot "+i);
            h.assertFalse(SpawnPopulation.reserveSupport(site.mob(EntityType.ZOMBIE),null,false),"A thirty-third pending monster is rejected in the same game tick");
            Mob unowned=site.mob(EntityType.SKELETON);
            EventHooks.finalizeMobSpawn(unowned,site.level,site.level.getCurrentDifficultyAt(unowned.blockPosition()),MobSpawnType.MOB_SUMMONED,null);
            h.assertTrue(unowned.isSpawnCancelled()&&!unowned.getPersistentData().hasUUID(SpawnPopulation.SUMMONER),"An unowned base-game summon cannot bypass a full local area");
            h.assertTrue(SpawnPopulation.localCount(site.level,first.getBoundingBox().inflate(32,8,32))==32
                    &&SpawnPopulation.localCount(site.level,second.getBoundingBox().inflate(32,8,32))==32,"Both players count the shared pending monsters once");
            site.date(32,33,true);
            for(int i=32;i<48;i++)h.assertTrue(SpawnPopulation.reserveSupport(site.mob(EntityType.ZOMBIE),null,false),"Blood moon expands the same pending ledger to slot "+i);
            h.assertFalse(SpawnPopulation.reserveSupport(site.mob(EntityType.ZOMBIE),null,false),"A forty-ninth pending monster is rejected");
            h.assertTrue(site.level.getGameTime()==tick,"All reservations ran in one real game tick");
            Mob extra=site.mob(EntityType.SKELETON);
            h.assertTrue(SpawnPopulation.reserveSupport(extra,site.mob(EntityType.WITHER),true),"A scripted boss formation retains its encounter count");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void deepAndShallowPendingNaturalsSpendTheSameWorldAllowance(GameTestHelper h) {
        try(Site site=new Site(h)) {
            site.date(33,33,true);site.player(0);
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).size()==289,"One real player contributes the expected seventeen-by-seventeen chunk set");
            int existing=0;
            for(Entity entity:site.level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()&&!mob.isRemoved()
                    &&R196MobSpawning.category(mob)==R196SpawnRules.Category.MONSTER)existing++;
            h.assertTrue(existing<212,"The isolated server has room for the shared-budget control");
            for(int i=existing;i<212;i++) {
                Mob mob=site.mob(EntityType.ZOMBIE);
                mob.setPos(site.origin.x,i%2==0?-49:120,site.origin.z);
                h.assertTrue(SpawnPopulation.reserveNatural(mob),"Alternating deep and shallow reservations share slot "+i);
            }
            Mob deep=site.mob(EntityType.ZOMBIE);deep.setPos(site.origin.x,-49,site.origin.z);
            Mob shallow=site.mob(EntityType.ZOMBIE);shallow.setPos(site.origin.x,120,site.origin.z);
            h.assertFalse(SpawnPopulation.reserveNatural(deep),"A full shared world allowance rejects a further deep candidate");
            h.assertFalse(SpawnPopulation.reserveNatural(shallow),"A full shared world allowance rejects a further shallow candidate");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void eightLivingFollowersRemainCountedAfterSaveAndLedgerRebuild(GameTestHelper h) {
        try(Site site=new Site(h)) {
            site.date(33,33,true);Mob owner=site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_CALLER));
            var followers=new ArrayList<Mob>();var saved=new ArrayList<CompoundTag>();
            for(int i=0;i<8;i++) {
                Mob mob=site.mob(EntityType.ZOMBIE);followers.add(mob);
                h.assertTrue(SpawnPopulation.reserveSupport(mob,owner,false)&&site.level.addFreshEntity(mob),"A real follower occupies slot "+i);
                CompoundTag tag=new CompoundTag();mob.saveWithoutId(tag);saved.add(tag);
            }
            h.assertTrue(SpawnPopulation.supportCount(owner)==8,"Pending and added followers are deduplicated");
            h.assertFalse(SpawnPopulation.reserveSupport(site.mob(EntityType.ZOMBIE),owner,false),"The ninth follower is refused");
            followers.forEach(Mob::discard);SpawnPopulation.clearReservations(site.level);
            var loaded=new ArrayList<Mob>();
            for(CompoundTag tag:saved) {
                Mob copy=site.mob(EntityType.ZOMBIE);copy.load(tag);copy.setUUID(UUID.randomUUID());loaded.add(copy);
                h.assertTrue(site.level.addFreshEntity(copy),"Saved existing followers are preserved");
            }
            SpawnPopulation.clearReservations(site.level);
            h.assertTrue(SpawnPopulation.supportCount(owner)==8,"A rebuilt ledger recounts all persisted owner UUIDs");
            h.assertFalse(SpawnPopulation.reserveSupport(site.mob(EntityType.ZOMBIE),owner,false),"Reloading cannot create a ninth living follower");
            loaded.getFirst().discard();SpawnPopulation.release(loaded.getFirst());
            h.assertTrue(SpawnPopulation.reserveSupport(site.mob(EntityType.ZOMBIE),owner,false),"A dead or removed follower releases one live slot");
            site.date(1,1,false);
            Mob inhabitant=loaded.getLast();var transfer=new EntityJoinLevelEvent(inhabitant,site.level,false);NeoForge.EVENT_BUS.post(transfer);
            h.assertFalse(transfer.isCanceled(),"Previously admitted inhabitants survive a time rewind and dimension-transfer join");
            Mob disk=site.mob(EntityType.ZOMBIE);var fromDisk=new EntityJoinLevelEvent(disk,site.level,true);NeoForge.EVENT_BUS.post(fromDisk);
            h.assertFalse(fromDisk.isCanceled(),"Loading an existing saved hostile during opening peace remains supported");
        }
        h.succeed();
    }

    static final class Site implements AutoCloseable {
        final ServerLevel level;final Vec3 origin;final long time,highest;final boolean dirty,grace;
        final List<Mob> mobs=new ArrayList<>();final List<ServerPlayer> players=new ArrayList<>();
        final List<EmbeddedChannel> channels=new ArrayList<>();
        Site(GameTestHelper h) {
            level=h.getLevel();origin=h.absolutePos(new BlockPos(2,2,2)).getCenter().add(0,150,0);
            time=level.getServer().overworld().getDayTime();var history=BloodMoonInvasionData.get(level);
            highest=history.highestDay();dirty=history.isDirty();grace=ExtremeWorldState.graceEnded(level);
            SpawnPopulation.clearReservations(level);
        }
        void date(long day,long historical,boolean ended) {
            level.getServer().overworld().setDayTime((day-1)*24_000L+1000);
            BloodMoonInvasionData.get(level).restoreProgress(historical);ExtremeWorldState.restoreGraceEnded(level,ended);
        }
        Mob mob(EntityType<?> type) {
            Mob mob=(Mob)type.create(level);if(mob==null)throw new AssertionError("Registered entity creates");
            mob.moveTo(origin);mob.setNoAi(true);mobs.add(mob);return mob;
        }
        ServerPlayer player(int x) {
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"balance-player"),false);
            var player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);
            player.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,player,cookie) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet,PacketSendListener listener) {}
            };
            player.setGameMode(GameType.SURVIVAL);player.moveTo(origin.add(x,0,0));player.setNoGravity(true);
            level.addNewPlayer(player);players.add(player);return player;
        }
        @Override public void close() {
            mobs.forEach(Mob::discard);players.forEach(ServerPlayer::discard);channels.forEach(EmbeddedChannel::finishAndReleaseAll);
            SpawnPopulation.clearReservations(level);level.getServer().overworld().setDayTime(time);
            var history=BloodMoonInvasionData.get(level);history.restoreProgress(highest);history.setDirty(dirty);
            ExtremeWorldState.restoreGraceEnded(level,grace);
        }
    }
}
