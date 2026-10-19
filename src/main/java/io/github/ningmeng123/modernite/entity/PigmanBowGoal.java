package io.github.ningmeng123.modernite.entity;
import io.github.ningmeng123.mite.entity.SkeletonAiming;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
/** The parent ITE pigman's real ranged attack, retaining current arrow physics and calendar damage. @author NingMeng123 */
public final class PigmanBowGoal extends Goal {
    private final ZombifiedPiglin pig;private int cooldown;
    public PigmanBowGoal(ZombifiedPiglin pig){this.pig=pig;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){return IteConfig.bool("Entities.zombie_pigman_use_bow")&&pig.getMainHandItem().getItem() instanceof BowItem&&pig.getTarget()!=null&&pig.getTarget().isAlive();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void stop(){pig.stopUsingItem();}
    @Override public void tick(){
        var target=pig.getTarget();if(target==null)return;pig.getLookControl().setLookAt(target,30,30);
        boolean clearShot=SkeletonAiming.aimPoint(pig,target)!=null;
        if(pig.distanceToSqr(target)>225||!clearShot)pig.getNavigation().moveTo(target,1);else pig.getNavigation().stop();
        if(cooldown>0){cooldown--;return;}
        if(!clearShot)return;cooldown=60;
        ItemStack ammo=MobAbilities.firstItem("mite:iron_arrow","minecraft:arrow");if(!(ammo.getItem() instanceof ArrowItem item))return;
        int punch=(int)Math.min(1+Math.floor(Math.max(0,MobAbilities.day(pig.level())-64)/48D),5);
        if(punch>0)MobAbilities.enchant(pig,pig.getMainHandItem(),"minecraft:punch",punch);
        AbstractArrow arrow=item.createArrow(pig.level(),ammo,pig,pig.getMainHandItem());
        int power=io.github.ningmeng123.mite.enchantment.R196Enchantments.level(pig.getMainHandItem(),pig.level(),"minecraft:power")+1;
        arrow.setBaseDamage(2+pig.level().getDifficulty().getId()*.11+power*2+1);
        if(!PredictiveBowAiming.shoot(pig,arrow,target,1.6F))return;
        if(MobAbilities.day(pig.level())>196)arrow.setRemainingFireTicks(100);arrow.pickup=AbstractArrow.Pickup.DISALLOWED;pig.level().addFreshEntity(arrow);
        pig.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT,1,1);pig.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
}
