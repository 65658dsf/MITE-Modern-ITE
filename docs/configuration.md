# ITE 扩展玩法配置

开发署名：NingMeng123。

本文对应主模组 MITE: Modern 0.2.7、扩展模组 MITE: Modern ITE 0.2.7。在主模组的模组设置页左上角点击“ITE 扩展设置”，也可直接从 MITE: Modern ITE 的配置按钮进入。“编辑玩法参数”按原配置文件层级分类；“查看当前生效参数”可搜索中文名称或旧 JSON 键。

配置保存在游戏实例的 `config/modern_ite-common.toml`，同实例单机存档共用。多数开关和战斗数值保存后生效；初始化型基础属性与出生时抽取的随机特性在相应初始化或抽取时采用新值，已有特性的启用状态由对应开关控制。矿物设置只影响新生成区块。已经存入装备的紧急守备剩余冷却保留，新触发使用新设置。

多人服务器在玩家登录和配置重载时同步当前值，客户端只能查看，修改需由服主在服务端进行。没有客户端向服务端写配置的网络入口。本体常用的矿物倍率与通用怪物成长从本体页面调整；矿物原始候选次数、通过概率属于本体高级TOML参数。

扩展页面共92项，对应首批51项与本次补齐的41项，均已接入实际游戏逻辑。旧JSON作为键和说明的参考，不自动导入，也不会直接覆盖已有平衡默认值；此前已有机制保留当前默认，新加入机制采用本文表格中的默认值。单位 tick 按20 tick/秒计算。原JSON的32个主世界／地底矿物频率与概率项由本体高级矿物参数（TOML）管理，具体对应关系见后文。

“ITE 怪物自然刷新率”另设专用列表，直接读取当前实体注册表中 `modern_ite` 命名空间的怪物，显示已有自然生成权重与配置后的权重；本体和原版怪物在 MITE 页面调整。倍率在界面中以百分数显示，100% 保持现有规则，50% 为原权重的一半，0% 禁止该种怪物自然生成。它不改变现有怪物、生成蛋、命令、地牢或技能召援，也不绕过日期、维度、光照、密度与总量限制。没有自然生成入口的实体不会因设置倍率而新增入口。

倍率保存在同一配置文件的 `[GameMechanics.MobSpawning]` 下，例如 `natural_spawn_rates = ["modern_ite:zombie_chaos=0.5", "modern_ite:zombie_pigman_lord=0.0"]`。文件数值范围为 0–100 倍，对应界面 0%–10000%；未列出的实体为 1 倍，暂时不存在的 ITE ID 继续保留。现有四项 `*_spawn_frequency` 仍设置其真实原始权重，自然生成倍率在其后相乘。

僵尸猪人的自然生成池与 1/5 领主转换共同按最终实体倍率计算，关闭僵尸猪人领主的自然生成也会关闭自然猪人转成领主的路径；其他来源仍保留原规则。所有倍率为默认值时保留原有随机抽取方式。新增倍率随服务端配置一起同步，多人客户端只读。MITE 的“导出配置”在安装 ITE 时一并导出 `modern_ite-common.toml`，将两份 TOML 放入服务端 `config` 目录即可使用。

截图中的五个渐进伤害参数与征服者系数现在分别控制三部分伤害：

```text
怪物攻击玩家的额外伤害 = 此怪物此前成功伤害此玩家的次数
    × min(stepped_mob_damage_progress_max,
          stepped_mob_damage_factor + 主世界当前显示日期 / stepped_mob_damage_progress_increase_day)

玩家持工具近战怪物的基础渐进伤害 = min(stepped_player_damage_progress,
    此前成功命中次数 × max(0, 玩家经验等级) × stepped_player_damage_increase_per_lvl)

征服者额外伤害 = 此前成功命中次数 × 附魔等级 × conqueror_damage_boost_per_lvl
```

