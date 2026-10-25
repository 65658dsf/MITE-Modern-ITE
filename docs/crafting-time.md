# ITE 配方制造耗时修复

开发者：NingMeng123。日期：2026-10-25。

火把捆的四种合成和单捆分解恢复制造计时。原因为本体 `CraftingQuality.instantTorchBundle` 将这五个扩展配方提前判定为无需制造，既跳过玩家计时，也跳过自动合成器计时。扩展现在通过统一难度表接入本体 `TimedCraftingRecipes.get`，并取消这些已登记配方的即时特例；不需要更改本体 JAR。

## 火把捆的旧版依据

依据用户提供的 Extreme LH 0.6.6 和 MITE-ITE 2.0.28 JAR，以及 R196 原始 `RecipeHelper`、`bex` 制造公式；本地反编译资料位于相邻本体工程 `.reference/extreme/decompiled`、`.reference/ite/decompiled` 和 `.reference/r196/decompiled`。

Extreme `EXItemsRegistryInit.registerRecipes` 的合成输入是原木、丝线或筋、两份煤或木炭，没有覆盖配方难度。组件难度分别为 `120.00001F`、`25F`、`25F`、`25F`，按旧版 `float` 顺序相加得到整批 `195F`。`registerItems` 给 `Stack_Torch` 显式设置组件难度 `1.0E-9F`，分解采用此值；旧制造公式仍将周期限制为至少 25 tick，不能因为难度接近零而跳过制造。

| 操作 | 整批难度 | 0 级背包 | 0 级工作台，无其它加速 |
|---|---:|---:|---:|
| 原木＋丝线/筋＋两份煤/木炭 → 1 捆 | 195 | 138 tick，6.9 秒 | 114 tick，5.7 秒 |
| 1 捆 → 16 火把 | 1.0E-9 | 25 tick，1.25 秒 | 25 tick，1.25 秒 |

时间按 20 TPS 换算。等级、信标和笨拙诅咒继续由已有服务端制造逻辑处理。难度是整批输入的操作成本，不按结果数量再除以 16。一次拆一捆、产出 16 个火把的现代堆叠适配保留。

工作台周期按原式执行 `float` 除法后截断；`138 / 1.2F` 的实际浮点值略小于 115，所以是 114 tick。

## 其它配方

扩展的全部 154 个普通有序、无序配方补齐统一制造规则；另外补上与旧 Extreme 输入完全一致的原版灯笼 `minecraft:lantern`，整批难度为 `711.1111F`，基础周期 269 tick。难度数值、组件来源和现代适配说明保存在 `src/main/resources/modern_ite/timed-crafting.json`。玩家背包、工作台、自动合成器和工作台 JEI 索引均读取同一入口。

八种金属棒恢复旧版显式最低制造难度 `1F`，两件合并维修采用一半难度 `0.5F`，最低等待 25 tick；升材继续经过本体锻造计时。普通金属棒初始合成仍采用整批材料难度。保留的三种材质灯笼合并维修也按 `0.5F` 进入计时，并将受损输入标记为维修材料，避免有规则却无法启动。

宝石升级和三宝石回收的难度为 75；堡垒之眼采用旧版显式覆盖值 100。复活阵在旧版存在两条重复注册，本次采用其中明确设置的难度 25。现代材质手提灯笼和金属升级炉方块的制作按现有输入累加旧组件难度，适配依据在数据表中标出。

熔炼、锻造台升级、金属升级炉和强化台各有自己的服务端计时，不套用普通工作台周期。防护宝典应用继续使用本体维修配方入口，保留品质和经验豁免。

## 本体现代配方的审计范围

全体已加载配方的扫描另外发现 443 个现代静态配方仍没有制造规则。它们没有在旧版找到相同输入的配方，包括染色玻璃、旗帜、蜡烛、混凝土粉末、铜装饰与盔甲纹饰模板，以及现代新增染料提取方式等，需要另定现代适配耗时。本次按旧版证据修复对应配方，完整即时配方清单随运行时审计报告保存，避免只按产物名称套用其它操作的耗时。

## 验证

2026-10-25，`python tools/generate_crafting_timing.py --check`、完整构建、38 项 JUnit、342 项服务端 GameTest 全部通过。其中 8 项专项测试覆盖四种火把捆在背包与燧石台上的实际周期、分解、普通/Shift/数字键/副手、逐批连续制造、取消、同产物换料、真实红石自动合成器、三档材质灯笼合并维修、八种金属棒维修、独立来源难度向量和全配方扫描。

运行时扫描确认 155 个扩展静态合成配方均有有效规则（154 个普通配方，加既有铜棒工作台升铁配方），另有 1 个防护宝典动态配方；其它命名空间的 1079 个静态配方已计时，443 个现代新增配方尚待适配。专项测试已进入默认 GameTest 命名空间。

命令：

```powershell
.\gradlew.bat build runGameTestServer '-PmiteJar=build/verification/crafting-time/mite-base.jar' '-PmodernIteGameTestDirectory=build/verification/crafting-time/run-full' --console=plain
```

日志为 `build/verification/crafting-time/full.log`；完整清单为 `build/verification/crafting-time/recipe-audit.json`；成品边界和哈希见 `build/verification/crafting-time/package-audit.json`。测试使用独立服务端世界，本轮未运行图形客户端或多人客户端验证。
