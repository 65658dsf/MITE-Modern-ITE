package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.modernite.content.ExtremeContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real Player.attack/tick verifies cooldown damage instead of merely reading item attributes. @author NingMeng123 */
@GameTestHolder("modern_ite_club_cadence")
@PrefixGameTestTemplate(false)
public final class ClubAttackCadenceGameTests {
    @GameTest(template="gear_empty")
    public static void everyMetalClubRechargesInSeventeenTicksAndRepeatedClicksUseNativeDamageScaling(GameTestHelper h){
        for(String metal:new String[]{"copper","silver","gold","iron","ancient_metal","mithril","adamantium","vibranium"}){
            Player player=h.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1,12,1)));
            player.setNoGravity(true);player.setOnGround(true);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ExtremeContent.ITEM_ENTRIES.get("club_"+metal).get()));
            RecordingCow target=new RecordingCow(h);target.setPos(player.position().add(1,0,0));
            try{
                ticks(player,25);
                close(h,player.getAttributeValue(Attributes.ATTACK_SPEED),1.2D,metal+" live equipped attack speed");
                player.attack(target);
                float full=target.damage;
                h.assertTrue(full>0F,metal+" charged primary attack reaches the target");
                close(h,player.getAttackStrengthScale(0F),0D,metal+" primary attack resets native cooldown");
                player.attack(target);
                float strength=.5F/player.getCurrentItemAttackStrengthDelay();
                close(h,target.damage,full*(.2F+strength*strength*.8F),metal+" immediate repeat uses the native weak-strike damage curve");
                ticks(player,10);
                h.assertTrue(player.getAttackStrengthScale(0F)<.61F,metal+" cannot fully recharge after half a second");
                ticks(player,6);
                h.assertTrue(player.getAttackStrengthScale(0F)<1F,metal+" remains below full charge at tick16");
                ticks(player,1);
                close(h,player.getAttackStrengthScale(0F),1D,metal+" completes its 16.67 tick recharge by tick17");
                player.attack(target);
                close(h,target.damage,full,metal+" a fully recovered repeat delivers the same primary damage");
            }finally{target.discard();player.discard();}
        }
        h.succeed();
    }

    private static void ticks(Player player,int count){
        for(int tick=0;tick<count;tick++){player.tick();player.setOnGround(true);}
    }
    private static void close(GameTestHelper h,double actual,double expected,String message){
        h.assertTrue(Math.abs(actual-expected)<.0001D,message+": "+actual+" vs "+expected);
    }
    private static final class RecordingCow extends Cow {
        float damage;
        RecordingCow(GameTestHelper h){super(EntityType.COW,h.getLevel());setNoAi(true);setNoGravity(true);}
        @Override public boolean hurt(DamageSource source,float amount){damage=amount;return false;}
    }
}