玩家基础渐进伤害与征服者加伤相加，共用该玩家对该目标的同一份命中计数，一次成功伤害只递增一次；首击没有这两部分叠伤。将基础渐进伤害的上限或每级系数设为0，不会关闭征服者。基础渐进伤害只适用于持工具直接近战怪物；征服者保留原有的玩家目标范围。每个攻击者与受害者独立计数，取消或未造成实际伤害的攻击不累计，僵尸Boss继续免疫这两种玩家加伤。怪物攻击玩家的计数另行保存，末影龙与史莱姆族豁免；每名玩家最多保存256个怪物攻击者的计数，每个计数最多100万。怪物公式使用当前显示日期，不使用历史最高日期。

全套保护V的额外减伤在原生护甲和附魔结算后应用，默认再减少10%；它的四槽判定与振金装饰光效的材质要求分别处理。

## GameMechanics

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `first_day_longer_day_time` | 前期双倍白天 | `true` | 历史最高日期达到第32天前，白天日历以半速推进；夜晚、AI和生产速度不变。 |
| `in_rain_debuff_time` | 淋雨虚弱阈值（tick） | `3600` | 累计淋雨达到此时间后施加虚弱；在遮蔽处计数逐tick减少。 |
| `player_enchant_defense` | 武器格挡需要附魔 | `true` | 关闭后，原本支持格挡附魔的振金工具可直接格挡；不改变武器适用范围。 |
| `player_defense_cooldown` | 武器格挡冷却（tick） | `160` | 结束武器格挡后施加的物品冷却。 |
| `player_defence_max_time` | 武器格挡最大时间（tick） | `40` | 只控制本拓展的武器格挡，不改变原生盾牌。 |
| `in_wall_damage_for_player` | 玩家墙内窒息伤害 | `1` | 修改原生墙内窒息入口每次造成的伤害；0禁用，其他来源伤害不变。 |
| `stepped_mob_damage_factor` | 怪物渐进伤害基础系数 | `0.5` | 与日期增量相加后，再乘同一怪物此前对该玩家的成功伤害次数。 |
| `stepped_mob_damage_progress_max` | 怪物渐进伤害系数上限 | `2` | 限制每次命中的增长系数；0禁用怪物渐进加伤。 |
| `stepped_mob_damage_progress_increase_day` | 怪物渐进系数增长天数 | `50` | 当前主世界日期除以此值后加入基础系数；三个参数均参与实际伤害计算。 |
| `stepped_player_damage_progress` | 玩家基础渐进伤害上限 | `2` | 只限制无附魔部分；征服者加伤仍独立相加。 |
| `stepped_player_damage_increase_per_lvl` | 玩家渐进伤害每级系数 | `0.1` | 持工具近战怪物时，按此前命中次数与非负经验等级计算；0仅禁用此部分。 |
| `fishing_xp` | 每次钓获经验 | `30` | 替换每个实际钓获物结算的原生随机经验，不额外叠加另一份经验；0不提供经验。 |
| `player_disarm_player` | 玩家缴械玩家 | `true` | 持缴械附魔近战，按原附魔概率掉落对方主手物品；只在成功伤害后结算，服从服务器PvP、队伍友伤、格挡及创造／旁观限制。 |

## GameMechanics.Underworld

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `underworld_random_teleport` | 地底随机传送 | `true` | 关闭会清除待传送坐标并停止累计随机传送计时。 |
| `underworld_random_teleport_time_new` | 地底随机传送间隔（tick） | `132000` | 离开地底后计数逐tick减少；仅传送到同维度的安全落点。 |
| `underworld_debuff` | 地底疲劳 | `true` | 使用原有Extreme地底计数器并增加第二阶段；关闭清理本机制计时，不移除其他来源的药效。 |
| `underworld_debuff_period1` | 地底疲劳第一阶段（tick） | `54000` | 达到时施加缓慢I，保留此前首阶段默认时间。 |
| `underworld_debuff_period2` | 地底疲劳第二阶段（tick） | `108000` | 达到两阶段阈值中的较大值时升级为缓慢II；离开地底一天后按原有机制恢复。 |

