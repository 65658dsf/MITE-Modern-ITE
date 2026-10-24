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
    public static void sevenFinalizedEarlyArchersKeepPlainBowsEmptyStockAndRealRangedAiThroughDay35(GameTestHelper h) {
        for(long day:new long[]{1,35})try(var site=new Site(h,day)) {
            for(var kind:ARCHERS) {
                var skeleton=(AbstractSkeleton)site.create(kind);
                seedStock(skeleton);
                site.spawn(skeleton);
                h.assertTrue(BloodMoonEquipment.spawnEquipmentLocked(skeleton),"The actual spawn is locked through day35: "+kind);
                plain(h,skeleton); hiddenEmpty(h,skeleton);
                for(int tick=0;tick<12;tick++)NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(skeleton));
                plain(h,skeleton); hiddenEmpty(h,skeleton);
                var target=EntityType.COW.create(site.level);site.entities.add(target);
                target.moveTo(site.origin.add(8D,0D,0D));target.setNoAi(true);target.setNoGravity(true);target.setInvulnerable(true);
                h.assertTrue(site.level.addFreshEntity(target),"The real arrow target enters the level");skeleton.setTarget(target);
                SourceRangedAttackGoal ranged=skeleton.goalSelector.getAvailableGoals().stream().map(w->w.getGoal())
                        .filter(SourceRangedAttackGoal.class::isInstance).map(SourceRangedAttackGoal.class::cast).findFirst().orElseThrow();
                h.assertTrue(ranged.canUse(),"The actual installed ranged goal accepts its target: "+kind);
                h.assertFalse(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal() instanceof SourceMeleeGoal||w.getGoal() instanceof MeleeAttackGoal),
                        "No cached forced-melee goal competes with the archer's real bow goal");
                List<AbstractArrow> arrows=new ArrayList<>();
                Consumer<EntityJoinLevelEvent> observer=event->{if(event.getEntity() instanceof AbstractArrow arrow&&arrow.getOwner()==skeleton){arrows.add(arrow);site.entities.add(arrow);}};
                NeoForge.EVENT_BUS.addListener(observer);
                try {ranged.start();for(int tick=0;tick<ranged.attackInterval()+2;tick++)ranged.tick();}
                finally {ranged.stop();NeoForge.EVENT_BUS.unregister(observer);}
                h.assertTrue(arrows.stream().anyMatch(arrow->arrow.isAddedToLevel()&&arrow.getDeltaMovement().lengthSqr()>.001D),
                        "The installed source bow AI actually creates an owned moving arrow: "+kind);
                plain(h,skeleton); hiddenEmpty(h,skeleton);target.discard();skeleton.discard();arrows.forEach(Entity::discard);
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="early_equipment_bridge")
    public static void earlyMeleeBossesStayEmptyAndFreshDay36RestoresTheirInternalEquipment(GameTestHelper h) {
        var melee=List.of(ExtremeKind.EXCHANGER,ExtremeKind.SKELETON_KNOCKBACK,ExtremeKind.SKELETON_ELITE_LORD,
                ExtremeKind.ANNIHILATION_SKELETON,ExtremeKind.ZOMBIE_BOSS,ExtremeKind.UNDYING_ZOMBIE_EMPEROR);
        try(var site=new Site(h,35)) {
            for(var kind:melee) {
                Mob mob=site.create(kind);site.spawn(mob);
                for(var slot:EquipmentSlot.values())h.assertTrue(mob.getItemBySlot(slot).isEmpty(),"Early melee roles retain empty visible equipment: "+kind+"/"+slot);
                h.assertFalse(BloodMoonEquipment.earlyEquipmentArcher(mob),"A skeleton-shaped boss is still a melee role");
                if(kind==ExtremeKind.UNDYING_ZOMBIE_EMPEROR)h.assertTrue(mob.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST),"The equipment gate preserves the boss's independent ability effect");
                mob.discard();
            }
        }
        try(var site=new Site(h,36)) {
            for(var kind:List.of(ExtremeKind.SKELETON_BLAZE,ExtremeKind.WITHER_SKELETON_ARROW,ExtremeKind.SKELETON_ZOMBIE,ExtremeKind.UNDYING_ZOMBIE_EMPEROR)) {
                Mob mob=site.create(kind);site.spawn(mob);
                h.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob),"A fresh day36 spawn uses its normal equipment rules");
                if(kind==ExtremeKind.SKELETON_BLAZE||kind==ExtremeKind.WITHER_SKELETON_ARROW)
                    h.assertFalse(mob.getMainHandItem().getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty(),"Day36 restores the role's own bow enchantments: "+kind);
                if(kind==ExtremeKind.SKELETON_ZOMBIE)h.assertFalse(((ExtremeMob)mob).extremeMemory().spare.isEmpty(),"Day36 restores the skeleton-zombie's internal spare weapon");
                if(kind==ExtremeKind.UNDYING_ZOMBIE_EMPEROR)h.assertFalse(mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"Day36 restores fixed boss armor");
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="early_equipment_bridge")
    public static void loadedEarlyLocksClearHiddenCachesAndRestoreMissingBowsWithoutWashingCustomGear(GameTestHelper h) {
        try(var site=new Site(h,100)) {
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.moveTo(site.origin.add(8D,0D,0D));
            player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
            for(var kind:ARCHERS) {
                Mob original=site.create(kind);((ExtremeMob)original).extremeMemory().initialized=true;lock(original);seedStock(original);
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
                h.assertTrue(ItemStack.matches(restored.getItemBySlot(EquipmentSlot.CHEST),customChest)&&ItemStack.matches(restored.getOffhandItem(),customOffhand),
                        "Loaded locked entities keep visible saved and picked-up components exactly");
                if(customBow.isEmpty())h.assertTrue(restored.getMainHandItem().is(Items.BOW),"An old empty locked archer gains only its missing bow");
                else h.assertTrue(ItemStack.matches(restored.getMainHandItem(),customBow),"A visible custom bow is never replaced");
                hiddenEmpty(h,restored);
                h.assertFalse(restored.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==stale),"The real cached vanilla melee goal is removed, not just its NBT marker");
                restored.setTarget(player);((ExtremeMob)restored).extremeMemory().clock=19;
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(restored));
                h.assertTrue(restored.getMainHandItem().is(Items.BOW)&&ItemStack.matches(restored.getItemBySlot(EquipmentSlot.CHEST),customChest),
                        "Mirror copying and spare-weapon swapping cannot internally re-equip a locked birth");
                hiddenEmpty(h,restored);
            }
            for(var kind:List.of(ExtremeKind.UNDYING_ZOMBIE_EMPEROR,ExtremeKind.FISHING_ROD_ZOMBIE)) {
                Mob mob=site.create(kind);lock(mob);((ExtremeMob)mob).extremeMemory().initialized=true;
                ItemStack custom=named(kind==ExtremeKind.FISHING_ROD_ZOMBIE?Items.FISHING_ROD:Items.IRON_HELMET,"loaded custom item");
                if(kind==ExtremeKind.UNDYING_ZOMBIE_EMPEROR)MobAbilities.enchant(mob,custom,"minecraft:protection",2);
                mob.setItemSlot(kind==ExtremeKind.FISHING_ROD_ZOMBIE?EquipmentSlot.MAINHAND:EquipmentSlot.HEAD,custom);
                MobAbilities.initialize(mob,(ExtremeMob)mob);
                h.assertTrue(ItemStack.matches(mob.getItemBySlot(kind==ExtremeKind.FISHING_ROD_ZOMBIE?EquipmentSlot.MAINHAND:EquipmentSlot.HEAD),custom),"Saved-combat migration preserves locked custom equipment and its enchantments");
                h.assertTrue(mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"Saved-combat migration never silently adds the emperor's missing armor");
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="early_equipment_bridge")
    public static void lockedNativeForceMeleeAndPigmanBowRollsCannotRecreateBirthEquipment(GameTestHelper h) {
        try(var settings=new IteReferenceSpawnGameTests.Settings();var site=new Site(h,35)) {
            settings.set("skeleton_force_melee_attack",true);settings.set("zombie_pigman_use_bow",true);
            var skeleton=(AbstractSkeleton)site.nativeMob(EntityType.SKELETON);lock(skeleton);seedStock(skeleton);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
            var stale=new MeleeAttackGoal(skeleton,1D,false);skeleton.goalSelector.addGoal(3,stale);
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(skeleton,site.level,true));
            skeleton.tickCount=10;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(skeleton));
            hiddenEmpty(h,skeleton);
            h.assertFalse(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==stale),"Locked native archers discard the actual old forced-melee goal");
            for(int attempt=0;attempt<64;attempt++) {
                Mob pig=site.nativeMob(EntityType.ZOMBIFIED_PIGLIN);lock(pig);
                ExtremeNativeMobs.combatGoals(new EntityJoinLevelEvent(pig,site.level));
                h.assertTrue(pig.getMainHandItem().isEmpty(),"Internal pigman bow rolls cannot add equipment after the base birth lock");
            }
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
    private static void plain(GameTestHelper h,Mob mob){
        h.assertTrue(mob.getMainHandItem().is(Items.BOW)&&mob.getMainHandItem().getCount()==1
                &&mob.getMainHandItem().getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty(),"Only one plain ordinary bow is retained by an early addon archer");
        for(var slot:EquipmentSlot.values())if(slot!=EquipmentSlot.MAINHAND)h.assertTrue(mob.getItemBySlot(slot).isEmpty(),"All other visible early equipment slots remain empty: "+slot);
    }
    private static void hiddenEmpty(GameTestHelper h,Mob mob){
        if(mob instanceof ExtremeMob extreme)h.assertTrue(extreme.extremeMemory().spare.isEmpty(),"The addon MobMemory spare is empty");
        var data=mob.getPersistentData();h.assertFalse(data.contains("modern_ite:stocked_weapon")||data.contains("modern_ite:switch_weapon")||data.getBoolean("modern_ite:forced_melee_active"),"Addon stock, switching and forced melee stay cleared");
        h.assertTrue(data.getBoolean("modern_ite:stock_roll"),"The hidden stock roll stays completed to prevent a late reroll");
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
