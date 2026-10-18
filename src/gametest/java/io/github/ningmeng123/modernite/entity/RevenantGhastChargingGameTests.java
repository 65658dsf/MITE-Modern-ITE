package io.github.ningmeng123.modernite.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The synchronized texture flag follows real shots without changing their cadence. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class RevenantGhastChargingGameTests {
    private RevenantGhastChargingGameTests(){}

    private static int shots(GameTestHelper h,ExtremeMobs.GhastVariant ghast){
        return h.getLevel().getEntitiesOfClass(InfernalFireball.class,ghast.getBoundingBox().inflate(32),
            fireball->fireball.getOwner()==ghast).size();
    }

    @GameTest(template="gear_empty")
    public static void chargingTracksPreFireShotsAndTargetLossWithoutChangingCadence(GameTestHelper h){
        var ghast=(ExtremeMobs.GhastVariant)ExtremeMobs.type(ExtremeKind.REVENANT_GHAST).create(h.getLevel());
        h.assertTrue(ghast!=null,"The registered revenant ghast creates in a real server level");
        ghast.moveTo(h.absolutePos(new BlockPos(1,3,1)).getCenter());
        var target=h.makeMockPlayer(GameType.SURVIVAL);target.moveTo(ghast.position().add(8,0,0));
        ghast.setTarget(target);MobAbilities.initialize(ghast,ghast);
        var state=ghast.extremeMemory();state.cooldown=11;

        MobAbilities.tick(ghast,ghast);
        h.assertTrue(state.cooldown==10&&!ghast.isCharging(),"The ten-tick boundary remains uncharged");
        MobAbilities.tick(ghast,ghast);
        h.assertTrue(state.cooldown==9&&ghast.isCharging(),"The actual native synchronized flag enters the pre-fire window");
        for(int i=0;i<9;i++)MobAbilities.tick(ghast,ghast);
        h.assertTrue(state.cooldown==60&&!ghast.isCharging()&&shots(h,ghast)==1,
            "Firing creates one real projectile, restores the existing cooldown and clears charging");

        for(int i=0;i<59;i++)MobAbilities.tick(ghast,ghast);
        h.assertTrue(state.cooldown==1&&ghast.isCharging()&&shots(h,ghast)==1,"No second shot occurs before sixty ticks");
        MobAbilities.tick(ghast,ghast);
        h.assertTrue(state.cooldown==60&&!ghast.isCharging()&&shots(h,ghast)==2,"The next actual shot still occurs exactly sixty ticks later");

        state.cooldown=10;MobAbilities.tick(ghast,ghast);
        h.assertTrue(ghast.isCharging(),"The next pre-fire window starts normally");
        ghast.setTarget(null);MobAbilities.tick(ghast,ghast);
        h.assertTrue(state.cooldown==8&&!ghast.isCharging()&&shots(h,ghast)==2,
            "Losing the target clears the synchronized flag even on the early-return path, without resetting cooldown or firing");
        h.succeed();
    }
}
