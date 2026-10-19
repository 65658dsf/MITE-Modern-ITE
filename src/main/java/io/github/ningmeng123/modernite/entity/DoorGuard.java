package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Temporary, server-owned guard cycles for a thief's carried iron door. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class DoorGuard {
    public static final int RAISE_TICKS=5,BLOCK_TICKS=30,REST_TICKS=30,BREAK_TICKS=60;
    private static final ResourceLocation MOVEMENT=ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"door_guard_movement");
    private int elapsed,cooldown;
    private int lastTick=Integer.MIN_VALUE;
    private boolean raised;

    DoorGuard(){}

    /** Choose defense before melee can begin another attack after its recovery finishes. */
    @SubscribeEvent
    public static void beforeAi(EntityTickEvent.Pre event){
        if(event.getEntity() instanceof ExtremeMobs.ZombieVariant zombie)tick(zombie);
    }

    public static boolean holdsDoor(ExtremeMobs.ZombieVariant zombie){
        return zombie.extremeKind()==ExtremeKind.ZOMBIE_DOOR&&zombie.getMainHandItem().is(Items.IRON_DOOR);
    }

    private static boolean canGuard(ExtremeMobs.ZombieVariant zombie){
        return IteConfig.bool("mob_defense")&&holdsDoor(zombie)&&zombie.isAlive()&&!zombie.isRemoved();
    }

    private static DefenseIntent.Threat threat(ExtremeMobs.ZombieVariant zombie){
        var intent=DefenseIntent.evaluate(zombie,DefenseGuardFocus.focus(zombie));
        if(intent!=null)return intent;
        // Preserve the old non-player combat policy; only player intent gains the new analysis.
        LivingEntity target=zombie.getTarget();
        if(target!=null&&!(target instanceof Player)&&target.isAlive()&&!target.isRemoved()
                &&target.level()==zombie.level()&&zombie.distanceToSqr(target)<=64&&zombie.getSensing().hasLineOfSight(target))
            return new DefenseIntent.Threat(DefenseIntent.Kind.MELEE_READY,target,null,target.getEyePosition(),0D);
        return null;
    }

    /** Runs before the no-target early return so secondary attackers can also trigger a guard. */
    public static void tick(ExtremeMobs.ZombieVariant zombie){
        if(zombie.level().isClientSide||zombie.extremeKind()!=ExtremeKind.ZOMBIE_DOOR)return;
        DoorGuard guard=zombie.doorGuard;
        // The existing ability entry point still calls us at Post; a server tick advances only once.
        if(guard.lastTick==zombie.tickCount)return;
        guard.lastTick=zombie.tickCount;
        if(guard.cooldown>0)guard.cooldown--;
        if(!canGuard(zombie)){
            if(guard.raised)guard.lower(zombie,REST_TICKS);
            return;
        }
        if(!guard.raised){
            if(guard.cooldown>0||zombie.swinging||BetterMobCombat.attacking(zombie))return;
            var intent=threat(zombie);if(intent==null)return;
            guard.raised=true;guard.elapsed=0;zombie.setDoorGuardState((byte)1);
            DefenseGuardFocus.begin(zombie,intent);
            BetterMobCombat.cancel(zombie);
            var movement=zombie.getAttribute(Attributes.MOVEMENT_SPEED);
            if(movement!=null){
                movement.removeModifier(MOVEMENT);
                movement.addTransientModifier(new AttributeModifier(MOVEMENT,-.55,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            zombie.playSound(SoundEvents.IRON_DOOR_CLOSE,.55F,1.25F);
        }else if(++guard.elapsed>=RAISE_TICKS+BLOCK_TICKS){
            guard.lower(zombie,REST_TICKS);
        }else if(guard.elapsed>=RAISE_TICKS){
            zombie.setDoorGuardState((byte)2);
        }
        if(guard.raised){var intent=threat(zombie);if(intent!=null)DefenseGuardFocus.begin(zombie,intent);}
    }

    /** Return true only for a hit that actually met the raised door's directional guard. */
    public static boolean tryBlock(ExtremeMobs.ZombieVariant zombie,DamageSource source){
        if(zombie.level().isClientSide||!zombie.isDoorBlocking()||!canGuard(zombie)
                ||source.is(DamageTypeTags.BYPASSES_SHIELD)||source.is(DamageTypeTags.IS_EXPLOSION)
                ||source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypes.MAGIC)
                ||source.is(DamageTypes.INDIRECT_MAGIC)||source.is(DamageTypes.THORNS))return false;
        Entity direct=source.getDirectEntity();
        boolean arrow=direct instanceof AbstractArrow projectile&&projectile.getPierceLevel()==0;
        boolean melee=direct instanceof LivingEntity&&direct==source.getEntity()&&!source.is(DamageTypeTags.IS_PROJECTILE);
        if(!arrow&&!melee)return false;
        Vec3 toward=direct.position().subtract(zombie.position()).multiply(1,0,1);
        if(toward.lengthSqr()<.0001&&direct instanceof AbstractArrow projectile)
            toward=projectile.getDeltaMovement().scale(-1).multiply(1,0,1);
        // The rendered door follows the torso, including when LookControl has only turned the head.
        if(toward.lengthSqr()<.0001||Vec3.directionFromRotation(0,zombie.yBodyRot).dot(toward.normalize())<.5)return false;
        zombie.playSound(SoundEvents.SHIELD_BLOCK,.9F,.7F+zombie.getRandom().nextFloat()*.15F);
        if(melee&&direct instanceof LivingEntity attacker)IteMobSettingsRules.tryDisarm(zombie,attacker);
        if(melee&&direct instanceof LivingEntity attacker&&(attacker.canDisableShield()
                ||attacker.getMainHandItem().getItem() instanceof AxeItem||MobShields.axe(attacker.getMainHandItem()))){
            zombie.doorGuard.lower(zombie,BREAK_TICKS);
            zombie.playSound(SoundEvents.SHIELD_BREAK,.85F,.75F);
        }
        return true;
    }

    private void lower(ExtremeMobs.ZombieVariant zombie,int rest){
        raised=false;elapsed=0;cooldown=Math.max(cooldown,rest);zombie.setDoorGuardState((byte)0);
        DefenseGuardFocus.clear(zombie);
        var movement=zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if(movement!=null)movement.removeModifier(MOVEMENT);
    }

    /** Guard and movement state never survive entity data loading, even when loading into an existing object. */
    void reset(ExtremeMobs.ZombieVariant zombie){lower(zombie,0);cooldown=0;lastTick=Integer.MIN_VALUE;}
}