## GameMechanics.Nether

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `netherrack_damage` | 下界地狱岩热伤 | `true` | 保留当前游戏默认开启；旧JSON样本中的false不会自动覆盖它。 |
| `netherrack_damage_limit_day` | 地狱岩热伤开始日期（天） | `96` | 按主世界当前显示日期检查；每21个接触tick造成一次火伤。 |
| `nether_debuff` | 下界疲劳 | `true` | 在下界实际累计计时，离开后逐tick减少；关闭清理本机制计时。 |
| `nether_debuff_time` | 下界疲劳阈值（tick） | `72000` | 达到时施加挖掘疲劳II；计数随玩家保存和复制，已有药效按其剩余时间消退。 |

## Item

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `quartz_max_exp_level` | 石英献祭等级上限 | `55` | 玩家等级达到此值时拒绝献祭石英并显示需低于该等级的提示，不消耗物品；0完全禁用石英献祭，包括负经验状态，并显示配置禁用提示。 |
| `diamond_exp` | 钻石献祭经验 | `500` | 每颗钻石实际给予的经验；保留本体当前默认值，0禁用该献祭。 |
| `emerald_exp` | 绿宝石献祭经验 | `250` | 每颗绿宝石实际给予的经验；0禁用该献祭。 |
| `quartz_exp` | 石英献祭经验 | `35` | 新配置默认每颗35经验，同时受石英等级上限限制；0禁用该献祭并显示提示。已有配置的有效自定义值仍优先；若旧TOML仍为50，需要在模组设置改成35，不自动覆写用户配置。 |
| `lapis_exp` | 青金石献祭经验 | `25` | 每颗青金石实际给予的经验；0禁用该献祭。 |
| `golden_apple_eat_time` | 金苹果食用时间（tick） | `32` | 只改变食用耗时，保留现有营养和药效。 |
| `enchanted_golden_apple_eat_time` | 附魔金苹果食用时间（tick） | `32` | 独立控制附魔金苹果耗时，其他食物用时不变。 |

## Item.Recipes

金属升级使用独立的“金属升级炉”和 `modern_ite:metal_upgrading` 配方类型，可从对应菜单与JEI分类查看。升级炉由8个黑曜石围绕一个普通熔炉合成；铁块升级要求至少热级3的燃料，秘银块升级要求至少热级4。燃料热级与燃烧时间复用现有热控规则。原熔炉的铁块／秘银块烧制宝石碎片路线继续保留，两种用途使用不同机器与配方入口。

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `iron_to_mithril_cook_time` | 铁块升级秘银耗时（tick） | `6000` | 独立升级炉每批耗时，默认5分钟；产出1个秘银锭。 |
| `iron_block_count_to_mithril` | 每批铁块消耗 | `2` | 可设1–4，遵守铁块原生堆叠上限。 |
| `mithril_to_adamantium_cook_time` | 秘银块升级艾德曼耗时（tick） | `9000` | 独立升级炉每批耗时，默认7分30秒；产出1个艾德曼锭。 |
| `mithril_block_count_to_adamantium` | 每批秘银块消耗 | `1` | 可设1–4，遵守秘银块原生堆叠上限。 |

升级炉保存库存、燃烧和制作进度。切换配方或修改当前配方的耗时／消耗数量会清空本批进度；输入不足、热级不足或输出槽没有空间时不会完成升级。

