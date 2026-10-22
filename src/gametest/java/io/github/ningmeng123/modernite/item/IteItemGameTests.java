package io.github.ningmeng123.modernite.item;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.item.GemSacrifice;
import io.github.ningmeng123.mite.portal.MitePortalFrame;
import io.github.ningmeng123.mite.portal.MitePortals;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native item transactions, post-defense damage and successful portal publication. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class IteItemGameTests {
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private IteItemGameTests() {}

    @GameTest(template="gear_empty",batch="ite_config")
    public static void configuredGemExperienceUsesTheNativeSacrificeTransaction(GameTestHelper h) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(h)) {
            config.set("quartz_max_exp_level",1000000);
            var values=List.of(Map.entry(Items.DIAMOND,Map.entry("diamond_exp",77)),Map.entry(Items.EMERALD,Map.entry("emerald_exp",88)),
                    Map.entry(Items.QUARTZ,Map.entry("quartz_exp",99)),Map.entry(Items.LAPIS_LAZULI,Map.entry("lapis_exp",111)));
            for(var value:values) {
                awaitGemCooldown(h,session.player);
                config.set(value.getValue().getKey(),value.getValue().getValue());PlayerExperience.setTotal(session.player,0);
                var stack=new ItemStack(value.getKey(),2);session.player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var result=useGem(session.player);
                h.assertTrue(result.consumesAction()&&stack.getCount()==1&&session.player.totalExperience==value.getValue().getValue(),
                        "Real gem use consumes one item and grants the configured XP: "+value.getValue().getKey());
            }
            awaitGemCooldown(h,session.player);
            config.set("diamond_exp",0);PlayerExperience.setTotal(session.player,0);var disabled=new ItemStack(Items.DIAMOND);
            session.player.setItemInHand(InteractionHand.MAIN_HAND,disabled);useGem(session.player);
            h.assertTrue(disabled.getCount()==1&&session.player.totalExperience==0,"A zero reward disables sacrifice without consuming a gem");
        }h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void quartzLevelLimitRejectsUseWithoutChangingInventoryOrExperience(GameTestHelper h) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(h)) {
            config.set("quartz_max_exp_level",2);config.set("quartz_exp",1);
            var stack=new ItemStack(Items.QUARTZ,3);session.player.setItemInHand(InteractionHand.MAIN_HAND,stack);PlayerExperience.setLevels(session.player,1);
            useGem(session.player);
            h.assertTrue(stack.getCount()==2,"The level immediately below the configured limit still accepts quartz");
            awaitGemCooldown(h,session.player);
            PlayerExperience.setLevels(session.player,2);int before=session.player.totalExperience;
            useGem(session.player);
            h.assertTrue(stack.getCount()==2&&session.player.totalExperience==before,"At the exact level limit quartz is neither consumed nor credited");
            config.set("quartz_max_exp_level",0);PlayerExperience.setTotal(session.player,0);
            useGem(session.player);
            h.assertTrue(stack.getCount()==2&&session.player.totalExperience==0,"A zero level limit explicitly disables quartz sacrifice");
            PlayerExperience.setTotal(session.player,-50);useGem(session.player);
            h.assertTrue(stack.getCount()==2&&session.player.totalExperience==-50,"Zero also disables quartz while the player has negative experience debt");
        }h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void nativeQuartzSacrificeWorksAtFiftyFourAndExplainsTheFiftyFiveLevelBoundary(GameTestHelper helper) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(helper)) {
            config.set("quartz_max_exp_level",55);config.set("quartz_exp",35);
            var stack=new ItemStack(Items.QUARTZ,3);session.player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            PlayerExperience.setLevels(session.player,54);int before=session.player.totalExperience;
            helper.assertTrue(useGem(session.player).consumesAction()&&stack.getCount()==2
                    &&session.player.totalExperience==before+35,"A real level-fifty-four use consumes one quartz and credits the configured thirty-five points");
            helper.assertTrue(session.quartzMessages().isEmpty(),"A successful native sacrifice sends no rejection hint");
            awaitGemCooldown(helper,session.player);
            PlayerExperience.setLevels(session.player,55);before=session.player.totalExperience;
            helper.assertValueEqual(useGem(session.player),InteractionResult.PASS,"The quartz level rule leaves the native unsuccessful interaction unchanged");
            helper.assertTrue(stack.getCount()==2&&session.player.totalExperience==before,"The exact level limit neither consumes quartz nor awards experience");
            assertQuartzHint(helper,session,"message.modern_ite.quartz_sacrifice_level_limit",55);
            helper.assertFalse(IteItemRules.canSacrificeGem(session.player,stack,true),"The pure policy query still rejects the exact configured limit");
            helper.assertValueEqual(GemSacrifice.experienceValue(stack),35,"Pure reward lookup retains the configured reward independently of level eligibility");
            helper.assertTrue(session.quartzMessages().isEmpty(),"Policy and tooltip queries never publish interaction hints");
        }helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void nativeQuartzUseExplainsDisabledSettingsWithoutChangingItemsOrExperience(GameTestHelper helper) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(helper)) {
            var stack=new ItemStack(Items.QUARTZ,2);session.player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            PlayerExperience.setTotal(session.player,0);config.set("quartz_exp",35);config.set("quartz_max_exp_level",0);
            helper.assertValueEqual(useGem(session.player),InteractionResult.PASS,"Disabling the level gate does not cancel the ordinary unsuccessful item interaction");
            helper.assertTrue(stack.getCount()==2&&session.player.totalExperience==0,"A zero level limit preserves all quartz and experience");
            assertQuartzHint(helper,session,"message.modern_ite.quartz_sacrifice_disabled",null);
            config.set("quartz_max_exp_level",55);config.set("quartz_exp",0);
            helper.assertValueEqual(useGem(session.player),InteractionResult.PASS,"A zero configured reward also leaves the native result unchanged");
            helper.assertTrue(stack.getCount()==2&&session.player.totalExperience==0,"A disabled reward cannot consume quartz for no experience");
            assertQuartzHint(helper,session,"message.modern_ite.quartz_sacrifice_disabled",null);
        }helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void spectatorsAndEarlierCancelledNativeQuartzUseNeverPublishRejectionHints(GameTestHelper helper) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(helper)) {
            config.set("quartz_max_exp_level",55);config.set("quartz_exp",35);
            var stack=new ItemStack(Items.QUARTZ,2);session.player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            PlayerExperience.setLevels(session.player,55);int before=session.player.totalExperience;
            int[] intercepted={0};
            Consumer<PlayerInteractEvent.RightClickItem> cancel=event->{
                if(event.getEntity()==session.player) {
                    intercepted[0]++;event.setCancellationResult(InteractionResult.FAIL);event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,PlayerInteractEvent.RightClickItem.class,cancel);
            try {
                helper.assertValueEqual(useGem(session.player),InteractionResult.FAIL,"Earlier mod cancellation remains the native interaction result");
            }finally{NeoForge.EVENT_BUS.unregister(cancel);}
            helper.assertTrue(intercepted[0]==1&&stack.getCount()==2&&session.player.totalExperience==before
                    &&session.quartzMessages().isEmpty(),"An externally cancelled attempt neither mutates state nor misattributes failure to the level rule");
            session.player.setGameMode(GameType.SPECTATOR);
            helper.assertValueEqual(useGem(session.player),InteractionResult.PASS,"The real spectator use exits before publishing an item interaction");
            helper.assertTrue(stack.getCount()==2&&session.player.totalExperience==before&&session.quartzMessages().isEmpty(),
                    "Spectators never receive sacrifice feedback or change quartz and experience");
        }helper.succeed();
    }

    private static InteractionResult useGem(ServerPlayer player) {
        return player.gameMode.useItem(player,player.serverLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND);
    }

    private static void awaitGemCooldown(GameTestHelper helper,ServerPlayer player) {
        int ticks=0;
        while(gemCoolingDown(player)&&ticks++<20_000)player.getCooldowns().tick();
        helper.assertFalse(gemCoolingDown(player),"Native gem cooldown timers expire by normal ticking, never by removing cooldown state");
    }

    private static boolean gemCoolingDown(ServerPlayer player) {
        return player.getCooldowns().isOnCooldown(Items.QUARTZ)||player.getCooldowns().isOnCooldown(Items.DIAMOND)
                ||player.getCooldowns().isOnCooldown(Items.EMERALD)||player.getCooldowns().isOnCooldown(Items.LAPIS_LAZULI);
    }

    private static void assertQuartzHint(GameTestHelper helper,Session session,String key,Integer limit) {
        var messages=session.quartzMessages();
        helper.assertValueEqual(messages.size(),1,"Exactly one native action-bar packet explains the rejected quartz interaction");
        var message=messages.getFirst();var text=(TranslatableContents)message.content().getContents();
        helper.assertTrue(message.overlay()&&text.getKey().equals(key),"The rejection is a translated action-bar hint rather than ordinary chat");
        helper.assertTrue(limit==null?text.getArgs().length==0:text.getArgs().length==1&&limit.equals(text.getArgs()[0]),
                "The hint contains precisely the applicable configured level boundary");
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void bothGoldenAppleDurationsReachTheActualItemStackEntry(GameTestHelper h) {
        try(ConfigRestore config=new ConfigRestore()) {
            config.set("golden_apple_eat_time",13);config.set("enchanted_golden_apple_eat_time",7);
            var player=h.makeMockPlayer(GameType.SURVIVAL);
            h.assertTrue(new ItemStack(Items.GOLDEN_APPLE).getUseDuration(player)==13,"Ordinary golden apples use their configured duration");
            h.assertTrue(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).getUseDuration(player)==7,"Enchanted golden apples keep their separate configured duration");
            h.assertTrue(new ItemStack(Items.APPLE).getUseDuration(player)==32,"Other foods retain the base mod's duration");
        }h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void fullProtectionFiveReducesFinalDamageForAnyMaterialAndEitherEnchantSource(GameTestHelper h) {
        var victim=h.spawn(EntityType.ZOMBIE,new BlockPos(2,2,2));victim.setNoAi(true);victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        try(ConfigRestore config=new ConfigRestore()) {
            var protection=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
            Item[] pieces={Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS};
            for(boolean growth:new boolean[]{false,true}) {
                for(int index=0;index<ARMOR.length;index++) {
                    var armor=new ItemStack(pieces[index]);
                    if(growth)armor.set(GearProgression.PROGRESS.get(),new GearProgress(UUID.randomUUID(),5,0,
                            Map.of(ResourceLocation.withDefaultNamespace("protection"),5),1));
                    else armor.enchant(protection,5);
                    victim.setItemSlot(ARMOR[index],armor);
                }
                config.set("all_protection_v_defence_fraction",0D);float baseline=hit(victim,10);
                config.set("all_protection_v_defence_fraction",.5D);float reduced=hit(victim,10);
                h.assertTrue(baseline>0&&Math.abs(reduced-baseline*.5F)<.001F,"The configured reduction applies after native armor and protection, for ordinary or growth protection V");
            }
            victim.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            config.set("all_protection_v_defence_fraction",0D);float baseline=hit(victim,10);
            config.set("all_protection_v_defence_fraction",.5D);
            h.assertTrue(!IteItemRules.fullProtectionFive(victim)&&Math.abs(hit(victim,10)-baseline)<.001F,"An incomplete set receives no extra reduction");
        }finally{victim.discard();}h.succeed();
    }

    private static float hit(net.minecraft.world.entity.LivingEntity victim,float amount) {
        victim.setHealth(1000);victim.invulnerableTime=0;victim.hurt(victim.damageSources().magic(),amount);return 1000-victim.getHealth();
    }

    @GameTest(template="gear_empty")
    public static void restoredExperienceKeepsFractionalPointsAndCumulativeOverflowAcrossSeveralLevels(GameTestHelper h) {
        try(Session session=new Session(h)) {
            int[] grades={1,2,10};
            long[] expectedTool={525,575,1875},expectedArmor={525,575,1875};
            for(int index=0;index<grades.length;index++) {
                ItemStack tool=new ItemStack(MiteTools.get("vibranium_pickaxe")),
                        armor=new ItemStack(MiteArmorContent.get("mite:vibranium_chestplate"));
                tool.set(ExtremeGear.FORGE_GRADE.get(),grades[index]);armor.set(ExtremeGear.FORGE_GRADE.get(),grades[index]);
                GearProgression.award(tool,1000,session.player);GearProgression.award(armor,1000,session.player);
                h.assertTrue(GearProgression.state(tool).experienceMillis()==expectedTool[index]
                        &&GearProgression.state(armor).experienceMillis()==expectedArmor[index],
                        "Forging retains fractional points equally for tools and armor before one fifty-percent reduction, grade "+grades[index]);
            }
            for(GrowthCase sample:growthCases()) {
                ItemStack stack=new ItemStack(sample.item());stack.set(ExtremeGear.FORGE_GRADE.get(),10);
                int cap=GearProgression.maxLevel(stack);
                h.assertTrue(cap==sample.cap(),"The retained forging cap is independent of the new threshold: "+sample.name());
                GearProgression.award(stack,100_000L,session.player);
                h.assertTrue(GearProgression.state(stack).level()==sample.overflowLevel()
                                &&GearProgression.state(stack).experienceMillis()==sample.overflowRemainder(),
                        "One grade-ten 100-point award becomes 187.5 points and uses the category's unchanged thresholds once: "+sample.name());
                GearProgression.award(stack,Long.MAX_VALUE,session.player);
                h.assertTrue(GearProgression.state(stack).level()==cap
                                &&GearProgression.state(stack).experienceMillis()==0,
                        "A single large award reaches the retained forging cap and clears only max-level surplus");
                var before=GearProgression.state(stack);
                GearProgression.award(stack,Long.MAX_VALUE,session.player);
                h.assertTrue(GearProgression.state(stack).equals(before),"Full equipment ignores further growth awards");
                ItemStack loaded=ItemStack.parse(h.getLevel().registryAccess(),stack.save(h.getLevel().registryAccess())).orElseThrow();
                h.assertTrue(GearProgression.state(loaded).equals(before)&&GearProgression.maxLevel(loaded)==cap,
                        "Completed growth and forge caps survive actual item serialization");
            }
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void categoryThresholdsUseBaseCurvesAndApplyForgingOnceAtEveryLevel(GameTestHelper h) {
        try(Session session=new Session(h)) {
            for(GrowthCase sample:growthCases()) {
                ItemStack stack=new ItemStack(sample.item()),unforged=new ItemStack(sample.item());
                stack.set(ExtremeGear.FORGE_GRADE.get(),10);
                h.assertTrue(GearProgression.maxLevel(stack)==sample.cap(),"The real grade-ten cap is unchanged: "+sample.name());
                for(int level=0;level<sample.cap();level++) {
                    long threshold=(sample.first()+sample.increment()*(long)level)*1000L;
                    h.assertTrue(GearProgression.nextLevelExperienceMillis(stack,level)==threshold
                                    &&GearProgression.nextLevelExperienceMillis(unforged,level)==threshold,
                            "ITE follows the independently expected base category curve, without a material or forging threshold multiplier: "+sample.name()+" L"+level);
                    GearProgress before=GearProgression.state(stack);
                    long deficit=threshold-before.experienceMillis();
                    long rawMillis=(deficit*8L+14L)/15L;
                    long creditedMillis=rawMillis*15L/8L;
                    GearProgression.award(stack,rawMillis,session.player);
                    GearProgress after=GearProgression.state(stack);
                    long remainder=level+1==sample.cap()?0L:before.experienceMillis()+creditedMillis-threshold;
                    h.assertTrue(after.level()==level+1&&after.experienceMillis()==remainder,
                            "The actual award applies forging and the fifty-percent reduction once, deducts one reached threshold and retains exact thousandths: "+sample.name()+" L"+level);
                }
            }
        }h.succeed();
    }

    private record GrowthCase(String name,Item item,int first,int increment,int cap,int overflowLevel,long overflowRemainder) {}
    private static List<GrowthCase> growthCases() {
        return List.of(
                new GrowthCase("tool",MiteTools.get("vibranium_pickaxe"),100,50,25,1,87_500L),
                new GrowthCase("shield",MiteShields.get("vibranium"),100,50,25,1,87_500L),
                new GrowthCase("weapon",MiteTools.get("vibranium_sword"),150,100,25,1,37_500L),
                new GrowthCase("helmet",MiteArmorContent.get("mite:vibranium_helmet"),40,20,28,3,7_500L),
                new GrowthCase("chestplate",MiteArmorContent.get("mite:vibranium_chestplate"),80,40,28,1,107_500L),
                new GrowthCase("leggings",MiteArmorContent.get("mite:vibranium_leggings"),60,30,28,2,37_500L),
                new GrowthCase("boots",MiteArmorContent.get("mite:vibranium_boots"),20,10,28,4,47_500L));
    }

    @GameTest(template="gear_empty")
    public static void realArmorHitDistributesThousandthsBeforeEachForgeMultiplier(GameTestHelper h) {
        try(Session session=new Session(h)) {
            prepareArmorExperiencePlayer(session.player);
            ItemStack[] armor={new ItemStack(MiteArmorContent.get("mite:vibranium_helmet")),
                    new ItemStack(MiteArmorContent.get("mite:vibranium_chestplate")),
                    new ItemStack(MiteArmorContent.get("mite:vibranium_leggings"))};
            int[] grades={0,1,10};
            long[] expected={167L,174L,624L};
            for(int index=0;index<armor.length;index++) {
                armor[index].set(ExtremeGear.FORGE_GRADE.get(),grades[index]);
                session.player.setItemSlot(ARMOR[index],armor[index]);
            }
            session.player.tick();
            actualArmorExperienceHit(h,session.player,1F);
            for(int index=0;index<armor.length;index++) {
                GearProgress result=GearProgression.state(armor[index]);
                h.assertTrue(result.level()==0&&result.experienceMillis()==expected[index],
                        "One real damage point splits 334/333/333 stored units before grades 0/1/10 and one reduction yield 167/174/624: "+ARMOR[index]);
            }
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void realArmorHitAppliesGradeTenOnceAcrossFourPieces(GameTestHelper h) {
        try(Session session=new Session(h)) {
            prepareArmorExperiencePlayer(session.player);
            ItemStack[] armor={new ItemStack(MiteArmorContent.get("mite:vibranium_helmet")),
                    new ItemStack(MiteArmorContent.get("mite:vibranium_chestplate")),
                    new ItemStack(MiteArmorContent.get("mite:vibranium_leggings")),
                    new ItemStack(MiteArmorContent.get("mite:vibranium_boots"))};
            for(int index=0;index<armor.length;index++) {
                armor[index].set(ExtremeGear.FORGE_GRADE.get(),10);
                session.player.setItemSlot(ARMOR[index],armor[index]);
            }
            session.player.tick();
            actualArmorExperienceHit(h,session.player,4F);
            for(int index=0;index<armor.length;index++) {
                GearProgress result=GearProgression.state(armor[index]);
                h.assertTrue(result.level()==0&&result.experienceMillis()==1875L,
                        "Four real damage points yield one raw point per piece before exactly one grade-ten multiplier and one fifty-percent reduction: "+ARMOR[index]);
            }
        }h.succeed();
    }

    private static void prepareArmorExperiencePlayer(ServerPlayer player) {
        for(int tick=0;tick<60;tick++)player.tick();
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.setNoGravity(true);player.setOnGround(true);
    }

    private static void actualArmorExperienceHit(GameTestHelper h,ServerPlayer player,float damage) {
        var attacker=EntityType.COW.create(h.getLevel());
        attacker.setNoAi(true);attacker.setNoGravity(true);attacker.setPos(player.position().add(1D,0D,0D));
        player.setHealth(player.getMaxHealth());player.invulnerableTime=0;player.hurtTime=0;
        h.assertTrue(player.getMainHandItem().isEmpty()&&player.getOffhandItem().isEmpty()&&!player.isBlocking(),
                "The real armor-training hit has no held shield or active block");
        float health=player.getHealth();
        List<Float> incoming=new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> observer=event->{
            if(event.getEntity()==player&&event.getSource().getEntity()==attacker)incoming.add(event.getAmount());
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LivingIncomingDamageEvent.class,observer);
        try {
            h.assertTrue(player.hurt(player.damageSources().mobAttack(attacker),damage),
                    "The native entity-owned armor-training hit is accepted");
        }finally{NeoForge.EVENT_BUS.unregister(observer);attacker.discard();}
        h.assertTrue(incoming.size()==1&&incoming.getFirst()==damage,
                "The native incoming event sees exactly the requested damage once: "+incoming);
        h.assertTrue(player.getHealth()<health,"The accepted native hit really loses health after armor mitigation");
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void portalWarningIsSentOnlyAfterSuccessfulNativeTravelAndHonorsItsSwitch(GameTestHelper h) {
        var level=h.getLevel();BlockPos oldSpawn=level.getSharedSpawnPos();float oldAngle=level.getSharedSpawnAngle();
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(h)) {
            var frame=new MitePortalFrame(h.absolutePos(new BlockPos(1,2,1)),Direction.Axis.X);
            for(int x=-1;x<=2;x++)for(int y=-1;y<=3;y++)level.setBlock(frame.at(x,y),
                    x==-1||x==2||y==-1||y==3?Blocks.OBSIDIAN.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level,frame.bottomLeft(),frame.axis()).orElseThrow().createPortalBlocks();
            MitePortals.writeRoute(level,frame,0,false);
            BlockPos landing=h.absolutePos(new BlockPos(3,2,3));level.setBlockAndUpdate(landing.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(landing,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(landing.above(),Blocks.AIR.defaultBlockState());
            level.setDefaultSpawnPos(landing,0);session.player.setPos(Vec3.atBottomCenterOf(frame.bottomLeft()));session.drain();
            config.set("log_players_interact_with_portal",true);
            h.assertTrue(frame.complete(level)&&MitePortalFrame.find(level,frame.bottomLeft()).isPresent(),"The fixture contains a complete portal created through the native frame transaction");
            h.assertTrue(MitePortals.canEnter(level,frame.bottomLeft(),session.player),"The living server player can enter the constructed portal");
            var destination=MitePortals.destination(level,session.player,frame.bottomLeft());
            h.assertTrue(destination!=null,"The complete spawn-return portal finds its prepared dry landing");
            h.assertTrue(session.drain().isEmpty(),"A destination lookup prepares travel without publishing a warning");
            session.player.changeDimension(destination);
            h.assertTrue(session.drain().size()==1,"One successful native portal transition publishes one warning");
            config.set("log_players_interact_with_portal",false);
            destination=MitePortals.destination(level,session.player,frame.bottomLeft());h.assertTrue(destination!=null,"The portal remains usable with logging disabled");
            session.player.changeDimension(destination);h.assertTrue(session.drain().isEmpty(),"Turning logging off suppresses the actual packet");
            level.removeBlock(frame.at(-1,1),false);
            h.assertTrue(MitePortals.destination(level,session.player,frame.bottomLeft())==null&&session.drain().isEmpty(),"An invalid portal produces neither a travel transaction nor a warning");
        }finally{level.setDefaultSpawnPos(oldSpawn,oldAngle);}h.succeed();
    }

    private static final class ConfigRestore implements AutoCloseable {
        private final Map<String,Object> previous=new LinkedHashMap<>();
        void set(String key,Object value){previous.computeIfAbsent(key,ignored->IteConfig.setting(key).get());IteConfig.setting(key).set(value);IteConfig.refresh();}
        @Override public void close(){previous.forEach((key,value)->IteConfig.setting(key).set(value));IteConfig.refresh();}
    }
    private static final class Session implements AutoCloseable {
        final ServerPlayer player;final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ite-items"),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,3))));player.setNoGravity(true);player.setOnGround(true);
            player.getInventory().clearContent();player.removeAllEffects();drain();
        }
        List<ClientboundSystemChatPacket> drain() {
            return drain("message.modern_ite.portal_used");
        }
        List<ClientboundSystemChatPacket> quartzMessages() {
            return drain("message.modern_ite.quartz_sacrifice_");
        }
        private List<ClientboundSystemChatPacket> drain(String prefix) {
            channel.runPendingTasks();channel.flushOutbound();var messages=new ArrayList<ClientboundSystemChatPacket>();Object packet;
            while((packet=channel.readOutbound())!=null)if(packet instanceof ClientboundSystemChatPacket chat
                    &&chat.content().getContents() instanceof TranslatableContents text&&text.getKey().startsWith(prefix))messages.add(chat);
            return messages;
        }
        @Override public void close(){player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
    }
}
