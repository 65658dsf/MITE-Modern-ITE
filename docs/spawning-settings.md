# 实时刷怪设置

开发者：NingMeng123。更新：2026-10-24。适用 ITE 0.2.10 与 MITE: Modern 0.2.10 或更新版本。

ITE 安装后采用一套可编辑规则，以 R196 调度与 ITE 2.0.28 的数值为默认值。页面已取消参考／自定义切换，刷怪额度、日期、拥挤阈值、轮次概率和物种参数都可直接修改并实际生效。所有参数统一放在主模组设置 → “怪物刷新权重” → “上限与刷怪节奏”页面。安装ITE时自动加入ITE参数；未安装ITE时不显示这些专属项。ITE设置入口仅作为打开同一主模组页面的快捷链接。

分页和缩放窗口保留草稿；非法值会阻止保存，取消放弃草稿。单机保存到 `config/modern_ite-common.toml` 后立即发布新快照，下次调度读取新值，无需退出存档。服务端登录同步、配置热重载和本体完整配置导出均保留全部生效参数。远程多人客户端仅查看服务端同步值，由服主修改。降低额度不会清除已有怪物，也不会重置开局平安期。

自然候选权重支持0至1000000的有限小数，例如`modern_ite:zombie_chaos=2.5`，保存、服务器同步与实际选怪都保留小数，不取整。旧整数写法继续有效；清空沿用原候选权重，0移除整个候选族，未知ITE实体ID的设置仍会保存。负数、NaN、Infinity与超上限数值会被拒绝。权重随后仍乘自然生成倍率，不绕过入口、日期、维度和数量限制；旧`spawn_frequency`整数配置的类型保持不变。

ITE 0.2.10 调用本体的可配置调度、动态配置页面及出生装备接口，必须配套本体 0.2.10 或更新版本。未安装 ITE 时，本体自己的配置与规则保持原有行为。

## 数量额度与默认值

下列自然刷怪参数位于 `[GameMechanics.MobSpawning]`；召援与刷怪笼键沿用原有分类。有效区块数 `C` 按多人覆盖区块的并集计算；当前显示日期 `D` 从第1天起计算，与下文按历史最高日期判断的物种解锁分开。

```text
日期额度 G = max(日期下限, D × 每日增长)
若日期上限 > 0：G = min(G, max(日期下限, 日期上限))
区块额度 B = floor(区块基础值 × C / 256 × 血月区块倍率)
浅层额度 = floor(B + G)
深层额度 = floor((B + G) × 深层额度倍率)
```

非血月时公式中的血月区块倍率取1；没有有效区块或仍在开局平安期时额度为0。血月只放大区块部分，日期附加额度不会跟着翻倍。深浅层独立计数，包含同 tick 已准入但尚未加入世界的实体，重复检查同一实体不会重复占位。

| 配置键 | 默认值 | 作用 |
| --- | --- | --- |
| `mob_max_spawn_count_chunk_base` | 50 | 每256个有效区块的基础额度 |
| `mob_max_spawn_count_increase_per_day` | 1 | 当前显示日期每一天增加的额度 |
| `mob_max_spawn_count_day_minimum` | 0 | 日期附加额度下限 |
| `mob_max_spawn_count_day_maximum` | 0 | 日期附加额度上限；0不限 |
| `blood_moon_max_hostile_fraction` | 2 | 仅乘血月区块额度 |
| `deep_hostile_cap_multiplier` | 2 | 整个深层额度倍率；深浅分别计数 |
| `hostile_spawn_groups` | 3 | 普通日期每区块每轮生成组数 |
| `blood_moon_hostile_spawn_groups` | 3 | 血月每区块每轮生成组数 |
| `hostile_spawn_attempts` | 4 | 每组尝试次数；物种专属放大继续按比例应用 |

289个有效区块时，默认第3天浅层／深层额度为59／118，第32天血月为144／288。额度是允许补怪的条件，不是每轮实际生成数量。组数、尝试次数和深层额度倍率可设0，分别停止相应自然生成尝试或深层补怪，不移除已有怪物。

## 新增的9项可编辑参数

配置共111项玩法数值，以下9项将原本固定的拥挤保护和调度节奏开放为设置。与上表重叠的深层倍率仍计为同一项。

