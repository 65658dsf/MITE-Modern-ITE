package io.github.ningmeng123.modernite.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;

/** Real registry, save, combat and inherited-identity integration checks. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ExtremeMobGameTests {
    private ExtremeMobGameTests(){}
    private static Mob mob(GameTestHelper h,ExtremeKind k){var m=ExtremeMobs.type(k).create(h.getLevel());h.assertTrue(m!=null,"Registered mob creates: "+k);m.moveTo(h.absolutePos(new BlockPos(1,2,1)).getCenter());MobAbilities.initialize(m,(ExtremeMob)m);return m;}
    @GameTest(template="gear_empty")
    public static void allFortyFiveIdentitiesPersistTheirActualCombatState(GameTestHelper h){
        UUID player=UUID.randomUUID();int count=0;
        for(var kind:ExtremeKind.values()){
            Mob m=mob(h,kind);MobMemory memory=((ExtremeMob)m).extremeMemory();
            memory.clock=401;memory.summons=6;memory.entourage=true;memory.stage=2;memory.bats=5;memory.cursed=true;
            memory.phaseMaximum=4;memory.phaseCharges=1;memory.mimicName="NingMeng123";memory.contribution.put(player,57.25F);
            memory.spare=new ItemStack(Items.IRON_SWORD);memory.spare.setDamageValue(13);
            CompoundTag saved=new CompoundTag();m.saveWithoutId(saved);
            Mob copy=ExtremeMobs.type(kind).create(h.getLevel());copy.load(saved);MobMemory read=((ExtremeMob)copy).extremeMemory();
            h.assertTrue(read.clock==401&&read.summons==6&&read.entourage&&read.stage==2&&read.bats==5&&read.cursed,"Summon and curse stages survive: "+kind);
            h.assertTrue(read.phaseCharges==1&&read.phaseMaximum==4&&read.spare.getDamageValue()==13&&read.contribution.get(player)==57.25F,"Charge, weapon and contribution survive: "+kind);
            h.assertTrue(copy.getAttribute(Attributes.MAX_HEALTH)!=null&&copy.getMaxHealth()>0,"Every real identity has attributes: "+kind);count++;
        }
        h.assertTrue(count==45,"41 Extreme identities plus 4 production ITE prerequisites");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void mirrorCopiesEquipmentWithoutSharingOrDroppingPlayerItems(GameTestHelper h){
        Mob m=mob(h,ExtremeKind.MIRROR_ZOMBIE);var state=(ExtremeMob)m;var player=h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword=new ItemStack(Items.IRON_SWORD);sword.setDamageValue(17);player.setItemSlot(EquipmentSlot.MAINHAND,sword);
        m.setTarget(player);state.extremeMemory().clock=19;MobAbilities.tick(m,state);
        h.assertTrue(m.getMainHandItem().is(Items.IRON_SWORD)&&m.getMainHandItem()!=sword,"Mirror receives a copy of the player's held stack");
        m.getMainHandItem().setDamageValue(29);h.assertTrue(sword.getDamageValue()==17,"Monster wear cannot mutate the player's inventory");
        h.assertTrue(state.extremeMemory().mirrored,"Copy ability ran through the combat tick");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void fixedArmorBlocksTheRealExchangerPositionSwap(GameTestHelper h){
        Mob m=mob(h,ExtremeKind.EXCHANGER);var state=(ExtremeMob)m;var player=h.makeMockPlayer(GameType.SURVIVAL);
        var origin=m.position();player.moveTo(origin.add(4,0,0));var playerOrigin=player.position();
        ItemStack chest=new ItemStack(Items.IRON_CHESTPLATE);
        var fixed=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.parse("modern_ite:fixed")));
        chest.enchant(fixed,1);player.setItemSlot(EquipmentSlot.CHEST,chest);m.setTarget(player);
        state.extremeMemory().stage=1;state.extremeMemory().cooldown=0;MobAbilities.tick(m,state);
        h.assertTrue(m.position().equals(origin)&&player.position().equals(playerOrigin),"Fixed enchantment protects the two real positions");
        player.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);state.extremeMemory().stage=1;MobAbilities.tick(m,state);
        h.assertTrue(m.position().distanceToSqr(playerOrigin)<.001&&player.position().distanceToSqr(origin)<.001,"Without fixed, both XYZ positions exchange");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void sourceBossesExceedTheOrdinaryAttributeCeiling(GameTestHelper h){
        var dragon=EntityType.ENDER_DRAGON.create(h.getLevel());var wither=EntityType.WITHER.create(h.getLevel());
        ExtremeNativeMobs.initialize(dragon);ExtremeNativeMobs.initialize(wither);
        h.assertTrue(dragon.getMaxHealth()==7500&&wither.getAttributeBaseValue(Attributes.MAX_HEALTH)==1300,"Configured boss health is not clamped to 1024");
        h.assertTrue(wither.getPersistentData().getBoolean("modern_ite:boss"),"Base calendar policy can retain high boss health");
        double base=wither.getAttributeBaseValue(Attributes.MAX_HEALTH);io.github.ningmeng123.mite.entity.MonsterDayScaling.refresh(wither);double first=wither.getMaxHealth();
        io.github.ningmeng123.mite.entity.MonsterDayScaling.refresh(wither);
        h.assertTrue(wither.getAttributeBaseValue(Attributes.MAX_HEALTH)==base&&wither.getMaxHealth()==first,"Calendar refresh never compounds the source baseline");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void sourceAiAndDiggingRecognizeTheNewRegisteredIdentities(GameTestHelper h){
        var m=(ExtremeMobs.ZombieVariant)mob(h,ExtremeKind.MINER_ZOMBIE);
        h.assertTrue(io.github.ningmeng123.mite.entity.R196MonsterAi.managed(m),"Miner uses current MITE pathing and door logic");
        h.assertTrue(io.github.ningmeng123.mite.entity.R196DiggerGoal.watcher(m),"Inherited AnimalWatcher behavior is preserved");
        h.assertTrue(io.github.ningmeng123.mite.entity.R196DiggerGoal.first(m)!=null,"Miner has an actual digger goal");
        h.assertTrue(io.github.ningmeng123.mite.worldgen.R196MobSpawning.sourceClass(m).equals("tw")&&m.getType()==ExtremeMobs.type(ExtremeKind.MINER_ZOMBIE),"Behavior inheritance retains the distinct entity type");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void nonReflectableFireballsRetainTheirBlastProfile(GameTestHelper h){
        var fireball=InfernalFireball.NB.get().create(h.getLevel());fireball.normalPower(18);CompoundTag saved=new CompoundTag();fireball.saveWithoutId(saved);
        var read=InfernalFireball.NB.get().create(h.getLevel());read.load(saved);
        h.assertTrue(read.normalPower()==18&&!read.isPickable()&&!read.hurt(h.getLevel().damageSources().generic(),4),"Dragon boost, no deflection and impact profile survive a save");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void retiredCreaturesAndEggsAreAbsentFromTheRegistries(GameTestHelper h){
        for (String path : new String[]{"revenant_ghast", "pillager", "pillager_leader"}) {
            h.assertFalse(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath("modern_ite",path)),"Removed entity: "+path);
            h.assertFalse(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("modern_ite",path+"_spawn_egg")),"Removed spawn egg: "+path);
        }
        h.assertTrue(EntityType.PILLAGER.create(h.getLevel())!=null,"Vanilla pillagers remain available");h.succeed();
    }
}
