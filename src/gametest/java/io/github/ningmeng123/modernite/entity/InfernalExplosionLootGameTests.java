package io.github.ningmeng123.modernite.entity;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Real destruction and death transactions with ordinary explosions as a control. @author NingMeng123 */
@GameTestHolder("modern_ite_feedback")
@PrefixGameTestTemplate(false)
public final class InfernalExplosionLootGameTests {
    private InfernalExplosionLootGameTests() {}

    @GameTest(template="gear_empty",batch="infernal_no_loot",timeoutTicks=1200)
    public static void infernalCraterDropsContainerContentsButNoTerrainOrMobLootAndOrdinaryExplosionsStillWork(GameTestHelper h) {
        var fixture=new Fixture(h);
        try {
            var site=fixture.site;var level=site.level;
            Creeper source=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
            var stone=BlockPos.containing(site.origin).offset(0,-1,0);
            var chest=stone.offset(2,1,0);var furnace=stone.offset(4,1,0);
            fixture.block(stone,Blocks.STONE.defaultBlockState());
            fixture.container(chest,Blocks.CHEST.defaultBlockState());
            fixture.container(furnace,Blocks.FURNACE.defaultBlockState());
            var existing=new ItemEntity(level,site.origin.x+42,site.origin.y+2,site.origin.z,new ItemStack(Items.EMERALD,7));
            existing.setInvulnerable(true);level.addFreshEntity(existing);
            var insideEmerald=new ItemEntity(level,site.origin.x+3,site.origin.y+.2,site.origin.z,new ItemStack(Items.EMERALD,7));
            insideEmerald.setNoGravity(true);insideEmerald.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(insideEmerald);
            var insideEmeraldExtra=new ItemEntity(level,site.origin.x+3.1,site.origin.y+.2,site.origin.z,new ItemStack(Items.EMERALD,4));
            insideEmeraldExtra.setNoGravity(true);insideEmeraldExtra.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(insideEmeraldExtra);
            var cornerTool=new ItemEntity(level,site.origin.x+40,site.origin.y+.2,site.origin.z+40,new ItemStack(Items.IRON_PICKAXE));
            cornerTool.setNoGravity(true);cornerTool.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(cornerTool);
            var insideStart=insideEmerald.position();var cornerStart=cornerTool.position();var originalTool=cornerTool.getItem().copy();
            var existingXp=new ExperienceOrb(level,site.origin.x+42,site.origin.y,site.origin.z,7);level.getChunkAt(existingXp.blockPosition());level.addFreshEntity(existingXp);
            InfernalExplosionGameTestSupport.whenEntityVisible(h,level,cornerTool,fixture.bounds,()->{
            try {
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,fixture.bounds).contains(cornerTool),"The outer-corner tool is query-visible inside the native AABB before detonation");
            Mob victim=fixture.victim(site.origin.add(-1,0,0));
            int initialLootEntities=fixture.loot.size();
            var boundedEvent=new AtomicBoolean();
            Consumer<ExplosionEvent.Detonate> inspect=event->{if(event.getExplosion().getDirectSourceEntity()==source)boundedEvent.set(event.getAffectedEntities().stream().anyMatch(entity->entity instanceof ItemEntity item&&item.getItem().is(Items.EMERALD)&&item.distanceToSqr(insideStart)<1)&&!event.getAffectedEntities().contains(cornerTool));};
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ExplosionEvent.Detonate.class,inspect);
            try{level.explode(source,source.getX(),source.getY(),source.getZ(),3F,Level.ExplosionInteraction.MOB);}
            finally{NeoForge.EVENT_BUS.unregister(inspect);}
            h.assertTrue(boundedEvent.get(),"The public Detonate event receives the inner item and excludes the outer AABB-corner tool before source handlers run");
            h.assertTrue(!victim.isAlive(),"The infernal blast directly kills the low-health equipped victim immediately");
            h.assertTrue(fixture.emeraldCount(insideStart)==11&&fixture.innerEmeralds(insideStart).allMatch(item->item.getDeltaMovement().lengthSqr()==0),"Existing inner emerald stacks retain all eleven items without fragments or motion, whether merged or separate");
            h.assertTrue(!cornerTool.isRemoved()&&ItemStack.matches(originalTool,cornerTool.getItem())&&cornerTool.position().equals(cornerStart)&&cornerTool.getDeltaMovement().lengthSqr()==0,"A tool at the native AABB corner (40,40), outside the radius40 sphere, receives neither source wear nor motion");
            InfernalExplosionGameTestSupport.finishAfterJobs(h,level,()->{
                h.assertTrue(level.getBlockState(stone).isAir()&&level.getBlockState(chest).isAir()&&level.getBlockState(furnace).isAir(),"Stone, filled chest and filled furnace are actually destroyed");
                h.assertTrue(fixture.items().filter(item->item.getItem().is(Items.DIAMOND)).mapToInt(item->item.getItem().getCount()).sum()==46,"Both filled containers release every stored diamond exactly once");
                h.assertTrue(fixture.loot.subList(initialLootEntities,fixture.loot.size()).stream().allMatch(entity->entity instanceof ItemEntity item&&fixture.admittedItems.get(item).is(Items.DIAMOND)),
                        "Only the stored inventory is admitted; no container bodies, furnace material, stone, mob equipment, fragments or XP; new="+fixture.loot.subList(initialLootEntities,fixture.loot.size()).stream().map(entity->entity instanceof ItemEntity item?fixture.admittedItems.get(item):entity).toList());
                h.assertTrue(fixture.emeraldCount(insideStart)==11&&!existing.isRemoved()&&existing.getItem().getCount()==7,"Existing merged inner stacks retain eleven emeralds and the unrelated outer stack retains seven");
                h.assertTrue(fixture.innerEmeralds(insideStart).allMatch(item->item.position().distanceToSqr(insideStart)<.02&&item.getDeltaMovement().lengthSqr()==0)&&cornerTool.position().equals(cornerStart)&&ItemStack.matches(originalTool,cornerTool.getItem()),"Existing inner emeralds and outer-corner tool remain unchanged after queued destruction completes");
                h.assertTrue(!existingXp.isRemoved()&&fixture.loot.stream().filter(entity->entity instanceof ExperienceOrb&&!entity.isRemoved()).count()==1,"No new mob or block XP; unrelated existing XP remains");
                h.assertTrue(level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)&&level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT),"Dedicated suppression leaves global item and mob loot rules enabled");

                var ordinaryChest=BlockPos.containing(site.origin).offset(12,0,0);
                fixture.container(ordinaryChest,Blocks.CHEST.defaultBlockState());
                int diamondsBeforeOrdinary=fixture.items().filter(item->item.getItem().is(Items.DIAMOND)).mapToInt(item->item.getItem().getCount()).sum();
                level.explode(null,ordinaryChest.getX()+.5,ordinaryChest.getY()+.5,ordinaryChest.getZ()+.5,3F,Level.ExplosionInteraction.MOB);
                h.assertTrue(level.getBlockState(ordinaryChest).isAir(),"The ordinary control really destroys its filled container");
                h.assertTrue(fixture.items().filter(item->item.getItem().is(Items.DIAMOND)).mapToInt(item->item.getItem().getCount()).sum()==diamondsBeforeOrdinary+23,"An ordinary explosion still releases its own complete container contents");
                Mob ordinaryVictim=fixture.victim(site.origin.add(24,0,0));
                level.explode(null,ordinaryVictim.getX()+1,ordinaryVictim.getY()+.4,ordinaryVictim.getZ(),3F,Level.ExplosionInteraction.MOB);
                h.assertTrue(!ordinaryVictim.isAlive(),"The ordinary control really kills its equipped victim");
                h.assertTrue(fixture.loot.stream().anyMatch(entity->entity instanceof ExperienceOrb&&entity!=existingXp&&!entity.isRemoved()),"An ordinary explosion still awards XP to a recently player-hit mob");
                h.assertTrue(fixture.items().anyMatch(item->item.getItem().is(Items.GOLD_INGOT)),"Ordinary mob equipment loot remains enabled after the infernal callback scope closes");
                var ordinaryEmerald=new ItemEntity(level,site.origin.x+32,site.origin.y+.2,site.origin.z+8,new ItemStack(Items.EMERALD,5));
                ordinaryEmerald.setNoGravity(true);ordinaryEmerald.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(ordinaryEmerald);
                level.explode(null,ordinaryEmerald.getX()+1,ordinaryEmerald.getY()+.3,ordinaryEmerald.getZ(),3F,Level.ExplosionInteraction.MOB);
                h.assertTrue(ordinaryEmerald.isRemoved()&&fixture.items().filter(item->item.getItem().is(io.github.ningmeng123.mite.registry.MiteShards.EMERALD_SHARD.get())).mapToInt(item->item.getItem().getCount()).sum()==5,"Ordinary explosions retain all five emerald-to-fragments conversions");
            },fixture::close);
            }catch(Throwable failure){fixture.close();throw failure;}
            },fixture::close);
        }catch(Throwable failure){fixture.close();throw failure;}
    }

    @GameTest(template="gear_empty",batch="infernal_child_crater",timeoutTicks=1200)
    public static void legacyChildCraterStopsAtTwentyAndQueuedStateChangesArePreserved(GameTestHelper h) {
        var fixture=new Fixture(h);
        try {
            var site=fixture.site;Creeper source=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));MobSplitting.markChild(source);
            var floor=BlockPos.containing(site.origin).below();var inside=floor.offset(19,0,0);var outside=floor.offset(21,0,0);var changed=floor.offset(5,0,0);
            for(var pos:new BlockPos[]{inside,outside,changed})fixture.block(pos,Blocks.STONE.defaultBlockState());
            site.level.explode(source,source.getX(),source.getY(),source.getZ(),3F,Level.ExplosionInteraction.MOB);
            site.level.setBlock(changed,Blocks.DIRT.defaultBlockState(),2);
            InfernalExplosionGameTestSupport.finishAfterJobs(h,site.level,()->{
                h.assertTrue(site.level.getBlockState(inside).isAir()&&site.level.getBlockState(outside).is(Blocks.STONE),"Queued legacy miniature crater reaches nineteen blocks and stops before twenty-one");
                h.assertTrue(site.level.getBlockState(changed).is(Blocks.DIRT),"Queued destruction never removes a different state placed after the explosion selection");
            },fixture::close);
        }catch(Throwable failure){fixture.close();throw failure;}
    }

    @GameTest(template="gear_empty",batch="infernal_queue_lifecycle",timeoutTicks=1200)
    public static void disablingMobGriefingStopsAnAlreadyQueuedCrater(GameTestHelper h) {
        var fixture=new Fixture(h);
        try {
            var site=fixture.site;Creeper source=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
            var pos=BlockPos.containing(site.origin).below();fixture.block(pos,Blocks.STONE.defaultBlockState());
            site.level.explode(source,source.getX(),source.getY(),source.getZ(),3F,Level.ExplosionInteraction.MOB);
            h.assertTrue(InfernalExplosionDestruction.remainingJobs(site.level)>0,"The dedicated crater has a queued job before the game rule changes");
            site.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,site.level.getServer());
            InfernalExplosionGameTestSupport.finishAfterJobs(h,site.level,()->h.assertTrue(site.level.getBlockState(pos).is(Blocks.STONE),"Changing mobGriefing to false stops queued destruction before its next block callback"),fixture::close);
        }catch(Throwable failure){fixture.close();throw failure;}
    }

    private static final class Fixture implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final net.minecraft.world.entity.player.Player player;
        final AABB bounds;
        final Runnable releaseChunks;
        final java.util.List<Entity> loot=new java.util.ArrayList<>();
        final java.util.Map<ItemEntity,ItemStack> admittedItems=new java.util.IdentityHashMap<>();
        final Consumer<EntityJoinLevelEvent> observeLoot;
        final LinkedHashMap<BlockPos,BlockState> terrain=new LinkedHashMap<>();
        final boolean griefing,tileDrops,mobLoot,decay;
        boolean closed;
        Fixture(GameTestHelper h){
            site=new SpawnBalanceGameTests.Site(h);site.date(256,256,true);bounds=new AABB(site.origin,site.origin).inflate(44);releaseChunks=InfernalExplosionGameTestSupport.holdChunks(site.level,bounds);player=h.makeMockPlayer(GameType.SURVIVAL);
            observeLoot=event->{
                if(event.isCanceled()||event.getLevel()!=site.level||!bounds.contains(event.getEntity().position())||!(event.getEntity() instanceof ItemEntity||event.getEntity() instanceof ExperienceOrb))return;
                loot.add(event.getEntity());
                // Native merging can empty and discard an admitted item before queued destruction finishes.
                if(event.getEntity() instanceof ItemEntity item)admittedItems.put(item,item.getItem().copy());
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityJoinLevelEvent.class,observeLoot);
            var rules=site.level.getGameRules();griefing=rules.getBoolean(GameRules.RULE_MOBGRIEFING);tileDrops=rules.getBoolean(GameRules.RULE_DOBLOCKDROPS);mobLoot=rules.getBoolean(GameRules.RULE_DOMOBLOOT);decay=rules.getBoolean(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY);
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(true,site.level.getServer());rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(true,site.level.getServer());rules.getRule(GameRules.RULE_DOMOBLOOT).set(true,site.level.getServer());rules.getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(false,site.level.getServer());
        }
        void block(BlockPos pos,BlockState state){terrain.putIfAbsent(pos,site.level.getBlockState(pos));site.level.setBlock(pos,state,2);}
        java.util.stream.Stream<ItemEntity> items(){return loot.stream().filter(entity->entity instanceof ItemEntity&&!entity.isRemoved()).map(entity->(ItemEntity)entity);}
        java.util.stream.Stream<ItemEntity> innerEmeralds(Vec3 center){return items().filter(item->item.getItem().is(Items.EMERALD)&&item.position().distanceToSqr(center)<1);}
        int emeraldCount(Vec3 center){return innerEmeralds(center).mapToInt(item->item.getItem().getCount()).sum();}
        void container(BlockPos pos,BlockState state){block(pos,state);((Container)site.level.getBlockEntity(pos)).setItem(0,new ItemStack(Items.DIAMOND,23));}
        Mob victim(Vec3 pos){
            Mob mob=site.mob(EntityType.ZOMBIE);mob.moveTo(pos);site.level.getChunkAt(mob.blockPosition());site.level.addFreshEntity(mob);mob.setHealth(1);mob.tickCount=100;
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.GOLD_INGOT,3));mob.setDropChance(EquipmentSlot.MAINHAND,2F);
            mob.setLastHurtByPlayer(player);
            return mob;
        }
        @Override public void close(){
            if(closed)return;closed=true;
            terrain.forEach((pos,state)->{if(site.level.getBlockEntity(pos) instanceof Container container)container.clearContent();site.level.setBlock(pos,state,2);});
            loot.forEach(Entity::discard);NeoForge.EVENT_BUS.unregister(observeLoot);
            site.level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(ItemEntity::discard);site.level.getEntitiesOfClass(ExperienceOrb.class,bounds).forEach(ExperienceOrb::discard);
            var rules=site.level.getGameRules();rules.getRule(GameRules.RULE_MOBGRIEFING).set(griefing,site.level.getServer());rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(tileDrops,site.level.getServer());rules.getRule(GameRules.RULE_DOMOBLOOT).set(mobLoot,site.level.getServer());rules.getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(decay,site.level.getServer());site.close();releaseChunks.run();
        }
    }
}
