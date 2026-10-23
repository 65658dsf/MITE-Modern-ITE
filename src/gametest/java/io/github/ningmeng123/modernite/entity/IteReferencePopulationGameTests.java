package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.ExtremeRules;
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
        try(var settings=new IteReferenceSpawnGameTests.Settings();var site=new SpawnBalanceGameTests.Site(h)) {
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
        try(var settings=new IteReferenceSpawnGameTests.Settings();var site=new SpawnBalanceGameTests.Site(h)) {
            site.date(3,3,true);var player=site.player(0);
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).size()==289,"The uncrowded source area is eligible");
            for(int i=0;i<8;i++) {
                Mob mob=site.mob(EntityType.ZOMBIE);
                h.assertTrue(site.level.addFreshEntity(mob),"An existing near-player inhabitant enters the real entity manager");
            }
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).size()==289,"Exactly eight is still eligible: the source comparison is greater-than");
            Mob ninth=site.mob(EntityType.ZOMBIE);h.assertTrue(site.level.addFreshEntity(ninth),"An existing ninth inhabitant is retained");
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).isEmpty(),"Nine nearby monsters stop this player's whole source area");
            settings.set("natural_local_hostile_limit",10);
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).size()==289,"Editing the ordinary natural crowd threshold immediately reopens the actual candidate area");
            settings.set("natural_local_hostile_limit",8);site.date(8,8,true);site.level.setDayTime(7*24_000L+18_000L);
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).size()==289,"The default full-night threshold admits the nine-monster control");
            settings.set("full_moon_natural_local_hostile_limit",8);
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).isEmpty(),"Editing the full-night threshold closes the actual candidate area");
            site.date(32,32,true);site.level.setDayTime(31*24_000L+18_000L);
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).size()==289,"The separate blood-night threshold admits the same nine-monster control");
            settings.set("blood_moon_natural_local_hostile_limit",8);
            h.assertTrue(R196MobSpawning.eligibleChunks(site.level,true).isEmpty(),"Editing the blood-night threshold also closes the actual candidate area");
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

    @GameTest(template="gear_empty",batch="reference_population")
    public static void editableCapAndAttemptFieldsApplyWithoutChangingTheSourceRules(GameTestHelper h) {
        try(var settings=new IteReferenceSpawnGameTests.Settings();var site=new SpawnBalanceGameTests.Site(h)) {
            site.date(10,10,true);site.player(0);
            var policy=MiteExtensions.policy();
            var context=new MiteExtensions.SpawnContext(site.level,R196SpawnRules.Category.MONSTER,false);
            settings.set("mob_max_spawn_count_chunk_base",20);
            settings.set("mob_max_spawn_count_increase_per_day",2D);
            settings.set("mob_max_spawn_count_day_minimum",0);
            settings.set("mob_max_spawn_count_day_maximum",0);
            h.assertTrue(ExtremeRules.hostileCap(site.level,256,false)==40&&ExtremeRules.hostileCap(site.level,256,true)==80,
                    "Editing chunk and day growth updates both independent budgets immediately");
            settings.set("deep_hostile_cap_multiplier",3D);
            h.assertTrue(ExtremeRules.hostileCap(site.level,256,false)==40&&ExtremeRules.hostileCap(site.level,256,true)==120,
                    "Editing the deep multiplier changes only the deep budget");
            settings.set("deep_hostile_cap_multiplier",2D);
            settings.set("mob_max_spawn_count_day_minimum",30);
            h.assertTrue(ExtremeRules.hostileCap(site.level,256,false)==50,"The edited day minimum is active");
            settings.set("mob_max_spawn_count_day_maximum",35);site.date(20,20,true);
            h.assertTrue(ExtremeRules.hostileCap(site.level,256,false)==55,"The edited day maximum is active");
            settings.set("hostile_spawn_groups",7);settings.set("blood_moon_hostile_spawn_groups",9);settings.set("hostile_spawn_attempts",2);
            h.assertTrue(policy.spawnGroups(context,3)==7&&policy.spawnAttempts(context,null,4)==2,"Ordinary group and attempt controls alter the live source scheduler");
            settings.set("blood_moon_max_hostile_fraction",3D);site.date(32,32,true);
            h.assertTrue(ExtremeRules.hostileCap(site.level,256,false)==95&&ExtremeRules.hostileCap(site.level,256,true)==190,
                    "The edited blood multiplier applies to the chunk contribution without multiplying the clamped day addition");
            h.assertTrue(policy.spawnGroups(context,3)==9&&policy.spawnAttempts(context,null,4)==2,"The blood group control is active independently");
            h.assertTrue(policy.independentHostileBudgets(site.level,false)&&R196MobSpawning.referenceSpawning(site.level),
                    "Editing every cap and attempt field retains the one source scheduler and independent height budgets");
            settings.set("mob_max_spawn_count_chunk_base",0);settings.set("mob_max_spawn_count_increase_per_day",0D);
            settings.set("mob_max_spawn_count_day_minimum",0);settings.set("mob_max_spawn_count_day_maximum",0);
            Mob candidate=site.mob(EntityType.ZOMBIE);candidate.setPos(site.origin.x,120,site.origin.z);
            h.assertFalse(SpawnPopulation.reserveNatural(candidate),"A configured zero budget stops an actual new natural reservation");
            settings.set("hostile_spawn_groups",0);settings.set("blood_moon_hostile_spawn_groups",0);settings.set("hostile_spawn_attempts",0);
            h.assertTrue(policy.spawnGroups(context,3)==0&&policy.spawnAttempts(context,null,4)==0,"Zero groups and attempts disable the active scheduling loop");
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
}
