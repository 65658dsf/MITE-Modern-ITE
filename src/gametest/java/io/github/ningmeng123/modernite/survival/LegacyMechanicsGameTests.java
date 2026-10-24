package io.github.ningmeng123.modernite.survival;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.content.ExtremeLifeEvents;
import io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real damage, catch XP, disarm transactions and persistent environmental thresholds. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class LegacyMechanicsGameTests {
    private LegacyMechanicsGameTests() {}

    @GameTest(template="gear_empty",batch="ite_config")
    public static void nativePlayerSuffocationUsesItsConfiguredDamage(GameTestHelper h) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(h)) {
            var player=session.player;BlockPos feet=h.absolutePos(new BlockPos(2,3,2));
            player.moveTo(Vec3.atBottomCenterOf(feet));h.getLevel().setBlockAndUpdate(feet.above(),Blocks.STONE.defaultBlockState());
            config.set("in_wall_damage_for_player",3.5D);player.setHealth(1000);player.invulnerableTime=0;
            player.baseTick();h.assertTrue(Math.abs((1000-player.getHealth())-3.5F)<.001F,"The actual living base tick uses configured suffocation damage");
            config.set("in_wall_damage_for_player",0D);player.setHealth(1000);player.invulnerableTime=0;player.baseTick();
            h.assertTrue(player.getHealth()==1000,"Zero suppresses native player suffocation without changing the wall");
            h.getLevel().removeBlock(feet.above(),false);
        }h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void monsterStepsUseAllThreeParametersAndOnlyCountSuccessfulHits(GameTestHelper h) {
        var level=h.getLevel();long previousDay=level.getDayTime();
        var first=h.spawn(EntityType.ZOMBIE,new BlockPos(1,2,1));var other=h.spawn(EntityType.ZOMBIE,new BlockPos(3,2,1));
        first.setNoAi(true);other.setNoAi(true);first.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);other.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        try(ConfigRestore config=new ConfigRestore();Session target=new Session(h);Session untouched=new Session(h)) {
            level.setDayTime(99L*24000L);config.set("stepped_mob_damage_factor",.25D);
            config.set("stepped_mob_damage_progress_max",10D);config.set("stepped_mob_damage_progress_increase_day",100);
            DamageSource source=first.damageSources().mobAttack(first);
            float baseline=hit(target.player,source,5);float second=hit(target.player,source,5);
            h.assertTrue(Math.abs(second-baseline-1.25F)<.001F,"World day100 and the configured base/day divisor give a1.25second-hit bonus");
            config.set("stepped_mob_damage_progress_max",.5D);
            h.assertTrue(Math.abs(hit(target.player,source,5)-baseline-1F)<.001F,"The configured coefficient cap limits the third hit to two times0.5");
            config.set("stepped_mob_damage_progress_max",10D);config.set("stepped_mob_damage_progress_increase_day",200);
            h.assertTrue(Math.abs(hit(target.player,source,5)-baseline-2.25F)<.001F,"Changing the growth divisor changes the next real hit");
            h.assertTrue(Math.abs(hit(untouched.player,source,5)-baseline)<.001F,"Another victim does not share the first player's accumulated hits");
            h.assertTrue(Math.abs(hit(target.player,other.damageSources().mobAttack(other),5)-baseline)<.001F,"A different attacking mob has an independent counter");
            int before=target.player.getPersistentData().getCompound("modern_ite:stepped_mobs_v1").getInt(first.getUUID().toString());
            java.util.function.Consumer<LivingIncomingDamageEvent> cancel=event->{if(event.getEntity()==target.player)event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class,cancel);
            try{target.player.invulnerableTime=0;target.player.hurt(source,5);}finally{NeoForge.EVENT_BUS.unregister(cancel);}
            h.assertTrue(target.player.getPersistentData().getCompound("modern_ite:stepped_mobs_v1").getInt(first.getUUID().toString())==before,"A canceled damage transaction does not advance the counter");
            var slime=EntityType.SLIME.create(level);var dragon=EntityType.ENDER_DRAGON.create(level);
            try {
                for(var exempt:java.util.List.of(slime,dragon)) {
                    float one=hit(target.player,exempt.damageSources().mobAttack(exempt),2),two=hit(target.player,exempt.damageSources().mobAttack(exempt),2);
                    h.assertTrue(Math.abs(one-two)<.001F&&!target.player.getPersistentData().getCompound("modern_ite:stepped_mobs_v1").contains(exempt.getUUID().toString()),"Slime and dragon attackers receive no stepped bonus or counter");
                }
            }finally{slime.discard();dragon.discard();}
        }finally{level.setDayTime(previousDay);first.discard();other.discard();}h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void playerStepsShareOneSuccessfulHitCounterWithConqueror(GameTestHelper h) {
        var victim=h.spawn(EntityType.COW,new BlockPos(2,2,2));victim.setNoAi(true);victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(h)) {
            config.set("stepped_player_damage_progress",2D);config.set("stepped_player_damage_increase_per_lvl",.1D);
            config.set("conqueror_damage_boost_per_lvl",1D);session.player.experienceLevel=10;
            session.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
            DamageSource source=session.player.damageSources().playerAttack(session.player);
            float first=hit(victim,source,5),second=hit(victim,source,5),third=hit(victim,source,5);
            h.assertTrue(Math.abs(second-first-1F)<.001F&&Math.abs(third-first-2F)<.001F,"A tool without Conqueror gains level-based stepped damage up to its configured cap");
            String key="modern_ite:conqueror_"+session.player.getUUID();h.assertTrue(victim.getPersistentData().getInt(key)==3,"Each successful tool hit increments the shared history once");
            config.set("stepped_player_damage_progress",0D);var sword=new ItemStack(Items.IRON_SWORD);
            var conqueror=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,ExtremeEnchantments.key("conqueror")));
            sword.enchant(conqueror,2);session.player.setItemInHand(InteractionHand.MAIN_HAND,sword);
            h.assertTrue(Math.abs(hit(victim,source,5)-first-6F)<.001F&&victim.getPersistentData().getInt(key)==4,"Zero base-step cap leaves Conqueror active and does not double-count hits");
            victim.setInvulnerable(true);victim.invulnerableTime=0;victim.hurt(source,5);victim.setInvulnerable(false);
            h.assertTrue(victim.getPersistentData().getInt(key)==4,"A rejected attack cannot build Conqueror or base-step history");
        }finally{victim.discard();}h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void aRealFishingCatchSpawnsTheConfiguredExperience(GameTestHelper h) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(h)) {
            config.set("fishing_xp",37);var rod=new ItemStack(Items.FISHING_ROD);session.player.setItemInHand(InteractionHand.MAIN_HAND,rod);
            var hook=new FishingHook(session.player,h.getLevel(),0,0);hook.setPos(session.player.position().add(1,0,0));h.getLevel().addFreshEntity(hook);
            try(var joined=new JoinedEntities<>(h.getLevel(),net.minecraft.world.entity.ExperienceOrb.class)) {
                var nibble=FishingHook.class.getDeclaredField("nibble");nibble.setAccessible(true);nibble.setInt(hook,20);
                h.assertTrue(hook.retrieve(rod)>0,"The actual native retrieve transaction catches loot");
                var experience=joined.live();
                h.assertValueEqual(experience.size(),1,"The one real catch inserts exactly one new XP orb");
                h.assertTrue(experience.stream().allMatch(orb->orb.getValue()==37),"The spawned catch XP orb carries the configured amount");
                h.assertValueEqual(experience.stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum(),37,
                        "The complete catch transaction creates exactly37 total XP without deletion or duplication");
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}finally{hook.discard();}
        }h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void pvpDisarmOnlyCommitsAfterAllowedUnblockedDamage(GameTestHelper h) {
        boolean pvp=h.getLevel().getServer().isPvpAllowed();
        try(ConfigRestore config=new ConfigRestore();Session attacker=new Session(h);Session target=new Session(h)) {
            h.getLevel().getServer().setPvpAllowed(true);config.set("player_disarm_player",true);
            var sword=new ItemStack(Items.IRON_SWORD);sword.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(R196Enchantments.key("disarming")),5);
            attacker.player.setItemInHand(InteractionHand.MAIN_HAND,sword);target.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND));
            target.player.setInvulnerable(true);melee(attacker.player,target.player);
            h.assertTrue(!target.player.getMainHandItem().isEmpty(),"Rejected native damage cannot disarm a player");target.player.setInvulnerable(false);
            config.set("player_disarm_player",false);melee(attacker.player,target.player);
            h.assertTrue(!target.player.getMainHandItem().isEmpty(),"The configuration switch disables the PvP branch");
            config.set("player_disarm_player",true);target.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));target.player.startUsingItem(InteractionHand.OFF_HAND);
            melee(attacker.player,target.player);h.assertTrue(!target.player.getMainHandItem().isEmpty(),"An actively guarding player keeps the held item");target.player.stopUsingItem();
            h.getLevel().getServer().setPvpAllowed(false);melee(attacker.player,target.player);
            h.assertTrue(!target.player.getMainHandItem().isEmpty(),"The native server PvP switch remains authoritative");h.getLevel().getServer().setPvpAllowed(true);
            var scoreboard=h.getLevel().getScoreboard();var team=scoreboard.addPlayerTeam("ite-"+UUID.randomUUID().toString().substring(0,8));
            try {
                team.setAllowFriendlyFire(false);scoreboard.addPlayerToTeam(attacker.player.getScoreboardName(),team);scoreboard.addPlayerToTeam(target.player.getScoreboardName(),team);
                melee(attacker.player,target.player);h.assertTrue(!target.player.getMainHandItem().isEmpty(),"A team that forbids friendly fire also forbids disarming");
            }finally{scoreboard.removePlayerTeam(team);}
            for(var mode:java.util.List.of(GameType.CREATIVE,GameType.SPECTATOR)) {
                target.player.setGameMode(mode);melee(attacker.player,target.player);
                h.assertTrue(!target.player.getMainHandItem().isEmpty(),"Creative and spectator targets retain their item");
            }
            target.player.setGameMode(GameType.SURVIVAL);attacker.player.setGameMode(GameType.CREATIVE);melee(attacker.player,target.player);
            h.assertTrue(!target.player.getMainHandItem().isEmpty(),"Creative attacks do not enter the survival disarm branch");attacker.player.setGameMode(GameType.SURVIVAL);
            try(var joined=new JoinedEntities<>(h.getLevel(),ItemEntity.class)) {
                melee(attacker.player,target.player);
                h.assertTrue(target.player.getMainHandItem().isEmpty(),"A successful unblocked hit with Disarming V performs the real item transfer");
                var drops=joined.live().stream().filter(drop->drop.getItem().is(Items.DIAMOND)).toList();
                h.assertValueEqual(drops.size(),1,"The disarmed diamond is inserted into the real world exactly once");
                h.assertValueEqual(drops.stream().mapToInt(drop->drop.getItem().getCount()).sum(),1,
                        "The item transfer preserves exactly one diamond without deletion or duplication");
            }
        }finally{h.getLevel().getServer().setPvpAllowed(pvp);}h.succeed();
    }

    private static void melee(ServerPlayer attacker,ServerPlayer target) {
        target.invulnerableTime=0;DamageSource source=attacker.damageSources().playerAttack(attacker);
        SourceEnchantmentCombat.melee(attacker,target,source,2F,()->target.hurt(source,2F));
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void conquerorRetainsItsExistingPlayerTargetCombo(GameTestHelper h) {
        boolean previousPvp=h.getLevel().getServer().isPvpAllowed();
        try(ConfigRestore config=new ConfigRestore();Session attacker=new Session(h);Session target=new Session(h)) {
            h.getLevel().getServer().setPvpAllowed(true);config.set("conqueror_damage_boost_per_lvl",1D);
            var sword=new ItemStack(Items.IRON_SWORD);sword.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,ExtremeEnchantments.key("conqueror"))),2);
            attacker.player.setItemInHand(InteractionHand.MAIN_HAND,sword);DamageSource source=attacker.player.damageSources().playerAttack(attacker.player);
            float first=hit(target.player,source,3),second=hit(target.player,source,3);
            h.assertTrue(first>0&&Math.abs(second-first-2F)<.001F,"Conqueror keeps its former PvP combo bonus while unenchanted player steps remain Mob-only");
            h.assertTrue(target.player.getPersistentData().getInt("modern_ite:conqueror_"+attacker.player.getUUID())==2,"A PvP Conqueror hit records only one history step");
        }finally{h.getLevel().getServer().setPvpAllowed(previousPvp);}h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void bothFatigueStagesAndNetherTimerPersistAndHonorTheirSwitches(GameTestHelper h) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(h);Session replacement=new Session(h)) {
            config.set("underworld_debuff",true);config.set("underworld_debuff_period1",2);config.set("underworld_debuff_period2",4);
            config.set("nether_debuff",true);config.set("nether_debuff_time",3);session.player.tickCount=100;
            ExtremeLifeEvents.advanceUnderworldFatigue(session.player,true);
            h.assertTrue(!session.player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"The first-stage threshold does not trigger early");
            ExtremeLifeEvents.advanceUnderworldFatigue(session.player,true);
            h.assertTrue(session.player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==0,"The first stage applies actual Slowness I");
            ExtremeLifeEvents.advanceUnderworldFatigue(session.player,true);ExtremeLifeEvents.advanceUnderworldFatigue(session.player,true);
            h.assertTrue(session.player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==1,"The second stage upgrades the same timer to Slowness II");
            for(int i=0;i<2;i++)SourceSurvivalEvents.advanceNetherFatigue(session.player,true);
            h.assertTrue(!session.player.hasEffect(MobEffects.DIG_SLOWDOWN),"Nether fatigue waits for its configured residence time");
            SourceSurvivalEvents.advanceNetherFatigue(session.player,true);
            h.assertTrue(session.player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()==1,"The real Nether counter now reaches Mining Fatigue II");
            SourceSurvivalEvents.advanceNetherFatigue(session.player,false);
            h.assertTrue(SourceSurvivalEvents.state(session.player).getInt("nether_fatigue")==2,"Leaving the Nether drains one timer tick");
            var clone=new PlayerEvent.Clone(replacement.player,session.player,true);SourceSurvivalEvents.clone(clone);ExtremeLifeEvents.clone(clone);
            h.assertTrue(SourceSurvivalEvents.state(replacement.player).getInt("nether_fatigue")==2
                    &&replacement.player.getPersistentData().getCompound(ExtremeLifeEvents.PENALTY).getInt("elapsed")==4,"Actual player clone handlers preserve both counters");
            var penalty=session.player.getPersistentData().getCompound(ExtremeLifeEvents.PENALTY);penalty.putInt("recovery",23999);session.player.getPersistentData().put(ExtremeLifeEvents.PENALTY,penalty);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(session.player));
            h.assertTrue(!session.player.getPersistentData().getCompound(ExtremeLifeEvents.PENALTY).getBoolean("active"),"The existing player tick still completes recovery after one day outside the underworld");
            config.set("underworld_debuff",false);config.set("nether_debuff",false);
            ExtremeLifeEvents.advanceUnderworldFatigue(replacement.player,true);SourceSurvivalEvents.advanceNetherFatigue(replacement.player,true);
            h.assertTrue(!replacement.player.getPersistentData().contains(ExtremeLifeEvents.PENALTY)
                    &&!SourceSurvivalEvents.state(replacement.player).contains("nether_fatigue"),"Disabling either mechanism clears its own persistent timer");
        }h.succeed();
    }

    private static float hit(LivingEntity target,DamageSource source,float amount) {
        target.setHealth(1000);target.invulnerableTime=0;target.hurt(source,amount);return 1000-target.getHealth();
    }
    /** Native joins are synchronous even when the random test-grid entity section is not query-visible yet. */
    private static final class JoinedEntities<T extends net.minecraft.world.entity.Entity> implements AutoCloseable {
        private final java.util.List<T> entities=new java.util.ArrayList<>();
        private final java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> listener;
        JoinedEntities(net.minecraft.server.level.ServerLevel level,Class<T> type) {
            listener=event->{if(!event.isCanceled()&&event.getLevel()==level&&type.isInstance(event.getEntity()))entities.add(type.cast(event.getEntity()));};
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class,listener);
        }
        java.util.List<T> live() {return entities.stream().filter(entity->entity.isAddedToLevel()&&!entity.isRemoved()).toList();}
        @Override public void close() {NeoForge.EVENT_BUS.unregister(listener);entities.forEach(net.minecraft.world.entity.Entity::discard);}
    }
    private static final class ConfigRestore implements AutoCloseable {
        private final Map<String,Object> previous=new LinkedHashMap<>();
        void set(String key,Object value){previous.computeIfAbsent(key,ignored->IteConfig.setting(key).get());IteConfig.setting(key).set(value);IteConfig.refresh();}
        @Override public void close(){previous.forEach((key,value)->IteConfig.setting(key).set(value));IteConfig.refresh();}
    }
    private static final class Session implements AutoCloseable {
        final ServerPlayer player;final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            UUID id=UUID.randomUUID();var cookie=CommonListenerCookie.createInitial(new GameProfile(id,"mechanic-"+id.toString().substring(0,6)),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,3))));player.setNoGravity(true);player.setOnGround(true);
            player.getInventory().clearContent();player.removeAllEffects();
            for(int tick=0;tick<61;tick++)player.tick();player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);player.invulnerableTime=0;
        }
        @Override public void close(){player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
    }
}
