# 凋零宝藏与 Boss 血量

开发者：NingMeng123。日期：2026-11-04。拓展版本保持 0.3.0。

普通凋零和强化凋零最大生命均为 **1300**，末影龙为 **7500**。取值来自本地旧版极难 `Extreme-lh-0.6.6` 的 `cn.wensc.mitemod.extreme.mixin.entity.EntityWitherMixin.modifyDefaultHealth` 与 `EntityDragonMixin.moreHealth`；前者将原版 300 替换为 1300，后者直接设置 7500。它们是极难类内的硬编码值，不是另一参考 ITE 中的龙血量。普通凋零仅共用生命上限。

当前整合包安装 WitherPlus 0.1.1 时：

| 召唤材料 | 召唤结果 | 最大生命 | 蓄力 | 凋零宝藏 |
|---|---|---:|---:|---:|
| 三个普通凋零骷髅头 | 普通凋零，原版攻击行为 | 1300 | 220 tick | 0 |
| 实际召唤阵含 WitherPlus 特殊头 | 强化凋零，保留 WitherPlus 技能与 ITE 骷髅增援 | 1300 | 600 tick | 固定 5 个 |

沿用 WitherPlus 特殊头参与召唤的语义：实际消耗的三个头中至少一个特殊头即可强化。仅摆在阵列旁边的特殊头不计入；两个阵列彼此靠近也不会借用旁边的标记。普通命令生成的凋零不自动强化。已保存的 WitherPlus `WpSpecialSummon` 标记用于恢复和迁移身份。

普通凋零不会获得 WitherPlus 技能、ITE 加长蓄力、ITE 骷髅增援或额外爆炸增强。MITE 本体对怪物的通用规则仍适用。两种凋零均保留下界之星；强化凋零额外固定掉落五个 `modern_ite:wither_treasure`，不要求玩家击杀，也不受抢夺等级影响。

右键开启一个凋零宝藏，消耗一个并随机获得下表中的一种奖励。宝石类型从已有十种强化宝石中等概率选择，每次一颗。

| 奖励 | 概率 |
|---|---:|
| 3 级宝石 | 30% |
| 4 级宝石 | 20% |
| 5 级宝石 | 10% |
| 6 级宝石 | 5% |
| 10–20 颗钻石，含两端 | 20% |
| 一本随机单附魔满级附魔书 | 15% |

附魔池包括原版和模组附魔，排除诅咒及本体配置禁用的附魔；等级使用当前配置下的最高等级。若所有有效附魔均被禁用而抽中书，则提示失败、不消耗宝藏。奖励由服务器发放，满背包掉在玩家脚边；创造模式也消耗宝藏。

凋零宝藏采用独立的黑石、青色封印与骨白骷髅锁扣的原生立体模型，箱体有真实空腔，箱盖绕后铰链开启。右键一次自动完成开盖、翻找、取出并展示实际奖励的动作，不需要持续按住；主副手与第一、第三人称共用宝石箱已有的双手动作。20 TPS 下完整过程约 2.8 秒，第 1.8 秒显示已选定的宝石、钻石或满级附魔书，完成时消耗一个并发放同一份奖励。显奖前中断不扣宝藏；显奖后中断立即支付并结算已展示的奖励，避免预览后免费重抽。不同箱子、双手和重复点击共享一次开箱状态。

模型通过 [原生模型脚本](../tools/wither_treasure_model.mjs) 生成，纹理使用原版方块材质；最初的透明 PNG 仅保留作造型参考，实际物品使用立体几何。资源说明见 [宝藏模型与参考图](wither-treasure-art.md)。

兼容实现放在本拓展中，不修改或打包 WitherPlus、EnderDragonPlus。普通头和特殊头共用 `minecraft:wither`，因此通过实际召唤阵和可选 WitherPlus Brain Mixin 区分。两个 Boss 的最终生命在 `LOWEST` 加入事件中恢复，防止增强模组默认的 600/400 生命覆盖极难数值；读档时按当前剩余生命比例转换，不额外补满。修正 WitherPlus 0.1.1 特殊头配方的 1.21.1 ingredient 格式，材料仍为八个岩浆块包围一个普通凋零骷髅头；未安装该模组时跳过配方。

