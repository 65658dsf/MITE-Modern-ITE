package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.github.ningmeng123.mite.registry.MiteTools;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real array binding, skin persistence and legacy-anchor ownership. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class ResurrectionGameTests {
    private ResurrectionGameTests() {}

    @GameTest(template="gear_empty")
    public static void placementPersistsAndSynchronizesTheOwnersSkin(GameTestHelper h) {
        try (Session session=new Session(h,"ite-array")) {
            ServerPlayer player=session.player;
            player.getGameProfile().getProperties().put("textures",new Property("textures","saved-owner-skin","signed-skin"));
            BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
            var state=ExtremeContent.RESURRECTION.get().defaultBlockState();
            h.getLevel().setBlockAndUpdate(pos,state);
            state.getBlock().setPlacedBy(h.getLevel(),pos,state,player,new ItemStack(state.getBlock()));
            var array=(ResurrectionBlockEntity)h.getLevel().getBlockEntity(pos);
            h.assertTrue(array.isOwner(player)&&array.ownerName().equals("ite-array"),"Placement binds the actual player identity");
            var saved=array.saveWithoutMetadata(h.getLevel().registryAccess());
            var restored=new ResurrectionBlockEntity(pos,state);
            restored.loadWithComponents(saved,h.getLevel().registryAccess());
            h.assertTrue(restored.isOwner(player)&&restored.getOwnerProfile().properties().get("textures").stream()
                    .anyMatch(property->property.value().equals("saved-owner-skin")&&property.signature().equals("signed-skin")),
                    "Disk saves retain UUID and signed skin properties for an offline owner");
            var clientCopy=new ResurrectionBlockEntity(pos,state);
            clientCopy.handleUpdateTag(array.getUpdateTag(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(clientCopy.isOwner(player)&&clientCopy.getOwnerProfile().properties().equals(restored.getOwnerProfile().properties())
                    &&array.getUpdatePacket()!=null,"Initial chunk data and later update packets carry the same owner skin");
            h.assertTrue(ExtremeLifeEvents.resolveAnchor(player).orElseThrow().position().equals(pos),"The bound array remains the player's resurrection anchor");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void anOldAnchorCannotConsumeAnotherPlayersReplacement(GameTestHelper h) {
        try (Session previous=new Session(h,"ite-array-old");Session next=new Session(h,"ite-array-new")) {
            BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
            var state=ExtremeContent.RESURRECTION.get().defaultBlockState();
            h.getLevel().setBlockAndUpdate(pos,state);bind(previous.player,h,pos);
            h.getLevel().removeBlock(pos,false);h.getLevel().setBlockAndUpdate(pos,state);
            state.getBlock().setPlacedBy(h.getLevel(),pos,state,next.player,new ItemStack(state.getBlock()));
            var array=(ResurrectionBlockEntity)h.getLevel().getBlockEntity(pos);
            h.assertTrue(ExtremeLifeEvents.resolveAnchor(previous.player).isEmpty()&&array.isOwner(next.player),
                    "A stale position cannot steal the new owner's array");
            h.assertTrue(ExtremeLifeEvents.resolveAnchor(next.player).isPresent(),"The replacement owner retains a usable anchor");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void legacyAnchorsRecoverTheirOwnerAndRefreshSkinsOnLogin(GameTestHelper h) {
        try (Session session=new Session(h,"ite-array-old")) {
            BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
            var state=ExtremeContent.RESURRECTION.get().defaultBlockState();
            h.getLevel().setBlockAndUpdate(pos,state);bind(session.player,h,pos);
            // Old saves contain the block and the player's position, but no block-entity owner.
            h.getLevel().removeBlockEntity(pos);
            h.assertTrue(ExtremeLifeEvents.resolveAnchor(session.player).isPresent(),"An old player-side anchor remains valid after upgrading");
            var array=(ResurrectionBlockEntity)h.getLevel().getBlockEntity(pos);
            h.assertTrue(array.isOwner(session.player),"Migration reconstructs and synchronizes the missing owner data");
            var restored=new ResurrectionBlockEntity(pos,state);
            restored.loadWithComponents(array.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(restored);
            session.player.getGameProfile().getProperties().put("textures",new Property("textures","new-skin"));
            ExtremeLifeEvents.login(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(session.player));
            h.assertTrue(restored.getOwnerProfile().properties().get("textures").stream().anyMatch(property->property.value().equals("new-skin")),
                    "A returning owner refreshes persisted skin properties without per-tick profile requests");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void onlyTheOwnerCanBreakTheArrayInSurvivalAndCreative(GameTestHelper h) {
        try (Session owner=new Session(h,"array-keeper");Session visitor=new Session(h,"array-visitor")) {
            BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
            var state=ExtremeContent.RESURRECTION.get().defaultBlockState();
            h.getLevel().setBlockAndUpdate(pos,state);
            state.getBlock().setPlacedBy(h.getLevel(),pos,state,owner.player,new ItemStack(state.getBlock()));
            CompoundTag anchor=owner.player.getPersistentData().getCompound(ExtremeLifeEvents.ANCHOR).copy();
            visitor.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MiteTools.get("mithril_pickaxe")));
            int drops=arrayDrops(h,pos);
            for (GameType mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) {
                visitor.player.setGameMode(mode);
                h.assertTrue(state.getDestroyProgress(visitor.player,h.getLevel(),pos)==0,
                        "A non-owner cannot accumulate mining progress in "+mode);
                h.assertTrue(!visitor.player.gameMode.destroyBlock(pos),"Native block destruction rejects a non-owner in "+mode);
                h.assertTrue(h.getLevel().getBlockState(pos).is(state.getBlock())
                        &&h.getLevel().getBlockEntity(pos) instanceof ResurrectionBlockEntity array&&array.isOwner(owner.player),
                        "Rejected destruction preserves the block and its original owner in "+mode);
                h.assertTrue(anchor.equals(owner.player.getPersistentData().getCompound(ExtremeLifeEvents.ANCHOR))
                        &&arrayDrops(h,pos)==drops&&visitor.player.getMainHandItem().getDamageValue()==0,
                        "Rejected destruction consumes no anchor, drops or tool durability in "+mode);
            }
            owner.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MiteTools.get("mithril_pickaxe")));
            for (GameType mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) {
                if (h.getLevel().getBlockState(pos).isAir()) {
                    h.getLevel().setBlockAndUpdate(pos,state);
                    state.getBlock().setPlacedBy(h.getLevel(),pos,state,owner.player,new ItemStack(state.getBlock()));
                }
                owner.player.setGameMode(mode);
                h.assertTrue(owner.player.gameMode.destroyBlock(pos)&&h.getLevel().getBlockState(pos).isAir(),
                        "The actual owner retains native destruction in "+mode);
                h.assertTrue(!owner.player.getPersistentData().contains(ExtremeLifeEvents.ANCHOR),
                        "Successful owner removal unbinds the consumed anchor in "+mode);
            }
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void savedOwnershipProtectsTheArrayWhileItsOwnerIsOffline(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        var state=ExtremeContent.RESURRECTION.get().defaultBlockState();
        CompoundTag saved;
        UUID ownerId;
        try (Session owner=new Session(h,"array-keeper")) {
            ownerId=owner.player.getUUID();
            h.getLevel().setBlockAndUpdate(pos,state);
            state.getBlock().setPlacedBy(h.getLevel(),pos,state,owner.player,new ItemStack(state.getBlock()));
            saved=h.getLevel().getBlockEntity(pos).saveWithoutMetadata(h.getLevel().registryAccess());
        }
        var restored=new ResurrectionBlockEntity(pos,state);
        restored.loadWithComponents(saved,h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored);
        try (Session visitor=new Session(h,"array-visitor")) {
            h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayer(ownerId)==null,"The original owner has left the server");
            visitor.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MiteTools.get("mithril_pickaxe")));
            int drops=arrayDrops(h,pos);
            for (GameType mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) {
                visitor.player.setGameMode(mode);
                h.assertTrue(!visitor.player.gameMode.destroyBlock(pos)&&h.getLevel().getBlockEntity(pos)==restored
                        &&restored.getOwnerProfile().id().orElseThrow().equals(ownerId),
                        "Saved UUID ownership still protects the offline owner's array in "+mode);
            }
            h.assertTrue(arrayDrops(h,pos)==drops&&visitor.player.getMainHandItem().getDamageValue()==0,
                    "Breaking an offline owner's restored array produces no drops and spends no durability");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void anUnboundLegacyArrayRejectsVisitorsAndLetsItsAnchorOwnerMigrate(GameTestHelper h) {
        try (Session owner=new Session(h,"array-keeper");Session visitor=new Session(h,"array-visitor")) {
            BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
            var state=ExtremeContent.RESURRECTION.get().defaultBlockState();
            h.getLevel().setBlockAndUpdate(pos,state);
            visitor.player.setGameMode(GameType.CREATIVE);
            h.assertTrue(!visitor.player.gameMode.destroyBlock(pos)
                    &&h.getLevel().getBlockEntity(pos) instanceof ResurrectionBlockEntity array&&!array.hasOwner(),
                    "An unbound array is protected instead of being claimed or destroyed by a visitor");
            bind(owner.player,h,pos);
            h.getLevel().removeBlockEntity(pos);
            h.assertTrue(!visitor.player.gameMode.destroyBlock(pos)&&h.getLevel().getBlockState(pos).is(state.getBlock()),
                    "Missing pre-upgrade owner data does not allow another player to remove the array");
            owner.player.setGameMode(GameType.CREATIVE);
            h.assertTrue(owner.player.gameMode.destroyBlock(pos)&&h.getLevel().getBlockState(pos).isAir()
                    &&!owner.player.getPersistentData().contains(ExtremeLifeEvents.ANCHOR),
                    "The player with the real saved anchor can migrate and remove their own legacy array");
        }
        h.succeed();
    }

    private static int arrayDrops(GameTestHelper h,BlockPos pos) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1)).stream()
                .filter(entity->entity.getItem().is(ExtremeContent.RESURRECTION.get().asItem()))
                .mapToInt(entity->entity.getItem().getCount()).sum();
    }

    private static void bind(ServerPlayer player,GameTestHelper h,BlockPos pos) {
        var tag=new CompoundTag();tag.putString("dimension",h.getLevel().dimension().location().toString());tag.putLong("position",pos.asLong());
        player.getPersistentData().put(ExtremeLifeEvents.ANCHOR,tag);
    }

    private static final class Session implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        Session(GameTestHelper h,String name) {
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),name),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
            player.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,3))));
            player.setNoGravity(true);player.setOnGround(true);player.getInventory().clearContent();
        }
        @Override public void close() { player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll(); }
    }
}
