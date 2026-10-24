# 2026-10-24 生物与制造调整

开发者：NingMeng123

- 骷髅暴君、龙头僵尸最早于第 33 天自然生成；普通刷怪笼、召援等自动生成入口同样检查此日期。极难怪物总解锁日期配置得更晚时，仍取两者中较晚的日期。生成蛋、命令和已有实体沿用原有豁免。
- 龙头僵尸在用户导出配置的绝对权重 1 基础上，通过独立物种平衡规则将自然候选权重再乘 0.5；不修改用户导出的自然生成倍率列表。
- 黑暗地狱苦力怕的爆炸半径由 37.5 增至 75，方块爆炸计算半径由 34.375 增至 68.75；不再产生分裂子体。已有小型子体保留存档与半径缩放。
- 黑暗地狱苦力怕使用独立的红色自发光眼睛遮罩，48 格内持续显示黑烟。遮罩沿用原模型的面部 UV，不依赖其他资源包的眼睛素材。
- 万圣僵尸的挖掘寻路不再选择普通、灵魂或红石火把及其墙上变种；其他挖掘规则继续生效。
- 血月巨人僵尸使用已有的 `minecraft:giant` 自然候选，保留权重 10、单只生成和 96 格同类排斥；专项测试验证血月夜晚包含、普通夜晚排除。
- 地牢僵尸 BOSS 的远程惩罚闪电限制在以 BOSS 为中心的水平 10×10 方块区域（X、Z 各不超过 5 格）。其近战规则和技能周期继续使用原值。
- 普通方块刷怪笼使用本体的 9 秒周期与累计 15 只自毁规则，ITE 的近场加速设置不再缩短这类刷怪笼倒计时。
- 火把捆的四个制作配方及一个拆解配方通过本体统一即时配方分支取出；铜棒采用最终本体铜材质 0.75 倍率，原总基础伤害 7 统一计算为 5.25。

验证入口为 `FeedbackMobBalanceGameTests`、`ExtremeSpawnProgressionTest`，并更新了刷怪笼计时及苦力怕分裂的现有测试。客户端红眼与烟雾需在游戏画面中验收。

默认配置先单独完成 `modern_ite_defaults` 的 1 项真实服务端测试，逐叶验证 113 项导出设置的 schema 默认值、首次加载值及生效快照。随后完成 38 项 JUnit 与 334 项服务端 GameTest，全部通过；后者包括本轮 8 项反馈专项及独立铁傀儡命名空间。日志分别为 `build/feedback-defaults-only.log`、`build/feedback-full.log`。测试依赖本体固定副本 `mite-0.2.9-current.jar`；最终发布包不包含测试类。

随后加强血月巨人测试，并单独完成 10 项 `modern_ite_feedback` 验证，全部通过，日志为 `build/feedback-giant-chain.log`。真实 `R196MobSpawning.select` 在完整自然池中以固定种子序列选出 `tk` 巨人；放置使用实际支持面高度，在无方块光、原版巨人寻路分值为负的场景通过 `qualifiesWithEvent`，再经 `prepareSourceFinalization`、`finalizeNaturalMonster` 和真实实体加入事件成功生成一只 `NATURAL` 巨人，确认近战 AI 安装。次日同入口拒绝新增巨人并保留已生成个体。资格来自既有的 ITE 自然生成事件特判，不依赖原版巨人偏好的高亮环境；本次复核无需修改生产逻辑或默认配置。

最终使用本体固定候选 `mite-0.2.9-final.jar` 重新完成 38 项 JUnit 与 334 项完整服务端 GameTest，包含加强后的巨人全链测试，全部通过，日志为 `build/feedback-final.log`。此最终配套本体包含 R196 动物繁殖冷却读档保护。ITE 生产包内容保持不变，`build/libs/modern-ite-0.2.9.jar` 的 SHA-256 为 `DA7750B4AAA5E5D357D2B36FA5A2ABBE2B3EA41007107A019F93FD746D7C0C49`。

后续附魔预览隐藏、幽匿尖啸体首叫满警告值、地底煤矿石/绿宝石及主世界倍率调整使用本体固定包 `../miteto1.21.1neoforge/build/verification/shrieker-ores/mite-0.2.9.jar` 再次执行 ITE 全量配套验证，38 项 JUnit 与 334 项真实服务端 GameTest 全部通过，日志为 `build/shrieker-ores-ite-full.log`，运行目录为 `build/verification/shrieker-ores-ite-run`。本轮无 ITE 生产或默认配置改动，发布包 SHA-256 保持不变。配套本体包 SHA-256 为 `425CC8C22B74BCF2BDFC32D7E6C7DC0CDDB81D4339EB067DDA6CF06E931B88B0`。

最终武器平衡与幻灵刃配套回归完成 38 项 JUnit、337 项完整服务端 GameTest，全部通过；日志为 `build/phantom-blade-ite-release.log`。随后在 ITE、Simply Swords、Better Combat 共存环境中，独立命名空间 `modern_ite_weapon_balance` 的 3 项测试全部通过，日志为 `build/phantom-blade-ite-optional-release.log`。测试验证八种注册棒的最终伤害、攻速和玩家负重，并用真实振金重锤校验旧属性组件归一化先于锻造、宝石与成长加成；多次读取及保存重载均保持各效果只生效一次。ITE 生产包 SHA-256 保持上述 `DA7750B4...D746D7C0C49`，验证目录和日志不纳入源码提交。
