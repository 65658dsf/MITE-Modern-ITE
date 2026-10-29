package io.github.ningmeng123.modernite.entity;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual black infernal detonation leaves protected, bounded fire after its crater finishes. @author NingMeng123 */
@GameTestHolder("modern_ite_infernal_fire")
@PrefixGameTestTemplate(false)
public final class InfernalExplosionFireGameTests {
    private InfernalExplosionFireGameTests() {}

    @GameTest(template="gear_empty", batch="infernal_fire_adult", timeoutTicks=1200)
    public static void realAdultIgnitesSurvivingGroundAndTheFinishedCraterWithinFortyBlocks(GameTestHelper h) {
        var fixture = new Fixture(h, 0, false);
        try {
            for (int x=-5; x<=5; x++) for (int z=-5; z<=5; z++)
                fixture.block(fixture.ground.offset(x,0,z), Blocks.STONE.defaultBlockState());
            fixture.explode();
            h.assertTrue(InfernalExplosionDestruction.remainingJobs(fixture.level)>0,
                    "The real detonation queues its destruction and later surface ignition");
            h.assertTrue(fixture.fires()==0, "No fire appears before deferred crater destruction begins");
            InfernalExplosionGameTestSupport.finishAfterJobs(h, fixture.level, () -> {
                fixture.assertFireBounds(h,40);
                int craterFires=0;
                for (int x=-5; x<=5; x++) for (int z=-5; z<=5; z++) {
                    var state=fixture.level.getBlockState(fixture.ground.offset(x,0,z));
                    h.assertTrue(state.isAir()||state.is(Blocks.FIRE), "The selected stone is physically removed before ignition");
                    if(state.is(Blocks.FIRE))craterFires++;
                }
                h.assertTrue(craterFires>5, "The cleared stone layer leaves real fire on the surviving obsidian below");
            }, fixture::close);
        } catch(Throwable failure) { fixture.close(); throw failure; }
    }

    @GameTest(template="gear_empty", batch="infernal_fire_child", timeoutTicks=1200)
    public static void savedMiniatureIgnitionStopsAtTheExistingTwentyBlockBoundary(GameTestHelper h) {
        var fixture=new Fixture(h,1,true);
        try {
            fixture.explode();
            InfernalExplosionGameTestSupport.finishAfterJobs(h,fixture.level,()->fixture.assertFireBounds(h,20),fixture::close);
        } catch(Throwable failure) { fixture.close(); throw failure; }
    }

