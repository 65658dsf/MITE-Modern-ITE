package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.ExtremeRules;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Integration of source budgets, pending entities and the preserved split admission. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IteReferencePopulationGameTests {
    private IteReferencePopulationGameTests() {}

    @GameTest(template="gear_empty",batch="reference_population")
    public static void bothHeightBudgetsIncludePendingEntitiesWithoutDoubleSpending(GameTestHelper h) {
        try(var preset=new Preset();var site=new SpawnBalanceGameTests.Site(h)) {
            site.date(3,3,true);site.player(0);
            h.assertTrue(R196MobSpawning.referenceSpawning(site.level),"The addon selects the actual base reference scheduler");
            h.assertTrue(MiteExtensions.policy().independentHostileBudgets(site.level,false),"ITE reference allowances are independent");
            h.assertTrue(ExtremeRules.hostileCap(site.level,289,false)==59&&ExtremeRules.hostileCap(site.level,289,true)==118,
                    "Day three starts from the source chunk budget, not a one hundred day floor");
            int shallow=existing(site.level,false),deep=existing(site.level,true);
            for(int i=shallow;i<59;i++) {
                Mob mob=site.mob(EntityType.ZOMBIE);mob.setPos(site.origin.x,120,site.origin.z);
                h.assertTrue(SpawnPopulation.reserveNatural(mob),"A shallow pending reservation occupies slot "+i);
                h.assertTrue(SpawnPopulation.reserveNatural(mob),"Rechecking finalize/join does not spend the same UUID twice");
            }
            Mob extra=site.mob(EntityType.ZOMBIE);extra.setPos(site.origin.x,120,site.origin.z);
            h.assertFalse(SpawnPopulation.reserveNatural(extra),"A sixtieth shallow natural is refused");
            for(int i=deep;i<118;i++) {
                Mob mob=site.mob(EntityType.ZOMBIE);mob.setPos(site.origin.x,-49,site.origin.z);
                h.assertTrue(SpawnPopulation.reserveNatural(mob),"The independent deep allowance still admits slot "+i);
            }
            Mob deepExtra=site.mob(EntityType.ZOMBIE);deepExtra.setPos(site.origin.x,-49,site.origin.z);
            h.assertFalse(SpawnPopulation.reserveNatural(deepExtra),"The deep pending allowance is also bounded");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="reference_population")
    public static void surfaceCrowdingStopsTheWholeAreaAndSplitsKeepTheirOwnLimit(GameTestHelper h) {
        try(var preset=new Preset();var site=new SpawnBalanceGameTests.Site(h)) {
            site.date(3,3,true);var player=site.player(0);
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).size()==289,"The uncrowded source area is eligible");
            for(int i=0;i<8;i++) {
                Mob mob=site.mob(EntityType.ZOMBIE);
                h.assertTrue(site.level.addFreshEntity(mob),"An existing near-player inhabitant enters the real entity manager");
            }
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).size()==289,"Exactly eight is still eligible: the source comparison is greater-than");
            Mob ninth=site.mob(EntityType.ZOMBIE);h.assertTrue(site.level.addFreshEntity(ninth),"An existing ninth inhabitant is retained");
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).isEmpty(),"Nine nearby monsters stop this player's whole source area");
            Mob parent=site.mob(EntityType.CREEPER);
            for(int i=0;i<8;i++) {
                Mob child=site.mob(EntityType.CREEPER);MobSplitting.markChild(child);
                h.assertTrue(SpawnPopulation.reserveSupport(child,parent,false),"Split admission retains its independent owner/local limit: "+i);
            }
            h.assertFalse(SpawnPopulation.reserveSupport(site.mob(EntityType.CREEPER),parent,false),"A ninth live child is still refused");
            h.assertTrue(SpawnPopulation.supportCount(parent)==8,"Natural crowd protection does not lower an existing split's eight follower allowance");
            site.date(32,32,true);
            h.assertTrue(ExtremeRules.localHostileLimit(site.level)==48,"The preserved split admission still uses the configured blood limit");
        }
        h.succeed();
    }

    private static int existing(ServerLevel level,boolean deep) {
        int count=0;
        for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()&&!mob.isRemoved()
                &&!mob.isSpawnCancelled()&&R196MobSpawning.category(mob)==R196SpawnRules.Category.MONSTER
                &&(R196SpawnRules.sourceY(mob.getBlockY(),R196MobSpawning.underworld(level))<60)==deep)count++;
        return count;
    }
    private static final class Preset implements AutoCloseable {
        private final Object previous=IteConfig.localSnapshot().get("reference_spawning");
        Preset(){IteConfig.setting("reference_spawning").set(true);IteConfig.refresh();}
        @Override public void close(){IteConfig.setting("reference_spawning").set(previous);IteConfig.refresh();}
    }
}
