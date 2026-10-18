package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.furnace.FurnaceThermalMenuAccess;
import io.github.ningmeng123.mite.integration.jei.FurnaceJeiRecipe;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipe;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.client.gear.GearStationScreen;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.ForgeRecipes;
import io.github.ningmeng123.modernite.gear.GearStationMenu;
import io.github.ningmeng123.modernite.gear.GearMath;
import io.github.ningmeng123.modernite.integration.jei.ForgeJeiRecipe;
import io.github.ningmeng123.modernite.integration.jei.ForgeJeiRecipes;
import io.github.ningmeng123.modernite.integration.jei.VibraniumFurnaceJei;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in real optional JEI runtime, native screens and sidebar audit. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT)
public final class ForgeJeiClientAudit {
    private static boolean baked,started,finished,startupReady;
    private static int waiting,readyTicks,firstServerTick=-1;
    private static String worldName;
    private static WaitingScreen waitingScreen;
    private static Steps steps;
    private static JsonObject report;
    private ForgeJeiClientAudit() {}

    @EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event){if(enabled())baked=true;}
    }
    private static boolean enabled(){return Boolean.getBoolean("modern_ite.jeiAudit");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!enabled()||finished||!baked)return;Minecraft mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try {
            if(!started) {
                if(mc.level!=null||mc.getConnection()!=null||mc.getSingleplayerServer()!=null) {
                    report=initial();report.getAsJsonArray("failures").add("Refused existing world or connection");report.addProperty("status","aborted_existing_world");
                    report.addProperty("safe_client_shutdown_requested",false);finished=true;write();return;
                }
                if(mc.screen instanceof AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
                if(!(mc.screen instanceof TitleScreen))return;
                started=true;report=initial();
                if(!ModList.get().isLoaded("jei")) {
                    report.addProperty("optional_jei_absent_startup_succeeded",true);finish(mc);return;
                }
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
                report.addProperty("audit_render_distance",mc.options.renderDistance().get());report.addProperty("audit_simulation_distance",mc.options.simulationDistance().get());
                worldName="modern-ite-jei-audit-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8);report.addProperty("disposable_world",worldName);
                mc.createWorldOpenFlows().createFreshLevel(worldName,new LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(183994862L,false,false),registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            check(++waiting<3600,"Timed out waiting for the dedicated world and actual JEI runtime");
            if(mc.level==null||mc.player==null||mc.getConnection()==null||mc.getSingleplayerServer()==null)return;
            check(owned(mc),"Only the newly created JEI audit world may be used");
            if(firstServerTick<0)firstServerTick=mc.getSingleplayerServer().getTickCount();readyTicks++;
            if(waitingScreen==null){waitingScreen=new WaitingScreen();mc.setScreen(waitingScreen);}
            if(!startupReady&&readyTicks>=100&&mc.getSingleplayerServer().getTickCount()-firstServerTick>=100){
                startupReady=true;report.addProperty("server_ready_ticks_before_audit",mc.getSingleplayerServer().getTickCount()-firstServerTick);report.addProperty("client_ready_ticks_before_audit",readyTicks);
            }
            if(!startupReady)return;
            if(steps==null) {
                // Keep optional implementation classes out of the outer subscriber's method signatures.
                Object optional=Class.forName("mezz.jei.common.Internal").getMethod("getOptionalJeiRuntime").invoke(null);
                if(!(optional instanceof Optional<?> available)||available.isEmpty())return;
                steps=new Steps(mc,available.get());
            }
            steps.tick(mc);
        }catch(Throwable failure){fail("JEI client lifecycle",failure);finish(mc);}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!enabled()||finished||steps==null)return;
        try{steps.frame(Minecraft.getInstance());}catch(Throwable failure){fail("actual JEI recipe screen",failure);finish(Minecraft.getInstance());}
    }

    /** Loaded only after the optional JEI mod and its actual runtime are present. */
    private static final class Steps {
        private final IJeiRuntime runtime;
        private final RecipeType<ForgeJeiRecipe> type;
        private final IRecipeCategory<ForgeJeiRecipe> category;
        private final Map<Integer,List<ForgeJeiRecipe>> rows=new LinkedHashMap<>();
        private final Map<String,ForgeJeiRecipes.ToolStats> expectedStats=new HashMap<>();
        private final List<ForgeJeiRecipe> allRows;
        private Screen expectedScreen;
        private int phase=-2,frames;
        private GearStationScreen forgeScreen;
        private List<ForgeJeiRecipe> pagingRows;
        private Set<String> firstPageKeys,firstPageTables;
        private RecipeType<FurnaceJeiRecipe> furnaceType;
        private IRecipeCategory<FurnaceJeiRecipe> furnaceCategory;
        private List<FurnaceJeiRecipe> furnaceRows;
        private FurnaceJeiRecipe heatFiveRow;
        private JsonObject furnaceFacts;
        private FurnaceScreen furnaceScreen;
        private IRecipeCategory<RecipeHolder<SmithingRecipe>> smithingCategory;
        private RecipeHolder<SmithingRecipe> clubSmithingTarget;
        private JsonObject clubSmithingFacts;
        private record ClubRoute(String from,String to) {
            ResourceLocation base(){return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"club_"+from);}
            ResourceLocation addition(){return ResourceLocation.fromNamespaceAndPath(to.equals("iron")?"minecraft":"mite",to+"_ingot");}
            ResourceLocation result(){return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"club_"+to);}
        }
        private static final List<ClubRoute> CLUB_ROUTES=List.of(new ClubRoute("copper","iron"),new ClubRoute("silver","iron"),
                new ClubRoute("gold","iron"),new ClubRoute("iron","ancient_metal"),new ClubRoute("ancient_metal","mithril"),
                new ClubRoute("mithril","adamantium"),new ClubRoute("adamantium","vibranium"));
        @SuppressWarnings("unchecked") Steps(Minecraft mc,Object actualRuntime) {
            runtime=(IJeiRuntime)actualRuntime;var manager=runtime.getRecipeManager();JsonArray tierReports=new JsonArray();report.add("tiers",tierReports);
            check(ForgeJeiRecipes.profiles().keySet().equals(Set.of(0,1,2,3)),"Public table profiles contain exactly four physical tiers");
            for(ToolFamily family:List.of(ToolFamily.AXE,ToolFamily.WAR_HAMMER))check(itemIds(ForgeJeiRecipes.tools(family)).equals(legalToolIds(family)),"Public tool alternatives equal the real station's accepted damageable family");
            type=(RecipeType<ForgeJeiRecipe>)manager.getRecipeType(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"forging")).orElseThrow();
            category=manager.getRecipeCategory(type);check(category!=null&&category.getRecipeType().equals(type),"The actual plugin registers the one strengthening category");
            for(int tier=0;tier<4;tier++)check(manager.getRecipeType(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"forging_tier_"+tier)).isEmpty(),"Tier-specific strengthening recipe types must no longer exist");
            long categories=manager.createRecipeCategoryLookup().includeHidden().get().filter(value->value.getRecipeType().getRecipeClass()==ForgeJeiRecipe.class).count();
            check(categories==1,"The actual JEI registry contains exactly one strengthening tab for every material tier");
            check(manager.createRecipeCatalystLookup(type).includeHidden().getItemStack().findAny().isEmpty(),"No global table catalyst may bypass the per-recipe tier lookup");
            allRows=manager.createRecipeLookup(type).includeHidden().get().toList();check(!allRows.isEmpty(),"The single category has actual strengthening recipes");
            Set<String> expected=expectedRecipeKeys();Set<String> actual=new HashSet<>();Set<String> index=new HashSet<>();
            for(var recipe:ForgeJeiRecipes.build())check(index.add(key(recipe)),"Pure source index must not duplicate a gear/grade/stone variant");
            for(var recipe:allRows){check(actual.add(key(recipe)),"Actual JEI runtime must not duplicate gear/grade/stone variant: "+key(recipe));validateRecipe(recipe);}
            int layouts=0;
            for(int tier=0;tier<4;tier++) {
                int minimum=tier;List<ForgeJeiRecipe> registered=allRows.stream().filter(recipe->recipe.minimumTier()==minimum).toList();rows.put(tier,registered);
                check(!registered.isEmpty(),"Each physical minimum tier has recipes inside the shared category");
                JsonObject facts=new JsonObject();facts.addProperty("tier",tier);facts.addProperty("type",type.getUid().toString());facts.addProperty("recipes",registered.size());facts.add("expected_recipe_tables",json(expectedTables(tier)));tierReports.add(facts);
                // Exercise actual JEI layout building at every grade and both stone variants per tier.
                Set<String> sampled=new HashSet<>();
                for(var row:registered)if(sampled.add(row.inputGrade()+"/"+row.universal())){validateLayout(category,row);layouts++;}
            }
            check(actual.equals(expected),"Real JEI runtime covers every server-forgeable gear item, every grade step, and both normal/universal variants");
            check(index.equals(expected),"Public source index agrees with the independently enumerated server gear/grade matrix");
            report.addProperty("registered_recipe_count",actual.size());report.addProperty("all_gear_grade_variants_verified",true);report.addProperty("native_layouts_validated",layouts);report.addProperty("registered_forging_categories",categories);report.addProperty("shared_type",type.getUid().toString());report.addProperty("global_table_catalysts",0);
            // One category requires row-level invisible catalysts; category filtering cannot enforce capacity.
            JsonArray uses=new JsonArray();report.add("table_uses",uses);
            for(int table=0;table<4;table++) {
                var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.CATALYST,VanillaTypes.ITEM_STACK,new ItemStack(ExtremeGear.FORGES.get(table).get()));
                Set<String> found=recipeKeys(manager.createRecipeLookup(type).includeHidden().limitFocus(List.of(focus)).get().toList());
                int capacity=table;Set<String> allowed=recipeKeys(allRows.stream().filter(row->row.minimumTier()<=capacity).toList());
                check(found.equals(allowed),"Real table U lookup returns exactly its allowed rows through invisible per-recipe catalysts");
                JsonObject facts=new JsonObject();facts.addProperty("table_tier",table);facts.addProperty("allowed_recipe_rows",found.size());facts.addProperty("row_filter_verified",true);uses.add(facts);
            }
            validateFurnaceRuntime();
            validateClubSmithingRuntime(mc);
        }
        @SuppressWarnings("unchecked")
        private void validateClubSmithingRuntime(Minecraft mc) {
            clubSmithingFacts=new JsonObject();report.add("club_smithing",clubSmithingFacts);
            clubSmithingFacts.addProperty("status","runtime_verified");
            var manager=runtime.getRecipeManager();
            smithingCategory=manager.getRecipeCategory(RecipeTypes.SMITHING);
            check(smithingCategory!=null,"Native JEI smithing category exists for material-template upgrades");
            var loaded=mc.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMITHING).stream()
                    .filter(holder->isClub(holder.value().getResultItem(mc.level.registryAccess()))).toList();
            check(loaded.size()==7,"The actual synchronized RecipeManager loads exactly seven addon club smithing routes");
            check(loaded.stream().allMatch(holder->holder.value() instanceof SmithingTransformRecipe),
                    "All seven club upgrades use native SmithingTransformRecipe holders");
            var indexed=manager.createRecipeLookup(RecipeTypes.SMITHING).includeHidden().get()
                    .filter(holder->isClub(holder.value().getResultItem(mc.level.registryAccess()))).toList();
            Set<ResourceLocation> loadedIds=new HashSet<>(),indexedIds=new HashSet<>();
            for(var holder:loaded)check(loadedIds.add(holder.id()),"Loaded club smithing recipe IDs are unique");
            for(var holder:indexed)check(indexedIds.add(holder.id()),"Native JEI indexes each club smithing holder once");
            check(indexed.size()==7&&indexedIds.equals(loadedIds),"Native RecipeTypes.SMITHING lookup contains all seven real addon holders");
            JsonArray routes=new JsonArray();clubSmithingFacts.add("seven_routes",routes);
            Set<ResourceLocation> matchedIds=new HashSet<>();int layouts=0;
            for(ClubRoute route:CLUB_ROUTES) {
                var input=clubSmithingInput(route);
                var matches=loaded.stream().filter(holder->holder.value().matches(input,mc.level)).toList();
                check(matches.size()==1,"Exactly one loaded club upgrade matches "+route.from()+" -> "+route.to());
                RecipeHolder<SmithingRecipe> holder=matches.getFirst();
                check(matchedIds.add(holder.id()),"Each expected club route matches its own native recipe holder");
                ItemStack output=holder.value().assemble(input,mc.level.registryAccess());
                check(output.is(requiredStack(route.result()).getItem())&&output.getCount()==1,
                        "The source template, base club and single target ingot produce the exact next-material club");
                check(indexed.stream().anyMatch(actual->actual.id().equals(holder.id())),"Every expected club route is present in native JEI");
                var layout=manager.createRecipeLayoutDrawable(smithingCategory,holder,
                        runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                check(layout.getRecipe()==holder&&layout.getRecipeCategory()==smithingCategory,
                        "Native JEI builds the exact synchronized smithing holder, without a custom category");
                validateClubSmithingSlots(layout,route,output);layouts++;
                var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,output);
                check(manager.createRecipeLookup(RecipeTypes.SMITHING).includeHidden().limitFocus(List.of(focus)).get()
                        .anyMatch(actual->actual.id().equals(holder.id())),"Native output R lookup locates this club material upgrade");
                JsonObject facts=new JsonObject();facts.addProperty("from",route.from());facts.addProperty("to",route.to());
                facts.addProperty("recipe",holder.id().toString());facts.addProperty("template","mite:equipment_upgrade_smithing_template");
                facts.addProperty("base",route.base().toString());facts.addProperty("addition",route.addition().toString());
                facts.addProperty("output",route.result().toString());facts.addProperty("native_layout_verified",true);
                facts.addProperty("output_components_match_assembly",true);routes.add(facts);
                if(route.from().equals("adamantium")&&route.to().equals("vibranium"))clubSmithingTarget=holder;
            }
            check(matchedIds.equals(loadedIds)&&clubSmithingTarget!=null,"The seven exact routes cover the complete loaded club-upgrade catalog");
            var catalysts=manager.createRecipeCatalystLookup(RecipeTypes.SMITHING).includeHidden().getItemStack().toList();
            check(catalysts.size()==1&&catalysts.getFirst().is(Items.SMITHING_TABLE),
                    "The native smithing category exposes its ordinary smithing-table catalyst");
            long obsoleteLoaded=mc.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
                    .filter(holder->isAdvancedClub(holder.value().getResultItem(mc.level.registryAccess()))).count();
            long obsoleteNative=manager.createRecipeLookup(RecipeTypes.CRAFTING).includeHidden().get()
                    .filter(holder->isAdvancedClub(holder.value().getResultItem(mc.level.registryAccess()))).count();
            long obsoleteWorkbenches=0;
            for(var candidate:manager.createRecipeCategoryLookup().includeHidden().get().toList()) {
                if(candidate.getRecipeType().getRecipeClass()!=WorkbenchJeiRecipe.class)continue;
                var workbenchType=(RecipeType<WorkbenchJeiRecipe>)candidate.getRecipeType();
                obsoleteWorkbenches+=manager.createRecipeLookup(workbenchType).includeHidden().get()
                        .filter(row->isAdvancedClub(row.output())).count();
            }
            check(obsoleteLoaded==0&&obsoleteNative==0&&obsoleteWorkbenches==0,
                    "The four former advanced-club crafting upgrades are absent from loaded recipes and every native/workbench JEI view");
            clubSmithingFacts.addProperty("type",RecipeTypes.SMITHING.getUid().toString());clubSmithingFacts.addProperty("recipes",loaded.size());
            clubSmithingFacts.addProperty("seven_routes_verified",true);clubSmithingFacts.addProperty("native_recipe_lookup_verified",true);
            clubSmithingFacts.addProperty("native_output_lookup_verified",true);clubSmithingFacts.addProperty("native_layouts_validated",layouts);
            clubSmithingFacts.addProperty("native_outputs_match_real_assembly",true);
            clubSmithingFacts.add("public_catalysts",json(itemIds(catalysts)));
            clubSmithingFacts.addProperty("legacy_advanced_crafting_recipes",obsoleteLoaded);
            clubSmithingFacts.addProperty("legacy_advanced_native_crafting_rows",obsoleteNative);
            clubSmithingFacts.addProperty("legacy_advanced_workbench_rows",obsoleteWorkbenches);
        }
        private static boolean isClub(ItemStack stack) {
            var id=BuiltInRegistries.ITEM.getKey(stack.getItem());return id.getNamespace().equals(ModernIte.MOD_ID)&&id.getPath().startsWith("club_");
        }
        private static boolean isAdvancedClub(ItemStack stack) {
            var id=BuiltInRegistries.ITEM.getKey(stack.getItem());return id.getNamespace().equals(ModernIte.MOD_ID)
                    &&Set.of("club_ancient_metal","club_mithril","club_adamantium","club_vibranium").contains(id.getPath());
        }
        private static ItemStack requiredStack(ResourceLocation id) {
            var item=BuiltInRegistries.ITEM.getOptional(id).filter(value->value!=Items.AIR)
                    .orElseThrow(()->new IllegalStateException("Missing club smithing ingredient or result: "+id));
            return new ItemStack(item);
        }
        private static SmithingRecipeInput clubSmithingInput(ClubRoute route) {
            return new SmithingRecipeInput(requiredStack(ResourceLocation.parse("mite:equipment_upgrade_smithing_template")),
                    requiredStack(route.base()),requiredStack(route.addition()));
        }
        private static void validateClubSmithingSlots(IRecipeLayoutDrawable<?> layout,ClubRoute route,ItemStack assembledOutput) {
            var slots=layout.getRecipeSlotsView();var inputs=slots.getSlotViews(RecipeIngredientRole.INPUT);
            var outputs=slots.getSlotViews(RecipeIngredientRole.OUTPUT);
            check(inputs.size()==3&&outputs.size()==1,"Native smithing layout has exactly template, base, addition and output slots");
            Set<IRecipeSlotView> located=new HashSet<>();
            for(ItemStack ingredient:List.of(requiredStack(ResourceLocation.parse("mite:equipment_upgrade_smithing_template")),
                    requiredStack(route.base()),requiredStack(route.addition()))) {
                var matches=inputs.stream().filter(slot->contains(slot,ingredient)).toList();
                check(matches.size()==1&&located.add(matches.getFirst())&&matches.getFirst().getItemStacks().count()==1,
                        "Each exact single-count smithing ingredient is independently locatable in its native input slot");
            }
            // JEI's native SmithingCategoryExtension calls recipe.assemble with these default input stacks.
            // The material-upgrade hook adds preserved quality components absent from a fresh target stack.
            check(assembledOutput.is(requiredStack(route.result()).getItem())&&assembledOutput.getCount()==1,
                    "The actual recipe assembly produces one corresponding next-material club");
            check(outputs.getFirst().getItemStacks().count()==1&&contains(outputs.getFirst(),assembledOutput),
                    "The native output slot exactly matches real recipe assembly, including preserved components: expected="
                            +assembledOutput+" patch="+assembledOutput.getComponentsPatch()+"; actual="
                            +outputs.getFirst().getItemStacks().map(stack->stack+" patch="+stack.getComponentsPatch()).toList());
        }
        @SuppressWarnings("unchecked")
        private void validateFurnaceRuntime() {
            furnaceFacts=new JsonObject();report.add("vibranium_furnace",furnaceFacts);furnaceFacts.addProperty("status","runtime_verified");
            var manager=runtime.getRecipeManager();var uid=ResourceLocation.parse("mite:furnace_modern_ite_vibranium_furnace");
            furnaceType=(RecipeType<FurnaceJeiRecipe>)manager.getRecipeType(uid).orElseThrow();
            check(furnaceType.equals(VibraniumFurnaceJei.TYPE),"The extension uses the automatically registered base furnace type");
            furnaceCategory=manager.getRecipeCategory(furnaceType);check(furnaceCategory!=null,"The actual base JEI plugin creates the vibranium furnace category");
            var profile=R196FurnaceRules.profiles().get(VibraniumFurnaceJei.FURNACE_ID);
            check(profile!=null&&profile.maxHeat()==5&&profile.acceptsLarge()&&profile.cycleTicks()==200,"The automatically indexed furnace profile has the actual heat-five large-item server rules");
            var categories=manager.createRecipeCategoryLookup().includeHidden().get().filter(value->value.getRecipeType().getRecipeClass()==FurnaceJeiRecipe.class).toList();
            check(categories.size()==R196FurnaceRules.profiles().size(),"The base plugin has exactly one thermal category per physical device");
            int matchingCategories=0;
            for(var candidate:categories) {
                var candidateType=(RecipeType<FurnaceJeiRecipe>)candidate.getRecipeType();
                var candidateRows=manager.createRecipeLookup(candidateType).includeHidden().get().toList();
                if(candidateRows.stream().anyMatch(row->row.furnaceId().equals(VibraniumFurnaceJei.FURNACE_ID))) {
                    matchingCategories++;check(candidateType.equals(furnaceType),"No duplicate extension category may register vibranium furnace batches");
                }
            }
            check(matchingCategories==1,"Vibranium furnace batches appear in exactly one actual runtime category");
            furnaceRows=manager.createRecipeLookup(furnaceType).includeHidden().get().toList();check(!furnaceRows.isEmpty(),"The actual vibranium category contains real base thermal batches");
            Set<ResourceLocation> recipeIds=new HashSet<>();Map<Integer,FurnaceJeiRecipe> samples=new LinkedHashMap<>();
            for(var row:furnaceRows) {
                check(row.furnaceId().equals(VibraniumFurnaceJei.FURNACE_ID)&&!row.inputs().isEmpty()&&!row.fuels().isEmpty()&&!row.output().isEmpty(),"Every newly indexed batch belongs to this actual furnace and has resolved input, output and fuel");
                check(row.heat()>=1&&row.heat()<=5&&row.cycleTicks()==200,"Every newly indexed batch respects this device's real heat and cycle");
                check(recipeIds.add(furnaceCategory.getRegistryName(row)),"Actual vibranium thermal batch identities are unique");samples.putIfAbsent(row.heat(),row);
            }
            var blaze=new ItemStack(ExtremeContent.BLAZE_COAL.get());check(FurnaceThermalController.fuel(blaze).heat()==5,"The extension's actual blaze coal fuel supplies heat five");
            heatFiveRow=furnaceRows.stream().filter(row->row.heat()==5&&row.fuels().stream().anyMatch(fuel->fuel.is(blaze.getItem()))).findFirst().orElseThrow();
            Set<String> expectedCatalyst=Set.of(VibraniumFurnaceJei.FURNACE_ID);
            var catalysts=manager.createRecipeCatalystLookup(furnaceType).includeHidden().getItemStack().toList();
            check(catalysts.size()==1&&itemIds(catalysts).equals(expectedCatalyst),"The real native catalyst lookup exposes only the vibranium furnace");
            var focusFactory=runtime.getJeiHelpers().getFocusFactory();
            var uses=focusFactory.createFocus(RecipeIngredientRole.CATALYST,VanillaTypes.ITEM_STACK,new ItemStack(ExtremeContent.VIBRANIUM_FURNACE.get()));
            var fromUses=manager.createRecipeLookup(furnaceType).includeHidden().limitFocus(List.of(uses)).get().toList();
            check(furnaceRecipeIds(fromUses).equals(recipeIds),"The real furnace U lookup indexes every new batch");
            var output=focusFactory.createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,heatFiveRow.output());
            var fromOutput=manager.createRecipeLookup(furnaceType).includeHidden().limitFocus(List.of(output)).get().toList();
            var expectedOutput=furnaceRows.stream().filter(row->ItemStack.isSameItemSameComponents(row.output(),heatFiveRow.output())).toList();
            check(!fromOutput.isEmpty()&&furnaceRecipeIds(fromOutput).equals(furnaceRecipeIds(expectedOutput)),"Real output R lookup indexes the complete heat-five product variants");
            int layouts=0;
            for(var row:samples.values()) {
                var layout=manager.createRecipeLayoutDrawable(furnaceCategory,row,focusFactory.getEmptyFocusGroup()).orElseThrow();var slots=layout.getRecipeSlotsView();
                var input=slots.findSlotByName("input").orElseThrow();var fuel=slots.findSlotByName("fuel").orElseThrow();var result=slots.findSlotByName("output").orElseThrow();
                check(input.getRole()==RecipeIngredientRole.INPUT&&fuel.getRole()==RecipeIngredientRole.INPUT&&result.getRole()==RecipeIngredientRole.OUTPUT,"Native thermal layouts use the actual input/fuel/output roles");
                check(input.getItemStacks().count()==row.inputs().size()&&fuel.getItemStacks().count()==row.fuels().size(),"The native furnace layout preserves every real alternative and fuel quantity");
                for(var stack:row.inputs())check(contains(input,stack),"The actual furnace layout displays each exact input batch");
                for(var stack:row.fuels())check(contains(fuel,stack),"The actual furnace layout displays each exact legal fuel batch");
                check(contains(result,row.output()),"The actual furnace layout displays its exact thermal result batch");layouts++;
            }
            furnaceFacts.addProperty("type",uid.toString());furnaceFacts.addProperty("registered_categories",matchingCategories);furnaceFacts.addProperty("recipes",furnaceRows.size());furnaceFacts.addProperty("native_layouts_validated",layouts);
            furnaceFacts.add("public_catalysts",json(expectedCatalyst));furnaceFacts.addProperty("uses_lookup_verified",true);furnaceFacts.addProperty("output_lookup_verified",true);furnaceFacts.addProperty("heat_five_blaze_coal_verified",true);
        }
        private Set<ResourceLocation> furnaceRecipeIds(List<FurnaceJeiRecipe> source){Set<ResourceLocation> ids=new HashSet<>();source.forEach(row->ids.add(furnaceCategory.getRegistryName(row)));return ids;}
        private void validateRecipe(ForgeJeiRecipe row) {
            String material=GearProgression.material(row.input());int cap=cap(material),minimum=tier(material),grade=row.inputGrade();
            check(cap>0&&minimum==row.minimumTier()&&grade>=0&&grade<cap,"Source row obeys server material and grade constraints");
            check(row.input().getCount()==1&&row.output().getCount()==1&&row.input().is(row.output().getItem())&&row.outputGrade()==grade+1,"Source row produces one next-grade copy of the actual gear");
            check(ExtremeGear.grade(row.input())==grade&&ExtremeGear.grade(row.output())==grade+1,"Recipe display uses actual persisted forge-grade components");
            Map<String,Integer> expected=new HashMap<>();for(var cost:ForgeRecipes.costs(grade))expected.merge(row.universal()&&cost.id().endsWith("_enhance_stone")?"modern_ite:universal_enhance_stone":cost.id(),cost.count(),Integer::sum);
            Map<String,Integer> actual=new HashMap<>();for(var materialStack:row.materials())actual.merge(BuiltInRegistries.ITEM.getKey(materialStack.getItem()).toString(),materialStack.getCount(),Integer::sum);
            check(actual.equals(expected),"Actual JEI material stacks equal the physical server allocation, including universal substitution");
            var expectedRange=expectedStats.computeIfAbsent(grade+"/"+row.universal(),ignored->expectedStats(grade,row.universal()));
            check(row.stats().equals(expectedRange),"Every registered row's timing/failure range matches all actual server-legal tool combinations");
        }
        private void validateLayout(IRecipeCategory<ForgeJeiRecipe> category,ForgeJeiRecipe row) {
            var layout=runtime.getRecipeManager().createRecipeLayoutDrawable(category,row,runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            var slots=layout.getRecipeSlotsView();
            check(slots.getSlotViews(RecipeIngredientRole.INPUT).stream().anyMatch(slot->contains(slot,row.input())),"Actual JEI layout contains the exact current-grade gear input");
            check(slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream().anyMatch(slot->contains(slot,row.output())),"Actual JEI layout contains the exact next-grade output");
            for(var material:row.materials())check(slots.getSlotViews(RecipeIngredientRole.INPUT).stream().anyMatch(slot->contains(slot,material)),"Actual JEI layout shows each physical material cost");
            for(var tool:Map.of("axe",ToolFamily.AXE,"hammer",ToolFamily.WAR_HAMMER).entrySet()) {
                var slot=slots.findSlotByName(tool.getKey()).orElseThrow();check(slot.getRole()==RecipeIngredientRole.CATALYST,"Forge tools are reused catalysts, not consumed material inputs");
                check(itemIds(slot.getItemStacks().toList()).equals(legalToolIds(tool.getValue())),"Actual JEI tool slot includes all station-legal tool alternatives");
            }
        }
        void tick(Minecraft mc) {
            if(phase!=-2)return;
            var inventory=mc.player.getInventory();
            var gem=new GearStationScreen(GearStationMenu.gemClient(77,inventory),inventory,Component.literal("JEI gem mode audit"));mc.setScreen(gem);
            check(runtime.getScreenHelper().getGuiClickableArea(gem,122,60).findAny().isEmpty(),"Shared gem-mode screen must not register the forge click area");
            forgeScreen=new GearStationScreen(GearStationMenu.forgeClient(78,inventory),inventory,Component.literal("JEI forging click audit"));mc.setScreen(forgeScreen);
            var areas=runtime.getScreenHelper().getGuiClickableArea(forgeScreen,122,60).toList();check(areas.size()==1,"The real screen helper must expose one registered forge arrow area");
            var rect=areas.getFirst().getArea();check(rect.getX()==108&&rect.getY()==52&&rect.getWidth()==28&&rect.getHeight()==16,"Forge click rectangle matches the arrow and avoids the physical start button");
            check(runtime.getScreenHelper().getGuiClickableArea(forgeScreen,122,88).findAny().isEmpty(),"Physical Forge button must not also open JEI");
            areas.getFirst().onClick(runtime.getJeiHelpers().getFocusFactory(),runtime.getRecipesGui());
            check(mc.screen==runtime.getRecipesGui(),"Calling the actual registered arrow action must open the actual JEI recipe screen");
            check(runtime.getRecipesGui().getParentScreen().orElse(null)==forgeScreen,"Native JEI retains the forge screen as its parent");
            report.addProperty("forge_arrow_action_opened_native_jei",true);report.addProperty("gem_mode_has_no_forge_click_area",true);
            movePointerAway(mc);
            phase=-1;expectedScreen=mc.screen;frames=0;
        }
        void frame(Minecraft mc)throws Exception {
            if(phase==-2||mc.screen!=expectedScreen||mc.getOverlay()!=null||++frames<2)return;
            if(phase==-1) {
                assertSingleHeader();screenshot(mc,"forge-arrow-click.png");
                report.addProperty("native_arrow_header_tabs",1);
                openTier(mc,0);return;
            }
            if(phase>=0&&phase<=3) {
                var visible=visibleRows();check(visible.size()==1&&visible.getFirst().minimumTier()==phase,"Native single-row scene displays the requested physical minimum tier");
                Set<String> sidebar=assertSidebar(mc,expectedTables(phase));assertSingleHeader();
                String image="forging-tier-"+phase+".png";screenshot(mc,image);
                var facts=report.getAsJsonArray("tiers").get(phase).getAsJsonObject();facts.add("native_visible_sidebar",json(sidebar));facts.addProperty("native_sidebar_slots",sidebar.size());facts.addProperty("screenshot",screenshots().resolve(image).toString());
                if(phase<3)openTier(mc,phase+1);else {
                    report.addProperty("actual_native_sidebar_screens_verified",4);
                    // Only this disposable instance is resized logically, so a real mixed page fits two rows.
                    mc.options.guiScale().set(1);mc.resizeDisplay();
                    openRows(mc,List.of(sample(0),sample(3)),4);
                }return;
            }
            if(phase==4) {
                var visible=visibleRows();check(visible.size()==2&&visible.stream().map(ForgeJeiRecipe::minimumTier).collect(java.util.stream.Collectors.toSet()).equals(Set.of(0,3)),"Mixed native page really displays both iron-tier and vibranium-tier recipes together");
                assertSidebar(mc,expectedTables(3));assertSingleHeader();screenshot(mc,"mixed-tier-0-and-3-single-tab.png");
                report.addProperty("mixed_visible_page_intersection_only_vibranium",true);
                var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,new ItemStack(Items.IRON_SWORD));
                runtime.getRecipesGui().show(List.of(focus));
                Object logic=logic();logic.getClass().getMethod("setRecipeCategory",IRecipeCategory.class).invoke(logic,category);
                arm(mc,5);return;
            }
            if(phase==5) {
                var focused=focusedRows();Set<String> expected=recipeKeys(allRows.stream().filter(row->row.itemId().equals(ResourceLocation.withDefaultNamespace("iron_sword"))).toList());
                check(!expected.isEmpty()&&recipeKeys(focused).equals(expected),"Native generic R focus opens every iron sword grade/stone row in the shared strengthening category");
                check(visibleRows().stream().allMatch(row->row.minimumTier()==0),"Native output R focus shows iron rows");
                assertSidebar(mc,expectedTables(0));screenshot(mc,"generic-iron-sword-r-focus.png");report.addProperty("generic_output_r_focus_verified",true);
                // Obtain the real current page capacity before choosing a two-page, changing-tier query.
                var candidates=new ArrayList<>(rows.get(0));candidates.add(sample(3));openRows(mc,candidates,6);return;
            }
            if(phase==6) {
                Object logic=logic();int capacity=(Integer)logic.getClass().getMethod("getRecipesPerPage").invoke(logic);
                check(capacity>0&&rows.get(0).size()>=capacity,"Real native page capacity permits a controlled two-page query");
                pagingRows=new ArrayList<>(rows.get(0).subList(0,capacity));pagingRows.add(sample(3));openRows(mc,pagingRows,7);return;
            }
            if(phase==7) {
                var visible=visibleRows();firstPageKeys=recipeKeys(visible);firstPageTables=assertSidebar(mc,expectedTables(maximumTier(visible)));assertSingleHeader();screenshot(mc,"native-page-before-next.png");
                Object logic=logic();check(Boolean.TRUE.equals(logic.getClass().getMethod("nextPage").invoke(logic)),"Native nextPage really advances the controlled strengthening query");arm(mc,8);return;
            }
            if(phase==8) {
                var visible=visibleRows();Set<String> keys=recipeKeys(visible),tables=assertSidebar(mc,expectedTables(maximumTier(visible)));assertSingleHeader();
                check(!keys.equals(firstPageKeys),"Native nextPage changes the actual visible recipe layouts");
                Set<String> union=new HashSet<>(firstPageKeys);union.addAll(keys);check(union.equals(recipeKeys(pagingRows)),"Both real pages together cover exactly the controlled iron/vibranium query");
                check(!tables.equals(firstPageTables),"The native table strip changes with the actual visible page's tier intersection");
                screenshot(mc,"native-page-after-next.png");report.addProperty("native_next_page_updates_sidebar",true);report.addProperty("single_shared_category_all_scenes",true);openFurnaceArrow(mc);return;
            }
            if(phase==9) {
                Object logic=logic();check(logic.getClass().getMethod("getSelectedRecipeCategory").invoke(logic)==furnaceCategory,"The first native furnace-arrow action opens the indexed vibranium category rather than hidden vanilla smelting");
                var visible=visibleFurnaceRows();check(visible.stream().allMatch(row->row.furnaceId().equals(VibraniumFurnaceJei.FURNACE_ID)),"The actual clicked furnace screen draws this device's native batches");
                Set<String> sidebar=assertSidebar(mc,Set.of(VibraniumFurnaceJei.FURNACE_ID));screenshot(mc,"vibranium-furnace.png");
                furnaceFacts.addProperty("native_arrow_opened_correct_category",true);furnaceFacts.add("native_visible_sidebar",json(sidebar));furnaceFacts.addProperty("native_sidebar_slots",sidebar.size());furnaceFacts.addProperty("screenshot",screenshots().resolve("vibranium-furnace.png").toString());
                runtime.getRecipesGui().showRecipes(furnaceCategory,List.of(heatFiveRow),List.of());arm(mc,10);return;
            }
            if(phase==10) {
                var visible=visibleFurnaceRows();check(visible.size()==1&&visible.getFirst().heat()==5&&visible.getFirst().fuels().stream().anyMatch(fuel->fuel.is(ExtremeContent.BLAZE_COAL.get())),"The actual heat-five furnace page renders the source blaze-coal alternative");
                assertSidebar(mc,Set.of(VibraniumFurnaceJei.FURNACE_ID));screenshot(mc,"vibranium-furnace-heat-5.png");
                furnaceFacts.addProperty("native_heat_five_page_rendered",true);furnaceFacts.addProperty("heat_five_screenshot",screenshots().resolve("vibranium-furnace-heat-5.png").toString());furnaceFacts.addProperty("status","passed");
                mc.options.guiScale().set(2);mc.resizeDisplay();
                runtime.getRecipesGui().showRecipes(smithingCategory,List.of(clubSmithingTarget),List.of());arm(mc,11);return;
            }
            if(phase==11) {
                Object logic=logic();check(logic.getClass().getMethod("getSelectedRecipeCategory").invoke(logic)==smithingCategory,
                        "The screenshot scene selects the standard native JEI smithing category");
                var layouts=visibleClubSmithingLayouts();
                check(layouts.size()==1&&layouts.getFirst().getRecipe()==clubSmithingTarget,
                        "The actual rendered smithing drawable is the requested loaded adamantium-to-vibranium holder");
                var route=new ClubRoute("adamantium","vibranium");
                var assembled=clubSmithingTarget.value().assemble(clubSmithingInput(route),mc.level.registryAccess());
                validateClubSmithingSlots(layouts.getFirst(),route,assembled);
                var sidebar=assertSidebar(mc,Set.of("minecraft:smithing_table"));
                screenshot(mc,"club-smithing-upgrade.png");
                clubSmithingFacts.addProperty("native_target_holder_rendered",true);
                clubSmithingFacts.addProperty("native_drawn_holder",clubSmithingTarget.id().toString());
                clubSmithingFacts.addProperty("native_template_base_addition_output_rendered",true);
                clubSmithingFacts.add("native_visible_sidebar",json(sidebar));clubSmithingFacts.addProperty("native_sidebar_slots",sidebar.size());
                clubSmithingFacts.addProperty("gui_scale",mc.options.guiScale().get());
                clubSmithingFacts.addProperty("screenshot",screenshots().resolve("club-smithing-upgrade.png").toString());
                clubSmithingFacts.addProperty("status","passed");finish(mc);return;
            }
            throw new IllegalStateException("Unknown native JEI audit phase: "+phase);
        }
        private void openFurnaceArrow(Minecraft mc) {
            var helper=runtime.getScreenHelper();
            var stone=furnaceScreen(mc,80,2);check(!VibraniumFurnaceJei.isVibraniumFurnaceScreen(stone),"Native heat-two furnace screens retain their original click handlers");
            long stoneArrow=helper.getGuiClickableArea(stone,90,40).count(),stoneOutside=helper.getGuiClickableArea(stone,20,20).count();
            var netherrack=furnaceScreen(mc,81,4);check(!VibraniumFurnaceJei.isVibraniumFurnaceScreen(netherrack),"Native heat-four furnace screens retain their original click handlers");
            long netherrackArrow=helper.getGuiClickableArea(netherrack,90,40).count(),netherrackOutside=helper.getGuiClickableArea(netherrack,20,20).count();
            furnaceScreen=furnaceScreen(mc,82,5);check(VibraniumFurnaceJei.isVibraniumFurnaceScreen(furnaceScreen),"The actual native screen is identified from its synchronized heat-five menu capacity");
            check(((FurnaceThermalMenuAccess)furnaceScreen.getMenu()).mite$getMaxHeat()==5,"Native furnace menu data slot five synchronizes the real maximum heat");
            var areas=helper.getGuiClickableArea(furnaceScreen,90,40).toList();long outside=helper.getGuiClickableArea(furnaceScreen,20,20).count();
            check(stoneArrow==netherrackArrow&&areas.size()==stoneArrow+1,"Only the heat-five furnace prepends one actual arrow handler");
            check(stoneOutside==netherrackOutside&&outside==stoneOutside,"Outside the arrow, heat-five screens append no clickable area");
            var rect=areas.getFirst().getArea();check(rect.getX()==78&&rect.getY()==32&&rect.getWidth()==28&&rect.getHeight()==23,"The prioritized actual click area matches the native furnace arrow");
            furnaceFacts.addProperty("native_menu_maximum_heat",5);furnaceFacts.addProperty("ordinary_arrow_area_count",stoneArrow);furnaceFacts.addProperty("netherrack_arrow_area_count",netherrackArrow);furnaceFacts.addProperty("vibranium_arrow_area_count",areas.size());furnaceFacts.addProperty("off_arrow_area_count",outside);furnaceFacts.addProperty("other_screens_and_off_arrow_unchanged",true);
            areas.getFirst().onClick(runtime.getJeiHelpers().getFocusFactory(),runtime.getRecipesGui());
            check(mc.screen==runtime.getRecipesGui(),"The first real screen-helper action opens the actual native JEI screen");
            check(runtime.getRecipesGui().getParentScreen().orElse(null)==furnaceScreen,"The native furnace remains the recipe screen's parent");arm(mc,9);
        }
        private static FurnaceScreen furnaceScreen(Minecraft mc,int id,int maxHeat) {
            var menu=new FurnaceMenu(id,mc.player.getInventory());menu.setData(5,maxHeat);
            var screen=new FurnaceScreen(menu,mc.player.getInventory(),new ItemStack(ExtremeContent.VIBRANIUM_FURNACE.get()).getHoverName());mc.setScreen(screen);return screen;
        }
        private List<FurnaceJeiRecipe> visibleFurnaceRows()throws Exception {
            Object logic=logic(),state=field(logic,"state"),cache=field(logic,"cachedRecipeLayoutsWithButtons");check(cache!=null,"Native furnace rendered-layout cache exists");
            List<?> layouts=(List<?>)Class.forName("mezz.jei.gui.recipes.lookups.ILookupState").getMethod("getVisible",Class.forName("mezz.jei.gui.recipes.layouts.IRecipeLayoutList")).invoke(state,cache);
            List<FurnaceJeiRecipe> result=new ArrayList<>();for(Object layout:layouts) {
                Object drawable=Class.forName("mezz.jei.gui.recipes.IRecipeLayoutWithButtons").getMethod("getRecipeLayout").invoke(layout);
                check(drawable instanceof IRecipeLayoutDrawable<?>,"Native furnace row owns an actual recipe drawable");
                var actual=(IRecipeLayoutDrawable<?>)drawable;check(actual.getRecipeCategory()==furnaceCategory&&actual.getRecipe() instanceof FurnaceJeiRecipe,"The native screen actually renders the automatically registered furnace category");
                result.add((FurnaceJeiRecipe)actual.getRecipe());
            }check(!result.isEmpty(),"The actual native furnace page draws at least one thermal batch");return result;
        }
        private List<IRecipeLayoutDrawable<?>> visibleClubSmithingLayouts()throws Exception {
            Object logic=logic(),state=field(logic,"state"),cache=field(logic,"cachedRecipeLayoutsWithButtons");
            check(cache!=null,"Native smithing rendered-layout cache exists");
            List<?> layouts=(List<?>)Class.forName("mezz.jei.gui.recipes.lookups.ILookupState")
                    .getMethod("getVisible",Class.forName("mezz.jei.gui.recipes.layouts.IRecipeLayoutList")).invoke(state,cache);
            List<IRecipeLayoutDrawable<?>> result=new ArrayList<>();
            for(Object layout:layouts) {
                Object drawable=Class.forName("mezz.jei.gui.recipes.IRecipeLayoutWithButtons").getMethod("getRecipeLayout").invoke(layout);
                check(drawable instanceof IRecipeLayoutDrawable<?>,"Native smithing row owns an actual JEI drawable");
                var actual=(IRecipeLayoutDrawable<?>)drawable;
                check(actual.getRecipeCategory()==smithingCategory&&actual.getRecipe() instanceof RecipeHolder<?> holder
                        &&holder.value() instanceof SmithingTransformRecipe,"The visible row really draws the vanilla transform category and recipe holder");
                result.add(actual);
            }
            check(!result.isEmpty(),"The native smithing recipe screen renders at least one actual row");return result;
        }
        private Set<String> assertSidebar(Minecraft mc,Set<String> expected)throws Exception {
            List<?> actualSlots=(List<?>)field(field(expectedScreen,"recipeCatalysts"),"recipeSlots");Set<String> visible=new HashSet<>();
            check(actualSlots.size()==expected.size(),"Actual native JEI left sidebar must show every eligible table in its own slot");
            for(Object raw:actualSlots) {
                check(raw instanceof IRecipeSlotView,"Native left sidebar must contain actual JEI recipe slots");
                var rectangle=((IRecipeSlotDrawable)raw).getRect();
                check(rectangle.getX()>=0&&rectangle.getY()>=0&&rectangle.getX()+rectangle.getWidth()<=mc.getWindow().getGuiScaledWidth()
                        &&rectangle.getY()+rectangle.getHeight()<=mc.getWindow().getGuiScaledHeight(),"Every native sidebar table is visibly inside the screenshot viewport");
                List<ItemStack> ingredients=((IRecipeSlotView)raw).getItemStacks().toList();
                check(ingredients.size()==1,"Each usable table is visible simultaneously, not hidden in a rotating group");
                check(visible.add(BuiltInRegistries.ITEM.getKey(ingredients.getFirst().getItem()).toString()),"Native sidebar does not duplicate tables");
            }
            check(visible.equals(expected),"Actual visible sidebar equals the current page's full server-allowed table intersection");return visible;
        }
        private void assertSingleHeader()throws Exception {
            Object logic=logic();check(logic.getClass().getMethod("getSelectedRecipeCategory").invoke(logic)==category,"Native strengthening scene keeps the shared category selected");
            List<?> selected=(List<?>)logic.getClass().getMethod("getRecipeCategories").invoke(logic);check(selected.equals(List.of(category)),"The native header has one strengthening category, without tier tabs");
            List<?> tabs=(List<?>)field(field(expectedScreen,"recipeGuiTabs"),"tabs");check(tabs.size()==1,"Actual native drawn tab strip contains exactly one tab");
        }
        private Object logic()throws Exception{return field(runtime.getRecipesGui(),"logic");}
        private List<ForgeJeiRecipe> focusedRows()throws Exception {
            Object state=field(logic(),"state");Class<?> stateType=Class.forName("mezz.jei.gui.recipes.lookups.ILookupState");
            Object focused=stateType.getMethod("getFocusedRecipes").invoke(state);
            List<?> raw=(List<?>)Class.forName("mezz.jei.gui.recipes.lookups.IFocusedRecipes").getMethod("getRecipes").invoke(focused);
            List<ForgeJeiRecipe> result=new ArrayList<>();for(Object row:raw){check(row instanceof ForgeJeiRecipe,"Actual focused native recipes are strengthening rows");result.add((ForgeJeiRecipe)row);}return result;
        }
        private List<ForgeJeiRecipe> visibleRows()throws Exception {
            Object logic=logic(),state=field(logic,"state"),cache=field(logic,"cachedRecipeLayoutsWithButtons");check(cache!=null,"Native rendered layout cache exists");
            List<?> layouts=(List<?>)Class.forName("mezz.jei.gui.recipes.lookups.ILookupState").getMethod("getVisible",Class.forName("mezz.jei.gui.recipes.layouts.IRecipeLayoutList")).invoke(state,cache);
            List<ForgeJeiRecipe> result=new ArrayList<>();for(Object layout:layouts) {
                Object drawable=Class.forName("mezz.jei.gui.recipes.IRecipeLayoutWithButtons").getMethod("getRecipeLayout").invoke(layout);
                check(drawable instanceof IRecipeLayoutDrawable<?>,"Actual native row owns a real recipe drawable");Object recipe=((IRecipeLayoutDrawable<?>)drawable).getRecipe();
                check(recipe instanceof ForgeJeiRecipe,"Actual native visible layouts contain strengthening data");result.add((ForgeJeiRecipe)recipe);
            }check(!result.isEmpty(),"The actual JEI screen renders at least one row");return result;
        }
        private static int maximumTier(List<ForgeJeiRecipe> visible){return visible.stream().mapToInt(ForgeJeiRecipe::minimumTier).max().orElseThrow();}
        private static Set<String> recipeKeys(List<ForgeJeiRecipe> recipes){Set<String> result=new HashSet<>();recipes.forEach(row->result.add(key(row)));return result;}
        private ForgeJeiRecipe sample(int tier){return rows.get(tier).stream().filter(row->!row.universal()&&row.inputGrade()==0).findFirst().orElseThrow();}
        private void arm(Minecraft mc,int nextPhase){check(mc.screen==runtime.getRecipesGui(),"Every scene uses the real JEI screen");movePointerAway(mc);phase=nextPhase;expectedScreen=mc.screen;frames=0;}
        private void openRows(Minecraft mc,List<ForgeJeiRecipe> requested,int nextPhase){runtime.getRecipesGui().showRecipes(category,requested,List.of());arm(mc,nextPhase);}
        private void openTier(Minecraft mc,int tier) {
            openRows(mc,List.of(sample(tier)),tier);
        }
        private static void movePointerAway(Minecraft mc){GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),4,4);}
        private static boolean contains(IRecipeSlotView slot,ItemStack expected){return slot.getItemStacks().anyMatch(actual->ItemStack.matches(actual,expected));}
        private static Set<String> legalToolIds(ToolFamily family) {
            Set<String> result=new HashSet<>();for(var entry:BuiltInRegistries.ITEM.entrySet()){ItemStack stack=entry.getValue().getDefaultInstance();if(stack.isDamageableItem()&&R196EnchantmentCatalog.family(stack)==family)result.add(entry.getKey().location().toString());}return result;
        }
        private static ForgeJeiRecipes.ToolStats expectedStats(int grade,boolean universal) {
            int minTicks=Integer.MAX_VALUE,maxTicks=0,minFailure=100,maxFailure=0;
            for(String axe:legalToolIds(ToolFamily.AXE))for(String hammer:legalToolIds(ToolFamily.WAR_HAMMER)) {
                int a=GearMath.materialTier(GearProgression.material(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(axe)))));
                int h=GearMath.materialTier(GearProgression.material(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(hammer)))));
                int ticks=GearMath.forgeTicks(grade,a,h),base=universal?50:grade<4?35:grade<7?55:65;
                int failure=Math.clamp(base-Math.max(0,a-2)*2-Math.max(0,h-2)*3,0,100);
                minTicks=Math.min(minTicks,ticks);maxTicks=Math.max(maxTicks,ticks);minFailure=Math.min(minFailure,failure);maxFailure=Math.max(maxFailure,failure);
            }return new ForgeJeiRecipes.ToolStats(minTicks,maxTicks,minFailure,maxFailure);
        }
    }

    private static String key(ForgeJeiRecipe row){return row.itemId()+"/"+row.inputGrade()+"/"+row.universal();}
    private static int cap(String material){return switch(material==null?"":material){case "iron","ancient_metal"->3;case "mithril"->5;case "adamantium"->7;case "vibranium"->10;default->0;};}
    private static int tier(String material){return switch(material==null?"":material){case "iron"->0;case "ancient_metal","mithril"->1;case "adamantium"->2;case "vibranium"->3;default->-1;};}
    private static Set<String> expectedRecipeKeys() {
        Set<String> expected=new HashSet<>();for(var entry:BuiltInRegistries.ITEM.entrySet()) {
            ItemStack gear=new ItemStack(entry.getValue());if(!ExtremeGear.socketable(gear))continue;
            int maximum=cap(GearProgression.material(gear));for(int grade=0;grade<maximum;grade++)for(boolean universal:new boolean[]{false,true})expected.add(entry.getKey().location()+"/"+grade+"/"+universal);
        }return expected;
    }
    private static Set<String> expectedTables(int minimum) {Set<String> expected=new HashSet<>();for(int tier=minimum;tier<4;tier++)expected.add(BuiltInRegistries.ITEM.getKey(ExtremeGear.FORGES.get(tier).get().asItem()).toString());return expected;}
    private static Set<String> itemIds(List<ItemStack> stacks){Set<String> ids=new HashSet<>();stacks.forEach(stack->ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));return ids;}
    private static JsonArray json(Set<String> ids){JsonArray array=new JsonArray();ids.stream().sorted().forEach(array::add);return array;}
    private static Object field(Object object,String name)throws ReflectiveOperationException {Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void screenshot(Minecraft mc,String filename)throws Exception {Path path=screenshots().resolve(filename);Files.createDirectories(path.getParent());try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}}
    private static JsonObject initial(){JsonObject report=new JsonObject();report.addProperty("author","NingMeng123");report.addProperty("timestamp",Instant.now().toString());report.addProperty("scope","real optional JEI runtime; one shared forging tab; complete gear-grade-stone matrix; per-recipe table uses; registered click area; native single/mixed/focused/paginated sidebars; vibranium furnace batches; seven native club smithing upgrades and rendered screenshots");report.addProperty("jei_loaded",ModList.get().isLoaded("jei"));report.addProperty("existing_world_loaded",false);report.addProperty("multiplayer_connected",false);report.add("failures",new JsonArray());return report;}
    private static boolean owned(Minecraft mc){return started&&worldName!=null&&mc.getSingleplayerServer()!=null&&worldName.equals(mc.getSingleplayerServer().getWorldData().getLevelName());}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void fail(String label,Throwable failure){if(report==null)report=initial();report.getAsJsonArray("failures").add(label+": "+failure);LogUtils.getLogger().error("MODERN_ITE_FORGE_JEI_AUDIT {}",label,failure);}
    private static void finish(Minecraft mc){if(finished)return;finished=true;if(report==null)report=initial();boolean safe=owned(mc)||(started&&mc.level==null&&mc.getConnection()==null&&mc.getSingleplayerServer()==null);report.addProperty("status",report.getAsJsonArray("failures").isEmpty()?"passed":"failed");report.addProperty("safe_client_shutdown_requested",safe);write();if(safe)mc.stop();}
    private static Path output(){return Path.of(System.getProperty("modern_ite.jeiAuditOutput","build/verification/forge-jei-audit.json")).toAbsolutePath().normalize();}
    private static Path screenshots(){return output().getParent().resolve("forge-jei-audit");}
    private static void write(){try{Path path=output();Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n",StandardCharsets.UTF_8);LogUtils.getLogger().info("MODERN_ITE_FORGE_JEI_AUDIT {} report={}",report.get("status").getAsString(),path);}catch(Exception failure){LogUtils.getLogger().error("Cannot write forge JEI audit",failure);}}
    private static final class WaitingScreen extends Screen {
        WaitingScreen(){super(Component.literal("Modern ITE isolated JEI audit"));}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){graphics.fill(0,0,width,height,0xFF101820);graphics.drawCenteredString(font,title,width/2,height/2,0xFFFFFFFF);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean shouldCloseOnEsc(){return false;}
    }
}
