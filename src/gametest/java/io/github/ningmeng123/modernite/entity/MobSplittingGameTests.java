package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real death, native detonation, damage and saved miniature regressions. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class MobSplittingGameTests {
    private MobSplittingGameTests() {}

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void everySkeletonFamilySplitsIntoSixToEightSameTypeMiniatures(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            List<EntityType<?>> types=new ArrayList<>(List.of(EntityType.SKELETON,EntityType.WITHER_SKELETON,EntityType.STRAY,EntityType.BOGGED));
            for (var kind:List.of(MobKind.LONGDEAD,MobKind.LONGDEAD_GUARDIAN,MobKind.BONE_LORD,MobKind.ANCIENT_BONE_LORD)) types.add(MiteMobs.type(kind));
            for (var kind:ExtremeKind.values()) if (kind.family==ExtremeKind.Family.SKELETON) types.add(ExtremeMobs.type(kind));
            for (var type:types) {
                Mob parent=fixture.spawn(type);
                parent.getPersistentData().putBoolean("modern_ite:compressed",true);
                parent.setHealth(0);parent.die(parent.damageSources().generic());
                List<Mob> children=fixture.children(parent);
                assertChildren(h,parent,children);
                h.assertTrue(children.stream().allMatch(child->!child.getMainHandItem().isEmpty()),"Finalization gives skeleton miniatures their usable variant weapons: "+type);
                fixture.clear();
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void everyCreeperFamilySplitsIntoSixToEightSameTypeMiniatures(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            List<EntityType<?>> types=new ArrayList<>(List.of(EntityType.CREEPER,MiteMobs.type(MobKind.INFERNAL_CREEPER)));
            for (var kind:ExtremeKind.values()) if (kind.family==ExtremeKind.Family.CREEPER) types.add(ExtremeMobs.type(kind));
            for (var type:types) {
                Mob parent=fixture.spawn(type);roll(parent,true);
                parent.setHealth(0);parent.die(parent.damageSources().generic());
                List<Mob> children=fixture.children(parent);
                assertChildren(h,parent,children);
                h.assertTrue(children.stream().noneMatch(Entity::isPassenger),"Miniature creepers do not spawn extra bat mounts: "+type);
                fixture.clear();
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void failedProbabilityAndCanceledDeathNeverCreateChildren(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            Mob creeper=fixture.spawn(EntityType.CREEPER);roll(creeper,false);
            creeper.setHealth(0);creeper.die(creeper.damageSources().generic());
            h.assertTrue(fixture.children(creeper).isEmpty(),"A failed real creeper death roll produces no children");
            Mob skeleton=fixture.spawn(EntityType.SKELETON);
            skeleton.getPersistentData().putBoolean("modern_ite:compressed",false);
            skeleton.setHealth(0);skeleton.die(skeleton.damageSources().generic());
            h.assertTrue(fixture.children(skeleton).isEmpty(),"An ordinary skeleton without the rare trait produces no children");
            Mob canceled=fixture.spawn(EntityType.SKELETON);
            canceled.getPersistentData().putBoolean("modern_ite:compressed",true);
            Consumer<LivingDeathEvent> veto=event->{if (event.getEntity()==canceled) event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingDeathEvent.class,veto);
            try {
                canceled.die(canceled.damageSources().generic());
                h.assertTrue(fixture.children(canceled).isEmpty()&&!canceled.getPersistentData().getBoolean(MobSplitting.HANDLED),"A canceled native death neither splits nor consumes its future split");
            } finally {NeoForge.EVENT_BUS.unregister(veto);}
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void miniaturesKeepQuarterHealthHalfSizeAndInjuriesAcrossGrowthAndReload(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            for (var type:List.of(EntityType.SKELETON,EntityType.CREEPER,MiteMobs.type(MobKind.LONGDEAD),ExtremeMobs.type(ExtremeKind.RUN_CREEPER))) {
                Mob adult=fixture.spawn(type),child=fixture.spawn(type);
                MonsterDayScaling.refresh(adult);MonsterDayScaling.refresh(child);
                MobSplitting.markChild(child);
                float expected=adult.getMaxHealth()*.25F;
                h.assertTrue(close(child.getMaxHealth(),expected),"Miniature health is one quarter of the grown same-species adult: "+type+", actual="+child.getMaxHealth()+", expected="+expected);
                h.assertTrue(close(child.getBbHeight(),adult.getBbHeight()*.5F)&&close(child.getBbWidth(),adult.getBbWidth()*.5F),"Native scale changes both dimensions by half: "+type);
                child.setHealth(child.getMaxHealth()*.4F);
                for (int refresh=0;refresh<3;refresh++) {MonsterDayScaling.refresh(child);MobSplitting.markChild(child);}
                h.assertTrue(close(child.getMaxHealth(),expected)&&close(child.getHealth(),expected*.4F),"Repeated marking and calendar refresh neither compound the reduction nor heal injuries: "+type);
                CompoundTag saved=new CompoundTag();child.saveWithoutId(saved);
                Mob loaded=(Mob)type.create(h.getLevel());loaded.load(saved);
                MonsterDayScaling.refresh(loaded);MobSplitting.markChild(loaded);
                h.assertTrue(MobSplitting.isChild(loaded)&&close(loaded.getMaxHealth(),expected)&&close(loaded.getHealth(),expected*.4F),"Native save/load retains miniature identity, quarter maximum health and injuries: "+type);
                h.assertTrue(close(loaded.getAttributeValue(Attributes.SCALE),.5F),"Native scale attribute remains half after reload: "+type);
                loaded.discard();fixture.clear();
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void miniatureDeathsAndRepeatedDeathCallbacksNeverSplitAgain(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            Mob parent=fixture.spawn(EntityType.SKELETON);parent.getPersistentData().putBoolean("modern_ite:compressed",true);
            parent.setHealth(0);parent.die(parent.damageSources().generic());
            List<Mob> children=fixture.children(parent);assertChildren(h,parent,children);
            MobSplitting.trySplit(parent);
            h.assertTrue(fixture.children(parent).size()==children.size(),"A second callback for one parent cannot spawn a second wave");
            for (Mob child:children) {
                child.getPersistentData().putBoolean("modern_ite:compressed",true);
                child.setHealth(0);child.die(child.damageSources().generic());
                h.assertTrue(fixture.children(child).isEmpty(),"Even a miniature carrying a forced legacy compressed trait cannot recursively split");
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void actualMeleeAndArrowDamageAreHalvedForMiniatureSkeletons(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            Mob adult=fixture.spawn(EntityType.SKELETON),child=fixture.spawn(EntityType.SKELETON);MobSplitting.markChild(child);
            float adultMelee=damage(h,fixture,adult,false),childMelee=damage(h,fixture,child,false);
            float adultArrow=damage(h,fixture,adult,true),childArrow=damage(h,fixture,child,true);
            h.assertTrue(adultMelee>0&&close(childMelee,adultMelee*.5F),"The real damage pipeline halves miniature melee damage after calendar growth: adult="+adultMelee+", miniature="+childMelee);
            h.assertTrue(adultArrow>0&&close(childArrow,adultArrow*.5F),"The real damage pipeline halves miniature projectile damage after calendar growth: adult="+adultArrow+", miniature="+childArrow);
            Mob lightning=fixture.spawn(ExtremeMobs.type(ExtremeKind.SKELETON_LIGHTNING));
            float adultLightning=lightningDamage(h,fixture,lightning);
            MobSplitting.markChild(lightning);float childLightning=lightningDamage(h,fixture,lightning);
            h.assertTrue(close(adultLightning,6)&&close(childLightning,3),"The fixed lightning-arrow rule also respects miniature damage: adult="+adultLightning+", miniature="+childLightning);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void nativeExplosionsShrinkForOrdinaryInfernalAndBlackInfernalMiniatures(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            for (var type:List.of(EntityType.CREEPER,MiteMobs.type(MobKind.INFERNAL_CREEPER),ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER))) {
                Creeper adult=(Creeper)fixture.spawn(type),child=(Creeper)fixture.spawn(type);
                MobSplitting.markChild(child);roll(adult,false);
                float full=explosionRadius(h,adult),small=explosionRadius(h,child);
                h.assertTrue(full>0&&small>0&&close(small,full*.5F),"The native explosion uses half radius for miniature "+type+": adult="+full+", miniature="+small);
                fixture.clear();
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void realSelfExplosionSplitsAfterBlastAndMiniatureSelfExplosionDoesNotRecurse(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            Creeper parent=(Creeper)fixture.spawn(EntityType.CREEPER);roll(parent,true);
            // An airborne source cannot damage the template; the real blast still runs in full.
            detonate(parent);
            h.assertTrue(parent.isRemoved(),"Native detonation really removes its parent");
            List<Mob> children=fixture.children(parent);assertChildren(h,parent,children);
            h.assertTrue(children.stream().allMatch(Entity::isAlive),"New miniatures are spawned after the parent blast finishes and survive it");
            Creeper child=(Creeper)children.getFirst();roll(child,true);detonate(child);
            h.assertTrue(child.isRemoved()&&fixture.children(child).isEmpty(),"A miniature's real self explosion cannot recursively split");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void miniatureMirrorUsesTheAdultHealthThresholdBeforeCopyingPlayers(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            Mob adult=fixture.spawn(ExtremeMobs.type(ExtremeKind.MIRROR_SKELETON));
            Mob child=fixture.spawn(ExtremeMobs.type(ExtremeKind.MIRROR_SKELETON));
            MonsterDayScaling.refresh(adult);MonsterDayScaling.refresh(child);MobSplitting.markChild(child);
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.moveTo(fixture.origin.add(4,0,0));
            try {
                float playerMaximum=(adult.getMaxHealth()+child.getMaxHealth())*.5F;
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(playerMaximum);player.setHealth(playerMaximum);
                h.assertTrue(player.getMaxHealth()>child.getMaxHealth()&&player.getMaxHealth()<adult.getMaxHealth(),"The player maximum lies strictly between miniature and adult health, exercising the mirror threshold");
                adult.setTarget(player);child.setTarget(player);
                for (int tick=0;tick<20;tick++) {
                    MobAbilities.tick(adult,(ExtremeMob)adult);MobAbilities.tick(child,(ExtremeMob)child);
                }
                h.assertTrue(((ExtremeMob)adult).extremeMemory().mirrored&&((ExtremeMob)child).extremeMemory().mirrored,"Both real ability schedulers process their living player target");
                h.assertTrue(close(child.getAttributeBaseValue(Attributes.MAX_HEALTH),adult.getAttributeBaseValue(Attributes.MAX_HEALTH)),"Equivalent adult and miniature mirrors choose the same species health baseline");
                MonsterDayScaling.refresh(adult);MonsterDayScaling.refresh(child);
                h.assertTrue(close(child.getMaxHealth(),adult.getMaxHealth()*.25F),"Mirroring followed by native calendar growth preserves exactly one-quarter health");
            } finally {player.discard();}
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void disabledSkeletonSplittingAndZeroCountHonorExistingSettings(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            fixture.set("Entities.compressed_skeleton",false);
            Mob disabled=fixture.spawn(EntityType.SKELETON);disabled.getPersistentData().putBoolean("modern_ite:compressed",true);
            disabled.setHealth(0);disabled.die(disabled.damageSources().generic());
            h.assertTrue(fixture.children(disabled).isEmpty(),"Disabling the legacy skeleton split setting suppresses even an existing marked skeleton");
            fixture.set("Entities.compressed_skeleton",true);fixture.set("Entities.compressed_skeleton_expand_count",0);
            Mob zero=fixture.spawn(EntityType.SKELETON);zero.getPersistentData().putBoolean("modern_ite:compressed",true);
            zero.setHealth(0);zero.die(zero.damageSources().generic());
            h.assertTrue(fixture.children(zero).isEmpty(),"A configured zero count suppresses the real death split");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="mob_splitting")
    public static void configuredLargeSplitsStillRespectEightLivingSupportsPerParent(GameTestHelper h) {
        try (Fixture fixture=new Fixture(h)) {
            fixture.set("Entities.compressed_skeleton_expand_count",12);
            Mob parent=fixture.spawn(EntityType.SKELETON);parent.getPersistentData().putBoolean("modern_ite:compressed",true);
            parent.setHealth(0);parent.die(parent.damageSources().generic());
            List<Mob> children=fixture.children(parent);
            h.assertTrue(children.size()==8&&SpawnPopulation.supportCount(parent)==8,"The real spawn-admission ledger limits an eleven-to-thirteen-child request to eight living supports: actual="+children.size());
            h.assertTrue(children.stream().allMatch(MobSplitting::isChild),"Every admitted support is already marked as a miniature");
        }
        h.succeed();
    }

    private static void assertChildren(GameTestHelper h,Mob parent,List<Mob> children) {
        h.assertTrue(children.size()>=6&&children.size()<=8,"Successful death splitting produces six to eight children for "+parent.getType()+", actual="+children.size());
        for (Mob child:children) {
            h.assertTrue(child.getType()==parent.getType()&&MobSplitting.isChild(child),"Every child retains its exact variant and miniature marker");
            h.assertTrue(close(child.getAttributeValue(Attributes.SCALE),.5F),"The child's native synchronized scale is half size");
        }
    }

    private static float damage(GameTestHelper h,Fixture fixture,Mob attacker,boolean projectile) {
        Mob victim=fixture.spawn(EntityType.COW);victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(victim.getMaxHealth());
        Arrow arrow=EntityType.ARROW.create(h.getLevel());arrow.setOwner(attacker);
        DamageSource source=projectile?victim.damageSources().arrow(arrow,attacker):victim.damageSources().mobAttack(attacker);
        AtomicReference<Float> result=new AtomicReference<>();
        Consumer<LivingDamageEvent.Pre> listener=event->{if(event.getEntity()==victim) result.set(event.getNewDamage());};
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LivingDamageEvent.Pre.class,listener);
        try {victim.hurt(source,20);h.assertTrue(result.get()!=null,"The real damage reaches its final pre-damage callback");return result.get();}
        finally {NeoForge.EVENT_BUS.unregister(listener);arrow.discard();victim.discard();}
    }

    private static float lightningDamage(GameTestHelper h,Fixture fixture,Mob attacker) {
        var victim=h.makeMockPlayer(GameType.SURVIVAL);victim.moveTo(fixture.origin.add(4,0,0));
        Arrow arrow=EntityType.ARROW.create(h.getLevel());arrow.setOwner(attacker);
        AtomicReference<Float> result=new AtomicReference<>();
        Consumer<LivingDamageEvent.Pre> listener=event->{if(event.getEntity()==victim) result.set(event.getNewDamage());};
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LivingDamageEvent.Pre.class,listener);
        try {victim.hurt(victim.damageSources().arrow(arrow,attacker),20);h.assertTrue(result.get()!=null,"A real lightning-skeleton arrow reaches final player damage");return result.get();}
        finally {NeoForge.EVENT_BUS.unregister(listener);arrow.discard();victim.discard();}
    }

    private static float explosionRadius(GameTestHelper h,Creeper creeper) {
        AtomicReference<Explosion> observed=new AtomicReference<>();
        Consumer<ExplosionEvent.Start> listener=event->{if(event.getExplosion().getDirectSourceEntity()==creeper&&!event.isCanceled()) {observed.set(event.getExplosion());event.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,ExplosionEvent.Start.class,listener);
        try {detonate(creeper);h.assertTrue(observed.get()!=null,"Native creeper detonation reaches the final explosion event");return observed.get().radius();}
        finally {NeoForge.EVENT_BUS.unregister(listener);}
    }

    private static void detonate(Creeper creeper) {
        try {Method method=Creeper.class.getDeclaredMethod("explodeCreeper");method.setAccessible(true);method.invoke(creeper);}
        catch (ReflectiveOperationException failure) {throw new IllegalStateException("Cannot invoke native creeper detonation",failure);}
    }

    private static void roll(Mob mob,boolean success) {
        for(long seed=0;seed<10000;seed++) {
            mob.getRandom().setSeed(seed);
            if((mob.getRandom().nextInt(100)<3)==success) {mob.getRandom().setSeed(seed);return;}
        }
        throw new IllegalStateException("No deterministic split seed");
    }
    private static boolean close(double actual,double expected) {return Math.abs(actual-expected)<.002*Math.max(1,Math.abs(expected));}

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Vec3 origin;
        private final List<Mob> owned=new ArrayList<>();
        private final Map<String,Object> settings=new LinkedHashMap<>();
        private final long time,highest;
        private final boolean dirty,grace;
        private Fixture(GameTestHelper helper) {
            this.helper=helper;origin=helper.absolutePos(new BlockPos(1,220,1)).getCenter();
            var level=helper.getLevel().getServer().overworld();var history=BloodMoonInvasionData.get(level);
            time=level.getDayTime();highest=history.highestDay();dirty=history.isDirty();grace=ExtremeWorldState.graceEnded(level);
            level.setDayTime(255*24000L);history.restoreProgress(256);ExtremeWorldState.restoreGraceEnded(level,true);
            set("Entities.compressed_skeleton",true);set("Entities.compressed_skeleton_expand_count",7);
            SpawnPopulation.clearReservations(helper.getLevel());
        }
        private void set(String key,Object value) {settings.putIfAbsent(key,IteConfig.setting(key).get());IteConfig.setting(key).set(value);IteConfig.refresh();}
        private Mob spawn(EntityType<?> type) {
            Entity created=type.create(helper.getLevel());helper.assertTrue(created instanceof Mob,"Entity factory constructs a mob: "+type);
            Mob mob=(Mob)created;mob.moveTo(origin);mob.setNoAi(true);mob.setNoGravity(true);owned.add(mob);
            helper.assertTrue(helper.getLevel().addFreshEntity(mob),"Fixture mob passes the real join handlers: "+type);
            return mob;
        }
        private List<Mob> children(Mob parent) {
            List<Mob> children=helper.getLevel().getEntitiesOfClass(Mob.class,parent.getBoundingBox().inflate(16),mob->mob.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)&&mob.getPersistentData().getUUID(SpawnPopulation.SUMMONER).equals(parent.getUUID()));
            for(Mob child:children) if(!owned.contains(child)) owned.add(child);
            return children;
        }
        private void clear() {owned.forEach(Mob::discard);owned.clear();SpawnPopulation.clearReservations(helper.getLevel());}
        @Override public void close() {
            clear();settings.forEach((key,value)->IteConfig.setting(key).set(value));IteConfig.refresh();
            var level=helper.getLevel().getServer().overworld();level.setDayTime(time);var history=BloodMoonInvasionData.get(level);history.restoreProgress(highest);history.setDirty(dirty);ExtremeWorldState.restoreGraceEnded(level,grace);
        }
    }
}
