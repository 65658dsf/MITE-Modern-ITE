package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.LoggerFactory;

/** Source survival events; counters are stored on players and survive cloning. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class ExtremeLifeEvents {
    public static final String ANCHOR="modern_ite:resurrection", PENALTY="modern_ite:underworld_penalty";
    public static int coreLevel(Player player) {
        int result=0;for(var stack:player.getInventory().items)if(stack.getItem() instanceof DynamicCoreItem core&&stack.getDamageValue()<stack.getMaxDamage()-2)result=Math.max(result,core.level);
        if(player.getOffhandItem().getItem() instanceof DynamicCoreItem core&&player.getOffhandItem().getDamageValue()<player.getOffhandItem().getMaxDamage()-2)result=Math.max(result,core.level);return result;
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||player.isSpectator()||player.isCreative())return;
        var data=player.getPersistentData();var penalty=data.getCompound(PENALTY);boolean under=R196MobSpawning.underworld(player.serverLevel());
        int elapsed=penalty.getInt("elapsed"), recovery=penalty.getInt("recovery");boolean active=penalty.getBoolean("active");
        if(under){ elapsed=(int)Math.min(Integer.MAX_VALUE,(long)elapsed+1);recovery=0; if(elapsed>=54000)active=true;
            if(active&&player.tickCount%100==0)player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,220,0));
            if(active&&!penalty.getBoolean("warned")){player.sendSystemMessage(Component.translatable("message.modern_ite.underworld_penalty"));penalty.putBoolean("warned",true);}
        }else if(active&&++recovery>=24000){elapsed=0;recovery=0;active=false;penalty.putBoolean("warned",false);player.sendSystemMessage(Component.translatable("message.modern_ite.underworld_recovered"));}
        penalty.putInt("elapsed",elapsed);penalty.putInt("recovery",recovery);penalty.putBoolean("active",active);penalty.putInt("version",1);data.put(PENALTY,penalty);
        if(player.tickCount%10==0){
            int best=coreLevel(player);if(best>0){
                var old=player.getEffect(MobEffects.NIGHT_VISION);if(old==null||old.getDuration()<=220)player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,220,0,true,false));
                ItemStack selected=ItemStack.EMPTY;for(var stack:player.getInventory().items)if(stack.getItem() instanceof DynamicCoreItem core&&core.level==best&&stack.getDamageValue()<stack.getMaxDamage()-2){selected=stack;break;}
                if(selected.isEmpty()&&player.getOffhandItem().getItem() instanceof DynamicCoreItem)selected=player.getOffhandItem();
                if(!selected.isEmpty()){selected.setDamageValue(Math.min(selected.getMaxDamage(),selected.getDamageValue()+2));player.getInventory().setChanged();}
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void resurrect(LivingDeathEvent event) {
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player))return;
        var data=player.getPersistentData();var anchor=resolveAnchor(player);
        if(anchor.isEmpty()){data.remove(ANCHOR);return;}
        ServerLevel target=anchor.get().level();BlockPos pos=anchor.get().position();
        Optional<Vec3> landing=resurrectionLanding(target,pos,player);
        if(landing.isEmpty())return;
        // Travel can be vetoed by another mod. Keep the item and XP until the actual move succeeds.
        Vec3 destination=landing.get();float before=player.getHealth();player.setHealth(Math.min(20F,player.getMaxHealth()));
        player.teleportTo(target,destination.x,destination.y,destination.z,Set.of(),player.getYRot(),player.getXRot());
        if(player.serverLevel()!=target||player.position().distanceToSqr(destination)>1.0E-4){player.setHealth(before);return;}
        data.remove(ANCHOR);target.removeBlock(pos,false);event.setCanceled(true);PlayerExperience.setTotal(player,0);
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,1200,1));player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,600,1));player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,1200,0));player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,18000,1));
        player.sendSystemMessage(Component.translatable("message.modern_ite.array_used"));
    }
    public record Anchor(ServerLevel level,BlockPos position) {}
    static Optional<Anchor> resolveAnchor(ServerPlayer player) {
        var data=player.getPersistentData();if(!data.contains(ANCHOR))return Optional.empty();
        var saved=data.getCompound(ANCHOR);ResourceLocation id=ResourceLocation.tryParse(saved.getString("dimension"));
        if(id==null||!saved.contains("position",net.minecraft.nbt.Tag.TAG_LONG))return Optional.empty();
        ServerLevel level=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,id));BlockPos pos=BlockPos.of(saved.getLong("position"));
        if(level==null||!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos))return Optional.empty();
        level.getChunkAt(pos);
        return level.getBlockState(pos).is(ExtremeContent.RESURRECTION.get())?Optional.of(new Anchor(level,pos)):Optional.empty();
    }
    /** Check the occupied volume and the floor that remains after consuming the array. Never carve terrain. */
    static Optional<Vec3> resurrectionLanding(ServerLevel level,BlockPos array,ServerPlayer player) {
        for(BlockPos.MutableBlockPos column:BlockPos.spiralAround(array,2,Direction.EAST,Direction.SOUTH))for(int dy:new int[]{1,0,2,-1}) {
            BlockPos feet=column.offset(0,dy,0),floor=feet.below();
            if(floor.equals(array))floor=floor.below();
            if(!level.isInWorldBounds(feet)||feet.getY()+player.getBbHeight()>level.getMaxBuildHeight()
                    ||!level.getWorldBorder().isWithinBounds(feet))continue;
            BlockState support=level.getBlockState(floor);
            if(!support.isFaceSturdy(level,floor,Direction.UP)||unsafe(support))continue;
            Vec3 result=Vec3.atBottomCenterOf(feet);var box=player.getDimensions(player.getPose()).makeBoundingBox(result);
            if(!level.getWorldBorder().isWithinBounds(box)||!level.noCollision(player,box))continue;
            boolean clear=true;
            for(BlockPos occupied:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),
                    BlockPos.containing(box.maxX-1.0E-7,box.maxY-1.0E-7,box.maxZ-1.0E-7)))if(unsafe(level.getBlockState(occupied))){clear=false;break;}
            if(clear)return Optional.of(result);
        }
        return Optional.empty();
    }
    private static boolean unsafe(BlockState state) {
        return !state.getFluidState().isEmpty()||state.is(io.github.ningmeng123.mite.registry.MiteWorldBlocks.MANTLE.get())
                ||state.is(io.github.ningmeng123.mite.registry.MiteWorldBlocks.CORE.get())||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)
                ||state.is(Blocks.CACTUS)||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        var before=event.getOriginal().getPersistentData();var after=event.getEntity().getPersistentData();
        if(before.contains(ANCHOR))after.put(ANCHOR,before.getCompound(ANCHOR).copy());if(before.contains(PENALTY))after.put(PENALTY,before.getCompound(PENALTY).copy());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void use(PlayerInteractEvent.RightClickItem event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!event.getItemStack().is(ExtremeContent.FANCY_RED.get()))return;
        PlayerExperience.addPoints(player,500);if(!player.isCreative())event.getItemStack().shrink(1);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);event.setCanceled(true);
    }
    public static String foreignStrongboxOwner(ServerPlayer player,BlockPos pos) {
        var entity=player.serverLevel().getBlockEntity(pos);
        if(entity instanceof StrongboxBlockEntity box&&!box.isOwner(player))return box.ownerName();
        if(entity instanceof VibraniumChestEntity box&&!box.isOwner(player))return box.ownerName();
        return null;
    }
    /** Called by the native removal wrapper after onDestroyedByPlayer really removed the block. */
    public static void afterBlockRemoved(ServerPlayer player,BlockPos pos,BlockState state,String owner) {
        ServerLevel level=player.serverLevel();
        if(owner!=null){var message=Component.translatable("message.modern_ite.strongbox_broken",player.getName(),owner,pos.toShortString());level.getServer().getPlayerList().broadcastSystemMessage(message,false);LoggerFactory.getLogger(ModernIte.MOD_ID).info("Strongbox broken by {} owner={} dimension={} pos={}",player.getUUID(),owner,level.dimension().location(),pos);}
        var data=player.getPersistentData();if(state.is(ExtremeContent.RESURRECTION.get())&&data.contains(ANCHOR)){var anchor=data.getCompound(ANCHOR);if(anchor.getString("dimension").equals(level.dimension().location().toString())&&anchor.getLong("position")==pos.asLong())data.remove(ANCHOR);}
        if(!io.github.ningmeng123.modernite.economy.ExtremeEconomy.suppressCropVoucher()&&state.getBlock() instanceof CropBlock crop&&crop.isMaxAge(state))io.github.ningmeng123.modernite.economy.ExtremeEconomy.harvestVoucher(level,pos,player);
    }
}
