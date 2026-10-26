package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.registry.MiteFurnaces;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.content.metal.MetalUpgradingContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Preserve actual storage inventories through the real queued infernal explosion. @author NingMeng123 */
@GameTestHolder("modern_ite_feedback")
@PrefixGameTestTemplate(false)
public final class InfernalContainerDropsGameTests {
    private InfernalContainerDropsGameTests() {}

    @GameTest(template="gear_empty",batch="infernal_container_matrix",timeoutTicks=1200)
    public static void infernalExplosionPreservesEveryStoredStackAcrossContainerFamilies(GameTestHelper h) {
        var fixture=new Fixture(h);
        try {
            var states=new ArrayList<>(List.of(Blocks.CHEST.defaultBlockState(),Blocks.BARREL.defaultBlockState(),
                    Blocks.FURNACE.defaultBlockState(),Blocks.BLAST_FURNACE.defaultBlockState(),Blocks.SMOKER.defaultBlockState(),
                    Blocks.DISPENSER.defaultBlockState(),Blocks.DROPPER.defaultBlockState(),Blocks.HOPPER.defaultBlockState(),
                    ExtremeContent.VIBRANIUM_CHEST.get().defaultBlockState(),ExtremeContent.VIBRANIUM_FURNACE.get().defaultBlockState(),
                    MetalUpgradingContent.FURNACE.get().defaultBlockState()));
            MiteFurnaces.ALL.forEach(block->states.add(block.get().defaultBlockState()));
            states.add(Blocks.SHULKER_BOX.defaultBlockState());states.add(Blocks.PURPLE_SHULKER_BOX.defaultBlockState());
            var targets=new ArrayList<BlockPos>();var expected=new ArrayList<ItemStack>();
            for(int index=0;index<states.size();index++) {
                BlockPos pos=fixture.origin.offset(-24+(index%7)*8,0,-12+(index/7)*8);
                fixture.container(h,pos,states.get(index),"Container "+index,18+(index%12),expected);targets.add(pos);
            }
            BlockPos left=fixture.origin.offset(-2,0,15),right=left.east();
            fixture.container(h,left,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.LEFT),"Double left",27,expected);
            fixture.container(h,right,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.RIGHT),"Double right",28,expected);
            targets.add(left);targets.add(right);
            h.assertTrue(fixture.level.getBlockState(left).getValue(ChestBlock.TYPE)==ChestType.LEFT
                    &&fixture.level.getBlockState(right).getValue(ChestBlock.TYPE)==ChestType.RIGHT,"The storage matrix contains an actual paired double chest");
            BlockPos stone=fixture.origin.offset(0,-1,0),ender=fixture.origin.offset(4,0,16);
            fixture.block(stone,Blocks.STONE.defaultBlockState());fixture.block(ender,Blocks.ENDER_CHEST.defaultBlockState());
            var owner=h.makeMockPlayer(GameType.SURVIVAL);ItemStack personal=named(Items.DIAMOND,31,"Personal ender inventory");
            owner.getEnderChestInventory().setItem(0,personal.copy());
            h.assertTrue(ItemStack.matches(owner.getEnderChestInventory().getItem(0),personal),"The ender inventory fixture accepts every expected diamond before detonation");
            fixture.explode();
            InfernalExplosionGameTestSupport.finishAfterJobs(h,fixture.level,()->{
                for(BlockPos pos:targets)h.assertTrue(fixture.level.getBlockState(pos).isAir(),"The actual explosion destroys the storage block at "+pos.subtract(fixture.origin));
                h.assertTrue(fixture.level.getBlockState(stone).isAir(),"Ordinary terrain is still physically destroyed");
                assertStoredStacks(h,fixture.items,expected);
                h.assertTrue(fixture.items.stream().noneMatch(item->item.getItem().is(Items.STONE)||item.getItem().is(Items.COBBLESTONE)),"Ordinary terrain produces no item entities");
                h.assertTrue(ItemStack.matches(owner.getEnderChestInventory().getItem(0),personal),"Destroying an ender chest never drains its owner's separate personal inventory");
                h.assertTrue(flatten(fixture.items).stream().noneMatch(stack->ItemStack.isSameItemSameComponents(stack,personal)),"The personal ender inventory is never duplicated as a physical block drop");
                owner.discard();
            },fixture::close);
        }catch(Throwable failure){fixture.close();throw failure;}
    }

    @GameTest(template="gear_empty",batch="infernal_container_protection",timeoutTicks=1200)
    public static void protectedAndReplacedQueuedContainersKeepTheirCurrentInventories(GameTestHelper h) {
        var fixture=new Fixture(h);
        try {
            var unused=new ArrayList<ItemStack>();
            BlockPos protectedPos=fixture.origin.offset(4,0,0),changedPos=fixture.origin.offset(6,0,0),sameTypePos=fixture.origin.offset(10,0,0);
            fixture.container(h,protectedPos,Blocks.CHEST.defaultBlockState(),"Protected chest",23,unused);
            fixture.container(h,changedPos,Blocks.BARREL.defaultBlockState(),"Original barrel",17,unused);
            fixture.container(h,sameTypePos,Blocks.BARREL.defaultBlockState(),"Same-type original barrel",19,unused);
            var protectedContents=copyInventory((Container)fixture.level.getBlockEntity(protectedPos));
            var originalEntity=fixture.level.getBlockEntity(sameTypePos);var originalState=fixture.level.getBlockState(sameTypePos);
            Consumer<ExplosionEvent.Detonate> protect=event->{if(event.getExplosion().getDirectSourceEntity()==fixture.source)event.getAffectedBlocks().remove(protectedPos);};
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ExplosionEvent.Detonate.class,protect);
            try{fixture.explode();}finally{NeoForge.EVENT_BUS.unregister(protect);}
            ((Container)fixture.level.getBlockEntity(changedPos)).clearContent();
            fixture.block(changedPos,Blocks.DROPPER.defaultBlockState());
            ItemStack replacement=named(Items.EMERALD,29,"Queued replacement inventory");
            ((Container)fixture.level.getBlockEntity(changedPos)).setItem(0,replacement.copy());
            ((Container)fixture.level.getBlockEntity(sameTypePos)).clearContent();
            fixture.level.setBlock(sameTypePos,Blocks.AIR.defaultBlockState(),2);
            fixture.block(sameTypePos,Blocks.BARREL.defaultBlockState());
            ItemStack sameTypeReplacement=named(Items.DIAMOND,29,"Same-state replacement inventory");
            ((Container)fixture.level.getBlockEntity(sameTypePos)).setItem(0,sameTypeReplacement.copy());
            var replacementEntity=fixture.level.getBlockEntity(sameTypePos);
            h.assertTrue(fixture.level.getBlockState(sameTypePos)==originalState&&fixture.level.getBlockEntity(sameTypePos)!=originalEntity,"The replacement barrel has the identical block state but a new physical inventory entity");
            h.assertTrue(ItemStack.matches(((Container)replacementEntity).getItem(0),sameTypeReplacement),"The replacement barrel accepts its exact expected inventory before the queued blast resumes; maxStack="+sameTypeReplacement.getMaxStackSize()+", actual="+((Container)replacementEntity).getItem(0));
            InfernalExplosionGameTestSupport.finishAfterJobs(h,fixture.level,()->{
                h.assertTrue(fixture.level.getBlockState(protectedPos).is(Blocks.CHEST),"A protection listener can exclude the real container before queuing");
                assertInventory(h,(Container)fixture.level.getBlockEntity(protectedPos),protectedContents);
                h.assertTrue(fixture.level.getBlockState(changedPos).is(Blocks.DROPPER),"Queued destruction skips a different storage block placed after selection");
                h.assertTrue(ItemStack.matches(((Container)fixture.level.getBlockEntity(changedPos)).getItem(0),replacement),"The replacement inventory remains inside its live container");
                h.assertTrue(fixture.level.getBlockState(sameTypePos)==originalState&&fixture.level.getBlockEntity(sameTypePos)==replacementEntity
                        &&fixture.level.getBlockEntity(sameTypePos) instanceof Container current
                        &&ItemStack.matches(current.getItem(0),sameTypeReplacement),"An identically typed and shaped replacement container keeps its new inventory instead of inheriting the old blast target; state="+fixture.level.getBlockState(sameTypePos)+", sameNewEntity="+(fixture.level.getBlockEntity(sameTypePos)==replacementEntity)+", sameOldEntity="+(fixture.level.getBlockEntity(sameTypePos)==originalEntity)+", actual="+(fixture.level.getBlockEntity(sameTypePos) instanceof Container inventory?inventory.getItem(0):"missing"));
                h.assertTrue(fixture.items.isEmpty(),"Neither protected nor skipped containers release duplicate inventory drops");
            },fixture::close);
        }catch(Throwable failure){fixture.close();throw failure;}
    }

    @GameTest(template="gear_empty",batch="infernal_container_griefing",timeoutTicks=1200)
    public static void disabledMobGriefingKeepsStorageAndEveryInventoryStack(GameTestHelper h) {
        var fixture=new Fixture(h);
        try {
            BlockPos pos=fixture.origin.offset(4,0,0);var unused=new ArrayList<ItemStack>();
            fixture.container(h,pos,Blocks.DISPENSER.defaultBlockState(),"Undamaged dispenser",31,unused);
            var before=copyInventory((Container)fixture.level.getBlockEntity(pos));
            fixture.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,fixture.level.getServer());
            fixture.explode();
            InfernalExplosionGameTestSupport.finishAfterJobs(h,fixture.level,()->{
                h.assertTrue(fixture.level.getBlockState(pos).is(Blocks.DISPENSER),"Disabled mobGriefing prevents storage block destruction");
                assertInventory(h,(Container)fixture.level.getBlockEntity(pos),before);
                h.assertTrue(fixture.items.isEmpty(),"An intact dispenser does not emit any inventory or block drops");
            },fixture::close);
        }catch(Throwable failure){fixture.close();throw failure;}
    }

    @GameTest(template="gear_empty",batch="infernal_container_trial",timeoutTicks=1200)
    public static void naturalTrialStorageSuppressionIsRetainedBesideOrdinaryStorageAndDecoratedPots(GameTestHelper h) {
        var fixture=new Fixture(h);
        try {
            BlockPos natural=fixture.origin.offset(4,0,0),ordinary=fixture.origin.offset(6,0,0),pot=fixture.origin.offset(8,0,0);
            var forbidden=new ArrayList<ItemStack>();var expected=new ArrayList<ItemStack>();
            fixture.container(h,natural,Blocks.CHEST.defaultBlockState(),"Natural trial chest",23,forbidden);
            fixture.container(h,ordinary,Blocks.BARREL.defaultBlockState(),"Nearby ordinary barrel",27,expected);
            fixture.block(pot,Blocks.DECORATED_POT.defaultBlockState());
            h.assertTrue(fixture.level.getBlockEntity(pot) instanceof Container,"The decorated pot exposes its actual single-item storage");
            ItemStack pottery=named(Items.EMERALD,13,"Natural decorated pot contents");
            ((Container)fixture.level.getBlockEntity(pot)).setItem(0,pottery.copy());expected.add(pottery);
            NaturalTrialBlocks.record(fixture.level.getChunkAt(natural),natural);
            NaturalTrialBlocks.record(fixture.level.getChunkAt(pot),pot);
            h.assertTrue(NaturalTrialBlocks.isNatural(fixture.level,natural)&&NaturalTrialBlocks.isNatural(fixture.level,pot)
                    &&!NaturalTrialBlocks.isNatural(fixture.level,ordinary),"Only the marked fixtures have real natural trial provenance");
            fixture.explode();
            InfernalExplosionGameTestSupport.finishAfterJobs(h,fixture.level,()->{
                h.assertTrue(fixture.level.getBlockState(natural).isAir()&&fixture.level.getBlockState(ordinary).isAir()&&fixture.level.getBlockState(pot).isAir(),"The blast really destroys natural storage, ordinary storage and the decorated pot");
                assertStoredStacks(h,fixture.items,expected);
                List<ItemStack> actual=flatten(fixture.items);
                for(ItemStack denied:forbidden)h.assertTrue(actual.stream().noneMatch(stack->ItemStack.isSameItemSameComponents(stack,denied)),"Dedicated container recovery cannot bypass natural trial storage suppression: "+denied);
            },fixture::close);
        }catch(Throwable failure){fixture.close();throw failure;}
    }

    @GameTest(template="gear_empty",batch="infernal_container_tile_drops",timeoutTicks=1200)
    public static void disabledTileDropsStillReleasesStoredContentsWithoutTerrainOrContainerMaterials(GameTestHelper h) {
        var fixture=new Fixture(h);
        try {
            var expected=new ArrayList<ItemStack>();
            BlockPos dispenser=fixture.origin.offset(4,0,0),furnace=fixture.origin.offset(6,0,0),stone=fixture.origin.below();
            fixture.container(h,dispenser,Blocks.DISPENSER.defaultBlockState(),"Tile drops disabled dispenser",29,expected);
            fixture.container(h,furnace,Blocks.FURNACE.defaultBlockState(),"Tile drops disabled furnace",31,expected);
            fixture.block(stone,Blocks.STONE.defaultBlockState());
            fixture.level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false,fixture.level.getServer());
            fixture.explode();
            InfernalExplosionGameTestSupport.finishAfterJobs(h,fixture.level,()->{
                h.assertTrue(fixture.level.getBlockState(dispenser).isAir()&&fixture.level.getBlockState(furnace).isAir()&&fixture.level.getBlockState(stone).isAir(),"Disabled tile drops does not prevent actual mob block destruction");
                assertStoredStacks(h,fixture.items,expected);
                h.assertTrue(fixture.items.stream().allMatch(entity->expected.stream().anyMatch(stack->ItemStack.isSameItemSameComponents(entity.getItem(),stack))),"Only physical stored stacks are released, without terrain, container bodies or furnace material drops");
                h.assertFalse(fixture.level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS),"Container recovery leaves the disabled global tile-drop rule unchanged");
            },fixture::close);
        }catch(Throwable failure){fixture.close();throw failure;}
    }

    private static ItemStack named(net.minecraft.world.item.Item item,int count,String name) {
        ItemStack stack=new ItemStack(item,count);stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));return stack;
    }
    private static List<ItemStack> copyInventory(Container container) {
        var copy=new ArrayList<ItemStack>();for(int slot=0;slot<container.getContainerSize();slot++)copy.add(container.getItem(slot).copy());return copy;
    }
    private static void assertInventory(GameTestHelper h,Container container,List<ItemStack> expected) {
        for(int slot=0;slot<expected.size();slot++)h.assertTrue(ItemStack.matches(container.getItem(slot),expected.get(slot)),"Unbroken storage preserves exact item data in slot "+slot);
    }
    private static List<ItemStack> flatten(List<ItemEntity> items) {
        var stacks=new ArrayList<ItemStack>();
        for(ItemEntity entity:items)if(!entity.isRemoved())flatten(entity.getItem(),stacks);
        return stacks;
    }
    private static void flatten(ItemStack stack,List<ItemStack> stacks) {
        stacks.add(stack);var contents=stack.get(DataComponents.CONTAINER);
        if(contents!=null)contents.nonEmptyStream().forEach(child->flatten(child,stacks));
    }
    private static void assertStoredStacks(GameTestHelper h,List<ItemEntity> items,List<ItemStack> expected) {
        List<ItemStack> actual=flatten(items);
        for(ItemStack stack:expected) {
            int count=actual.stream().filter(found->ItemStack.isSameItemSameComponents(found,stack)).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(count==stack.getCount(),"Container item count and components survive exactly once: "+stack+", expected="+stack.getCount()+", actual="+count);
        }
        int expectedNamed=expected.stream().mapToInt(ItemStack::getCount).sum();
        int actualNamed=actual.stream().filter(stack->expected.stream().anyMatch(wanted->ItemStack.isSameItemSameComponents(wanted,stack))).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(expectedNamed==actualNamed,"All named stored items survive without loss or duplication, loose or inside dropped shulker boxes");
    }

    private static final class Fixture implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final net.minecraft.server.level.ServerLevel level;
        final BlockPos origin;
        final Creeper source;
        final AABB bounds;
        final Runnable releaseChunks;
        final List<ItemEntity> items=new ArrayList<>();
        final LinkedHashMap<BlockPos,BlockState> terrain=new LinkedHashMap<>();
        final Consumer<EntityJoinLevelEvent> observe;
        final boolean oldGrief,oldDrops,oldDecay;
        boolean closed;
        Fixture(GameTestHelper h) {
            site=new SpawnBalanceGameTests.Site(h);site.date(256,256,true);level=site.level;origin=BlockPos.containing(site.origin);
            bounds=new AABB(site.origin,site.origin).inflate(44);releaseChunks=InfernalExplosionGameTestSupport.holdChunks(level,bounds);
            source=(Creeper)site.mob(ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER));
            var rules=level.getGameRules();oldGrief=rules.getBoolean(GameRules.RULE_MOBGRIEFING);oldDrops=rules.getBoolean(GameRules.RULE_DOBLOCKDROPS);oldDecay=rules.getBoolean(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY);
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(true,level.getServer());rules.getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(true,level.getServer());
            observe=event->{if(!event.isCanceled()&&event.getLevel()==level&&event.getEntity() instanceof ItemEntity item&&bounds.contains(item.position()))items.add(item);};
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityJoinLevelEvent.class,observe);
        }
        void block(BlockPos pos,BlockState state) {terrain.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,state,2);}
        void container(GameTestHelper h,BlockPos pos,BlockState state,String label,int count,List<ItemStack> expected) {
            block(pos,state);h.assertTrue(level.getBlockEntity(pos) instanceof Container,"Registered storage entity exposes a real inventory: "+label+" / "+state);
            Container container=(Container)level.getBlockEntity(pos);
            ItemStack diamonds=named(Items.DIAMOND,count,label+" diamonds");
            ItemStack helmet=named(Items.IRON_HELMET,1,label+" helmet");helmet.setDamageValue(37);
            helmet.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),5);
            container.setItem(0,diamonds.copy());container.setItem(container.getContainerSize()-1,helmet.copy());
            h.assertTrue(ItemStack.matches(container.getItem(0),diamonds)&&ItemStack.matches(container.getItem(container.getContainerSize()-1),helmet),
                    "The storage fixture accepts both exact expected stacks before the explosion: "+label+", maxStack="+diamonds.getMaxStackSize()+", actual="+container.getItem(0));
            expected.add(diamonds);expected.add(helmet);
        }
        void explode() {level.explode(source,source.getX(),source.getY(),source.getZ(),3F,Level.ExplosionInteraction.MOB);}
        @Override public void close() {
            if(closed)return;closed=true;
            terrain.forEach((pos,state)->{if(level.getBlockEntity(pos) instanceof Container container)container.clearContent();level.setBlock(pos,state,2);});
            items.forEach(Entity::discard);NeoForge.EVENT_BUS.unregister(observe);
            level.getEntitiesOfClass(ItemEntity.class,bounds).forEach(Entity::discard);
            var rules=level.getGameRules();rules.getRule(GameRules.RULE_MOBGRIEFING).set(oldGrief,level.getServer());rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(oldDrops,level.getServer());rules.getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(oldDecay,level.getServer());
            site.close();releaseChunks.run();
        }
    }
}
