package io.github.ningmeng123.modernite;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.survival.PlayerHealthEvents;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.gear.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

/** Verify the live base extension entry points and real entity transactions. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class CalendarGameTests {
    @GameTest(template="gear_empty")
    public static void sourceSpawnAttemptsUseTheCurrentCalendar(GameTestHelper h) {
        var level=h.getLevel().getServer().overworld();long old=level.getDayTime();
        var policy=MiteExtensions.policy();var context=new MiteExtensions.SpawnContext(level,io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER,false);
        try {
            level.setDayTime(31*24000L);
            h.assertTrue(policy.monsterCap(level,false,289,0)==636&&policy.monsterCap(level,true,289,0)==1272,"Blood moon budgets use 50 per 128 chunks, day floor 100, and separate deep multiplier");
            h.assertTrue(policy.spawnGroups(context,3)==6&&policy.spawnAttempts(context,null,4)==8&&policy.spawnJitter(context,6)==8,"Blood moon six-by-eight attempts and jitter");
            var phase=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("mite:phase_spider"));
            h.assertTrue(policy.spawnAttempts(context,phase,4)==128,"Phase spider attempts remain positive instead of overflowing a byte");
            h.assertTrue(policy.distantHostileSkipBound(context,2)==4,"Blood moon distant candidate rejection is one in four");
            level.setDayTime(127*24000L);
            h.assertTrue(policy.monsterCap(level,false,289,0)==240&&policy.spawnGroups(context,3)==3&&policy.distantHostileSkipBound(context,2)==2,"Blue moon day 128 does not inherit blood spawning");
            level.setDayTime(143*24000L);
            h.assertTrue(policy.monsterCap(level,false,289,0)==768&&policy.spawnAttempts(context,null,4)==8,"Day 144 uses the accelerated calendar");
            var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            var netherContext=new MiteExtensions.SpawnContext(nether,io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER,false);
            int groups=policy.spawnGroups(netherContext,3);
            h.assertTrue((groups==3||groups==4)&&policy.spawnAttempts(netherContext,null,4)==5,"Nether uses its dedicated ordinary rounds and extra attempt");
        } finally {level.setDayTime(old);}
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void calendarAndEquipmentShareTheSameProgress(GameTestHelper h) {
        long[] days={128,144,208,224,256}; long[] counts={3,4,8,9,11};
        for(int i=0;i<days.length;i++) {
            var state=LunarCalendar.state((days[i]-1)*24000L+18000,true);
            h.assertTrue(state.blueNight()==(days[i]==128),"Only day 128 is blue");
            h.assertTrue(state.bloodNight()==(days[i]!=128),"All later selected dates are blood moons");
            h.assertTrue(BloodMoonEquipmentRules.bloodMoons(days[i])==counts[i],"Equipment consumes the approved accelerated blood count");
            h.assertTrue(!LunarCalendar.state((days[i]-1)*24000L,false).bloodPeriod(),"Other dimensions have no local blood moon");
        }
        h.assertTrue(BloodMoonEquipmentRules.stage(BloodMoonEquipmentRules.bloodMoons(208))==8,"Day 208 unlocks adamantium");
        h.assertTrue(BloodMoonEquipmentRules.stage(BloodMoonEquipmentRules.bloodMoons(224))==9,"Day 224 unlocks vibranium");
        h.assertTrue(LunarCalendar.state(119*24000L,true).moonDogPeriod(),"Day 120 moon dog");
        h.assertTrue(!LunarCalendar.state(247*24000L,true).moonDogPeriod(),"Moon dog does not repeat on day 248");
        h.assertTrue(LunarCalendar.state(140*24000L,true).harvestPeriod(),"Day 141 is three days before blood moon");
        var data=new BloodMoonInvasionData();data.observeDay(224);data.observeDay(32);
        var loaded=BloodMoonInvasionData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.highestDay()==224&&loaded.unlocked(),"Highest date survives rewind and disk round trip");
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void healthCombinesLevelAndArmorGemsWithoutHealing(GameTestHelper h) {
        var level=h.getLevel().getServer().overworld();var history=BloodMoonInvasionData.get(level);
        long oldDay=level.getDayTime(),oldHighest=history.highestDay();
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"ite-health"));
        try {
            player.experienceLevel=135;
            int code=GemType.HEALTH.ordinal()*6+6;
            int i=0;for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
                var stack=new ItemStack(List.of(Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS).get(i++));
                stack.set(ExtremeGear.SOCKETS.get(),GemSockets.EMPTY.with(0,code).with(1,code-5));player.setItemSlot(slot,stack);
            }
            for(long day: new long[]{95,96,127,128,169,170}) {
                level.setDayTime((day-1)*24000L);history.restoreProgress(day);
                double expected=day<96?20:day<128?30:day<170?40:84;
                h.assertTrue(MiteExtensions.maximumHealth(player)==expected,"Date cap includes all four armor gems on day "+day);
            }
            player.setHealth(5);PlayerHealthEvents.onPlayerTick(new PlayerTickEvent.Post(player));
            h.assertTrue(player.getMaxHealth()==84&&player.getHealth()==5,"Unlocking max health never heals");
            player.setHealth(84);player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            PlayerHealthEvents.onPlayerTick(new PlayerTickEvent.Post(player));
            h.assertTrue(player.getMaxHealth()==78&&player.getHealth()==78,"Removing a six-rank gem clips excess health");
            player.experienceLevel=1000;
            h.assertTrue(MiteExtensions.maximumHealth(player)==78,"Unlimited player levels do not grow base health beyond 60");
            h.assertTrue(MiteExtensions.foodCapacity(player)==20,"Food and nutrition remain at most 20");
        } finally {
            for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))player.setItemSlot(slot,ItemStack.EMPTY);
            level.setDayTime(oldDay);history.restoreProgress(oldHighest);
        }
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void passengersAndCanceledJoinsAreOneBudgetTransaction(GameTestHelper h) throws Exception {
        Class<?> budgetType=Class.forName("io.github.ningmeng123.mite.worldgen.R196MobSpawning$SpawnBudget");
        var constructor=budgetType.getDeclaredConstructor(int.class,int.class);constructor.setAccessible(true);
        var commit=R196MobSpawning.class.getDeclaredMethod("finalizeNaturalMonster",ServerLevel.class,Mob.class,SpawnGroupData.class,int.class,budgetType);commit.setAccessible(true);
        Field deep=budgetType.getDeclaredField("deep"),shallow=budgetType.getDeclaredField("shallow");deep.setAccessible(true);shallow.setAccessible(true);
        var root=EntityType.CREEPER.create(h.getLevel());var rider=EntityType.CREEPER.create(h.getLevel());
        root.setPos(h.absolutePos(new net.minecraft.core.BlockPos(1,1,1)).getCenter());rider.startRiding(root,true);
        rider.setPos(root.getX(),70,root.getZ());Object budget=constructor.newInstance(1,0);
        var rejected=(R196MobSpawning.NaturalMonsterSpawn)commit.invoke(null,h.getLevel(),root,null,Integer.MAX_VALUE,budget);
        h.assertTrue(rejected.monsters()==0&&root.isRemoved()&&rider.isRemoved(),"A rider in a full height interval rejects the complete spawn");
        h.assertTrue(deep.getInt(budget)==1&&shallow.getInt(budget)==0,"Rejected tree consumes no budget");
        var canceled=EntityType.CREEPER.create(h.getLevel());canceled.setPos(h.absolutePos(new net.minecraft.core.BlockPos(2,1,2)).getCenter());
        Consumer<EntityJoinLevelEvent> listener=event->{if(event.getEntity()==canceled)event.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            Object available=constructor.newInstance(1,1);
            var result=(R196MobSpawning.NaturalMonsterSpawn)commit.invoke(null,h.getLevel(),canceled,null,Integer.MAX_VALUE,available);
            h.assertTrue(result.monsters()==0&&canceled.isRemoved()&&deep.getInt(available)==1,"Canceled join discards the tree and leaves the ledger unchanged");
        } finally {NeoForge.EVENT_BUS.unregister(listener);}
        h.succeed();
    }
}
