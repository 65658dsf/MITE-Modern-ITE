package io.github.ningmeng123.modernite.config;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Implemented ITE settings, preserving the current port's defaults. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID, bus=EventBusSubscriber.Bus.MOD)
public final class IteConfig {
    public record Option(String key, List<String> path, Object defaultValue, double minimum, double maximum) {
        public Option { path=List.copyOf(path); }
        public Object validated(Object input) {
            if(defaultValue instanceof Boolean) return input instanceof Boolean ? input : defaultValue;
            if(!(input instanceof Number number)) return defaultValue;
            double value=number.doubleValue();
            if(!Double.isFinite(value)||value<minimum||value>maximum) return defaultValue;
            if(defaultValue instanceof Integer) return value==Math.rint(value) ? Integer.valueOf((int)value) : defaultValue;
            return Double.valueOf(value);
        }
    }
    private static final List<Option> OPTIONS=new ArrayList<>();
    private static final Map<String,ModConfigSpec.ConfigValue<?>> SETTINGS=new LinkedHashMap<>();
    public static final ModConfigSpec SPEC;
    private static volatile Map<String,Object> local;
    private static volatile Map<String,Object> remote;
    static {
        var builder=new ModConfigSpec.Builder();
        add(builder, "GameMechanics", "first_day_longer_day_time", true, 0D, 1D, "前期双倍白天。历史最高日期达到第32天前，白天日历以半速推进；夜晚、AI和生产速度不变。");
        add(builder, "GameMechanics", "in_rain_debuff_time", 3600, 1D, 100000000D, "淋雨虚弱阈值（tick）。累计淋雨达到此时间后施加虚弱；在遮蔽处计数逐tick减少。");
        add(builder, "GameMechanics", "player_enchant_defense", true, 0D, 1D, "武器格挡需要附魔。关闭后，原本支持格挡附魔的振金工具可直接格挡；不改变武器适用范围。");
        add(builder, "GameMechanics", "player_defense_cooldown", 160, 0D, 100000000D, "武器格挡冷却（tick）。结束武器格挡后施加的物品冷却。");
        add(builder, "GameMechanics", "player_defence_max_time", 40, 1D, 100000000D, "武器格挡最大时间（tick）。只控制本拓展的武器格挡，不改变原生盾牌。");
        add(builder, "GameMechanics.Underworld", "underworld_random_teleport", true, 0D, 1D, "地底随机传送。关闭会清除待传送坐标并停止累计随机传送计时。");
        add(builder, "GameMechanics.Underworld", "underworld_random_teleport_time_new", 132000, 1D, 100000000D, "地底随机传送间隔（tick）。离开地底后计数逐tick减少；仅传送到同维度的安全落点。");
        add(builder, "GameMechanics.Nether", "netherrack_damage", true, 0D, 1D, "下界地狱岩热伤。保留当前游戏默认开启；旧JSON样本中的false不会自动覆盖它。");
        add(builder, "GameMechanics.Nether", "netherrack_damage_limit_day", 96, 0D, 1000000D, "地狱岩热伤开始日期（天）。按主世界当前显示日期检查；每21个接触tick造成一次火伤。");
        add(builder, "Item.Enchantment", "crit_enchantment_chance_boost_per_lvl", 20D, 0D, 100D, "暴击每级几率（%）。20代表每级20%，不是2%或0.2%；总概率最多100%。");
        add(builder, "Item.Enchantment", "crit_enchantment_damage_boost_per_lvl", 0.4D, 0D, 10000D, "暴击每级额外伤害。玩家与持有暴击附魔的怪物共用此数值；暴击倍率仍为1.5。");
        add(builder, "Item.Enchantment", "emergency_cooldown_", 120000, 0D, 100000000D, "紧急守备基础冷却（tick）。新触发时使用新数值；已写入装备的剩余冷却会保留。");
        add(builder, "Item.Enchantment", "emergency_cooldown_reduce_every_armor", 0.2D, 0D, 1D, "紧急守备每件额外装备冷却减免。0.2代表20%；第一件以外每件就绪护甲减少此比例，最终冷却至少0。");
        add(builder, "Item.Enchantment", "emergency_health_recover_amount", 10D, 0.01D, 1000000D, "紧急守备恢复至的生命值。实际恢复不会超过玩家最大生命值。");
        add(builder, "Item.Enchantment", "conqueror_damage_boost_per_lvl", 1D, 0D, 10000D, "征服者每级每次叠加伤害。额外伤害=附魔等级×该玩家此前对同一目标成功近战命中次数×此值；与玩家渐进伤害加算，共用成功命中计数。");
        add(builder, "Item.Enchantment", "beheading_chance_boost_per_lvl", 0.05D, 0D, 1D, "斩首每级额外头颅掉落概率。0.05代表5%，总概率最多100%。");
        add(builder, "Block", "mob_spawner_speed_up_with_players", true, 0D, 1D, "玩家近场加速刷怪笼。玩家在近场时，每tick额外减少2tick刷怪倒计时；旁观者除外。");
        add(builder, "Entities", "enhance_limit", 366, 0D, 1000000D, "特殊生物属性成长天数上限。控制本拓展特殊生物的种类专属成长曲线；本体通用怪物成长仍由主模组设置控制。");
        add(builder, "Entities", "skeleton_triple_shot", true, 0D, 1D, "三发骷髅。允许已抽中三发特性的骷髅发射额外两支箭。");
        add(builder, "Entities", "compressed_skeleton", true, 0D, 1D, "分裂骷髅。允许带分裂特性的骷髅死亡后召唤随从，仍服从召援数量与密度限制。");
        add(builder, "Entities", "compressed_skeleton_crack_stand_time", 20, 1D, 100000000D, "分裂骷髅停滞时间（tick）。分裂后随从的虚弱、缓慢与抗性持续时间。");
        add(builder, "Entities", "compressed_skeleton_expand_count", 4, 0D, 64D, "分裂骷髅分裂数量。当前默认4；召援系统仍限制实际成功数量，不保证全部生成。");
        add(builder, "Entities", "skeleton_force_melee_attack", true, 0D, 1D, "允许强制骷髅近战。控制带近战特性的骷髅；不强制所有骷髅近战。");
        add(builder, "Entities", "bone_lord_and_longdead_change_weapon_chance", 0.25D, 0D, 1D, "古尸和骷髅领主切换武器概率。0.25代表25%；对新抽取切换特性的生物生效。");
        add(builder, "Entities", "bat_poison_attack", true, 0D, 1D, "吸血蝙蝠攻击中毒。关闭仅禁用本拓展添加的中毒效果。");
        add(builder, "Entities", "zombie_pigman_use_bow", true, 0D, 1D, "允许僵尸猪人使用弓。开启时保留当前的带弓抽取概率。");
        add(builder, "Entities", "zombie_pigman_boost_chance", 0.025D, 0D, 1D, "精英猪人生成概率。0.025代表2.5%，对新抽取特性的猪人生效。");
        add(builder, "Entities", "ghast_overworld_boost", 1.25D, 0D, 100D, "主世界恶魂火球爆炸范围倍率。控制本拓展火球计算中的主世界增强系数。");
        add(builder, "Entities", "wolves_attack_players_with_meat", true, 0D, 1D, "野狼攻击手持肉的玩家。按当前有效逻辑检查手持肉，不扫描整个快捷栏。");
        add(builder, "Entities", "can_boost_iron_golem", true, 0D, 1D, "允许强化铁傀儡。关闭时暂停已有强化的额外20生命、3攻击及体型；再次开启恢复加成，不额外回血。");
        add(builder, "Entities", "annihilation_skeleton_despawn_time", 3600, 1D, 100000000D, "湮灭骷髅受击后消失时间（tick）。从其受攻击进入消失阶段后开始计时。");
        add(builder, "Entities", "ender_dragon_attack_wither_chance", 0.2D, 0D, 1D, "末影龙受击反击凋零概率。0.2代表20%。");
        add(builder, "Entities", "ender_dragon_immune_to_arrow_health_percent", 0.3D, 0D, 1D, "末影龙免疫箭矢生命比例。0.3代表生命低于30%时免疫箭矢。");
        add(builder, "Entities", "ender_dragon_attack_slowness", true, 0D, 1D, "末影龙攻击附加缓慢。控制本拓展添加的龙攻击缓慢效果。");
        add(builder, "Entities", "nether_mod_attack_fire_chance", 0.5D, 0D, 1D, "下界生物攻击点燃概率。0.5代表50%，包括当前实装的火焰攻击路径。");
        add(builder, "Entities", "slime_attack_damage_multiplier", 1D, 0D, 100D, "绿色史莱姆伤害倍率。初始化时按原始伤害×倍率+加成计算；不反复叠加。");
        add(builder, "Entities", "slime_attack_damage_bonus", 4D, 0D, 10000D, "绿色史莱姆伤害加成。仅绿色史莱姆；对新初始化的生物生效。");
        add(builder, "Entities", "giant_zombie_spawn_zombie_cooldown", 1200, 1D, 100000000D, "巨型僵尸召援间隔（tick）。仍保留召援总量、存活随从和局部密度限制。");
        add(builder, "GameMechanics.MobSpawning", "ghast_spawn_limit_day", 128, 1D, 1000000D, "主世界恶魂开始生成日期（天）。仅影响本拓展添加的主世界自然生成入口。");
        add(builder, "GameMechanics.MobSpawning", "annihilation_skeleton_spawn_in_light", false, 0D, 1D, "允许主世界湮灭骷髅在亮处生成。仅放宽该生物的光照检查，仍受平安期、距离和领主日期限制。");
        add(builder, "GameMechanics.MobSpawning", "wandering_witch_spawn_chance_overworld", 33, 0D, 100D, "主世界女巫生成通过率（%）。每次符合其他条件的女巫族自然生成尝试的通过率。");
        add(builder, "GameMechanics.MobSpawning", "wandering_witch_spawn_limit_day_overworld", 65, 1D, 1000000D, "主世界流浪女巫开始日期（天）。保留当前第65天默认值；统一领主解锁下限继续生效。");
        add(builder, "GameMechanics.MobSpawning", "wandering_witch_spawn_limit_day_other", 65, 1D, 1000000D, "地底流浪女巫开始日期（天）。当前流浪女巫不在下界自然生成；统一领主解锁下限继续生效。");
        add(builder, "GameMechanics.MobSpawning", "wandering_witch_spawn_chance_underworld", 50, 0D, 100D, "地底女巫生成通过率（%）。每次符合其他条件的女巫族自然生成尝试的通过率。");
        add(builder, "GameMechanics.MobSpawning", "zombie_lord_spawn_frequency", 2, 0D, 1000D, "僵尸领主生成权重。0禁用对应自然池条目；保留其他召援或事件入口。");
        add(builder, "GameMechanics.MobSpawning", "zombie_pigman_lord_spawn_frequency", 1, 0D, 1000D, "僵尸猪人领主生成权重。0禁用对应自然池条目；不改变其他来源的生成。");
        add(builder, "GameMechanics.MobSpawning", "annihilation_skeleton_spawn_frequency", 1, 0D, 1000D, "湮灭骷髅生成权重。0禁用对应自然池条目。");
        add(builder, "GameMechanics.MobSpawning", "wandering_witch_spawn_frequency", 1, 0D, 1000D, "流浪女巫生成权重。0禁用对应自然池条目。");
        add(builder, "WorldGen.Nether", "nether_adamantium_max_count_per_chunk", 4, 0D, 1000D, "下界艾德曼矿脉尝试次数／区块。只影响新生成区块；这是候选矿脉次数，不是最终矿石方块数量。");
        add(builder, "WorldGen.Nether", "nether_adamantium_max_count_per_vein", 3, 1D, 64D, "下界艾德曼矿脉尺寸。只影响新生成区块；受地形、替换目标与原生矿脉算法影响。");
        add(builder, "Entities", "zombie_pigman_attack_animails", false, 0D, 1D, "僵尸猪人攻击动物。关闭保持当前默认目标选择；开启后允许猪人选择动物为攻击目标。");
        add(builder, "GameMechanics", "in_wall_damage_for_player", 1D, 0D, 1000000D, "玩家墙内窒息伤害。每次墙内窒息结算的基础伤害；0禁用，默认保留当前1点。");
        add(builder, "GameMechanics", "stepped_mob_damage_factor", 0.5D, 0D, 10000D, "怪物连击伤害初始系数。同一怪物此前成功伤害此玩家次数×min(系数上限,初始系数+世界日期/增长天数)。");
        add(builder, "GameMechanics", "stepped_mob_damage_progress_max", 2D, 0D, 10000D, "怪物连击伤害系数上限。0禁用怪物连击加伤；末影龙和史莱姆族保持旧规则豁免。");
        add(builder, "GameMechanics", "stepped_mob_damage_progress_increase_day", 50, 1D, 1000000D, "怪物连击系数每增加1所需天数。日期使用主世界当前显示日期；所有数值参与真实加伤。");
        add(builder, "GameMechanics", "stepped_player_damage_progress", 2D, 0D, 10000D, "玩家无附魔连击伤害上限。持工具近战同一怪物，额外min(上限,此前成功命中次数×玩家等级×每级加成)，与征服者加伤相加。");
        add(builder, "GameMechanics", "stepped_player_damage_increase_per_lvl", 0.1D, 0D, 10000D, "玩家连击每等级伤害系数。0禁用无附魔连击部分；不禁用征服者附魔本身。");
        add(builder, "GameMechanics", "fishing_xp", 30, 0D, 1000000D, "每次钓获经验。替换每个实际钓获物结算的原生随机经验，0不产生钓获经验。");
        add(builder, "GameMechanics", "player_disarm_player", true, 0D, 1D, "玩家可以缴械玩家。持有缴械附魔近战时按原附魔概率缴落对方主手物品；格挡、创造和旁观者不受影响。");
        add(builder, "GameMechanics.MobSpawning", "blood_moon_max_hostile_fraction", 3D, 0D, 1000D, "血月怪物总额度倍率。在当前共享刷怪额度公式的最后乘此倍率，默认3保留当前平衡；0禁用血月自动敌对额度。");
        add(builder, "GameMechanics.MobSpawning", "ancient_bone_lord_spawn_limit_day", 33, 1D, 1000000D, "远古骷髅公爵自动生成日期。仅覆盖该生物的自动生成门槛，其他领主仍第33天；生成蛋与命令不受限制。");
        add(builder, "GameMechanics.MobSpawning", "mob_max_spawn_count_increase_per_day", 1D, 0D, 10000D, "日期刷怪额度增长系数。当前总额度为floor(50×有效区块/128)+max(100,当前日期×此值)，血月再乘血月倍率。");
        add(builder, "GameMechanics.Underworld", "underworld_debuff", true, 0D, 1D, "地底疲劳开关。控制现有地底惩罚和新增第二阶段；关闭清理本机制计时与状态，不移除其他来源药效。");
        add(builder, "GameMechanics.Underworld", "underworld_debuff_period1", 54000, 1D, 100000000D, "地底疲劳第一阶段时间（tick）。默认54000保留当前首阶段；达到阈值产生缓慢I。");
        add(builder, "GameMechanics.Underworld", "underworld_debuff_period2", 108000, 1D, 100000000D, "地底疲劳第二阶段时间（tick）。达到max(第一阶段,此值)后升级为缓慢II，离开一天按现有规则恢复。");
        add(builder, "GameMechanics.Nether", "nether_debuff", true, 0D, 1D, "下界疲劳开关。在下界实际累计停留时间，达到阈值产生挖掘疲劳II；离开后计时逐tick减少。");
        add(builder, "GameMechanics.Nether", "nether_debuff_time", 72000, 1D, 100000000D, "下界疲劳触发时间（tick）。旧计时器缺少递增路径，现在以真实停留时间触发；计时随玩家保存和复制。");
        add(builder, "Item", "quartz_max_exp_level", 55, 0D, 1000000D, "石英献祭等级上限。玩家等级达到此值不再允许石英献祭；0完全禁用石英献祭。");
        add(builder, "Item", "diamond_exp", 500, 0D, 100000000D, "钻石献祭经验。保留本体当前500默认，0禁用此宝石献祭。");
        add(builder, "Item", "emerald_exp", 250, 0D, 100000000D, "绿宝石献祭经验。保留本体当前250默认，0禁用此宝石献祭。");
        add(builder, "Item", "quartz_exp", 50, 0D, 100000000D, "石英献祭经验。保留本体当前50默认，并受石英等级上限限制。");
        add(builder, "Item", "lapis_exp", 25, 0D, 100000000D, "青金石献祭经验。保留本体当前25默认，0禁用此宝石献祭。");
        add(builder, "Item", "golden_apple_eat_time", 32, 1D, 100000000D, "金苹果食用时间（tick）。只改变金苹果使用耗时；默认32保留现有食物规则，不更改营养与药效。");
        add(builder, "Item", "enchanted_golden_apple_eat_time", 32, 1D, 100000000D, "附魔金苹果食用时间（tick）。只改变附魔金苹果使用耗时；默认32保留现状。");
        add(builder, "Item.Recipes", "iron_to_mithril_cook_time", 6000, 1D, 100000000D, "金属升级炉铁转秘银耗时（tick）。独立金属升级炉配方，不替换普通熔炉烧制宝石碎片配方。");
        add(builder, "Item.Recipes", "iron_block_count_to_mithril", 2, 1D, 4D, "金属升级铁块消耗数量。输入铁块，产出1秘银锭；数量不超过本体铁块原生堆叠上限4。");
        add(builder, "Item.Recipes", "mithril_to_adamantium_cook_time", 9000, 1D, 100000000D, "金属升级炉秘银转艾德曼耗时（tick）。独立升级配方，要求相应热级燃料。");
        add(builder, "Item.Recipes", "mithril_block_count_to_adamantium", 1, 1D, 4D, "金属升级秘银块消耗数量。输入秘银块，产出1艾德曼锭；数量不超过本体原生堆叠上限4。");
        add(builder, "Item.Enchantment", "all_protection_v_defence_fraction", 0.1D, 0D, 1D, "全套保护V额外减伤比例。四个正确护甲槽每件普通或成长保护至少V；在原生护甲与附魔结算后额外减伤，0.1为10%。");
        add(builder, "Entities", "bone_lord_tweak", true, 0D, 1D, "骷髅领主增强。控制本拓展为本体骷髅领主添加的增强；不关闭本体通用日期成长。");
        add(builder, "Entities", "infernal_creeper_boost", true, 0D, 1D, "地狱爬行者增强。控制本拓展添加的地狱爬行者增强，不改变独立黑色地狱爬行者身份。");
        add(builder, "Entities", "villager_wool_to_emerald_shard_count", 4, 1D, 64D, "羊毛换碎片所需羊毛数量。牧羊人羊毛收购改为绿宝石碎片，实际报价使用此数量。");
        add(builder, "Entities", "villager_wool_to_emerald_shard_shard_count", 1, 1D, 64D, "羊毛交易获得绿宝石碎片数。产出使用本体注册的绿宝石碎片物品，不额外重复添加交易。");
        add(builder, "Entities", "mob_defense", true, 0D, 1D, "怪物格挡。关闭时停止怪物武器与盾牌格挡；保留目标选择与普通攻击。");
        add(builder, "Entities", "mob_attack_cause_fire", true, 0D, 1D, "怪物持械攻击概率点燃。控制持械怪物额外点燃路径；下界生物的独立点燃概率仍用原设置。");
        add(builder, "Entities", "mob_disarm_when_defence", true, 0D, 1D, "怪物使用缴械附魔。控制怪物攻击和格挡时的缴械附魔效果，不禁用玩家对怪物缴械。");
        add(builder, "Entities.Animals", "animal_ill_to_death_time", 144000, 0D, 100000000D, "动物患病死亡倒计时（tick）。只有本体判定患病的牲畜累计；康复逐tick消退，随实体保存，0禁用病死。");
        add(builder, "Entities.Animals", "breed_xp_chicken", 15, 0D, 1000000D, "鸡繁殖经验。只在真实繁殖成功且允许生物掉落时结算。");
        add(builder, "Entities.Animals", "breed_xp_sheep", 30, 0D, 1000000D, "羊繁殖经验。只替换该物种原生繁殖经验，不额外叠加经验球。");
        add(builder, "Entities.Animals", "breed_xp_pig", 25, 0D, 1000000D, "猪繁殖经验。0不产生繁殖经验。");
        add(builder, "Entities.Animals", "breed_xp_cow", 45, 0D, 1000000D, "牛繁殖经验。0不产生繁殖经验。");
        add(builder, "Misc", "log_players_interact_with_portal", true, 0D, 1D, "玩家成功使用传送门时广播。只在本体传送门真正完成传送后广播，查询、失败、取消不广播。");
        SPEC=builder.build();
        local=defaults();
    }
    private IteConfig() {}
    private static void add(ModConfigSpec.Builder builder,String category,String key,Object fallback,double min,double max,String comment) {
        var path=new ArrayList<>(List.of(category.split("\\.")));path.add(key);
        OPTIONS.add(new Option(key,path,fallback,min,max));
        builder.translation("modern_ite.config."+key).comment(comment);
        ModConfigSpec.ConfigValue<?> setting;
        if(fallback instanceof Boolean flag) setting=builder.define(path,flag);
        else if(fallback instanceof Integer count) setting=builder.defineInRange(path,count,(int)min,(int)max);
        else setting=builder.defineInRange(path,((Number)fallback).doubleValue(),min,max);
        SETTINGS.put(key,setting);
    }
    private static String shortKey(String key) { return key.substring(key.lastIndexOf('.')+1); }
    public static List<Option> options() { return List.copyOf(OPTIONS); }
    public static Map<String,Object> defaults() {
        var values=new LinkedHashMap<String,Object>();OPTIONS.forEach(o->values.put(o.key(),o.defaultValue()));return Map.copyOf(values);
    }
    public static Map<String,Object> validated(Map<String,?> input) {
        var result=new LinkedHashMap<String,Object>();
        for(var option:OPTIONS) result.put(option.key(),option.validated(input==null?null:input.get(option.key())));
        return Map.copyOf(result);
    }
    public static Map<String,Object> localSnapshot() { return local; }
    public static Map<String,Object> effectiveSnapshot() { var server=remote;return server==null?local:server; }
    public static Object value(String key) {
        String name=shortKey(key);Object result=effectiveSnapshot().get(name);
        if(result==null) throw new IllegalArgumentException("Unknown ITE setting: "+key);
        return result;
    }
    public static boolean bool(String key) { return (Boolean)value(key); }
    public static int integer(String key) { return ((Number)value(key)).intValue(); }
    public static double number(String key) { return ((Number)value(key)).doubleValue(); }
    @SuppressWarnings("unchecked")
    public static ModConfigSpec.ConfigValue<Object> setting(String key) {
        var setting=SETTINGS.get(shortKey(key));
        if(setting==null) throw new IllegalArgumentException("Unknown ITE setting: "+key);
        return (ModConfigSpec.ConfigValue<Object>)setting;
    }
    /** Publish immutable values so spawning workers never read ConfigSpec caches. */
    public static void refresh() {
        var values=new LinkedHashMap<String,Object>();
        SETTINGS.forEach((key,setting)->values.put(key,SPEC.isLoaded()?setting.get():setting.getDefault()));
        local=validated(values);
    }
    public static void receiveServerSnapshot(Map<String,?> snapshot) { remote=validated(snapshot); }
    public static void clearServerSnapshot() { remote=null; }
    @SubscribeEvent public static void configChanged(ModConfigEvent event) {
        if(event.getConfig().getSpec()!=SPEC||event instanceof ModConfigEvent.Unloading) return;
        refresh();IteConfigSync.broadcast();
    }
}
