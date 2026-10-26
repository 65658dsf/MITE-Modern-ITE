package io.github.ningmeng123.modernite.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real server-player death through an actual infernal blast, including inventory escrow. @author NingMeng123 */
@GameTestHolder("modern_ite_feedback")
@PrefixGameTestTemplate(false)
public final class InfernalPlayerDeathGameTests {
    private InfernalPlayerDeathGameTests() {}

    @GameTest(template="gear_empty",batch="infernal_player_drops",timeoutTicks=1200)
    public static void infernalPlayerDeathDropsTheActualInventoryWhenMimicRollFails(GameTestHelper h) {
        run(h,1,false,false);
    }

    @GameTest(template="gear_empty",batch="infernal_player_mimic",timeoutTicks=1200)
    public static void infernalPlayerDeathCanTransferTheInventoryToOnePersistentMimic(GameTestHelper h) {
        run(h,0,false,true);
    }

    @GameTest(template="gear_empty",batch="infernal_player_kept_inventory",timeoutTicks=1200)
    public static void infernalPlayerDeathRespectsKeepInventoryWithoutCopyingItToTheMimic(GameTestHelper h) {
        run(h,0,true,true);
    }

    private static void run(GameTestHelper h,int roll,boolean keepInventory,boolean expectMimic) {
        var site=new SpawnBalanceGameTests.Site(h);site.date(256,256,true);
        var level=site.level;var rules=level.getGameRules();
        boolean oldKeep=rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
        boolean oldGrief=rules.getBoolean(GameRules.RULE_MOBGRIEFING);
        var bounds=new AABB(site.origin,site.origin).inflate(44);
        Runnable releaseChunks=InfernalExplosionGameTestSupport.holdChunks(level,bounds);
        var items=new ArrayList<ItemEntity>();var mimics=new ArrayList<Mob>();
        var deaths=new ArrayList<LivingDropsEvent>();
        ServerPlayer player=site.player(1);
        player.getInventory().clearContent();player.removeAllEffects();player.setHealth(1);
        try {
            var spawnProtection=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);spawnProtection.setInt(player,0);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        ItemStack diamonds=new ItemStack(Items.DIAMOND,23);
        diamonds.set(DataComponents.CUSTOM_NAME,Component.literal("Infernal player diamonds"));
        ItemStack helmet=new ItemStack(Items.IRON_HELMET);helmet.setDamageValue(37);
        helmet.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),5);
        ItemStack apples=new ItemStack(Items.GOLDEN_APPLE,3);
        ItemStack vanishing=new ItemStack(Items.IRON_SWORD);
        vanishing.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.VANISHING_CURSE),1);
        player.getInventory().setItem(0,diamonds.copy());player.getInventory().setItem(36,helmet.copy());
        player.getInventory().setItem(40,apples.copy());player.getInventory().setItem(1,vanishing.copy());
        // This regression isolates the death transaction; native damage may normally wear armour first.
        player.getAbilities().invulnerable=false;player.invulnerableTime=0;
        Creeper source=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
        BlockPos stone=BlockPos.containing(site.origin).below();var oldStone=level.getBlockState(stone);
        Consumer<EntityJoinLevelEvent> joins=event->{
            if(event.getLevel()!=level||event.isCanceled()||!bounds.contains(event.getEntity().position()))return;
            if(event.getEntity() instanceof ItemEntity item)items.add(item);
            if(event.getEntity() instanceof Mob mob&&mob instanceof ExtremeMob state&&state.extremeKind()==ExtremeKind.MIMIC_ZOMBIE)mimics.add(mob);
        };
        Consumer<LivingDropsEvent> death=event->{
            if(event.getEntity()!=player)return;
            h.assertTrue(InfernalExplosionLoot.isDirectKill(event.getSource()),"The real player death originates inside the dedicated infernal blast");
            deaths.add(event);forceRoll(player,roll);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityJoinLevelEvent.class,joins);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingDropsEvent.class,death);
        Runnable cleanup=()->{
            NeoForge.EVENT_BUS.unregister(joins);NeoForge.EVENT_BUS.unregister(death);
            items.forEach(Entity::discard);mimics.forEach(Entity::discard);
            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(oldKeep,level.getServer());
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(oldGrief,level.getServer());
            level.setBlock(stone,oldStone,2);site.close();releaseChunks.run();
        };
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(keepInventory,level.getServer());
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
        level.setBlock(stone,Blocks.STONE.defaultBlockState(),2);
        h.runAfterDelay(5,()->{
            try {
                level.explode(source,source.getX(),source.getY(),source.getZ(),3F,Level.ExplosionInteraction.MOB);
                h.assertTrue(!player.isAlive()&&deaths.size()==1,"An actual infernal detonation kills the server player and runs the native death-drop transaction once");
                h.assertTrue(mimics.size()==(expectMimic?1:0),"The five-percent death roll also applies to infernal player deaths");
                var expected=List.of(diamonds,player.getInventory().getItem(36).isEmpty()?helmet:player.getInventory().getItem(36).copy(),apples);
                InfernalExplosionGameTestSupport.finishAfterJobs(h,level,()->{
                    h.assertTrue(level.getBlockState(stone).isAir(),"Queued terrain destruction still completes normally");
                    if(keepInventory){
                        h.assertTrue(player.getInventory().getItem(0).getCount()==23&&player.getInventory().getItem(40).getCount()==3&&!player.getInventory().getItem(1).isEmpty(),"keepInventory retains main-hand, armour, offhand and cursed equipment");
                        h.assertTrue(items.isEmpty(),"Kept inventory is not duplicated on the ground");
                    }else{
                        h.assertTrue(player.getInventory().isEmpty(),"The actual dropped inventory is removed from the dead player");
                    }
                    if(expectMimic){
                        Mob mimic=mimics.getFirst();h.assertTrue(mimic.isAlive()&&mimic.isPersistenceRequired(),"A newly spawned inventory holder survives the ongoing explosion");
                        List<ItemEntity> recovered=new ArrayList<>();PlayerMimicInventory.release(mimic,recovered);
                        if(keepInventory)h.assertTrue(recovered.isEmpty(),"An empty death-drop event never copies the player's kept inventory");
                        else assertInventory(h,recovered,expected);
                        PlayerMimicInventory.release(mimic,recovered);
                        h.assertTrue(recovered.size()==(keepInventory?0:3)&&items.isEmpty(),"Escrow is released only once and never duplicated on the ground");
                    }else assertInventory(h,items,expected);
                },cleanup);
            }catch(Throwable failure){cleanup.run();throw failure;}
        });
    }

    private static void assertInventory(GameTestHelper h,List<ItemEntity> drops,List<ItemStack> expected) {
        h.assertTrue(drops.size()==expected.size(),"Exactly the three non-vanishing death inventory stacks are recovered: "+drops);
        h.assertTrue(drops.stream().noneMatch(Entity::isRemoved),"The recovered inventory entities survive the blast and queued terrain destruction");
        for(ItemStack stack:expected){
            int count=drops.stream().filter(item->ItemStack.isSameItemSameComponents(stack,item.getItem())).mapToInt(item->item.getItem().getCount()).sum();
            // Damage during the fatal hit may change only armour wear; all components stay on the native stack.
            if(stack.is(Items.IRON_HELMET))count=drops.stream().filter(item->item.getItem().is(Items.IRON_HELMET)
                    &&item.getItem().getEnchantments().equals(stack.getEnchantments())&&item.getItem().getDamageValue()>=37).mapToInt(item->item.getItem().getCount()).sum();
            h.assertTrue(count==stack.getCount(),"Fatal-blast inventory count and components survive: "+stack);
        }
        h.assertTrue(drops.stream().noneMatch(item->item.getItem().is(Items.IRON_SWORD)),"Vanishing curse retains its normal player death semantics");
    }

    private static void forceRoll(ServerPlayer player,int roll) {
        for(long seed=0;seed<10_000;seed++){
            player.getRandom().setSeed(seed);
            if(player.getRandom().nextInt(20)==roll){player.getRandom().setSeed(seed);return;}
        }
        throw new IllegalStateException("No deterministic five-percent roll seed");
    }
}