## Item.Enchantment

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `crit_enchantment_chance_boost_per_lvl` | 暴击每级几率（%） | `20` | 20代表每级20%，不是2%或0.2%；总概率最多100%。 |
| `crit_enchantment_damage_boost_per_lvl` | 暴击每级额外伤害 | `0.4` | 玩家与持有暴击附魔的怪物共用此数值；暴击倍率仍为1.5。 |
| `emergency_cooldown_` | 紧急守备基础冷却（tick） | `120000` | 新触发时使用新数值；已写入装备的剩余冷却会保留。 |
| `emergency_cooldown_reduce_every_armor` | 紧急守备每件额外装备冷却减免 | `0.2` | 0.2代表20%；第一件以外每件就绪护甲减少此比例，最终冷却至少0。 |
| `emergency_health_recover_amount` | 紧急守备恢复至的生命值 | `10` | 实际恢复不会超过玩家最大生命值。 |
| `conqueror_damage_boost_per_lvl` | 征服者每级每次叠加伤害 | `1` | 与玩家基础渐进伤害共用命中计数，附魔部分独立加算；基础渐进上限不限制征服者，原有PvP目标范围保留。 |
| `beheading_chance_boost_per_lvl` | 斩首每级额外头颅掉落概率 | `0.05` | 0.05代表5%，总概率最多100%。 |
| `all_protection_v_defence_fraction` | 全套保护V额外减伤比例 | `0.1` | 四件护甲正确穿戴，每件普通或成长保护独立达到V；原生防御结算后再减伤10%，不要求振金材质，绕过附魔的伤害不受影响。 |

## Block

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `mob_spawner_speed_up_with_players` | 玩家近场加速刷怪笼 | `true` | 玩家在近场时，每tick额外减少2tick刷怪倒计时；旁观者除外。 |

