package io.github.ningmeng123.modernite;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.survival.PlayerHealthEvents;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.gear.*;
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
import io.github.ningmeng123.modernite.world.ExtremeWorldState;

/** Verify the live base extension entry points and real entity transactions. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class CalendarGameTests {
    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void sourceSpawnAttemptsUseTheCurrentCalendar(GameTestHelper h) {
        var level=h.getLevel().getServer().overworld();long old=level.getDayTime();
        var history=BloodMoonInvasionData.get(level);long highest=history.highestDay();boolean dirty=history.isDirty();
        boolean grace=ExtremeWorldState.graceEnded(level);
        var policy=MiteExtensions.policy();var context=new MiteExtensions.SpawnContext(level,io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER,false);
        try {
            level.setDayTime(31*24000L);
            ExtremeWorldState.restoreGraceEnded(level,true);
            h.assertTrue(policy.independentHostileBudgets(level,false),"Deep and shallow monsters retain independent source budgets");
            h.assertTrue(policy.monsterCap(level,false,289,0)==144&&policy.monsterCap(level,true,289,0)==288,"The first blood date doubles the chunk contribution and preserves the ordinary day addition before deep scaling");
            h.assertTrue(policy.spawnGroups(context,3)==3&&policy.spawnAttempts(context,null,4)==4&&policy.spawnJitter(context,6)==6,"Blood dates default to the source three groups of four attempts and original scatter");
            var phase=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("mite:phase_spider"));
            h.assertTrue(policy.spawnAttempts(context,phase,64)==64,"Phase spiders retain the source sixty-four-attempt special case");
            h.assertTrue(policy.distantHostileSkipBound(context,2)==2,"Distant candidate rejection retains the source one-in-two draw on a blood date");
            level.setDayTime(127*24000L);
            h.assertTrue(policy.monsterCap(level,false,289,0)==184&&policy.monsterCap(level,true,289,0)==368&&policy.spawnGroups(context,3)==3&&policy.distantHostileSkipBound(context,2)==2,"Blue moon day 128 uses the ordinary independent source budgets");
            level.setDayTime(143*24000L);
            h.assertTrue(policy.monsterCap(level,false,289,0)==256&&policy.monsterCap(level,true,289,0)==512&&policy.spawnAttempts(context,null,4)==4,"Day 144 retains the accelerated blood calendar with the source default cap formula");
            var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            var netherContext=new MiteExtensions.SpawnContext(nether,io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER,false);
            int groups=policy.spawnGroups(netherContext,3);
            h.assertTrue(groups==3&&policy.spawnAttempts(netherContext,null,4)==4,"Nether uses three ordinary groups of four attempts");
            level.setDayTime(32*24000L);
            h.assertTrue(policy.spawnGroups(context,3)==3&&policy.localDensity(level,null,8)==8&&ExtremeRules.localHostileLimit(level)==32,"Ordinary natural crowd protection and the preserved support limit keep separate defaults");
            level.setDayTime(31*24000L+18000L);
            h.assertTrue(policy.localDensity(level,null,12)==12&&ExtremeRules.localHostileLimit(level)==48,"Blood-night natural crowd protection is twelve while the preserved support limit remains forty-eight");
        } finally {level.setDayTime(old);history.restoreProgress(highest);history.setDirty(dirty);ExtremeWorldState.restoreGraceEnded(level,grace);}
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
    @GameTest(template="gear_empty",batch="spawn_balance")
    public static void passengersAndCanceledJoinsAreOneBudgetTransaction(GameTestHelper h) {
        var root=EntityType.CREEPER.create(h.getLevel());var rider=EntityType.CREEPER.create(h.getLevel());
        root.getPersistentData().putBoolean(io.github.ningmeng123.modernite.entity.SpawnPopulation.EXPLICIT_COMMAND,true);
        rider.getPersistentData().putBoolean(io.github.ningmeng123.modernite.entity.SpawnPopulation.EXPLICIT_COMMAND,true);
        root.setPos(h.absolutePos(new net.minecraft.core.BlockPos(1,1,1)).getCenter());rider.startRiding(root,true);
        rider.setPos(root.getX(),70,root.getZ());
        var rejected=R196MobSpawning.finalizeNaturalMonster(h.getLevel(),root,null,1);
        h.assertTrue(rejected.monsters()==0&&root.isRemoved()&&rider.isRemoved(),"A two-monster tree cannot spend a single shared slot across height intervals");
        var canceled=EntityType.CREEPER.create(h.getLevel());canceled.setPos(h.absolutePos(new net.minecraft.core.BlockPos(2,1,2)).getCenter());
        canceled.getPersistentData().putBoolean(io.github.ningmeng123.modernite.entity.SpawnPopulation.EXPLICIT_COMMAND,true);
        Consumer<EntityJoinLevelEvent> listener=event->{if(event.getEntity()==canceled)event.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var result=R196MobSpawning.finalizeNaturalMonster(h.getLevel(),canceled,null,2);
            h.assertTrue(result.monsters()==0&&canceled.isRemoved(),"Canceled join discards the tree without reporting a committed monster");
        } finally {NeoForge.EVENT_BUS.unregister(listener);}
        h.succeed();
    }
}
