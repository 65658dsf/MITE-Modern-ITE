# 普通铁傀儡挥臂但无法伤害玩家

开发与验证：NingMeng123，2026-10-20。

## 原因与修复

`ExtremeNativeMobs.initialize` 原先把普通铁傀儡的基础攻击设为 0，再在 `LivingIncomingDamageEvent` 中改为固定 25、强化时 28。原生 `Player.hurt` 会提前拒绝零伤害，无法进入这个事件；`IronGolem.doHurtTarget` 已经先广播挥臂，所以玩家看到攻击动作却不掉血。非玩家目标的旧测试没有覆盖这个入口差异。

普通铁傀儡初始基础攻击改为 25，让真实攻击进入伤害流程。原有事件继续决定固定 25／28 点伤害，保留原生十刻挥臂、击飞与攻击节奏。加载及每十刻同步时，仅对 `minecraft:iron_golem` 的零基础攻击恢复为 25，修复已经保存 `modern_ite:source_profile=true` 的旧实例。迁移保留已有非零基础值，不影响黑铁傀儡。

## 回归验证

独立 `modern_ite_golem` GameTest 命名空间使用现有空结构的副本，测试资源与审核代码不进入发布 JAR。`IronGolemMeleeGameTests` 使用真实生存模式 `ServerPlayer`，清除登录保护、装备和效果，核对真实伤害事件、扣血及击飞：

- 新生普通铁傀儡实际伤害玩家，保留十刻攻击动画和上抛。
- 真实 NBT 保存、重载、加入世界后修复旧零攻击实例。
- 已加载实例通过生产十刻同步恢复攻击。
- 已有强化状态随配置切换，伤害依次为 28、25、28。
- 旧档非零自定义基础攻击在加入和同步后保持不变。
- 黑铁傀儡不进入普通铁傀儡迁移。
- 普通铁傀儡对敌对生物仍造成固定 25 点伤害。

在隔离构建目录 `miteto1.21.1neoforge/build/verification/golem-fix` 中，先用修复前生产代码执行负控：7 项中的 4 项失败，真实玩家攻击返回 false、基础攻击为 0、伤害事件列表为空。覆盖修复后，无额外动画模组以及安装 Better Combat 2.4.0、Player Animator 2.0.4、EMF 3.3.12、ETF 7.2.6、Cloth Config 15.0.140 的两次服务端运行均为 7 项全通过；扩展构建及 30 项 JUnit 通过。日志分别为 `before-with-mods.log`、`native.log`、`with-mods.log`。

定向运行：`gradlew.bat runGameTestServer -PmodernIteGameTestNamespaces=modern_ite_golem`，并按实例指定 `miteJar` 与 `modernIteGameTestDirectory`。默认仍运行原有 `modern_ite` 命名空间。

本轮服务端验证未重新进行客户端资源包绘制审核。首次隔离验证包为 `build/releases/modern-ite-0.2.6-golem-fix.jar`，采用本体 `6282c9e2`、扩展 `14be841` 的已提交快照并覆盖本轮修复与测试，未带入其它未提交改动。

按用户要求，最终本体和扩展版本统一为 0.2.5，保留当前全部修复；扩展本体依赖下限及默认构建路径同步为 0.2.5。正式构建产物为两仓各自的 `build/libs/mite-0.2.5.jar`、`build/libs/modern-ite-0.2.5.jar`，并集中保存到本体的 `build/releases/0.2.5/`。应同时使用这次构建的两个 JAR。

最终两仓的 `build compileGametestJava` 均通过，本体 1664 项、扩展 30 项 JUnit 无失败或错误。0.2.5 配套实例加载上述可选动画模组后，7 项铁傀儡专项测试再次全部通过，日志位于 `build/verification/release-025/golem-gametest.log`。两个发布 JAR 的实际版本、NingMeng123 署名及本体依赖下限已核对，保留动画适配类与资源，不含审核器和第三方动画库。
