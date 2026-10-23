package io.github.ningmeng123.modernite.config;

import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.world.ConfigurableNetherOre;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Settings validation, immutable synchronization and actual placement candidates. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class IteConfigGameTests {
    private IteConfigGameTests() {}

    @GameTest(template="gear_empty",batch="ite_config")
    public static void settingsOnlyExposeTheAuditedImplementedKeys(GameTestHelper h) {
        Set<String> known=Set.of(("first_day_longer_day_time in_rain_debuff_time player_enchant_defense "
                +"player_defense_cooldown player_defence_max_time underworld_random_teleport underworld_random_teleport_time_new "
                +"netherrack_damage netherrack_damage_limit_day crit_enchantment_chance_boost_per_lvl crit_enchantment_damage_boost_per_lvl "
                +"emergency_cooldown_ emergency_cooldown_reduce_every_armor emergency_health_recover_amount conqueror_damage_boost_per_lvl "
                +"beheading_chance_boost_per_lvl mob_spawner_speed_up_with_players mob_spawner_extra_delay_reduction enhance_limit skeleton_triple_shot compressed_skeleton "
                +"reference_spawning mob_max_spawn_count_chunk_base mob_max_spawn_count_day_minimum mob_max_spawn_count_day_maximum local_hostile_limit blood_moon_local_hostile_limit hostile_spawn_groups blood_moon_hostile_spawn_groups hostile_spawn_attempts "
                +"compressed_skeleton_crack_stand_time compressed_skeleton_expand_count skeleton_force_melee_attack "
                +"bone_lord_and_longdead_change_weapon_chance bat_poison_attack zombie_pigman_use_bow zombie_pigman_boost_chance "
                +"ghast_overworld_boost wolves_attack_players_with_meat can_boost_iron_golem annihilation_skeleton_despawn_time "
                +"ender_dragon_attack_wither_chance ender_dragon_immune_to_arrow_health_percent ender_dragon_attack_slowness "
                +"nether_mod_attack_fire_chance slime_attack_damage_multiplier slime_attack_damage_bonus giant_zombie_spawn_zombie_cooldown "
                +"ghast_spawn_limit_day annihilation_skeleton_spawn_in_light wandering_witch_spawn_chance_overworld "
                +"wandering_witch_spawn_limit_day_overworld wandering_witch_spawn_limit_day_other wandering_witch_spawn_chance_underworld "
                +"zombie_lord_spawn_frequency zombie_pigman_lord_spawn_frequency annihilation_skeleton_spawn_frequency wandering_witch_spawn_frequency "
                +"nether_adamantium_max_count_per_chunk nether_adamantium_max_count_per_vein zombie_pigman_attack_animails "
                +"in_wall_damage_for_player stepped_mob_damage_factor stepped_mob_damage_progress_max stepped_mob_damage_progress_increase_day stepped_player_damage_progress stepped_player_damage_increase_per_lvl fishing_xp player_disarm_player blood_moon_max_hostile_fraction ancient_bone_lord_spawn_limit_day mob_max_spawn_count_increase_per_day underworld_debuff underworld_debuff_period1 underworld_debuff_period2 nether_debuff nether_debuff_time quartz_max_exp_level diamond_exp emerald_exp quartz_exp lapis_exp golden_apple_eat_time enchanted_golden_apple_eat_time iron_to_mithril_cook_time iron_block_count_to_mithril mithril_to_adamantium_cook_time mithril_block_count_to_adamantium all_protection_v_defence_fraction bone_lord_tweak infernal_creeper_boost villager_wool_to_emerald_shard_count villager_wool_to_emerald_shard_shard_count mob_defense mob_attack_cause_fire mob_disarm_when_defence animal_ill_to_death_time breed_xp_chicken breed_xp_sheep breed_xp_pig breed_xp_cow log_players_interact_with_portal").split(" "));
        var actual=new HashSet<String>();
        for(var option:IteConfig.options()) {
            h.assertTrue(actual.add(option.key()),"Each public setting key is unique: "+option.key());
            h.assertTrue(option.path().getLast().equals(option.key()),"Config paths retain their old JSON leaf keys");
        }
        h.assertTrue(actual.equals(known)&&actual.equals(IteConfig.defaults().keySet()),"The schema exactly matches the audited implemented settings");
        h.assertTrue(actual.size()==102,"All 102 selected extension settings are registered");
        for(String unsupported:Set.of("overworld_adamantite_ore","underworld_mantle_block_offset","copper_frequency_overworld","copper_chance_underworld"))
            h.assertTrue(!actual.contains(unsupported),"An unimplemented legacy effect is not exposed as a nonfunctional control: "+unsupported);
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void quartzSacrificeDefaultsToThirtyFiveWithoutOverwritingExplicitRewards(GameTestHelper helper) {
        helper.assertTrue(IteConfig.defaults().get("quartz_exp").equals(35)
                && IteConfig.setting("quartz_exp").getDefault().equals(35), "Both the schema and generated config use thirty-five quartz sacrifice experience");
        helper.assertTrue(IteConfig.validated(Map.of()).get("quartz_exp").equals(35)
                && IteConfig.validated(Map.of("quartz_exp",-1)).get("quartz_exp").equals(35), "Missing or invalid rewards fall back to the new default");
        for(int reward:new int[]{0,35,50,99})
            helper.assertTrue(IteConfig.validated(Map.of("quartz_exp",reward)).get("quartz_exp").equals(reward),
                    "Explicit existing and customized quartz rewards remain authoritative, including zero disabling it");
        helper.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void invalidNetworkValuesFallBackWithoutAddingUnknownSettings(GameTestHelper h) {
        var invalid=new HashMap<String,Object>();
        invalid.put("in_rain_debuff_time",-1);invalid.put("conqueror_damage_boost_per_lvl",Double.NaN);
        invalid.put("underworld_random_teleport_time_new",3.5D);invalid.put("netherrack_damage","false");
        invalid.put("beheading_chance_boost_per_lvl",Double.POSITIVE_INFINITY);invalid.put("unknown_setting",12);
        var validated=IteConfig.validated(invalid);var defaults=IteConfig.defaults();
        for(String key:invalid.keySet())if(defaults.containsKey(key))
            h.assertTrue(validated.get(key).equals(defaults.get(key)),"Invalid values use the safe default: "+key);
        h.assertTrue(validated.keySet().equals(defaults.keySet()),"Unknown input never becomes a setting");
        var boundaries=IteConfig.validated(Map.of("player_defense_cooldown",0,"beheading_chance_boost_per_lvl",1D,
                "nether_adamantium_max_count_per_vein",64));
        h.assertTrue(boundaries.get("player_defense_cooldown").equals(0)&&boundaries.get("beheading_chance_boost_per_lvl").equals(1D)
                &&boundaries.get("nether_adamantium_max_count_per_vein").equals(64),"Valid zero, probability one and maximum vein size remain valid");
        h.assertTrue(immutable(validated)&&immutable(defaults)&&immutable(IteConfig.localSnapshot()),"All published settings maps reject mutation");
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void configPayloadRoundTripsTypedValuesAndDisconnectRestoresLocalSettings(GameTestHelper h) {
        var before=IteConfig.effectiveSnapshot();boolean localBefore=before==IteConfig.localSnapshot();
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var source=new HashMap<>(IteConfig.localSnapshot());source.put("in_rain_debuff_time",17);
            source.put("conqueror_damage_boost_per_lvl",2.125D);source.put("netherrack_damage",false);
            var payload=new IteConfigSync(source);source.put("in_rain_debuff_time",99);
            h.assertTrue(payload.values().get("in_rain_debuff_time").equals(17)&&immutable(payload.values()),"Payload construction detaches and freezes the sender's map");
            IteConfigSync.CODEC.encode(buffer,payload);var decoded=IteConfigSync.CODEC.decode(buffer);
            h.assertTrue(decoded.values().get("in_rain_debuff_time") instanceof Integer,"JSON numeric decoding restores integer settings to their schema type");
            IteConfig.receiveServerSnapshot(decoded.values());
            h.assertTrue(IteConfig.integer("in_rain_debuff_time")==17&&IteConfig.number("conqueror_damage_boost_per_lvl")==2.125D
                    &&!IteConfig.bool("netherrack_damage"),"The received server values become the effective integer, double and false settings");
            h.assertTrue(immutable(IteConfig.effectiveSnapshot()),"The effective server map is immutable as well");
            IteConfig.clearServerSnapshot();
            h.assertTrue(IteConfig.effectiveSnapshot()==IteConfig.localSnapshot(),"The same clear operation used by client disconnect restores the local snapshot");
        }finally{buffer.release();if(localBefore)IteConfig.clearServerSnapshot();else IteConfig.receiveServerSnapshot(before);}
        h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void configurableNetherOreUsesActualCandidateCountAndVeinConfiguration(GameTestHelper h) {
        Object count=IteConfig.setting("nether_adamantium_max_count_per_chunk").get();
        Object vein=IteConfig.setting("nether_adamantium_max_count_per_vein").get();
        try {
            var placement=new ConfigurableNetherOre.Count();BlockPos origin=new BlockPos(12,32,8);
            IteConfig.setting("nether_adamantium_max_count_per_chunk").set(0);IteConfig.refresh();
            h.assertTrue(placement.getPositions(null,RandomSource.create(123),origin).count()==0,"Zero configured attempts emits no actual placement candidates");
            IteConfig.setting("nether_adamantium_max_count_per_chunk").set(7);
            IteConfig.setting("nether_adamantium_max_count_per_vein").set(11);IteConfig.refresh();
            var positions=placement.getPositions(null,RandomSource.create(123),origin).toList();
            h.assertTrue(positions.size()==7&&positions.stream().allMatch(origin::equals),"The native repeating placement emits exactly seven candidates before later spread and height modifiers");
            var target=OreConfiguration.target(new BlockMatchTest(Blocks.NETHERRACK),ExtremeContent.NETHER_ADAMANTIUM.get().defaultBlockState());
            var original=new OreConfiguration(java.util.List.of(target),3,.25F);var configured=ConfigurableNetherOre.configured(original);
            h.assertTrue(configured.size==11&&configured.targetStates.equals(original.targetStates)&&configured.discardChanceOnAirExposure==.25F,
                    "The live vein configuration changes size while retaining real replacement targets and exposure chance");
            h.assertTrue(original.size==3,"An existing shared configured feature is not mutated");
        }finally{
            IteConfig.setting("nether_adamantium_max_count_per_chunk").set(count);
            IteConfig.setting("nether_adamantium_max_count_per_vein").set(vein);IteConfig.refresh();
        }h.succeed();
    }

    @GameTest(template="gear_empty",batch="ite_config")
    public static void spawningRulesRefreshCapsDensityAndAttemptsWithoutReloadingWorld(GameTestHelper h) {
        var level=h.getLevel().getServer().overworld();long time=level.getDayTime();
        var history=io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData.get(level);
        long highest=history.highestDay();boolean dirty=history.isDirty();
        boolean grace=io.github.ningmeng123.modernite.world.ExtremeWorldState.graceEnded(level);
        var before=IteConfig.localSnapshot();
        var changes=Map.<String,Object>ofEntries(
                Map.entry("reference_spawning",false),
                Map.entry("mob_max_spawn_count_chunk_base",20),Map.entry("mob_max_spawn_count_day_minimum",30),
                Map.entry("mob_max_spawn_count_day_maximum",250),Map.entry("mob_max_spawn_count_increase_per_day",2D),
                Map.entry("blood_moon_max_hostile_fraction",3D),Map.entry("local_hostile_limit",7),
                Map.entry("blood_moon_local_hostile_limit",9),Map.entry("hostile_spawn_groups",6),
                Map.entry("blood_moon_hostile_spawn_groups",8),Map.entry("hostile_spawn_attempts",2));
        var policy=io.github.ningmeng123.mite.api.MiteExtensions.policy();
        var context=new io.github.ningmeng123.mite.api.MiteExtensions.SpawnContext(level,
                io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER,false);
        try {
            changes.forEach((key,value)->IteConfig.setting(key).set(value));IteConfig.refresh();
            level.setDayTime(144*24000L);
            io.github.ningmeng123.modernite.world.ExtremeWorldState.restoreGraceEnded(level,true);
            h.assertTrue(policy.monsterCap(level,false,256,0)==290&&policy.monsterCap(level,true,256,0)==290,
                    "Live ordinary caps use the configured chunk base and capped date allowance with a shared height budget");
            h.assertTrue(policy.localDensity(level,null,0)==7&&policy.spawnGroups(context,3)==6&&policy.spawnAttempts(context,null,4)==2,
                    "The actual natural-spawn policy immediately reads refreshed density, groups and attempts");
            level.setDayTime(143*24000L);
            h.assertTrue(policy.monsterCap(level,false,256,0)==870&&policy.localDensity(level,null,0)==9&&policy.spawnGroups(context,3)==8,
                    "Blood dates multiply the final configured cap and use their own local density and groups");
            IteConfig.setting("mob_max_spawn_count_day_maximum").set(0);IteConfig.refresh();
            h.assertTrue(policy.monsterCap(level,false,256,0)==984,"Zero date maximum removes the ceiling rather than disabling growth");
            IteConfig.setting("hostile_spawn_attempts").set(0);IteConfig.refresh();
            h.assertTrue(policy.spawnAttempts(context,null,4)==0,"Zero attempts stops the active spawning loop without removing monsters");
            level.setDayTime(1000);history.restoreProgress(1);
            io.github.ningmeng123.modernite.world.ExtremeWorldState.restoreGraceEnded(level,false);
            h.assertTrue(policy.monsterCap(level,false,256,0)==0&&policy.spawnGroups(context,3)==0,
                    "Live custom population values cannot bypass the existing grace period");
        } finally {
            changes.keySet().forEach(key->IteConfig.setting(key).set(before.get(key)));IteConfig.refresh();
            level.setDayTime(time);history.restoreProgress(highest);history.setDirty(dirty);
            io.github.ningmeng123.modernite.world.ExtremeWorldState.restoreGraceEnded(level,grace);
        }
        h.succeed();
    }

    private static boolean immutable(Map<String,Object> map) {
        try{map.put("unknown_setting",0);return false;}catch(UnsupportedOperationException expected){return true;}
    }
}