## Entities

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `enhance_limit` | 特殊生物属性成长天数上限 | `366` | 控制本拓展特殊生物的种类专属成长曲线；本体通用怪物成长仍由主模组设置控制。 |
| `skeleton_triple_shot` | 三发骷髅 | `true` | 允许已抽中三发特性的骷髅发射额外两支箭。 |
| `compressed_skeleton` | 分裂骷髅 | `true` | 允许各类骷髅以3%概率获得死亡分裂特性，生成同类小型骷髅；幼体不再分裂，仍服从召援数量与密度限制。 |
| `compressed_skeleton_crack_stand_time` | 分裂骷髅停滞时间（tick） | `20` | 分裂后小型骷髅的虚弱、缓慢与抗性持续时间。 |
| `compressed_skeleton_expand_count` | 分裂骷髅分裂数量中值 | `7` | 实际随机取设置值±1（限制在1～64）；默认7生成6～8只，0禁用。召援和局部密度可能减少实际成功数量；已有配置值保留，需设为7才能采用新的默认数量。 |
| `skeleton_force_melee_attack` | 允许强制骷髅近战 | `true` | 控制带近战特性的骷髅；不强制所有骷髅近战。 |
| `bone_lord_and_longdead_change_weapon_chance` | 古尸和骷髅领主切换武器概率 | `0.25` | 0.25代表25%；对新抽取切换特性的生物生效。 |
| `bat_poison_attack` | 吸血蝙蝠攻击中毒 | `true` | 关闭仅禁用本拓展添加的中毒效果。 |
| `zombie_pigman_use_bow` | 允许僵尸猪人使用弓 | `true` | 开启时保留当前的带弓抽取概率。 |
| `zombie_pigman_boost_chance` | 精英猪人生成概率 | `0.025` | 0.025代表2.5%，对新抽取特性的猪人生效。 |
| `ghast_overworld_boost` | 主世界恶魂火球爆炸范围倍率 | `1.25` | 控制本拓展火球计算中的主世界增强系数。 |
| `wolves_attack_players_with_meat` | 野狼攻击手持肉的玩家 | `true` | 按当前有效逻辑检查手持肉，不扫描整个快捷栏。 |
| `can_boost_iron_golem` | 允许强化铁傀儡 | `true` | 关闭时已有强化标记保留，但额外20生命、3攻击及体型暂时停用；再次开启恢复加成，不额外回血。 |
| `annihilation_skeleton_despawn_time` | 湮灭骷髅受击后消失时间（tick） | `3600` | 从其受攻击进入消失阶段后开始计时。 |
| `ender_dragon_attack_wither_chance` | 末影龙受击反击凋零概率 | `0.2` | 0.2代表20%。 |
| `ender_dragon_immune_to_arrow_health_percent` | 末影龙免疫箭矢生命比例 | `0.3` | 0.3代表生命低于30%时免疫箭矢。 |
| `ender_dragon_attack_slowness` | 末影龙攻击附加缓慢 | `true` | 控制本拓展添加的龙攻击缓慢效果。 |
| `nether_mod_attack_fire_chance` | 下界生物攻击点燃概率 | `0.5` | 0.5代表50%，包括当前实装的火焰攻击路径。 |
| `slime_attack_damage_multiplier` | 绿色史莱姆伤害倍率 | `1` | 初始化时按原始伤害×倍率+加成计算；不反复叠加。 |
| `slime_attack_damage_bonus` | 绿色史莱姆伤害加成 | `4` | 仅绿色史莱姆；对新初始化的生物生效。 |
| `giant_zombie_spawn_zombie_cooldown` | 巨型僵尸召援间隔（tick） | `1200` | 仍保留召援总量、存活随从和局部密度限制。 |
| `zombie_pigman_attack_animails` | 僵尸猪人攻击动物 | `false` | 关闭保持当前默认目标选择；开启后允许猪人选择动物为攻击目标。 |
| `bone_lord_tweak` | 骷髅领主增强 | `true` | 控制本拓展给本体骷髅领主与远古骷髅公爵增加的属性，已有生物会定期刷新；不关闭本体通用日期成长。 |
| `infernal_creeper_boost` | 地狱爬行者增强 | `true` | 控制本体地狱爬行者的额外爆炸增强；独立黑色地狱爬行者身份保留。 |
| `villager_wool_to_emerald_shard_count` | 羊毛交易消耗数量 | `4` | 现有羊毛收购报价改为绿宝石碎片交易，包含已保存报价，不重复添加同一笔交易。 |
| `villager_wool_to_emerald_shard_shard_count` | 羊毛交易获得碎片数量 | `1` | 使用本体注册的绿宝石碎片，报价库存、已用次数与声望相关信息保留。 |
| `mob_defense` | 怪物格挡 | `true` | 关闭会停止怪物武器、盾牌格挡；保留目标选择与普通攻击。 |
| `mob_attack_cause_fire` | 怪物持械攻击概率点燃 | `true` | 控制持械怪物额外火焰攻击特性；下界生物的独立点燃概率仍由 `nether_mod_attack_fire_chance` 控制。 |
| `mob_disarm_when_defence` | 怪物缴械附魔效果 | `true` | 控制怪物攻击及格挡时的缴械效果；玩家对怪物缴械继续使用原有路径。 |

## Entities.Animals

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `animal_ill_to_death_time` | 牲畜患病死亡时间（tick） | `144000` | 本体判定患病时累计，康复逐tick消退，达到时死亡；随实体保存，0禁用并清理病死计数。 |
| `breed_xp_chicken` | 鸡繁殖经验 | `15` | 只在真实繁殖成功且允许生物掉落时结算；0不提供经验。 |
| `breed_xp_sheep` | 羊繁殖经验 | `30` | 替换原生繁殖经验，不另外叠加一批经验球。 |
| `breed_xp_pig` | 猪繁殖经验 | `25` | 实际繁殖成功时给予的经验；0不提供经验。 |
| `breed_xp_cow` | 牛繁殖经验 | `45` | 牛及哞菇使用此值；其他未列物种保留原生规则。 |

## Misc

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `log_players_interact_with_portal` | 成功使用传送门时广播 | `true` | 只在本体传送门真正完成传送后广播；目的地查询、失败或取消传送不广播。 |

