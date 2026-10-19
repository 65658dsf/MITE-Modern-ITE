package io.github.ningmeng123.modernite.enchantment;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
/** Registry, independent source effects, actual cooldown persistence and maintenance. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class EnchantmentGameTests {
 private static net.minecraft.core.Holder<Enchantment> enchant(GameTestHelper h,String id) {
  return h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,ExtremeEnchantments.key(id)));
 }
 @GameTest(template="gear_empty") public static void allTwelveEnchantmentIdentitiesExistWithTheirSourceCaps(GameTestHelper h) {
  for(var definition:ExtremeEnchantments.DEFINITIONS) {
   var registered=enchant(h,definition.id());
   h.assertTrue(registered.value().getMaxLevel()==definition.maximum(),"Source maximum for "+definition.id());
   h.assertTrue(R196EnchantmentCatalog.definition("modern_ite:"+definition.id())!=null,"The source enchanting table can find "+definition.id());
  }
  h.assertTrue(ExtremeEnchantments.DEFINITIONS.size()==12,"Five Extreme, six ITE and Experience are distinct registered identities");h.succeed();
 }
 @GameTest(template="gear_empty") public static void chainBookRejectsSilkAndOnlyAcceptsIronOrHigherPickOrHammer(GameTestHelper h) {
  h.assertTrue(!R196EnchantmentCatalog.compatible("modern_ite:chain","minecraft:silk_touch"),"Chain and Silk Touch cannot be combined by the actual source anvil catalog");
  h.assertTrue(ExtremeEnchantments.applicable("chain",new ItemStack(Items.IRON_PICKAXE),false),"Iron pickaxes accept Chain");
  h.assertTrue(!ExtremeEnchantments.applicable("chain",new ItemStack(Items.STONE_PICKAXE),false),"Stone is below the Chain threshold");
  h.assertTrue(!ExtremeEnchantments.applicable("chain",new ItemStack(Items.IRON_SWORD),false),"A sword is not a pick or war hammer");h.succeed();
 }
 @GameTest(template="gear_empty") public static void ordinaryAndGrowthLevelsStaySeparateInTheStoredItem(GameTestHelper h) {
  ItemStack weapon=new ItemStack(Items.IRON_SWORD);weapon.enchant(enchant(h,"crit"),2);
  weapon.set(GearProgression.PROGRESS.get(),new GearProgress(UUID.randomUUID(),3,713,Map.of(ExtremeEnchantments.key("crit"),1),1));
  h.assertTrue(ExtremeEnchantments.level(weapon,"crit")==3,"Effect views include both sources");
  var restored=ItemStack.parse(h.getLevel().registryAccess(),weapon.save(h.getLevel().registryAccess())).orElseThrow();
  h.assertTrue(ExtremeEnchantments.level(restored,"crit")==3&&GearProgression.state(restored).experienceMillis()==713,"Transport retains independent ordinary and growth data");
  h.assertTrue(restored.getEnchantmentLevel(enchant(h,"crit"))>=2,"The original normal enchantment remains present");h.succeed();
 }
 @GameTest(template="gear_empty") public static void fourReadyEmergencyPiecesShareReducedCooldownAndMaintenanceRetainsIt(GameTestHelper h) {
  try(Session session=new Session(h)) {
   ServerPlayer player=session.player;
   for(var pair:List.of(Map.entry(EquipmentSlot.HEAD,Items.IRON_HELMET),Map.entry(EquipmentSlot.CHEST,Items.IRON_CHESTPLATE),Map.entry(EquipmentSlot.LEGS,Items.IRON_LEGGINGS),Map.entry(EquipmentSlot.FEET,Items.IRON_BOOTS))) {
    ItemStack armor=new ItemStack(pair.getValue());armor.enchant(enchant(h,"emergency"),1);player.setItemSlot(pair.getKey(),armor);
   }
   player.setHealth(0);LivingDeathEvent death=new LivingDeathEvent(player,player.damageSources().generic());NeoForge.EVENT_BUS.post(death);
   h.assertTrue(death.isCanceled()&&player.getHealth()>0,"Emergency cancels death before any resurrection array fallback");
   for(ItemStack armor:player.getArmorSlots()) h.assertTrue(armor.getOrDefault(ExtremeEnchantments.EMERGENCY_COOLDOWN.get(),0)==48000,"Four ready pieces use40% of the120000tick source cooldown");
   var source=player.getItemBySlot(EquipmentSlot.CHEST);var repaired=new ItemStack(source.getItem());GearProgression.preserve(source,repaired);
   h.assertTrue(repaired.getOrDefault(ExtremeEnchantments.EMERGENCY_COOLDOWN.get(),0)==48000,"Actual base maintenance keeps the emergency history");
   var saved=ItemStack.parse(h.getLevel().registryAccess(),repaired.save(h.getLevel().registryAccess())).orElseThrow();
   h.assertTrue(saved.getOrDefault(ExtremeEnchantments.EMERGENCY_COOLDOWN.get(),0)==48000,"Cooldown persists in item NBT");
   LivingDeathEvent again=new LivingDeathEvent(player,player.damageSources().generic());NeoForge.EVENT_BUS.post(again);
   h.assertTrue(!again.isCanceled(),"Cooling equipment does not grant a second rescue");
  }h.succeed();
 }
 @GameTest(template="gear_empty") public static void aNonfatalHitBelowThirtyPercentAlsoActivatesEmergency(GameTestHelper h) {
  try(Session session=new Session(h)) {
   ServerPlayer player=session.player;ItemStack armor=new ItemStack(Items.IRON_CHESTPLATE);armor.enchant(enchant(h,"emergency"),1);player.setItemSlot(EquipmentSlot.CHEST,armor);
   // The native ServerPlayer has sixty ticks of login immunity. Advance that real path first.
   for(int tick=0;tick<61;tick++)player.tick();player.invulnerableTime=0;
   player.setHealth(player.getMaxHealth());
   h.assertTrue(player.hurt(player.damageSources().magic(),player.getMaxHealth()*.75F),"An actual nonfatal hit passes through the native damage path");
   h.assertTrue(player.getHealth()==Math.min(10F,player.getMaxHealth())&&armor.getOrDefault(ExtremeEnchantments.EMERGENCY_COOLDOWN.get(),0)==120000,
       "Source emergency activates after surviving a hit at thirty percent health, before a later lethal fallback is needed");
  }h.succeed();
 }
 @GameTest(template="gear_empty") public static void aMonsterHoldingCritFiveUsesTheSourceCriticalFormula(GameTestHelper h) {
  var attacker=h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,new BlockPos(1,2,1));
  var victim=h.spawn(net.minecraft.world.entity.EntityType.COW,new BlockPos(2,2,1));
  try {
   attacker.setNoAi(true);victim.setNoAi(true);victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);
   var weapon=new ItemStack(Items.IRON_SWORD);attacker.setItemSlot(EquipmentSlot.MAINHAND,weapon);
   h.assertTrue(victim.hurt(attacker.damageSources().mobAttack(attacker),5F),"The ordinary hit uses the real native damage pipeline");
   float baseline=1000-victim.getHealth();victim.setHealth(1000);victim.invulnerableTime=0;weapon.enchant(enchant(h,"crit"),5);
   h.assertTrue(victim.hurt(attacker.damageSources().mobAttack(attacker),5F),"The enchanted monster hit uses the same pipeline");
   h.assertTrue(Math.abs((1000-victim.getHealth())-(baseline*1.5F+2F))<.001F,"At the source guaranteed critical rank the monster deals baseline times1.5 plus0.4 per rank");
  }finally{attacker.discard();victim.discard();}h.succeed();
 }
 @GameTest(template="gear_empty",batch="ite_config") public static void configuredConquerorScalesRepeatedHitsAndKeepsTargetsIndependent(GameTestHelper h) {
  double previous=IteConfig.number("conqueror_damage_boost_per_lvl");
  var victim=h.spawn(net.minecraft.world.entity.EntityType.COW,new BlockPos(2,2,1));
  var untouched=h.spawn(net.minecraft.world.entity.EntityType.COW,new BlockPos(3,2,1));
  try(Session session=new Session(h)) {
   IteConfig.setting("conqueror_damage_boost_per_lvl").set(2.5D);IteConfig.refresh();
   var weapon=new ItemStack(Items.IRON_SWORD);weapon.enchant(enchant(h,"conqueror"),2);session.player.setItemSlot(EquipmentSlot.MAINHAND,weapon);
   for(var cow:List.of(victim,untouched)){cow.setNoAi(true);cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);cow.setHealth(1000);}
   h.assertTrue(victim.hurt(session.player.damageSources().playerAttack(session.player),5F),"The first direct hit follows the real damage pipeline");
   float first=1000-victim.getHealth();victim.invulnerableTime=0;
   h.assertTrue(victim.hurt(session.player.damageSources().playerAttack(session.player),5F),"The second direct hit also succeeds");
   float second=1000-victim.getHealth()-first;
   h.assertTrue(Math.abs(second-first-5F)<.001F,"Rank two gains exactly two times the configured2.5per previous successful hit");
   h.assertTrue(untouched.hurt(session.player.damageSources().playerAttack(session.player),5F),"A fresh target can be attacked");
   h.assertTrue(Math.abs((1000-untouched.getHealth())-first)<.001F,"Conqueror history belongs to each player-target pair");
  }finally{IteConfig.setting("conqueror_damage_boost_per_lvl").set(previous);IteConfig.refresh();victim.discard();untouched.discard();}h.succeed();
 }
 @GameTest(template="gear_empty",batch="ite_config") public static void configuredEmergencyCooldownAboveTheFormerLimitSurvivesItemPersistence(GameTestHelper h) {
  int previous=IteConfig.integer("emergency_cooldown_");
  try(Session session=new Session(h)) {
   IteConfig.setting("emergency_cooldown_").set(240000);IteConfig.refresh();
   var armor=new ItemStack(Items.IRON_CHESTPLATE);armor.enchant(enchant(h,"emergency"),1);session.player.setItemSlot(EquipmentSlot.CHEST,armor);
   session.player.setHealth(0);var death=new LivingDeathEvent(session.player,session.player.damageSources().generic());NeoForge.EVENT_BUS.post(death);
   h.assertTrue(death.isCanceled()&&armor.getOrDefault(ExtremeEnchantments.EMERGENCY_COOLDOWN.get(),0)==240000,"The configured base cooldown is applied to real equipment");
   var saved=ItemStack.parse(h.getLevel().registryAccess(),armor.save(h.getLevel().registryAccess())).orElseThrow();
   h.assertTrue(saved.getOrDefault(ExtremeEnchantments.EMERGENCY_COOLDOWN.get(),0)==240000,"The widened persistent codec retains cooldowns beyond120000ticks");
  }finally{IteConfig.setting("emergency_cooldown_").set(previous);IteConfig.refresh();}h.succeed();
 }
 @GameTest(template="gear_empty",batch="ite_config") public static void disablingTheGuardEnchantmentRequirementOnlyUnlocksEligibleVibraniumTools(GameTestHelper h) {
  boolean previous=IteConfig.bool("player_enchant_defense");
  try {
   var tool=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("mite:vibranium_sword")));
   h.assertTrue(!tool.isEmpty(),"The test uses the actual registered vibranium sword");
   IteConfig.setting("player_enchant_defense").set(true);IteConfig.refresh();
   h.assertTrue(!ExtremeEnchantments.guard(tool),"An ordinary eligible tool still needs its enchantment by default");
   IteConfig.setting("player_enchant_defense").set(false);IteConfig.refresh();
   h.assertTrue(ExtremeEnchantments.guard(tool)&&tool.getUseDuration(h.makeMockPlayer(GameType.SURVIVAL))==ExtremeEnchantments.guardDuration(),"Disabling the requirement also updates the real Item use duration");
   h.assertTrue(!ExtremeEnchantments.guard(new ItemStack(Items.IRON_SWORD))&&!ExtremeEnchantments.guard(new ItemStack(Items.APPLE)),"Other materials and food do not become guarding tools");
  }finally{IteConfig.setting("player_enchant_defense").set(previous);IteConfig.refresh();}h.succeed();
 }
 private static final class Session implements AutoCloseable {
  final ServerPlayer player;final EmbeddedChannel channel;
  Session(GameTestHelper h) {
   var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ite-enchant"),false);
   player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
   var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
   net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
   player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
   player.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,3))));player.setNoGravity(true);player.setOnGround(true);
   player.getInventory().clearContent();player.removeAllEffects();
  }
  @Override public void close(){player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
 }
}
