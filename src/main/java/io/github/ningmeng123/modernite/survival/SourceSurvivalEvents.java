package io.github.ningmeng123.modernite.survival;

import io.github.ningmeng123.mite.portal.MitePortals;
import io.github.ningmeng123.mite.rules.R196PortalRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Effective ITE environmental paths, independent of the chosen Extreme fatigue counter. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class SourceSurvivalEvents {
    public static final String DATA="modern_ite:source_survival";
    private static final String HOT="modern_ite:netherrack_contacts";
    private SourceSurvivalEvents() {}
    public static CompoundTag state(Player player) { return player.getPersistentData().getCompound(DATA); }
    private static void save(Player player,CompoundTag data) { data.putInt("version",1);player.getPersistentData().put(DATA,data); }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()||player.isCreative()) return;
        ServerLevel level=player.serverLevel();
        boolean rain=level.isRainingAt(player.blockPosition())||level.isRainingAt(BlockPos.containing(player.getX(),player.getBoundingBox().maxY,player.getZ()));
        advanceRain(player,rain);
        advanceTeleport(player,R196MobSpawning.underworld(level));
        advanceNetherFatigue(player,level.dimension().equals(Level.NETHER));
    }
    /** Actual Nether residence advances the old timer; leaving drains it and cloning preserves it. */
    public static void advanceNetherFatigue(ServerPlayer player,boolean nether) {
        CompoundTag data=state(player);
        if(!IteConfig.bool("nether_debuff")){data.remove("nether_fatigue");save(player,data);return;}
        int elapsed=Math.max(0,data.getInt("nether_fatigue"));
        if(nether) {
            elapsed=(int)Math.min(Integer.MAX_VALUE,(long)elapsed+1);
            if(elapsed>=IteConfig.integer("nether_debuff_time"))player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,3600,1));
        }else if(elapsed>0)elapsed--;
        data.putInt("nether_fatigue",elapsed);save(player,data);
    }
    /** Rain exposure is accumulated, capped, saved, and slowly drained under shelter. */
    public static void advanceRain(ServerPlayer player,boolean raining) {
        int limit=IteConfig.integer("in_rain_debuff_time");
        CompoundTag data=state(player);int before=Math.clamp(data.getInt("rain"),0,limit);
        if(raining) {
            if(before<limit) data.putInt("rain",before+1);
            else player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,3600,0));
        } else if(before>0) data.putInt("rain",before-1);
        save(player,data);
    }
    /** Leaving the dimension drains the source timer; it does not reset all accumulated exposure. */
    public static void advanceTeleport(ServerPlayer player,boolean underworld) {
        int limit=IteConfig.integer("underworld_random_teleport_time_new");
        CompoundTag data=state(player);int elapsed=Math.clamp(data.getInt("teleport"),0,limit+1);
        if(!underworld || !IteConfig.bool("underworld_random_teleport")) {
            if(elapsed>0) data.putInt("teleport",elapsed-1);
            data.remove("pending");data.remove("retry_at");save(player,data);return;
        }
        elapsed=Math.min(limit+1,elapsed+1);data.putInt("teleport",elapsed);
        int left=limit-elapsed;
        if(left==6000) player.displayClientMessage(Component.translatable("message.modern_ite.teleport_five_minutes"),true);
        else if(left==1200) player.displayClientMessage(Component.translatable("message.modern_ite.teleport_one_minute"),true);
        else if(left>=0&&left<=200&&elapsed%20==0) player.displayClientMessage(Component.translatable("message.modern_ite.teleport_countdown",left/20),true);
        save(player,data);
        if(elapsed>limit&&player.serverLevel().getGameTime()>=data.getLong("retry_at")) tryRandomTeleport(player,data);
    }
    private static void tryRandomTeleport(ServerPlayer player,CompoundTag data) {
        ServerLevel level=player.serverLevel();
        if(!R196MobSpawning.underworld(level)) return;
        if(!data.getBoolean("pending")) {
            int radius=R196PortalRules.runeRadius(false,level.getWorldBorder().getSize()/2D);
            var destination=R196PortalRules.runeCoordinates(level.random.nextInt(),false,radius,
                (x,z)->!level.getWorldBorder().isWithinBounds(new BlockPos(x,0,z))||level.getBiome(new BlockPos(x,level.getMinBuildHeight()+64,z)).is(BiomeTags.IS_OCEAN));
            data.putInt("x",destination.x());data.putInt("z",destination.z());data.putBoolean("pending",true);
        }
        var landing=MitePortals.safeLanding(level,player,data.getInt("x"),data.getInt("z"),true,8);
        if(landing.isPresent()) {
            Vec3 pos=landing.get();player.teleportTo(level,pos.x,pos.y,pos.z,player.getYRot(),player.getXRot());
            player.displayClientMessage(Component.translatable("message.modern_ite.teleport_complete"),true);
            data.putInt("teleport",0);data.remove("pending");data.remove("retry_at");
        } else {
            data.putLong("retry_at",level.getGameTime()+200);
            player.displayClientMessage(Component.translatable("message.modern_ite.teleport_no_landing"),true);
        }
        save(player,data);
    }
    /** The source checks an expanded contact box, not footwear, and starts on displayed day96. */
    public static boolean hotNetherrack(Entity entity,long day) {
        if(!IteConfig.bool("netherrack_damage")||!entity.level().dimension().equals(Level.NETHER)||day<IteConfig.integer("netherrack_damage_limit_day")||entity.fireImmune()
                ||entity instanceof LivingEntity living&&living.hasEffect(MobEffects.FIRE_RESISTANCE)) return false;
        var box=entity.getBoundingBox().inflate(.001,.005,.001);
        return BlockPos.betweenClosedStream(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX-1E-7,box.maxY-1E-7,box.maxZ-1E-7))
                .anyMatch(pos->entity.level().hasChunkAt(pos)&&entity.level().getBlockState(pos).is(Blocks.NETHERRACK));
    }
    @SubscribeEvent public static void contact(EntityTickEvent.Post event) {
        Entity entity=event.getEntity();
        if(!(entity.level() instanceof ServerLevel level)||entity.isRemoved()||entity instanceof Player player&&(player.isCreative()||player.isSpectator())) return;
        long day=R196StructureGeneration.displayDay(level.getServer().overworld().getDayTime());
        if(!hotNetherrack(entity,day)) return;
        int contacts=entity.getPersistentData().getInt(HOT)+1;
        if(contacts>20) {
            contacts=0;entity.hurt(entity.damageSources().inFire(),1F);
        }
        entity.getPersistentData().putInt(HOT,contacts);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        CompoundTag previous=event.getOriginal().getPersistentData();
        if(previous.contains(DATA)) event.getEntity().getPersistentData().put(DATA,previous.getCompound(DATA).copy());
    }
}
