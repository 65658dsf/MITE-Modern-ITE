package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.rules.R196VanillaMobRules;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Rare, single-generation miniature offspring; black infernal creepers never split. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class MobSplitting {
    public static final String CHILD="modern_ite:split_child", HANDLED="modern_ite:split_handled";
    private static final ResourceLocation HEALTH=ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"split_health");
    private static final ResourceLocation SIZE=ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"split_size");
    private MobSplitting() {}

    public static boolean isChild(Entity entity){return entity!=null&&entity.getPersistentData().getBoolean(CHILD);}
    public static float damageMultiplier(Entity entity){return isChild(entity)?.5F:1F;}

    /** Attribute modifiers use native persistence and tracking packets, including late observers. */
    public static void markChild(Mob mob){
        mob.getPersistentData().putBoolean(CHILD,true);
        mob.getPersistentData().putBoolean("modern_ite:compressed",false);
        modifier(mob,Attributes.SCALE,SIZE,-.5D);
        // Native scale normally updates dimensions next tick; placement needs the small box now.
        mob.refreshDimensions();
        applyHealthScale(mob);
    }
    private static void modifier(Mob mob,Holder<Attribute> key,ResourceLocation id,double amount){
        var attribute=mob.getAttribute(key);
        if(attribute!=null&&!attribute.hasModifier(id))attribute.addPermanentModifier(
                new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** The base's standing-size override predates SCALE; restore miniatures after that override. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void dimensions(EntityEvent.Size event){
        if(!(event.getEntity() instanceof Mob mob)||mob instanceof R196Mob||event.getPose()!=Pose.STANDING
                ||mob.getAttributes()==null||R196VanillaMobRules.entry(mob.getType())==null)return;
        if(mob.getType()==EntityType.SKELETON&&mob instanceof OrdinarySkeletonState state&&state.mite$sourceSkeletonType()==1)return;
        var scale=mob.getAttribute(Attributes.SCALE);
        // The modifier is synchronized to clients, whereas persistent entity data is server-only.
        if(scale!=null&&scale.hasModifier(SIZE))event.setNewSize(event.getNewSize().scale(mob.getScale()));
    }

    /** Evaluate calendar formulas on the adult body, then scale the entire result. */
    public static void beforeHealthGrowth(Mob mob){
        if(!isChild(mob)||!mob.isAlive()||!(mob.level() instanceof ServerLevel))return;
        var attribute=mob.getAttribute(Attributes.MAX_HEALTH);
        if(attribute==null||!attribute.hasModifier(HEALTH))return;
        float fraction=mob.getHealth()/mob.getMaxHealth();
        attribute.removeModifier(HEALTH);
        mob.setHealth(fraction*mob.getMaxHealth());
    }
    public static void applyHealthScale(Mob mob){
        if(!isChild(mob)||!mob.isAlive()||!(mob.level() instanceof ServerLevel))return;
        var attribute=mob.getAttribute(Attributes.MAX_HEALTH);
        if(attribute==null||attribute.hasModifier(HEALTH))return;
        float fraction=mob.getHealth()/mob.getMaxHealth();
        modifier(mob,Attributes.MAX_HEALTH,HEALTH,-.75D);
        mob.setHealth(fraction*mob.getMaxHealth());
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event){
        if(!event.isCanceled()&&event.getEntity() instanceof Mob mob)trySplit(mob);
    }
    public static void trySplit(Mob parent){
        if(!(parent.level() instanceof ServerLevel)||isChild(parent)
                ||!(parent instanceof Creeper||parent instanceof AbstractSkeleton))return;
        if(parent instanceof ExtremeMob extreme&&extreme.extremeKind()==ExtremeKind.BLACK_INFERNAL_CREEPER)return;
        var data=parent.getPersistentData();
        if(data.getBoolean(HANDLED))return;
        data.putBoolean(HANDLED,true);
        int count;
        if(parent instanceof AbstractSkeleton){
            int configured=IteConfig.integer("compressed_skeleton_expand_count");
            if(!IteConfig.bool("compressed_skeleton")||configured==0||!data.getBoolean("modern_ite:compressed"))return;
            int minimum=Math.max(1,configured-1),maximum=Math.min(64,configured+1);
            count=minimum+parent.getRandom().nextInt(maximum-minimum+1);
        }else{
            if(parent.getRandom().nextInt(100)>=3)return;
            count=6+parent.getRandom().nextInt(3);
        }
        for(int i=0;i<count;i++){
            Mob child=MobAbilities.summonType(parent,parent.getType(),1,false,MobSplitting::markChild);
            if(child==null)continue;
            // Finalize/join listeners have now selected the same species' native body and equipment.
            markChild(child);
            if(child instanceof AbstractSkeleton){
                int ticks=IteConfig.integer("compressed_skeleton_crack_stand_time");
                child.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,ticks,127));
                child.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,ticks,127));
                child.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,ticks,127));
            }
        }
    }

    /** Covers melee, material arrows and explosions after the native calendar damage multiplier. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void outgoingDamage(LivingIncomingDamageEvent event){
        if(!event.isCanceled()&&!event.getEntity().level().isClientSide&&isChild(event.getSource().getEntity()))
            event.setAmount(event.getAmount()*.5F);
    }
}
