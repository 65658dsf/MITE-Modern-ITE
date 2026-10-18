package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native removal, persisted containers and transactional cross-world resurrection. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class ContentSafetyGameTests {
    private ContentSafetyGameTests() {}

    @GameTest(template="gear_empty")
    public static void aBlockedArrayKeepsItsAnchorAndExperience(GameTestHelper h) {
        try(Session session=new Session(h)) {
            ServerPlayer player=session.player;BlockPos array=h.absolutePos(new BlockPos(4,4,4));
            for(BlockPos pos:BlockPos.betweenClosed(array.offset(-2,-2,-2),array.offset(2,4,2)))h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(array,ExtremeContent.RESURRECTION.get().defaultBlockState());bind(player,h.getLevel(),array);
            PlayerExperience.setTotal(player,5000);player.setHealth(0);
            var death=new LivingDeathEvent(player,player.damageSources().generic());ExtremeLifeEvents.resurrect(death);
            h.assertTrue(!death.isCanceled()&&player.getHealth()==0,"An obstructed destination cannot grant a rescue");
            h.assertTrue(h.getLevel().getBlockState(array).is(ExtremeContent.RESURRECTION.get())
                    &&player.getPersistentData().contains(ExtremeLifeEvents.ANCHOR)&&player.totalExperience==5000,
                    "A failed rescue consumes neither the array, anchor nor XP");
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void aVetoedCrossWorldRescueCanRetryAndOnlyThenConsumesTheArray(GameTestHelper h) {
        ServerLevel target=h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(target!=null,"The native Nether is available");
        BlockPos array=new BlockPos(32,252,32);
        try(Session session=new Session(h);WorldPatch patch=new WorldPatch(target,array)) {
            ServerPlayer player=session.player;patch.clearAndSupport();target.setBlockAndUpdate(array,ExtremeContent.RESURRECTION.get().defaultBlockState());
            bind(player,target,array);PlayerExperience.setTotal(player,4321);player.setHealth(0);
            Consumer<EntityTravelToDimensionEvent> deny=event->{if(event.getEntity()==player)event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTravelToDimensionEvent.class,deny);
            try {
                var vetoed=new LivingDeathEvent(player,player.damageSources().generic());ExtremeLifeEvents.resurrect(vetoed);
                h.assertTrue(!vetoed.isCanceled()&&player.serverLevel()==h.getLevel()&&player.getHealth()==0,
                        "A native travel veto leaves the player in the original world and the death uncanceled");
                h.assertTrue(player.totalExperience==4321&&target.getBlockState(array).is(ExtremeContent.RESURRECTION.get())
                        &&player.getPersistentData().contains(ExtremeLifeEvents.ANCHOR),"Travel failure keeps every consumable for a retry");
            }finally{NeoForge.EVENT_BUS.unregister(deny);}
            var rescued=new LivingDeathEvent(player,player.damageSources().generic());ExtremeLifeEvents.resurrect(rescued);
            h.assertTrue(rescued.isCanceled()&&player.serverLevel()==target&&player.getHealth()>0,"The same saved anchor supports an actual cross-world rescue");
            h.assertTrue(player.totalExperience==0&&!player.getPersistentData().contains(ExtremeLifeEvents.ANCHOR)
                    &&target.getBlockState(array).isAir(),"Only a successful rescue consumes the array and all XP");
            h.assertTrue(target.noCollision(player)&&player.fallDistance==0,"The destination fits the player and clears previous falling damage");
            var repeated=new LivingDeathEvent(player,player.damageSources().generic());ExtremeLifeEvents.resurrect(repeated);
            h.assertTrue(!repeated.isCanceled(),"One array cannot rescue twice");
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void aRealDeathEventUsesTheArrayBeforeAnyDeathMimicIsCreated(GameTestHelper h) {
        try(Session session=new Session(h)) {
            ServerPlayer player=session.player;ServerLevel level=h.getLevel();BlockPos array=h.absolutePos(new BlockPos(1,2,1));
            level.setBlockAndUpdate(array.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(array,ExtremeContent.RESURRECTION.get().defaultBlockState());
            level.setBlockAndUpdate(array.above(),Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(array.above(2),Blocks.AIR.defaultBlockState());
            bind(player,level,array);PlayerExperience.setTotal(player,2000);player.setHealth(0);
            var deathArea=player.getBoundingBox().inflate(8);var mimic=io.github.ningmeng123.modernite.entity.ExtremeMobs.type(io.github.ningmeng123.modernite.entity.ExtremeKind.MIMIC_ZOMBIE);
            int before=level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,deathArea,entity->entity.getType()==mimic).size();
            var death=new LivingDeathEvent(player,player.damageSources().generic());NeoForge.EVENT_BUS.post(death);
            h.assertTrue(death.isCanceled()&&player.getHealth()>0&&!player.getPersistentData().contains(ExtremeLifeEvents.ANCHOR)
                    &&level.getBlockState(array).isAir(),"The real event bus completes and consumes the array rescue");
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,deathArea,entity->entity.getType()==mimic).size()==before,
                    "A rescued player does not also create a death mimic at the original death position");
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void removingAnOldArrayElsewhereDoesNotPreventBindingANewOne(GameTestHelper h) {
        try(Session session=new Session(h)) {
            ServerPlayer player=session.player;BlockPos old=h.absolutePos(new BlockPos(1,2,1)),next=h.absolutePos(new BlockPos(3,2,1));
            h.getLevel().setBlockAndUpdate(old,ExtremeContent.RESURRECTION.get().defaultBlockState());bind(player,h.getLevel(),old);
            h.getLevel().removeBlock(old,false);h.getLevel().setBlockAndUpdate(next,ExtremeContent.RESURRECTION.get().defaultBlockState());
            var state=h.getLevel().getBlockState(next);ExtremeContent.RESURRECTION.get().setPlacedBy(h.getLevel(),next,state,player,new ItemStack(ExtremeContent.RESURRECTION.get()));
            h.assertTrue(ExtremeLifeEvents.resolveAnchor(player).orElseThrow().position().equals(next),"An explosion or another player leaving a stale anchor cannot lock future placement");
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void aCanceledNativeBlockBreakDoesNotUnbindTheArray(GameTestHelper h) {
        try(Session session=new Session(h)) {
            ServerPlayer player=session.player;BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
            player.setGameMode(GameType.CREATIVE);h.getLevel().setBlockAndUpdate(pos,ExtremeContent.RESURRECTION.get().defaultBlockState());bind(player,h.getLevel(),pos);
            Consumer<BlockEvent.BreakEvent> deny=event->{if(event.getPlayer()==player)event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,BlockEvent.BreakEvent.class,deny);
            try {
                h.assertTrue(!player.gameMode.destroyBlock(pos),"The native break observes a late veto");
                h.assertTrue(player.getPersistentData().contains(ExtremeLifeEvents.ANCHOR)&&h.getLevel().getBlockState(pos).is(ExtremeContent.RESURRECTION.get()),
                        "A canceled pre-event does not commit removal side effects");
            }finally{NeoForge.EVENT_BUS.unregister(deny);}
            h.assertTrue(player.gameMode.destroyBlock(pos)&&!player.getPersistentData().contains(ExtremeLifeEvents.ANCHOR),
                    "The actual native removal unbinds exactly once");
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void thePrivateChestRoundTripsContentsAndDropsThemOnlyOnce(GameTestHelper h) {
        try(Session session=new Session(h)) {
            ServerPlayer player=session.player;BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
            h.getLevel().setBlockAndUpdate(pos,ExtremeContent.VIBRANIUM_CHEST.get().defaultBlockState());
            var chest=(VibraniumChestEntity)h.getLevel().getBlockEntity(pos);chest.setOwner(player);
            chest.setItem(0,new ItemStack(Items.DIAMOND,7));chest.setItem(1,new ItemStack(Items.GOLD_INGOT,3));
            var saved=chest.saveWithoutMetadata(h.getLevel().registryAccess());chest.clearContent();chest.loadWithComponents(saved,h.getLevel().registryAccess());
            h.assertTrue(chest.isOwner(player)&&chest.canOpen(player)&&!chest.canOpen(h.makeMockPlayer(GameType.SURVIVAL)),"Saved UUID ownership protects the native menu");
            h.assertTrue(chest.getItem(0).getCount()==7&&chest.getItem(1).getCount()==3,"Both saved inventory stacks are restored");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_AXE));
            h.assertTrue(player.gameMode.destroyBlock(pos),"The actual player break removes the chest");
            h.assertTrue(dropped(h.getLevel(),pos,Items.DIAMOND)==7&&dropped(h.getLevel(),pos,Items.GOLD_INGOT)==3
                    &&dropped(h.getLevel(),pos,ExtremeContent.VIBRANIUM_CHEST.get().asItem())==1,
                    "Native removal and the block loot table return one chest and one copy of every stored item");
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void theVibraniumFurnaceUsesTheSharedHeatControllerAndKeepsItsInventory(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(2,2,2));ServerLevel level=h.getLevel();
        level.setBlockAndUpdate(pos,ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState());
        var furnace=(VibraniumFurnaceEntity)level.getBlockEntity(pos);
        h.assertTrue(FurnaceThermalController.profile(furnace.getBlockState()).maxHeat()==5,"The native subclass receives the addon heat-five profile");
        furnace.setItem(0,new ItemStack(Items.RAW_IRON));furnace.setItem(1,new ItemStack(ExtremeContent.BLAZE_COAL.get(),2));
        for(int tick=0;tick<20;tick++)AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
        var saved=furnace.saveWithoutMetadata(level.registryAccess());furnace.clearContent();furnace.loadWithComponents(saved,level.registryAccess());
        for(int tick=0;tick<2000&&!furnace.getItem(0).isEmpty();tick++)AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
        h.assertTrue(furnace.getItem(0).isEmpty()&&!furnace.getItem(2).isEmpty(),"The restored real furnace completes an actual source smelting recipe");
        h.assertTrue(furnace.getItem(1).getCount()==1,"The same burning fuel survives save/load without paying twice");
        var result=furnace.getItem(2).copy();level.removeBlock(pos,false);
        h.assertTrue(dropped(level,pos,result.getItem())==result.getCount()&&dropped(level,pos,ExtremeContent.BLAZE_COAL.get())==1,
                "Breaking the subclass drops one copy of output and unused fuel");
        h.succeed();
    }

    private static int dropped(ServerLevel level,BlockPos pos,Item item) {
        return level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).stream().filter(entity->entity.getItem().is(item)).mapToInt(entity->entity.getItem().getCount()).sum();
    }
    private static void bind(ServerPlayer player,ServerLevel level,BlockPos pos) {
        var tag=new CompoundTag();tag.putString("dimension",level.dimension().location().toString());tag.putLong("position",pos.asLong());player.getPersistentData().put(ExtremeLifeEvents.ANCHOR,tag);
    }
    private static final class WorldPatch implements AutoCloseable {
        final ServerLevel level;final BlockPos array;final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
        WorldPatch(ServerLevel level,BlockPos array){this.level=level;this.array=array;for(BlockPos pos:BlockPos.betweenClosed(array.offset(-2,-1,-2),array.offset(2,3,2)))previous.put(pos.immutable(),level.getBlockState(pos));}
        void clearAndSupport(){for(BlockPos pos:previous.keySet())level.setBlockAndUpdate(pos,pos.getY()==array.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());}
        @Override public void close(){previous.forEach(level::setBlockAndUpdate);}
    }
    private static final class Session implements AutoCloseable {
        final ServerPlayer player;final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ite-content"),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
            player.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,3))));
            player.setNoGravity(true);player.setOnGround(true);player.getInventory().clearContent();player.removeAllEffects();
        }
        @Override public void close(){player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
    }
}