    @GameTest(template="gear_empty", batch="infernal_fire_protection", timeoutTicks=1200)
    public static void protectionAndBlocksPlacedAfterSelectionPreventIgnition(GameTestHelper h) {
        var fixture=new Fixture(h,2,false);
        Consumer<ExplosionEvent.Detonate> protect=event->{
            if(event.getExplosion().getDirectSourceEntity()==fixture.source)
                event.getAffectedBlocks().removeIf(pos->pos.getX()>=fixture.ground.getX());
        };
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ExplosionEvent.Detonate.class,protect);
            try { fixture.explode(); } finally { NeoForge.EVENT_BUS.unregister(protect); }
            for(int x=-16;x<=-8;x++)for(int z=-6;z<=6;z++)
                fixture.block(fixture.ground.offset(x,0,z),Blocks.OBSIDIAN.defaultBlockState());
            InfernalExplosionGameTestSupport.finishAfterJobs(h,fixture.level,()->{
                h.assertTrue(fixture.fires()>100,"Unprotected surface locations still ignite");
                for(int x=0;x<=42;x++)for(int z=-42;z<=42;z++)
                    h.assertTrue(!fixture.level.getBlockState(fixture.ground.offset(x,0,z)).is(Blocks.FIRE),
                            "Removing air from Detonate protects that exact surface location");
                for(int x=-16;x<=-8;x++)for(int z=-6;z<=6;z++)
                    h.assertTrue(fixture.level.getBlockState(fixture.ground.offset(x,0,z)).is(Blocks.OBSIDIAN),
                            "Deferred ignition never overwrites a block placed after the explosion selected air");
            },fixture::close);
        } catch(Throwable failure) { NeoForge.EVENT_BUS.unregister(protect); fixture.close(); throw failure; }
    }

    @GameTest(template="gear_empty", batch="infernal_fire_rules", timeoutTicks=1200)
    public static void cancelledOrMobGriefingDisabledExplosionsLeaveNoFire(GameTestHelper h) {
        var fixture=new Fixture(h,3,false);
        Consumer<ExplosionEvent.Start> cancel=event->{if(event.getExplosion().getDirectSourceEntity()==fixture.source)event.setCanceled(true);};
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ExplosionEvent.Start.class,cancel);
            try { fixture.explode(); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(fixture.fires()==0&&InfernalExplosionDestruction.remainingJobs(fixture.level)==0,
                    "Cancelling the actual creeper explosion prevents ignition and deferred work");
            fixture.source=fixture.create(false);
            fixture.rule(GameRules.RULE_MOBGRIEFING,false);
            fixture.explode();
            h.assertTrue(fixture.fires()==0&&InfernalExplosionDestruction.remainingJobs(fixture.level)==0,
                    "mobGriefing=false prevents both ground ignition and destruction work");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); fixture.close(); }
        h.succeed();
    }

    @GameTest(template="gear_empty", batch="infernal_fire_flag", timeoutTicks=1200)
    public static void nonFlamingCalculatorAndUnsupportedOrWaterSurfacesDoNotIgnite(GameTestHelper h) {
        var fixture=new Fixture(h,4,false);
        try {
            Creeper ordinary=EntityType.CREEPER.create(fixture.level);
            ordinary.moveTo(fixture.source.position());
            var center=ordinary.position().add(0,ordinary.getBbHeight()/4,0);
            fixture.level.explode(ordinary, Explosion.getDefaultDamageSource(fixture.level,ordinary),
                    new InfernalExplosionCalculator(ordinary,40F), center.x,center.y,center.z,20F,false,Level.ExplosionInteraction.MOB);
            ordinary.discard();
            h.assertTrue(InfernalExplosionDestruction.remainingJobs(fixture.level)==0&&fixture.fires()==0,
                    "A false fire flag does not enqueue air-only ignition targets");
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++) {
                if(x>=0)fixture.block(fixture.ground.offset(x,-2,z),Blocks.OBSIDIAN.defaultBlockState());
                // A solid rim and bottom keep the real water from leaking beyond the restored fixture.
                fixture.block(fixture.ground.offset(x,-1,z),(x< -1?Blocks.AIR:x==-1?Blocks.OBSIDIAN:Blocks.WATER).defaultBlockState());
            }
            fixture.explode();
            InfernalExplosionGameTestSupport.finishAfterJobs(h,fixture.level,()->{
                h.assertTrue(fixture.fires()>100,"The surrounding supported surface remains a positive ignition control");
                for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)
                    if(x!=-1)h.assertTrue(!fixture.level.getBlockState(fixture.ground.offset(x,0,z)).is(Blocks.FIRE),
                            "Air and water cannot support the old explosion's ground fire");
            },fixture::close);
        } catch(Throwable failure) { fixture.close(); throw failure; }
    }

    private static final class Fixture implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final ServerLevel level;
        final BlockPos ground;
        final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
        final boolean griefing,fireTick;
        final Runnable release;
        Creeper source;
        boolean closed;
        Fixture(GameTestHelper h,int slot,boolean child) {
            site=new SpawnBalanceGameTests.Site(h);level=site.level;site.date(256,256,true);
            ground=new BlockPos(100_000+slot*128,240,100_000);
            release=InfernalExplosionGameTestSupport.holdChunks(level,new AABB(ground).inflate(44));
            griefing=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            fireTick=level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
            rule(GameRules.RULE_MOBGRIEFING,true);rule(GameRules.RULE_DOFIRETICK,false);
            for(int x=-42;x<=42;x++)for(int z=-42;z<=42;z++) {
                block(ground.offset(x,-1,z),Blocks.OBSIDIAN.defaultBlockState());
                block(ground.offset(x,0,z),Blocks.AIR.defaultBlockState());
                block(ground.offset(x,1,z),Blocks.AIR.defaultBlockState());
            }
            source=create(child);
        }
        Creeper create(boolean child) {
            Creeper creeper=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
            creeper.moveTo(ground.getX()+.5,ground.getY(),ground.getZ()+.5,0F,0F);creeper.setNoGravity(true);
            if(child)MobSplitting.markChild(creeper);
            if(!level.addFreshEntity(creeper))throw new AssertionError("The real black infernal creeper is admitted to the test world");
            return creeper;
        }
        void block(BlockPos pos,BlockState state) {
            previous.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,state,2);
        }
        void rule(GameRules.Key<GameRules.BooleanValue> key,boolean value) { level.getGameRules().getRule(key).set(value,level.getServer()); }
        void explode() {
            try {
                Method method=Creeper.class.getDeclaredMethod("explodeCreeper");method.setAccessible(true);method.invoke(source);
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Cannot invoke the real creeper explosion",failure); }
        }
        int fires() {
            int count=0;
            for(var pos:previous.keySet())if(level.getBlockState(pos).is(Blocks.FIRE))count++;
            return count;
        }
        void assertFireBounds(GameTestHelper h,int radius) {
            int fireCount=0,eligibleCount=0;
            var center=source.position().add(0,source.getBbHeight()/4,0);
            for(int x=-42;x<=42;x++)for(int z=-42;z<=42;z++)for(int y=0;y<=1;y++) {
                var pos=ground.offset(x,y,z);var state=level.getBlockState(pos);
                if(pos.distToCenterSqr(center)<=radius*radius&&level.getBlockState(pos.below()).isSolidRender(level,pos.below()))eligibleCount++;
                if(state.is(Blocks.FIRE)) {
                    fireCount++;
                    h.assertTrue(pos.distToCenterSqr(center)<=radius*radius,"Every residual flame remains inside the physical spherical blast boundary");
                    h.assertTrue(level.getBlockState(pos.below()).isSolidRender(level,pos.below()),"Every flame rests on surviving solid terrain");
                }
            }
            h.assertTrue(fireCount>100&&fireCount<eligibleCount,
                    "Old one-in-three ignition leaves a substantial scattered surface, fire="+fireCount+", eligible="+eligibleCount);
        }
        @Override public void close() {
            if(closed)return;closed=true;
            previous.forEach((pos,state)->level.setBlock(pos,state,2));
            rule(GameRules.RULE_MOBGRIEFING,griefing);rule(GameRules.RULE_DOFIRETICK,fireTick);
            site.close();release.run();
        }
    }
}
