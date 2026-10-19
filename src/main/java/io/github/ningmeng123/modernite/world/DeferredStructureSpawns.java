package io.github.ningmeng123.modernite.world;

import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Keep one-shot structure inhabitants until automatic spawning is allowed. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class DeferredStructureSpawns extends SavedData {
    private static final Factory<DeferredStructureSpawns> FACTORY=new Factory<>(DeferredStructureSpawns::new,DeferredStructureSpawns::load);
    private static final ThreadLocal<Boolean> REPLAYING=ThreadLocal.withInitial(()->false);
    private final List<CompoundTag> entries=new ArrayList<>();
    private DeferredStructureSpawns() {}
    static DeferredStructureSpawns get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY,"modern_ite_deferred_structure_spawns");
    }
    public static void defer(Mob mob,boolean needsFinalize) {
        if(REPLAYING.get())return;
        ServerLevel level=(ServerLevel)mob.level();
        CompoundTag entity=new CompoundTag();if(!mob.save(entity))return;
        CompoundTag entry=new CompoundTag();entry.putString("dimension",level.dimension().location().toString());
        entry.put("entity",entity);entry.putBoolean("finalize",needsFinalize);
        Runnable append=()->{
            DeferredStructureSpawns data=get(level);
            if(data.entries.stream().noneMatch(old->old.getCompound("entity").hasUUID("UUID")
                    &&old.getCompound("entity").getUUID("UUID").equals(mob.getUUID()))) {
                data.entries.add(entry);data.setDirty();
            }
        };
        if(level.getServer().isSameThread())append.run();else level.getServer().execute(append);
    }
    @SubscribeEvent
    public static void afterLevelTick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel root)||root.dimension()!=Level.OVERWORLD
                ||root.getGameTime()%20!=0||ExtremeWorldState.graceActive(root))return;
        replay(root);
    }
    static void replay(ServerLevel root) {
        if(ExtremeWorldState.graceActive(root))return;
        DeferredStructureSpawns data=get(root);
        for(Iterator<CompoundTag> iterator=data.entries.iterator();iterator.hasNext();) {
            CompoundTag entry=iterator.next(),tag=entry.getCompound("entity");
            ResourceLocation dimension=ResourceLocation.tryParse(entry.getString("dimension"));
            if(dimension==null){iterator.remove();data.setDirty();continue;}
            ServerLevel level=root.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,dimension));
            if(level==null)continue;
            Entity entity=EntityType.loadEntityRecursive(tag.copy(),level,value->value);
            if(!(entity instanceof Mob mob)){iterator.remove();data.setDirty();continue;}
            if(!level.hasChunkAt(mob.blockPosition())||!SpawnPopulation.automaticAllowed(level,mob.getType()))continue;
            if(level.getEntity(mob.getUUID())!=null){iterator.remove();data.setDirty();continue;}
            // Admission rechecks local crowding at the actual replay time.
            REPLAYING.set(true);
            List<Entity> owned=new ArrayList<>();owned.add(mob);
            try {
                if(entry.getBoolean("finalize"))EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.STRUCTURE,null);
                mob.getSelfAndPassengers().forEach(value->{if(!owned.contains(value))owned.add(value);});
                if(!mob.isSpawnCancelled()&&mob.getSpawnType()!=null) {
                    CompoundTag initialized=new CompoundTag();mob.save(initialized);
                    forgetAdmission(initialized);
                    entry.put("entity",initialized);entry.putBoolean("finalize",false);data.setDirty();
                }
                if(!mob.isSpawnCancelled()&&(!entry.getBoolean("finalize")||mob.getSpawnType()!=null)&&level.tryAddFreshEntityWithPassengers(mob)
                        &&owned.stream().allMatch(value->value.isAddedToLevel()&&!value.isRemoved())) {
                    iterator.remove();data.setDirty();
                }else {for(Entity child:owned){child.discard();if(child instanceof Mob member)SpawnPopulation.release(member);}}
            } finally {REPLAYING.remove();}
        }
    }
    private static void forgetAdmission(Tag tag) {
        if(tag instanceof CompoundTag compound) {
            compound.remove("modern_ite:spawn_admitted");
            for(String key:compound.getAllKeys())forgetAdmission(compound.get(key));
        }else if(tag instanceof ListTag list)for(Tag child:list)forgetAdmission(child);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("version",1);ListTag list=new ListTag();entries.forEach(entry->list.add(entry.copy()));tag.put("spawns",list);return tag;
    }
    static DeferredStructureSpawns load(CompoundTag tag,HolderLookup.Provider registries) {
        DeferredStructureSpawns data=new DeferredStructureSpawns();
        for(Tag entry:tag.getList("spawns",Tag.TAG_COMPOUND))data.entries.add(((CompoundTag)entry).copy());return data;
    }
}
