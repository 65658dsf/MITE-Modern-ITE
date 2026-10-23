# 实时刷怪设置

开发者：NingMeng123。更新：2026-10-23。

从 MITE 玩法设置进入 ITE 设置，点击“ITE 刷怪规则”。页面首项为刷怪预设，默认“ITE 2.0.28”，关闭后切换为“现有自定义”。分页、缩放窗口和切换预设会保留草稿；非法的生效字段会阻止保存，取消放弃草稿。参考模式中灰显的自定义字段不参与校验，其上一次有效数值继续保留。

单机保存到 `config/modern_ite-common.toml` 后立即发布新快照，下次刷怪调度读取新值；无需退出存档。预设参与登录同步、配置热重载和本体的完整配置导出。联机客户端仅查看服务端同步值。切换模式和降低额度都不会移除已有怪物，也不会重置开局平安期。

两种预设下，所有 `modern_ite` 生物均须经过完整 64 天后才能自动生成，即历史最高日期第 65 天起。该底线覆盖自然池、刷怪笼、转换、替换、骑手、召援与事件，已有的更晚物种日期、维度、光照和平安期条件继续生效；本体 `mite` 生物的日期不受此底线影响。命令、生成蛋和已有存档实体保留，时间回拨不会重新锁住已解锁的日期。

## 默认的 ITE 2.0.28 参考预设

`GameMechanics.MobSpawning.reference_spawning = true` 时，以 R196 调度和老 ITE 自然池为依据，不叠加老 Extreme 扩大额度和血月尝试次数的规则。

- 有效区块数按多人覆盖区块并集计算。浅层额度为 `floor(50 × 有效区块数 / 除数) + 当前显示日期`，普通除数为 256，血月为 128；深层额度在整个结果上乘 2。深浅层独立计数，没有前期至少附加 100，也不把血月总额度乘 3。第 1 天按 1 计算，仍受既有平安期约束。
- 289 个有效区块时，单独 R196 的普通浅层/深层额度为 56/112；ITE 第 3 天为 59/118，第 32 天血月为 144/288。额度是补怪条件，并非每轮实际生成数量。
- 自然刷怪恢复原版附近拥挤保护：地表普通 8、满月夜 10、血月夜 12，并保留深度和要塞接近度修正。超过门槛会暂停该玩家区域补怪；它不是场上硬上限，已生成的怪物仍可移动过来。
- 沿用原版深层 10%、浅层 17% 的每 tick 启动概率及随机高低谷、停刷期，通常每区块 3 组、每组 4 次尝试；48 格外有 50% 随机跳过。保留原版月相、距离、光照和物种检查，不额外把血月轮次概率乘 2。
- 自然池恢复老 ITE 各生物群系、维度、权重和群体大小，ITE 僵尸领主、猪人领主、湮灭骷髅和流浪女巫统一最早第 65 天；主世界恶魂第 128 天、远古骷髅公爵第 192 天的具体条件保留。额外 Extreme 变种和狂犬不进入参考自然池，原有事件、命令和生成蛋入口保留。
- 普通女巫恢复原有召狼机制，流浪女巫使用老 ITE 自身随从规则。保留老 ITE 的 64 格追踪、巨型僵尸默认 1200 tick 召援间隔及刷怪笼近场加速。

参考预设固定自然额度、日期增量、生成组数、尝试次数和原版物种日期/通过率/光照条件。以下旧字段仅在自定义模式生效，参考模式页面中会灰显：

| 配置键 | 保留的旧默认值 |
| --- | --- |
| `mob_max_spawn_count_chunk_base` | 50 |
| `mob_max_spawn_count_day_minimum` / `mob_max_spawn_count_day_maximum` | 100 / 0 |
| `mob_max_spawn_count_increase_per_day` | 1 |
| `blood_moon_max_hostile_fraction` | 3 |
| `hostile_spawn_groups` / `blood_moon_hostile_spawn_groups` | 3 / 4 |
| `hostile_spawn_attempts` | 4 |
| `ghast_spawn_limit_day` | 128 |
| `ancient_bone_lord_spawn_limit_day` | 33 |
| `wandering_witch_spawn_limit_day_overworld` / `wandering_witch_spawn_limit_day_other` | 65 / 65 |
| `wandering_witch_spawn_chance_overworld` / `wandering_witch_spawn_chance_underworld` | 33 / 50 |
| `annihilation_skeleton_spawn_in_light` | false |

