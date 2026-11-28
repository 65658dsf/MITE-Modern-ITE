# MITE: Modern ITE

面向 Minecraft 1.21.1 的 MITE: Modern 生存扩展，将 MITE-ITE 2.0.28 与 Extreme LH 0.6.6 的移植内容整合为一个模组。在本体的生存、装备与日历系统上，加入极难生物、领主技能、宝石镶嵌、独立强化锻造和后期探索内容。

开发者 **NingMeng123** · 当前源码配套版本 **0.4.1** · 模组 ID `modern_ite`

[安装与版本配套](#安装与版本配套) · [主要内容](#主要内容) · [玩法配置](#玩法配置) · [本地开发](#本地开发) · [文档与历史记录](#文档与历史记录)

本仓库保存 ITE 扩展的源码、资源与测试。MITE: Modern 本体提供基础玩法及配置、制造、装备成长、刷怪调度等接口，两者配套安装和构建。[本体仓库](https://github.com/65658dsf/mite-neoforge-1.21.1)

## 安装与版本配套

| 项目 | 当前要求 |
| --- | --- |
| Minecraft | 1.21.1 |
| 模组加载器 | NeoForge 21.1.259 或更新的 21.1 系列版本 |
| Java | 21 |
| 必需前置 | MITE: Modern 0.4.1 或更新版本 |
| 当前配套文件 | `MITE-Modern-0.4.1.jar` 与 `MITE-Modern-ITE-0.4.1.jar` |
| 联机安装 | 客户端和服务端均安装本体与 ITE 扩展 |

1. 首次加装或升级前备份存档。准备对应版本的本体和 ITE 扩展 JAR，源码构建步骤见下方“本地开发”。
2. 为 Minecraft 1.21.1 安装对应 NeoForge，将两个 JAR 放入游戏实例的 `mods` 文件夹。联机时在服务端配置相同的配套模组。
3. 启动游戏，在模组列表中确认 MITE: Modern 与 MITE: Modern ITE，然后进入存档调整玩法参数。

### 可选兼容

| 模组或资源包 | 用途 |
| --- | --- |
| JEI | 查询强化锻造、装备材质升级、金属升级炉和振金熔炉配方；在相关界面点击进度箭头打开配方 |
| Simply Swords 与 Better Combat | 通过本体的兼容入口接入武器平衡，支持适用装备的宝石、成长和强化结算 |
| Enchantment Descriptions | 显示扩展附魔的简体中文或英文效果描述 |
| WitherPlus | 特殊头召唤的强化凋零提供凋零宝藏掉落 |
| Punchy | 手持宝石按默认 `I` 键播放双手检视，按键可在游戏设置中调整 |
| 3D 宝石资源包 | 为 66 种宝石和 5 种碎片提供切面模型与动态扫光 |

JEI 适配版本为 19.57.0.451 所在的 19.x 系列，WitherPlus 适配要求为 0.1.1 或更新版本。宝石资源包放入实例的 `resourcepacks` 文件夹后启用，安装与生成步骤见独立说明。[3D 宝石与检视](docs/gem-3d-resource-pack.md)

## 主要内容

| 系统 | 玩法 |
| --- | --- |
| 极难生物与领主 | 僵尸领主、猪人领主、湮灭骷髅及多种僵尸、骷髅、苦力怕变体，提供分裂、召援、缴械和特殊攻击 |
| 时间领主 | 范围减速玩家模拟与动作，配有施法预警、状态提示和强化狂风人召援 |
| 宝石与镶嵌 | 十一类六阶宝石、碎片、宝石箱和八孔镶嵌台。同件装备同类宝石取最高阶，每把武器最多镶嵌一颗攻速宝石。 |
| 强化与装备成长 | 最低强化台按目标等级划分。+1～+3 用铁，+4～+5 用秘银，+6～+7 用艾德曼，+8～+10 用振金。高阶台兼容较低等级，装备材质决定强化上限。 |
| 生产与资源 | 独立金属升级炉、振金熔炉、私人箱、材质灯笼，以及红钻、枫木、凭证和经济内容 |
| 新物品与附魔 | 金属棒、铁桶头盔、极难弩、强化石、防护宝典及十二种扩展附魔 |
| 猪灵堡垒之眼 | 1 颗末影珍珠和 1 块艾德曼锭无序合成。在下界投掷后，眼睛会飞向最近的堡垒遗迹。结构进度默认要求世界达到第 32 天、在下界步行超过 1000 格并制作过一件金质护甲。 |
| 金苹果 | 把苹果放在工作台中央，左、右各放 1 块金锭，上、下两行各放 3 颗金粒。在普通或绿宝石附魔台消耗 400 点经验可将它转为附魔金苹果。 |
| 旧版极难成就 | 本体进度树收录 9 项 ITE 与 Extreme LH 成就。制作振金锭获得「那我岂不是要无敌了?」，穿齐振金护甲获得「那我岂不是无敌了?」。 |
| 复活与死亡回收 | 可绑定主人的复活传送阵、皮肤展示，以及玩家僵尸接管死亡物品的回收流程 |
| Boss 与宝藏 | 龙与凋零的强化规则，凋零宝藏的立体开箱、奖励展示与掉落结算 |
| 外观与动画 | 宝石扫光、可选立体宝石、双手检视、振金符文与星空护甲，以及生物模型和 Fresh Animations 适配 |

生物的生成条件和技能、装备成本与效果分别收录在专题文档中。[生物来源表](docs/mobs-source.md)、[装备与经济](docs/gear-economy-source.md)、[时间领主](docs/time-lord.md)、[凋零宝藏](docs/wither-treasure.md)

### 日历与成长

| 项目 | 默认规则 |
| --- | --- |
| 血月 | 第 32、64、96 天；之后从第 144 天起每 16 天一次 |
| 蓝月与月晕 | 第 128 天为蓝月，第 120 天为月晕 |
| 丰收月 | 每次血月前 3 天 |
| 前期双倍白天 | 开启时默认第 1～31 天白天半速推进，第 32 天恢复正常；结束日期可在设置中调整 |
| 开荒与怪物解锁 | 第二天入夜前提供自动敌对生成平安期，之后按物种日期、维度和刷怪条件准入 |
| 装备进阶 | 按历史最高日期和血月进程提升怪物出生装备 |
| 玩家生命 | 等级基础生命最高 60 点，四件护甲生命宝石最多增加 24 点，总生命另受历史最高日期门槛限制 |

生命日期门槛在第 96 天前为 20 点，第 96 天起为 30 点，第 128 天起为 40 点，第 170 天起开放完整等级与宝石上限。满足等级和宝石条件时，最高生命为 84 点，即 42 颗心。具体生命曲线、装备上限与宝石规则见规格文档。[装备经济规格](docs/gear-economy-source.md)

## 玩法配置

在模组列表中打开 **MITE: Modern → 配置**。ITE 设置会随对应功能出现在本体各分区，也可通过首页搜索找到设置名称、配置键或生物 ID。

| 内容 | 调整入口 |
| --- | --- |
| 下界艾德曼矿脉 | 世界与资源 |
| 刷怪额度、日期和节奏 | 生物与刷怪 → 上限与刷怪节奏 |
| ITE 生物自然权重和技能 | 生物与刷怪 |
| 格挡、连击和附魔效果 | 战斗与附魔 |
| 双倍白天、环境疲劳、经验和金属升级 | 生存与成长 |
| 传送提示 | 性能与管理 |
| 配置文件 | 游戏实例的 `config/modern_ite-common.toml` |

单机保存后发布新配置快照。多人游戏会在玩家登录或服务端重载时同步生效值。ITE 专属参数在联机时供查看，具备权限的管理员可以修改统一刷怪规则。矿物参数用于新生成区块，出生时抽取的特性在相应初始化时读取配置。“导出配置”会一并导出 ITE 的 TOML 文件。[配置项目与生效时机](docs/configuration.md)、[刷怪规则与日期](docs/spawning-settings.md)

更新存档时继续使用原物品身份、装备组件和已有归属资料。金属棒的逐级材质升级、旧灯笼与历史磨具等兼容细节由对应文档记录。[金属棒与装备](docs/gear-economy-source.md)、[灯笼与维修](docs/lantern-repair.md)、[本体接口](docs/base-interface-changes.md)

## 本地开发

准备 Java 21，先构建同版本本体，再构建扩展。下面使用 PowerShell，并将两个仓库放在同一父目录。

```powershell
git clone https://github.com/65658dsf/mite-neoforge-1.21.1.git miteto1.21.1neoforge
git clone https://github.com/65658dsf/MITE-Modern-ITE.git MITE-Modern-ITE

cd miteto1.21.1neoforge
.\gradlew.bat build
cd ..\MITE-Modern-ITE
.\gradlew.bat build
```

默认本体依赖路径由 `mod_version` 决定，当前为 `../miteto1.21.1neoforge/build/libs/MITE-Modern-0.4.1.jar`。扩展产物输出到 `build/libs/MITE-Modern-ITE-0.4.1.jar`。也可通过 `miteJar` 指定本体文件。

```powershell
.\gradlew.bat build "-PmiteJar=D:\Mods\MITE-Modern-0.4.1.jar"
```

| 命令 | 作用 |
| --- | --- |
| `.\gradlew.bat test` | 运行 JUnit 单元测试 |
| `.\gradlew.bat runGameTestServer` | 运行服务端 GameTest |
| `.\gradlew.bat runClient` | 启动开发客户端，默认目录为 `run-client` |
| `.\gradlew.bat runServer` | 启动开发服务端，默认目录为 `run-server` |
| `.\gradlew.bat runClient -PmodernIteWithJei=true` | 为开发客户端加载 JEI |
| `.\gradlew.bat runClient -PmodernIteClientAudit=true` | 执行标题页资源检查并退出测试实例 |

macOS 和 Linux 可通过 `bash ./gradlew` 执行对应任务。首次构建会下载 NeoForge 和构建依赖。测试代码位于独立的 `gametest` 源集，验证结果与实际检查范围收录在验收文档中。[验收记录](docs/verification.md)

<details>
<summary>专题客户端检查</summary>

以下命令使用各自的开发测试目录，输出日志、报告或截图到 `build/verification`。

| Gradle 参数 | 检查内容 |
| --- | --- |
| `-PmodernIteMobRenderAudit=true` | 重点生物模型、渲染与粒子 |
| `-PmodernIteJeiAudit=true` | 真实 JEI 配方页和强化台列表 |
| `-PmodernIteLanternAudit=true` | 灯笼模型、副手照明与视野效果 |
| `-PmodernIteResurrectionAudit=true` | 复活阵模型、贴图和皮肤展示 |
| `-PmodernIteTimeLordAudit=true` | 时间领主的客户端表现 |

将所需参数附加到 `.\gradlew.bat runClient`。独立目录可通过 `-PmodernIteClientDirectory=路径` 指定，服务端 GameTest 目录可通过 `-PmodernIteGameTestDirectory=路径` 指定。

</details>

### 代码位置

| 位置 | 职责 |
| --- | --- |
| `src/main/java/io/github/ningmeng123/modernite/entity/` | 扩展生物、技能、装备和生成规则 |
| `src/main/java/io/github/ningmeng123/modernite/gear/` | 宝石、镶嵌、强化和装备数据 |
| `src/main/java/io/github/ningmeng123/modernite/config/` | TOML 配置、同步与客户端设置页 |
| `src/main/java/io/github/ningmeng123/modernite/content/` | 物品、方块、生产设备与凋零宝藏 |
| `src/main/java/io/github/ningmeng123/modernite/integration/jei/` | JEI 配方分类与界面入口 |
| `src/main/resources/` | 数据包、模型、贴图、声音和兼容资源 |
| `src/test/`、`src/gametest/` | 单元测试、服务端与客户端集成检查 |
| `tools/` | 资源生成及专题检查工具 |

## 常见问题

### 启动时提示缺少本体或版本不匹配

按上方环境表安装 MITE: Modern 与 ITE 扩展，核对两个 JAR 的版本和游戏实例的 NeoForge 版本。联机时同时核对服务端模组列表。

### 在哪里查看强化和材质升级配方

安装适配版本的 JEI，对装备查看配方或用途，选择“强化锻造”或“装备材质升级”分类。强化台及振金熔炉界面的进度箭头也可打开对应配方。

### 如何调整怪物的出现日期和数量

在 **MITE: Modern → 配置 → 生物与刷怪 → 上限与刷怪节奏** 中调整总日期、物种独立日期与生成额度。自动准入日期取总限制和该物种日期的较大值，其他维度、栖息地与事件条件继续参与判断。[刷怪设置](docs/spawning-settings.md)

### 如何启用宝石立体外观和检视

将 3D 宝石资源包放入 `resourcepacks` 并启用；安装 Punchy 后手持宝石按 `I`，可播放普通宝石或立体宝石的双手检视。[资源包与检视说明](docs/gem-3d-resource-pack.md)

问题反馈请附上本体、ITE、Minecraft 和 NeoForge 版本，以及复现步骤、相关配置、`logs/latest.log` 或崩溃报告。[提交问题](https://github.com/65658dsf/MITE-Modern-ITE/issues)

## 文档与历史记录

| 主题 | 文档 |
| --- | --- |
| 玩法与生物 | [生物来源](docs/mobs-source.md) · [时间领主](docs/time-lord.md) · [僵尸领主](docs/zombie-lord.md) · [生物补充](docs/mob-additions.md) |
| 生存与开局 | [ITE 生存规格](docs/ite-survival-source.md) · [开局装备](docs/starting-equipment.md) · [发现与追击](docs/initial-player-visibility.md) |
| 装备与生产 | [装备经济](docs/gear-economy-source.md) · [制造耗时](docs/crafting-time.md) · [灯笼维修](docs/lantern-repair.md) |
| 外观与资源 | [立体宝石](docs/gem-3d-resource-pack.md) · [宝石扫光](docs/gem-item-shine.md) · [模型兼容](docs/model-compatibility.md) · [振金效果](docs/vibranium-runes-and-glint.md) |
| 配置与验证 | [配置说明](docs/configuration.md) · [刷怪设置](docs/spawning-settings.md) · [接口差异](docs/base-interface-changes.md) · [验收记录](docs/verification.md) |
| 历史版本 | [0.4.1](docs/release-0.4.1.md) · [0.4.0](docs/release-0.4.0.md) · [0.3.9](docs/release-0.3.9.md) · [0.3.8-bugfix1](docs/release-0.3.8-bugfix1.md) · [0.3.8](docs/release-0.3.8.md) · [0.3.7](docs/release-0.3.7.md) · [0.3.6](docs/release-0.3.6.md) · [0.3.5](docs/release-0.3.5.md) · [0.3.4](docs/release-0.3.4.md) · [0.3.3](docs/release-0.3.3.md) · [0.3.2](docs/release-0.3.2.md) · [0.2.16](docs/release-0.2.16.md) · [0.2.12](docs/release-0.2.12.md) · [0.2.10](docs/release-0.2.10.md) |

## 来源与许可

规则与资源对照以 MITE-ITE 2.0.28、Extreme LH 0.6.6 及本体的既有实现为依据。资源原路径、SHA-256 与现代适配记录收录在来源清单中。[内容来源清单](src/main/resources/modern_ite/content-provenance.json)

模组元数据声明的许可为 **All Rights Reserved**，开发署名为 **NingMeng123**。
