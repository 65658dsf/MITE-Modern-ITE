package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Remaining legacy controls are checked through native damage, trades, breeding and saved needs. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IteRemainingMobSettingsGameTests {
    private IteRemainingMobSettingsGameTests() {}
    private static final class Settings implements AutoCloseable {
        private final Map<String,Object> previous=new LinkedHashMap<>();
        void set(String key,Object value){var setting=IteConfig.setting(key);previous.putIfAbsent(key,setting.get());setting.set(value);IteConfig.refresh();}
        @Override public void close(){previous.forEach((key,value)->IteConfig.setting(key).set(value));IteConfig.refresh();}
    }
    private static Vec3 origin(GameTestHelper h){return h.absolutePos(new BlockPos(1,220,1)).getCenter();}
    private static void prepare(Mob mob,Vec3 at){mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(at);}

    @GameTest(template="gear_empty",batch="ite_remaining_mobs")
    public static void boneLordToggleUpdatesExistingProfilesWithoutHealing(GameTestHelper h){
        Mob lord=MiteMobs.type(MobKind.BONE_LORD).create(h.getLevel());prepare(lord,origin(h));
        Mob ancient=MiteMobs.type(MobKind.ANCIENT_BONE_LORD).create(h.getLevel());prepare(ancient,origin(h));
        try(Settings settings=new Settings()){
            settings.set("bone_lord_tweak",true);ExtremeNativeMobs.initialize(lord);ExtremeNativeMobs.initialize(ancient);
            h.assertTrue(lord.getAttributeBaseValue(Attributes.MAX_HEALTH)==35&&ancient.getAttributeBaseValue(Attributes.MAX_HEALTH)==40,
                    "Enabled preserves the current ordinary and ancient lord health profiles");
            lord.setHealth(10);ancient.setHealth(10);settings.set("bone_lord_tweak",false);
            IteMobSettingsRules.synchronizeBoneLordProfile(lord);IteMobSettingsRules.synchronizeBoneLordProfile(ancient);
            h.assertTrue(lord.getAttributeBaseValue(Attributes.MAX_HEALTH)==22&&lord.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)==3.5
                    &&ancient.getAttributeBaseValue(Attributes.MAX_HEALTH)==25&&ancient.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)==8,
                    "Disabling selects the real unboosted species attributes");
            settings.set("bone_lord_tweak",true);IteMobSettingsRules.synchronizeBoneLordProfile(lord);
            h.assertTrue(lord.getAttributeBaseValue(Attributes.MAX_HEALTH)==35&&lord.getHealth()==10,
                    "Reenabling restores the stronger profile without healing existing damage");
        }finally{lord.discard();ancient.discard();}h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_remaining_mobs")
    public static void infernalToggleChangesTheRealNativeExplosionRadius(GameTestHelper h){
        try(Settings settings=new Settings()){
            settings.set("infernal_creeper_boost",true);float boosted=explode(h);
            settings.set("infernal_creeper_boost",false);float normal=explode(h);
            h.assertTrue(boosted==6F&&normal==3F,"The actual native infernal detonation uses radius six or three according to the setting");
        }h.succeed();
    }
    private static float explode(GameTestHelper h){
        Creeper creeper=(Creeper)MiteMobs.type(MobKind.INFERNAL_CREEPER).create(h.getLevel());prepare(creeper,origin(h));
        AtomicReference<Explosion> observed=new AtomicReference<>();
        Consumer<ExplosionEvent.Start> listener=event->{if(event.getExplosion().getDirectSourceEntity()==creeper){observed.set(event.getExplosion());event.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(ExplosionEvent.Start.class,listener);
        try{
            Method detonate=Creeper.class.getDeclaredMethod("explodeCreeper");detonate.setAccessible(true);detonate.invoke(creeper);
            h.assertTrue(observed.get()!=null,"Native detonation reaches the real explosion event");return observed.get().radius();
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot call native creeper detonation",failure);}
        finally{NeoForge.EVENT_BUS.unregister(listener);creeper.discard();}
    }

    @GameTest(template="gear_empty",batch="ite_remaining_mobs")
    public static void woolSettingsChangeSavedOffersWithoutResettingTheirUses(GameTestHelper h){
        Villager villager=EntityType.VILLAGER.create(h.getLevel());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.SHEPHERD).setLevel(1));
        var original=new MerchantOffer(new ItemCost(Items.WHITE_WOOL,18),Optional.empty(),new ItemStack(Items.EMERALD),3,7,2,.05F,4);
        original.setSpecialPriceDiff(-1);var offers=new MerchantOffers();offers.add(original);villager.setOffers(offers);
        try(Settings settings=new Settings()){
            settings.set("villager_wool_to_emerald_shard_count",6);settings.set("villager_wool_to_emerald_shard_shard_count",3);
            MerchantOffer changed=villager.getOffers().getFirst();
            h.assertTrue(changed.getBaseCostA().getCount()==6&&changed.getResult().is(MiteShards.EMERALD_SHARD.get())&&changed.getResult().getCount()==3,
                    "The native offer getter uses the configured wool input and real registered shards");
            h.assertTrue(changed.getUses()==3&&changed.getMaxUses()==7&&changed.getDemand()==4&&changed.getSpecialPriceDiff()==-1,
                    "Migrating an existing offer preserves uses, demand and reputation adjustment");
            int count=villager.getOffers().size();settings.set("villager_wool_to_emerald_shard_count",9);
            h.assertTrue(villager.getOffers().getFirst().getBaseCostA().getCount()==9&&villager.getOffers().size()==count,
                    "Subsequent setting changes update the same stock slot without appending duplicates");
        }finally{villager.discard();}h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_remaining_mobs")
    public static void livestockIllnessPersistsRecoversAndHonorsDisabledDeath(GameTestHelper h){
        var cow=EntityType.COW.create(h.getLevel());prepare(cow,origin(h));
        var restored=EntityType.COW.create(h.getLevel());
        try(Settings settings=new Settings()){
            settings.set("animal_ill_to_death_time",3);Livestock032.needs(cow,new LivestockRules032.Needs(0,1,1));
            IteMobSettingsRules.advanceIllness(cow);IteMobSettingsRules.advanceIllness(cow);
            var saved=new CompoundTag();cow.saveWithoutId(saved);restored.load(saved);
            h.assertTrue(restored.getPersistentData().getInt(IteMobSettingsRules.ILLNESS)==2,"The real entity save retains accumulated illness");
            Livestock032.needs(restored,new LivestockRules032.Needs(1,1,1));IteMobSettingsRules.advanceIllness(restored);
            h.assertTrue(restored.getPersistentData().getInt(IteMobSettingsRules.ILLNESS)==1,"A healthy tick reduces prior illness by exactly one");
            settings.set("animal_ill_to_death_time",0);IteMobSettingsRules.advanceIllness(restored);
            h.assertTrue(restored.isAlive()&&!restored.getPersistentData().contains(IteMobSettingsRules.ILLNESS),"Zero disables death and clears its counter");
            settings.set("animal_ill_to_death_time",3);Livestock032.needs(restored,new LivestockRules032.Needs(1,0,1));
            IteMobSettingsRules.advanceIllness(restored);IteMobSettingsRules.advanceIllness(restored);
            h.assertTrue(restored.isAlive(),"The sick animal remains alive before the configured threshold");
            IteMobSettingsRules.advanceIllness(restored);h.assertFalse(restored.isAlive(),"The threshold triggers real native death");
        }finally{cow.discard();restored.discard();}h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_remaining_mobs")
    public static void allFourSpeciesAwardConfiguredExperienceOnlyOncePerRealBirth(GameTestHelper h){
        boolean mobLoot=h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        try(Settings settings=new Settings()){
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true,h.getLevel().getServer());
            EntityType<?>[] species={EntityType.CHICKEN,EntityType.SHEEP,EntityType.PIG,EntityType.COW};
            int[] rewards={11,13,17,19};String[] names={"chicken","sheep","pig","cow"};
            for(int i=0;i<species.length;i++){
                Animal first=(Animal)species[i].create(h.getLevel()),second=(Animal)species[i].create(h.getLevel());
                Vec3 at=origin(h).add(i*8,0,0);prepare(first,at);prepare(second,at);
                settings.set("breed_xp_"+names[i],rewards[i]);
                try{
                    first.spawnChildFromBreeding(h.getLevel(),second);
                    var orbs=h.getLevel().getEntitiesOfClass(ExperienceOrb.class,first.getBoundingBox().inflate(3));
                    h.assertTrue(orbs.stream().mapToInt(ExperienceOrb::getValue).sum()==rewards[i],
                            "A real "+names[i]+" birth awards exactly its configured experience without an extra random reward");
                    orbs.forEach(ExperienceOrb::discard);
                }finally{
                    h.getLevel().getEntitiesOfClass(Animal.class,first.getBoundingBox().inflate(3),Animal::isBaby).forEach(Animal::discard);
                    first.discard();second.discard();
                }
            }
        }finally{h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(mobLoot,h.getLevel().getServer());}h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_remaining_mobs")
    public static void fireAttackAndDisarmingSwitchesReachSuccessfulMelee(GameTestHelper h){
        var zombie=EntityType.ZOMBIE.create(h.getLevel());prepare(zombie,origin(h));
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(zombie.position().add(0,0,1));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);
        var weapon=MobAbilities.item("mite:copper_sword");MobAbilities.enchant(zombie,weapon,"mite:disarming",5);
        zombie.setItemInHand(InteractionHand.MAIN_HAND,weapon);zombie.getPersistentData().putBoolean(IteMobSettingsRules.FIRE_ATTACK,true);
        try(Settings settings=new Settings()){
            settings.set("mob_attack_cause_fire",false);settings.set("mob_disarm_when_defence",false);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
            h.assertTrue(zombie.doHurtTarget(player),"A real armed zombie melee lands in the disabled control");
            h.assertTrue(!player.isOnFire()&&!player.getMainHandItem().isEmpty(),"Disabled options prevent a saved fire trait and the equipped disarming enchantment");
            player.invulnerableTime=0;settings.set("mob_attack_cause_fire",true);settings.set("mob_disarm_when_defence",true);
            h.assertTrue(zombie.doHurtTarget(player),"The second real melee lands after enabling both options");
            h.assertTrue(player.isOnFire()&&player.getMainHandItem().isEmpty(),"Successful melee applies the configured flame trait and one disarm transaction");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(4),e->e.getItem().is(Items.IRON_SWORD));
            h.assertTrue(drops.size()==1,"The held player weapon is dropped once rather than copied or deleted");drops.forEach(ItemEntity::discard);
        }finally{player.clearFire();player.discard();zombie.discard();}h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_remaining_mobs")
    public static void defenseSwitchStopsNativeShieldsAndSuccessfulBlocksCanDisarm(GameTestHelper h){
        var zombie=EntityType.ZOMBIE.create(h.getLevel());prepare(zombie,origin(h));
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(zombie.position().add(0,0,2));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var weapon=MobAbilities.item("mite:copper_sword");MobAbilities.enchant(zombie,weapon,"mite:disarming",5);
        zombie.setItemInHand(InteractionHand.MAIN_HAND,weapon);zombie.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(MiteShields.get("iron")));
        zombie.setYRot(0);zombie.yBodyRot=0;zombie.startUsingItem(InteractionHand.OFF_HAND);
        for(int i=0;i<6;i++)zombie.tick();
        var source=zombie.damageSources().playerAttack(player);
        try(Settings settings=new Settings()){
            settings.set("mob_defense",true);settings.set("mob_disarm_when_defence",true);
            h.assertTrue(zombie.isDamageSourceBlocked(source),"The shield has passed its actual native raising delay");
            zombie.hurt(source,2F);
            h.assertTrue(player.getMainHandItem().isEmpty(),"A successful real shield block can use the defender's disarming enchantment");
            settings.set("mob_defense",false);
            h.assertFalse(zombie.isDamageSourceBlocked(source),"Disabling mob defense bypasses an already raised native shield immediately");
        }finally{
            h.getLevel().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(4),e->e.getItem().is(Items.IRON_SWORD)).forEach(ItemEntity::discard);
            player.discard();zombie.discard();
        }h.succeed();
    }
}