| 配置键 | 默认值 | 可填范围 | 实际作用 |
| --- | --- | --- | --- |
| `natural_local_hostile_limit` | 8 | 0～1000000整数 | 普通自然刷怪附近拥挤基值 |
| `full_moon_natural_local_hostile_limit` | 10 | 0～1000000整数 | 满月夜自然拥挤阈值 |
| `blood_moon_natural_local_hostile_limit` | 12 | 0～1000000整数 | 血月夜自然拥挤阈值 |
| `deep_hostile_cap_multiplier` | 2 | 0～1000 | 深层独立额度倍率 |
| `hostile_spawn_interval_ticks` | 1 | 1～72000整数 | 敌对自然刷怪调度间隔，单位tick |
| `deep_spawn_chance_percent` | 10 | 0～100 | 每轮深层通过率，单位% |
| `surface_spawn_chance_percent` | 17 | 0～100 | 每轮浅层通过率，单位% |
| `blood_moon_spawn_chance_multiplier` | 1 | 0～100 | 血月轮次概率倍率 |
| `other_dimension_spawn_chance_multiplier` | 0.25 | 0～100 | 主世界外维度轮次概率倍率 |

主世界普通拥挤基值继续按深度修正；满月夜或血月夜取对应阈值与深度修正值的较大者，再应用原有要塞接近度修正。主世界外使用普通基值。附近怪物数量超过最终阈值时，跳过该玩家整个候选区域。阈值控制新增自然刷怪，不是场上硬上限，已有怪物仍可移动过来。

每次调度间隔结束后，深浅层分别按通过率及血月／维度倍率判定，最终概率最多100%；随后保留原有随机高低谷与停刷期、附近拥挤、距离、光照和物种检查。默认不额外放大血月轮次。48格外仍有50%随机跳过。每轮通过率或适用倍率设0会停止相应自然尝试。

## 极难怪物日期与物种参数

首屏“极难怪物开始日期”对应 `extreme_mob_spawn_limit_day`，默认65，可填1～1000000。按主世界历史最高日期判断，到指定日期后额外Extreme变种与狂犬加入各自维度和栖息地的自然池，共用上述额度与拥挤保护，不额外增加轮次。该门槛也覆盖ITE物种的刷怪笼、转换、骑手、召援和事件入口；更晚的物种日期、开局平安期与其他条件继续生效。命令、生成蛋和已有存档实体保留，时间回拨不会重置解锁进度。

自然候选以R196与老ITE基础池为依据，再在门槛后加入额外Extreme候选；末地、蘑菇群系、女巫小屋和下界要塞仍保留各自特殊池。玩家僵尸与拆笼挑战僵尸Boss维持事件入口，不进入自然池。下界僵尸猪人领主使用独立候选，不再从普通猪人额外按1/5替换。

| 配置键 | 默认值 | 作用 |
| --- | --- | --- |
| `ghast_spawn_limit_day` | 128 | 主世界恶魂开始日期，仍需血月夜晚等条件 |
| `ancient_bone_lord_spawn_limit_day` | 192 | 远古骷髅公爵自动生成日期，主世界仍需血月等条件 |
| `wandering_witch_spawn_limit_day_overworld` | 65 | 主世界流浪女巫开始日期 |
| `wandering_witch_spawn_limit_day_other` | 65 | 地底流浪女巫开始日期 |
| `wandering_witch_spawn_chance_overworld` | 33 | 主世界流浪女巫自然通过率（%） |
| `wandering_witch_spawn_chance_underworld` | 50 | 地底流浪女巫自然通过率（%） |
| `annihilation_skeleton_spawn_in_light` | false | 是否放宽主世界湮灭骷髅的光照检查 |

物种日期与统一门槛取较晚者。例如统一设33时，铁桶僵尸可从第33天进入候选，闪电骷髅和女巫领主仍至少第96天、不朽尸皇仍至少第128天；统一设173时，这些物种都须等到第173天。调整日期或通过率不绕过维度、空间与其他物种条件。

## 玩家僵尸、分裂与召援

玩家实际死亡时独立按5%判定玩家僵尸，即使尚未到统一日期或仍在开局平安期也可能生成。它不进入自然池，保留原局部密度准入。未抽中或生成失败时正常掉落物品；成功时由玩家僵尸接管实际死亡掉落并保存，不会因远离玩家、和平难度而自然消失，也不会被环境或其他生物伤害杀死，保存重载后继续保留，玩家击杀后只释放一次。黑暗地狱苦力怕直接炸死玩家同样按5%判定；成功时由玩家僵尸保管真实死亡物品，未生成或生成失败时正常落地。

