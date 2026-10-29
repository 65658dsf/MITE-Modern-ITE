package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Player-reported balance changes through real admission, crafting, digging and explosion paths. @author NingMeng123 */
@GameTestHolder("modern_ite_feedback")
@PrefixGameTestTemplate(false)
public final class FeedbackMobBalanceGameTests {
    private FeedbackMobBalanceGameTests() {}

    @GameTest(template="gear_empty",batch="feedback_balance")
    public static void tyrantsAndDragonZombiesUnlockOnlyAfterThirtyTwoCompleteDays(GameTestHelper h) {
        try(var site=new SpawnBalanceGameTests.Site(h);var settings=new IteReferenceSpawnGameTests.Settings()) {
            settings.set("extreme_mob_spawn_limit_day",3);
            for(int day:new int[]{3,32,33}) {
                site.date(day,day,true);
                var pos=BlockPos.containing(site.origin);
                for(var kind:List.of(ExtremeKind.DRAGON_ZOMBIE,ExtremeKind.SKELETON_ELITE_LORD)) {
                    var type=ExtremeMobs.type(kind);
                    h.assertTrue(SpawnPopulation.automaticAllowed(site.level,type)==(day>=33),"Every automatic admission respects day33: "+kind+" day="+day);
                    h.assertTrue(MobAbilities.maySpawn(kind,site.level,pos)==(day>=33),"The natural candidate follows its species floor: "+kind);
                    Mob candidate=site.mob(type);
                    EventHooks.finalizeMobSpawn(candidate,site.level,site.level.getCurrentDifficultyAt(pos),MobSpawnType.MOB_SUMMONED,null);
                    h.assertTrue(candidate.isSpawnCancelled()==(day<33),"Actual finalize cannot bypass the species date: "+kind);
                }
            }
            settings.set("extreme_mob_spawn_limit_day",96);
            h.assertFalse(SpawnPopulation.automaticAllowed(site.level,ExtremeMobs.type(ExtremeKind.DRAGON_ZOMBIE)),"An explicitly later shared date still takes effect");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="feedback_balance")
    public static void dragonZombieRarityIsAnIndependentSpeciesFactorAfterConfiguredWeights(GameTestHelper h) {
        var rates=io.github.ningmeng123.modernite.config.IteSpawnConfig.effectiveSnapshot();
        var weights=io.github.ningmeng123.modernite.config.IteSpawnConfig.effectiveWeights();
        boolean remote=rates!=io.github.ningmeng123.modernite.config.IteSpawnConfig.localSnapshot()
                ||weights!=io.github.ningmeng123.modernite.config.IteSpawnConfig.localWeights();
        try(var site=new SpawnBalanceGameTests.Site(h)) {
            io.github.ningmeng123.modernite.config.IteSpawnConfig.receiveServerSnapshot(Map.of(),Map.of("modern_ite:dragon_zombie",100D));
            var ordinary=new io.github.ningmeng123.mite.worldgen.R196MobSpawning.Entry(EntityType.ZOMBIE,"tw",R196SpawnRules.Category.MONSTER,100,1,1,1,false);
            var dragon=new io.github.ningmeng123.mite.worldgen.R196MobSpawning.Entry(ExtremeMobs.type(ExtremeKind.DRAGON_ZOMBIE),null,R196SpawnRules.Category.MONSTER,3,1,1,1,true);
            var random=RandomSource.create(196);int dragons=0;
            for(int i=0;i<4096;i++)if(io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns.choose(site.level,BlockPos.containing(site.origin),R196SpawnRules.Category.MONSTER,List.of(ordinary,dragon),random,true).type()==dragon.type())dragons++;
            h.assertTrue(dragons>1200&&dragons<1500,"Equal configured weights become 100:50 after the independent half-factor; observed "+dragons+"/4096");
            h.assertTrue(io.github.ningmeng123.modernite.config.IteSpawnConfig.effectiveSnapshot().isEmpty(),"Species balancing leaves the user's configured rate list unchanged");
        }finally{
            if(remote)io.github.ningmeng123.modernite.config.IteSpawnConfig.receiveServerSnapshot(rates,weights);
            else io.github.ningmeng123.modernite.config.IteSpawnConfig.clearServerSnapshot();
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="feedback_balance")
    public static void bloodMoonPoolIncludesGiantZombiesAndOrdinaryNightsExcludeThem(GameTestHelper h) {
        var terrain=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try(var site=new SpawnBalanceGameTests.Site(h)) {
            var pos=BlockPos.containing(site.origin);
            var context=new MiteExtensions.SpawnContext(site.level,R196SpawnRules.Category.MONSTER,false);
            site.date(32,32,true);site.level.setDayTime(31*24_000L+18_000L);
            site.level.updateSkyBrightness();site.player(40);
            for(BlockPos point:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,14,3))) {
                terrain.put(point.immutable(),site.level.getBlockState(point));
                site.level.setBlock(point,point.getY()==pos.getY()-1||point.getY()==pos.getY()+14
                        ?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            io.github.ningmeng123.mite.worldgen.R196MobSpawning.Entry selected=null;int selectedSeed=-1;
            for(int seed=0;seed<8192;seed++) {
                var entry=io.github.ningmeng123.mite.worldgen.R196MobSpawning.select(site.level,pos,R196SpawnRules.Category.MONSTER,RandomSource.create(seed),false,true);
                if(entry!=null&&entry.type()==EntityType.GIANT){selected=entry;selectedSeed=seed;break;}
            }
            h.assertTrue(selected!=null&&selected.sourceClass().equals("tk")&&!selected.modern(),"The real complete natural selector chooses the source giant, seed="+selectedSeed);
            Double y=io.github.ningmeng123.mite.worldgen.R196MobSpawning.positionY(site.level,pos,R196SpawnRules.Category.MONSTER);
            h.assertTrue(y!=null,"The same natural placement helper finds the supporting platform");
            Mob giant=site.mob(selected.type());giant.moveTo(pos.getX()+.5,y,pos.getZ()+.5,0,0);giant.setNoAi(false);
            h.assertTrue(site.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos)==0,"The natural candidate has no block lighting");
            h.assertTrue(((net.minecraft.world.entity.PathfinderMob)giant).getWalkTargetValue(pos,site.level)<0,"The dark candidate exercises vanilla Giant's incompatible bright-path preference");
            h.assertTrue(io.github.ningmeng123.mite.worldgen.R196MobSpawning.qualifiesWithEvent(giant,site.level,MobSpawnType.NATURAL,true,selected.modern()),"The actual positioned natural qualification overrides vanilla Giant brightness and source light preferences");
            var prepared=io.github.ningmeng123.mite.worldgen.R196MobSpawning.prepareSourceFinalization(giant,site.level,null,MobSpawnType.NATURAL);
            var committed=io.github.ningmeng123.mite.worldgen.R196MobSpawning.finalizeNaturalMonster(site.level,prepared.mob(),prepared.group(),1);
            h.assertTrue(committed.monsters()==1&&giant.isAddedToLevel()&&!giant.isSpawnCancelled()&&giant.getSpawnType()==MobSpawnType.NATURAL,"Real finalize, reservation and join commit exactly one natural blood-moon giant");
            h.assertTrue(giant.goalSelector.getAvailableGoals().stream().anyMatch(goal->goal.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal),"The joined giant has its installed melee AI");
            site.date(33,33,true);site.level.setDayTime(32*24_000L+18_000L);
            h.assertFalse(IteReferenceSpawns.pool(context,pos,List.of()).stream().anyMatch(e->e.type()==EntityType.GIANT),"Giant zombies are absent on an ordinary night");
            Mob ordinaryCandidate=site.mob(EntityType.GIANT);ordinaryCandidate.moveTo(giant.position());
            h.assertFalse(io.github.ningmeng123.mite.worldgen.R196MobSpawning.qualifiesWithEvent(ordinaryCandidate,site.level,MobSpawnType.NATURAL,true,false),"An ordinary-night candidate cannot pass the same real positioned qualification");
            h.assertTrue(giant.isAddedToLevel()&&giant.isAlive(),"The next ordinary date preserves the existing natural giant");
        }finally{
            terrain.forEach((pos,state)->h.getLevel().setBlock(pos,state,2));
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="feedback_balance")
    public static void pumpkinZombieCannotPlanToRemoveAnyTorchVariant(GameTestHelper h) {
        try(var site=new SpawnBalanceGameTests.Site(h)) {
            Zombie pumpkin=(Zombie)site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_PUMPKIN));
            pumpkin.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            var goal=new R196DiggerGoal(pumpkin);var pos=h.absolutePos(new BlockPos(0,2,0));
            for(var torch:List.of(Blocks.TORCH,Blocks.WALL_TORCH,Blocks.SOUL_TORCH,Blocks.SOUL_WALL_TORCH,Blocks.REDSTONE_TORCH,Blocks.REDSTONE_WALL_TORCH)) {
                site.level.setBlock(pos,torch.defaultBlockState(),2);
                h.assertFalse(goal.canDestroyMaterial(pos),"The production planner protects "+torch);
            }
            site.level.setBlock(pos,Blocks.DIRT.defaultBlockState(),2);
            h.assertTrue(goal.canDestroyMaterial(pos),"Its other normal digging remains available");
            site.level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="feedback_balance")
    public static void blackInfernalBlastHasFortyBlockReachAndNeverCreatesSplitChildren(GameTestHelper h) {
        try(var site=new SpawnBalanceGameTests.Site(h)) {
            site.date(256,256,true);
            Creeper parent=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
            AtomicReference<Explosion> observed=new AtomicReference<>();
            Consumer<ExplosionEvent.Start> listener=event->{if(event.getExplosion().getDirectSourceEntity()==parent&&!event.isCanceled()){observed.set(event.getExplosion());event.setCanceled(true);}};
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,ExplosionEvent.Start.class,listener);
            try {
                Method detonate=Creeper.class.getDeclaredMethod("explodeCreeper");detonate.setAccessible(true);detonate.invoke(parent);
                h.assertTrue(observed.get()!=null&&Math.abs(observed.get().radius()*2F-40F)<.001F,"Real native detonation selects entities and applies knockback within forty blocks");
            }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
            finally{NeoForge.EVENT_BUS.unregister(listener);}
            for(int seed=0;seed<256;seed++){parent.getRandom().setSeed(seed);MobSplitting.trySplit(parent);}
            h.assertTrue(site.level.getEntitiesOfClass(Mob.class,parent.getBoundingBox().inflate(16),mob->mob.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)&&parent.getUUID().equals(mob.getPersistentData().getUUID(SpawnPopulation.SUMMONER))).isEmpty(),"Neither native explosion nor repeated death callbacks create black creeper children");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="infernal_crater",timeoutTicks=1200)
    public static void blackInfernalCraterIsAnActualFortyBlockCircleAndPreservesBlastProofBlocks(GameTestHelper h) {
        var terrain=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        boolean griefing=h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean blockDrops=h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        var site=new SpawnBalanceGameTests.Site(h);
        Runnable releaseChunks=InfernalExplosionGameTestSupport.holdChunks(site.level,new net.minecraft.world.phys.AABB(site.origin,site.origin).inflate(44));
        Runnable cleanup=()->{
            terrain.forEach((pos,state)->h.getLevel().setBlock(pos,state,2));
            h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing,h.getLevel().getServer());
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(blockDrops,h.getLevel().getServer());
            site.close();
            releaseChunks.run();
        };
        try {
            site.date(256,256,true);
            site.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,site.level.getServer());
            site.level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,site.level.getServer());
            Creeper creeper=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
            BlockPos floor=BlockPos.containing(site.origin).below();
            var bedrock=floor.offset(12,0,0);var obsidian=floor.offset(13,0,0);
            for(int x=-42;x<=42;x++)for(int z=-42;z<=42;z++){
                var pos=floor.offset(x,0,z);terrain.put(pos,site.level.getBlockState(pos));
                site.level.setBlock(pos,(pos.equals(bedrock)?Blocks.BEDROCK:pos.equals(obsidian)?Blocks.OBSIDIAN:Blocks.STONE).defaultBlockState(),2);
            }
            var center=creeper.position().add(0,creeper.getBbHeight()/4,0);
            site.level.explode(creeper,creeper.getX(),creeper.getY(),creeper.getZ(),3F,Level.ExplosionInteraction.MOB);
            h.assertTrue(InfernalExplosionDestruction.remainingJobs(site.level)>0,"Terrain destruction is queued instead of blocking the explosion tick");
            InfernalExplosionGameTestSupport.finishAfterJobs(h,site.level,()->{
                for(var pos:terrain.keySet()){
                    boolean protectedBlock=pos.equals(bedrock)||pos.equals(obsidian);
                    boolean removed=pos.distToCenterSqr(center)<=40D*40D&&!protectedBlock;
                    h.assertTrue(site.level.getBlockState(pos).isAir()==removed,"The actual stone platform follows the spherical radius40 cross-section at "+pos.subtract(floor)+", state="+site.level.getBlockState(pos)+", loaded="+site.level.hasChunkAt(pos));
                }
                h.assertTrue(site.level.getBlockState(bedrock).is(Blocks.BEDROCK)&&site.level.getBlockState(obsidian).is(Blocks.OBSIDIAN),"Bedrock and obsidian survive inside the crater");
            },cleanup);
        }catch(Throwable failure){cleanup.run();throw failure;}
    }

    @GameTest(template="gear_empty",batch="infernal_entity_range",timeoutTicks=500)
    public static void blackInfernalDamageAndKnockbackStopAtFortyAndLegacyChildrenAtTwenty(GameTestHelper h) {
        boolean griefing=h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        var site=new SpawnBalanceGameTests.Site(h);
        var bounds=new net.minecraft.world.phys.AABB(site.origin,site.origin).inflate(44);
        Runnable releaseChunks=InfernalExplosionGameTestSupport.holdChunks(site.level,bounds);
        boolean[] closed={false};
        Runnable cleanup=()->{
            if(closed[0])return;closed[0]=true;
            site.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing,site.level.getServer());
            site.close();releaseChunks.run();
        };
        try {
            site.date(256,256,true);
            site.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,site.level.getServer());
            infernalEntityRange(h,site,bounds,false,cleanup);
        }catch(Throwable failure){cleanup.run();throw failure;}
    }

    private static void infernalEntityRange(GameTestHelper h,SpawnBalanceGameTests.Site site,net.minecraft.world.phys.AABB bounds,boolean child,Runnable cleanup) {
        Creeper creeper=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
        if(child)MobSplitting.markChild(creeper);
        float radius=child?20F:40F;
        Mob inside=site.mob(EntityType.COW),outside=site.mob(EntityType.COW);
        inside.setPos(site.origin.add(radius-1,0,0));outside.setPos(site.origin.add(radius+1,0,0));
        inside.setNoGravity(true);outside.setNoGravity(true);
        site.level.getChunkAt(inside.blockPosition());site.level.getChunkAt(outside.blockPosition());
        InfernalExplosionGameTestSupport.whenPositionsEntityTicking(h,site.level,
            List.of(inside.blockPosition(),outside.blockPosition()),()->{
        h.assertTrue(site.level.addFreshEntity(inside)&&site.level.addFreshEntity(outside),"Both real range-test targets join the loaded level");
        InfernalExplosionGameTestSupport.whenEntityVisible(h,site.level,inside,bounds,()->
            InfernalExplosionGameTestSupport.whenEntityVisible(h,site.level,outside,bounds,()->{
            try {
                inside.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);outside.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                float insideHealth=inside.getHealth(),outsideHealth=outside.getHealth();
                site.level.explode(creeper,creeper.getX(),creeper.getY(),creeper.getZ(),3F,Level.ExplosionInteraction.MOB);
                h.assertTrue(inside.getHealth()<insideHealth&&inside.getDeltaMovement().lengthSqr()>0,"Real damage and knockback reach one block inside radius"+radius);
                h.assertTrue(outside.getHealth()==outsideHealth&&outside.getDeltaMovement().lengthSqr()==0,"Real damage and knockback cannot reach one block outside radius"+radius);
                inside.discard();outside.discard();
                if(child){cleanup.run();h.succeed();}
                else infernalEntityRange(h,site,bounds,true,cleanup);
            }catch(Throwable failure){cleanup.run();throw failure;}
        },cleanup),cleanup);
        },cleanup);
    }

    @GameTest(template="gear_empty",batch="feedback_balance")
    public static void blackInfernalCraterRespectsMobGriefingAndCanceledExplosions(GameTestHelper h) {
        var terrain=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        boolean griefing=h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try(var site=new SpawnBalanceGameTests.Site(h)) {
            site.date(256,256,true);
            Creeper creeper=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
            var pos=BlockPos.containing(site.origin).below();var outer=pos.offset(41,0,0);
            for(var point:List.of(pos,outer)){terrain.put(point,site.level.getBlockState(point));site.level.setBlock(point,Blocks.STONE.defaultBlockState(),2);}
            site.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,site.level.getServer());
            site.level.explode(creeper,creeper.getX(),creeper.getY(),creeper.getZ(),3F,Level.ExplosionInteraction.MOB);
            h.assertTrue(site.level.getBlockState(pos).is(Blocks.STONE),"mobGriefing=false prevents the actual crater");
            site.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,site.level.getServer());
            Consumer<ExplosionEvent.Start> cancel=event->{if(event.getExplosion().getDirectSourceEntity()==creeper)event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ExplosionEvent.Start.class,cancel);
            try{site.level.explode(creeper,creeper.getX(),creeper.getY(),creeper.getZ(),3F,Level.ExplosionInteraction.MOB);}
            finally{NeoForge.EVENT_BUS.unregister(cancel);}
            h.assertTrue(site.level.getBlockState(pos).is(Blocks.STONE)&&site.level.getBlockState(outer).is(Blocks.STONE),"Canceling the explosion prevents both original and replacement terrain damage");
            Consumer<ExplosionEvent.Detonate> protect=event->{if(event.getExplosion().getDirectSourceEntity()==creeper)event.getAffectedBlocks().remove(pos);};
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ExplosionEvent.Detonate.class,protect);
            try{site.level.explode(creeper,creeper.getX(),creeper.getY(),creeper.getZ(),3F,Level.ExplosionInteraction.MOB);}
            finally{NeoForge.EVENT_BUS.unregister(protect);}
            h.assertTrue(site.level.getBlockState(pos).is(Blocks.STONE),"A normal Detonate protection listener can remove blocks after spherical selection");
        }finally{
            terrain.forEach((pos,state)->h.getLevel().setBlock(pos,state,2));
            h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing,h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="feedback_balance")
    public static void bossLightningAffectsOnlyItsTenByTenSquare(GameTestHelper h) {
        try(var site=new SpawnBalanceGameTests.Site(h)) {
            site.date(96,96,true);
            Mob boss=site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_BOSS));
            var memory=((ExtremeMob)boss).extremeMemory();
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.moveTo(site.origin.add(6,0,0));boss.setTarget(player);
            MobAbilities.initialize(boss,(ExtremeMob)boss);
            player.getPersistentData().putLong("modern_ite:boss_melee_grace",Long.MIN_VALUE);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);player.setHealth(100);
            memory.summons=19;memory.cooldown=100;MobAbilities.tick(boss,(ExtremeMob)boss);
            h.assertTrue(player.getHealth()==100,"A target six blocks away is outside the 10x10 lightning area");
            player.moveTo(site.origin.add(5,0,5));player.invulnerableTime=0;
            memory.summons=19;MobAbilities.tick(boss,(ExtremeMob)boss);
            h.assertTrue(player.getHealth()<100,"The square's 5,5 boundary remains in range even beyond a five-block circle");
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="feedback_balance")
    public static void copperClubUsesTheSharedSurvivalMaterialBudget(GameTestHelper h) {
        var club=new ItemStack(ExtremeContent.ITEM_ENTRIES.get("club_copper").get());
        var silver=new ItemStack(ExtremeContent.ITEM_ENTRIES.get("club_silver").get());
        double[] damage={0},reference={0};
        club.forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier)->{if(attribute.equals(Attributes.ATTACK_DAMAGE))damage[0]+=modifier.amount();});
        silver.forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier)->{if(attribute.equals(Attributes.ATTACK_DAMAGE))reference[0]+=modifier.amount();});
        h.assertTrue(Math.abs(1+damage[0]-5.25)<.001,"The source seven-point copper club uses one0.75 material budget; the superseded0.6 penalty is absent");
        h.assertTrue(Math.abs(1+reference[0]-5.6)<.001,"The source seven-point silver club uses one0.8 material budget");
        h.assertTrue(Math.abs((1+damage[0])-(1+reference[0])*.9375)<.001,
                "Copper5.25 and silver5.6 preserve the reviewed0.75/0.8 material ratio without the superseded40% penalty");
        h.succeed();
    }
}
