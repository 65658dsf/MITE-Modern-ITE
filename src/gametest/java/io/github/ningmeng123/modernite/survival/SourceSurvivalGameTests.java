package io.github.ningmeng123.modernite.survival;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
/** Boundary checks against actual player effects and persistent cloning. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class SourceSurvivalGameTests {
 @GameTest(template="gear_empty") public static void rainExposureStartsWeaknessOnlyAfterTheFullSourceThreshold(GameTestHelper h) {
  try(Session session=new Session(h)) {
   var player=session.player;
   for(int tick=0;tick<3600;tick++)SourceSurvivalEvents.advanceRain(player,true);
   h.assertTrue(!player.hasEffect(MobEffects.WEAKNESS),"Exactly3600rain ticks accumulate without early weakness");
   SourceSurvivalEvents.advanceRain(player,true);
   var weakness=player.getEffect(MobEffects.WEAKNESS);
   h.assertTrue(weakness!=null&&weakness.getDuration()==3600&&weakness.getAmplifier()==0,"The next exposed tick applies source weaknessI for3600ticks");
   SourceSurvivalEvents.advanceRain(player,false);
   h.assertTrue(SourceSurvivalEvents.state(player).getInt("rain")==3599,"Shelter drains one exposure tick rather than clearing the history");
   h.assertTrue(player.hasEffect(MobEffects.WEAKNESS),"Entering shelter does not instantly cure an existing weakness effect");
  }h.succeed();
 }
 @GameTest(template="gear_empty") public static void underworldTimerDrainsAndCloningKeepsPendingSurvivalHistory(GameTestHelper h) {
  try(Session before=new Session(h);Session after=new Session(h)) {
   var state=SourceSurvivalEvents.state(before.player);state.putInt("rain",725);state.putInt("teleport",500);state.putBoolean("pending",true);
   before.player.getPersistentData().put(SourceSurvivalEvents.DATA,state);
   SourceSurvivalEvents.advanceTeleport(before.player,false);
   h.assertTrue(SourceSurvivalEvents.state(before.player).getInt("teleport")==499&&!SourceSurvivalEvents.state(before.player).getBoolean("pending"),"Leaving the underworld drains one tick and drops the obsolete pending destination");
   SourceSurvivalEvents.clone(new PlayerEvent.Clone(after.player,before.player,true));
   var copied=SourceSurvivalEvents.state(after.player);
   h.assertTrue(copied.getInt("rain")==725&&copied.getInt("teleport")==499,"Player replacement retains the environmental history");
   copied.putInt("rain",0);
   h.assertTrue(SourceSurvivalEvents.state(before.player).getInt("rain")==725,"The replacement state is a detached copy");
  }h.succeed();
 }
 @GameTest(template="gear_empty",batch="ite_config") public static void configuredRainAndTeleportThresholdsRefreshWithoutLosingSavedCounters(GameTestHelper h) {
  int rain=IteConfig.integer("in_rain_debuff_time"),teleport=IteConfig.integer("underworld_random_teleport_time_new");
  boolean enabled=IteConfig.bool("underworld_random_teleport");
  try(Session session=new Session(h)) {
   IteConfig.setting("in_rain_debuff_time").set(3);IteConfig.setting("underworld_random_teleport_time_new").set(3);
   IteConfig.setting("underworld_random_teleport").set(true);IteConfig.refresh();
   for(int tick=0;tick<3;tick++){SourceSurvivalEvents.advanceRain(session.player,true);SourceSurvivalEvents.advanceTeleport(session.player,true);}
   h.assertTrue(!session.player.hasEffect(MobEffects.WEAKNESS)&&SourceSurvivalEvents.state(session.player).getInt("teleport")==3,"The newly configured thresholds retain their inclusive accumulation boundary");
   SourceSurvivalEvents.advanceRain(session.player,true);SourceSurvivalEvents.advanceTeleport(session.player,true);
   h.assertTrue(session.player.hasEffect(MobEffects.WEAKNESS)&&SourceSurvivalEvents.state(session.player).getInt("teleport")==4,"The next tick reaches the configured environmental action boundary");
   var state=SourceSurvivalEvents.state(session.player);state.putBoolean("pending",true);session.player.getPersistentData().put(SourceSurvivalEvents.DATA,state);
   IteConfig.setting("underworld_random_teleport").set(false);IteConfig.refresh();SourceSurvivalEvents.advanceTeleport(session.player,true);
   h.assertTrue(SourceSurvivalEvents.state(session.player).getInt("teleport")==3&&!SourceSurvivalEvents.state(session.player).getBoolean("pending"),"Turning off random teleport drains history and clears a pending destination");
  }finally{
   IteConfig.setting("in_rain_debuff_time").set(rain);IteConfig.setting("underworld_random_teleport_time_new").set(teleport);
   IteConfig.setting("underworld_random_teleport").set(enabled);IteConfig.refresh();
  }h.succeed();
 }

 @GameTest(template = "gear_empty", batch = "ite_config")
 public static void hotNetherrackExemptsMonstersAndStillDamagesPassiveMobs(GameTestHelper h) {
  var overworld = h.getLevel().getServer().overworld();
  var nether = overworld.getServer().getLevel(Level.NETHER);
  var floor = h.absolutePos(BlockPos.ZERO).atY(150);
  var previous = nether.getBlockState(floor);
  long previousTime = overworld.getDayTime();
  boolean enabled = IteConfig.bool("netherrack_damage");
  int limit = IteConfig.integer("netherrack_damage_limit_day");
  var piglin = EntityType.PIGLIN.create(nether);
  var cow = EntityType.COW.create(nether);
  try {
   IteConfig.setting("netherrack_damage").set(true);
   IteConfig.setting("netherrack_damage_limit_day").set(96);
   IteConfig.refresh();
   overworld.setDayTime(95 * 24_000L);
   nether.setBlockAndUpdate(floor, Blocks.NETHERRACK.defaultBlockState());
   piglin.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
   cow.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
   float piglinHealth = piglin.getHealth();
   float cowHealth = cow.getHealth();
   for (int tick = 0; tick < 21; tick++) {
    SourceSurvivalEvents.contact(new EntityTickEvent.Post(piglin));
    SourceSurvivalEvents.contact(new EntityTickEvent.Post(cow));
   }
   h.assertValueEqual(piglin.getHealth(), piglinHealth, "Hot netherrack leaves a Nether monster unharmed");
   h.assertValueEqual(cow.getHealth(), cowHealth - 1, "Hot netherrack still damages a passive mob after 21 contacts");
   h.assertTrue(piglin.hurt(piglin.damageSources().inFire(), 1), "The Nether monster remains vulnerable to ordinary fire");
  } finally {
   piglin.discard();
   cow.discard();
   nether.setBlockAndUpdate(floor, previous);
   overworld.setDayTime(previousTime);
   IteConfig.setting("netherrack_damage").set(enabled);
   IteConfig.setting("netherrack_damage_limit_day").set(limit);
   IteConfig.refresh();
  }
  h.succeed();
 }

 private static final class Session implements AutoCloseable {
  final ServerPlayer player;final EmbeddedChannel channel;
  Session(GameTestHelper h) {
   var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ite-survival"),false);
   player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
   var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
   net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
   player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
  }
  @Override public void close(){player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
 }
}
