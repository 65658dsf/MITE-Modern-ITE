package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.content.ExtremeContent;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual accepted, canceled and repeated club hits through the encounter state machine. @author NingMeng123 */
@GameTestHolder("modern_ite_boss_challenge")
@PrefixGameTestTemplate(false)
public final class BossChallengeGameTests {
    @GameTest(template="gear_empty")
    public static void aMithrilClubHitStartsTheChallengeAndTemporaryTargetLossRetainsDamage(GameTestHelper h){
        try(var site=new SpawnBalanceGameTests.Site(h)){
            site.date(96,96,true);
            var boss=(ExtremeMobs.ZombieVariant)site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_BOSS));
            MobAbilities.initialize(boss,boss);
            Player player=player(h,boss);
            try{
                boss.setTarget(null);
                attack(player,boss);
                float damaged=boss.getHealth();
                h.assertTrue(damaged<boss.getMaxHealth(),"The real mithril club primary attack removes boss health");
                h.assertTrue(boss.getTarget()==player,"An accepted first hit immediately establishes the actual challenger");
                h.assertTrue(boss.extremeMemory().cooldown==200&&!boss.extremeMemory().contribution.isEmpty(),
                        "Accepted melee damage starts the saved grace timer and contribution together");
                boss.setTarget(null);
                for(int tick=0;tick<199;tick++)MobAbilities.tick(boss,boss);
                h.assertTrue(boss.getHealth()==damaged&&!boss.extremeMemory().contribution.isEmpty(),
                        "Transient target loss cannot immediately reset a live challenge");
                attack(player,boss);
                h.assertTrue(boss.getHealth()<damaged&&boss.extremeMemory().cooldown==200,
                        "A subsequent accepted club hit renews the encounter rather than causing challenge failure");
                boss.setTarget(null);
                for(int tick=0;tick<201;tick++)MobAbilities.tick(boss,boss);
                h.assertTrue(boss.getHealth()==boss.getMaxHealth()&&boss.extremeMemory().contribution.isEmpty(),
                        "A genuine full ten seconds without a target still fails and clears the challenge");
            }finally{player.discard();}
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void canceledClubDamageCannotCreateContributionOrExtendTheBossChallenge(GameTestHelper h){
        try(var site=new SpawnBalanceGameTests.Site(h)){
            site.date(96,96,true);
            var boss=(ExtremeMobs.ZombieVariant)site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_BOSS));
            MobAbilities.initialize(boss,boss);
            Player player=player(h,boss);
            Consumer<LivingIncomingDamageEvent> cancel=event->{if(event.getEntity()==boss)event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LivingIncomingDamageEvent.class,cancel);
            try{
                boss.extremeMemory().cooldown=37;boss.setTarget(null);
                attack(player,boss);
                h.assertTrue(boss.getHealth()==boss.getMaxHealth()&&boss.extremeMemory().contribution.isEmpty(),
                        "A rejected native damage transaction never counts as boss contribution");
                h.assertTrue(boss.extremeMemory().cooldown==37&&boss.getTarget()==null,
                        "Canceled melee damage cannot refresh the timer or manufacture a challenger");
            }finally{NeoForge.EVENT_BUS.unregister(cancel);player.discard();}
        }
        h.succeed();
    }

    private static Player player(GameTestHelper h,ExtremeMobs.ZombieVariant boss){
        Player player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(boss.position().add(1,0,0));player.setNoGravity(true);player.setOnGround(true);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);player.setHealth(100);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ExtremeContent.ITEM_ENTRIES.get("club_mithril").get()));
        // Equip through native living ticks; allow the first strike to fully charge at the real 1.1/s speed.
        for(int tick=0;tick<25;tick++){player.tick();player.setOnGround(true);}
        return player;
    }

    private static void attack(Player player,ExtremeMobs.ZombieVariant boss){
        for(int tick=0;tick<20;tick++){player.tick();player.setOnGround(true);}
        player.setPos(boss.position().add(1,0,0));boss.invulnerableTime=0;player.attack(boss);
    }
}
