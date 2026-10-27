package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import io.github.ningmeng123.mite.entity.R196ExplosionCalculator;
import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import static io.github.ningmeng123.modernite.entity.ExtremeKind.*;

/** Literal ability families translated to modern entities, with persistent stage transitions. @author NingMeng123 */
public final class MobAbilities {
    public static final float MIMIC_HEALTH=10F;
    private static final double DARK_GOLEM_LAUNCH_SPEED=15;
    // Rises 15 blocks with vanilla air gravity (.08) and drag (.98F), even when hit while falling.
    private static final double MELEE_LAUNCH_SPEED=1.6929821475097433;
    private MobAbilities(){}
    public static int day(Level level){return level instanceof ServerLevel server?(int)Math.min(Integer.MAX_VALUE,BloodMoonInvasion.highestDay(server)):(int)(level.getDayTime()/24000+1);}
    public static boolean underground(Level level){return level.dimension().location().getPath().equals("underworld");}
    public static boolean maySpawn(ExtremeKind k,ServerLevel l,BlockPos pos){
        if(IteReferenceSpawns.enabled())return SpawnPopulation.automaticAllowed(l,ExtremeMobs.type(k))&&IteReferenceSpawns.naturalAllowed(l,ExtremeMobs.type(k),pos);
        return SpawnPopulation.automaticAllowed(l,ExtremeMobs.type(k))&&customNaturalAllowed(k,l);
    }
    /** Shared habitats for the extra Extreme species in either natural-spawn preset. */
    static boolean customNaturalAllowed(ExtremeKind k,ServerLevel l){
        if(day(l)<IteConfig.extremeMobFirstDay("modern_ite:"+k.id(),l.dimension()==Level.OVERWORLD)
                ||k==MIMIC_ZOMBIE||k==ZOMBIE_BOSS||k.family==Family.PILLAGER)return false;
        boolean nether=l.dimension()==Level.NETHER,under=underground(l),over=l.dimension()==Level.OVERWORLD;
        if(!over&&!nether&&!under)return false;
        if(k==SKELETON_FLOATING)return under;
        if(k==DARK_IRON_GOLEM||k==SKELETON_KNOCKBACK||k==SKELETON_LIGHTNING)return under||nether;
        if(k==WITHER_SKELETON_ARROW)return nether;
        if(k==BLACK_INFERNAL_CREEPER)return over;
        if(k==UNDYING_ZOMBIE_EMPEROR||k==WITCH_LORD||k==TIME_LORD||k==WANDERING_WITCH||k==MINER_ZOMBIE)return over||under;
        if(k==ZOMBIE_PIGMAN_LORD)return nether||under||io.github.ningmeng123.mite.rules.LunarCalendar.state(l.getDayTime(),true).bloodPeriod();
        return true;
    }
    public static boolean spawnRules(Mob mob,ExtremeKind k,LevelAccessor level,MobSpawnType reason){
        if(reason==MobSpawnType.SPAWN_EGG||reason==MobSpawnType.COMMAND||SpawnPopulation.explicit(mob))return true;
        if(!(mob.level() instanceof ServerLevel server)||!SpawnPopulation.automaticAllowed(server,mob.getType()))return false;
        if(reason==MobSpawnType.MOB_SUMMONED||reason==MobSpawnType.CONVERSION)return true;
        if(IteReferenceSpawns.enabled()&&IteReferenceSpawns.referenceIdentity(k))return level instanceof ServerLevelAccessor access
                &&IteReferenceSpawns.variantQualification(mob,k,access,reason);
        if(!maySpawn(k,server,mob.blockPosition())||level.getDifficulty()==Difficulty.PEACEFUL)return false;
        if(k==DARK_IRON_GOLEM&&mob.getRandom().nextInt(9)>=7)return false;
        if(k.family==Family.WITCH&&mob.getRandom().nextInt(100)>=IteConfig.integer("GameMechanics.MobSpawning.wandering_witch_spawn_chance_"+(underground(server)?"underworld":"overworld")))return false;
        if(k==ANNIHILATION_SKELETON&&server.dimension()==Level.OVERWORLD
                &&(mob.getRandom().nextInt(4)!=0||server.getNearestPlayer(mob,24)!=null))return false;
        boolean spawnInLight=k==ANNIHILATION_SKELETON&&server.dimension()==Level.OVERWORLD&&IteConfig.bool("GameMechanics.MobSpawning.annihilation_skeleton_spawn_in_light");
        return (reason!=MobSpawnType.NATURAL||server.dimension()!=Level.OVERWORLD||spawnInLight||level.getBrightness(LightLayer.BLOCK,mob.blockPosition())==0)
                &&(spawnInLight||level.getMaxLocalRawBrightness(mob.blockPosition())<=7)&&level.getBlockState(mob.blockPosition()).getFluidState().isEmpty();
    }
    private static void stat(Mob mob,Holder<net.minecraft.world.entity.ai.attributes.Attribute> key,double value){var a=mob.getAttribute(key);if(a!=null)a.setBaseValue(value);}
    public static void attributes(Mob m,ExtremeKind k){
        if(k.boss())m.getPersistentData().putBoolean("modern_ite:boss",true);
        int raw=day(m.level()),limit=IteConfig.integer("Entities.enhance_limit"),d=Math.min(limit,raw),healthRate=Math.min(d/16,10);
        double x=d/7-7,rate=.5+x/(20+Math.abs(x));
        // The agreed C4 model replaces inherited linear day bonuses. Only species-specific challenge curves remain.
        double health=15,attack=8,speed=k.family==Family.ZOMBIE?.23:k.family==Family.CREEPER?.25:.3;
        switch(k){
            case EXCHANGER -> {health=rate*50+healthRate*5;attack=rate*40;speed=.2572;}
            case ZOMBIE_DOOR -> {x=d/8-8;rate=.5+x/(20+Math.abs(x));health=(rate*60+healthRate*10)*2;attack=rate*50;speed=.3;}
            case MINER_ZOMBIE -> {x=d/8D-8;rate=.5+x/(20+Math.abs(x));health=rate*60+healthRate*10;attack=rate*30;speed=.3;}
            case ZOMBIE_FLY_AWAY -> attack=3;
            case ZOMBIE_JUMP -> health=rate*60+healthRate*15;
            case ZOMBIE_PUMPKIN -> {health=60;attack=8;speed=.23;}
            case ZOMBIE_IRON_BUCKET -> health=60;
            case MIMIC_ZOMBIE -> {health=MIMIC_HEALTH;attack=10;}
            case ZOMBIE_CALLER -> {health=60;speed=.22;}
            case ZOMBIE_BOSS -> {int r=Math.min(limit,raw/10);health=20+r*45;attack=6+r;speed=.3;}
            case ZOMBIE_ELITE_LORD,ZOMBIE_LORD -> {health=rate*60+healthRate*15;attack=12;speed=.3;}
            case UNDYING_ZOMBIE_EMPEROR -> {health=(rate*60+healthRate*15)*2;attack=12;speed=.3;}
            case SKELETON_ELITE_LORD -> {health=rate*90+healthRate*15;attack=12;}
            case MIRROR_ZOMBIE,MIRROR_SKELETON -> attack=rate*40;
            case ANNIHILATION_SKELETON -> {health=30;attack=(13+d/5D)*3;}
            case ZOMBIE_PIGMAN_LORD -> {health=50;attack=12;}
            case WANDERING_WITCH -> {health=100;speed=.3;}
            case WITCH_LORD -> {health=370;speed=.3;}
            case TIME_LORD -> {health=180;attack=12;speed=.28;}
            case DARK_IRON_GOLEM -> {health=(45+raw/6D)*4;attack=(30+raw/20D)*2;}
            case RED_GHOUL -> {health=20;attack=5;speed=.28;}
            case ZOMBIE_KNOCKBACK -> speed=.3;
            case FISHING_ROD_ZOMBIE -> speed=.27;
            case RUN_CREEPER -> {health=20;speed=.5;}
            case BLACK_INFERNAL_CREEPER -> health=(30+raw/8D)*3+100;
            default -> {if(k.family==Family.CREEPER)health=20;}
        }
        stat(m,Attributes.MAX_HEALTH,health);stat(m,Attributes.ATTACK_DAMAGE,attack);stat(m,Attributes.MOVEMENT_SPEED,speed);
        stat(m,Attributes.FOLLOW_RANGE,k==ZOMBIE_CALLER?35:k==RED_GHOUL?40:k==DARK_IRON_GOLEM?25:k==ANNIHILATION_SKELETON?48:64);
        if(k==ANNIHILATION_SKELETON||k==ZOMBIE_BOSS)stat(m,Attributes.KNOCKBACK_RESISTANCE,1);
        m.setHealth(m.getMaxHealth());
    }
    public static ItemStack item(String id){Item i=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));return i==Items.AIR?ItemStack.EMPTY:new ItemStack(i);}
    public static ItemStack firstItem(String...ids){for(String id:ids){var s=item(id);if(!s.isEmpty())return s;}return ItemStack.EMPTY;}
    private static void equip(Mob m,EquipmentSlot slot,String...ids){m.setItemSlot(slot,firstItem(ids));}
    public static void enchant(Mob mob,ItemStack s,String id,int level){
        if(s.isEmpty())return;
        if(s.get(DataComponents.ENCHANTMENTS)==null)s.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        mob.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(ResourceLocation.parse(id)).ifPresent(h->s.enchant(h,level));
    }
    public static int enchantment(ItemStack stack,LivingEntity entity,String suffix){
        return io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments.level(stack,suffix);
    }
    private static void armor(Mob mob,String material,boolean protection){
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        String[] pieces={"helmet","chestplate","leggings","boots"};
        for(int i=0;i<slots.length;i++){
            var s=firstItem("mite:"+material+"_"+pieces[i],"minecraft:iron_"+pieces[i]);
            if(protection)enchant(mob,s,"minecraft:protection",5);mob.setItemSlot(slots[i],s);
        }
    }
    /** Birth locks clear only addon-owned hidden stock, retaining visible custom or picked-up equipment. */
    public static void clearEarlySpawnEquipment(Mob mob){
        if(mob instanceof ExtremeMob extreme)extreme.extremeMemory().spare=ItemStack.EMPTY;
        ExtremeNativeMobs.clearEarlySpawnEquipment(mob);
    }
    public static void initialize(Mob mob,ExtremeMob state){
        var k=state.extremeKind();var s=state.extremeMemory();
        if(k==MIMIC_ZOMBIE){
            // Normalize older saves without healing an injured substitute or retaining old day growth.
            var health=mob.getAttribute(Attributes.MAX_HEALTH);
            if(health!=null&&health.getBaseValue()!=MIMIC_HEALTH){
                health.setBaseValue(MIMIC_HEALTH);health.removeModifier(io.github.ningmeng123.mite.entity.MonsterDayScaling.HEALTH_MODIFIER);
                mob.setHealth(Math.min(mob.getHealth(),MIMIC_HEALTH));
            }
        }
        if(s.initialized){upgradeSavedCombat(mob,k);return;}s.initialized=true;
        s.phaseMaximum=s.phaseCharges=mob.getRandom().nextInt(3)+2;
        if(mob instanceof Zombie zombie)zombie.setCanBreakDoors(true);
        boolean fixedBossGear=k==ZOMBIE_LORD||k==ZOMBIE_ELITE_LORD||k==SKELETON_ELITE_LORD||k==TIME_LORD||k==ZOMBIE_BOSS||k==UNDYING_ZOMBIE_EMPEROR||k==ZOMBIE_PIGMAN_LORD;
        if(!BloodMoonEquipment.spawnEquipmentLocked(mob)){
        if(k.family==Family.SKELETON)equip(mob,EquipmentSlot.MAINHAND,"minecraft:bow");
        if(k.family==Family.PILLAGER)equip(mob,EquipmentSlot.MAINHAND,"modern_ite:crossbow","minecraft:crossbow");
        if(fixedBossGear){
            if(k==TIME_LORD)TimeLordEquipment.initialize(mob);
            else {
            armor(mob,"vibranium",k==UNDYING_ZOMBIE_EMPEROR);
            equip(mob,EquipmentSlot.MAINHAND,k==ZOMBIE_BOSS||k==ZOMBIE_ELITE_LORD?"mite:vibranium_war_hammer":"mite:vibranium_sword","minecraft:iron_sword");
            }
        }
        switch(k){
            case TIME_LORD -> {equip(mob,EquipmentSlot.OFFHAND,"minecraft:clock");s.cooldown=80;}
            case ZOMBIE_DOOR -> equip(mob,EquipmentSlot.MAINHAND,"minecraft:iron_door");
            case MINER_ZOMBIE -> {equip(mob,EquipmentSlot.MAINHAND,"mite:vibranium_pickaxe","minecraft:iron_pickaxe");enchant(mob,mob.getMainHandItem(),"minecraft:efficiency",10);}
            case ZOMBIE_IRON_BUCKET -> equip(mob,EquipmentSlot.HEAD,"modern_ite:iron_bucket_helmet","minecraft:iron_helmet");
            case SKELETON_KNOCKBACK -> equip(mob,EquipmentSlot.MAINHAND,"minecraft:wooden_sword");
            case EXCHANGER -> equip(mob,EquipmentSlot.MAINHAND,"minecraft:ender_pearl");
            case SKELETON_ELITE_LORD -> equip(mob,EquipmentSlot.MAINHAND,"mite:vibranium_sword","minecraft:iron_sword");
            case SKELETON_BLAZE -> enchant(mob,mob.getMainHandItem(),"minecraft:flame",2);
            case WITHER_SKELETON_ARROW -> {equip(mob,EquipmentSlot.MAINHAND,"mite:ancient_metal_bow","minecraft:bow");enchant(mob,mob.getMainHandItem(),"minecraft:punch",2);}
            case SKELETON_ZOMBIE -> {s.spare=firstItem("mite:rusted_iron_sword","minecraft:iron_sword");armor(mob,tier(day(mob.level())),false);}
            case FISHING_ROD_ZOMBIE -> mob.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            case RANDOM_ZOMBIE -> randomEquipment(mob);
            case UNDYING_ZOMBIE_EMPEROR -> {equip(mob,EquipmentSlot.MAINHAND,"modern_ite:club_vibranium","mite:vibranium_sword");enchant(mob,mob.getMainHandItem(),"minecraft:sharpness",5);}
            case ANNIHILATION_SKELETON -> {armor(mob,"adamantium",false);equip(mob,EquipmentSlot.MAINHAND,"mite:vibranium_dagger","minecraft:iron_sword");enchant(mob,mob.getMainHandItem(),"minecraft:knockback",5);}
            case ZOMBIE_PUMPKIN -> equip(mob,EquipmentSlot.HEAD,"minecraft:carved_pumpkin");
            case MIMIC_ZOMBIE,ZOMBIE_CHAOS,ZOMBIE_INVISIBLE_LOOK_UP -> {for(var slot:EquipmentSlot.values())if(slot!=EquipmentSlot.BODY)mob.setItemSlot(slot,ItemStack.EMPTY);}
            default -> {}
        }
        }
        if(fixedBossGear){mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,Integer.MAX_VALUE,0));mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,Integer.MAX_VALUE,0));}
        if(k.mirrors()||k==MIMIC_ZOMBIE||k==UNDYING_ZOMBIE_EMPEROR||k==ANNIHILATION_SKELETON)for(var slot:EquipmentSlot.values())mob.setDropChance(slot,0);
        if(k==ZOMBIE_FLY_AWAY||k==ZOMBIE_KNOCKBACK||k==ZOMBIE_JUMP||k==ZOMBIE_DOOR||k==DARK_IRON_GOLEM)mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,Integer.MAX_VALUE,k==ZOMBIE_FLY_AWAY||k==DARK_IRON_GOLEM?1:0));
        if(k==INVISIBLE_CREEPER||k==INVISIBLE_REVERSED_CREEPER)mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,Integer.MAX_VALUE,0,false,false));
        configureGoals(mob,k);
        if(k==MIMIC_ZOMBIE||k==ZOMBIE_BOSS||k.family==Family.PILLAGER)mob.setPersistenceRequired();
        mob.getPersistentData().putInt("modern_ite:combat_revision",1);
    }
    private static void upgradeSavedCombat(Mob mob,ExtremeKind kind){
        var data=mob.getPersistentData();if(data.getInt("modern_ite:combat_revision")>=1)return;
        if(kind==FISHING_ROD_ZOMBIE&&!BloodMoonEquipment.spawnEquipmentLocked(mob)&&mob.getMainHandItem().getItem() instanceof FishingRodItem)mob.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        if(kind==UNDYING_ZOMBIE_EMPEROR&&!BloodMoonEquipment.spawnEquipmentLocked(mob)){
            EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
            String[] pieces={"helmet","chestplate","leggings","boots"};
            for(int i=0;i<slots.length;i++){
                ItemStack armor=mob.getItemBySlot(slots[i]);
                if(armor.isEmpty()){armor=firstItem("mite:vibranium_"+pieces[i],"minecraft:iron_"+pieces[i]);mob.setItemSlot(slots[i],armor);}
                enchant(mob,armor,"minecraft:protection",5);
            }
        }
        if(kind==DARK_IRON_GOLEM)stat(mob,Attributes.ATTACK_DAMAGE,(30+day(mob.level())/20D)*2);
        if(kind==ZOMBIE_LORD){mob.setNoAi(false);mob.setInvulnerable(false);configureGoals(mob,kind);}
        data.putInt("modern_ite:combat_revision",1);
    }
    private static String tier(int d){return d>=320?"vibranium":d>=192?"adamantium":d>=128?"mithril":d>=64?"ancient_metal":"iron";}
    private static void randomEquipment(Mob mob){
        String[] materials={"copper","iron","ancient_metal","mithril","adamantium","vibranium"};
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND};
        String[] pieces={"helmet","chestplate","leggings","boots","sword"};
        for(int i=0;i<slots.length;i++){
            // Each slot rolls its own presence and material; an unrolled slot stays empty.
            ItemStack stack=ItemStack.EMPTY;
            if(mob.getRandom().nextFloat()<(slots[i]==EquipmentSlot.MAINHAND?.7F:.5F)){
                String material=materials[mob.getRandom().nextInt(materials.length)];
                stack=firstItem("mite:"+material+"_"+pieces[i],"minecraft:iron_"+pieces[i]);
                if(stack.isDamageableItem())stack.setDamageValue(mob.getRandom().nextInt(Math.max(1,stack.getMaxDamage()/2)));
            }
            mob.setItemSlot(slots[i],stack);
        }
    }
    private static void removeGoals(GoalSelector selector,java.util.function.Predicate<Goal> predicate){
        // removeAllGoals only removes entries; removeGoal also stops them and releases item use and control locks.
        selector.getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(predicate).toList().forEach(selector::removeGoal);
    }
    public static void configureGoals(Mob mob,ExtremeKind k){
        if(mob instanceof Zombie zombie){
            if(io.github.ningmeng123.mite.entity.R196DiggerGoal.first(zombie)==null)
                zombie.goalSelector.addGoal(1,new io.github.ningmeng123.mite.entity.R196DiggerGoal(zombie));
            removeGoals(zombie.goalSelector,g->g instanceof MeleeAttackGoal);
            if(SourceMeleeGoal.first(zombie)==null)zombie.goalSelector.addGoal(2,new SourceMeleeGoal(zombie,1,true));
        }
        if(mob instanceof AbstractSkeleton skeleton){
            boolean bow=mob.getMainHandItem().getItem() instanceof BowItem;
            removeGoals(skeleton.goalSelector,g->g instanceof RangedBowAttackGoal<?>||g instanceof RangedAttackGoal||g instanceof MeleeAttackGoal
                ||bow&&g instanceof SourceMeleeGoal||!bow&&g instanceof SourceRangedAttackGoal);
            if(bow){
                if(skeleton.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof SourceRangedAttackGoal))
                    skeleton.goalSelector.addGoal(4,new SourceRangedAttackGoal(skeleton,1,k==SKELETON_FAST_ARROW?30:60,15));
            }else if(SourceMeleeGoal.first(skeleton)==null)skeleton.goalSelector.addGoal(4,new SourceMeleeGoal(skeleton,1.15,true));
        }
        if(mob instanceof Witch witch){
            removeGoals(witch.goalSelector,g->g instanceof RangedAttackGoal||g instanceof SourceRangedAttackGoal);
            witch.goalSelector.addGoal(2,new SourceRangedAttackGoal(witch,1,k==WITCH_LORD?25:50,16));
            witch.goalSelector.addGoal(1,new AvoidEntityGoal<>(witch,Player.class,3,1.1,1.4));
        }
        if(mob instanceof Ghast ghast){
            removeGoals(ghast.goalSelector,g->g.getClass().getEnclosingClass()==Ghast.class&&g.getFlags().isEmpty());
            ghast.targetSelector.addGoal(1,new NearestAttackableTargetGoal<>(ghast,Player.class,true));
        }
        if(k==DARK_IRON_GOLEM){
            removeGoals(mob.targetSelector,g->true);mob.targetSelector.addGoal(1,new NearestAttackableTargetGoal<>(mob,Player.class,true));
            mob.targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(mob,net.minecraft.world.entity.npc.Villager.class,false));
            mob.targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(mob,Animal.class,true));
        }
        if(k==RED_GHOUL)mob.targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(mob,Animal.class,true));
    }
    public static void effect(LivingEntity target,String key,int ticks,int amplifier){
        var id=ResourceLocation.parse(key.contains(":")?key:"modern_ite:"+key);
        BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(h->target.addEffect(new MobEffectInstance(h,ticks,amplifier)));
    }
    public static void tick(Mob mob,ExtremeMob state){
        var k=state.extremeKind();var s=state.extremeMemory();initialize(mob,state);s.clock++;
        if(s.cooldown>0&&k!=ZOMBIE_BOSS)s.cooldown--;if(s.fleeingPotion>0)s.fleeingPotion--;
        if(s.supportCooldown>0)s.supportCooldown--;
        if(!(mob.level() instanceof ServerLevel level))return;
        if(k==ZOMBIE_DOOR&&mob instanceof ExtremeMobs.ZombieVariant zombie)DoorGuard.tick(zombie);
        LivingEntity target=mob.getTarget();
        if(k==ZOMBIE_SIEGE)for(var passenger:mob.getPassengers())if(passenger instanceof Zombie rider){rider.getPersistentData().putBoolean("modern_ite:siege_rider",true);if(!rider.isBaby())rider.setBaby(true);}
        if(!s.entourage){
            s.entourage=true;
            if(k==UNDYING_ZOMBIE_EMPEROR){summon(mob,ZOMBIE_LORD,2);summon(mob,WANDERING_WITCH,2);}
            if(k==ZOMBIE_SIEGE){
                Mob rider=summonId(mob,"minecraft:zombie",1);
                if(rider instanceof Zombie baby){baby.getPersistentData().putBoolean("modern_ite:siege_rider",true);baby.setBaby(true);baby.moveTo(mob.position());baby.startRiding(mob,true);}
            }
        }
        if(k==PHASE_CREEPER){if(s.clock%100==0&&s.phaseCharges<s.phaseMaximum)s.phaseCharges++;if(target!=null&&s.clock%10==0&&mob.getRandom().nextInt(3)==0&&mob.distanceToSqr(target)>9)teleportNear(mob,target);}
        if(k==ANNIHILATION_SKELETON&&s.stage>0&&++s.summons>=IteConfig.integer("Entities.annihilation_skeleton_despawn_time")){mob.spawnAnim();mob.discard();return;}
        if(IteReferenceSpawns.enabled()&&k==WANDERING_WITCH)referenceWanderingTick(mob,s);
        if(target==null||!target.isAlive()){
            if(k==ZOMBIE_BOSS&&!s.contribution.isEmpty()){mob.setHealth(mob.getMaxHealth());announce(mob,"僵尸BOSS挑战失败");s.contribution.clear();}return;
        }
        if(k==TIME_LORD)TimeLordAbilities.tick(mob,s,target);
        if(k==EXCHANGER&&target instanceof Player player){
            if(mob.distanceToSqr(target)>1024){mob.setTarget(null);s.stage=0;return;}
            if(s.stage==0){player.displayClientMessage(Component.literal("[转移骷髅] 你已被注视，3秒后转移"),true);s.stage=1;s.cooldown=61;}
            else if(s.stage==1&&s.cooldown==0){boolean fixed=false;for(var worn:player.getArmorSlots())fixed|=enchantment(worn,player,"fixed")>0;
                if(!fixed){Vec3 at=mob.position();mob.teleportTo(player.getX(),player.getY(),player.getZ());player.teleportTo(at.x,at.y,at.z);}s.stage=2;}
        }
        if(k.mirrors()&&target instanceof Player&&s.clock%20==0)mirror(mob,target,s);
        if(k==DRAGON_ZOMBIE&&s.clock%20==0)for(var zombie:level.getEntitiesOfClass(Zombie.class,mob.getBoundingBox().inflate(16,8,16),z->z!=mob)){zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,40));zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,40));}
        if(k==DRAGON_ZOMBIE&&s.cooldown==0&&mob.distanceToSqr(target)<=36&&mob.getSensing().hasLineOfSight(target)){dragonBreath(mob,target);s.cooldown=100;}
        if(k==FISHING_ROD_ZOMBIE&&s.clock%20==0&&mob.distanceToSqr(target)<=225&&mob.getSensing().hasLineOfSight(target))effect(target,"big_head",100,0);
        if(k==ZOMBIE_GHAST&&mob.distanceToSqr(target)<=4096&&mob.getSensing().hasLineOfSight(target)&&s.cooldown==0){mob.playSound(SoundEvents.GHAST_WARN,1.5F,1F);mob.playSound(SoundEvents.GHAST_SHOOT,1F,1F);for(int i=0;i<(level.dimension()==Level.NETHER?5:1);i++)InfernalFireball.shoot(mob,target,false,level.dimension()==Level.NETHER?1.5:0);s.cooldown=60;}
        if(k==ZOMBIE_DOOR){
            if(!s.exchangerTried){s.exchangerTried=true;if(mob.getRandom().nextInt(10)==0)summon(mob,EXCHANGER,1);}
            if(s.cooldown==0&&s.summons<10){summonZombie(mob,1);s.summons++;s.cooldown=200;}
        }
        if(k==ZOMBIE_ELITE_LORD&&s.cooldown==0&&s.summons<24){
            if(mob.getRandom().nextInt(4)==0)summon(mob,MINER_ZOMBIE,1);
            else summonId(mob,new String[]{"mite:ghoul","mite:wight","mite:revenant"}[mob.getRandom().nextInt(3)],1);
            s.summons++;s.cooldown=51;
        }
        if(k==SKELETON_ELITE_LORD&&s.cooldown==0&&s.summons<6){summonId(mob,"minecraft:skeleton",1);s.summons++;s.cooldown=100;}
        if(k==WITCH_LORD&&target instanceof Player&&s.summons<2&&s.cooldown==0
                &&level.getEntitiesOfClass(ExtremeMobs.WitchVariant.class,mob.getBoundingBox().inflate(16,8,16),w->w.extremeKind()==WANDERING_WITCH).size()<2){
            if(summon(mob,WANDERING_WITCH,1)!=null)s.summons++;s.cooldown=20;
        }
        if(k==ZOMBIE_CALLER){
            if(s.stage==0&&underground(level))startCaller(mob,s);
            if(s.stage==1&&s.clock>=200){summonZombie(mob,3);s.stage=2;}
            if(s.stage==2&&s.clock>=400){level.explode(mob,Explosion.getDefaultDamageSource(level,mob),new R196ExplosionCalculator(mob,3.3F),mob.getX(),mob.getY()+mob.getBbHeight()/4,mob.getZ(),3F,false,Level.ExplosionInteraction.MOB);summonId(mob,"minecraft:silverfish",7+mob.getRandom().nextInt(3));mob.discard();}
        }
        if(k==ZOMBIE_SIEGE&&s.cooldown==0&&mob.getFirstPassenger()!=null){Entity baby=mob.getFirstPassenger();baby.stopRiding();Vec3 launch=target.position().subtract(mob.position()).normalize().scale(1.5);baby.setDeltaMovement(launch.x,.8,launch.z);baby.hurtMarked=true;s.cooldown=60;}
        if(k==SKELETON_ZOMBIE&&!BloodMoonEquipment.spawnEquipmentLocked(mob)&&!s.spare.isEmpty()&&s.clock%10==0){boolean bow=mob.getMainHandItem().getItem() instanceof BowItem;if(bow&&mob.distanceToSqr(target)<25||!bow&&mob.distanceToSqr(target)>36){var held=mob.getMainHandItem();mob.setItemSlot(EquipmentSlot.MAINHAND,s.spare);s.spare=held;configureGoals(mob,k);}}
        if(k.family==Family.WITCH&&(!IteReferenceSpawns.enabled()||k!=WANDERING_WITCH))witchTick(mob,target,s);
        if(k==ZOMBIE_BOSS&&target instanceof Player&&mob.distanceToSqr(target)>2.4*2.4){
            if(s.cooldown>0)s.cooldown--;else if(mob.getHealth()<mob.getMaxHealth()){mob.setHealth(mob.getMaxHealth());announce(mob,"僵尸BOSS挑战失败");s.contribution.clear();}
            if(++s.summons%20==0&&Math.abs(target.getX()-mob.getX())<=5&&Math.abs(target.getZ()-mob.getZ())<=5
                    &&target.getPersistentData().getLong("modern_ite:boss_melee_grace")<level.getGameTime()){
                LightningBolt bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt!=null){bolt.setVisualOnly(true);bolt.moveTo(target.position());level.addFreshEntity(bolt);}target.hurt(mob.damageSources().magic(),10);
            }
            if(s.summons>=60){
                // A remembered opponent may stay hidden; an unfamiliar player must first be seen.
                var nearby=level.getEntitiesOfClass(Player.class,mob.getBoundingBox().inflate(16),
                        p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()
                                &&(p==target||SourceMonsterTargeting.canSee(mob,p,false)));
                if(!nearby.isEmpty())mob.setTarget(nearby.get(mob.getRandom().nextInt(nearby.size())));
                s.summons=0;
            }
        }
    }
    private static void mirror(Mob mob,LivingEntity player,MobMemory s){
        if(!s.mirrored){if(player.getMaxHealth()>(MobSplitting.isChild(mob)?mob.getMaxHealth()*4F:mob.getMaxHealth())){stat(mob,Attributes.MAX_HEALTH,player.getMaxHealth());mob.setHealth(mob.getMaxHealth());}s.mirrored=true;}
        if(!BloodMoonEquipment.spawnEquipmentLocked(mob))for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND}){if(!ItemStack.isSameItemSameComponents(mob.getItemBySlot(slot),player.getItemBySlot(slot)))mob.setItemSlot(slot,player.getItemBySlot(slot).copy());mob.setDropChance(slot,0);}
        for(var p:player.getActiveEffects())mob.addEffect(new MobEffectInstance(p));configureGoals(mob,((ExtremeMob)mob).extremeKind());
    }
    public static void startCaller(Mob mob,MobMemory s){if(s.stage!=0)return;s.stage=1;s.clock=0;summonZombie(mob,4);}
    public static void dragonBreath(Mob mob,LivingEntity target){
        if(!(mob.level() instanceof ServerLevel level))return;
        Vec3 mouth=mob.getEyePosition(),end=target.position();
        Vec3 delta=end.subtract(mouth);
        // A short visible spray ends in a dragon cloud whose radius never grows past 1.5 blocks.
        for(int i=0;i<=12;i++){
            Vec3 point=mouth.add(delta.scale(i/12D));
            level.sendParticles(ParticleTypes.DRAGON_BREATH,point.x,point.y,point.z,3,.08,.08,.08,.01);
        }
        AreaEffectCloud cloud=new AreaEffectCloud(level,end.x,end.y+.05,end.z);
        cloud.setOwner(mob);cloud.setParticle(ParticleTypes.DRAGON_BREATH);cloud.setRadius(1.5F);
        cloud.setRadiusPerTick(0);cloud.setRadiusOnUse(0);cloud.setWaitTime(10);cloud.setDuration(80);
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM,1,1));level.addFreshEntity(cloud);
        mob.playSound(SoundEvents.ENDER_DRAGON_SHOOT,1,.85F);
    }
    public static void witchSupportTick(Mob witch){
        if(IteReferenceSpawns.enabled()&&!(witch instanceof ExtremeMob)){referenceWolfTick(witch);return;}
        if(!(witch.level() instanceof ServerLevel level)||!(witch.getTarget() instanceof Player target)
                ||!target.isAlive()||witch.distanceToSqr(target)>1024)return;
        long now=level.getGameTime();var data=witch.getPersistentData();
        if(data.getLong("modern_ite:witch_next_summon")>now)return;
        data.putLong("modern_ite:witch_next_summon",now+160);
        var minions=level.getEntitiesOfClass(Mob.class,witch.getBoundingBox().inflate(48,24,48),m->m.isAlive()
            &&m.getPersistentData().hasUUID("modern_ite:summoner")&&m.getPersistentData().getUUID("modern_ite:summoner").equals(witch.getUUID()));
        int room=8-minions.size();
        for(int i=0;i<Math.min(room,4);i++){
            String type=i%2==0?"mite:vampire_bat":witch.getRandom().nextBoolean()?"minecraft:spider":"minecraft:cave_spider";
            summonId(witch,type,1);
        }
        for(var minion:minions)if(minion.getTarget()!=target)minion.setTarget(target);
    }
    private static boolean validWitchTarget(Mob witch,Player target){
        return target!=null&&target.isAlive()&&!target.isSpectator()&&!target.isCreative()&&target.level()==witch.level();
    }
    /** R196 tv: one successful wolf call, sixty ticks after a player harms the witch. */
    private static void referenceWolfTick(Mob witch){
        if(!(witch.level() instanceof ServerLevel level))return;
        var data=witch.getPersistentData();
        if(data.getBoolean("modern_ite:reference_wolves_called"))return;
        int countdown=data.getInt("modern_ite:reference_wolf_countdown");
        if(countdown>0){
            data.putInt("modern_ite:reference_wolf_countdown",--countdown);
            if(countdown!=0)return;
            Player target=data.hasUUID("modern_ite:reference_wolf_target")?level.getPlayerByUUID(data.getUUID("modern_ite:reference_wolf_target")):null;
            if(validWitchTarget(witch,target)&&summonReferenceWolves(witch,target)>0)data.putBoolean("modern_ite:reference_wolves_called",true);
        }else if(witch.getLastHurtByMob() instanceof Player player&&validWitchTarget(witch,player)){
            data.putUUID("modern_ite:reference_wolf_target",player.getUUID());data.putInt("modern_ite:reference_wolf_countdown",60);
        }
    }
    private static int summonReferenceWolves(Mob witch,Player target){
        ServerLevel level=(ServerLevel)witch.level();int count=0,max=1+witch.getRandom().nextInt(3);
        for(int attempt=0;attempt<16&&count<max;attempt++){
            var wolf=EntityType.WOLF.create(level);if(wolf==null)break;
            boolean placed=false;
            // WorldServer.tryCreateNewLivingEntityCloseTo searches sixteen positions 8--16 blocks from the victim.
            for(int placement=0;placement<16;placement++){
                BlockPos pos=target.blockPosition().offset(witch.getRandom().nextInt(33)-16,witch.getRandom().nextInt(7)-3,witch.getRandom().nextInt(33)-16);
                for(int down=0;down<8&&level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty();down++)pos=pos.below();
                for(int up=0;up<8&&!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty();up++)pos=pos.above();
                double distance=pos.distSqr(target.blockPosition());if(distance<64||distance>256)continue;
                Double y=io.github.ningmeng123.mite.worldgen.R196MobSpawning.positionY(level,pos,io.github.ningmeng123.mite.rules.R196SpawnRules.Category.ANIMAL);
                if(y==null)continue;
                wolf.moveTo(pos.getX()+.5,y,pos.getZ()+.5,witch.getYRot(),0);
                if(io.github.ningmeng123.mite.worldgen.R196SpawnQualification.qualifies(wolf,level,false,false)){placed=true;break;}
            }
            if(!placed){wolf.discard();continue;}
            // Placement has confirmed a supporting surface; a fresh entity has not ticked its ground state yet.
            wolf.setOnGround(true);
            var path=wolf.getNavigation().createPath(target,0);
            if(path==null||path.getEndNode()==null||path.getEndNode().asBlockPos().distSqr(target.blockPosition())>2){wolf.discard();continue;}
            if(!SpawnPopulation.reserveSupport(wolf,witch,false)){wolf.discard();continue;}
            try{
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(wolf,level,level.getCurrentDifficultyAt(wolf.blockPosition()),MobSpawnType.MOB_SUMMONED,null);
                if(wolf.isSpawnCancelled()||wolf.isRemoved())continue;
                wolf.getPersistentData().putBoolean("mite_wolf_witch_ally_032",true);
                wolf.getPersistentData().putBoolean(io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032.HOSTILE,true);
                wolf.setTarget(target);wolf.setPersistentAngerTarget(target.getUUID());wolf.startPersistentAngerTimer();
                if(level.addFreshEntity(wolf)){wolf.spawnAnim();count++;}
            }finally{if(!wolf.isAddedToLevel()){wolf.discard();SpawnPopulation.release(wolf);}}
        }
        return count;
    }
    /** ITE wandering witches call eight bats only after retaliation or the close-range escape action. */
    private static void referenceWanderingTick(Mob witch,MobMemory memory){
        ServerLevel level=(ServerLevel)witch.level();var data=witch.getPersistentData();
        int countdown=data.getInt("modern_ite:reference_bat_countdown");
        if(countdown>0){
            data.putInt("modern_ite:reference_bat_countdown",--countdown);
            if(countdown==0)referenceWanderingBats(witch);
        }else if(witch.getLastHurtByMob() instanceof Player player&&validWitchTarget(witch,player)){
            data.putUUID("modern_ite:reference_bat_target",player.getUUID());data.putInt("modern_ite:reference_bat_countdown",160);
        }
        Player close=level.getNearestPlayer(witch,4);
        if(validWitchTarget(witch,close)&&memory.fleeingPotion==0){
            close.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20+witch.getRandom().nextInt(30)));
            throwPotion(witch,close);referenceWanderingBats(witch);memory.fleeingPotion=80;
        }
        Player near=level.getNearestPlayer(witch,32);
        if(!memory.cursed&&near instanceof ServerPlayer player&&validWitchTarget(witch,player)&&player.hasLineOfSight(witch)){
            if(witch instanceof Witch source)io.github.ningmeng123.modernite.content.DwarfCurse.scheduleSelected(player,source,
                    io.github.ningmeng123.mite.curse.WitchCursing.selectCurse(io.github.ningmeng123.mite.curse.WitchCursing.seed(source),player.getGameProfile().getName()),0);
            memory.cursed=true;
        }
    }
    private static void referenceWanderingBats(Mob witch){
        ServerLevel level=(ServerLevel)witch.level();var data=witch.getPersistentData();
        Player target=data.hasUUID("modern_ite:reference_bat_target")?level.getPlayerByUUID(data.getUUID("modern_ite:reference_bat_target")):null;
        if(!validWitchTarget(witch,target))return;
        int room=8-SpawnPopulation.supportCount(witch);
        for(int i=0;i<room;i++)summonReferenceBat(witch,target,witch.getRandom().nextInt(10)>8?"mite:nightwing":"mite:giant_vampire_bat");
    }
    private static void summonReferenceBat(Mob witch,Player target,String id){
        ServerLevel level=(ServerLevel)witch.level();Entity entity=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)).create(level);
        if(!(entity instanceof Mob bat))return;
        bat.moveTo(Math.floor((target.getX()+witch.getX())/2)+.5,Math.floor((target.getY()+witch.getY())/2),Math.floor((target.getZ()+witch.getZ())/2)+.5,witch.getYRot(),0);
        if(!level.noCollision(bat)||!SpawnPopulation.reserveSupport(bat,witch,false)){bat.discard();return;}
        try{
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(bat,level,level.getCurrentDifficultyAt(bat.blockPosition()),MobSpawnType.MOB_SUMMONED,null);
            if(bat.isRemoved()||bat.isSpawnCancelled())return;
            bat.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,Integer.MAX_VALUE,1));bat.setTarget(target);
            if(level.addFreshEntity(bat))bat.spawnAnim();
        }finally{if(!bat.isAddedToLevel()){bat.discard();SpawnPopulation.release(bat);}}
    }
    private static void witchTick(Mob mob,LivingEntity target,MobMemory s){
        if(mob.distanceToSqr(target)<16&&s.fleeingPotion==0){target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20+mob.getRandom().nextInt(30)));throwPotion(mob,target);s.fleeingPotion=80;}
        witchSupportTick(mob);
        if(!s.cursed&&target instanceof ServerPlayer player&&mob.distanceToSqr(player)<1024&&player.hasLineOfSight(mob)){
            if(mob instanceof Witch witch){
                io.github.ningmeng123.modernite.content.DwarfCurse.scheduleSelected(player,witch,
                    io.github.ningmeng123.mite.curse.WitchCursing.selectCurse(io.github.ningmeng123.mite.curse.WitchCursing.seed(witch),player.getGameProfile().getName()),0);
            }
            s.cursed=true;
        }
    }
    public static void throwPotion(Mob witch,LivingEntity target){
        if(!(witch.level() instanceof ServerLevel level))return;
        var effects=new ArrayList<MobEffectInstance>();
        if(witch.distanceToSqr(target)>=100&&!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))effects.add(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,1800));
        else if(witch.getRandom().nextFloat()<.3)effects.add(new MobEffectInstance(MobEffects.HARM,1));
        else if(!target.hasEffect(MobEffects.POISON))effects.add(new MobEffectInstance(MobEffects.POISON,900));
        else if(!target.hasEffect(MobEffects.WEAKNESS))effects.add(new MobEffectInstance(MobEffects.WEAKNESS,1800));else effects.add(new MobEffectInstance(MobEffects.HARM,1));
        var primary=effects.getFirst().getEffect();
        if(!primary.equals(MobEffects.HARM)&&witch.getRandom().nextFloat()<.6)effects.add(new MobEffectInstance(MobEffects.HARM,1));
        if(!primary.equals(MobEffects.POISON)&&!target.hasEffect(MobEffects.POISON)&&witch.getRandom().nextFloat()<.5)effects.add(new MobEffectInstance(MobEffects.POISON,600));
        if(!primary.equals(MobEffects.WEAKNESS)&&!target.hasEffect(MobEffects.WEAKNESS)&&witch.getRandom().nextFloat()<.4)effects.add(new MobEffectInstance(MobEffects.WEAKNESS,1200));
        if(!primary.equals(MobEffects.MOVEMENT_SLOWDOWN)&&!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&witch.getRandom().nextFloat()<.3)effects.add(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,1200));
        var stack=new ItemStack(Items.SPLASH_POTION);stack.set(DataComponents.POTION_CONTENTS,new PotionContents(Optional.empty(),Optional.empty(),effects));
        ThrownPotion potion=new ThrownPotion(level,witch);potion.setItem(stack);
        Vec3 delta=target.getEyePosition().add(target.getDeltaMovement().scale(witch.distanceTo(target)*(.5+witch.getRandom().nextFloat()))).subtract(potion.position());
        potion.shoot(delta.x,delta.y+Math.sqrt(delta.horizontalDistanceSqr())*.2,delta.z,.9F,8);level.addFreshEntity(potion);
    }
    public static Mob summon(Mob owner,ExtremeKind kind,int count){return summon(owner,kind,count,false);}
    public static Mob summon(Mob owner,ExtremeKind kind,int count,boolean encounter){return summonType(owner,ExtremeMobs.type(kind),count,encounter);}
    public static Mob summonId(Mob owner,String id,int count){return summonId(owner,id,count,false);}
    public static Mob summonId(Mob owner,String id,int count,boolean encounter){var t=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));return summonType(owner,t,count,encounter);}
    private static void summonZombie(Mob owner,int count){summonId(owner,"minecraft:zombie",count);}
    public static Mob summonType(Mob owner,EntityType<?> type,int count){return summonType(owner,type,count,false);}
    public static Mob summonType(Mob owner,EntityType<?> type,int count,boolean encounter){
        return summonType(owner,type,count,encounter,mob->{});
    }
    static Mob summonType(Mob owner,EntityType<?> type,int count,boolean encounter,java.util.function.Consumer<Mob> prepare){
        if(!(owner.level() instanceof ServerLevel level))return null;Mob last=null;
        for(int i=0;i<count;i++){
            Entity e=type.create(level);if(!(e instanceof Mob mob))continue;boolean valid=false;
            prepare.accept(mob);
            for(int attempt=0;attempt<12;attempt++){BlockPos p=owner.blockPosition().offset(owner.getRandom().nextInt(7)-3,0,owner.getRandom().nextInt(7)-3);mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,owner.getYRot(),0);if(level.noCollision(mob)){valid=true;break;}}
            if(!valid||!SpawnPopulation.reserveSupport(mob,owner,encounter)){mob.discard();continue;}
            Set<UUID> existing=summonBaseline(level,owner);Set<Entity> owned=new LinkedHashSet<>();owned.add(mob);
            try{
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.MOB_SUMMONED,null);
                rememberSummonTree(mob,existing,owned);
                if(mob.isRemoved()||mob.isSpawnCancelled()||mob.getSpawnType()==null)continue;
                mob.setTarget(owner.getTarget());
                if(owner instanceof ExtremeMob ex&&ex.extremeKind().family==Family.WITCH)mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,Integer.MAX_VALUE,1));
                if(level.addFreshEntity(mob)){mob.spawnAnim();last=mob;}
            }finally{
                if(!mob.isAddedToLevel())discardSummonTree(mob,existing,owned);
            }
        }return last;
    }
    /** Protect pre-existing riders and the summoner even when a section is not query-visible yet. */
    static Set<UUID> summonBaseline(ServerLevel level,Mob owner){
        Set<UUID> existing=new HashSet<>();
        for(Entity entity:level.getAllEntities())existing.add(entity.getUUID());
        if(owner!=null)owner.getRootVehicle().getSelfAndPassengers().forEach(entity->existing.add(entity.getUUID()));
        return existing;
    }
    static void rememberSummonTree(Mob root,Set<UUID> existing,Set<Entity> owned){
        Entity vehicle=root.getRootVehicle();
        // A newly summoned creeper can itself become the passenger of its freshly summoned bat.
        boolean ownVehicle=vehicle!=root&&!existing.contains(vehicle.getUUID())
                &&vehicle.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                &&root.getUUID().equals(vehicle.getPersistentData().getUUID(SpawnPopulation.SUMMONER));
        (ownVehicle?vehicle:root).getSelfAndPassengers().forEach(entity->{
            if(entity==root||!existing.contains(entity.getUUID()))owned.add(entity);
        });
    }
    static void discardSummonTree(Mob root,Set<UUID> existing,Set<Entity> owned){
        rememberSummonTree(root,existing,owned);
        // Keep the snapshot taken before the join: a later listener may already have detached riders.
        for(Entity entity:owned)if(entity!=root){entity.discard();if(entity instanceof Mob mob)SpawnPopulation.release(mob);}
        root.discard();SpawnPopulation.release(root);
    }
    public static boolean evade(Mob mob,MobMemory state,DamageSource source){
        if(state.phaseCharges<=0||source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_FALL)||source.is(DamageTypes.MAGIC))return false;
        Entity threat=source.getEntity();for(int i=0;i<64;i++){
            double x=mob.getX()+mob.getRandom().nextInt(11)-5,z=mob.getZ()+mob.getRandom().nextInt(11)-5;
            if(threat!=null&&(x-threat.getX())*(x-threat.getX())+(z-threat.getZ())*(z-threat.getZ())<9)continue;
            if(teleport(mob,x,mob.getY()+mob.getRandom().nextInt(9)-4,z)){state.phaseCharges--;return true;}
        }return false;
    }
    private static void teleportNear(Mob mob,LivingEntity target){Vec3 delta=target.position().subtract(mob.position()).normalize().scale(3);teleport(mob,mob.getX()+delta.x,mob.getY()+delta.y,mob.getZ()+delta.z);}
    private static boolean teleport(Mob mob,double x,double y,double z){
        BlockPos p=BlockPos.containing(x,y,z);for(int i=0;i<8&&mob.level().isEmptyBlock(p.below());i++)p=p.below();
        if(!mob.level().getBlockState(p.below()).isSolid()||!mob.level().noCollision(mob,mob.getBoundingBox().move(x-mob.getX(),p.getY()-mob.getY(),z-mob.getZ())))return false;
        mob.teleportTo(x,p.getY(),z);if(mob.level() instanceof ServerLevel level)level.sendParticles(ParticleTypes.PORTAL,x,p.getY()+1,z,16,.3,.6,.3,.1);return true;
    }
    public static void hit(Mob mob,ExtremeMob state,LivingEntity target){
        var k=state.extremeKind();switch(k){
            case ZOMBIE_CHAOS,CHAOS_CREEPER -> effect(target,"chaos",200,0);
            case ZOMBIE_JUMP,INVISIBLE_REVERSED_CREEPER -> effect(target,"reversed",120,0);
            case ZOMBIE_INVISIBLE_LOOK_UP -> effect(target,"look_up",200,0);
            case ZOMBIE_PUMPKIN -> effect(target,"occlusion",600,0);
            case RED_GHOUL -> effect(target,"fast_sensitivity",120,4);
            case FLASH_CREEPER -> effect(target,"flash",100,0);
            case WITHER_CREEPER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER,160));
            case WITHER_SKELETON_ARROW -> target.addEffect(new MobEffectInstance(MobEffects.WITHER,300));
            case SKELETON_FLOATING -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION,200));
            // Preserve the skeleton's existing horizontal shove; set upward speed after native knockback.
            case SKELETON_KNOCKBACK -> knock(mob,target,.6,0);
            default -> {}
        }
    }
    /** Apply after native hurt, weapon knockback and iron-golem lift have all finished. */
    public static void afterMeleeHit(Mob mob,ExtremeMob state,LivingEntity target){
        if(mob.level().isClientSide||!target.isAlive())return;
        var kind=state.extremeKind();
        if(kind==SKELETON_KNOCKBACK||kind==ZOMBIE_FLY_AWAY){
            Vec3 velocity=target.getDeltaMovement();
            if(kind==ZOMBIE_FLY_AWAY){
                mob.swing(InteractionHand.MAIN_HAND);mob.playSound(SoundEvents.IRON_GOLEM_ATTACK,1,.85F);
                Vec3 away=target.position().subtract(mob.position()).multiply(1,0,1).normalize();
                if(away.lengthSqr()<.001)away=mob.getLookAngle().multiply(1,0,1).normalize();
                velocity=new Vec3(away.x*.25,MELEE_LAUNCH_SPEED,away.z*.25);
            }
            target.setDeltaMovement(velocity.x,MELEE_LAUNCH_SPEED,velocity.z);
            target.hurtMarked=true;
            if(target instanceof ServerPlayer player)LaunchVelocityPayload.queue(player);
            return;
        }
        if(kind==ZOMBIE_IRON_BUCKET&&target instanceof Player){
            // The synchronized status supplies the bucket visuals without touching equipped armor.
            effect(target,"bucket_head",200,0);return;
        }
        if(kind==ZOMBIE_KNOCKBACK){
            // Restore its horizontal shove while preserving ordinary hit, jump or fall motion on Y.
            knock(mob,target,3.2,0);return;
        }
        if(kind!=DARK_IRON_GOLEM)return;
        Vec3 away=target.position().subtract(mob.position()).multiply(1,0,1).normalize();
        if(away.lengthSqr()<.001)away=mob.getLookAngle().multiply(1,0,1).normalize();
        // Keep a small separation from the attacker; existing sprint/fall speed cannot turn this into a horizontal throw.
        target.setDeltaMovement(away.x*.25,DARK_GOLEM_LAUNCH_SPEED,away.z*.25);
        target.hurtMarked=true;
        if(target instanceof ServerPlayer player)LaunchVelocityPayload.queue(player);
    }
    private static void knock(Mob mob,LivingEntity target,double horizontal,double vertical){Vec3 d=target.position().subtract(mob.position()).multiply(1,0,1).normalize();if(d.lengthSqr()<.001)d=mob.getLookAngle().multiply(1,0,1).normalize();target.setDeltaMovement(target.getDeltaMovement().add(d.scale(horizontal)).add(0,vertical,0));target.hurtMarked=true;if(target instanceof ServerPlayer player)LaunchVelocityPayload.queue(player);}
    public static void announce(Mob boss,String message){if(boss.level() instanceof ServerLevel level)for(var p:level.players())p.sendSystemMessage(Component.literal(message));}
}
