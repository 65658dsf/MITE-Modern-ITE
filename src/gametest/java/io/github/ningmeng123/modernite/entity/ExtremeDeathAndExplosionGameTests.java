package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
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
    private static void mimicRoll(ServerPlayer player,int roll){
        for(long seed=0;seed<10_000;seed++){
            player.getRandom().setSeed(seed);
            if(player.getRandom().nextInt(10)==roll){player.getRandom().setSeed(seed);return;}
        }
        throw new IllegalStateException("No deterministic player random seed for mimic roll "+roll);
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
        mimicRoll(player,0);
        ExtremeMobEvents.drops(new LivingDropsEvent(player,h.getLevel().damageSources().generic(),incoming,false));
        var mimics=h.getLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(2),m->m instanceof ExtremeMob e&&e.extremeKind()==ExtremeKind.MIMIC_ZOMBIE&&name.equals(e.extremeMemory().mimicName));
        h.assertTrue(mimics.size()==1&&incoming.isEmpty(),"Only after the mimic joins does the original drop collection transfer");
        Mob mimic=mimics.getFirst();List<ItemEntity> recovered=new ArrayList<>();
        MonsterDayScaling.refresh(mimic);
        h.assertTrue(mimic.getMaxHealth()==10&&mimic.getHealth()==10,"The spawned player substitute has exactly ten health after joining and calendar scaling");
        PlayerMimicInventory.release(mimic,recovered);
        h.assertTrue(mimic.isPersistenceRequired()&&recovered.size()==1&&recovered.getFirst().getItem().getCount()==23,"The persistent substitute owns the exact real player drop");
        mimic.discard();h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void emptyDeathDropsNeverCopyThePlayersKeptInventory(GameTestHelper h){
        String name="KeptItems"+UUID.randomUUID().toString().substring(0,5);
        ServerPlayer player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
        player.setPos(h.absolutePos(new BlockPos(2,2,2)).getCenter());player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,11));
        mimicRoll(player,0);
        ExtremeMobEvents.drops(new LivingDropsEvent(player,h.getLevel().damageSources().generic(),new ArrayList<>(),false));
        var mimics=h.getLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(2),m->m instanceof ExtremeMob e&&name.equals(e.extremeMemory().mimicName));
        h.assertTrue(mimics.size()==1,"A substitute can spawn for a death with no transferable drops");List<ItemEntity> recovered=new ArrayList<>();
        PlayerMimicInventory.release(mimics.getFirst(),recovered);
        h.assertTrue(recovered.isEmpty()&&player.getInventory().getItem(0).getCount()==11,"keepInventory-style deaths neither steal nor duplicate inventory");
        mimics.getFirst().discard();h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void playerDeathMimicIgnoresConfiguredUnlockAndOpeningGrace(GameTestHelper h){
        var settingsBefore=IteConfig.effectiveSnapshot();boolean localSettings=settingsBefore==IteConfig.localSnapshot();
        var ratesBefore=IteSpawnConfig.effectiveSnapshot();var weightsBefore=IteSpawnConfig.effectiveWeights();
        boolean localSpawns=ratesBefore==IteSpawnConfig.localSnapshot()&&weightsBefore==IteSpawnConfig.localWeights();
        try(var site=new SpawnBalanceGameTests.Site(h)){
            {
                var settings=new java.util.HashMap<>(settingsBefore);
                settings.put("extreme_mob_spawn_limit_day",999);
                IteConfig.receiveServerSnapshot(settings);
                site.date(33,33,true);
                h.assertFalse(SpawnPopulation.automaticAllowed(site.level,ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE)),
                        "An ordinary automatic mimic still obeys the configured day 999 gate");
                site.date(1,1,false);
                h.assertTrue(ExtremeWorldState.graceActive(site.level),"The fixture is inside the real first-day grace period");
                Mob ordinary=site.mob(ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE));
                h.assertFalse(site.level.addFreshEntity(ordinary),"A mimic without the actual death-source marker cannot bypass opening grace");
                String name="EarlyDeath"+UUID.randomUUID().toString().substring(0,5);
                ServerPlayer player=new ServerPlayer(site.level.getServer(),site.level,new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
                player.setPos(site.origin);
                List<ItemEntity> incoming=new ArrayList<>(List.of(new ItemEntity(site.level,player.getX(),player.getY(),player.getZ(),new ItemStack(Items.DIAMOND,23))));
                mimicRoll(player,0);ExtremeMobEvents.drops(new LivingDropsEvent(player,site.level.damageSources().generic(),incoming,false));
                var mimics=site.level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(2),
                        m->m instanceof ExtremeMob e&&e.extremeKind()==ExtremeKind.MIMIC_ZOMBIE&&name.equals(e.extremeMemory().mimicName));
                h.assertTrue(mimics.size()==1&&incoming.isEmpty(),"A successful ten-percent death roll creates exactly one mimic on day one before day 999");
                Mob mimic=mimics.getFirst();site.mobs.add(mimic);
                h.assertTrue(SpawnPopulation.localCount(site.level,mimic.getBoundingBox().inflate(2))>=1,"A death substitute remains in population accounting");
                List<ItemEntity> recovered=new ArrayList<>();PlayerMimicInventory.release(mimic,recovered);PlayerMimicInventory.release(mimic,recovered);
                h.assertTrue(recovered.size()==1&&recovered.getFirst().getItem().getCount()==23,"The early death transfers the real drops once without duplication");
                mimic.discard();SpawnPopulation.clearReservations(site.level);
            }
        }finally{
            IteConfig.clearServerSnapshot();
            if(!localSettings)IteConfig.receiveServerSnapshot(settingsBefore);
            if(!localSpawns)IteSpawnConfig.receiveServerSnapshot(ratesBefore,weightsBefore);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void unsuccessfulMimicRollLeavesTheOriginalPlayerDropsUntouched(GameTestHelper h){
        for(int roll=1;roll<10;roll++){
            String name="DeathDrops"+UUID.randomUUID().toString().substring(0,5);
            ServerPlayer player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
            player.setPos(h.absolutePos(new BlockPos(2,2,2)).getCenter());
            ItemStack diamonds=new ItemStack(Items.DIAMOND,23);diamonds.set(DataComponents.CUSTOM_NAME,Component.literal("Unclaimed diamonds"));
            ItemStack helmet=new ItemStack(Items.IRON_HELMET);helmet.setDamageValue(37);
            helmet.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),5);
            List<ItemStack> stacks=List.of(diamonds,helmet),expected=stacks.stream().map(ItemStack::copy).toList();
            List<ItemEntity> originals=stacks.stream().map(stack->new ItemEntity(h.getLevel(),player.getX(),player.getY(),player.getZ(),stack)).toList();
            List<ItemEntity> incoming=new ArrayList<>(originals);
            LivingDropsEvent event=new LivingDropsEvent(player,h.getLevel().damageSources().generic(),incoming,false);
            mimicRoll(player,roll);ExtremeMobEvents.drops(event);
            var mimics=h.getLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(2),m->m instanceof ExtremeMob e&&e.extremeKind()==ExtremeKind.MIMIC_ZOMBIE&&name.equals(e.extremeMemory().mimicName));
            try{
                h.assertTrue(mimics.isEmpty(),"Mimic roll "+roll+" does not spawn a substitute");
                h.assertTrue(!event.isCanceled()&&event.getDrops()==incoming&&incoming.size()==originals.size(),"Mimic roll "+roll+" preserves the normal death-drop event and collection");
                for(int i=0;i<originals.size();i++)h.assertTrue(incoming.get(i)==originals.get(i)&&!incoming.get(i).isRemoved()&&incoming.get(i).getItem()==stacks.get(i)&&ItemStack.matches(expected.get(i),incoming.get(i).getItem()),"Mimic roll "+roll+" preserves original item entity, stack, count, name, wear and enchantments in drop "+i);
            }finally{mimics.forEach(Mob::discard);}
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void canceledMimicJoinLeavesTheOriginalPlayerDropsUntouched(GameTestHelper h){
        String name="DeniedMimic"+UUID.randomUUID().toString().substring(0,5);
        ServerPlayer player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
        player.setPos(h.absolutePos(new BlockPos(2,2,2)).getCenter());
        ItemStack stack=new ItemStack(Items.DIAMOND,23);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Unclaimed after denied join"));ItemStack expected=stack.copy();
        ItemEntity original=new ItemEntity(h.getLevel(),player.getX(),player.getY(),player.getZ(),stack);
        List<ItemEntity> incoming=new ArrayList<>(List.of(original));List<Mob> attempts=new ArrayList<>();
        Consumer<EntityJoinLevelEvent> deny=event->{if(event.getEntity() instanceof Mob mimic&&mimic instanceof ExtremeMob e&&e.extremeKind()==ExtremeKind.MIMIC_ZOMBIE&&name.equals(e.extremeMemory().mimicName)){attempts.add(mimic);event.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,EntityJoinLevelEvent.class,deny);
        try{
            LivingDropsEvent event=new LivingDropsEvent(player,h.getLevel().damageSources().generic(),incoming,false);
            mimicRoll(player,0);ExtremeMobEvents.drops(event);
            h.assertTrue(attempts.size()==1,"The successful probability roll attempts exactly one substitute join");
            h.assertTrue(h.getLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(2),m->m instanceof ExtremeMob e&&name.equals(e.extremeMemory().mimicName)).isEmpty(),"A canceled substitute join does not leave a substitute in the world");
            h.assertTrue(!event.isCanceled()&&incoming.size()==1&&incoming.getFirst()==original&&!original.isRemoved()&&original.getItem()==stack&&ItemStack.matches(expected,stack),"A failed substitute join preserves the normal player drop, original item entity, stack count and components");
        }finally{NeoForge.EVENT_BUS.unregister(deny);attempts.forEach(Mob::discard);}
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void mimicHealthStaysTenAcrossDaysRefreshesAndReloadsWithoutHealing(GameTestHelper h){
        var level=h.getLevel().getServer().overworld();var history=BloodMoonInvasionData.get(level);
        long time=level.getDayTime(),highest=history.highestDay();boolean dirty=history.isDirty();
        try{
            level.setDayTime(0);history.restoreProgress(1);
            Mob mimic=mob(h,ExtremeKind.MIMIC_ZOMBIE);
            h.assertTrue(mimic.getMaxHealth()==10&&mimic.getHealth()==10,"A newly created substitute starts at exactly ten health");
            mimic.setHealth(4);
            for(long day:new long[]{1,32,128,366,10_000}){
                level.setDayTime((day-1)*24_000L);history.restoreProgress(day);
                for(int refresh=0;refresh<3;refresh++){
                    MobAbilities.initialize(mimic,(ExtremeMob)mimic);MonsterDayScaling.refresh(mimic);
                    h.assertTrue(mimic.getAttributeBaseValue(Attributes.MAX_HEALTH)==10&&mimic.getMaxHealth()==10&&mimic.getHealth()==4,"Day "+day+" refresh "+refresh+" keeps ten maximum health without healing injuries");
                }
                CompoundTag saved=new CompoundTag();mimic.saveWithoutId(saved);
                Mob loaded=ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE).create(h.getLevel());loaded.load(saved);
                MobAbilities.initialize(loaded,(ExtremeMob)loaded);MonsterDayScaling.refresh(loaded);
                h.assertTrue(loaded.getMaxHealth()==10&&loaded.getHealth()==4,"A day "+day+" save reload keeps ten maximum health and the existing injury");
                mimic=loaded;
            }
        }finally{level.setDayTime(time);history.restoreProgress(highest);history.setDirty(dirty);}
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void legacyMimicHealthAndGrowthAreNormalizedWithoutHealing(GameTestHelper h){
        var level=h.getLevel().getServer().overworld();var history=BloodMoonInvasionData.get(level);
        long time=level.getDayTime(),highest=history.highestDay();boolean dirty=history.isDirty();
        try{
            level.setDayTime(365*24_000L);history.restoreProgress(366);
            for(float health:new float[]{80,4}){
                Mob legacy=mob(h,ExtremeKind.MIMIC_ZOMBIE);var attribute=legacy.getAttribute(Attributes.MAX_HEALTH);
                attribute.setBaseValue(80);attribute.addPermanentModifier(new AttributeModifier(MonsterDayScaling.HEALTH_MODIFIER,.5,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));legacy.setHealth(health);
                CompoundTag saved=new CompoundTag();legacy.saveWithoutId(saved);
                Mob loaded=ExtremeMobs.type(ExtremeKind.MIMIC_ZOMBIE).create(h.getLevel());loaded.load(saved);
                h.assertTrue(((ExtremeMob)loaded).extremeMemory().initialized&&loaded.getMaxHealth()==120,"The legacy fixture retains its initialized eighty-health baseline and old calendar modifier");
                MobAbilities.initialize(loaded,(ExtremeMob)loaded);
                h.assertTrue(loaded.getAttributeBaseValue(Attributes.MAX_HEALTH)==10&&loaded.getAttribute(Attributes.MAX_HEALTH).getModifier(MonsterDayScaling.HEALTH_MODIFIER)==null&&loaded.getMaxHealth()==10&&loaded.getHealth()==Math.min(health,10),"Loading an old substitute removes legacy health and growth while clipping excess health without healing");
                MonsterDayScaling.refresh(loaded);MobAbilities.initialize(loaded,(ExtremeMob)loaded);MonsterDayScaling.refresh(loaded);
                h.assertTrue(loaded.getMaxHealth()==10&&loaded.getHealth()==Math.min(health,10),"Repeated initialization and growth refresh preserve the migrated substitute's injury");
            }
        }finally{level.setDayTime(time);history.restoreProgress(highest);history.setDirty(dirty);}
        h.succeed();
    }

    @GameTest(template="gear_empty",timeoutTicks=40)
    public static void invisibleCreeperExplosionCannotCreateAnInfiniteInvisibilityCloud(GameTestHelper h){
        Creeper creeper=(Creeper)mob(h,ExtremeKind.INVISIBLE_CREEPER);
        // The one-block template only forces its own chunk; keep the ticking creeper inside it.
        creeper.moveTo(h.absolutePos(new BlockPos(0,2,0)).getCenter());
        CompoundTag saved=new CompoundTag();creeper.saveWithoutId(saved);saved.putShort("Fuse",(short)1);saved.putByte("ExplosionRadius",(byte)0);creeper.load(saved);
        h.getLevel().addFreshEntity(creeper);creeper.ignite();
        var bounds=creeper.getBoundingBox().inflate(3);
        h.runAfterDelay(5,()->{
            h.assertTrue(creeper.isRemoved(),"The real creeper fuse completes (entity ticks="+creeper.tickCount+", ignited="+creeper.isIgnited()+")");
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
