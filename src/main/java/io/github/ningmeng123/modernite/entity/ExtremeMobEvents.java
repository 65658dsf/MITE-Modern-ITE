package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import static io.github.ningmeng123.modernite.entity.ExtremeKind.*;

/** Server authority for hit effects, source encounters and death inventory recovery. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class ExtremeMobEvents {
    private ExtremeMobEvents(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide||event.isCanceled())return;
        if(event.getEntity() instanceof Mob mob&&mob instanceof ExtremeMob extreme){boolean loaded=extreme.extremeMemory().initialized;MobAbilities.initialize(mob,extreme);if(loaded)MobAbilities.configureGoals(mob,extreme.extremeKind());}
        if(event.getEntity() instanceof Pillager pillager&&pillager.getPersistentData().getBoolean("modern_ite:upgrade_crossbow")){
            pillager.getPersistentData().remove("modern_ite:upgrade_crossbow");
            ItemStack weapon=pillager.getMainHandItem();
            if(weapon.is(Items.CROSSBOW))pillager.setItemSlot(EquipmentSlot.MAINHAND,weapon.transmuteCopy(io.github.ningmeng123.modernite.content.ExtremeContent.CROSSBOW.get()));
        }
        if(event.getEntity() instanceof AbstractSkeleton skeleton&&!skeleton.getPersistentData().getBoolean("modern_ite:legacy_rolls")){
            skeleton.getPersistentData().putBoolean("modern_ite:legacy_rolls",true);
            skeleton.getPersistentData().putBoolean("modern_ite:triple",IteConfig.bool("Entities.skeleton_triple_shot")&&skeleton.getRandom().nextInt(100)>85);
            skeleton.getPersistentData().putBoolean("modern_ite:fire_arrows",skeleton.getRandom().nextInt(10)>5);
            skeleton.getPersistentData().putBoolean("modern_ite:compressed",!MobSplitting.isChild(skeleton)&&IteConfig.bool("Entities.compressed_skeleton")&&skeleton.getRandom().nextInt(100)<3);
        }
        if(event.getEntity() instanceof AbstractArrow arrow&&arrow.getOwner() instanceof AbstractSkeleton skeleton
                &&!arrow.getPersistentData().getBoolean("modern_ite:split_arrow")){
            if(skeleton.getPersistentData().getBoolean("modern_ite:fire_arrows")&&MobAbilities.day(event.getLevel())>196)arrow.setRemainingFireTicks(100);
            if(IteConfig.bool("Entities.skeleton_triple_shot")&&skeleton.getPersistentData().getBoolean("modern_ite:triple")){
                arrow.getPersistentData().putBoolean("modern_ite:split_arrow",true);
                for(double angle:new double[]{-.12,.12}){
                    Arrow split=EntityType.ARROW.create(event.getLevel());if(split==null)continue;
                    split.getPersistentData().putBoolean("modern_ite:split_arrow",true);split.setOwner(skeleton);split.setPos(arrow.position());
                    split.setDeltaMovement(arrow.getDeltaMovement().yRot((float)angle));split.setBaseDamage(arrow.getBaseDamage());
                    split.setRemainingFireTicks(arrow.getRemainingFireTicks());split.pickup=AbstractArrow.Pickup.DISALLOWED;event.getLevel().addFreshEntity(split);
                }
            }
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event){
        if(event.getEntity() instanceof Mob mob&&mob instanceof ExtremeMob extreme&&!mob.level().isClientSide&&mob.isAlive())MobAbilities.tick(mob,extreme);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void finalizeSpawn(FinalizeSpawnEvent event){
        if(event.isCanceled()||event.isSpawnCancelled()||event.getEntity() instanceof ExtremeMob)return;
        if(!(event.getLevel().getLevel() instanceof ServerLevel level))return;
        Mob original=event.getEntity();
        if(original.getType()==EntityType.PILLAGER&&original instanceof Pillager&&MobAbilities.day(level)>=33){
            // Keep vanilla patrol/raid identity and AI while retaining a survival source for the addon weapon.
            original.getPersistentData().putBoolean("modern_ite:upgrade_crossbow",true);
        }else if(!IteReferenceSpawns.enabled()&&original.getType()==EntityType.ZOMBIFIED_PIGLIN
                &&level.getServer().isSameThread()&&event.getSpawnType()!=MobSpawnType.STRUCTURE
                &&event.getSpawnType()!=MobSpawnType.COMMAND&&event.getSpawnType()!=MobSpawnType.SPAWN_EGG
                &&!SpawnPopulation.explicit(original)&&SpawnPopulation.automaticAllowed(level,ExtremeMobs.type(ZOMBIE_PIGMAN_LORD))
                &&pigmanReplacementRoll(original,event.getSpawnType())){
            Mob replacement=ExtremeMobs.type(ZOMBIE_PIGMAN_LORD).create(level);if(replacement==null)return;
            replacement.moveTo(original.position());replacement.setYRot(original.getYRot());
            if(original.getPersistentData().hasUUID(SpawnPopulation.SUMMONER))replacement.getPersistentData().putUUID(SpawnPopulation.SUMMONER,original.getPersistentData().getUUID(SpawnPopulation.SUMMONER));
            if(SpawnPopulation.encounter(original))replacement.getPersistentData().putBoolean(SpawnPopulation.ENCOUNTER,true);
            Set<UUID> existing=MobAbilities.summonBaseline(level,original);Set<Entity> owned=new LinkedHashSet<>();owned.add(replacement);
            event.setSpawnCancelled(true);SpawnPopulation.transfer(original,replacement);boolean added=false;
            try{
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(replacement,level,event.getDifficulty(),event.getSpawnType(),event.getSpawnData());
                MobAbilities.rememberSummonTree(replacement,existing,owned);
                replacement.setTarget(original.getTarget());
                if(!replacement.isRemoved()&&!replacement.isSpawnCancelled()&&replacement.getSpawnType()!=null)added=level.addFreshEntity(replacement);
            }finally{
                if(!added){
                    MobAbilities.discardSummonTree(replacement,existing,owned);event.setSpawnCancelled(false);
                    SpawnPopulation.transfer(replacement,original);
                }
            }
        }
    }
    private static boolean pigmanReplacementRoll(Mob original,MobSpawnType reason) {
        if(reason!=MobSpawnType.NATURAL)return original.getRandom().nextInt(5)==0;
        return ConfiguredMonsterSpawns.replacement(original.getRandom(),.2D,original.getType(),
                ExtremeMobs.type(ZOMBIE_PIGMAN_LORD),()->original.getRandom().nextInt(5)==0);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent event){
        if(event.isCanceled())return;
        if(witch(event.getEntity())&&witch(attacker(event.getSource()))){event.setCanceled(true);return;}
        if(!(event.getEntity() instanceof Mob mob)||!(mob instanceof ExtremeMob state))return;
        var k=state.extremeKind();var s=state.extremeMemory();DamageSource damage=event.getSource();
        if(event.getAmount()>0&&Float.isFinite(event.getAmount())&&mob instanceof ExtremeMobs.ZombieVariant zombie
                &&DoorGuard.tryBlock(zombie,damage)){event.setCanceled(true);return;}
        if(k.fireproof()&&damage.is(DamageTypeTags.IS_FIRE)){event.setCanceled(true);return;}
        if(k==PHASE_CREEPER&&MobAbilities.evade(mob,s,damage)){event.setCanceled(true);return;}
        if(k==ZOMBIE_BOSS){
            if(!(damage.getEntity() instanceof Player player)||damage.getDirectEntity()!=player||damage.is(DamageTypeTags.IS_PROJECTILE)||damage.is(DamageTypeTags.IS_EXPLOSION)){event.setCanceled(true);return;}
            event.setAmount(event.getAmount()/5F);player.removeEffect(MobEffects.DAMAGE_BOOST);s.cooldown=200;
            player.getPersistentData().putLong("modern_ite:boss_strength_suppression",player.level().getGameTime()+200);
        }
        if(k==ANNIHILATION_SKELETON&&(!(damage.getEntity() instanceof Player player)||damage.getDirectEntity()!=player
                ||Math.abs(player.getY()-mob.getY())>=3||damage.is(DamageTypeTags.IS_EXPLOSION)||damage.is(DamageTypeTags.IS_PROJECTILE))){event.setCanceled(true);return;}
        if(k==ANNIHILATION_SKELETON)s.stage=1;
        if(k==DARK_IRON_GOLEM){
            boolean pick=damage.getEntity() instanceof LivingEntity living&&(living.getMainHandItem().getItem() instanceof PickaxeItem
                ||net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(living.getMainHandItem().getItem()).getPath().endsWith("_pickaxe"));
            if(!pick&&!damage.is(DamageTypeTags.IS_EXPLOSION)&&!damage.is(DamageTypes.DROWN)&&!(damage.getDirectEntity() instanceof Snowball)){event.setCanceled(true);return;}
        }
        if(k==ZOMBIE_CALLER&&damage.getEntity() instanceof Player)MobAbilities.startCaller(mob,s);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fixedLightning(LivingDamageEvent.Pre event){
        if(event.getEntity() instanceof Player&&attacker(event.getSource()) instanceof ExtremeMob e&&e.extremeKind()==SKELETON_LIGHTNING
                &&event.getSource().getDirectEntity() instanceof AbstractArrow)event.setNewDamage(6F*MobSplitting.damageMultiplier(attacker(event.getSource())));
    }
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event){
        Mob attacker=attacker(event.getSource());
        if(attacker instanceof ExtremeMob e&&event.getNewDamage()>0){
            MobAbilities.hit(attacker,e,event.getEntity());
            if(e.extremeKind()==ZOMBIE_BOSS)event.getEntity().getPersistentData().putLong("modern_ite:boss_melee_grace",attacker.level().getGameTime()+30);
            if(e.extremeKind()==SKELETON_LIGHTNING&&event.getSource().getDirectEntity() instanceof AbstractArrow&&attacker.level() instanceof ServerLevel level){LightningBolt bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt!=null){bolt.setVisualOnly(true);bolt.moveTo(event.getEntity().position());level.addFreshEntity(bolt);}}
        }
        if(event.getEntity() instanceof ExtremeMob state&&state.extremeKind()==ZOMBIE_BOSS&&event.getSource().getEntity() instanceof Player player)
            state.extremeMemory().contribution.merge(player.getUUID(),event.getNewDamage(),Float::sum);
    }
    private static Mob attacker(DamageSource source){
        if(source.getEntity() instanceof Mob mob)return mob;
        if(source.getDirectEntity() instanceof Projectile p&&p.getOwner() instanceof Mob mob)return mob;return null;
    }
    private static boolean witch(Entity entity){return entity instanceof Witch||entity instanceof ExtremeMob e&&e.extremeKind().family==Family.WITCH;}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void witchTarget(LivingChangeTargetEvent event){
        if(witch(event.getEntity())&&witch(event.getNewAboutToBeSetTarget()))event.setNewAboutToBeSetTarget(null);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event){
        if(event.isCanceled()||event.getEntity().level().isClientSide)return;
        if(event.getEntity() instanceof Mob mob){
            if(mob instanceof ExtremeMob state){
                if(invisibleCreeper(state.extremeKind()))mob.removeEffect(MobEffects.INVISIBILITY);
                if(state.extremeKind().mirrors()||state.extremeKind()==MIMIC_ZOMBIE||state.extremeKind()==UNDYING_ZOMBIE_EMPEROR)
                    for(var slot:EquipmentSlot.values())mob.setItemSlot(slot,ItemStack.EMPTY);
                if(state.extremeKind()==ZOMBIE_BOSS)bossReward(mob,state.extremeMemory());
            }
            if(mob.getPersistentData().hasUUID("modern_ite:summoner")&&mob.level() instanceof ServerLevel level){
                Entity owner=level.getEntity(mob.getPersistentData().getUUID("modern_ite:summoner"));
                if(owner instanceof ExtremeMob e&&e.extremeKind().family==Family.WITCH)e.extremeMemory().bats=Math.max(0,e.extremeMemory().bats-1);
            }
        }
    }
    private static void bossReward(Mob boss,MobMemory state){
        if(!(boss.level() instanceof ServerLevel level))return;
        var ranked=state.contribution.entrySet().stream().sorted(Map.Entry.<UUID,Float>comparingByValue().reversed()).toList();
        MobAbilities.announce(boss,"僵尸BOSS挑战成功");
        var enchantments=boss.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().filter(h->h.value().getMaxLevel()>0).toList();
        for(var row:ranked){ServerPlayer player=level.getServer().getPlayerList().getPlayer(row.getKey());if(player==null)continue;
            int diamonds=(int)(row.getValue()/20),bookLevel=(int)(row.getValue()/50);
            while(diamonds>0){int n=Math.min(64,diamonds);give(player,new ItemStack(Items.DIAMOND,n));diamonds-=n;}
            if(bookLevel>0&&!enchantments.isEmpty()){
                var chosen=enchantments.get(boss.getRandom().nextInt(enchantments.size()));var book=new ItemStack(Items.ENCHANTED_BOOK);
                book.enchant(chosen,Math.clamp(bookLevel,1,chosen.value().getMaxLevel()));give(player,book);
            }
        }
        for(int i=0;i<Math.min(5,ranked.size());i++){var row=ranked.get(i);var player=level.getServer().getPlayerList().getPlayer(row.getKey());MobAbilities.announce(boss,(i+1)+". "+(player==null?row.getKey():player.getGameProfile().getName())+"："+String.format(java.util.Locale.ROOT,"%.2f",row.getValue()));}
    }
    private static void give(Player player,ItemStack stack){if(!player.getInventory().add(stack))player.drop(stack,false);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event){
        if(event.isCanceled()||event.getEntity().level().isClientSide)return;
        if(event.getEntity() instanceof ServerPlayer player){playerMimic(event,player);return;}
        if(!(event.getEntity() instanceof Mob mob)||!(mob instanceof ExtremeMob state))return;
        ExtremeKind k=state.extremeKind();boolean player=event.isRecentlyHit();int day=MobAbilities.day(mob.level());
        if(k==MIMIC_ZOMBIE)PlayerMimicInventory.release(mob,event.getDrops());
        if(player)switch(k){
            case MIRROR_SKELETON,MIRROR_ZOMBIE,ZOMBIE_DOOR -> drop(event,"minecraft:emerald",Math.min(day/32,3));
            case MINER_ZOMBIE -> drop(event,"minecraft:iron_nugget",Math.min(day/32,3));
            case EXCHANGER -> drop(event,"mite:iron_arrow",3);
            case ZOMBIE_LORD,SKELETON_ELITE_LORD -> drop(event,"minecraft:diamond",Math.min(day/32+1,3));
            case ZOMBIE_ELITE_LORD -> drop(event,"minecraft:emerald",Math.min(day/32+1,3));
            case ZOMBIE_PIGMAN_LORD -> drop(event,"minecraft:quartz",Math.min(day/32+1,3));
            case UNDYING_ZOMBIE_EMPEROR -> {drop(event,"minecraft:diamond",2+mob.getRandom().nextInt(3));drop(event,"mite:mithril_ingot",2);drop(event,"mite:adamantium_ingot",1);drop(event,"mite:vibranium_nugget",1+mob.getRandom().nextInt(4));}
            case ANNIHILATION_SKELETON -> {drop(event,"mite:vibranium_nugget",2);drop(event,"minecraft:diamond",2);}
            case DARK_IRON_GOLEM -> {drop(event,"minecraft:diamond",1+mob.getRandom().nextInt(2));drop(event,"mite:adamantium_nugget",1+mob.getRandom().nextInt(8));}
            case ZOMBIE_CALLER -> {drop(event,"minecraft:rotten_flesh",2+mob.getRandom().nextInt(2));drop(event,"minecraft:iron_nugget",1+mob.getRandom().nextInt(8));drop(event,new String[]{"minecraft:bread","minecraft:apple","minecraft:cooked_beef","minecraft:cooked_chicken","minecraft:pumpkin_pie"}[mob.getRandom().nextInt(5)],1);}
            case BLACK_INFERNAL_CREEPER -> drop(event,"modern_ite:resurrection_array",1);
            default -> {}
        }
        if(k==RANDOM_ZOMBIE)drop(event,"minecraft:emerald",1);
        if(k==WITCH_LORD)drop(event,"minecraft:enchanted_golden_apple",2);
        if(k==WITCH_LORD||k==WANDERING_WITCH){drop(event,"mite:adamantium_nugget",1+mob.getRandom().nextInt(3));drop(event,"minecraft:nether_wart",4);}
        if(k==SKELETON_ZOMBIE&&mob.getRandom().nextFloat()<(player?.5F:.25F))drop(event,"minecraft:rotten_flesh",1);
        if(k==WITHER_SKELETON_ARROW){int coal=mob.getRandom().nextInt(3)-1;if(!player&&coal>0)coal-=mob.getRandom().nextInt(coal+1);drop(event,"minecraft:coal",coal);if(player&&mob.getRandom().nextInt(100)<5)drop(event,"minecraft:wither_skeleton_skull",1);}
        if(k==ZOMBIE_GHAST&&mob.getRandom().nextFloat()<.08)drop(event,"minecraft:ghast_tear",1);
        if(k==SKELETON_BLAZE&&mob.getRandom().nextFloat()<.05)drop(event,"minecraft:blaze_powder",1);
    }
    private static void playerMimic(LivingDropsEvent event,ServerPlayer player){
        if(player.getRandom().nextInt(20)!=0)return;
        Mob mimic=ExtremeMobs.type(MIMIC_ZOMBIE).create(player.serverLevel());if(!(mimic instanceof ExtremeMob e))return;
        SpawnPopulation.markPlayerDeathMimic(mimic);
        MobAbilities.initialize(mimic,e);mimic.moveTo(player.position());e.extremeMemory().mimicName=player.getGameProfile().getName();
        mimic.setCustomName(Component.literal(e.extremeMemory().mimicName));mimic.setCustomNameVisible(true);mimic.setPersistenceRequired();mimic.setCanPickUpLoot(false);
        PlayerMimicInventory.capture(mimic,event.getDrops());
        // Failed or canceled joins must leave the original player drops available.
        if(player.serverLevel().addFreshEntity(mimic))event.getDrops().clear();
    }
    private static void drop(LivingDropsEvent event,String id,int count){if(count<=0)return;var stack=MobAbilities.item(id);if(stack.isEmpty())return;stack.setCount(count);var m=event.getEntity();event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(m.level(),m.getX(),m.getY(),m.getZ(),stack));}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void brokenSpawner(BlockEvent.BreakEvent event){
        if(event.isCanceled()||event.getPlayer().isCreative()||!event.getState().is(Blocks.SPAWNER)||!(event.getLevel() instanceof ServerLevel level)||!MobAbilities.underground(level))return;
        BlockPos pos=event.getPos().immutable();Player player=event.getPlayer();
        var server=level.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount()+1,()->{if(!level.getBlockState(pos).is(Blocks.SPAWNER))spawnBoss(level,pos,player);}));
    }
    public static void spawnBoss(ServerLevel level,BlockPos pos,Player player){
        if(!SpawnPopulation.automaticAllowed(level,ExtremeMobs.type(ZOMBIE_BOSS))||level.random.nextFloat()>=.5)return;
        Mob boss=ExtremeMobs.type(ZOMBIE_BOSS).create(level);if(boss==null)return;
        boss.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);boss.setTarget(player);
        if(!SpawnPopulation.reserveSupport(boss,null,true)){boss.discard();return;}
        try{
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(boss,level,level.getCurrentDifficultyAt(pos),MobSpawnType.MOB_SUMMONED,null);
            if(boss.isRemoved()||boss.isSpawnCancelled()||boss.getSpawnType()==null||!level.addFreshEntity(boss))return;
            boss.spawnAnim();MobAbilities.summon(boss,WITCH_LORD,1,true);
        }finally{
            if(!boss.isAddedToLevel()){boss.discard();SpawnPopulation.release(boss);}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void blast(ExplosionEvent.Start event){
        if(!(event.getExplosion().getDirectSourceEntity() instanceof Mob mob)||!(mob instanceof ExtremeMob state))return;
        // Vanilla creepers copy active potions into a lingering cloud after exploding.
        if(invisibleCreeper(state.extremeKind()))mob.removeEffect(MobEffects.INVISIBILITY);
        if(event.isCanceled())return;
        if(state.extremeKind()!=BLACK_INFERNAL_CREEPER||mob.getPersistentData().getBoolean("modern_ite:processing_blast"))return;
        event.setCanceled(true);mob.getPersistentData().putBoolean("modern_ite:processing_blast",true);
        float radius=InfernalExplosionCalculator.RADIUS*(MobSplitting.isChild(mob)?.5F:1F);
        // Native entity selection and knockback use twice Explosion.radius; the crater has its own physical radius.
        try{mob.level().explode(mob,Explosion.getDefaultDamageSource(mob.level(),mob),new InfernalExplosionCalculator(mob,radius),mob.getX(),mob.getY()+mob.getBbHeight()/4,mob.getZ(),radius/2F,false,Level.ExplosionInteraction.MOB);}
        finally{mob.getPersistentData().remove("modern_ite:processing_blast");}
    }
    private static boolean invisibleCreeper(ExtremeKind kind){return kind==INVISIBLE_CREEPER||kind==INVISIBLE_REVERSED_CREEPER;}
    @SubscribeEvent
    public static void creeperBlastEffects(ExplosionEvent.Detonate event){
        if(event.getLevel().isClientSide||!(event.getExplosion().getDirectSourceEntity() instanceof Mob mob)||!(mob instanceof ExtremeMob state))return;
        ExtremeKind kind=state.extremeKind();
        if(kind==BLACK_INFERNAL_CREEPER&&mob.getPersistentData().getBoolean("modern_ite:processing_blast")){
            InfernalExplosionPayload.send((ServerLevel)event.getLevel(),event.getExplosion().center(),InfernalExplosionCalculator.RADIUS*(MobSplitting.isChild(mob)?.5F:1F));
            return;
        }
        if(kind!=FLASH_CREEPER&&kind!=WITHER_CREEPER)return;
        var center=event.getExplosion().center();double radius=event.getExplosion().radius()*2D;
        for(Entity entity:event.getAffectedEntities())if(entity instanceof Player player&&player.isAlive()&&!player.isSpectator()&&!player.isCreative()
                &&player.distanceToSqr(center)<=radius*radius&&Explosion.getSeenPercent(center,player)>0){
            if(kind==FLASH_CREEPER)MobAbilities.effect(player,"flash",100,0);
            else player.addEffect(new MobEffectInstance(MobEffects.WITHER,160),mob);
        }
    }
}