苦力怕与骷髅的分裂机制完整保留：默认3%获得分裂特性，生成6～8只同种小型个体，保留现有体型与属性缩放，幼体不会再次分裂。骷髅仍受 `compressed_skeleton` 开关、`compressed_skeleton_expand_count` 数量中值（默认7，实际±1）和初始停滞时间控制。

| 配置键 | 默认值 | 作用 |
| --- | --- | --- |
| `local_hostile_limit` | 32 | 普通日期分裂与非遭遇战召援的局部准入上限 |
| `blood_moon_local_hostile_limit` | 48 | 血月分裂与非遭遇战召援的局部准入上限 |
| `giant_zombie_spawn_zombie_cooldown` | 1200 | 巨型僵尸召援间隔，单位tick |
| `mob_spawner_speed_up_with_players` | true | 玩家近场刷怪笼加速 |
| `mob_spawner_extra_delay_reduction` | 2 | 加速时每tick额外减少的冷却tick；0使用原生速度 |

32／48在玩家周围水平32格、垂直8格范围检查，与自然刷怪的8／10／12阈值分开。分裂仍遵守每主人最多8只存活随从、平安期、日期与碰撞条件；空间或额度不足时数量可能减少。普通女巫保留一次召狼，流浪女巫保留老ITE随从规则；64格追踪、巨型僵尸召援及刷怪笼正在运行的倒计时读取相应配置。

## 权重与旧配置迁移

四项 `*_spawn_frequency` 调整对应ITE候选原始权重，0关闭该自然候选。

| 配置键 | 默认值 |
| --- | --- |
| `zombie_lord_spawn_frequency` | 2 |
| `zombie_pigman_lord_spawn_frequency` | 1 |
| `annihilation_skeleton_spawn_frequency` | 1 |
| `wandering_witch_spawn_frequency` | 1 |

“ITE 怪物刷新权重”页面直接填写0～1000000的有限数值，支持2.5、0.125等小数；留空使用各池原始值，0移除候选。权重只决定同池相对份额，不增加额度或绕过日期，也不会给玩家僵尸、事件Boss新增自然入口。

编辑或还原某个实体的绝对权重时，清除该实体旧 `natural_spawn_rates` 倍率，避免重复缩放；未编辑和未知ITE ID的数据继续参与导出、登录同步与热重载。

升级时，若旧TOML仍含已废弃的 `reference_spawning` 键，会在加载前执行一次迁移：恰为旧默认的血月倍率3改为2、远古骷髅公爵日期33改为192、日期下限100改为0、血月组数4改为3，随后删除废弃键。其他自定义数值、日期、权重和分裂设置保留。键删除后不会再次迁移，用户以后主动填写这些数值也不会被覆盖。旧网络数据中的废弃键会被忽略，不能切回另一套规则。

```toml
[GameMechanics.MobSpawning]
extreme_mob_spawn_limit_day = 65
mob_max_spawn_count_chunk_base = 50
mob_max_spawn_count_day_minimum = 0
mob_max_spawn_count_day_maximum = 0
mob_max_spawn_count_increase_per_day = 1.0
blood_moon_max_hostile_fraction = 2.0
deep_hostile_cap_multiplier = 2.0
natural_local_hostile_limit = 8
full_moon_natural_local_hostile_limit = 10
blood_moon_natural_local_hostile_limit = 12
hostile_spawn_interval_ticks = 1
deep_spawn_chance_percent = 10.0
surface_spawn_chance_percent = 17.0
blood_moon_spawn_chance_multiplier = 1.0
other_dimension_spawn_chance_multiplier = 0.25
hostile_spawn_groups = 3
blood_moon_hostile_spawn_groups = 3
hostile_spawn_attempts = 4
natural_spawn_rates = []
natural_spawn_weights = ["modern_ite:zombie_lord=2.0"]
```

## 验证记录

本轮实际构建、自动测试、客户端审计与包校验结果统一记录在[验收记录](verification.md)。自动调度与密度夹具不代替真实地形、生存战斗或多人长期试玩。
