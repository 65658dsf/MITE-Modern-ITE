package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.mite.entity.R196ExplosionCalculator;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Both used legacy projectile types are real tracked entities with saved blast profiles. @author NingMeng123 */
public final class InfernalFireball extends LargeFireball {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,ModernIte.MOD_ID);
    public static final DeferredHolder<EntityType<?>,EntityType<InfernalFireball>> NB=TYPES.register("nb_fireball",()->
        EntityType.Builder.<InfernalFireball>of((t,l)->new InfernalFireball(t,l,false),MobCategory.MISC).sized(1,1).clientTrackingRange(10).updateInterval(1).build(ModernIte.MOD_ID+":nb_fireball"));
    public static final DeferredHolder<EntityType<?>,EntityType<InfernalFireball>> INFERNAL=TYPES.register("infernal_blast_fireball",()->
        EntityType.Builder.<InfernalFireball>of((t,l)->new InfernalFireball(t,l,true),MobCategory.MISC).sized(1,1).clientTrackingRange(10).updateInterval(1).build(ModernIte.MOD_ID+":infernal_blast_fireball"));
    private boolean infernal;
    private float blockPower=3.575F,livingPower=5.5F,normalPower=1F;
    public InfernalFireball(EntityType<? extends InfernalFireball> type,Level level,boolean infernal){super(type,level);this.infernal=infernal;}
    public static void register(IEventBus bus){TYPES.register(bus);}
    public void normalPower(float power){normalPower=Math.clamp(power,0F,128F);}
    public float normalPower(){return normalPower;}
    public static InfernalFireball shoot(LivingEntity shooter,LivingEntity target,boolean infernal,double spread){
        InfernalFireball shot=(infernal?INFERNAL:NB).get().create(shooter.level());
        if(shot==null)return null;
        shot.setOwner(shooter);
        double lead=Math.pow(shooter.distanceToSqr(target),.44)*(.5+shooter.getRandom().nextFloat());
        Vec3 aim=target.position().add(target.getDeltaMovement().scale(lead)).add(0,target.getBbHeight()*.5,0)
            .add((shooter.getRandom().nextDouble()-.5)*spread,0,(shooter.getRandom().nextDouble()-.5)*spread);
        Vec3 origin=shooter.position().add(0,shooter.getBbHeight()*.5,0), direction=aim.subtract(origin).normalize();
        shot.setPos(origin.add(direction.scale(Math.min(4,Math.max(1,shooter.getBbWidth())))));
        shot.setDeltaMovement(direction.scale(.9));
        shooter.level().addFreshEntity(shot);return shot;
    }
    @Override public boolean isPickable(){return false;}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){return false;}
    @Override protected void onHit(HitResult hit){
        if(level().isClientSide)return;
        if(!infernal&&hit instanceof EntityHitResult e)e.getEntity().hurt(damageSources().fireball(this,getOwner()),6F);
        if(infernal) level().explode(this,damageSources().fireball(this,getOwner()),new R196ExplosionCalculator(this,livingPower),
            getX(),getY(),getZ(),blockPower,true,Level.ExplosionInteraction.MOB);
        else level().explode(this,getX(),getY(),getZ(),normalPower,true,Level.ExplosionInteraction.MOB);
        discard();
    }
    @Override protected void onHitEntity(EntityHitResult hit){}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putBoolean("Infernal",infernal);t.putFloat("BlockPower",blockPower);t.putFloat("LivingPower",livingPower);t.putFloat("NormalPower",normalPower);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);infernal=t.getBoolean("Infernal");if(t.contains("BlockPower"))blockPower=t.getFloat("BlockPower");if(t.contains("LivingPower"))livingPower=t.getFloat("LivingPower");if(t.contains("NormalPower"))normalPower(t.getFloat("NormalPower"));}
}