实现对照用户提供的 [WitherPlus 1.21.1 NeoForge 源码](https://github.com/hp3081925122/Wither-Plus/tree/1211neoforge) 和已安装的 0.1.1 JAR。后者的特殊头配方使用了不适用于 1.21.1 的字符串 ingredient 格式，兼容资源按源码中的材料和当前版本格式恢复配方。

验证命令（PowerShell，JAR 路径替换为实际路径）：

```powershell
.\gradlew.bat build compileGametestJava
.\gradlew.bat runGameTestServer '-PmodernIteGameTestNamespaces=modern_ite_wither,modern_ite_boss' '-PmodernIteGameTestDirectory=build/verification/wither-native/run'
.\gradlew.bat runGameTestServer '-PmodernIteGameTestNamespaces=modern_ite_wither,modern_ite_witherplus,modern_ite_boss' '-PmodernIteGameTestDirectory=build/verification/wither-plus/run' '-PmodernIteWitherPlusJar=D:/我的世界/.minecraft/versions/mite–1.21.1/mods/witherplus-1.21.1-neoforge-0.1.1.jar' '-PmodernIteEnderDragonPlusJar=D:/我的世界/.minecraft/versions/mite–1.21.1/mods/enderdragonplus-1.21.1-neoforge-0.2.jar'
```

2026-11-04 验证结果：40 项 JUnit 通过；不安装两个 Plus 的 344 项服务端 GameTest 通过（`build/verification/wither-native/run2.log`）；同时安装 WitherPlus 0.1.1 与 EnderDragonPlus 0.2 的 15 项专项 GameTest 通过（`build/verification/wither-plus/run4.log`）。包含真实物品放置召唤、实际死亡掉落、特殊头配方加载、宝藏奖池、背包满、冷却、附魔配置、Boss 受伤读档与重复加入。全部 562 个主资源 JSON 可解析，发布包未包含依赖模组或测试类。

已将构建包安装到 `D:/我的世界/.minecraft/versions/mite–1.21.1/mods/modern-ite-0.3.0.jar`，SHA-256 为 `6291b47d0a487eb54e8088c7a8a722ce765eb28a1c56e1c59d175144c2ec8f0f`。旧包备份为同版本目录下的 `.codex-backups/wither-treasure-2026-11-04/modern-ite-0.3.0.before-f713601d9311.jar`；本次未修改两个 Plus JAR 或用户存档。重启游戏加载新版。

23:00 补齐本体配套更新：两个包虽然均为 0.3.0，旧本体构建缺少新版 ITE 设置搜索所需的类和方法，导致客户端启动失败。已备份替换为含 `ConfigSearchIndex` 的配套本体，安装文件 SHA-256 为 `02f03afe86c9db494be9b9923cd3bf6515d1f64dba15e7652c2ba4f90a6b9550`，独立客户端启动和配置界面检查通过；详见 [配套修复记录](verification.md)。

立体开箱补全已通过 40 项 JUnit、351 项服务端 GameTest，以及实际客户端 GPU 检查。原宝石箱的创造模式策略保持，宝藏始终消耗一个；满背包、取消与角色克隆结算已验证。复现客户端检查：`.\gradlew.bat runClient -PmodernIteWitherTreasureAudit=true`；报告和动画位于 `build/verification/wither-treasure/`。当前已安装的 ITE SHA-256 为 `6db3594f0350b69c0ee110042be3f0dd6210684e65992541ad347f92a88c29f5`，旧包备份在版本目录 `.codex-backups/wither-treasure-3d-2026-11-04/modern-ite-0.3.0.before-6291b47d0a48.jar`。
