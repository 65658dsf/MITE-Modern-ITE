package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Forty-one Extreme identities and the four production ITE prerequisites. @author NingMeng123 */
public final class ExtremeMobs {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,ModernIte.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ModernIte.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,ModernIte.MOD_ID);
    private static final Supplier<SoundEvent> CALLER_SAY=sound("entity.zombie_caller.ambient"),CALLER_HURT=sound("entity.zombie_caller.hurt"),CALLER_DEATH=sound("entity.zombie_caller.death");
    private static final Map<ExtremeKind,Supplier<? extends EntityType<? extends Mob>>> MOBS=new EnumMap<>(ExtremeKind.class);
    static {
        for(ExtremeKind kind:ExtremeKind.values()) {
            switch(kind.family) {
                case ZOMBIE -> ExtremeMobs.<ZombieVariant>add(kind,(t,l)->new ZombieVariant(t,l,kind),kind==ExtremeKind.ZOMBIE_CALLER?1.4F:.6F,kind==ExtremeKind.ZOMBIE_CALLER?2.3F:1.95F);
                case SKELETON -> ExtremeMobs.<SkeletonVariant>add(kind,(t,l)->new SkeletonVariant(t,l,kind),.6F,1.99F);
                case CREEPER -> ExtremeMobs.<CreeperVariant>add(kind,(t,l)->new CreeperVariant(t,l,kind),.6F,1.7F);
                case WITCH -> ExtremeMobs.<WitchVariant>add(kind,(t,l)->new WitchVariant(t,l,kind),.6F,1.95F);
                case GHAST -> ExtremeMobs.<GhastVariant>add(kind,(t,l)->new GhastVariant(t,l,kind),4F,4F);
                case GOLEM -> ExtremeMobs.<GolemVariant>add(kind,(t,l)->new GolemVariant(t,l,kind),1.4F,2.7F);
                case PILLAGER -> ExtremeMobs.<PillagerVariant>add(kind,(t,l)->new PillagerVariant(t,l,kind),.6F,1.95F);
                case PIGMAN -> ExtremeMobs.<PigmanVariant>add(kind,(t,l)->new PigmanVariant(t,l,kind),.6F,1.95F);
            }
            ITEMS.registerItem(kind.id()+"_spawn_egg",p->new DeferredSpawnEggItem(()->type(kind),0x353842,kind.boss()?0xC82C3C:0x75AC8C,p.stacksTo(16)));
        }
    }
    private ExtremeMobs(){}
    private static Supplier<SoundEvent> sound(String key){return SOUNDS.register(key,()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,key)));}
    private static <T extends Mob> void add(ExtremeKind kind,EntityType.EntityFactory<T> factory,float width,float height) {
        MOBS.put(kind,TYPES.register(kind.id(),()->{
            var b=EntityType.Builder.of(factory,MobCategory.MONSTER).sized(width,height).clientTrackingRange(10);
            if(kind.fireproof())b.fireImmune();
            return b.build(ModernIte.MOD_ID+":"+kind.id());
        }));
    }
    public static EntityType<? extends Mob> type(ExtremeKind kind){return MOBS.get(kind).get();}
    public static void register(IEventBus bus){TYPES.register(bus);ITEMS.register(bus);SOUNDS.register(bus);InfernalFireball.register(bus);bus.addListener(ExtremeMobs::attributes);bus.addListener(ExtremeMobs::spawnPlacements);}
    private static void spawnPlacements(RegisterSpawnPlacementsEvent event){
        for(var kind:ExtremeKind.values()) {
            var placement=kind.family==ExtremeKind.Family.GHAST?SpawnPlacementTypes.NO_RESTRICTIONS:SpawnPlacementTypes.ON_GROUND;
            // PositionCheck runs the actual mob's MobAbilities.spawnRules once, including its random rolls.
            event.register(type(kind),placement,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type,level,reason,pos,random)->true,RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
    private static void attributes(EntityAttributeCreationEvent event){
        for(var k:ExtremeKind.values()) {
            AttributeSupplier.Builder b=switch(k.family){
                case ZOMBIE -> Zombie.createAttributes(); case SKELETON -> AbstractSkeleton.createAttributes();
                case CREEPER -> Creeper.createAttributes(); case WITCH -> Witch.createAttributes();
                case GHAST -> Ghast.createAttributes(); case GOLEM -> IronGolem.createAttributes();
                case PILLAGER -> Pillager.createAttributes(); case PIGMAN -> ZombifiedPiglin.createAttributes();
            };
            b.add(Attributes.FOLLOW_RANGE,64).add(Attributes.ATTACK_DAMAGE,8).add(Attributes.MAX_HEALTH,40);
            event.put(type(k),b.build());
        }
    }
    public static void addSpawnEntries(MiteExtensions.SpawnContext context,BlockPos position,List<R196MobSpawning.Entry> entries){
        if(context.category()!=io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER)return;
        RabidDogContent.addSpawnEntries(context, entries);
        var level=context.level();
        if(level.dimension()==Level.OVERWORLD&&MobAbilities.day(level)>=IteConfig.integer("GameMechanics.MobSpawning.ghast_spawn_limit_day")
                &&io.github.ningmeng123.mite.rules.LunarCalendar.state(level.getDayTime(),true).bloodPeriod()
                &&level.getEntitiesOfClass(Ghast.class,new net.minecraft.world.phys.AABB(position).inflate(64)).isEmpty()
                &&level.isEmptyBlock(position.above())&&level.isEmptyBlock(position.above(2))&&level.isEmptyBlock(position.above(3)))
            entries.add(new R196MobSpawning.Entry(EntityType.GHAST,null,context.category(),2,1,1,1,true));
        for(var kind:ExtremeKind.values()) {
            int weight=spawnWeight(kind);
            if(weight==0||!MobAbilities.maySpawn(kind,context.level(),position))continue;
            entries.add(new R196MobSpawning.Entry(type(kind),null,context.category(),weight,1,1,1,true));
        }
    }
    public static int spawnWeight(ExtremeKind kind){
        return switch(kind){
            case ZOMBIE_LORD,ZOMBIE_PIGMAN_LORD,ANNIHILATION_SKELETON,WANDERING_WITCH -> IteConfig.integer("GameMechanics.MobSpawning."+kind.id()+"_spawn_frequency");
            default -> kind.weight;
        };
    }
    public static boolean isBoss(Mob mob){return mob instanceof ExtremeMob e&&e.extremeKind().boss();}
    public static class ZombieVariant extends Zombie implements ExtremeMob {
        private static final EntityDataAccessor<Byte> DATA_DOOR_GUARD=SynchedEntityData.defineId(ZombieVariant.class,EntityDataSerializers.BYTE);
        private final ExtremeKind kind;private final MobMemory memory=new MobMemory();
        final DoorGuard doorGuard=new DoorGuard();
        private float doorGuardProgress,doorGuardProgressOld;
        public ZombieVariant(EntityType<? extends ZombieVariant> t,Level l,ExtremeKind k){super(t,l);kind=k;MobAbilities.attributes(this,k);}
        public ExtremeKind extremeKind(){return kind;}public MobMemory extremeMemory(){return memory;}
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(DATA_DOOR_GUARD,(byte)0);}
        public boolean isDoorRaised(){return DoorGuard.holdsDoor(this)&&isAlive()&&entityData.get(DATA_DOOR_GUARD)>0;}
        public boolean isDoorBlocking(){return DoorGuard.holdsDoor(this)&&isAlive()&&entityData.get(DATA_DOOR_GUARD)==2;}
        public float getDoorGuardProgress(float partial){return Mth.lerp(partial,doorGuardProgressOld,doorGuardProgress);}
        void setDoorGuardState(byte state){entityData.set(DATA_DOOR_GUARD,state);}
        @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
            super.onSyncedDataUpdated(key);
            // A newly tracked entity can already be blocking when its first packet arrives.
            if(level().isClientSide&&DATA_DOOR_GUARD.equals(key)&&entityData.get(DATA_DOOR_GUARD)==2&&doorGuardProgress==0)
                doorGuardProgress=doorGuardProgressOld=1;
        }
        @Override public void tick(){
            doorGuardProgressOld=doorGuardProgress;
            doorGuardProgress=Mth.clamp(doorGuardProgress+(isDoorRaised()?.2F:-.2F),0,1);
            super.tick();
        }
        @Override public boolean doHurtTarget(Entity target){
            if(isDoorRaised())return false;
            boolean landed=super.doHurtTarget(target);
            if(landed&&target instanceof LivingEntity living)MobAbilities.afterMeleeHit(this,this,living);
            return landed;
        }
        @Override public void swing(InteractionHand hand,boolean updateSelf){
            // SourceMeleeGoal swings before calling doHurtTarget; suppress both the
            // damage and its animation so a raised door cannot twitch with each retry.
            if(!isDoorRaised())super.swing(hand,updateSelf);
        }
        @Override public void setBaby(boolean baby){super.setBaby(false);}
        @Override protected boolean convertsInWater(){return false;}
        @Override protected SoundEvent getAmbientSound(){return kind==ExtremeKind.ZOMBIE_CALLER?CALLER_SAY.get():super.getAmbientSound();}
        @Override protected SoundEvent getHurtSound(DamageSource source){return kind==ExtremeKind.ZOMBIE_CALLER?CALLER_HURT.get():super.getHurtSound(source);}
        @Override protected SoundEvent getDeathSound(){return kind==ExtremeKind.ZOMBIE_CALLER?CALLER_DEATH.get():super.getDeathSound();}
        @Override protected boolean isSunBurnTick(){return kind!=null&&kind.fireproof()?false:super.isSunBurnTick();}
        @Override public boolean checkSpawnRules(LevelAccessor l,MobSpawnType reason){return MobAbilities.spawnRules(this,kind,l,reason);}
        @Override public boolean removeWhenFarAway(double d){return kind!=ExtremeKind.MIMIC_ZOMBIE&&kind!=ExtremeKind.ZOMBIE_BOSS&&super.removeWhenFarAway(d);}
        @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);memory.write(t,registryAccess());}
        @Override public void readAdditionalSaveData(CompoundTag t){
            super.readAdditionalSaveData(t);memory.read(t,registryAccess());doorGuard.reset(this);
            doorGuardProgress=doorGuardProgressOld=0;
        }
    }
    public static class SkeletonVariant extends Skeleton implements ExtremeMob {
        private final ExtremeKind kind;private final MobMemory memory=new MobMemory();
        public SkeletonVariant(EntityType<? extends SkeletonVariant> t,Level l,ExtremeKind k){super(t,l);kind=k;MobAbilities.attributes(this,k);}
        public ExtremeKind extremeKind(){return kind;}public MobMemory extremeMemory(){return memory;}
        @Override public void reassessWeaponGoal(){
            // AbstractSkeleton calls this before this variant's kind has been assigned.
            if(kind==null){super.reassessWeaponGoal();return;}
            if(!level().isClientSide)MobAbilities.configureGoals(this,kind);
        }
        @Override protected boolean isSunBurnTick(){return kind!=null&&kind.fireproof()?false:super.isSunBurnTick();}
        @Override public boolean canFreeze(){return false;}
        @Override public boolean checkSpawnRules(LevelAccessor l,MobSpawnType reason){return MobAbilities.spawnRules(this,kind,l,reason);}
        @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);memory.write(t,registryAccess());}
        @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);memory.read(t,registryAccess());}
    }
    public static class CreeperVariant extends Creeper implements ExtremeMob {
        private final ExtremeKind kind;private final MobMemory memory=new MobMemory();
        public CreeperVariant(EntityType<? extends CreeperVariant> t,Level l,ExtremeKind k){super(t,l);kind=k;MobAbilities.attributes(this,k);}
        public ExtremeKind extremeKind(){return kind;}public MobMemory extremeMemory(){return memory;}
        @Override public boolean checkSpawnRules(LevelAccessor l,MobSpawnType reason){return MobAbilities.spawnRules(this,kind,l,reason);}
        @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);memory.write(t,registryAccess());}
        @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);memory.read(t,registryAccess());}
    }
    public static class WitchVariant extends Witch implements ExtremeMob {
        private final ExtremeKind kind;private final MobMemory memory=new MobMemory();
        public WitchVariant(EntityType<? extends WitchVariant> t,Level l,ExtremeKind k){super(t,l);kind=k;MobAbilities.attributes(this,k);}
        public ExtremeKind extremeKind(){return kind;}public MobMemory extremeMemory(){return memory;}
        @Override public void performRangedAttack(LivingEntity target,float strength){MobAbilities.throwPotion(this,target);}
        @Override public boolean canBeLeader(){return false;}
        @Override public boolean checkSpawnRules(LevelAccessor l,MobSpawnType reason){return MobAbilities.spawnRules(this,kind,l,reason);}
        @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);memory.write(t,registryAccess());}
        @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);memory.read(t,registryAccess());}
    }
    public static class GhastVariant extends Ghast implements ExtremeMob {
        private final ExtremeKind kind;private final MobMemory memory=new MobMemory();
        public GhastVariant(EntityType<? extends GhastVariant> t,Level l,ExtremeKind k){super(t,l);kind=k;MobAbilities.attributes(this,k);}
        public ExtremeKind extremeKind(){return kind;}public MobMemory extremeMemory(){return memory;}
        @Override public boolean checkSpawnRules(LevelAccessor l,MobSpawnType reason){return MobAbilities.spawnRules(this,kind,l,reason);}
        @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);memory.write(t,registryAccess());}
        @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);memory.read(t,registryAccess());}
    }
    public static class GolemVariant extends IronGolem implements ExtremeMob {
        private final ExtremeKind kind;private final MobMemory memory=new MobMemory();
        public GolemVariant(EntityType<? extends GolemVariant> t,Level l,ExtremeKind k){super(t,l);kind=k;MobAbilities.attributes(this,k);}
        public ExtremeKind extremeKind(){return kind;}public MobMemory extremeMemory(){return memory;}
        @Override public boolean doHurtTarget(Entity target){
            boolean landed=super.doHurtTarget(target);
            if(landed&&target instanceof LivingEntity living)MobAbilities.afterMeleeHit(this,this,living);
            return landed;
        }
        @Override public boolean checkSpawnRules(LevelAccessor l,MobSpawnType reason){return MobAbilities.spawnRules(this,kind,l,reason);}
        @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);memory.write(t,registryAccess());}
        @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);memory.read(t,registryAccess());}
    }
    public static class PillagerVariant extends Pillager implements ExtremeMob {
        private final ExtremeKind kind;private final MobMemory memory=new MobMemory();
        public PillagerVariant(EntityType<? extends PillagerVariant> t,Level l,ExtremeKind k){super(t,l);kind=k;MobAbilities.attributes(this,k);}
        public ExtremeKind extremeKind(){return kind;}public MobMemory extremeMemory(){return memory;}
        @Override public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon){return weapon instanceof CrossbowItem;}
        @Override public void performRangedAttack(LivingEntity target,float strength){performCrossbowAttack(this,1.6F);}
        @Override public boolean checkSpawnRules(LevelAccessor l,MobSpawnType reason){return MobAbilities.spawnRules(this,kind,l,reason);}
        @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);memory.write(t,registryAccess());}
        @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);memory.read(t,registryAccess());}
    }
    public static class PigmanVariant extends ZombifiedPiglin implements ExtremeMob {
        private final ExtremeKind kind;private final MobMemory memory=new MobMemory();
        public PigmanVariant(EntityType<? extends PigmanVariant> t,Level l,ExtremeKind k){super(t,l);kind=k;MobAbilities.attributes(this,k);}
        public ExtremeKind extremeKind(){return kind;}public MobMemory extremeMemory(){return memory;}
        @Override public void setBaby(boolean baby){super.setBaby(false);}
        @Override public boolean checkSpawnRules(LevelAccessor l,MobSpawnType reason){return MobAbilities.spawnRules(this,kind,l,reason);}
        @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);memory.write(t,registryAccess());}
        @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);memory.read(t,registryAccess());}
    }
}
