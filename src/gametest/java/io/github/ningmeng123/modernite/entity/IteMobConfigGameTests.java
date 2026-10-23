package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Nondefault configuration reaches real entity joins, saved traits and spawn pools. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IteMobConfigGameTests {
    private IteMobConfigGameTests() {}

    private static final class Settings implements AutoCloseable {
        private final Map<String,Object> before=new LinkedHashMap<>();
        void set(String key,Object value){
            var setting=IteConfig.setting(key);before.putIfAbsent(key,setting.get());setting.set(value);IteConfig.refresh();
        }
        @Override public void close(){before.forEach((key,value)->IteConfig.setting(key).set(value));IteConfig.refresh();}
    }

    private static void place(GameTestHelper h,Mob mob){
        h.assertTrue(mob!=null,"The real registered entity creates");
        mob.setNoAi(true);mob.setNoGravity(true);mob.moveTo(h.absolutePos(new BlockPos(1,160,1)).getCenter());
    }

    @GameTest(template="gear_empty")
    public static void togglingTripleShotStopsAnExistingSavedTrait(GameTestHelper h){
        Skeleton skeleton=EntityType.SKELETON.create(h.getLevel());place(h,skeleton);
        skeleton.getPersistentData().putBoolean("modern_ite:legacy_rolls",true);
        skeleton.getPersistentData().putBoolean("modern_ite:triple",true);
        skeleton.getPersistentData().putBoolean("modern_ite:melee_roll",false);
        h.assertTrue(h.getLevel().addFreshEntity(skeleton),"The saved-trait skeleton joins normally");
        try(Settings settings=new Settings()){
            settings.set("skeleton_triple_shot",false);
            var arrow=EntityType.ARROW.create(h.getLevel());arrow.setOwner(skeleton);arrow.setPos(skeleton.position());
            h.assertTrue(h.getLevel().addFreshEntity(arrow),"The disabled control arrow joins");
            h.assertTrue(h.getLevel().getEntitiesOfClass(AbstractArrow.class,skeleton.getBoundingBox().inflate(4),a->a.getOwner()==skeleton).size()==1,
                    "Disabling triple shot stops an already saved triple-shot trait");
            settings.set("skeleton_triple_shot",true);
            var second=EntityType.ARROW.create(h.getLevel());second.setOwner(skeleton);second.setPos(skeleton.position());
            h.assertTrue(h.getLevel().addFreshEntity(second),"The reenabled control arrow joins");
            h.assertTrue(h.getLevel().getEntitiesOfClass(AbstractArrow.class,skeleton.getBoundingBox().inflate(4),a->a.getOwner()==skeleton).size()==4,
                    "Reenabling the setting adds exactly two real companion arrows");
        }finally{
            h.getLevel().getEntitiesOfClass(AbstractArrow.class,skeleton.getBoundingBox().inflate(4),a->a.getOwner()==skeleton).forEach(AbstractArrow::discard);
            skeleton.discard();
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void customSlimeDamageAndAnnihilationTimerReachEntityBehavior(GameTestHelper h){
        var slime=EntityType.SLIME.create(h.getLevel());place(h,slime);slime.setSize(3,true);
        var annihilation=ExtremeMobs.type(ExtremeKind.ANNIHILATION_SKELETON).create(h.getLevel());place(h,annihilation);
        try(Settings settings=new Settings()){
            settings.set("slime_attack_damage_multiplier",2D);settings.set("slime_attack_damage_bonus",7D);
            double original=slime.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            h.assertTrue(h.getLevel().addFreshEntity(slime),"The custom-config slime joins through the real listener");
            h.assertTrue(slime.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)==original*2+7,
                    "The initial attribute applies the configured multiplier and bonus once");
            settings.set("annihilation_skeleton_despawn_time",3);
            h.assertTrue(h.getLevel().addFreshEntity(annihilation),"The annihilation skeleton joins normally");
            var state=(ExtremeMob)annihilation;state.extremeMemory().stage=1;state.extremeMemory().summons=0;
            MobAbilities.tick(annihilation,state);MobAbilities.tick(annihilation,state);
            h.assertFalse(annihilation.isRemoved(),"The attacked skeleton remains before the configured duration");
            MobAbilities.tick(annihilation,state);
            h.assertTrue(annihilation.isRemoved(),"The third tick triggers the configured disappearance");
        }finally{slime.discard();annihilation.discard();}
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void savedGolemBoostAndForcedMeleeRespondToSettingChanges(GameTestHelper h){
        var golem=EntityType.IRON_GOLEM.create(h.getLevel());place(h,golem);
        golem.getPersistentData().putBoolean("modern_ite:source_profile",true);
        golem.getPersistentData().putBoolean("modern_ite:golem_boost",true);
        golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(220);golem.setHealth(220);
        Skeleton skeleton=EntityType.SKELETON.create(h.getLevel());place(h,skeleton);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        skeleton.getPersistentData().putBoolean("modern_ite:melee_roll",true);
        try(Settings settings=new Settings()){
            settings.set("can_boost_iron_golem",false);settings.set("skeleton_force_melee_attack",true);
            h.assertTrue(h.getLevel().addFreshEntity(golem)&&h.getLevel().addFreshEntity(skeleton),"Both existing traits join");
            h.assertTrue(golem.getAttributeBaseValue(Attributes.MAX_HEALTH)==200,"A disabled saved golem boost loses its extra twenty health");
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof SourceMeleeGoal),
                    "The saved forced-melee trait installs its actual melee goal");
            settings.set("can_boost_iron_golem",true);settings.set("skeleton_force_melee_attack",false);
            golem.tickCount=skeleton.tickCount=10;
            ExtremeNativeMobs.tick(new EntityTickEvent.Post(golem));ExtremeNativeMobs.tick(new EntityTickEvent.Post(skeleton));
            h.assertTrue(golem.getAttributeBaseValue(Attributes.MAX_HEALTH)==220,"Reenabling the golem boost restores its twenty health once");
            h.assertFalse(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof SourceMeleeGoal),
                    "Disabling forced melee restores bow behavior on the existing skeleton");
            ExtremeNativeMobs.tick(new EntityTickEvent.Post(golem));
            h.assertTrue(golem.getAttributeBaseValue(Attributes.MAX_HEALTH)==220,"Repeated updates never stack golem health");
        }finally{golem.discard();skeleton.discard();}
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void nondefaultWitchDaysAndWeightsChangeTheActualSpawnPool(GameTestHelper h){
        var context=new MiteExtensions.SpawnContext(h.getLevel(),R196SpawnRules.Category.MONSTER,false);
        var pos=h.absolutePos(new BlockPos(1,2,1));
        try(var settings=new IteReferenceSpawnGameTests.Preset(false)){
            settings.set("wandering_witch_spawn_limit_day_overworld",1);settings.set("wandering_witch_spawn_frequency",17);
            var entries=new ArrayList<R196MobSpawning.Entry>();ExtremeMobs.addSpawnEntries(context,pos,entries);
            h.assertTrue(entries.stream().anyMatch(e->e.type()==ExtremeMobs.type(ExtremeKind.WANDERING_WITCH)&&e.weight()==17),
                    "Lowering the configured day exposes a real weight-seventeen wandering-witch entry");
            settings.set("wandering_witch_spawn_frequency",0);
            entries.clear();ExtremeMobs.addSpawnEntries(context,pos,entries);
            h.assertFalse(entries.stream().anyMatch(e->e.type()==ExtremeMobs.type(ExtremeKind.WANDERING_WITCH)),
                    "A zero natural-pool weight removes the actual wandering-witch entry");
            settings.set("wandering_witch_spawn_frequency",17);settings.set("wandering_witch_spawn_limit_day_overworld",1000000);
            entries.clear();ExtremeMobs.addSpawnEntries(context,pos,entries);
            h.assertFalse(entries.stream().anyMatch(e->e.type()==ExtremeMobs.type(ExtremeKind.WANDERING_WITCH)),
                    "A future configured day blocks the same natural-pool entry");
        }
        h.succeed();
    }
}
