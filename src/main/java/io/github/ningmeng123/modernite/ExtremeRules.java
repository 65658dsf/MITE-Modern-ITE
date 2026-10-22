package io.github.ningmeng123.modernite;

import java.util.*;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.*;
import io.github.ningmeng123.mite.worldgen.*;
import io.github.ningmeng123.modernite.content.*;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.ExtremeSpawnProgression;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.SpawnPopulation;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.enchantment.ExtremeEnchantments;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** One provider owns all extension decisions; the base scheduler remains authoritative. @author NingMeng123 */
public final class ExtremeRules implements MiteExtensions.Policy {
    @Override public int gemSacrificeExperience(ItemStack stack,int original) { return io.github.ningmeng123.modernite.item.IteItemRules.gemSacrificeExperience(stack,original); }
    @Override public boolean canSacrificeGem(Player player,ItemStack stack,boolean original) { return io.github.ningmeng123.modernite.item.IteItemRules.canSacrificeGem(player,stack,original); }
    @Override public int foodUseDuration(ItemStack stack,LivingEntity user,int original) { return io.github.ningmeng123.modernite.item.IteItemRules.foodUseDuration(stack,user,original); }
    @Override public void portalUsed(net.minecraft.server.level.ServerPlayer player,BlockPos entry) { io.github.ningmeng123.modernite.item.IteItemRules.portalUsed(player,entry); }
    @Override public int breedingExperience(net.minecraft.world.entity.animal.Animal parent,int original) { return io.github.ningmeng123.modernite.entity.IteMobSettingsRules.breedingExperience(parent,original); }
    @Override public float creeperExplosionRadius(net.minecraft.world.entity.monster.Creeper creeper,float original) { return io.github.ningmeng123.modernite.entity.IteMobSettingsRules.creeperExplosionRadius(creeper,original); }
    @Override public void villagerOffers(net.minecraft.world.entity.npc.Villager villager,net.minecraft.world.item.trading.MerchantOffers offers) { io.github.ningmeng123.modernite.entity.IteMobSettingsRules.villagerOffers(villager,offers); }
    @Override public boolean mobDefense(Mob mob,boolean original) { return original && io.github.ningmeng123.modernite.config.IteConfig.bool("mob_defense"); }
    @Override public float playerInWallDamage(Player player,float original) { return (float)io.github.ningmeng123.modernite.config.IteConfig.number("in_wall_damage_for_player"); }
    @Override public int fishingExperience(Player player,int original) { return io.github.ningmeng123.modernite.config.IteConfig.integer("fishing_xp"); }
    @Override public boolean canDisarmPlayer(Player attacker,Player target,boolean original) { return original || io.github.ningmeng123.modernite.config.IteConfig.bool("player_disarm_player"); }
    public static void register(IEventBus bus) { MiteExtensions.register(new ExtremeRules());bus.addListener((FMLCommonSetupEvent event)->event.enqueueWork(MiteExtensions::applyAttributeRanges)); }
    public static long currentDay(ServerLevel level) { return ExtremeCalendar.day(level.getServer().overworld().getDayTime()); }
    public static boolean blood(ServerLevel level) { return level.dimension().equals(Level.OVERWORLD)&&ExtremeCalendar.blood(currentDay(level)); }
    @Override public LunarCalendar.State calendar(LunarCalendar.State state) {
        if(!state.overworld())return state;
        long day=state.day();
        var event=day==128?LunarCalendar.Event.BLUE:ExtremeCalendar.blood(day)?LunarCalendar.Event.BLOOD:day==120?LunarCalendar.Event.MOON_DOG:ExtremeCalendar.blood(day+3)?LunarCalendar.Event.HARVEST:LunarCalendar.Event.ORDINARY;
        return new LunarCalendar.State(day,state.phase(),true,state.night(),event);
    }
    @Override public long bloodMoonCount(long day,long original) { return ExtremeCalendar.bloodCount(day); }
    @Override public boolean independentHostileBudgets(ServerLevel level,boolean original) { return false; }
    public static int hostileCap(ServerLevel level,int chunks,boolean deep) {
        return ExtremeWorldState.graceActive(level)?0:ExtremeCalendar.cap(currentDay(level),chunks,blood(level),deep,
                io.github.ningmeng123.modernite.config.IteConfig.number("blood_moon_max_hostile_fraction"),
                io.github.ningmeng123.modernite.config.IteConfig.number("mob_max_spawn_count_increase_per_day"),
                io.github.ningmeng123.modernite.config.IteConfig.integer("mob_max_spawn_count_chunk_base"),
                io.github.ningmeng123.modernite.config.IteConfig.integer("mob_max_spawn_count_day_minimum"),
                io.github.ningmeng123.modernite.config.IteConfig.integer("mob_max_spawn_count_day_maximum"));
    }
    @Override public int monsterCap(ServerLevel level,boolean deep,int chunks,int original) { return hostileCap(level,chunks,deep); }
    @Override public float hostilePassProbability(ServerLevel level,boolean deep,float original) { return ExtremeWorldState.graceActive(level)?0:original; }
    public static int localHostileLimit(ServerLevel level) {
        return io.github.ningmeng123.modernite.config.IteConfig.integer(blood(level)?"blood_moon_local_hostile_limit":"local_hostile_limit");
    }
    @Override public int localDensity(ServerLevel level,Player player,int original) { return localHostileLimit(level); }
    @Override public int spawnGroups(MiteExtensions.SpawnContext context,int original) {
        if(context.category()!=R196SpawnRules.Category.MONSTER)return original;
        boolean blood=blood(context.level());
        return ExtremeWorldState.graceActive(context.level())?0:io.github.ningmeng123.modernite.config.IteConfig.integer(blood?"blood_moon_hostile_spawn_groups":"hostile_spawn_groups");
    }
    @Override public int spawnAttempts(MiteExtensions.SpawnContext context,EntityType<?> selected,int original) {
        if(context.category()!=R196SpawnRules.Category.MONSTER)return original;
        return ExtremeWorldState.graceActive(context.level())?0:io.github.ningmeng123.modernite.config.IteConfig.integer("hostile_spawn_attempts");
    }
    @Override public int spawnJitter(MiteExtensions.SpawnContext context,int original) { return context.category()==R196SpawnRules.Category.MONSTER&&blood(context.level())?8:original; }
    @Override public int distantHostileSkipBound(MiteExtensions.SpawnContext context,int original) { return context.category()==R196SpawnRules.Category.MONSTER&&blood(context.level())?4:original; }
    @Override public boolean spawnDistance(MiteExtensions.SpawnContext context,BlockPos pos,double nearestSq,double spawnSq,boolean close,boolean original) {
        if(context.category()!=R196SpawnRules.Category.MONSTER)return original;
        return !ExtremeWorldState.graceActive(context.level())&&spawnSq>=576&&nearestSq>=576;
    }
    @Override public boolean outdoorAllows(MiteExtensions.SpawnContext context,Mob mob,boolean original) { return blood(context.level())||original; }
    @Override public boolean ownsMonsterSpawnSettings(String entityId) { return IteSpawnConfig.owns(entityId); }
    @Override public double monsterSpawnMultiplier(EntityType<?> type,double original) {
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        return IteSpawnConfig.owns(id)?IteSpawnConfig.multiplier(id):original;
    }
    @Override public double monsterSpawnWeight(EntityType<?> type,double original) {
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        return IteSpawnConfig.owns(id)?IteSpawnConfig.weight(id,original):original;
    }
    @Override public double monsterSpawnWeightMultiplier(MiteExtensions.SpawnContext context,BlockPos pos,EntityType<?> type,double original) {
        if(type!=EntityType.ZOMBIFIED_PIGLIN||!naturalPigmanReplacementAllowed(context.level()))return original;
        double parent=ConfiguredMonsterSpawns.multiplier(type);
        double lord=ConfiguredMonsterSpawns.multiplier(ExtremeMobs.type(ExtremeKind.ZOMBIE_PIGMAN_LORD));
        return .8D*parent+.2D*lord;
    }
    /** Same admission gate as the natural pigman finalization path. */
    public static boolean naturalPigmanReplacementAllowed(ServerLevel level) {
        return level.getServer().isSameThread()
                &&SpawnPopulation.automaticAllowed(level,ExtremeMobs.type(ExtremeKind.ZOMBIE_PIGMAN_LORD));
    }
    @Override public List<R196MobSpawning.Entry> spawnPool(MiteExtensions.SpawnContext context,BlockPos pos,List<R196MobSpawning.Entry> original) {
        var result=new ArrayList<>(original);ExtremeMobs.addSpawnEntries(context,pos,result);
        long day=io.github.ningmeng123.mite.worldgen.BloodMoonInvasion.highestDay(context.level());
        result.removeIf(entry->!ExtremeSpawnProgression.unlocked(BuiltInRegistries.ENTITY_TYPE.getKey(entry.type()).toString(),day,
                io.github.ningmeng123.modernite.config.IteConfig.integer("ancient_bone_lord_spawn_limit_day"))
                ||ExtremeWorldState.graceActive(context.level())&&io.github.ningmeng123.modernite.entity.SpawnPopulation.hostile(entry.type()));
        return List.copyOf(result);
    }
    @Override public double maximumHealth(Player player,double original) { return ExtremeGear.health(player,original); }
    @Override public int foodCapacity(Player player,int original) { return ExtremeGear.nutritionCapacity(player,original); }
    @Override public int gearMaximumLevel(ItemStack stack,int original) { return ExtremeGear.maximumGearLevel(stack,original); }
    @Override public float naturalRegeneration(Player player,float original) { return ExtremeGear.naturalRegeneration(player,original); }
    @Override public long gearExperience(ItemStack stack,long original) { return ExtremeGear.gearExperience(stack,original); }
    @Override public int materialUpgradeIngots(ItemStack stack,int original) {
        var itemId=BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId.getNamespace().equals("modern_ite")&&itemId.getPath().startsWith("club_")?4:original;
    }
    @Override public void preserve(ItemStack source,ItemStack result) { ExtremeGear.preserve(source,result);ExtremeEnchantments.preserveStack(source,result); }
    @Override public boolean isMaintenanceRecipe(net.minecraft.world.item.crafting.CraftingRecipe recipe) { return recipe instanceof io.github.ningmeng123.modernite.gear.ProtectionTomeRecipe; }
    @Override public boolean enchantmentSupported(Holder<Enchantment> enchantment,boolean original) { return ExtremeEnchantments.supported(enchantment,original); }
    @Override public boolean enchantmentApplicable(String id,ItemStack stack,boolean original) { return ExtremeEnchantments.applicable(id,stack,original); }
    @Override public boolean enchantmentsCompatible(String left,String right,boolean original) { return ExtremeEnchantments.compatible(left,right,original); }
    @Override public io.github.ningmeng123.mite.progression.GearAffixRules.Definition affixDefinition(ItemStack stack,Holder<Enchantment> enchantment,io.github.ningmeng123.mite.progression.GearAffixRules.Definition original) { return ExtremeEnchantments.affixDefinition(stack,enchantment,original); }
    @Override public double maximumHealthAttributeRange(double original) { return 1_000_000D; }
    @Override public double mobHealthLimit(Mob mob,double original) {
        if(mob instanceof io.github.ningmeng123.modernite.entity.ExtremeMob extreme&&extreme.extremeKind()==io.github.ningmeng123.modernite.entity.ExtremeKind.MIMIC_ZOMBIE)
            return io.github.ningmeng123.modernite.entity.MobAbilities.MIMIC_HEALTH;
        return mob.getPersistentData().getBoolean("modern_ite:boss")?1_000_000D:original;
    }
    @Override public R196FurnaceRules.Profile furnaceProfile(BlockState state,R196FurnaceRules.Profile original) { return state.is(ExtremeContent.VIBRANIUM_FURNACE.get())?new R196FurnaceRules.Profile(5,true,200):original; }
    @Override public R196FurnaceRules.Fuel fuel(ItemStack stack,R196FurnaceRules.Fuel original) { return stack.is(ExtremeContent.BLAZE_COAL.get())?new R196FurnaceRules.Fuel(6800,5):original; }
    @Override public int requiredHeat(ItemStack stack,int original) {
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if(id.equals("modern_ite:nether_adamantium_ore"))return 4;
        if(id.equals("mite:vibranium_block")||id.equals("mite:adamantium_block"))return 5;
        if(id.equals("mite:mithril_block")||id.equals("mite:ancient_metal_block"))return 4;
        if(id.equals("minecraft:iron_block")||id.equals("minecraft:gold_block")||id.equals("minecraft:copper_block")||id.equals("mite:silver_block")||id.equals("modern_ite:fancy_red_ore"))return 3;
        if(id.equals("minecraft:coal_block")||id.startsWith("modern_ite:gem_shard"))return 3;
        return original;
    }
    @Override public void furnaceCooked(AbstractFurnaceBlockEntity furnace) {
        if(furnace instanceof VibraniumFurnaceEntity&&furnace.getLevel()!=null&&furnace.getLevel().random.nextFloat()<.05F){var output=furnace.getItem(2);if(!output.isEmpty()&&output.getCount()<output.getMaxStackSize())output.grow(1);}
    }
}