## GameMechanics.MobSpawning

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `ghast_spawn_limit_day` | 主世界恶魂开始生成日期（天） | `128` | 仅影响本拓展添加的主世界自然生成入口。 |
| `annihilation_skeleton_spawn_in_light` | 允许主世界湮灭骷髅在亮处生成 | `false` | 仅放宽该生物的光照检查，仍受平安期、距离和领主日期限制。 |
| `wandering_witch_spawn_chance_overworld` | 主世界女巫生成通过率（%） | `33` | 每次符合其他条件的女巫族自然生成尝试的通过率。 |
| `wandering_witch_spawn_limit_day_overworld` | 主世界流浪女巫开始日期（天） | `65` | 保留当前第65天默认值；统一领主解锁下限继续生效。 |
| `wandering_witch_spawn_limit_day_other` | 地底流浪女巫开始日期（天） | `65` | 当前流浪女巫不在下界自然生成；统一领主解锁下限继续生效。 |
| `wandering_witch_spawn_chance_underworld` | 地底女巫生成通过率（%） | `50` | 每次符合其他条件的女巫族自然生成尝试的通过率。 |
| `zombie_lord_spawn_frequency` | 僵尸领主生成权重 | `2` | 0禁用对应自然池条目；保留其他召援或事件入口。 |
| `zombie_pigman_lord_spawn_frequency` | 僵尸猪人领主生成权重 | `1` | 0禁用对应自然池条目；不改变其他来源的生成。 |
| `annihilation_skeleton_spawn_frequency` | 湮灭骷髅生成权重 | `1` | 0禁用对应自然池条目。 |
| `wandering_witch_spawn_frequency` | 流浪女巫生成权重 | `1` | 0禁用对应自然池条目。 |
| `blood_moon_max_hostile_fraction` | 血月共享怪物额度倍率 | `3` | 当前共享额度计算完成后乘此值；默认保留当前平衡，0禁用血月自动敌对额度。 |
| `mob_max_spawn_count_increase_per_day` | 日期刷怪额度增长系数 | `1` | 基础总额度为 `floor(50×有效区块/128)+max(100,当前日期×此值)`，血月再乘血月倍率。 |
| `ancient_bone_lord_spawn_limit_day` | 远古骷髅公爵自动生成日期 | `33` | 只覆盖该生物的自动生成门槛；其他领主维持各自原有门槛，生成蛋与命令不受此项限制。 |

## WorldGen.Nether

| 旧配置键 | 设置项 | 默认值 | 生效说明 |
|---|---|---|---|
| `nether_adamantium_max_count_per_chunk` | 下界艾德曼矿脉尝试次数／区块 | `4` | 只影响新生成区块；这是候选矿脉次数，不是最终矿石方块数量。 |
| `nether_adamantium_max_count_per_vein` | 下界艾德曼矿脉尺寸 | `3` | 只影响新生成区块；受地形、替换目标与原生矿脉算法影响。 |

## 本体管理的矿物与地形参数

原JSON中主世界、地底世界各8种矿物的32个频率／概率键，统一对应主模组的高级TOML字段；常用页面按本次要求仅显示倍率，原始候选与概率留在高级TOML参数中。`*_frequency_*` 对应每区块候选次数 `candidates`，`*_chance_*` 对应候选通过概率 `chance_percent`；这里的“频率”是候选次数，不是互相竞争的矿物权重。路径分别为 `overworld_generation.<矿物>.candidates`／`chance_percent` 与 `underworld_generation.<矿物>.candidates`／`chance_percent`。

