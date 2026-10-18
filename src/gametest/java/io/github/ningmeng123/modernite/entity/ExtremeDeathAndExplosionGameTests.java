package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real explosion, serialized inventory escrow and vanilla crossbow integration regressions. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ExtremeDeathAndExplosionGameTests {
    private ExtremeDeathAndExplosionGameTests(){}
    private static Mob mob(GameTestHelper h,ExtremeKind kind){
        Mob mob=ExtremeMobs.type(kind).create(h.getLevel());
        h.assertTrue(mob!=null,"Registered entity creates: "+kind);
        mob.moveTo(h.absolutePos(new BlockPos(2,2,2)).getCenter());
        MobAbilities.initialize(mob,(ExtremeMob)mob);mob.setNoAi(true);return mob;
    }

    @GameTest(template="gear_empty")
    public static void mimicRecoversEveryStoredStackAfterReloadWithoutDependingOnMobLoot(GameTestHelper h){
        Mob mimic=mob(h,ExtremeKind.MIMIC_ZOMBIE);
        List<ItemEntity> incoming=new ArrayList<>();List<ItemStack> expected=new ArrayList<>();
        for(int i=0;i<36;i++){
            ItemStack stack=new ItemStack(Items.COBBLESTONE,i+1);
            stack.set(DataComponents.CUSTOM_NAME,Component.literal("Inventory slot "+i));
            incoming.add(new ItemEntity(h.getLevel(),mimic.getX(),mimic.getY(),mimic.getZ(),stack));expected.add(stack.copy());
        }
        ItemStack helmet=new ItemStack(Items.IRON_HELMET);helmet.setDamageValue(37);
        helmet.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),5);
        incoming.add(new ItemEntity(h.getLevel(),mimic.getX(),mimic.getY(),mimic.getZ(),helmet));expected.add(helmet.copy());
        PlayerMimicInventory.capture(mimic,incoming);
        mimic.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(200);
        incoming.getFirst().getItem().shrink(1);
        CompoundTag saved=new CompoundTag();mimic.saveWithoutId(saved);
        Mob loaded=ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE).create(h.getLevel());loaded.load(saved);
        h.assertTrue(saved.getCompound("NeoForgeData").getList("modern_ite:player_death_inventory",10).size()==expected.size(),"The entity save includes every escrowed stack");
        h.assertTrue(loaded.getPersistentData().getList("modern_ite:player_death_inventory",10).size()==expected.size(),"The loaded entity retains every escrowed stack");
        var inventory=loaded.getPersistentData().getList("modern_ite:player_death_inventory",10);
        for(int i=0;i<inventory.size();i++){
            ItemStack decoded=ItemStack.parseOptional(loaded.registryAccess(),inventory.getCompound(i));
            h.assertTrue(ItemStack.matches(expected.get(i),decoded),"Serialized inventory entry "+i+" decodes unchanged: "+inventory.getCompound(i));
        }
        List<ItemEntity> recovered=new ArrayList<>();boolean loot=h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        try{
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false,h.getLevel().getServer());
            ExtremeMobEvents.drops(new LivingDropsEvent(loaded,h.getLevel().damageSources().generic(),recovered,false));
        }finally{h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(loot,h.getLevel().getServer());}
        h.assertTrue(recovered.size()==expected.size(),"All inventory stacks survive, including more than the six equipment slots: expected "+expected.size()+", recovered "+recovered.size());
        for(int i=0;i<expected.size();i++)h.assertTrue(ItemStack.matches(expected.get(i),recovered.get(i).getItem()),"Count, wear and enchantments survive unchanged in slot "+i);
        ExtremeMobEvents.drops(new LivingDropsEvent(loaded,h.getLevel().damageSources().generic(),recovered,false));
        h.assertTrue(recovered.size()==expected.size(),"A second death-drop callback cannot duplicate escrowed items");h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void playerDeathTransfersActualDropsToOnePersistentMimic(GameTestHelper h){
        String name="DeathEscrow"+UUID.randomUUID().toString().substring(0,5);
        ServerPlayer player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
        player.setPos(h.absolutePos(new BlockPos(2,2,2)).getCenter());
        List<ItemEntity> incoming=new ArrayList<>();incoming.add(new ItemEntity(h.getLevel(),player.getX(),player.getY(),player.getZ(),new ItemStack(Items.DIAMOND,23)));
        ExtremeMobEvents.drops(new LivingDropsEvent(player,h.getLevel().damageSources().generic(),incoming,false));
        var mimics=h.getLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(2),m->m instanceof ExtremeMob e&&e.extremeKind()==ExtremeKind.MIMIC_ZOMBIE&&name.equals(e.extremeMemory().mimicName));
        h.assertTrue(mimics.size()==1&&incoming.isEmpty(),"Only after the mimic joins does the original drop collection transfer");
        Mob mimic=mimics.getFirst();List<ItemEntity> recovered=new ArrayList<>();
        PlayerMimicInventory.release(mimic,recovered);
        h.assertTrue(mimic.isPersistenceRequired()&&recovered.size()==1&&recovered.getFirst().getItem().getCount()==23,"The persistent substitute owns the exact real player drop");
        mimic.discard();h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void emptyDeathDropsNeverCopyThePlayersKeptInventory(GameTestHelper h){
        String name="KeptItems"+UUID.randomUUID().toString().substring(0,5);
        ServerPlayer player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
        player.setPos(h.absolutePos(new BlockPos(2,2,2)).getCenter());player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,11));
        ExtremeMobEvents.drops(new LivingDropsEvent(player,h.getLevel().damageSources().generic(),new ArrayList<>(),false));
        var mimics=h.getLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(2),m->m instanceof ExtremeMob e&&name.equals(e.extremeMemory().mimicName));
        h.assertTrue(mimics.size()==1,"A substitute can spawn for a death with no transferable drops");List<ItemEntity> recovered=new ArrayList<>();
        PlayerMimicInventory.release(mimics.getFirst(),recovered);
        h.assertTrue(recovered.isEmpty()&&player.getInventory().getItem(0).getCount()==11,"keepInventory-style deaths neither steal nor duplicate inventory");
        mimics.getFirst().discard();h.succeed();
    }

    @GameTest(template="gear_empty",timeoutTicks=40)
    public static void invisibleCreeperExplosionCannotCreateAnInfiniteInvisibilityCloud(GameTestHelper h){
        Creeper creeper=(Creeper)mob(h,ExtremeKind.INVISIBLE_CREEPER);
        CompoundTag saved=new CompoundTag();creeper.saveWithoutId(saved);saved.putShort("Fuse",(short)1);saved.putByte("ExplosionRadius",(byte)0);creeper.load(saved);
        h.getLevel().addFreshEntity(creeper);creeper.ignite();
        var bounds=creeper.getBoundingBox().inflate(3);
        h.runAfterDelay(5,()->{
            h.assertTrue(creeper.isRemoved(),"The real creeper fuse completes");
            h.assertTrue(h.getLevel().getEntitiesOfClass(AreaEffectCloud.class,bounds).isEmpty(),"No lingering potion cloud is created from the permanent invisibility buff");h.succeed();
        });
    }

    @GameTest(template="gear_empty")
    public static void specialCreeperBlastsApplyFlashAndWitherEvenWhenDamageIsResisted(GameTestHelper h){
        for(ExtremeKind kind:List.of(ExtremeKind.FLASH_CREEPER,ExtremeKind.WITHER_CREEPER)){
            Mob creeper=mob(h,kind);var player=h.makeMockPlayer(GameType.SURVIVAL);player.moveTo(creeper.position().add(1,0,0));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,4));h.getLevel().addFreshEntity(player);
            h.getLevel().explode(creeper,creeper.getX(),creeper.getY()+.5,creeper.getZ(),2F,Level.ExplosionInteraction.NONE);
            h.assertTrue(player.hasEffect(kind==ExtremeKind.FLASH_CREEPER?ExtremeEffects.VALUES.get("flash"):MobEffects.WITHER),"The real explosion applies "+kind+" independently of health damage");
            player.discard();creeper.discard();
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void witchesRejectFriendlyTargetsAndDamage(GameTestHelper h){
        var witch=EntityType.WITCH.create(h.getLevel());Mob lord=mob(h,ExtremeKind.WITCH_LORD);
        witch.setTarget(lord);lord.setTarget(witch);
        h.assertTrue(witch.getTarget()==null&&lord.getTarget()==null,"Vanilla and extreme witches cannot target each other");
        float health=lord.getHealth();lord.hurt(h.getLevel().damageSources().indirectMagic(witch,witch),8);
        h.assertTrue(lord.getHealth()==health,"Friendly potion damage cannot hurt another witch");h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void vanillaPillagerKeepsItsIdentityAndActuallyFiresTheUpgradedCrossbow(GameTestHelper h){
        var level=h.getLevel();var history=BloodMoonInvasionData.get(level);long day=history.highestDay();boolean dirty=history.isDirty();
        var pillager=EntityType.PILLAGER.create(level);pillager.moveTo(h.absolutePos(new BlockPos(2,2,2)).getCenter());pillager.setNoAi(true);
        try{
            history.observeDay(33);EventHooks.finalizeMobSpawn(pillager,level,level.getCurrentDifficultyAt(pillager.blockPosition()),MobSpawnType.PATROL,null);
            pillager.getMainHandItem().enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.QUICK_CHARGE),1);
            level.addFreshEntity(pillager);
        }finally{history.restoreProgress(day);history.setDirty(dirty);}
        ItemStack weapon=pillager.getMainHandItem();
        h.assertTrue(pillager.getType()==EntityType.PILLAGER&&weapon.is(ExtremeContent.CROSSBOW.get()),"Vanilla patrol identity retains the addon weapon after default spawn equipment runs");
        h.assertTrue(weapon.getEnchantments().getLevel(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.QUICK_CHARGE))==1,"Upgrading retains the original weapon enchantments");
        h.assertTrue(pillager.canFireProjectileWeapon((ProjectileWeaponItem)weapon.getItem()),"Vanilla projectile selection accepts the addon crossbow");
        var target=h.makeMockPlayer(GameType.SURVIVAL);target.moveTo(pillager.position().add(4,0,0));pillager.setTarget(target);
        weapon.set(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.of(new ItemStack(Items.ARROW)));pillager.performRangedAttack(target,1);
        var arrows=level.getEntitiesOfClass(AbstractArrow.class,pillager.getBoundingBox().inflate(10),arrow->arrow.getOwner()==pillager);
        h.assertTrue(!arrows.isEmpty()&&weapon.getOrDefault(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.EMPTY).isEmpty(),"The real vanilla firing API emits and consumes its charged projectile");
        arrows.forEach(AbstractArrow::discard);pillager.discard();h.succeed();
    }
}
