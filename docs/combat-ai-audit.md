# 极难生物战斗 AI 检查

开发与验证：NingMeng123，2026-10-19。范围为当前 45 种极难／ITE 生物，以及极难规则改写的普通僵尸猪人。

## 已复现并修复

| 生物／触发条件 | 原因 | 修复 |
| --- | --- | --- |
| 极难骷髅被缴械、拾起弓剑或切换镜像装备 | 原生装备回调只重装原版目标，旧的源近战／远程目标仍留在选择器中 | 极难骷髅在装备回调内统一重配源攻击目标；覆盖全部 11 种骷髅 |
| 镜像骷髅、骷髅僵尸等在正在拉弓时切换近战 | `GoalSelector.removeAllGoals` 只删除条目，没有执行旧目标的 `stop`，旧控制锁及物品使用状态可能继续占用 | 配置攻击目标时逐项调用 `removeGoal`，实际停止旧目标；新攻击可在下一次调度接替 |
| 普通弓猪人失去弓，或原本持剑的猪人捡起弓 | 只在进世界时按武器安装一种攻击任务，后来没有接替任务 | 常驻弓任务与低优先级近战任务，依当前主手切换；保留原有索敌和装备抽取 |
| 弓猪人在 15 格内被墙挡住 | 距离够近就停止导航，随后因无视线直接返回，持续站着不追不射 | 使用实际射击瞄点判定，遮挡时继续寻路，能射击时再停步 |
| 空闲极难僵尸被铁傀儡攻击，或玩家骑蝙蝠后僵尸将目标转向坐骑 | 已选目标合法，但原有三条近战任务仅接收玩家／村民／动物，铁傀儡和蝙蝠均不匹配 | 主模组增加优先级 5 的通用近战兜底，保留原有三类任务及索敌规则 |

骷髅攻击类别保持不变时沿用原目标和倒计时；速射手原有 30 tick 节奏正常。本轮没有修改生物数值、技能强度或生成概率。

## 验证

新增 10 项 GameTest，加上此前镜像兼容的 4 项，分别在安装和不安装 Better Combat 的环境执行。安装环境同时包含 Simply Swords 1.70.2、Better Combat 2.4.0、Player Animator 2.0.4。主模组依赖固定为本轮构建的快照，避免并行构建覆盖测试输入。

- 全 45 种生物：实际进入世界、主动锁定生存玩家，并具备可用攻击任务；保存后重建、读档、再次进入世界后重复检查。
- 骷髅：全部 11 种弓／空手／剑切换，真实选择器的运行状态、旧拉弓状态清理、速射间隔和镜像装备更新。
- 僵尸：通过实际复仇／坐骑目标选择，执行选择器选中的兜底近战，验证原生伤害和 Better Combat 延迟伤害。
- 猪人：真实选择器完成丢弓近战、遮挡寻路、持剑→弓→剑切换、真实箭矢和实际伤害。

| 运行 | 结果 | 本地日志 |
| --- | --- | --- |
| 修复前的骷髅 4 项及僵尸 2 项 | 6 项均在预期故障断言失败 | `build/verification/extreme-ai/baseline-present.log` |
| 修复前的猪人 2 项 | 2 项均在预期故障断言失败 | `build/verification/extreme-ai/pigman-baseline.log` |
| 修复后，Better Combat + Simply Swords | 14 项全部通过 | `build/verification/extreme-ai/fixed-present.log` |
| 修复后，不安装 Better Combat | 14 项全部通过 | `build/verification/extreme-ai/fixed-absent.log` |
| 修复后，追加已有实体状态、技能与弓箭回归，无 Better Combat | 共 36 项全部通过 | `build/verification/extreme-ai/existing-combat-regression.log` |
| 主模组索敌、近战、注册与兼容回归，无 Better Combat | 58 项全部通过 | 主模组 `build/verification/extreme-ai-base-absent.log` |
| 主模组注册与兼容回归，有 Better Combat | 32 项全部通过 | 主模组 `build/verification/extreme-ai-base-present.log` |
| 主模组构建 | 通过（本次跳过整套 JUnit） | 主模组 `build/verification/extreme-ai-base-build.log` |
| 拓展构建、14 项 JUnit、全部测试源码编译 | 通过 | `build/verification/extreme-ai/extension-build.log` |
| 仅暂存内容的隔离构建、14 项 JUnit、36 项无 Better Combat 战斗回归 | 全部通过 | `build/verification/extreme-ai/commit-scope/candidate-verification.log` |

首次 45 种矩阵的普通 Player 测试替身未进入原版玩家查询，改用真实生存 ServerPlayer 注册到 ServerLevel；测试替身不进行客户端登录，避免可选模组的网络握手影响索敌检查。猪人领主、女巫、苦力怕、暗黑铁傀儡的索敌及攻击任务检查通过。

45 种矩阵是索敌和攻击调度检查；实际伤害、武器切换与遮挡寻路使用上面的专项测试验证，不能视为穷尽所有地形或多人实战。本轮没有做多人实机验收。Simply Swords 的缺少可选第三方材料的两条配方解析错误与上轮相同，不属于本次战斗修改。

## 本地产物

更新时同时替换主模组与拓展。固定输出目录为 `build/verification/extreme-ai/release/`，两包均为 0.2.0 的当前工作区联调构建。本轮改动的实体类已与编译输出核对一致，两个包均未包含测试类，核对记录为 `build/verification/extreme-ai/release-check.json`。

| JAR | SHA-256 |
| --- | --- |
| `mite-0.2.0.jar` | `b8b7c957bae1ba94254d75b6ae33d045c76beef954ee400b044edff9fd75be6e` |
| `modern-ite-0.2.0.jar` | `aec1322a03ad2dc2246b97caf8492a54a782d7f13511b2b42bdcf3823c325611` |