| 矿物路径名 | 原主世界频率／概率键 | 原地底频率／概率键 |
|---|---|---|
| `copper` | `copper_frequency_overworld`、`copper_chance_overworld` | `copper_frequency_underworld`、`copper_chance_underworld` |
| `silver` | `silver_frequency_overworld`、`silver_chance_overworld` | `silver_frequency_underworld`、`silver_chance_underworld` |
| `iron` | `iron_frequency_overworld`、`iron_chance_overworld` | `iron_frequency_underworld`、`iron_chance_underworld` |
| `gold` | `gold_frequency_overworld`、`gold_chance_overworld` | `gold_frequency_underworld`、`gold_chance_underworld` |
| `mithril` | `mithril_frequency_overworld`、`mithril_chance_overworld` | `mithril_frequency_underworld`、`mithril_chance_underworld` |
| `lapis` | `lapis_frequency_overworld`、`lapis_chance_overworld` | `lapis_frequency_underworld`、`lapis_chance_underworld` |
| `diamond` | `diamond_frequency_overworld`、`diamond_chance_overworld` | `diamond_frequency_underworld`、`diamond_chance_underworld` |
| `adamantium` | `adamantium_frequency_overworld`、`adamantium_chance_overworld` | `adamantium_frequency_underworld`、`adamantium_chance_underworld` |

主模组同时提供主世界8项、地底11项矿物倍率，路径为 `overworld_ores.<矿物>` 与 `underworld_ores.<矿物>`，默认均为1；地底另含砾石、红石及虫蚀方块。倍率0禁用、0.5减半、2加倍候选次数，并保留已有高级候选次数和概率设置。实际候选数为高级候选数乘倍率，通过概率只结算一次；原生默认通过率为10%，不会自动套用旧JSON样例中的100%。所有矿物调整只影响新生成区块，不修改既有矿石。

主世界艾德曼的原生高级候选次数默认0，因此仅把倍率设为1或2不会生矿；需要先在高级TOML中将 `overworld_generation.adamantium.candidates` 设为正数，并保持概率与倍率非0。旧 `overworld_adamantite_ore` 的开关用途由这里的0／正数候选设置承担。

`underworld_mantle_block_offset` 没有单独加入这92项扩展设置；地底世界保留本体当前地形与高度规则。下界艾德曼的两个扩展旧键保留名称，实际控制候选矿脉尝试次数及矿脉尺寸，不能理解成每区块最终矿石方块数量。世界生成继续保留原有替换目标、高度、弃矿概率和群系筛选。

自动回归入口：`./gradlew runGameTestServer -PmodernIteGameTestDirectory=run-config-gametest`；客户端独立界面检查：`./gradlew runClient -PmodernIteConfigAudit=true`。客户端检查只在隔离目录打开设置页，不载入玩家世界。

## 本次验证（2026-10-19）

提交前从暂存区独立导出配置功能源码，构建、27项JUnit和208项GameTest通过。原有拟态僵尸修改保留在工作区，未纳入配置功能提交；下述212项是包含该独立修改的工作区组合验证结果。

扩展27项JUnit、本体1644项JUnit、212项扩展GameTest和1932项本体GameTest已通过。覆盖真实渐进伤害与征服者共用计数及PvP回归、成功伤害后的缴械、原生窒息与钓鱼经验、地底两阶段／下界计时和复制、宝石献祭、金苹果用时、全套保护V减伤、成功门户广播、独立升级炉交易与进度保存、牲畜病死／繁殖经验、羊毛交易以及高级矿物候选与倍率控制。

当前版本已完成两项真实客户端审计：设置页在320×240与854×480下保存14张GPU截图，验证矿物倍率0／0.5／2、主世界与地底独立草稿、隐藏页面的NaN拒绝保存、重置为1、两个维度一起保存及恢复原配置；金属升级炉保存5张中文GPU截图，验证两种真实菜单的原生进度同步、JEI两项升级配方、唯一机器催化剂、U／R查询及箭头入口。设置页报告与截图位于 `build/verification/config/`，升级炉报告与截图位于 `build/verification/metal/`。

打包检查确认本体1324个生产类、938份资源JSON，以及扩展228个生产类、538份资源JSON逐字节匹配编译与资源处理结果；未内嵌测试类或JEI实现。

联机验证覆盖服务端权限方向、快照编解码及断连清理入口；尚未做独立客户端和服务器双进程多人实测，上述客户端审计不替代该验证。