本体的自然刷怪冷却和概率设置不覆盖参考预设的固定 R196 调度；如需保留原现代版调度参数，可关闭参考预设。

## 分裂、召援和刷怪笼

苦力怕与骷髅的现有分裂机制完整保留：默认 3% 获得分裂特性，生成 6～8 只同种小型个体，幼体保持现有体型与属性缩放，且不会再次死亡分裂。`compressed_skeleton`、`compressed_skeleton_expand_count`（默认 7，实际取 ±1）和停滞时间继续生效。

| 两种预设均生效的配置 | 默认值 | 作用 |
| --- | --- | --- |
| `local_hostile_limit` / `blood_moon_local_hostile_limit` | 32 / 48 | 玩家周围水平 32 格、垂直 8 格的分裂与非遭遇战召援准入；0 停止新增 |
| `giant_zombie_spawn_zombie_cooldown` | 1200 | 巨型僵尸召援间隔（tick） |
| `mob_spawner_speed_up_with_players` | true | 开启玩家近场刷怪笼加速 |
| `mob_spawner_extra_delay_reduction` | 2 | 加速开启时每 tick 额外减少的冷却 tick；0 保留原生倒计时速度 |

分裂继续遵守每主人最多 8 只活随从、平安期、日期、碰撞和原有 32/48 局部准入。参考模式的自然刷怪单独使用 8/10/12 拥挤保护；32/48 不覆盖它。自定义模式则继续让自然刷怪和非遭遇战召援共用 32/48。刷怪笼加速作用于正在运行的倒计时。

## 现有自定义预设与权重兼容

`reference_spawning = false` 恢复现有自定义调度与自然池。所有旧配置键和值原样保留。普通总额度为 `floor(区块基础值 × 有效区块数 / 128) + 日期额度`，日期额度为 `max(日期下限, 当前显示日期 × 每日增长)`，正数上限将日期部分限制到 `max(日期下限, 日期上限)`；血月最后乘总额度倍率并向下取整。深浅层共享总额。289 个有效区块的旧默认值是前 100 天普通 212、第 32 天血月 636。

物种自己的日期与第 65 天底线取较晚者，例如闪电骷髅与女巫领主第 96 天、不朽尸皇第 128 天；流浪女巫仍遵守该预设的可配置日期。

四项 `*_spawn_frequency` 继续在两种模式调整已有 ITE 候选，0 关闭对应入口。实体生成权重填写 `0–1000000` 的整数，留空使用各池原始值，0 移除整个候选及其条件变种。权重是池内相对份额，不增加总额度，也不会绕过日期、维度、平安期或创建自然池不存在的条目。因此旧配置为额外 Extreme 怪物填写正权重，也不会使其重新进入参考自然池。

权重页面直接编辑绝对权重。编辑或还原实体时清除该实体旧百分倍率，防止新权重被旧倍率暗中缩放；未编辑的旧倍率仍兼容。未知 ITE ID 的底层数据保留，参与导出、登录同步和热重载。

```toml
[GameMechanics.MobSpawning]
reference_spawning = true
natural_spawn_rates = []
natural_spawn_weights = ["modern_ite:zombie_lord=2.0"]
```

## 验证范围

回归覆盖参考预设默认值、旧配置保留、双向切换、同步及导出；刷怪行为覆盖深浅独立额度、多人区块并集、局部门槛、月相、物种条件和两种分裂的单代保护。真实客户端需检查最小 320×240 页面、禁用字段、分页与缩放草稿、切换预设后非法值校验和多人只读状态。历史 2026-10-22 的构建/客户端审计记录不作为本次参考预设的验证证据。

2026-10-23实际验证：双方编译与生产打包成功，本体1,722项JUnit、2,341项全量服务器GameTest通过；ITE34项JUnit、300项全量服务器GameTest通过，其中11项覆盖现有分裂。ITE日志为`build/verification/reference-spawning/ite-third.log`；本体日志为相邻工程的`build/verification/reference-spawning-full-final.log`。本体固定种子三夜加速夹具也通过，隔离验证附近拥挤后停止新增和血月轮次，不模拟实际地形或战斗。

本体`runData -PmiteGameplayConfigClientAudit=true`通过配置入口、后端链接和中英文词条静态检查；本轮未完成真实窗口渲染、双客户端联机或实际生存试玩。最终包校验见`build/verification/reference-spawning/final-package-audit.json`：本体1,409个类及4,598个资源、ITE265个类及743个资源与构建输出逐字节一致，均不包含开发测试条目。新版主模组与ITE的两个JAR应配套替换。
