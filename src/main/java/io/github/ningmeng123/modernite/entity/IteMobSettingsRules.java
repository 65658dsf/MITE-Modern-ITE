package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobEquipmentDrops;
import io.github.ningmeng123.mite.entity.R196InfernalCreeper;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Remaining ITE species, livestock and trade controls use the actual native transactions. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class IteMobSettingsRules {
    public static final String ILLNESS="modern_ite:illness_to_death_counter";
    public static final String FIRE_ATTACK="modern_ite:attack_cause_fire";
    private static final String FIRE_ROLLED="modern_ite:attack_cause_fire_rolled";
    private static final String BONE_TWEAK="modern_ite:bone_lord_tweak_applied";
    private IteMobSettingsRules() {}

    public static int breedingExperience(Animal parent,int original){
        var identity=BuiltInRegistries.ENTITY_TYPE.getKey(parent.getType());if(!identity.getNamespace().equals("minecraft"))return original;
        String species=identity.getPath();
        return switch(species){
            case "chicken","sheep","pig","cow" -> IteConfig.integer("breed_xp_"+species);
            case "mooshroom" -> IteConfig.integer("breed_xp_cow");
            default -> original;
        };
    }

    public static float creeperExplosionRadius(Creeper creeper,float original){
        float radius=creeper instanceof R196InfernalCreeper&&!IteConfig.bool("infernal_creeper_boost")?original*.5F:original;
        return MobSplitting.isChild(creeper)?radius*.5F:radius;
    }

    /** Replace the source wool purchase, including saved offers, without resetting stock or reputation. */
    public static void villagerOffers(Villager villager,MerchantOffers offers){
        if(villager.level().isClientSide||villager.isBaby())return;
        var shard=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mite","emerald_shard"));
        int wool=IteConfig.integer("villager_wool_to_emerald_shard_count");
        int shards=IteConfig.integer("villager_wool_to_emerald_shard_shard_count");
        for(int i=0;i<offers.size();i++){
            MerchantOffer previous=offers.get(i);
            var inputBlock=net.minecraft.world.level.block.Block.byItem(previous.getBaseCostA().getItem());
            boolean buysWool=inputBlock.defaultBlockState().is(BlockTags.WOOL);
            if(!buysWool||!previous.getCostB().isEmpty()||!previous.getResult().is(Items.EMERALD)&&!previous.getResult().is(shard))continue;
            if(previous.getBaseCostA().getCount()==wool&&previous.getResult().is(shard)&&previous.getResult().getCount()==shards)continue;
            var changed=new MerchantOffer(new ItemCost(previous.getBaseCostA().getItem(),wool),Optional.empty(),new ItemStack(shard,shards),
                    previous.getUses(),previous.getMaxUses(),previous.getXp(),previous.getPriceMultiplier(),previous.getDemand());
            changed.setSpecialPriceDiff(previous.getSpecialPriceDiff());offers.set(i,changed);
        }
    }

    public static void rememberBoneLordProfile(Mob mob){
        if(boneLord(mob))mob.getPersistentData().putBoolean(BONE_TWEAK,IteConfig.bool("bone_lord_tweak"));
    }
    private static boolean boneLord(Mob mob){
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id.getNamespace().equals("mite")&&(id.getPath().equals("bone_lord")||id.getPath().equals("ancient_bone_lord"));
    }
    public static void synchronizeBoneLordProfile(Mob mob){
        if(!boneLord(mob))return;
        var data=mob.getPersistentData();boolean desired=IteConfig.bool("bone_lord_tweak");
        // Existing addon saves already use the strengthened 35/40 health and 10/13 attack profile.
        boolean before=!data.contains(BONE_TWEAK)||data.getBoolean(BONE_TWEAK);
        if(before!=desired){
            boolean ancient=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals("ancient_bone_lord");
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(desired?(ancient?40:35):(ancient?25:22));
            mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(desired?(ancient?13:10):(ancient?8:3.5));
            mob.setHealth(Math.min(mob.getHealth(),mob.getMaxHealth()));
        }
        data.putBoolean(BONE_TWEAK,desired);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void spawned(FinalizeSpawnEvent event){
        Mob mob=event.getEntity();
        if(event.isCanceled()||event.isSpawnCancelled()||mob.level().isClientSide||!(mob instanceof Zombie||mob instanceof AbstractSkeleton))return;
        var data=mob.getPersistentData();if(data.getBoolean(FIRE_ROLLED))return;
        data.putBoolean(FIRE_ROLLED,true);
        int day=MobAbilities.day(mob.level());if(mob instanceof Zombie)day=Math.min(day,IteConfig.integer("enhance_limit"));
        float chance=Math.min(.05F+day/800F,.25F);
        data.putBoolean(FIRE_ATTACK,IteConfig.bool("mob_attack_cause_fire")&&mob.getRandom().nextFloat()<chance);
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof Mob mob)||!(mob.level() instanceof ServerLevel level)||!mob.isAlive())return;
        if(mob.tickCount%10==0)synchronizeBoneLordProfile(mob);
        if(Livestock032.sourceLivestock(mob))advanceIllness(mob);
        if(IteConfig.bool("mob_attack_cause_fire")&&mob.getPersistentData().getBoolean(FIRE_ATTACK)&&mob.tickCount%20==0)
            level.sendParticles(ParticleTypes.FLAME,mob.getX(),mob.getY()+mob.getBbHeight()*.6,mob.getZ(),2,.2,.3,.2,.01);
    }

    public static void advanceIllness(Mob mob){
        if(mob.level().isClientSide||!mob.isAlive()||!Livestock032.sourceLivestock(mob))return;
        int limit=IteConfig.integer("animal_ill_to_death_time");var data=mob.getPersistentData();
        if(limit==0){data.remove(ILLNESS);return;}
        int before=Math.max(0,data.getInt(ILLNESS));
        boolean well=Livestock032.needs(mob).well();
        int elapsed=well?Math.max(0,before-1):(int)Math.min((long)before+1,Integer.MAX_VALUE);
        if(elapsed==0)data.remove(ILLNESS);else data.putInt(ILLNESS,elapsed);
        if(!well&&elapsed>=limit)mob.hurt(mob.damageSources().genericKill(),Float.MAX_VALUE);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent.Post event){
        if(event.getNewDamage()<=0||event.getEntity().level().isClientSide
                ||!(event.getSource().getDirectEntity() instanceof Mob attacker)||event.getSource().getEntity()!=attacker)return;
        if(IteConfig.bool("mob_attack_cause_fire")&&attacker.getPersistentData().getBoolean(FIRE_ATTACK)&&event.getEntity() instanceof Player
                &&io.github.ningmeng123.mite.registry.MiteTools.definition(attacker.getMainHandItem())!=null)event.getEntity().igniteForSeconds(8);
        tryDisarm(attacker,event.getEntity());
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void blocked(LivingShieldBlockEvent event){
        if(event.isCanceled()||!event.getBlocked()||event.getBlockedDamage()<=0||!(event.getEntity() instanceof Mob defender)
                ||!(event.getDamageSource().getDirectEntity() instanceof LivingEntity attacker)||event.getDamageSource().getEntity()!=attacker)return;
        tryDisarm(defender,attacker);
    }

    public static boolean disarmable(LivingEntity target){
        return !(target instanceof ExtremeMob e&&switch(e.extremeKind()){
            case EXCHANGER,MINER_ZOMBIE,ZOMBIE_DOOR,ZOMBIE_BOSS,SKELETON_ELITE_LORD,ZOMBIE_LORD,
                    UNDYING_ZOMBIE_EMPEROR,ZOMBIE_PIGMAN_LORD,ANNIHILATION_SKELETON -> true;
            default -> false;
        });
    }

    /** One successful transaction clears one held stack and gives its dropped entity a pickup delay. */
    public static boolean tryDisarm(Mob attacker,LivingEntity target){
        if(attacker.level().isClientSide||!IteConfig.bool("mob_disarm_when_defence")||target.getMainHandItem().isEmpty()||!disarmable(target)
                ||target instanceof Player player&&(player.isCreative()||player.isSpectator()||player.isBlocking()))return false;
        var sources=R196Enchantments.sources(attacker.getMainHandItem(),attacker.level(),"disarming");
        if(!EnchantmentSources.present(sources)||attacker.getRandom().nextDouble()>=EnchantmentSources.probability(sources,rank->rank/5D))return false;
        ItemStack held=target.getMainHandItem();
        if(target instanceof Player player){
            var dropped=player.drop(held.copy(),false);if(dropped==null)return false;
            dropped.setPickUpDelay(20);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.getInventory().setChanged();
            var look=player.getLookAngle();dropped.setDeltaMovement(dropped.getDeltaMovement().add(-look.z*.4,0,look.x*.4));
        }else if(target instanceof Mob mob){
            float chance=((SourceMobDropChanceAccess)mob).mite$equipmentDropChance(EquipmentSlot.MAINHAND);
            if(MobEquipmentDrops.canDrop(mob,chance,held)){
                var dropped=mob.spawnAtLocation(held.copy(),mob.getBbHeight()/2F);if(dropped!=null)dropped.setPickUpDelay(20);
            }
            mob.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            mob.getPersistentData().putInt("mite_enchantment_disarmed_ticks",40);
        }else return false;
        return true;
    }
}
