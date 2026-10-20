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
import io.github.ningmeng123.mite.registry.MiteTools;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.neoforged.neoforge.common.NeoForge;
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
                config.set(value.getValue().getKey(),value.getValue().getValue());PlayerExperience.setTotal(session.player,0);
                var stack=new ItemStack(value.getKey(),2);session.player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var event=NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(session.player,InteractionHand.MAIN_HAND));
                h.assertTrue(event.isCanceled()&&stack.getCount()==1&&session.player.totalExperience==value.getValue().getValue(),
                        "Real gem use consumes one item and grants the configured XP: "+value.getValue().getKey());
            }
            config.set("diamond_exp",0);PlayerExperience.setTotal(session.player,0);var disabled=new ItemStack(Items.DIAMOND);
            session.player.setItemInHand(InteractionHand.MAIN_HAND,disabled);GemSacrifice.onUse(new PlayerInteractEvent.RightClickItem(session.player,InteractionHand.MAIN_HAND));
            h.assertTrue(disabled.getCount()==1&&session.player.totalExperience==0,"A zero reward disables sacrifice without consuming a gem");
        }h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void quartzLevelLimitRejectsUseWithoutChangingInventoryOrExperience(GameTestHelper h) {
        try(ConfigRestore config=new ConfigRestore();Session session=new Session(h)) {
            config.set("quartz_max_exp_level",2);config.set("quartz_exp",1);
            var stack=new ItemStack(Items.QUARTZ,3);session.player.setItemInHand(InteractionHand.MAIN_HAND,stack);PlayerExperience.setLevels(session.player,1);
            GemSacrifice.onUse(new PlayerInteractEvent.RightClickItem(session.player,InteractionHand.MAIN_HAND));
            h.assertTrue(stack.getCount()==2,"The level immediately below the configured limit still accepts quartz");
            PlayerExperience.setLevels(session.player,2);int before=session.player.totalExperience;
            GemSacrifice.onUse(new PlayerInteractEvent.RightClickItem(session.player,InteractionHand.MAIN_HAND));
            h.assertTrue(stack.getCount()==2&&session.player.totalExperience==before,"At the exact level limit quartz is neither consumed nor credited");
            config.set("quartz_max_exp_level",0);PlayerExperience.setTotal(session.player,0);
            GemSacrifice.onUse(new PlayerInteractEvent.RightClickItem(session.player,InteractionHand.MAIN_HAND));
            h.assertTrue(stack.getCount()==2&&session.player.totalExperience==0,"A zero level limit explicitly disables quartz sacrifice");
            PlayerExperience.setTotal(session.player,-50);GemSacrifice.onUse(new PlayerInteractEvent.RightClickItem(session.player,InteractionHand.MAIN_HAND));
            h.assertTrue(stack.getCount()==2&&session.player.totalExperience==-50,"Zero also disables quartz while the player has negative experience debt");
        }h.succeed();
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
    public static void sourceExperienceTruncatesBeforeArmorDoublingAndEachAwardAdvancesAtMostOneLevel(GameTestHelper h) {
        try(Session session=new Session(h)) {
            int[] grades={1,2,10};
            long[] expectedTool={1000,1000,3000},expectedArmor={2000,2000,6000};
            for(int index=0;index<grades.length;index++) {
                ItemStack tool=new ItemStack(Items.IRON_SWORD),armor=new ItemStack(Items.IRON_CHESTPLATE);
                tool.set(ExtremeGear.FORGE_GRADE.get(),grades[index]);armor.set(ExtremeGear.FORGE_GRADE.get(),grades[index]);
                GearProgression.award(tool,1000,session.player);GearProgression.award(armor,1000,session.player);
                h.assertTrue(GearProgression.state(tool).experienceMillis()==expectedTool[index]
                        &&GearProgression.state(armor).experienceMillis()==expectedArmor[index],
                        "The real award service truncates forging's whole points before armor doubles them, grade "+grades[index]);
            }
            ItemStack[] equipment={new ItemStack(MiteTools.get("vibranium_sword")),
                    new ItemStack(MiteArmorContent.get("mite:vibranium_chestplate"))};
            int[] expectedCaps={25,28};
            for(int index=0;index<equipment.length;index++) {
                ItemStack stack=equipment[index];stack.set(ExtremeGear.FORGE_GRADE.get(),10);
                int cap=GearProgression.maxLevel(stack);
                h.assertTrue(cap==expectedCaps[index],"Source equipment cap remains independent of experience thresholds");
                while(GearProgression.state(stack).level()<cap) {
                    int before=GearProgression.state(stack).level();
                    long threshold=GearProgression.nextLevelExperienceMillis(stack,before);
                    long divisor=index==1?2000L:1000L;
                    long input=(threshold+divisor-1)/divisor*1000L;
                    // Grade ten deliberately overshoots the threshold; the same event must not buy several levels.
                    GearProgression.award(stack,input,session.player);
                    h.assertTrue(GearProgression.state(stack).level()==before+1
                            &&GearProgression.state(stack).experienceMillis()==0,
                            "Every threshold-sized action advances exactly one level and discards its surplus");
                }
                var before=GearProgression.state(stack);
                GearProgression.award(stack,Long.MAX_VALUE,session.player);
                h.assertTrue(GearProgression.state(stack).equals(before),"Full equipment ignores further growth awards");
                ItemStack loaded=ItemStack.parse(h.getLevel().registryAccess(),stack.save(h.getLevel().registryAccess())).orElseThrow();
                h.assertTrue(GearProgression.state(loaded).equals(before)&&GearProgression.maxLevel(loaded)==cap,
                        "Completed growth and forge caps survive actual item serialization");
            }
        }h.succeed();
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
            channel.runPendingTasks();channel.flushOutbound();var messages=new ArrayList<ClientboundSystemChatPacket>();Object packet;
            while((packet=channel.readOutbound())!=null)if(packet instanceof ClientboundSystemChatPacket chat
                    &&chat.content().getContents() instanceof TranslatableContents text&&text.getKey().equals("message.modern_ite.portal_used"))messages.add(chat);
            return messages;
        }
        @Override public void close(){player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
    }
}
