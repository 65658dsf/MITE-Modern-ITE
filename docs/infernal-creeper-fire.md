# 地狱苦力怕爆炸残火

开发署名：NingMeng123。日期：2026-10-29。

普通地狱苦力怕与黑暗地狱苦力怕恢复爆炸后的地面残火。火焰是实际的普通火方块；在批准的爆炸受影响位置中，当前位置为空气、下方为完整不透明实体方块时，以 `1/3` 概率放置。没有额外的固定燃烧时长，后续蔓延、熄灭与下雨等行为交给游戏火焰规则处理。

普通地狱苦力怕由配套本体的 `R196CreeperExplosionMixin` 打开原生燃烧爆炸。黑暗地狱苦力怕保留分帧挖坑，在全部方块破坏完成后，继续使用同一服务端预算检查残火位置，避免火焰被后续挖坑清掉。空气候选与可炸方块一起经过公开的 `ExplosionEvent.Detonate` 列表，保护模组可以移除；残火不会覆盖当前的非空气方块或强制加载区块。关闭 `mobGriefing`、取消爆炸或移除受影响位置都会阻止相应残火。

## 低版本依据与范围适配

本地原始证据位于相邻本体仓库：

- `.reference/r196/decompiled/abr.java:92`：`EntityInfernalCreeper` 自动启用燃烧爆炸；黑暗地狱苦力怕继承该类。
- 同文件 `225–234`：完成地形破坏后，遍历受影响位置，在空气上方、下方为不透明标准立方体时，按 `nextInt(3) == 0` 放置普通火焰。
- `.reference/r196/decompiled/aqz.java:399`：下方判定对应 `isAlwaysOpaqueStandardFormCube()`。
- `.reference/r196/decompiled/tf.java:100`：方块强度为爆炸标量乘 `0.715`，生物强度乘 `1.1`；带电各再翻倍。
- `.reference/ite/decompiled/net/xiaoyu233/mitemod/miteite/trans/entity/EntityCreeperTrans.java:41` 与 `EntityInfernalCreeperTrans.java:41`：ITE 基础标量为 `1.25`，地狱本身翻倍，默认增强开启再翻倍。
- `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityBlackInfernalCreeper.java:19`：黑暗变种在此基础上再乘 `6.25`。

旧版地形采用随机能量射线、方块阻力与弱方块预破坏，没有固定球形物理半径。因此本次不把上述强度数值直接写入现代半径：普通地狱保留现代方块强度 `6`、带电 `12`，ITE 增强关闭和幼体仍按原配置缩放；黑暗地狱保留现有实际球形半径 `40` 格，历史幼体为 `20` 格。

旧版生火循环位于地形破坏开关之外，关闭 `mobGriefing` 仍可能放火。这里明确采用现代保护规则，关闭该规则时连同残火一起禁止。

## 验证

验证结果及配套产物记录在 [验收记录](verification.md)。
