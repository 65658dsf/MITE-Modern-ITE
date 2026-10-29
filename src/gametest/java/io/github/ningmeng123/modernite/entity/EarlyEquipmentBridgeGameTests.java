package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual addon policy, spawn lifecycle, old locks and installed arrow AI. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class EarlyEquipmentBridgeGameTests {
    private static final Set<ExtremeKind> ARCHERS=Set.of(ExtremeKind.MIRROR_SKELETON,ExtremeKind.SKELETON_FAST_ARROW,
            ExtremeKind.SKELETON_LIGHTNING,ExtremeKind.SKELETON_FLOATING,ExtremeKind.SKELETON_BLAZE,
            ExtremeKind.WITHER_SKELETON_ARROW,ExtremeKind.SKELETON_ZOMBIE);
    private EarlyEquipmentBridgeGameTests() {}

    @GameTest(template="gear_empty",batch="early_equipment_bridge")
    public static void actualPolicyRecognizesExactlySevenAddonArchersRatherThanEverySkeletonFamily(GameTestHelper h) {
        try(var site=new Site(h,35)) {
            for(var kind:ExtremeKind.values()) {
                Mob mob=site.create(kind);
                h.assertValueEqual(MiteExtensions.policy().earlyEquipmentArcher(mob,true),ARCHERS.contains(kind),
                        "The real installed policy identifies the exact addon combat role: "+kind);
            }
            Mob wither=site.nativeMob(EntityType.WITHER_SKELETON);
            h.assertFalse(BloodMoonEquipment.earlyEquipmentArcher(wither),"The native melee wither skeleton never receives a bow");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="early_equipment_bridge")
    public static void sevenFinalizedArchersRetainNativeEquipmentAndRangedAiFromTheFirstDay(GameTestHelper h) {
        for(long day:new long[]{1,35,36})try(var site=new Site(h,day)) {
            for(var kind:ARCHERS) {
                var skeleton=(AbstractSkeleton)site.create(kind);
                site.spawn(skeleton);
                h.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(skeleton),"Native equipment is available without a day35 boundary: "+kind+"/"+day);
                nativeArcherEquipment(h,skeleton,kind);
                var before=visibleEquipment(skeleton);
                ItemStack spare=((ExtremeMob)skeleton).extremeMemory().spare.copy();
                for(int tick=0;tick<12;tick++)NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(skeleton));
                unchangedEquipment(h,skeleton,before);
                h.assertTrue(ItemStack.matches(((ExtremeMob)skeleton).extremeMemory().spare,spare),"Idle ticks retain the role's native hidden spare");
                var target=EntityType.COW.create(site.level);site.entities.add(target);
                target.moveTo(site.origin.add(8D,0D,0D));target.setNoAi(true);target.setNoGravity(true);target.setInvulnerable(true);
                h.assertTrue(site.level.addFreshEntity(target),"The real arrow target enters the level");skeleton.setTarget(target);
                SourceRangedAttackGoal ranged=skeleton.goalSelector.getAvailableGoals().stream().map(w->w.getGoal())
                        .filter(SourceRangedAttackGoal.class::isInstance).map(SourceRangedAttackGoal.class::cast).findFirst().orElseThrow();
                h.assertTrue(ranged.canUse(),"The actual installed ranged goal accepts its target: "+kind);
                h.assertFalse(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal() instanceof SourceMeleeGoal||w.getGoal() instanceof MeleeAttackGoal),"No stale melee scheduler competes with the actual ranged bow goal");
                List<AbstractArrow> arrows=new ArrayList<>();
                Consumer<EntityJoinLevelEvent> observer=event->{if(event.getEntity() instanceof AbstractArrow arrow&&arrow.getOwner()==skeleton){arrows.add(arrow);site.entities.add(arrow);}};
                NeoForge.EVENT_BUS.addListener(observer);
                try {ranged.start();for(int tick=0;tick<ranged.attackInterval()+2;tick++)ranged.tick();}
                finally {ranged.stop();NeoForge.EVENT_BUS.unregister(observer);}
                h.assertTrue(arrows.stream().anyMatch(arrow->arrow.isAddedToLevel()&&arrow.getDeltaMovement().lengthSqr()>.001D),
                        "The installed source bow AI actually creates an owned moving arrow: "+kind);
                unchangedEquipment(h,skeleton,before);nativeArcherEquipment(h,skeleton,kind);
                target.discard();skeleton.discard();arrows.forEach(Entity::discard);
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="early_equipment_bridge")
    public static void meleeBossesRetainTheirSpeciesWeaponsAndArmorBeforeAndAfterDay35(GameTestHelper h) {
        var expected=java.util.Map.of(ExtremeKind.EXCHANGER,"minecraft:ender_pearl",ExtremeKind.SKELETON_KNOCKBACK,"minecraft:wooden_sword",
                ExtremeKind.SKELETON_ELITE_LORD,"mite:vibranium_sword",ExtremeKind.ANNIHILATION_SKELETON,"mite:vibranium_dagger",
                ExtremeKind.ZOMBIE_BOSS,"mite:vibranium_war_hammer",ExtremeKind.UNDYING_ZOMBIE_EMPEROR,"modern_ite:club_vibranium");
        for(long day:new long[]{1,35,36})try(var site=new Site(h,day)) {
            for(var entry:expected.entrySet()) {
                var kind=entry.getKey();Mob mob=site.create(kind);site.spawn(mob);
                h.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob),"Every native boss loadout is available from day one: "+kind);
                h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mob.getMainHandItem().getItem()).toString().equals(entry.getValue()),
                        "The actual species weapon survives the real spawn pipeline: "+kind+"/"+day);
                h.assertFalse(BloodMoonEquipment.earlyEquipmentArcher(mob),"A skeleton-shaped boss is still a melee role");
                if(kind!=ExtremeKind.EXCHANGER&&kind!=ExtremeKind.SKELETON_KNOCKBACK)
                    for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
                        h.assertFalse(mob.getItemBySlot(slot).isEmpty(),"Fixed boss armor exists from the first day: "+kind+"/"+slot);
                if(kind==ExtremeKind.UNDYING_ZOMBIE_EMPEROR)h.assertTrue(mob.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST),"Equipment and the emperor's independent ability both survive");
                mob.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="early_equipment_bridge")
    public static void loadedRetiredLocksPreserveVisibleAndHiddenEquipmentAndAllowNativeCombat(GameTestHelper h) {
        try(var site=new Site(h,100)) {
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.moveTo(site.origin.add(8D,0D,0D));
            player.setItemSlot(EquipmentSlot.MAINHAND,named(Items.IRON_SWORD,"player sword"));
            player.setItemSlot(EquipmentSlot.CHEST,named(Items.IRON_CHESTPLATE,"player chest"));
            for(var kind:ARCHERS) {
                Mob original=site.create(kind);((ExtremeMob)original).extremeMemory().initialized=true;lock(original);seedStock(original);
                original.getPersistentData().putInt("modern_ite:combat_revision",1);
                ItemStack customChest=named(Items.IRON_CHESTPLATE,"saved chest"),customOffhand=named(Items.STICK,"picked up");
                MobAbilities.enchant(original,customChest,"minecraft:protection",2);
                original.setItemSlot(EquipmentSlot.CHEST,customChest);original.setItemSlot(EquipmentSlot.OFFHAND,customOffhand);
                ItemStack customBow=kind==ExtremeKind.MIRROR_SKELETON?named(Items.BOW,"custom saved bow"):ItemStack.EMPTY;
                if(!customBow.isEmpty())MobAbilities.enchant(original,customBow,"minecraft:power",1);
                original.setItemSlot(EquipmentSlot.MAINHAND,customBow);
                CompoundTag saved=new CompoundTag();original.saveWithoutId(saved);
                var restored=(AbstractSkeleton)site.create(kind);restored.load(saved);
                var stale=new MeleeAttackGoal(restored,1D,false);restored.goalSelector.addGoal(3,stale);
                NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(restored,site.level,true));
                h.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(restored)||restored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).contains("early_equipment_locked"),
                        "Loading retires the obsolete birth-lock marker instead of re-enabling it");
                h.assertTrue(ItemStack.matches(restored.getItemBySlot(EquipmentSlot.CHEST),customChest)&&ItemStack.matches(restored.getOffhandItem(),customOffhand),
                        "Retiring old locks preserves exact saved and picked-up visible components");
                if(customBow.isEmpty())h.assertTrue(restored.getMainHandItem().is(Items.BOW),"An old empty archer receives its missing ordinary bow");
                else h.assertTrue(ItemStack.matches(restored.getMainHandItem(),customBow),"A visible custom bow is never replaced");
                hiddenPreserved(h,restored);
                h.assertFalse(restored.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==stale),"Actual loaded bow reassessment removes the stale vanilla melee goal");
                // Isolate the species' own swap from the separate optional native-skeleton backup system.
                if(kind==ExtremeKind.SKELETON_ZOMBIE){restored.getPersistentData().putBoolean("modern_ite:switch_weapon",false);player.moveTo(site.origin.add(2D,0D,0D));}
                else player.moveTo(site.origin.add(kind==ExtremeKind.MIRROR_SKELETON?2D:8D,0D,0D));
                h.assertTrue(player.isAlive(),"The skill target remains a living survival player");
                restored.setTarget(player);h.assertTrue(restored.getTarget()==player,"The loaded monster actually accepts the skill target");
                ((ExtremeMob)restored).extremeMemory().clock=19;
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(restored));
                if(kind==ExtremeKind.MIRROR_SKELETON)
                    h.assertTrue(((ExtremeMob)restored).extremeMemory().mirrored&&ItemStack.matches(restored.getMainHandItem(),player.getMainHandItem())&&ItemStack.matches(restored.getItemBySlot(EquipmentSlot.CHEST),player.getItemBySlot(EquipmentSlot.CHEST)),
                            "A retired lock permits the mirror's real equipment-copy ability");
                else if(kind==ExtremeKind.SKELETON_ZOMBIE)
                    h.assertTrue(restored.getMainHandItem().is(Items.IRON_SWORD)&&((ExtremeMob)restored).extremeMemory().spare.getItem() instanceof net.minecraft.world.item.BowItem,
                            "A retired lock permits the skeleton-zombie's saved spare swap at melee distance");
                else h.assertTrue(restored.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem&&ItemStack.matches(restored.getItemBySlot(EquipmentSlot.CHEST),customChest),
                        "Unrelated archer roles preserve their saved bow and armor during combat");
            }
            for(var kind:List.of(ExtremeKind.UNDYING_ZOMBIE_EMPEROR,ExtremeKind.FISHING_ROD_ZOMBIE)) {
                Mob mob=site.create(kind);lock(mob);((ExtremeMob)mob).extremeMemory().initialized=true;
                mob.getPersistentData().putInt("modern_ite:combat_revision",1);
                ItemStack custom=named(kind==ExtremeKind.FISHING_ROD_ZOMBIE?Items.FISHING_ROD:Items.IRON_HELMET,"loaded custom item");
                if(kind==ExtremeKind.UNDYING_ZOMBIE_EMPEROR)MobAbilities.enchant(mob,custom,"minecraft:protection",2);
                var slot=kind==ExtremeKind.FISHING_ROD_ZOMBIE?EquipmentSlot.MAINHAND:EquipmentSlot.HEAD;mob.setItemSlot(slot,custom);
                NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(mob,site.level,true));
                h.assertTrue(ItemStack.matches(mob.getItemBySlot(slot),custom),"Retiring an obsolete lock preserves current custom equipment and enchantments");
                h.assertTrue(mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"Retiring the lock alone does not reroll missing equipment on an already migrated saved entity");
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="early_equipment_bridge")
    public static void retiredLocksAllowConfiguredNativeMeleeAndOneTimePigmanBowRolls(GameTestHelper h) {
        try(var settings=new IteReferenceSpawnGameTests.Settings();var site=new Site(h,35)) {
            settings.set("skeleton_force_melee_attack",true);settings.set("zombie_pigman_use_bow",true);
            var skeleton=(AbstractSkeleton)site.nativeMob(EntityType.SKELETON);lock(skeleton);seedStock(skeleton);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(skeleton,site.level,true));
            skeleton.tickCount=10;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(skeleton));
            h.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(skeleton),"The retired old marker cannot block configured native combat");
            h.assertTrue(skeleton.getPersistentData().getBoolean("modern_ite:forced_melee_active")
                            &&skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal() instanceof SourceMeleeGoal),
                    "An enabled saved melee roll installs the actual source melee goal while holding a bow");
            h.assertTrue(ItemStack.parseOptional(skeleton.registryAccess(),skeleton.getPersistentData().getCompound("modern_ite:stocked_weapon")).is(Items.IRON_SWORD),
                    "Retiring a lock retains the native hidden backup weapon");
            settings.set("skeleton_force_melee_attack",false);NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(skeleton));
            h.assertFalse(skeleton.getPersistentData().getBoolean("modern_ite:forced_melee_active"),"Disabling the option clears its active forced-melee state");
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal() instanceof SourceRangedAttackGoal),"The real ranged scheduler returns after disabling forced melee");
            int bows=0;
            for(int attempt=0;attempt<64;attempt++) {
                Mob pig=site.nativeMob(EntityType.ZOMBIFIED_PIGLIN);lock(pig);pig.getRandom().setSeed(attempt);
                NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(pig,site.level,true));
                h.assertTrue(pig.getPersistentData().getBoolean("modern_ite:pigman_rolls")&&!BloodMoonEquipment.spawnEquipmentLocked(pig),"Every saved pigman completes its configured one-time combat roll");
                ItemStack rolled=pig.getMainHandItem().copy();if(rolled.is(Items.BOW))bows++;
                NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(pig,site.level,true));
                h.assertTrue(ItemStack.matches(pig.getMainHandItem(),rolled),"Rejoining cannot reroll or replace the decided pigman weapon");
            }
            h.assertTrue(bows>0&&bows<64,"Deterministic seeds exercise both admitted bow rolls and ordinary pigmen before day36");
        }
        h.succeed();
    }

    private static ItemStack named(net.minecraft.world.item.Item item,String name){var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));return stack;}
    private static void lock(Mob mob){var tag=new CompoundTag();tag.putBoolean("early_equipment_locked",true);mob.getPersistentData().put(BloodMoonEquipment.DATA_KEY,tag);}
    private static void seedStock(Mob mob){
        if(mob instanceof ExtremeMob extreme)extreme.extremeMemory().spare=new ItemStack(Items.IRON_SWORD);
        var data=mob.getPersistentData();data.put("modern_ite:stocked_weapon",new ItemStack(Items.IRON_SWORD).save(mob.registryAccess()));
        data.putBoolean("modern_ite:switch_weapon",true);data.putBoolean("modern_ite:forced_melee_active",true);data.putBoolean("modern_ite:melee_roll",true);
    }
    private static void nativeArcherEquipment(GameTestHelper h,Mob mob,ExtremeKind kind){
        h.assertTrue(mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem&&mob.getMainHandItem().getCount()==1,"The actual archer retains one native or material bow: "+kind);
        if(kind==ExtremeKind.SKELETON_BLAZE)h.assertTrue(io.github.ningmeng123.mite.enchantment.R196Enchantments.level(mob.getMainHandItem(),mob.level(),"minecraft:flame")==2,"The first-day blaze archer keeps its native Flame II");
        if(kind==ExtremeKind.WITHER_SKELETON_ARROW)h.assertTrue(io.github.ningmeng123.mite.enchantment.R196Enchantments.level(mob.getMainHandItem(),mob.level(),"minecraft:punch")==2,"The first-day wither archer keeps its native Punch II");
        if(kind==ExtremeKind.SKELETON_ZOMBIE)h.assertTrue(!((ExtremeMob)mob).extremeMemory().spare.isEmpty(),"The first-day skeleton-zombie retains its own hidden melee weapon");
    }
    private static List<ItemStack> visibleEquipment(Mob mob){return java.util.Arrays.stream(EquipmentSlot.values()).map(slot->mob.getItemBySlot(slot).copy()).toList();}
    private static void unchangedEquipment(GameTestHelper h,Mob mob,List<ItemStack> before){
        for(var slot:EquipmentSlot.values())h.assertTrue(ItemStack.matches(mob.getItemBySlot(slot),before.get(slot.ordinal())),"Idle/ranged activity preserves exact species equipment: "+slot);
    }
    private static void hiddenPreserved(GameTestHelper h,Mob mob){
        if(mob instanceof ExtremeMob extreme)h.assertTrue(extreme.extremeMemory().spare.is(Items.IRON_SWORD),"The saved addon MobMemory spare is preserved");
        var data=mob.getPersistentData();
        h.assertTrue(ItemStack.parseOptional(mob.registryAccess(),data.getCompound("modern_ite:stocked_weapon")).is(Items.IRON_SWORD)&&data.getBoolean("modern_ite:switch_weapon"),"The saved native hidden backup and switching decision are preserved");
    }
    private static final class Site implements AutoCloseable {
        final GameTestHelper helper;final ServerLevel level;final Vec3 origin;final long time,highest;final boolean dirty;
        final List<Entity> entities=new ArrayList<>();
        Site(GameTestHelper helper,long day){
            this.helper=helper;level=helper.getLevel();time=level.getServer().overworld().getDayTime();
            var history=BloodMoonInvasionData.get(level);highest=history.highestDay();dirty=history.isDirty();
            level.getServer().overworld().setDayTime((day-1)*24000L+6000L);history.restoreProgress(day);
            origin=helper.absolutePos(new BlockPos(1,180,1)).getCenter();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk((BlockPos.containing(origin).getX()>>4)+x,(BlockPos.containing(origin).getZ()>>4)+z);
        }
        Mob create(ExtremeKind kind){return nativeMob(ExtremeMobs.type(kind));}
        Mob nativeMob(EntityType<?> type){Mob mob=(Mob)type.create(level);if(mob==null)throw new AssertionError("Registered equipment fixture creates: "+type);entities.add(mob);mob.moveTo(origin);mob.setNoAi(true);mob.setNoGravity(true);return mob;}
        void spawn(Mob mob){EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.SPAWN_EGG,null);helper.assertTrue(!mob.isSpawnCancelled()&&level.addFreshEntity(mob),"The actual finalized test spawn enters its real level");if(mob instanceof ExtremeMob extreme)extreme.extremeMemory().entourage=true;}
        @Override public void close(){entities.forEach(Entity::discard);level.getServer().overworld().setDayTime(time);var history=BloodMoonInvasionData.get(level);history.restoreProgress(highest);history.setDirty(dirty);}
    }
}
