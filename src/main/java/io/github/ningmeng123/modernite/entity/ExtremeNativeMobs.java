package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.*;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.*;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** ITE/Extreme deltas on existing species; the base remains the sole calendar multiplier. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class ExtremeNativeMobs {
    private static final String PROFILE="modern_ite:source_profile";
    private ExtremeNativeMobs(){}
    private static String id(Entity e){return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();}
    private static void stat(Mob m,Holder<Attribute> key,double value){var a=m.getAttribute(key);if(a!=null)a.setBaseValue(value);}
    private static void body(Mob m,double hp,double attack,double speed,double follow){stat(m,Attributes.MAX_HEALTH,hp);stat(m,Attributes.ATTACK_DAMAGE,attack);stat(m,Attributes.MOVEMENT_SPEED,speed);stat(m,Attributes.FOLLOW_RANGE,follow);}
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void join(EntityJoinLevelEvent event){
        if(event.isCanceled()||event.getLevel().isClientSide)return;
        if(event.getEntity() instanceof Mob m&&!(m instanceof ExtremeMob)){initialize(m);if(m.getType()==EntityType.GIANT&&m.getPersistentData().getBoolean(PROFILE))installGiant(m);}
        if(event.getEntity() instanceof LargeFireball ball&&!(ball instanceof InfernalFireball)&&ball.getOwner() instanceof Ghast ghast&&ghast.getType()==EntityType.GHAST){
            event.setCanceled(true);int count=ghast.level().dimension()==Level.NETHER?5:1;
            for(int i=0;i<count;i++){
                InfernalFireball copy=InfernalFireball.NB.get().create(ghast.level());if(copy==null)continue;
                copy.setOwner(ghast);copy.setPos(ball.position());
                copy.setDeltaMovement(ball.getDeltaMovement().add(count==1?Vec3.ZERO:new Vec3((ghast.getRandom().nextDouble()-.5)*.08,0,(ghast.getRandom().nextDouble()-.5)*.08)));
                copy.accelerationPower=ball.accelerationPower*1.15;
                float power=ghast.level().dimension()==Level.OVERWORLD?Math.round(6*1.25F)*2:6;
                if(ghast.getPersistentData().getBoolean("modern_ite:dragon_summoned"))power*=3;
                copy.normalPower(power);ghast.level().addFreshEntity(copy);
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void combatGoals(EntityJoinLevelEvent event){
        if(event.isCanceled()||event.getLevel().isClientSide||!(event.getEntity() instanceof Mob m)||m instanceof ExtremeMob)return;
        if(m.getType()==EntityType.ZOMBIFIED_PIGLIN&&m instanceof ZombifiedPiglin pig){
            var data=pig.getPersistentData();
            if(!data.getBoolean("modern_ite:pigman_rolls")){
                data.putBoolean("modern_ite:pigman_rolls",true);data.putBoolean("modern_ite:pigman_boosted",pig.getRandom().nextDouble()<.025);
                if(pig.getRandom().nextInt(10)>8)pig.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
            }
            if(R196DiggerGoal.first(pig)==null)pig.goalSelector.addGoal(1,new R196DiggerGoal(pig));
            var attacks=pig.goalSelector.getAvailableGoals().stream().filter(w->w.getGoal() instanceof SourceMeleeGoal
                ||w.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal||w.getGoal() instanceof PigmanBowGoal).toList();
            for(var attack:attacks)pig.goalSelector.removeGoal(attack.getGoal());
            // The bow goal claims movement first; losing the bow immediately releases it to melee.
            pig.goalSelector.addGoal(4,new PigmanBowGoal(pig));
            pig.goalSelector.addGoal(5,new SourceMeleeGoal(pig,1,true));
            // The source default zombie_pigman_attack_animails is false.
            pig.targetSelector.removeAllGoals(g->true);
            pig.targetSelector.addGoal(1,new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(pig));
            pig.targetSelector.addGoal(2,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(pig,Player.class,true));
            pig.targetSelector.addGoal(3,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(pig,net.minecraft.world.entity.npc.Villager.class,false));
        }
        if(m instanceof AbstractSkeleton skeleton){
            var data=m.getPersistentData();
            if(!data.contains("modern_ite:melee_roll"))data.putBoolean("modern_ite:melee_roll",m.getRandom().nextInt(100)<15);
            if(data.getBoolean("modern_ite:melee_roll")&&m.getMainHandItem().getItem() instanceof BowItem){
                skeleton.goalSelector.removeAllGoals(g->g instanceof SourceRangedAttackGoal||g instanceof net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<?>);
                skeleton.goalSelector.addGoal(4,new SourceMeleeGoal(skeleton,1.2,false));
            }
            if(m instanceof R196Skeleton bones&&bones.miteKind()!=MobKind.LONGDEAD_GUARDIAN&&(bones.isLongdead()||bones.isLord())&&!data.contains("modern_ite:stock_roll")){
                data.putBoolean("modern_ite:stock_roll",true);data.putBoolean("modern_ite:switch_weapon",m.getRandom().nextFloat()<.25);
                if(data.getBoolean("modern_ite:switch_weapon")){
                    ItemStack spare=m.getMainHandItem().getItem() instanceof BowItem?MobAbilities.firstItem("mite:ancient_metal_sword","minecraft:iron_sword"):new ItemStack(Items.BOW);
                    data.put("modern_ite:stocked_weapon",spare.save(m.registryAccess()));
                }
            }
        }
    }
    public static void initialize(Mob m){
        CompoundTag t=m.getPersistentData();if(t.getBoolean(PROFILE))return;t.putBoolean(PROFILE,true);
        float ratio=m.getHealth()/Math.max(1,m.getMaxHealth());String key=id(m);
        if(m instanceof EnderDragon){t.putBoolean("modern_ite:boss",true);stat(m,Attributes.MAX_HEALTH,7500);}
        else if(m instanceof WitherBoss wither){t.putBoolean("modern_ite:boss",true);stat(m,Attributes.MAX_HEALTH,1300);if(wither.getInvulnerableTicks()>0)wither.setInvulnerableTicks(600);}
        else if(key.equals("minecraft:zombie")||key.equals("minecraft:zombie_villager"))body(m,15,8,.23,64);
        else if(key.equals("minecraft:zombified_piglin"))body(m,40,12,.25,64);
        else if(key.equals("minecraft:skeleton"))body(m,15,8,.3,64);
        else if(key.equals("minecraft:wither_skeleton"))body(m,45,13,.25,64);
        else if(key.equals("minecraft:enderman"))body(m,60,15,.3,64);
        else if(key.equals("minecraft:iron_golem"))body(m,200,0,.25,64);
        else if(key.equals("minecraft:giant")){body(m,100,25,.3,64);stat(m,Attributes.KNOCKBACK_RESISTANCE,1);}
        else if(key.equals("minecraft:ghast"))stat(m,Attributes.MAX_HEALTH,15);
        else if(key.equals("minecraft:blaze")){stat(m,Attributes.MAX_HEALTH,40);stat(m,Attributes.ATTACK_DAMAGE,6);}
        else if(key.equals("minecraft:creeper"))body(m,20,0,.25,64);
        else if(key.equals("minecraft:spider"))body(m,15,6,1,64);
        else if(key.equals("minecraft:cave_spider"))body(m,16,6,1,28);
        else if(m instanceof R196Elemental earth&&!earth.isClay())body(m,m.level().dimension()==Level.NETHER?60:30,12,.2,20);
        else if(key.equals("mite:invisible_stalker"))body(m,20,6,.23,40);
        else if(key.equals("mite:revenant"))body(m,35,12,.28,64);
        else if(key.equals("mite:longdead"))body(m,15,8,.29,40);
        else if(key.equals("mite:longdead_guardian"))body(m,26,10,.29,40);
        else if(key.equals("mite:bone_lord"))body(m,35,10,.27,44);
        else if(key.equals("mite:ancient_bone_lord"))body(m,40,13,.3,48);
        if(m instanceof Slime&&!(m instanceof MagmaCube)){var attack=m.getAttribute(Attributes.ATTACK_DAMAGE);if(attack!=null)attack.setBaseValue(attack.getBaseValue()+4);}
        if(m instanceof MagmaCube)stat(m,Attributes.MAX_HEALTH,m.getAttributeBaseValue(Attributes.MAX_HEALTH)+30);
        m.setHealth(Math.min(m.getMaxHealth(),m.getMaxHealth()*ratio));
    }
    private static void installGiant(Mob m){
        if(!(m instanceof PathfinderMob path))return;
        m.goalSelector.addGoal(0,new net.minecraft.world.entity.ai.goal.FloatGoal(path));
        m.goalSelector.addGoal(2,new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(path,1.1,true));
        m.goalSelector.addGoal(6,new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(path,1));
        m.targetSelector.addGoal(2,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(m,Player.class,true));
        m.targetSelector.addGoal(3,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(m,net.minecraft.world.entity.npc.Villager.class,false));
        m.targetSelector.addGoal(4,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(m,net.minecraft.world.entity.animal.Animal.class,true));
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void spawned(FinalizeSpawnEvent event){
        Mob m=event.getEntity();if(event.isCanceled()||event.isSpawnCancelled()||m.level().isClientSide)return;
        if(m instanceof Zombie z&&!(m instanceof ExtremeMob)&&MobAbilities.day(m.level())>32&&m.getRandom().nextInt(5)==0){z.setBaby(true);stat(m,Attributes.ATTACK_DAMAGE,m.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)*.5);stat(m,Attributes.MOVEMENT_SPEED,.35);}
        if(m instanceof Creeper&&!id(m).contains("infernal")&&m.getRandom().nextInt(5)==0){Mob bat=MobAbilities.summonId(m,"mite:vampire_bat",1);if(bat!=null)m.startRiding(bat,true);}
        if(id(m).equals("mite:phase_spider")&&m.getRandom().nextInt(100)==0){
            String[] riders={"minecraft:skeleton","mite:longdead_guardian","mite:longdead","modern_ite:skeleton_blaze","mite:bone_lord","mite:ancient_bone_lord","modern_ite:skeleton_elite_lord","modern_ite:skeleton_fast_arrow","modern_ite:skeleton_zombie"};
            Mob rider=MobAbilities.summonId(m,riders[m.getRandom().nextInt(riders.length)],1);if(rider!=null)rider.startRiding(m,true);
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event){
        if(event.getEntity() instanceof Player p&&!p.level().isClientSide&&p.getPersistentData().getLong("modern_ite:boss_strength_suppression")>p.level().getGameTime())p.removeEffect(MobEffects.DAMAGE_BOOST);
        if(!(event.getEntity() instanceof Mob m)||!(m.level() instanceof ServerLevel level)||!m.isAlive())return;
        if(m instanceof Zombie zombie&&m.getPersistentData().getBoolean("modern_ite:siege_rider")&&!zombie.isBaby())zombie.setBaby(true);
        if(m instanceof Witch&&!(m instanceof ExtremeMob))MobAbilities.witchSupportTick(m);
        if(m instanceof WitherBoss wither){
            var t=m.getPersistentData();int invul=wither.getInvulnerableTicks();
            if(invul>0){t.putBoolean("modern_ite:wither_charging",true);if(m.tickCount%10==0)wither.heal(10);}
            else{
                if(t.getBoolean("modern_ite:wither_charging")&&!t.getBoolean("modern_ite:wither_initial")){t.putBoolean("modern_ite:wither_initial",true);witherWave(wither);}
                if(wither.getHealth()<=wither.getMaxHealth()/2&&!t.getBoolean("modern_ite:wither_half")){t.putBoolean("modern_ite:wither_half",true);witherWave(wither);}
            }
        }
        if(m instanceof EnderDragon dragon)dragonTick(dragon,level);
        if(m instanceof Bat bat&&(bat.isVehicle()||bat.isPassenger()))bat.setResting(false);
        if(m instanceof R196Bat bat&&bat.isVehicle()&&bat.getTarget()!=null){
            var prey=bat.getTarget();Vec3 aim=prey.position().subtract(bat.position()).normalize().scale(.025);bat.setDeltaMovement(bat.getDeltaMovement().add(aim.x,-.025,aim.z));
            if(!(prey instanceof Player))bat.setTarget(null);
        }
        if(m instanceof ZombifiedPiglin pig&&m.getPersistentData().getBoolean("modern_ite:pigman_boosted")&&MobAbilities.day(level)>128&&m.tickCount%30==0){
            int day=MobAbilities.day(level);
            for(var ally:level.getEntitiesOfClass(ZombifiedPiglin.class,m.getBoundingBox().inflate(16,8,16))){
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,40+day/32*10,pig.getRandom().nextInt(Math.max((day-96)/96,1))));
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,day/32*10,pig.getRandom().nextInt(Math.max((day-128)/96,1))));
            }
        }
        if(m instanceof AbstractSkeleton skeleton&&m.tickCount%10==0&&m.getTarget()!=null&&m.getSensing().hasLineOfSight(m.getTarget())&&m.getPersistentData().getBoolean("modern_ite:switch_weapon")){
            boolean bow=m.getMainHandItem().getItem() instanceof BowItem;
            if(bow&&m.distanceToSqr(m.getTarget())<25||!bow&&m.distanceToSqr(m.getTarget())>36){
                var data=m.getPersistentData();ItemStack spare=ItemStack.parseOptional(m.registryAccess(),data.getCompound("modern_ite:stocked_weapon"));
                if(!spare.isEmpty()){var held=m.getMainHandItem();m.setItemSlot(EquipmentSlot.MAINHAND,spare);data.put("modern_ite:stocked_weapon",held.save(m.registryAccess()));skeleton.reassessWeaponGoal();}
            }
        }
        if(id(m).equals("minecraft:giant")&&m.tickCount%1200==0)MobAbilities.summonId(m,"minecraft:zombie",1);
    }
    public static void witherWave(WitherBoss wither){
        for(int i=0;i<15;i++){
            Mob troop=MobAbilities.summonId(wither,i<5?"modern_ite:wither_skeleton_arrow":"minecraft:wither_skeleton",1);
            if(troop!=null)troop.getPersistentData().putBoolean("modern_ite:wither_minion",true);
        }
    }
    private static void dragonTick(EnderDragon d,ServerLevel level){
        boolean half=d.getHealth()<d.getMaxHealth()/2;var data=d.getPersistentData();
        if(half&&!data.getBoolean("modern_ite:dragon_half")){
            data.putBoolean("modern_ite:dragon_half",true);
            for(int i=0;i<5;i++)for(String type:new String[]{"modern_ite:zombie_elite_lord","modern_ite:skeleton_elite_lord","minecraft:ghast"}){
                Mob troop=MobAbilities.summonId(d,type,1);if(troop==null)continue;troop.getPersistentData().putBoolean("modern_ite:dragon_summoned",true);
                stat(troop,Attributes.MAX_HEALTH,troop.getAttributeBaseValue(Attributes.MAX_HEALTH)*3);
                if(troop instanceof Zombie)stat(troop,Attributes.ATTACK_DAMAGE,troop.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)*3);troop.setHealth(troop.getMaxHealth());
            }
        }
        if(half&&d.tickCount%1200==0)for(ServerPlayer p:level.players())if(p.isAlive()&&!p.isSpectator()&&!p.isCreative()){
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,600));Mob troop=MobAbilities.summonId(d,"mite:revenant",1);if(troop!=null)troop.setTarget(p);
        }
        if(d.tickCount%(half?40:80)==0){
            Player target=level.getNearestPlayer(d,128);if(target!=null&&!target.isCreative()&&!target.isSpectator()){
                d.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
                d.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target.position());
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void incoming(LivingIncomingDamageEvent event){
        if(event.isCanceled()||event.getEntity().level().isClientSide)return;
        LivingEntity victim=event.getEntity();DamageSource src=event.getSource();Entity attacker=src.getEntity();
        if(victim instanceof WitherBoss&&attacker!=null&&attacker.getPersistentData().getBoolean("modern_ite:wither_minion")
                ||victim.getPersistentData().getBoolean("modern_ite:wither_minion")&&(attacker instanceof WitherBoss||src.getDirectEntity() instanceof WitherSkull)){event.setCanceled(true);return;}
        if(victim instanceof ElementalMaterial e&&e.modernIteMaterial()==1&&src.is(DamageTypeTags.IS_FIRE))event.setAmount(event.getAmount()*2);
        if(victim instanceof EnderDragon d){
            if(d.getHealth()<d.getMaxHealth()*.3&&src.is(DamageTypeTags.IS_PROJECTILE)){event.setCanceled(true);return;}
            if(d.getHealth()<150)event.setAmount(event.getAmount()*.8F);
            if(attacker instanceof Player player&&d.getRandom().nextInt(5)==0)player.addEffect(new MobEffectInstance(MobEffects.WITHER,200));
        }
        if(attacker instanceof IronGolem golem&&golem.getType()==EntityType.IRON_GOLEM&&src.getDirectEntity()==attacker)event.setAmount(25+(golem.getPersistentData().getBoolean("modern_ite:golem_boost")?3:0));
        if(victim instanceof Player player&&player.isUsingItem()&&!ExtremeEnchantments.guard(player.getUseItem())
                &&BuiltInRegistries.ITEM.getKey(player.getUseItem().getItem()).toString().startsWith("modern_ite:club_")
                &&!src.is(DamageTypeTags.BYPASSES_SHIELD)&&src.getSourcePosition()!=null){
            Vec3 direction=src.getSourcePosition().subtract(player.position()).multiply(1,0,1).normalize();
            if(player.getViewVector(1).multiply(1,0,1).dot(direction)>0){event.setAmount(event.getAmount()*.5F);player.playSound(SoundEvents.SHIELD_BLOCK,.7F,1.2F);EquipmentDurability.hurt(player.getUseItem(),1,player,player.getUsedItemHand()==net.minecraft.world.InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);}
        }
        if(victim instanceof Bat&&victim.getPersistentData().hasUUID("modern_ite:summoner")&&src.is(DamageTypeTags.WITCH_RESISTANT_TO))event.setCanceled(true);
    }
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event){
        if(event.getNewDamage()<=0)return;
        if(event.getSource().getEntity() instanceof EnderMan&&event.getSource().getDirectEntity()==event.getSource().getEntity()){event.getEntity().push(0,.6,0);event.getEntity().hurtMarked=true;}
        if(event.getSource().getEntity() instanceof EnderDragon dragon&&event.getEntity() instanceof Player player){
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,300));
            if(dragon.level() instanceof ServerLevel level&&dragon.getRandom().nextBoolean()){
                LightningBolt bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt!=null){bolt.moveTo(player.position().add(dragon.getRandom().nextInt(17)-8,0,dragon.getRandom().nextInt(17)-8));level.addFreshEntity(bolt);}
            }
        }
        if(event.getSource().getEntity() instanceof R196Bat bat&&bat.miteKind()!=MobKind.NIGHTWING&&bat.getRandom().nextBoolean())event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON,480));
        if(event.getSource().getEntity() instanceof ZombifiedPiglin pig&&event.getSource().getDirectEntity()==pig&&pig.getRandom().nextBoolean())event.getEntity().igniteForSeconds(5);
        if(event.getSource().getEntity() instanceof MagmaCube cube&&cube.getRandom().nextBoolean())event.getEntity().igniteForSeconds(100);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof Mob m)||m.level().isClientSide)return;
        if(m instanceof Creeper&&m.getRandom().nextInt(100)<3)MobAbilities.summonId(m,"minecraft:creeper",4);
        if(m.getType()==EntityType.SPIDER&&m.getRandom().nextInt(100)<3)MobAbilities.summonId(m,"mite:wood_spider",4);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event){
        if(!(event.getEntity() instanceof Mob m)||m instanceof ExtremeMob)return;String key=id(m);
        if(m instanceof ElementalMaterial material&&material.modernIteMaterial()==2){
            event.getDrops().removeIf(drop->BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).toString().equals("mite:ancient_metal_block"));drop(event,"mite:ancient_metal_ingot",1);
        }
        if(!event.isRecentlyHit())return;
        if(key.equals("mite:revenant")){event.getDrops().removeIf(drop->drop.getItem().is(Items.ROTTEN_FLESH));drop(event,"minecraft:lapis_lazuli",1+m.getRandom().nextInt(3));}
        if(key.equals("mite:infernal_creeper")){drop(event,"minecraft:tnt",1+m.getRandom().nextInt(2));if(m.getRandom().nextInt(100)<5)drop(event,"minecraft:blaze_powder",1);}
        if(key.equals("mite:demon_spider")&&m.getRandom().nextInt(100)<5)drop(event,"minecraft:blaze_powder",1);
        if(key.equals("mite:phase_spider")&&m.getRandom().nextInt(100)<5)drop(event,"minecraft:ender_pearl",1);
        if(key.equals("minecraft:giant"))drop(event,MobAbilities.day(m.level())<128?"mite:ancient_metal_nugget":"mite:mithril_ingot",MobAbilities.day(m.level())<128?4+m.getRandom().nextInt(2):1);
    }
    private static void drop(LivingDropsEvent e,String id,int n){var stack=MobAbilities.item(id);if(stack.isEmpty())return;stack.setCount(n);var m=e.getEntity();e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(m.level(),m.getX(),m.getY(),m.getZ(),stack));}
    @SubscribeEvent public static void effect(MobEffectEvent.Applicable event){
        LivingEntity entity=event.getEntity();
        if(entity instanceof ExtremeMob e&&(e.extremeKind()==ExtremeKind.ANNIHILATION_SKELETON
                ||e.extremeKind()==ExtremeKind.ZOMBIE_BOSS&&event.getEffectInstance()!=null
                    &&!event.getEffectInstance().is(MobEffects.MOVEMENT_SPEED)&&!event.getEffectInstance().is(MobEffects.DAMAGE_BOOST))
                ||entity instanceof Bat&&entity.getPersistentData().hasUUID("modern_ite:summoner"))event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event){
        if(event.getLevel().isClientSide||event.getEntity().isSpectator())return;
        if(event.getTarget() instanceof Bat bat&&event.getItemStack().isEmpty()&&!bat.isVehicle()){event.getEntity().startRiding(bat,true);bat.setResting(false);event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);}
        if(event.getTarget() instanceof IronGolem golem&&golem.getType()==EntityType.IRON_GOLEM&&!golem.getPersistentData().getBoolean("modern_ite:golem_boost")
                &&BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString().equals("mite:mithril_block")){
            golem.getPersistentData().putBoolean("modern_ite:golem_boost",true);stat(golem,Attributes.MAX_HEALTH,golem.getAttributeBaseValue(Attributes.MAX_HEALTH)+20);golem.setHealth(golem.getMaxHealth());golem.refreshDimensions();
            if(!event.getEntity().isCreative())event.getItemStack().shrink(1);event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    @SubscribeEvent public static void size(net.neoforged.neoforge.event.entity.EntityEvent.Size event){
        if(event.getEntity() instanceof IronGolem g&&g.getType()==EntityType.IRON_GOLEM&&g.getPersistentData().getBoolean("modern_ite:golem_boost"))event.setNewSize(EntityDimensions.scalable(1.9F,3.5F));
    }
    @SubscribeEvent public static void witherBlast(ExplosionEvent.Start event){
        if(!(event.getExplosion().getDirectSourceEntity() instanceof WitherBoss wither)||wither.getPersistentData().getBoolean("modern_ite:wither_blast"))return;
        event.setCanceled(true);wither.getPersistentData().putBoolean("modern_ite:wither_blast",true);
        try{wither.level().explode(wither,Explosion.getDefaultDamageSource(wither.level(),wither),new R196ExplosionCalculator(wither,event.getExplosion().radius()*2),wither.getX(),wither.getY()+wither.getEyeHeight(),wither.getZ(),event.getExplosion().radius(),false,Level.ExplosionInteraction.MOB);}
        finally{wither.getPersistentData().remove("modern_ite:wither_blast");}
    }
}
