package io.github.ningmeng123.modernite.survival;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native beacon pulses, the manufacturing transaction and the spawner countdown. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class SourceUtilityGameTests {
    private SourceUtilityGameTests() {}

    @GameTest(template="gear_empty")
    public static void aClosePlayerSpeedsTheActualSpawnerWithoutChangingItsActivationRadius(GameTestHelper h) {
        try(Session session=new Session(h)) {
            var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(2,2,2));level.setBlockAndUpdate(pos,Blocks.SPAWNER.defaultBlockState());
            var spawner=((SpawnerBlockEntity)level.getBlockEntity(pos)).getSpawner();var tag=new CompoundTag();
            tag.putShort("Delay",(short)30);tag.putShort("SpawnRange",(short)4);tag.putShort("RequiredPlayerRange",(short)16);spawner.load(level,pos,tag);
            session.player.moveTo(Vec3.atCenterOf(pos).add(1,1,0));spawner.serverTick(level,pos);
            h.assertTrue(spawner.save(new CompoundTag()).getShort("Delay")==27,"A close player advances the real native countdown by three");
            session.player.moveTo(Vec3.atCenterOf(pos).add(12,0,0));spawner.serverTick(level,pos);
            h.assertTrue(spawner.save(new CompoundTag()).getShort("Delay")==26,"A player inside normal activation but outside the source close box only advances one");
            session.player.moveTo(Vec3.atCenterOf(pos).add(40,0,0));spawner.serverTick(level,pos);
            h.assertTrue(spawner.save(new CompoundTag()).getShort("Delay")==26,"The bonus cannot activate a spawner without a player in its native activation range");
        }h.succeed();
    }

    @GameTest(template="gear_empty",timeoutTicks=240)
    public static void aNativeBeaconPulseShortensARealCraftAndRemovalRestoresItsPeriod(GameTestHelper h) {
        Session session=new Session(h);var player=session.player;var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        for(BlockPos support:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,-1,1)))level.setBlockAndUpdate(support,Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos,Blocks.BEACON.defaultBlockState());var beacon=(BeaconBlockEntity)level.getBlockEntity(pos);
        var tag=new CompoundTag();tag.putString("primary_effect","minecraft:speed");beacon.loadWithComponents(tag,level.registryAccess());
        var menu=player.inventoryMenu;var grid=(CraftingContainer)menu.getSlot(1).container;
        grid.setItem(0,new ItemStack(Items.DANDELION,2));grid.setItem(1,new ItemStack(Items.DANDELION,2));
        grid.setItem(2,new ItemStack(Items.DANDELION,2));grid.setItem(3,new ItemStack(MiteFoods.WATER_BOWL.get(),2));menu.slotsChanged(grid);
        h.assertTrue(TimedCraftingController.period(player,menu)==100,"The real unskilled salad starts with its unchanged hundred-tick period");
        // The beam scanner and the ordinary eighty-tick beacon pulse run naturally in the test world.
        h.runAfterDelay(170,()->{
            try(session) {
                h.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED)&&SourceUtilityRules.craftingBoost(player)==.25F,
                        "An actual powered tier-one beacon grants native Speed and the source craft assistance together");
                h.assertTrue(TimedCraftingController.period(player,menu)==80,"The production controller applies the source quarter-speed bonus");
                menu.clicked(0,0,ClickType.PICKUP,player);
                for(int tick=0;tick<79;tick++)TimedCraftingController.tick(player);
                h.assertTrue(grid.getItem(0).getCount()==2&&TimedCraftingController.progress(player)==79,"The accelerated period still forbids early material consumption");
                TimedCraftingController.tick(player);TimedCraftingController.cancel(player);
                int salads=player.getInventory().countItem(MiteFoods.SALAD.get())+(menu.getCarried().is(MiteFoods.SALAD.get())?menu.getCarried().getCount():0);
                h.assertTrue(salads==1&&grid.getItem(0).getCount()==1&&grid.getItem(3).getCount()==1,"One real accelerated transaction consumes and produces exactly one batch");
                level.removeBlock(pos,false);
                h.assertTrue(SourceUtilityRules.craftingBoost(player)==0&&TimedCraftingController.period(player,menu)==100,"Removing the source beacon clears assistance immediately");
            }h.succeed();
        });
    }

    @GameTest(template="gear_empty")
    public static void aWildWolfOnlyUsesTheReachableHeldMeatLureAtSixtyFourBlocks(GameTestHelper h) {
        try(Session session=new Session(h)) {
            var wolf=h.spawn(net.minecraft.world.entity.EntityType.WOLF,new BlockPos(2,2,2));
            try {
                Vec3 origin=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,2,2))).add(0,160,0);
                wolf.moveTo(origin);wolf.setNoGravity(true);session.player.moveTo(origin.add(32,0,0));
                var goal=(SourceUtilityRules.MeatPlayerTarget)wolf.targetSelector.getAvailableGoals().stream().map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                        .filter(value->value instanceof SourceUtilityRules.MeatPlayerTarget).findFirst().orElseThrow();
                session.player.getInventory().setItem(1,new ItemStack(Items.BEEF));
                h.assertTrue(!goal.canUse(),"Unselected hotbar meat does not activate the unreachable legacy hotbar branch");
                session.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BEEF));
                h.assertTrue(goal.canUse(),"Held meat lures a wild wolf beyond the former ordinary follow radius");goal.start();
                h.assertTrue(wolf.getTarget()==session.player,"The installed real goal sets the native attack target");
                session.player.moveTo(origin.add(65,0,0));h.assertTrue(!goal.canUse(),"The source lure cannot target beyond sixty-four blocks");
                session.player.moveTo(origin.add(32,0,0));wolf.tame(session.player);h.assertTrue(!goal.canUse(),"An owned wolf does not treat its owner as meat prey");
            }finally{wolf.discard();}
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void overlappingBeaconSourcesUseTheStrongestAndFallBackWhenItIsRemoved(GameTestHelper h) {
        try(Session session=new Session(h)) {
            var player=session.player;var level=h.getLevel();BlockPos weak=h.absolutePos(new BlockPos(1,1,1)),strong=h.absolutePos(new BlockPos(3,1,1));
            level.setBlockAndUpdate(weak,Blocks.BEACON.defaultBlockState());level.setBlockAndUpdate(strong,Blocks.BEACON.defaultBlockState());
            SourceUtilityRules.beaconPulse(level,weak,1);SourceUtilityRules.beaconPulse(level,strong,4);
            h.assertTrue(SourceUtilityRules.craftingBoost(player)==.85F,"Overlapping pulses use the strongest source rather than stacking");
            level.removeBlock(strong,false);
            h.assertTrue(SourceUtilityRules.craftingBoost(player)==.25F,"Destroying the strong beacon immediately restores the remaining live source");
            level.removeBlock(weak,false);h.assertTrue(SourceUtilityRules.craftingBoost(player)==0,"No assistance remains without any live beacon source");
        }h.succeed();
    }

    private static final class Session implements AutoCloseable {
        final ServerPlayer player;final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ite-utility"),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,2,2))));player.setNoGravity(true);player.setOnGround(true);player.setInvulnerable(true);
            player.getInventory().clearContent();player.removeAllEffects();PlayerExperience.setTotal(player,0);player.getFoodData().setFoodLevel(6);
        }
        @Override public void close(){TimedCraftingController.forget(player);player.inventoryMenu.getSlot(1).container.clearContent();player.inventoryMenu.setCarried(ItemStack.EMPTY);player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
    }
}
