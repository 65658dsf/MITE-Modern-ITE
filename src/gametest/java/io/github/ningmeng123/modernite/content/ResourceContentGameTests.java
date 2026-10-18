package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.MobAbilities;
import io.github.ningmeng123.modernite.gear.EnhanceGemItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded recipes, resource reachability and actual ore/leaf drops. @author NingMeng123 */
@GameTestHolder("modern_ite") @PrefixGameTestTemplate(false)
public final class ResourceContentGameTests {
    private ResourceContentGameTests() {}
    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) throw new IllegalStateException("Missing registered material: " + id);
        return item;
    }
    private static ItemStack stack(String id) { return new ItemStack(item(id)); }
    private static void craft(GameTestHelper h, String recipe, String result, int count, String[] pattern, Map<Character,String> keys) {
        var ingredients = new ArrayList<ItemStack>();
        for (String row : pattern) for (char cell : row.toCharArray()) ingredients.add(cell == ' ' ? ItemStack.EMPTY : stack(keys.get(cell)));
        CraftingInput input = CraftingInput.of(pattern[0].length(), pattern.length, ingredients);
        var manager = h.getLevel().getRecipeManager();
        var authored = manager.byKey(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, recipe)).orElseThrow();
        h.assertTrue(authored.value() instanceof CraftingRecipe source && source.matches(input,h.getLevel()), "The authored source recipe really matches its full material grid: " + recipe);
        var selected = manager.getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
        ItemStack assembled = selected.value().assemble(input,h.getLevel().registryAccess());
        h.assertTrue(assembled.is(item(result)) && assembled.getCount() == count, "The actual RecipeManager yields the intended result: " + recipe);
    }
    private static void smelt(GameTestHelper h,String input,String result) {
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(stack(input)),h.getLevel()).orElseThrow();
        h.assertTrue(recipe.value().getResultItem(h.getLevel().registryAccess()).is(item(result)), "The real furnace recipe resolves " + input);
    }

    @GameTest(template="gear_empty")
    public static void loadedSourceCraftingRecipesMatchCoreClubFurnaceChestAndMapleInputs(GameTestHelper h) {
        for (String metal : new String[]{"copper","silver","gold","iron","ancient_metal","mithril","adamantium","vibranium"}) {
            String ns = Set.of("copper","gold","iron").contains(metal) ? "minecraft:" : "mite:";
            craft(h,"dynamic_core_"+metal,"modern_ite:dynamic_core_"+metal,1,new String[]{" I ","IGI","ITI"},
                    Map.of('I',ns+metal+"_ingot",'G',"minecraft:glass",'T',"minecraft:torch"));
        }
        for (String metal : new String[]{"copper","silver","gold","iron"}) {
            String ns = metal.equals("silver") ? "mite:" : "minecraft:";
            String nugget = metal.equals("copper") ? "mite:copper_nugget" : ns + metal + "_nugget";
            craft(h,"club_"+metal,"modern_ite:club_"+metal,1,new String[]{"#*#","#*#"," * "},Map.of('#',nugget,'*',ns+metal+"_ingot"));
        }
        String previous="iron";
        for (String metal : new String[]{"ancient_metal","mithril","adamantium","vibranium"}) {
            var keys=new java.util.HashMap<Character,String>();keys.put('#',"mite:"+metal+"_nugget");keys.put('*',"mite:"+metal+"_ingot");keys.put('A',"modern_ite:club_"+previous);keys.put('B',"modern_ite:voucher_club_core");
            craft(h,"club_"+metal,"modern_ite:club_"+metal,1,new String[]{"#*#","#*#",metal.equals("vibranium")?"BAB":" A "},keys);previous=metal;
        }
        craft(h,"vibranium_furnace","modern_ite:vibranium_furnace",1,new String[]{"VOA","DND","AOV"},
                Map.of('V',"mite:vibranium_ingot",'O',"minecraft:obsidian",'A',"mite:adamantium_ingot",'D',"minecraft:diamond",'N',"mite:netherrack_furnace"));
        craft(h,"vibranium_chest","modern_ite:vibranium_chest",1,new String[]{"VVV","V V","VVV"},Map.of('V',"mite:vibranium_ingot"));
        craft(h,"maple_planks","modern_ite:maple_planks",4,new String[]{"L"},Map.of('L',"modern_ite:maple_log"));
        craft(h,"maple_slab","modern_ite:maple_slab",6,new String[]{"PPP"},Map.of('P',"modern_ite:maple_planks"));
        craft(h,"maple_stairs","modern_ite:maple_stairs",4,new String[]{"P  ","PP ","PPP"},Map.of('P',"modern_ite:maple_planks"));
        craft(h,"voucher_club_core","modern_ite:voucher_club_core",1,new String[]{"FPV"},
                Map.of('F',"modern_ite:voucher_fishing",'P',"modern_ite:voucher_planting",'V',"modern_ite:voucher_villager"));
        smelt(h,"minecraft:coal_block","modern_ite:diamond_chunk");smelt(h,"modern_ite:maple_log","minecraft:charcoal");
        smelt(h,"modern_ite:nether_adamantium_ore","mite:adamantium_ingot");h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void bothNewOresEnforceSourceHarvestLevelFourThroughTheRealEventBus(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        for (var ore : new Block[]{ExtremeContent.RED_ORE.get(),ExtremeContent.NETHER_ADAMANTIUM.get()}) {
            var state=ore.defaultBlockState();
            h.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE)&&state.is(BlockTags.NEEDS_DIAMOND_TOOL),"New ore has its actual modern mining tags");
            for (String tool : new String[]{"minecraft:iron_pickaxe","mite:ancient_metal_pickaxe","mite:mithril_pickaxe","mite:adamantium_pickaxe","mite:vibranium_pickaxe","mite:mithril_war_hammer","mite:mithril_axe"}) {
                ItemStack held=stack(tool);var definition=MiteTools.definition(held);
                boolean qualifies=definition.harvestLevel()>=4&&(tool.endsWith("_pickaxe")||tool.endsWith("_war_hammer"));
                player.setItemInHand(InteractionHand.MAIN_HAND,held);
                var event=NeoForge.EVENT_BUS.post(new PlayerEvent.HarvestCheck(player,state,h.getLevel(),h.absolutePos(new BlockPos(1,1,1)),true));
                h.assertTrue(event.canHarvest()==qualifies,"Actual ore harvest gate matches source level4 and mining family: "+tool);
            }
        }h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void placedRedDiamondOreCannotMultiplyItsGemEvenWithFortuneTen(GameTestHelper h) {
        var enchants=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        ItemStack pick=stack("mite:mithril_pickaxe");pick.enchant(enchants.getHolderOrThrow(Enchantments.FORTUNE),10);
        BlockPos pos=h.absolutePos(new BlockPos(1,1,1));
        var natural=ExtremeContent.RED_ORE.get().defaultBlockState();
        var context=new BlockPlaceContext(h.getLevel(),h.makeMockPlayer(GameType.SURVIVAL),InteractionHand.MAIN_HAND,
                new ItemStack(ExtremeContent.RED_ORE.get()),new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        var placed=ExtremeContent.RED_ORE.get().getStateForPlacement(context);
        h.assertTrue(placed.getValue(RedDiamondOreBlock.PLACED)&&!natural.getValue(RedDiamondOreBlock.PLACED),"Actual player placement marks the ore while generated defaults remain natural");
        for (int i=0;i<24;i++) {
            var nativeDrops=Block.getDrops(natural,h.getLevel(),pos,null,null,pick);
            var placedDrops=Block.getDrops(placed,h.getLevel(),pos,null,null,pick);
            h.assertTrue(nativeDrops.size()==1&&nativeDrops.getFirst().is(ExtremeContent.FANCY_RED.get())&&nativeDrops.getFirst().getCount()==2,
                    "Natural source fortune10 guarantees two gems using its 10-percent-per-level curve");
            h.assertTrue(placedDrops.size()==1&&placedDrops.getFirst().is(ExtremeContent.FANCY_RED.get())&&placedDrops.getFirst().getCount()==1,
                    "A player-placed ore returns exactly its one original gem on every roll");
        }
        pick.enchant(enchants.getHolderOrThrow(Enchantments.SILK_TOUCH),1);
        var silk=Block.getDrops(placed,h.getLevel(),pos,null,null,pick);
        h.assertTrue(silk.size()==1&&silk.getFirst().is(ExtremeContent.RED_ORE.get().asItem())&&silk.getFirst().getCount()==1,"Silk touch still returns exactly one ore item");h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void everyNonCreativeItemHasARealRecipeOrAnExplicitRuntimeOrWorldRoute(GameTestHelper h) {
        Set<Item> results=new HashSet<>();
        for(var holder:h.getLevel().getRecipeManager().getRecipes()) {
            ItemStack result=holder.value().getResultItem(h.getLevel().registryAccess());if(!result.isEmpty())results.add(result.getItem());
            if(holder.id().getNamespace().equals(ModernIte.MOD_ID))for(var ingredient:holder.value().getIngredients())
                if(!ingredient.isEmpty())h.assertTrue(ingredient.getItems().length>0,"Loaded source recipe has a resolved item/tag ingredient: "+holder.id());
        }
        Set<String> worldOrRuntime=Set.of("fancy_red_ore","nether_adamantium_ore","maple_log","maple_leaves","maple_sapling",
                "voucher_fishing","voucher_planting","voucher_villager","crossbow","iron_bucket_helmet");
        for(var entry:BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation id=entry.getKey().location();Item item=entry.getValue();if(!id.getNamespace().equals(ModernIte.MOD_ID)||item instanceof SpawnEggItem)continue;
            h.assertTrue(results.contains(item)||worldOrRuntime.contains(id.getPath())||item instanceof EnhanceGemItem gem&&gem.rank==1,
                    "Every non-creative extension item has a loaded recipe or a specifically audited runtime route: "+id);
        }
        // These exceptions are backed by the actual loaded biome data, leaf loot and monster equipment.
        var forest=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.FOREST).value();
        boolean maple=forest.getGenerationSettings().features().stream().flatMap(set->set.stream())
                .anyMatch(holder->holder.unwrapKey().map(key->key.location().equals(ExtremeContent.id("maple"))).orElse(false));
        h.assertTrue(maple,"The maple placed feature is really added to forest generation");
        var underworld=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).get(ResourceLocation.fromNamespaceAndPath("mite","underworld"));
        var nether=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.NETHER_WASTES).value();
        h.assertTrue(underworld!=null&&underworld.getGenerationSettings().features().stream().flatMap(set->set.stream())
                .anyMatch(holder->holder.unwrapKey().map(key->key.location().equals(ExtremeContent.id("fancy_red_ore"))).orElse(false)),"Red diamond ore really enters the underworld's generation");
        h.assertTrue(nether.getGenerationSettings().features().stream().flatMap(set->set.stream())
                .anyMatch(holder->holder.unwrapKey().map(key->key.location().equals(ExtremeContent.id("nether_adamantium_ore"))).orElse(false)),"Nether adamantium ore really enters nether biome generation");
        h.assertTrue(stack("modern_ite:maple_log").is(ItemTags.LOGS)&&stack("modern_ite:maple_planks").is(ItemTags.PLANKS),"Maple enters existing wood crafting tags");
        var leaves=ExtremeContent.MAPLE_LEAVES.get().defaultBlockState();BlockPos pos=h.absolutePos(new BlockPos(1,1,1));int saplings=0;
        for(int i=0;i<400;i++)for(ItemStack drop:Block.getDrops(leaves,h.getLevel(),pos,null))if(drop.is(ExtremeContent.MAPLE_SAPLING.get().asItem()))saplings+=drop.getCount();
        h.assertTrue(saplings>0,"Actual natural maple-leaf loot can renew the tree from saplings");
        for(ExtremeKind kind:new ExtremeKind[]{ExtremeKind.PILLAGER,ExtremeKind.ZOMBIE_IRON_BUCKET}) {
            var mob=ExtremeMobs.type(kind).create(h.getLevel());MobAbilities.initialize(mob,(ExtremeMob)mob);
            boolean pillager=kind==ExtremeKind.PILLAGER;var slot=pillager?EquipmentSlot.MAINHAND:EquipmentSlot.HEAD;
            h.assertTrue(mob.getItemBySlot(slot).is(pillager?ExtremeContent.CROSSBOW.get():ExtremeContent.BUCKET_HELMET.get()),"Non-craftable equipment is really equipped by its natural source creature");
            var saved=new CompoundTag();mob.saveWithoutId(saved);
            h.assertTrue(saved.getList(pillager?"HandDropChances":"ArmorDropChances",Tag.TAG_FLOAT).getFloat(pillager?0:3)>0,"Source creature retains a positive natural equipment drop chance");mob.discard();
        }h.succeed();
    }
}
