# 自动验证与游戏内验收

开发署名：NingMeng123。

## 黑暗地狱苦力怕保留容器库存掉落（2026-10-26）

专属爆炸现在会释放被实际摧毁的箱子、桶、熔炉、发射器、投掷器、漏斗及其他实现 Container 的方块库存。处理每个物理容器自身的槽位，暂存物品后执行原有无掉落爆炸／移除回调，确认原容器移除后再在抑制范围外撒出物品，避免回调重复释放及爆炸衰减吞物；如果容器拒绝移除，则恢复原槽。普通方块、容器本体、熔炉替代材料与普通生物仍无掉落。玩家死亡、5%玩家僵尸与蘑菇云规则保持。

双箱每半仅处理自己的库存，按原 BlockEntity 身份识别，允许左半移除后的连接状态变化；排队后换成同类型同状态但不同实体的新容器会跳过。潜影盒内部物品散落，不额外生成潜影盒本体；末影箱不读取玩家个人库存，不抽取可能跨容器或远程网络的通用物品能力。本体天然试炼容器的库存禁掉落规则保留，装饰罐沿用既有例外；doTileDrops=false 时仍按原生库存散落语义释放存储物品，游戏规则值不变。

38 项 JUnit、构建与全部 GameTest 源码编译通过。独立爆炸专项 **24/24 通过**，日志为 `build/infernal-container-blast-final.log`。新增五项实际爆炸回归覆盖 18 种单容器和双箱两半、三种原版熔炉、本体五炉、振金炉／箱和金属升级炉、两种潜影盒，逐项核对带名称、耐久、附魔的库存数量；另覆盖保护事件、不同类型／同类型替换、关闭 mobGriefing、天然试炼标记与关闭 doTileDrops。旧回归精确核对箱子／熔炉共46颗钻石，其余方块材料、生物装备与经验仍无新增。厚地形压测仍完整处理129395块、零物品／经验；87个服务tick，最大实测4.02ms，软预算不代表严格上限。

首轮发现双箱右半因连接状态变化被旧队列误跳过，已改为按物理容器实体校验；随后两项新夹具曾把超出本体堆叠上限的钻石当作初始库存，现使用上限内数量并在爆炸前校验实际存入值，数量守恒断言保持。

最终 **360/360 全量服务端测试通过**，日志为 `build/infernal-container-full-final.log`。首轮全量另遇既有范围测试在实体加入后立即起爆的查询时序问题；将其改为独立批次、持有区块并等待内外目标实际可查询后，成年40／幼体20格边界的健康和击退断言保持，最终全量通过。该调整不改变生产伤害规则。

按用户要求，最终源码和 JAR 内 `META-INF/neoforge.mods.toml` 的版本号均为 **0.2.13**，以该版本重新构建并通过38项JUnit及全部测试源码编译；日志为 `build/infernal-container-0.2.13-build.log`。冻结成品 `build/delivery/infernal-container-drops/modern-ite-0.2.13.jar`，1556117字节，SHA-256 `90F81495268A8F7B60A61FDABC88AB397C3295B8E83BC9B41DDCA4D6BC8AAFA4`。`build/verification/infernal-container/package-audit.json` 核对306个生产类、747个资源与编译输出一致，无测试、审计类、本体类或嵌套JAR。固定配套本体0.2.14的SHA-256为 `BC3DF7B95B2278D008C76F6335D69B20CE59EACD70A1677119885429076FA560`。本轮未改客户端特效或重跑图形客户端。

## 玩家爆炸死亡掉落与5%持久玩家僵尸（2026-10-26）

黑暗地狱苦力怕的专属死亡掉落拦截现在只针对非玩家生物，玩家重新经过原生死亡掉落、经验和 LivingDropsEvent 流程。所有玩家死亡（包括该爆炸）统一以 nextInt(20)==0，即 5% 判定玩家僵尸；生成成功时保管实际死亡掉落，未命中或加入失败时物品正常落地。keepInventory 和消失诅咒仍由原生流程处理，不复制保留的背包；方块、容器和普通生物的专属零掉落继续生效。

玩家僵尸在构造和读取旧存档时恢复持久标记，不随距离、空闲时间、和平难度或日晒消失，不会水下转化。非玩家归属的环境、其他生物及爆炸伤害不能杀死它；玩家近战和玩家投射物正常生效，击杀后只释放一次保管物品。保留显式 /kill 管理清理入口，不妨碍区块正常保存和卸载。

构建、38 项 JUnit 和 **355 项全量服务端 GameTest 全部通过**，日志为 `build/player-death-fix-final.log`；独立的爆炸与厚地形专项 **19/19 通过**，日志为 `build/player-death-fix-blast.log`。新增三项原生 ServerPlayer 实际爆炸回归，验证 5% 未命中落地、命中僵尸保管、keepInventory、消失诅咒及分帧挖坑结束后物品仍存在；新增生命周期回归覆盖旧存档、远离和和平难度、环境／怪物伤害，以及玩家近战／箭击杀和重复回调不复制。19 个失败随机结果逐一保留实际原始掉落。

初轮将可选厚地形压测与全量测试混跑时出现两项旧场景失败：压测区域有物品、光照 1 边界等待超时；正常全量复跑中光照通过，另遇旧角落工具尚未进入实体查询的固定 5 tick 假设。该夹具改为有界等待工具实际可查询后再起爆，未改生产范围规则或光照逻辑；随后全量 355/355 通过，独立厚地形压测零物品／经验，旧光照测试原样隔离 **6/6 通过**（`build/player-death-fix-light-isolated.log`）。未确认首轮混跑失败的唯一原因，不将其归因于生产修复。

成品为 `build/delivery/player-death-fix/modern-ite-0.2.14.jar`，1555032 字节，SHA-256 `F878728796AA748C81E0F2FA8E5595C583A3D451E1A12E588EF0D39402931F66`，配套本体 0.2.14。`build/verification/player-death-fix/package-audit.json` 核对 306 个生产类、747 个资源与编译输出一致，无测试、审计类、本体类或嵌套 JAR。本轮只执行服务端测试，未重跑图形客户端。

上述冻结交付产物包含当时工作区中的独立模型兼容改动；本次玩家死亡提交仅包含其行为、测试、文案和共享版本号，模型兼容另行提交。待提交快照已单独导出，使用本体 0.2.14 执行 `build compileGametestJava` 成功，38 项 JUnit 通过，日志为 `build/verification/player-death-fix/commit-snapshot-build.log`，确认无需未提交的模型文件即可构建。

## 黑暗地狱苦力怕蘑菇云消失修复（2026-10-26）

优化版曾把整朵云放进原生粒子引擎的单个批次。原生资源重载清空或粒子队列容量逐出时不会调用粒子 remove，因此云仍在计时，批次却已不再被绘制；批次原先固定在世界原点，也不能正确向按位置裁剪粒子的模组表达位置。现在在客户端 AFTER_PARTICLES 世界阶段直接提交同一个排序批次，位置使用实际爆心；不依赖原生队列存续，继续使用粒子图集、深度测试与最多四朵云的预算。资源重载后为现有烟团重新绑定图集 sprite。半径 40、无掉落、约 6 秒展开与 18 秒结束保持。

原生实机报告 `build/verification/infernal-explosion/visibility-regression/client-audit.json` 通过：实际苦力怕在 X=300.5 自爆，六个阶段截图确认逐渐升高、变大；第 80 tick 主动执行原生 ParticleEngine.clearParticles 后，第 120／220 tick 仍正常显示。第 120 tick 可见火云约 242×353 像素，坑内抬头截图确认近距离仍可见，低头时云在视野上方。400 tick 后清理完成，爆炸后及动画结束时均无物品、经验。对应日志 `build/infernal-visibility-client.log`。

额外装载 Sodium 0.8.13、Iris 1.8.14（关闭光影）、EntityCulling 1.11.3、AsyncParticles 21.1.4.8、AcceleratedRendering 1.0.14 的同一实际爆炸审计通过，报告为 `build/verification/infernal-explosion/visibility-optimized-client/client-audit.json`，日志为 `build/infernal-visibility-optimized-client.log`。截图再次确认队列清空后正常显示；平均帧间隔基线 8.43 ms、升起阶段 8.49 ms、成熟阶段 8.47 ms，受硬件和帧限影响，不代表所有设备或开启第三方光影的性能。

38 项 JUnit、最终构建和 16 项真实服务器专项测试通过，日志分别为 `build/infernal-visibility-final-build.log` 与 `build/visibility-server-final.log`。新增回归验证球形边界前置于公开 Detonate、半径外 (40,40) 角落铁镐不移动且不损坏、既有绿宝石总量保持，以及普通爆炸仍将 5 颗绿宝石转换为 5 个碎片。厚地形仍完整处理 129395 块，新增物品与经验为 0；此轮初始同步 36.65 ms、后续 79 tick，单次最大 15.38 ms，4 ms 是回调检查点间软预算，不能视作硬上限。

修复成品冻结在 `build/delivery/mushroom-visibility-fix/modern-ite-0.2.13.jar`，1545132 字节，SHA-256 `C9964A2E1B4DE98075C9AFE66138F571F5F9E6B8F5143B70995C2894B99FF319`。`build/verification/infernal-explosion/visibility-package-audit.json` 核对 303 个生产类、747 个资源与编译输出逐字节一致，无测试、本体类或嵌套 JAR。配套本体仍为 mite-0.2.12.jar。

## 黑暗地狱苦力怕圆形爆坑、环流蘑菇云与性能优化（2026-10-25）

开发署名：NingMeng123。

成年体的实际破坏范围是以爆点为中心、半径 40 格的球，地面截面为直径约 80 格的圆；旧存档小型个体为半径 20 格。伤害与击退立即结算并遵守 40／20 格边界，方块在保护模组筛选后从中心向外分帧破坏。整个服务器共享每 tick 最多 2048 个方块、约 4 ms 的软预算，多个爆坑公平轮转；不会在一个 tick 破坏 13 万方块或一次发送整坑坐标。保留基岩、黑曜石、流体、取消事件与 mobGriefing；排队期间修改过的方块、未加载的区块会跳过，关停破坏规则、卸载世界和停服会清理对应作业。

此专属爆炸不产生方块、容器内容物、直接炸死生物的物品与经验；原版和其他爆炸仍正常掉落。抑制仅作用于本次爆炸的死亡或方块回调，不修改全局掉落规则，不额外扫删世界里已有物品。后续边界回归补充：专属爆炸保留既有物品，不把绿宝石、钻石转换为新碎片；公开 Detonate 事件前将实体候选限制在真实球形边界内。

烟云运动设计参考 HBM 的 [EntityNukeTorex](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT/blob/06e9aa0a3d6aa3a1e627bc5079f98a169265eddd/src/main/java/com/hbm/entity/effect/EntityNukeTorex.java) 与 [RenderTorex](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT/blob/06e9aa0a3d6aa3a1e627bc5079f98a169265eddd/src/main/java/com/hbm/render/entity/effect/RenderTorex.java)，在本项目中独立实现地面供烟、上升热柱和云顶内侧上升／顶部外翻／外缘下卷的环流；原版粒子贴图配合空间热度，形成较亮热核与同时存在的暗色外围烟团。约 6 秒逐渐展开，后续继续缓慢翻卷、供烟并冷却，18 秒结束。上方凝结雾延后出现，每颗新生粒子都平滑长大和淡入。

全细节每朵云最多生成 868 个烟团，较原来的 2492 个减少约 65%；远距离和减少／最少粒子设置继续降低密度，最多保留 4 朵云。整个效果复用一个排序批次，按远近正确绘制透明团块；公共升高、扩张、冷却和环流时钟每云每 tick 只计算一次，无需逐粒子网络包。2026-10-26 改为在世界粒子阶段直接绘制，详见上方消失修复。

配套本体为 mite-0.2.12.jar。38 项 JUnit、构建与全部 GameTest 源码编译通过；modern_ite_feedback 与 modern_ite_infernal_benchmark 共 16 项真实服务端测试全部通过，日志为 build/infernal-optimized-server-rerun.log。逐格检查 85×85 石头平台圆形边界、19／21 格幼体边界、实体伤害与击退、基岩／黑曜石、已修改方块保护、开关破坏规则、取消爆炸及保护事件；开启正常掉落规则，用填满物品的箱子／熔炉和保证装备掉落的生物验证专属零掉落，并验证普通爆炸仍有物品和经验。

厚地形实测选中并完整处理 129395 块石头，新物品和经验球均为 0。初始同步处理约 102.10 ms（选块 52.55 ms、入队 49.55 ms），后续使用 193 个服务 tick，最慢一次约 6.42 ms；4 ms 是检查点间软预算，单次原生方块回调无法中断。正常 20 TPS 约需 9.65 秒完成这块厚地表，期间继续正常 tick；GameTest 不限速墙钟约 1165 ms 不应当作正常游戏挖坑时间。此前整坑同步破坏与掉落的同规模测试约 3978 ms、1611 个掉落物，见 build/infernal-benchmark.log。

隔离客户端通过真实自爆、网络包接收、六个阶段的升高／展开截图、粒子批次与帧耗时统计，以及爆炸后和动画结束时物品／经验为零的验证。报告为 build/verification/infernal-explosion/client-audit.json，截图为同目录 mushroom-cloud-{5,20,45,80,120,220}ticks.png，最终构建与实机日志为 build/infernal-optimized-final.log。400 tick 检查烟云完成清理。运行命令为 ./gradlew.bat runClient -PmodernIteInfernalExplosionAudit=true；本轮未进行第三方光影或双客户端联机验收。

该轮历史成品已冻结在 build/delivery/mushroom-optimized/modern-ite-0.2.12.jar，为 1541353 字节，SHA-256 FB33ACB34D5606929A6E2C1E6E9DD0F00D03881CA8E36570053405430B2E45DE；不包含 GameTest 或客户端审计类。新版成品见上方消失修复。

## 颠倒效果同步翻转玩家模型（2026-10-25）

`modern_ite:reversed` 现在接入原版玩家倒立渲染，按实体高度平移后绕 Z 轴翻转 180°，玩家皮肤、盔甲和手持物品一起倒立。保留原有镜头翻转和移动输入反向；效果解除后立即恢复正常，原版 Dinnerbone／Grumm 行为保持。可见状态同步共用 `PlayerVisualEffectSync`，对附近追踪玩家补发颠倒效果，支持开始追踪、刷新、移除和自然到期，正常倒计时不逐 tick 刷包。

隔离客户端 `./gradlew.bat runClient -PmodernIteReversedPlayerAudit=true --console=plain` 通过。真实混入后的本地／远程玩家判定、玩家旋转矩阵和普通僵尸对照均通过；宽／细两种装备玩家的 GPU 截图确认整体倒立，解除后逐像素恢复。报告为 `build/verification/reversed-player/client-audit.json`，截图位于同目录的 `mob-render-audit/`，日志为 `client.log`。

服务端通过 `build/verification/reversed-player/sync.init.gradle` 仅筛选 `BucketHeadSyncGameTests`，4 项真实 ChunkMap 追踪和原生效果包测试全部通过，覆盖铁桶效果回归及新增颠倒同步，日志为 `build/verification/reversed-player/sync-gametest.log`。本轮使用远程玩家渲染与原生观察者数据包验证两端，未运行两个真实客户端联机。

最终 `build compileGametestJava` 和 38 项 JUnit 通过，日志为 `build/verification/reversed-player/build.log`。产物 `build/libs/modern-ite-0.2.12.jar` 包含新渲染 Mixin 和同步类，已排除旧同步类与测试类；1,502,919 字节，SHA-256 `e9b84f6184cb211960411bf569c4dacdff0994e347ec1950e94074c5145d3212`，记录见 `build/verification/reversed-player/package-audit.json`。

## 主模组统一刷怪配置与可编辑默认值（2026-10-23）

ITE 0.2.9取消参考／自定义切换，以ITE 2.0.28数值为可编辑默认值。主模组的“怪物刷新权重 → 上限与刷怪节奏”页面通过可选接口接入34个刷怪控件；未安装ITE时不注册这些字段，仍显示本体配置。ITE入口仅打开同一主页面，原重复ITE规则页已移除。所有额度、日期、自然拥挤阈值、深层倍率、尝试次数和调度概率均有实际读写路径，保留独立深浅预算和来源物种条件。

旧配置在ConfigSpec加载前识别废弃开关并一次迁移旧默认值，移除开关后不再覆写用户后续编辑。主页面提供器验证了完整字段、类型／边界、非法请求不改值、有效保存到隔离TOML、调度与额度立即读取及原值恢复。怪物解锁天数、玩家死亡僵尸10%及日期／平安期豁免、分裂均保留。真实草地召狼回归发现新生狼尚未初始化地面状态导致无法寻路，在确认支撑后设置onGround修复，现已验证60 tick后召1～3狼且成功后不重复。

构建与**37项JUnit、315项全量ITE服务器GameTest全部通过**，日志`build/verification/unified-spawning/final.log`。本体单独安装的`mite_config,mite_spawning` **55项GameTest**通过，日志位于相邻本体工程`build/verification/unified-spawning-base-tests.log`。本体`runData -PmiteGameplayConfigClientAudit=true`也通过入口、后端链接与中英文静态审计；本轮未执行真实窗口渲染或双客户端联机。

配套产物保存在`build/verification/unified-spawning/delivery/`：主模组`mite-0.2.9.jar` SHA-256 `12a148599eb75ecf86b6d0368439174acdf0626b10388c0d3c25769ce53bfe39`；ITE `modern-ite-0.2.9.jar` SHA-256 `26497b4cbae8cd1d5680dbb877b33fd6f7ddea0f24d33dc33062dd77e3391249`。`package-audit.json`确认ITE263个生产类、743个资源与输出逐字节匹配，无旧重复规则页、测试或本体类混入；分裂源文件保持不变。新版ITE依赖主模组0.2.9的新接口，两个包需配套替换。

## 历史验证：可配置极难日期与10%玩家死亡僵尸（2026-10-23）

模组设置的ITE刷怪规则首屏新增 `extreme_mob_spawn_limit_day`，默认65、范围1～1000000，两种刷怪预设均生效。按历史最高日期解锁，门槛后额外Extreme自然怪及狂犬加入对应自然池；降低门槛可提前普通物种，原有更晚物种日期、维度、特殊池、光照、数量额度与拥挤保护仍生效。玩家僵尸仍为死亡事件专用，概率由25%改为10%；只有真实玩家死亡来源豁免日期及开局平安期，局部人口准入、失败回退和物品只释放一次的规则保留。

构建、**36项JUnit、310项全量服务器GameTest全部通过**，日志为 `build/verification/extreme-unlock-day/gametest-final.log`。覆盖两预设日期前后、动态调高／降低、历史回退、物种晚日期／维度、参考特殊群系与结构、自然权重0、无自然入口身份、真实自然资格／Finalize／Join，以及第1天且门槛999时的死亡掉落接管。10个概率结果逐个验证，1个成功、9个保留原掉落。首轮仅新测试的假玩家 `moveTo` 因无网络连接报错，改为既有夹具使用的 `setPos` 后全部通过，未因此修改生产逻辑。

生产包 `build/libs/modern-ite-0.2.8.jar`，1,463,656字节，SHA-256 `35b3c008a2548002cd0c265d92ea9128c8b68eefce5be748bfd761cb7d7637c7`。固定本体依赖为 `build/verification/extreme-unlock-day/mite-base.jar`，SHA-256 `b078c37b33ed6d319370b9b34bf345587c1588bd821b541e7ecf693b66879368`。`package-audit.json`确认265个生产类、743个资源逐字节匹配，无开发测试、本体类或嵌套JAR；分裂源文件保持不变。配置UI审计源和双语检查已更新，本轮未执行真实图形客户端或双客户端联机。

## 历史验证：ITE 生物在第 64 天结束后生成（2026-10-23）

参考和自定义两种预设均对全部 `modern_ite` 生物的自动生成增加历史最高日期第 65 天底线，覆盖自然池、刷怪笼、转换、替换、骑手、召援、事件和无生成原因的直接加入。更晚的物种日期与原维度、光照、平安期条件继续生效；本体 `mite` 生物日期、命令、生成蛋和已有存档实体保留。自定义自然池对物种日期取较晚值，防止统一底线把闪电骷髅第 96 天等原有晚期条件提前。

构建、**35 项 JUnit、301 项全量服务端 GameTest 全部通过**。新增真实入口回归覆盖两种预设的 45 种 ExtremeKind 加狂犬、第 64 天最后 tick `1535999` 拒绝、第 65 天首 tick `1536000` 解锁、九种自动生成原因、转换加入、召援、候选池过滤、已有实体与回拨；既有光照与生成权重夹具改为解锁后的日期。铁门防御的控制器恢复测试显式开启怪物 Better Combat 配置并在同步测试结束后恢复，适配其默认关闭的新基线。

初轮命令：`./gradlew.bat build runGameTestServer -PmiteJar=build/verification/ite-day65/mite-base.jar -PmodernIteGameTestDirectory=build/verification/ite-day65/run-final --console=plain`；日志为 `build/verification/ite-day65/gametest-final.log`。该轮固定本体依赖 SHA-256 为 `cf71cf3e58403f1d1c58f689a5b610debdcc3bc7905b641cb44be261b0951af5`。本轮未执行真实客户端或双客户端联机。

随后配套包含日常合成计时补表的新本体重新构建和全量验证，冻结依赖为 `build/verification/final-crafting-day65/mite-final.jar`，SHA-256 `82e2fb2b7c7b5804c1a3f436ae0b9f7e113fb78270f609d0b9995455002d76fb`。新本体首轮 300/301 通过，既有石英等级 2 交易断言偶发失败；只补充其库存、等级、经验和有效配置诊断后，使用独立测试世界再次 **301/301 全部通过**，包括第 64/65 天真实 tick 边界。前后本体仅合成计时相关生产类变化，石英和经验实现逐字节一致；未修改其生产逻辑，未确认该偶发失败的原因。最终日志为 `build/verification/final-crafting-day65/gametest-diagnostic.log`，配套产物核对为 `build/verification/final-crafting-day65/final-pair-audit.json`。

最终交付本体另补齐静态烟花配方 `firework_rocket_simple` 的源难度 100，冻结为 `build/verification/final-crafting-day65/mite-final-firework.jar`，9,584,906 字节，SHA-256 `b078c37b33ed6d319370b9b34bf345587c1588bd821b541e7ecf693b66879368`。使用该依赖再次构建并执行 **301/301 全量 GameTest 全通过**，第 64/65 天真实 tick 边界保留，石英失败未再次出现；ITE 成品哈希保持下方所列值。交付配套日志为 `build/verification/final-crafting-day65/gametest-firework-final.log`，核对记录为 `build/verification/final-crafting-day65/final-pair-firework-audit.json`，先前冻结依赖与日志均保留。

交付 `build/libs/modern-ite-0.2.8.jar`，1,461,791 字节，SHA-256 为 `1aaf25b70e0b755b24e362bf4e35bd83bcaba80bc2638d939bb107735b08b1ef`。`build/verification/ite-day65/package-audit.json` 确认 265 个生产类、743 个资源与编译输出逐字节一致，无测试类、本体类或嵌套 JAR。

## 振金熔炉右键无法使用修复（2026-10-22）

振金熔炉继承的原版 `FurnaceBlock.openContainer` 只接受 `FurnaceBlockEntity`，而振金炉实体继承 `AbstractFurnaceBlockEntity`，因此右键消耗了交互却未打开菜单。现在为振金炉重写容器入口，打开其真实实体的原生 `FurnaceMenu`，保留炉口可达限制和熔炉交互统计。

新增两项回归在修复前均复现失败，日志 `build/verification/vibranium-furnace-before.log`；修复后 **30项JUnit、5项熔炉GameTest全部通过**，日志 `build/verification/vibranium-furnace-fixed.log`。新增测试覆盖真实方块右键、菜单库存绑定、原生同步器向客户端菜单副本传输热量五、注册ticker第199/200 tick的产出边界、燃料消耗及四朝向阻挡后恢复；另三项为既有熔炉JEI索引和实际批量冶炼回归。新测试使用默认启用的 `modern_ite` 命名空间。

首次在 `build/verification/vibranium-furnace-snapshot` 中以已提交版本仅叠加本次熔炉源码和测试完成修复前后对照；随后对包含其他既有改动的当前完整工作区再次验证：构建、全部GameTest源码编译、30项JUnit和5项熔炉GameTest全部通过，日志为 `build/verification/vibranium-furnace-full-build.log` 和 `build/verification/vibranium-furnace-full-gametest.log`。完整构建与专项测试分别使用 `build/verification/vibranium-furnace-full.init.gradle`、`build/verification/vibranium-furnace.init.gradle` 隔离输出，固定本体为 `build/verification/vibranium-furnace-base/mite-0.2.7.jar`。

最终修复包 `build/libs/modern-ite-0.2.7-furnace-fix.jar` 包含当前完整工作区改动，为1,432,581字节，SHA-256为 `ea7a8b15c37ffa812eefcacfe07539a928a713c31fc8b55f140b6656e5b80f9d`；炉方块类与完整构建及隔离回归的编译输出均一致，无测试类、本体类或嵌套JAR，核对记录为 `build/verification/vibranium-furnace-package.json`。

## 大力士僵尸与击飞骷髅统一约15格（2026-10-22）

此前将平地受击的小跳高度作为减半基准，导致最终只有约 `0.577` 格，不符合十几格击飞的要求。现将两种怪的有效近战命中最终向上速度统一为 `1.6929821475097433` 格/tick，标准空气、上方无障碍时上升约15格；原生运动包量化后约 `14.998` 格。保留近战末尾设置速度、覆盖快落、金心承伤仍触发以及拒绝伤害不触发的规则。

23 项战斗 GameTest 全部通过，日志 `build/verification/fifteen-block-launch-gametest.log`。实测 `Player.travel` 覆盖72组运动状态／击退抗性组合、两种怪同高、连续命中及吸收生命；原生 `ServerPlayer` 运动包发送后，使用解码速度再次模拟实际移动，检查上升 `15±0.01` 格。负对照包含无敌、取消、受击冷却及盾牌完全格挡。专项命令为 `./gradlew.bat -I build/verification/launch-height.init.gradle runGameTestServer --console=plain`。

真实客户端在独立新世界完成四项验收：大力士僵尸、默认木剑击飞骷髅分别在平地和三层雪旁土墙攻击真实生存玩家，经原生网络同步和 `LocalPlayer` 正常 tick，两者四次实测均上升 `14.99836672524168` 格。测试玩家临时增加生命避免木剑直接击杀；客户端不注入速度。报告 `build/verification/launch-height-client.json` 状态为 `passed`、失败列表为空，日志 `build/verification/fifteen-block-launch-client.log`。可复跑命令为 `./gradlew.bat runClient -PmodernIteLaunchHeightAudit=true --console=plain`，自动创建并关闭专用测试世界和客户端。本轮未执行真实双客户端联机或完整第三方整合包测试。

`./gradlew.bat build compileGametestJava --console=plain` 成功，30项JUnit及全部GameTest源码编译通过，日志 `build/verification/fifteen-block-launch-build.log`。成品 `build/libs/modern-ite-0.2.7.jar` 的击飞实现与编译输出一致，无测试类；SHA-256：`8d5ea93d63e457fd029a719732ae10bb2dbae1033ba74e7447b26509579ff978`。

## 历史调整：大力士僵尸与击飞骷髅漏击飞修复（2026-10-22）

复现了快落抵消向上冲量，以及金心承伤未减少普通生命导致技能被跳过的问题。现在两种怪在真实近战成功且生命或吸收生命减少后，将最终向上速度固定为 `0.2681150704457247` 格/tick，标准空气中上升约 `0.577` 格；地面、空中、跳跃、快落和击退抗性均不改变此次高度。纵向技能完全移到近战末尾，保留骷髅原有水平击退和大力士的小幅水平分离及动画。

新增回归在修复前复现 5 项失败，日志为 `build/verification/reliable-launch-before.log`。修复后构建、30 项 JUnit、全部 GameTest 源码编译和 23 项战斗 GameTest 全部通过：72 组地面／空中、六种初始纵向速度、三档击退抗性及两种怪的真实命中与 `Player.travel` 高度；金心完全吸收仍起飞；连续有效命中；无敌、取消、伤害冷却和原生盾牌完全格挡负对照；实际 `ServerPlayer` 与 `ServerEntity.sendChanges` 捕获发送给受击者本人的小速度运动包。原生同步确认能发送低于 `3.9` 的最终速度，无需改变协议。日志为 `build/verification/reliable-launch-build.log`、`reliable-launch-gametest.log`，专项启动仍使用 `build/verification/launch-height.init.gradle`。本轮未重跑全量 GameTest 或真实双客户端联机实战。

## 历史调整：大力士僵尸与击飞骷髅高度统一（2026-10-22）

大力士不再采用固定高抛，改为与当前击飞骷髅共用命中向上冲量、原版近战结算及半高反解。普通地面命中最终向上速度均约 `0.268115070` 格/tick，标准空气上升约 `0.577` 格；相同运动状态与击退抗性下两者同高。大力士保留双臂上挑及 `0.25` 格/tick水平分离，完全吸收和拒绝伤害不触发技能，暗黑铁傀儡强度保持。

18 项战斗 GameTest 全部通过，其中用真实近战和 `Player.travel` 对照地面、空中、跳跃、两种下落速度及上升速度、普通／完全击退抗性共 12 组，核对两者最终纵向速度和实际上升高度一致；同时验证地面上升不足一格、快落保持、完全吸收、拒绝伤害和速度同步队列。专项命令仍为 `./gradlew.bat -I build/verification/launch-height.init.gradle runGameTestServer`，日志为 `build/verification/launch-height-aligned-gametest.log`。构建、30 项 JUnit 和全部 GameTest 源码编译通过，日志为 `build/verification/launch-height-aligned-build.log`。本轮未重跑全量 GameTest 或真实联机客户端实战。

## 历史调整：大力士僵尸与击飞骷髅高度减半（2026-10-22）

大力士最终向上速度降为 `5.710941971` 格/tick，标准空气无障碍上升约 `111.142` 格，为此前一半。击飞骷髅在完整近战结束后，按原版重力与空气阻力将最终上升高度减半，覆盖地面受击速度钳制，保留水平击退及既有快落；完全吸收和拒绝伤害不触发额外调整。暗黑铁傀儡保持原强度。

构建、30 项 JUnit、全部 GameTest 源码编译及 18 项战斗 GameTest 通过。真实 `Player.travel` 覆盖大力士半高、骷髅地面／空中／跳跃／下落及普通／完全击退抗性共 8 组半高对照，另覆盖快落、完全吸收、拒绝命中和旧高速度同步队列清除。专项启动包含 `SpawnBalanceTestSetup`，确保原有攻城尸和女巫召援测试满足日期前提。日志为 `build/verification/launch-height-build.log`、`launch-height-gametest.log`，命令为 `./gradlew.bat build compileGametestJava` 和 `./gradlew.bat -I build/verification/launch-height.init.gradle runGameTestServer`。本轮未重跑全量 GameTest 或真实联机客户端实战。

## 0.2.6 疯狗、矮冬瓜诅咒和末影人大头（2026-10-21）

新增独立敌对疯狗与速度 I、成功咬伤后 120 tick 的服务端自动挥砍；女巫在原诅咒流程中加入半生命、半攻击属性、半体型的矮冬瓜诅咒；末影人头部及原生发光眼睛固定放大 2 倍。玩法与管理员命令见 [三项生物更新](mob-additions.md)。

`build`、30 项 JUnit 和完整 266 项服务端 GameTest 全部通过，其中新增疯狗 4 项、失控攻击 5 项、矮冬瓜诅咒 7 项。覆盖真实攻击与受击、无敌拒绝、距离和墙体、120 tick 到期、原生施咒、属性恢复、牛奶与祛咒瓶、保存和跨维度、真实死亡复活以及管理员命令。日志为 `build/verification/mob-additions/gametest-rerun.log`。

隔离客户端 GPU 验证通过，报告 `build/verification/mob-render-audit.json` 的 `status` 为 `passed`、失败列表为空。相同镜头下末影人头宽由 35 像素变为 70，发光眼睛由 108 像素变为 468；愤怒和携物姿态、重复动画、正常身体尺寸及移速检查通过。疯狗专用狼模型、1.2 倍头部、愤怒狼材质及两个新状态图标通过。截图为 `build/verification/mob-render-audit/enderman_big_head.png` 和 `rabid_dog.png`，日志为 `build/verification/mob-additions/client-rerun.log`。本轮未进行真实双客户端联机实战或完整第三方整合包测试。

成品 `build/libs/modern-ite-0.2.6.jar` 的 990 个生产类和资源条目与构建输出逐字节一致，无 GameTest 或客户端审计测试类。SHA-256：`b865cff43334439fe2c4fa6a4f49f0eb8d92261cab85b6295efeb0de4fe59381`。保留了工作区原有铁桶头盔资源修改。

## 0.2.6 苦力怕与骷髅小型死亡分裂（2026-10-21）

各类苦力怕与骷髅默认以3%概率生成6～8只同类型小型个体，苦力怕自爆也可触发；幼体半体型、四分之一日期成长后生命、半攻击伤害，苦力怕爆炸半径减半，幼体不再分裂。保留骷髅配置开关、初始停滞和生成额度。镜像生命阈值、闪电箭固定伤害、黑色地狱苦力怕专属爆炸均已适配。本体固定站立尺寸原先覆盖原生SCALE，现按同步属性补回小型碰撞尺寸，并在生成落点检查前立即刷新。

使用本地构建的 `mite-0.2.6.jar`，执行 `./gradlew.bat build compileGametestJava`：30项JUnit、构建及全部GameTest源码编译通过。隔离世界执行 `./gradlew.bat -I build/verification/mob-splitting.init.gradle runGameTestServer -PsplitFocused=true`，11项专项GameTest全部通过：19种骷髅、10种苦力怕的真实死亡路径；失败概率与取消死亡；成长刷新、伤势、存读与尺寸；防重复和防递归；实际伤害管线中的近战、箭与闪电固定伤害；普通、地狱及黑色地狱爆炸半径；自爆后幼体存活；镜像生命复制；配置关闭与零数量；每名父体最多8个存活随从。

日志为 `build/verification/mob-splitting-build.log` 和 `build/verification/mob-splitting-server-verified.log`，安装包为 `build/libs/modern-ite-0.2.6.jar`。已有骷髅数量配置值会保留，将 `compressed_skeleton_expand_count` 设为7即可采用6～8只。此次未运行全量GameTest或真实联机客户端验收。

## 0.2.5 铁桶套头与第三人称遮蔽（2026-10-21）

铁桶僵尸成功近战命中玩家后施加独立 `modern_ite:bucket_head` 状态，持续 200 tick，重复命中刷新。原头盔实例及附魔、名称等组件保留，空头盔槽也不插入物品；到期或喝奶解除。玩家头上的铁桶由独立渲染层绘制，支持宽／细模型、下蹲及大头缩放，并通过原生效果包向追踪玩家同步。第一人称、背后第三人称、正面第三人称均遮挡上方 75% 视野，只露出屏幕底部 25%。南瓜僵尸的原生南瓜视野遮罩也在三个视角下生效，两种遮罩均不受 F1 隐藏 HUD 影响。

使用当前本地本体 `mite-0.2.5.jar`，隔离输出 `build/bucket-head`；构建、30 项 JUnit 和全部 GameTest 源码编译通过。9 项专项 GameTest 全部通过，其中 7 项覆盖真实近战、原头盔及组件保留、空头槽、拒绝命中、远程归因和其他僵尸负对照、非玩家负对照、刷新、自然到期及真实牛奶清除；另 2 项通过实际 ChunkMap 追踪、服务端 tick 和连接包捕获，验证新追踪者补状态、添加／刷新／移除／到期同步，以及普通倒计时不逐 tick 发包。

隔离客户端新建测试世界完成原生 GPU 检查：三视角铁桶遮罩逐像素相同，上部完全不透光、下部场景像素完整保留；南瓜遮罩三视角一致，F1 保留遮挡，双效果叠加仍保留铁桶上部不透明区域；移除后原像素恢复。宽／细玩家模型的独立桶层、原钻石头盔保留、下蹲和大头变换也通过检查。桶层采用原生铁块的不透明材质，完整覆盖内部头盔和面部。日志为 `build/verification/bucket-head/build.log`、`gametest-final.log`、`client-final.log` 和 `final-build.log`；图像及完整客户端报告分别位于同目录 `mob-render-audit/` 和 `client-audit.json`。

本轮安装包为 `build/bucket-head/libs/modern-ite-0.2.5.jar`，包含工作区已有修改；生产类和资源均与构建输出逐字节一致，无测试类、本体类或嵌套 JAR，核对见 `build/verification/bucket-head/package-audit.json`。本轮未重跑全量 GameTest，也未执行两个真实游戏客户端的联机实战。

提交前在当前 0.2.6 主线基础上，仅导出本次暂存内容至 `build/verification/bucket-commit/snapshot`，使用本体 `mite-0.2.6.jar` 独立验证：构建、30 项 JUnit、全部 GameTest 源码编译及 9 项套桶／同步 GameTest 全部通过。日志为 `build/verification/bucket-commit/candidate-build.log` 和 `candidate-gametest.log`；客户端 GPU 结果沿用上文此前验证，本次提交前未重新运行客户端。

## 0.2.5 灯笼种类精简（2026-10-20）

正常展示与合成仅保留艾德曼、振金；秘银保留注册及模型、语言以兼容旧存档，隐藏于创造栏、搜索和 JEI，移除合成配方；铜、银、金、铁、古代金属灯笼及专用模型、配方、语言移除。已有秘银灯笼的照明、耐久和金属粒修复继续生效。

执行 `./gradlew.bat build compileGametestJava`，构建、30 项 JUnit 和全部 GameTest 源码编译通过。通过 `./gradlew.bat -I build/verification/lantern-selection/focused.init.gradle runGameTestServer` 独立运行灯笼照明、铁砧修复和资源配方三组共 15 项 GameTest，全部通过，包含真实创造栏与搜索仅显示两档、秘银无任何配方产物以及三档修复验证。本轮未运行客户端或实际 JEI 界面验收。

发布包 `build/libs/modern-ite-0.2.5.jar` 为 1,344,578 字节，SHA-256 为 `794c0d4ca7e209dac0f121df616fac9ffc50f50f91a71339f520a84b56967b9f`。228 个生产类和 527 份 JSON 与源码及编译输出逐字节一致，确认仅三份灯笼模型、两份配方，无测试类、本体类、JEI 实现或嵌套 JAR。专项日志与包核对报告位于 `build/verification/lantern-selection/server.log` 和 `package-audit.json`；以下同步发布及更早记录保留为历史证据。

## 0.2.5 同步发布（2026-10-20）

本体与 ITE 同步发布为 0.2.5，仅调整版本元数据、默认依赖路径和最低依赖版本。ITE 的 228 个生产类与下方已验证交付包逐字节一致，本体的 1338 个生产类也与固定功能验证依赖一致，沿用下方 **217 项 GameTest** 的功能证据；对固定 0.2.5 本体依赖重新执行 **30 项 JUnit 和打包，全部通过**，不把改版本号视为再次运行 GameTest。

发布包 `build/libs/modern-ite-0.2.5.jar` 为 **1,348,205 字节**，SHA-256 为 `11f861dac2d3029499911e41c042fbfa8e1a2721ebc610888d7a328a298c0f66`；最低本体依赖为 0.2.5。包内 228 个生产类、737 份资源与输出逐字节一致，无测试类、本体类或嵌套 JAR。固定本体 SHA-256 为 `bd8fd7fc664bab7092c78251c62565eb9506db903aaa5ee5444a51221a2bce36`。构建日志与核对报告分别为 `build/verification/source-progression-0.2.5-ite.log`、`build/verification/source-progression-0.2.5-ite-package.json`。

## 0.2.1 旧版装备成长规则（2026-10-20）

本轮要求本体 0.2.4：非护甲成长装备上限为 15 加独立锻造等级，人形护甲为材质最低采集级乘 3 再加锻造等级；振金工具与护甲最高分别为 25、28。历史磨具组件保留，磨具点击不再消耗或扩展等级；零成长上限的皮革仍可镶嵌宝石并通过维护继承。锻造经验采用旧 float 表达式、整经验截断及护甲外层乘 2，单次奖励最多升一级并清空多余经验。

固定本体依赖 SHA-256 为 `3e88ab94e887cbaf316371dcaa92f1a18848cdb8f69bc8cda29747ccf0b77591`，保存在 `build/verification/base/mite-0.2.4-source-final.jar`。**30 项 JUnit、217 项组合 GameTest 全部通过**，包含实际 ServerPlayer 奖励截断、振金装备逐次升至上限、完整组件存档、皮革镶嵌、历史磨具无消耗和金属棒维护。组合日志为 `build/verification/source-modifier-final-ite.log`；随后仅补齐燧石/石/黑曜石/钻石四种原料采集级的纯算式一致性，重新执行 30 项 JUnit 和打包通过，日志为 `build/verification/source-modifier-final-ite-package.log`。既有刷怪笼设置测试补充登入后两 tick 的实体索引等待，配置切换的原倒计时断言保留。

交付包为 `build/libs/modern-ite-0.2.1-source-progression.jar`，**1,348,206 字节**，SHA-256 为 `8271f99bc74fba2ac68180ca04e7ae8984d9ae6283fe34912a9254ae788b73ff`。包内 **228 个生产类、737 份资源**逐字节匹配最终编译输出；无测试类、本体类或嵌套 JAR，依赖元数据最低本体版本为 0.2.4。核对报告为 `build/verification/source-modifier-final-ite-package.json`。下方其他版本的验证记录保留为历史证据。

2026-10-19 的 0.2.0 振金符文、15 种简易武器动画、单层原版流光验证见 [符文与流光验收](vibranium-runes-and-glint.md)；灯笼与大头效果的最新增量验证见下方对应节。下方 0.2.0 材质叠加包的字节数与 SHA 为本轮之前的历史构建。

**0.2.0 材质轮次**移除了背部光翼及安装座，保留振金原纹理并在甲面叠加星光，全套振金每件保护至少V的触发门槛保持。该材质轮次的构建、14项JUnit、标题页、实际GPU及包一致性检查通过，源码按功能分类提交；该轮刷怪调整的验证见下节。以下0.1.12及更早的通过结果、包哈希和光翼预览均为历史证据。

## 0.2.0 开荒平安期、昼长与刷怪平衡（2026-10-19）

主世界日历到达 `37000 tick` 前禁止全部自动敌对生成，动物、命令及刷怪蛋保留；保护状态在主世界保存，跨维度共享，时间回拨、死亡或重进不重置。历史最高日期32天前白天按本体边界以0.5日历tick推进，夜晚正常；第32天起恢复原生昼长。只设置NeoForge原生昼长速率，停时规则、睡眠及游戏tick保持原有语义。

深浅层改用共享额度：`floor(50 × 有效区块并集 / 128) + max(100, 当前日期)`，血月三倍，无有效区块为0。289区块时普通前期212、首次血月636；日期项第101天起继续增长。玩家水平32格、垂直8格内普通上限32、血月48；逐次复核并登记同tick尚未加入的怪。自然生成距离至少24格，主世界极难怪方块光照须为0；普通3轮×4次、血月4轮×4次，相位蜘蛛使用相同尝试数。

12种领主及同级强敌共享历史最高日期33天门槛，覆盖自然池、猪人替换、骑手、召援与事件；原有维度、权重和地形条件继续生效。普通召援每个召唤者最多8个存活随从，死亡和移除释放额度；存档中的随从UUID重新计数。龙、凋零及拆笼Boss挑战保留编队数量，但不绕过平安期和领主日期限制。命令豁免只传给显式根实体与NBT乘客，不传给它们之后的技能召援；既有存档实体继续保留。

一次性结构敌对守卫在平安期被拦截时，将原UUID、维度和实体NBT存入主世界延期队列；符合条件且所属区块载入后补回一次，避免海底神殿等结构的固定守卫永久丢失。队列跨存档保留并去重，循环自然刷怪不排队。没有召唤者UUID的本体自动召援也在真实Finalize/Join入口复核局部密度。

测试新增或更新日期额度与强敌门槛JUnit、日历策略、世界保护存读、跨维度状态、自然光照0/1/7真实传播、23/24格距离、8种自动生成原因、无原因加入、命令NBT实体树、刷怪蛋、同tick重叠玩家密度、交替深浅候选共用212额度、存活随从上限和读档重建，以及结构延期队列存读、自然候选不排队和原UUID只补回一次。原生 `ServerLevel.tickTime` 每组实推20次，确认早期白天日历+10而游戏时间+20、夜间两者均+20、停时时仅游戏时间+20，第32天白天恢复两者均+20。

修改全局时间的同步用例集中于独立 `spawn_balance` 批次，异步光照用例各自独立批次；测试保存并恢复游戏时间、日历、日历小数、速率、最高日、保护状态和停时规则。服务端战斗测试基线仅在原生GameTestServer中从第33天夜间开始，普通客户端和用户世界不受此测试设置影响。

最终构建、**25项JUnit与175项服务端GameTest全部通过**，其中 `spawn_balance` 19项、实际光照传播2项均通过。服务端175项耗时19.25秒，保留日志为 `build/verification/spawn-balance/verified-server.log`。首轮172项中的刷怪相关18项已通过，唯一失败为既有防御测试的玩家空间查询fixture；测试场地改用首次固定锚点和80格间隔，避免测试重排后场地重叠。最终全套175项包含该用例并通过，没有据此调整生产AI。

复现使用冻结本体 `build/verification/defense-intent/mite-0.2.0.jar`，SHA-256为 `e720e895f79338c80f57e21caa33ec951079ef0e0a1fc50a28f2038fa55f0353`；命令为 `./gradlew.bat build runGameTestServer '-PmiteJar=build/verification/defense-intent/mite-0.2.0.jar'`。本轮产物 `build/libs/modern-ite-0.2.0.jar` 为 **1,227,463字节**，SHA-256为 `b8aa575d63ce85ba224e8157fc8f6a1e39342863e1232facf857c1d0b0ae6461`。包审计 `build/verification/spawn-balance/package-audit.json` 确认192个生产类与编译输出逐字节一致，528份JSON可解析、全部编译资源一致，无测试类、本体类或嵌套JAR。产物保留工作区已有的其他改动，未修改本体源码或拓展版本号。

跨维度真实服务端测试载入主世界、下界和末地；地底世界未在该GameTestServer中实际载入，只能确认它复用相同共享规则，仍保留游戏内验收。真实联机客户端的昼长同步、多人长期刷怪分布和整合包组合本轮未实测。

提交范围另做隔离验证：排除任务开始前已有的拟态僵尸生命、概率和存档迁移改动及其4项新增测试，从Git暂存区导出至 `build/spawn-commit`。独立构建、**25项JUnit和171项服务端GameTest全部通过**。验证中补齐防御夹具的成长刷新及完整场地实体tick就绪等待，保留原有断言。提交候选包为 `build/spawn-commit/build/libs/modern-ite-0.2.0.jar`，**1,225,084字节**，SHA-256为 `08c24d5a87e51f8d7beee68e562c627439717d840f2e37a4f98a24c743ed75ec`；192个生产类与编译输出一致，528份JSON及资源核对通过，无测试类、本体类或嵌套JAR。审计为 `build/verification/spawn-balance/commit-audit.json`，服务端日志为 `build/verification/spawn-balance/commit-verified-server.log`。前述175项及其包哈希仍属于包含其他未提交改动的工作区验证。

## 0.2.0 持盾攻击意图与主动防御（2026-10-19）

普通盾经 ITE Mixin 接入本体 `ShieldBlockGoal`，与僵尸盗贼铁门共用 `DefenseIntent`。持械逼近、近战戒备、真实攻击、弓弩／三叉戟瞄准及预测来箭触发主动防御，取消随机起盾门槛。非仇恨玩家威胁只改变临时防御焦点；停步转身后继续追击原目标。有限窗口、强制休息、起盾延迟、斧破防及真实正面格挡范围保留。完整规则见 [攻击意图与主动防御](defense-intent.md)。

使用冻结本体 **0.2.0**，SHA-256 为 `e720e895f79338c80f57e21caa33ec951079ef0e0a1fc50a28f2038fa55f0353`，在独立 `build/defense-intent-build` 输出、`run-defense-intent-gametest` 世界运行。最终构建、**22 项 JUnit、161 项服务端 GameTest 全部通过**；配方／标签解析错误为 0。测试总数包含当前工作区的其他功能，本次意图与门防御分别有 10 组真实 GameTest，另新增 8 项数学测试。

覆盖两 tick 真实位置观测、停步／远离／传送、实际武器攻击距离、主副手远程使用、已装填弩、三叉戟、近期攻击过期、非仇恨玩家与稳定焦点，以及空手／挖掘／背对／隔墙／创造／旁观／盟友负例。来箭测试包含穿透、擦身、远离、途中阻挡及射手隐藏；松弓后通过世界实际推进箭矢，验证盾牌耐久减少且生命不损失。防御链覆盖 5 tick 起手、有限窗口、休息与破防、真实近战互斥、侧后伤害、转向限速、保留物理运动和放盾恢复追击。铁门另验证原生挥手及 Better Combat 状态恢复期间不举门、恢复后的 Pre 优先起门，同 tick Post 不重复计时；可选 Better Combat 客户端未安装，恢复用例使用本体真实状态接口构造隔离测试状态。

独立客户端 `modernIteDefenseAudit=true` 验证通过：普通盾／铁门 × 双惯用手共 4 组，**224 次原生客户端 tick、36 张 GPU 姿态图**，含侧转、反向、角度环绕及网络插值移动；头部每 tick 不超过 25°、身体不超过 15°，原生模型装备方向与实际身体／头部方向一致，并有卸下装备的像素负对照。普通盾使用原生服务端物品使用同步数据；没有加载既有世界或连接多人服务器。拼图已人工检查。

最终产物 `build/defense-intent-build/libs/modern-ite-0.2.0.jar` 为 **1,204,283 字节**，SHA-256 为 `ca456a66085f1c25da12e601ddaa219fd73faaffafa95943ca9bbf8180cfdffa`。184 个生产类与编译输出逐字节一致，3 个防御 Mixin 齐全，无测试类、本体类或嵌套 JAR。此包保留当前工作区其他待提交功能，未修改本体仓库或版本号。

日志和报告均在 `build/verification/defense-intent/`：最终服务端 `verified-server.log`、客户端 `client-audit.json`、包核对 `package-audit.json`，姿态拼图 `mob-render-audit/defense-intent-poses.png`。本轮命令为 `./gradlew.bat -I build/verification/defense-intent/isolated.init.gradle -PmiteJar=build/verification/defense-intent/mite-0.2.0.jar build runGameTestServer`；同一依赖和隔离 init 加 `-PmodernIteDefenseAudit=true runClient` 执行客户端专项。init 只设置上述独立输出与测试世界目录。

提交前仅提取持盾意图及其铁门防御前置代码，共 26 个文件，在 `build/verification/defense-intent/commit-check/snapshot` 独立完成构建、**22 项 JUnit、157 项 GameTest，全部通过**。拟态僵尸的并行源码、测试和文档改动均未纳入；上述 161 项与客户端图像仍为此前工作区验证记录。本次提交候选日志为 `build/verification/defense-intent/commit-check/candidate-build.log`，包核对为同目录 `candidate-audit.json`。

## 0.2.0 击退僵尸水平击退修正（2026-10-19）

击退僵尸从大力士的固定上抛分支分离，成功近战结束后追加 `3.2` 格/tick的向外水平速度，纵向完全保留原生受击及既有跳跃／下落速度。大力士与暗黑铁傀儡的上抛规则保持；下方0.1.10两种僵尸统一上抛的记录属于历史行为。

使用固定依赖 `build/verification/base/mite-0.1.10.jar`，独立构建、14项JUnit与14项战斗GameTest全部通过。新增回归用真实 `doHurtTarget` 对照普通僵尸，覆盖地面／跳跃／下落、四个攻击方向、普通／完全击退抗性共24组，确认明显水平推离、方向正确、纵向速度与普通受击一致；保留大力士半高、运动状态不改变上抛高度、拒绝伤害不追加特技和速度codec检查。

本次只运行 `ExtremeCombatRegressionGameTests`，未重跑全量GameTest或客户端。日志为 `build/verification/knockback-fix-gametest.log`；测试包 `build/knockback-fix-build/libs/modern-ite-0.2.0.jar` 包含当前工作区的其他待提交更改。

提交前仅提取本次5个文件的改动，在 `build/verification/knockback-commit/snapshot` 独立完成构建、14项JUnit和14项战斗GameTest，全部通过；日志为 `build/verification/knockback-commit/candidate-build.log`。

## 0.2.0 灯笼提携动画与耐久（2026-10-19）

第一人称绘制真实玩家手臂和袖子，手掌握住提环，前臂从侧面伸入，灯体向下悬挂；站立轻晃、行走摆动并保留装备切换和挥手过渡。第三人称提臂姿态、掌心锚点和悬挂摆动同步支持左右惯用手及主副手。弓弩、盾、望远镜、游泳等动作保留原有手臂姿态。

后续修复耐久同步引起的周期性抬手抖动：灯笼物品的原生换装备判断忽略单独的耐久变化，保持手持高度并立即更新渲染缓存；切换槽位、材质、空手和其他组件变化仍保留动画。构建与14项JUnit通过；原生客户端对八材质双手执行440次手持渲染器tick，连续耐久同步及最后一点耗尽均不降低手持高度，真实切换负对照正常。该回归与原有GPU／HUD／光照检查一起通过，日志为 `build/verification/lantern-wear-client.log`，结果位于客户端报告的 `lanterns.carry.durability_reequip`。

八种灯笼最大耐久依材质减半为铜800、银1200、金1600、铁2400、古代金属3200、秘银4800、艾德曼8000、振金12800。耐久条满值也显示，双语提示新增剩余／总耐久，副手槽上方显示数值，低于等于四分之一变黄、耗尽变红；F1隐藏数值。照明效果、每10 tick消耗2点和耗尽后保留物品的规则沿用。

使用固定本体 `build/verification/base/mite-0.1.10.jar`，独立输出构建、14项JUnit、6项灯笼GameTest全部通过。原生客户端专项通过40个物品显示样本及16个实际持灯样本，覆盖双惯用手、主副手、站立／行走、卸下后的共享模型恢复；另检查实际HUD满／半／空数值、F1与非灯笼负对照，以及原有照明和药水兼容。截图已人工检查。报告为 `build/verification/lantern-carry-client-audit.json`，姿态拼图及HUD截图在 `build/verification/mob-render-audit/lantern-carry-poses.png` 和 `lantern-durability-hud.png`。

混合工作区全量GameTest为142/144通过，失败为既有的 `idleextremezombiecanretaliateagainstanirongolem` 与 `idleextremezombiecanattacktheselectedbatmount`；对应历史基线复现见下节。本次未修改僵尸AI。灯笼专项的最终独立日志为 `build/verification/lantern-carry-focused-gametest.log`，客户端日志为 `lantern-carry-client-final.log`。

产物 `build/lantern-carry-build/libs/modern-ite-0.2.0.jar` 包含当前工作区其他待提交功能，170个生产类和528份JSON与编译输出／源码逐字节一致，无测试类、本体类、JEI实现或嵌套JAR。扣耐久抖动修复后的大小1,177,402字节，SHA-256为 `d46e1252e849a81af91962eccbb27acbb487dba751e31f82ead8974c886cd2c3`，最新包检查记录为 `build/verification/lantern-wear-package-audit.json`。

## 0.2.0 大头减速与镜头拉近（2026-10-19）

大头效果保留玩家头部和帽层两倍缩放，补充固定45%移速减益，以及客户端FOV系数额外乘0.75的镜头拉近。减速基准来自旧Extreme的 `getSpeedBoostVsSlowDown`；当前实现使用独立属性修饰符，刷新和升级不叠加，移除或自然到期均清除。原版速度、缓慢效果保留，玩家FOV设置不修改。最终视角还会叠加原生移速对FOV的影响，不能将额外0.75系数视为最终视角比例。

使用固定依赖 `build/verification/base/mite-0.1.10.jar`，在当前含其他待提交功能的工作区完成独立输出构建、14项JUnit和GameTest源码编译。新增5项服务端专项全部通过，覆盖实际玩家移速、等级与刷新、自然到期、原版效果共存，以及真实大头僵尸15格边界和施加刷新。隔离客户端新建测试世界检查通过：头部/帽层添加与移除、4组原生FOV采样（效果比例0/1、设置55/100）、重复查询和续时、玩家间隔离，以及已有FOV事件修饰的乘法组合。

全量GameTest为140/143通过，失败项为 `dragonbreathneverexpandsbeyondoneandhalfblocks`、`idleextremezombiecanretaliateagainstanirongolem` 和 `idleextremezombiecanattacktheselectedbatmount`。另在源码副本中将此次两个生产文件恢复为HEAD，保持其余工作区逻辑，单独运行这两类测试：13/15通过，两项相同的僵尸AI失败复现，龙息用例通过。全量测试未全部通过，龙息失败的完整原因尚未确认；本次未修改龙息或僵尸AI。

提交前仅提取本次7个文件的改动，在 `build/verification/big-head-commit/snapshot` 独立完成构建、14项JUnit和GameTest源码编译，全部通过；日志为同目录上级的 `candidate-build.log`。上述143项服务端结果与客户端检查属于此前混合工作区验证。

产物 `build/big-head-build/libs/modern-ite-0.2.0.jar` 为1,167,165字节，SHA-256为 `c20b9e814baa7975376aa13ffcb0580305804d15eb13fd54f3c497b443a07de9`；166个生产类与本次编译输出逐字节一致，无测试类、本体类、JEI实现或嵌套JAR混入。日志为 `build/verification/big-head-build.log`、`big-head-gametest.log`、`big-head-focused-gametest.log`、`big-head-baseline-gametest.log` 和 `big-head-client.log`；客户端与包检查报告分别为 `big-head-client-audit.json`、`big-head-package-audit.json`。

## 0.2.0 初次材质叠加历史构建：移除光翼，在原甲面叠加星光

背部光翼及安装座均已移除；星光叠加在原振金甲面上，原材质RGB和alpha都参与绘制，保留蓝灰颜色、明暗层次及像素纹理，也保留面孔、裸前臂及透明开口。玩家与僵尸共用原触发规则：四个正确槽位穿齐振金护甲，每件普通保护附魔或MITE保护成长词缀至少V，两类来源可跨部件混用，同件等级不相加。

**初次材质叠加构建历史状态：0.2.0验收通过。** 14项JUnit本轮执行通过，GameTest源码编译通过；服务端108项GameTest沿用0.1.11历史证据，本轮未重跑。标题页、原生GPU、200帧／10fps／20秒GIF和包一致性检查通过。玩家与僵尸的原材质颜色、纹理边缘、皮肤开口及动态星光均通过检查，背部原生轮廓2像素容差之外的新几何为0。本地包 `build/libs/modern-ite-0.2.0.jar` 为1,068,706字节，SHA-256为 `a473b57c60c9c1587dfe29479292a375d19d832ca5c506880cb770928c826d18`；157个生产类、528份资源JSON和3份shader均与编译输出或源码一致，包内无光翼类。动图为 `build/verification/mob-render-audit/cosmic-vibranium-armor.gif`，SHA-256为 `30d07b385e8a86a33fa02ed13581ec23157021ffda1321781abd0af56db33c5b`。日志为 `build/verification/armor-overlay-build.log`、`armor-overlay-client.log`、`armor-overlay-render.log`，当前完整核对见 `build/verification/release-audit.json`。初次构建当时仅保留本地改动；其后续源码与符文、原版流光修改一并分类提交。

## 0.1.12 历史版本：振金配色与根部向尖端流光

触发门槛保持为玩家或僵尸四个正确槽位均穿振金，每件普通保护附魔或MITE保护成长词缀至少V，可跨部件混用；同件两类等级不相加。甲面继续只使用原生护甲贴图alpha，RGB不用来上色；程序化星空采用原振金锭底色 `#404058`、`#4A5C8A`、`#6E6E86` 与高光 `#77CEFB`、`#D8D8D8`，保留面孔、裸前臂及透明开口。Re-Avaritia固定提交的渲染方法参考与独立程序化资源说明沿用下方记录。

4对、共8片阶梯像素光翼跟随最终身体姿态及幼年模型缩放。黄→绿→青色带以 `FLOW_PERIOD_TICKS=80`（4秒）由根部向尖端平移，亮屑沿翼移动；完整星空周期仍为400 tick（20秒），时间来自实体tick，额外粒子为0。固定姿态GPU审计分别比较0、5、10、80 tick，并用归一化色比排除单纯亮度脉冲：玩家两侧5／10 tick分别向外平移12／23像素，僵尸两侧为13／25像素；80 tick回归，轮廓IoU均超过0.95。

**本轮渲染范围验收通过。** 构建、GameTest源码编译、标题页与实际GPU检查通过；14项JUnit复用既有通过结果，服务端108项GameTest未重跑，历史来源明确为0.1.11的 `build/verification/cosmic-gametest.log`。新预览 `build/verification/mob-render-audit/cosmic-vibranium-flow.gif` 为200个原生GPU帧、10fps、20秒，SHA-256为 `25afd9c67ae946467963ee6eeaecb66094822762df769b97e645b1a48aa187dc`。

本地包 `build/libs/modern-ite-0.1.12.jar` 为1,077,523字节，SHA-256为 `675a8b1ea22c2915c048ee4efd05a20e3d9fcca0cbc0df5897bf7ba665a772b1`；160个生产类、528份资源JSON及3份shader资源均与编译输出或源码逐字节一致，无测试类、本体类、旧运行时类、嵌套JAR或JEI实现混入。日志为 `build/verification/vibranium-flow-build.log`、`vibranium-flow-client.log`、`vibranium-flow-render.log`，当前完整核对为 `build/verification/release-audit.json`。0.1.12源码提交包含全套振金保护V星空和动态光翼；下方独立0.1.10发布范围保留为历史证据，真实多人实战仍待验证。

## 0.1.11 历史本地版本：星空甲面与八片光刃

四个正确槽位均为振金护甲，且每件普通保护附魔或MITE保护成长词缀至少V时，玩家和僵尸共用星空甲面及背部光刃；两类来源可在不同部件混用。混入其他材质、缺件、错槽或一件不足V均不触发，同一件普通IV与成长IV不相加。原生护甲纹理只用于alpha覆盖区域，RGB不参与最终颜色；头、胸、腿、靴的可见甲面统一替换为星空，保留原生面孔、裸前臂与透明开口。

实现参考 [Re-Avaritia的固定cosmic.fsh](https://github.com/Nova-Committee/Re-Avaritia/blob/43fdb64ceacf07662c39c1e82cc07fdc322163ef/src/main/resources/assets/avaritia/shaders/core/cosmic.fsh) 的视线与分层星空方法，适配本项目NeoForge 1.21.1原生shader管线。星云、星点和颜色均独立程序化生成，没有复制上游星图或美术资源。每个实体的shader pass立即绘制，时间来自实体tick，不使用真实墙钟；400 tick（20秒）循环可用于固定时刻GPU复验。

参考图的背部结构落实为4对、共8片阶梯像素光刃，浅金经黄绿到翡翠绿流光；安装座和光刃跟随最终身体姿态，幼年模型采用对应身体分支缩放。星空与光刃共用400 tick周期，额外粒子数量为0。最终动态预览目标为 `build/verification/mob-render-audit/cosmic-light-wings-dynamic.gif`：40个实际GPU帧，每帧500毫秒，总长20秒。

**0.1.11历史状态：本地最终验收通过。** 最后亮度调整后构建、14项JUnit、108项GameTest、标题页及实际GPU检查全部通过，配方和标签错误为0。GPU控制覆盖完整星空甲面、皮肤开口、背部光刃连接、姿态与动态变化；40帧GIF已核对总长20秒，SHA-256为 `b35bc5f3547dd9fc3bb5562e2ca8c5cd8b9bfa4c2edcb1a4a335abc1c415aa4e`。发布包 `build/libs/modern-ite-0.1.11.jar` 为1,076,512字节，SHA-256为 `08726d0481484031538b7b553a5eae17c61692c1162c80d1c15ee6505d506e0f`；160个生产类与编译输出逐字节匹配，528份资源JSON及3份shader资源与源码逐字节一致，无测试类、本体类、嵌套JAR或JEI实现混入。日志为 `build/verification/cosmic-build.log`、`cosmic-gametest.log`、`cosmic-client.log`、`cosmic-render.log`；该版本历史核对摘要保存在 `docs/verification-summary.json` 的 `local_validation_0_1_11`；同名 `release-audit.json` 当前已更新至0.1.12。0.1.11当时未作为独立版本提交或推送，其后续实现纳入0.1.12源码提交；真实多人实战仍待验证。

## 0.1.10 已推送版本独立验收（2026-10-18）

该范围已随提交 `c03abc3f25cc6971a0127e1b264aee0fff3f3f11` 推送GitHub。验证时从候选树 `874bedee6d35186435001997ed02b62613868651` 建立 `build/verification/commit-prep/snapshot`，独立完成构建、**14 项 JUnit 和 102 项 GameTest**，失败和错误均为0，配方与标签解析错误均为0。102项是本次隔离提交范围的测试，与历史混合工作区的108项分别记录；最终文档仅补充实测证据，不改变已验证的代码和资源。

标题页检查通过45个生物渲染器、2个弹体、160个物品模型、7个状态效果图标和2个菜单工厂；振金棒以128×128单帧尺寸加载8帧动画，每帧3 tick。实际GPU检查通过45种纹理、14个重点模型、玩家大头添加／移除，以及5组生物粒子预览；所有检查仅使用隔离目录和自动新建测试世界，未进入用户存档。可选JEI实际界面检查仍是下文历史证据，本轮未重新运行。

发布包 `build/verification/commit-prep/snapshot/build/libs/modern-ite-0.1.10.jar` 为 **1,052,650 字节**，SHA-256 为 `2e509fb2ec3cf1ea8ae01c3985b230f54797dbff25d72d9c139255180aa6b74e`。全部 **152个生产类** 与本次编译输出逐字节一致，**527份资源JSON** 解析成功且与打包内容逐字节一致；无测试类、本体类、旧运行时类、嵌套JAR或JEI实现混入，已删除生物的刷怪蛋与掉落表均未打包。

日志：`build/verification/commit-prep/submission-build-gametest.log`、`submission-client.log`、`submission-render.log`。完整独立核对记录为 `build/verification/commit-prep/submission-validation.json`；标题页和GPU报告在同目录的 `snapshot/build/verification/`。真实多人高延迟、Better Combat安装后的实战及长期生存平衡仍待游玩验证。

## 0.1.10 历史行为：大力士与击退僵尸向上击飞

本次“大力士僵尸”和“击飞僵尸”分别对应 `modern_ite:zombie_fly_away` 与现名“击退僵尸”的 `modern_ite:zombie_knockback`；旧Extreme资源和当前注册均没有第三个独立“击飞僵尸”身份。两者的成功近战命中统一为向上抛飞：最终 `vy = 9.105464833` 格/tick，水平速度仅作 `0.25` 格/tick的小幅分离；不再随天数增大，也不保留原击退僵尸的远距离水平弹射。

暗黑铁傀儡的高度基准固定为最终 `vy = 15` 格/tick。按标准空气中的原生重力与阻力逐tick递推，从命中位置计算的理论最高上升量约为 `444.57` 格；两个僵尸约为 `222.285` 格，目标是实际高度约为其一半，不能直接把初速度除以二。这些数值为标准空气理论基准；本次隔离候选提交的真实 `Player.travel` 测试已确认半高比例，误差小于0.02格。

此前在 `LivingDamageEvent.Post` 应用特技时，事件之后仍有原生受击击退逻辑，可能覆盖已经施加的向上速度。现在由 `ZombieVariant` 和 `GolemVariant` 的实际 `doHurtTarget` 成功返回后再设置最终速度，并同步到玩家客户端，确保近战命中链结束时保留完整击飞。

历史混合工作区在2026-10-18通过构建、14项JUnit及108项GameTest，配方和标签解析错误为0。其真实近战链、真实玩家 `travel` 半高（误差小于0.02格）、18组运动／抗性组合、拒绝伤害和速度codec检查均作为历史证据保留；日志为 `build/verification/launch-build.log` 与 `launch-gametest.log`。这些历史数字不代表本次候选提交。本次隔离范围的102项GameTest已通过，包括真实近战链、半高、18组运动／抗性组合、拒绝伤害和速度codec；发布包一致性通过，多人实战仍待验证。

## 弓箭手预判与静止命中率（2026-10-18）

普通骷髅、本体长死人及 11 种拓展骷髅共 13 个实体类型，在简单、普通、困难三个难度分别验证 6、15、30 格平地距离，以及 15 格距离的正负 5 格高低差。每个组合以 100 支固定随机种子的真实原生箭射击已加入世界的静止生存玩家，逐 tick 执行箭矢碰撞，以实际扣血计数；195 个组合、共 **19,500 箭全部命中，每组最低 100/100**，达到至少 95% 的目标。场景为无遮挡空气环境、站立玩家，不以伤害直调或仅方向检查代替命中。

移动测试覆盖全部上述骷髅的 0.215 格/tick 横向走位，每种各 100 箭，预判命中至少 85%，且比无运动信息对照至少多命中 60 箭；猪人真实持弓 Goal 的静止与移动各 100 箭也通过对应门槛。另覆盖服务器位置取样识别停步、反向、传送及同 tick 多弓手共用观测，石墙遮挡拒绝射击，以及发射后持续遵守原生重力、阻力。6 项新增纯数学测试独立积分弹道，验证静止距离/高差/朝向、5 类移动轨迹、限幅、不可达目标及数值边界。

在包含其他待提交改动的当前工作区，以本地固定 `build/verification/base/mite-0.1.10.jar` 为依赖，执行 `./gradlew.bat build runGameTestServer -PmiteJar=build/verification/base/mite-0.1.10.jar`，**14 项 JUnit、105 项 GameTest 全部通过**，配方与标签解析错误为 0。发布包 `build/libs/modern-ite-0.1.9.jar` 的 158 个生产类逐项匹配当前编译产物，包含预判 Mixin，不含测试类、本体类或嵌套 JAR；包检查记录为 `build/verification/predictive-archery-package.json`。

提交前从 `322bc1e` 单独提取本次弓箭手代码、测试和文档，在 `build/verification/archery-commit-scope/snapshot` 验证候选提交：构建、**14 项 JUnit、67 项 GameTest 全部通过**，三种难度的静止测试仍全部为 100/100。候选保留基线版本及其他功能，显式使用上述固定本体包的绝对路径；日志为 `build/verification/archery-commit-check.log`。此前 105 项结果属于混合工作区验证。

## 彩石及相关方块移除（2026-10-18）

移除 16 色彩石、彩石砖、台阶、楼梯、墙和缓冲彩光石，共 96 个方块及其物品注册；删除 720 个专用资源文件，清理配方、掉落、挖掘标签、中英文名称、素材来源记录及生成脚本。

在包含其他待提交开发改动的当前工作区中，使用本地已验证的 `build/verification/base/mite-0.1.10.jar` 作为依赖，`build runGameTestServer` 通过，8 项 JUnit 和 97 项 GameTest 全部通过；配方解析错误与标签加载错误均为 0。该工作区源码与发布 JAR 均无已移除内容的代码或资源引用。日志与专项报告分别为 `build/verification/remove-colorful-build-gametest.log`、`build/verification/colorful-removal-audit.json`。

## 自动验证范围

- JUnit：日历边界、累计血月数、共享额度、日期增长及领主门槛、生命门槛、强化运算和经济抽取规则。
- 组合 GameTest：实际注册与组件序列化、45 种实体创建和保存、镜像装备引用、矿工寻路、专属弹体、Boss 基础生命和日成长、主装备组件维护、宝石槽不复制、四甲属性叠加、锻造成功/失败及读档继续、宝典真实制作流程、交易去重/读档/换职、真实钓鱼表、环境计数、复活安全和容器物品唯一性。
- 客户端自动检查：在隔离目录启动到标题页，检查45种生物及两个弹体渲染器、所有新增物品模型、两类菜单注册和七种状态效果图标；另在专属新世界做模型与粒子截图。
- 基础模组 0.1.9 独立回归：1590 项 JUnit、2054 项 GameTest 全部通过（`../miteto1.21.1neoforge/extension-base-regression.log`）。该记录不能替代其他会话在此之后修改的回归。

2026-10-18 的 **0.1.1** 最终构建：**8 项 JUnit、50 项组合 GameTest 全部通过**，包括亡灵恶魂充能同步和自然生成落点回归；配方解析错误 0、缺失标签错误 0。标题页自动检查 **50 个实体/弹体渲染器、253 个物品模型、7 个效果图标、2 个菜单工厂全部通过**。另完成下述五种生物的实际 GPU 绘制，不包含完整游玩和菜单视觉截图验收。发布 JAR 的边界与逐文件来源记录见 `build/verification/release-audit.json`。

仓库保留可审阅的精简结果 [verification-summary.json](verification-summary.json)。构建日志、运行存档和 `build/` 下的完整报告只在本地保存，不进入 Git 历史。

## 盾牌耐久消耗调整

仅安装 ITE 时生效：成功格挡后的玩家盾牌耐久消耗为本体的三倍，怪物为本体的一半并向下取整。例如耐久损伤预算为 4 时，本体扣 5 点，ITE 玩家扣 15 点、怪物扣 2 点。倍率在原生耐久附魔结算前应用；预算低于 3、取消事件、零预算、绕背不消耗和盾牌破损回调保留。本体源码不包含此倍率调整。

2026-10-18 验证：ITE 构建和 8 项 JUnit、54 项组合 GameTest 全部通过。新增 `ShieldWearGameTests` 的 4 项用例覆盖所有已注册盾材质、玩家/怪物、主副手、事件改写预算及末点破损。日志为 `build/verification/shield-wear-build.log` 与 `build/verification/shield-wear-gametest.log`。本体单独运行的 23 项相关 JUnit、34 项盾牌 GameTest 也全部通过，日志为 `../miteto1.21.1neoforge/build/verification/shield-wear-baseline.log`。

## 副手材质灯笼（2026-10-18）

秘银、艾德曼光源核心保留原注册 ID，改用立体灯笼模型；新增铜、银、金、铁、古代金属、振金六种材质。八档最大耐久为 1600、2400、3200、4800、6400、9600、16000、25600，所有材质的照明一致。只有未耗尽的副手灯笼生效，生存模式每 10 tick 消耗 2 点，末点可用，耗尽保留物品。创造免损耗，旁观不生效。

服务端仅消耗副手灯笼耐久，客户端接入原生光照计算；不再添加普通夜视效果，取下当帧恢复光照，已有夜视药水保持原状态。八份工作台配方统一使用对应材质 5 锭、1 玻璃、1 火把；中英文名称、提示、共享模型和资源生成器同步更新。

2026-10-18 在含其他并行开发改动的工作区中独立构建通过，8 项 JUnit、97 项 GameTest 全部通过，配方和标签解析错误均为 0；新增 5 项灯笼用例覆盖全部材质、背包和主手无效、只磨损副手、末点耗尽和普通夜视药水完整保留。最终日志为 `build/verification/lantern-final.log`，固定依赖 `build/verification/base/mite-0.1.10.jar`。

原生客户端灯笼专项验收通过：八材质均为 72 面立体模型、贴图无缺失，GUI 和第一／第三人称双手共 40 个 GPU 显示样本正常。真实玩家光图像素的 RGB 总和从暗处 42 提高到 756；全部材质亮度一致。主手／背包、耗尽和旁观负对照通过，装备切换不等待光图 tick 即可恢复；7 tick 短效药水的原对象、时长及原生渐退强度保持，灯笼亮度与原版完整夜视一致。报告为 `build/verification/lantern-client-audit.json`，预览图为 `build/verification/mob-render-audit/lantern-eight-materials.png`。

为避免同目录其他聊天的构建覆盖，本轮产物位于 `build/lantern-verification/libs/modern-ite-0.1.9.jar`。153 个生产类逐字节匹配编译输出，八份模型／配方与双语提示齐全；测试类、本体类及嵌套 JAR 均未打包，旧客户端核心 mixin 已移除。核对报告为 `build/verification/lantern-release-audit.json`。

提交前从 `21c2f07` 单独提取灯笼修改，在 `build/verification/lantern-commit-scope/snapshot` 验证候选提交：构建、8 项 JUnit、59 项 GameTest 全部通过，配方和标签解析错误为 0。该候选保留基线的版本及其他功能，日志为 `build/verification/lantern-commit-check.log`；上述 97 项及 153 类记录属于此前混合工作区验收。

## 0.1.7 生物修复与外观调整

本节记录生物战斗、生成、装备和模型调整，移除亡灵恶魂及两个旧版掠夺者注册和刷怪蛋，现有45种生物。原版掠夺者保留身份和袭击/巡逻AI，并可使用原拓展弩。

| 范围 | 修改与验证重点 |
|---|---|
| 僵尸外观 | 迷你恶魂头和触须、龙首、双段铁门盾牌、倒扣铁桶、两倍大头、大力士双臂铁傀儡式上挑；玩家桶盔和大头效果共用有效渲染入口 |
| 骷髅与苦力怕外观 | 凋零射手模型、疾跑前倾和加长腿、修长黑色相位及紫眼、混乱旋转箭头 |
| 粒子 | 不朽尸皇暗红灵魂环、闪电电弧、漂浮云雾、烈焰旋转火焰、湮灭紫黑逆传送与烟雾 |
| 伤害与控制 | 击退僵尸原力度×3、大力士和击飞骷髅上抛增强、暗黑铁傀儡攻击×2；真实箭命中链核对闪电6点真伤及漂浮；鼠标输入临时×12 |
| 视野与爆炸 | 万圣节用原版南瓜遮罩；闪光白屏并渐退；闪光/凋零对爆炸可见范围施效；隐身苦力怕死亡/爆炸前清除隐身，阻止永久药水云 |
| AI与生成 | 镜像/双武器骷髅幂等切换本体战斗目标，僵尸领主真实双向近战可用；攻城骑手绕过本体普通僵尸成人策略并保持幼年；漂浮骷髅仅地底自然生成 |
| 装备与龙息 | 随机僵尸四甲每件独立50%、主手70%，材质独立；龙息云最大半径固定1.5格；尸皇全甲保护V；大头移除钓竿 |
| 女巫与替身 | 女巫不互伤、不互相锁敌，召唤吸血蝙蝠和蜘蛛；替身完整接管真实死亡掉落，37堆栈带附魔/磨损重载验证，死亡不依赖玩家击杀或doMobLoot且只释放一次 |
| 旧存档与联机 | 已初始化的旧生物执行一次装备/AI迁移；大速度自定义同步绕过原版运动包±3.9截断，双端均需0.1.7 |

本轮新增实质回归包括实际箭伤害、背包NBT存读、真实爆炸、女巫随从上限、武器AI对象保留、攻城幼年存读、旧装备迁移和精确速度包编码。客户端使用隔离平坦世界验证45种纹理和14个重点GPU目标，另检查大力士实际攻击姿态、玩家大头添加/移除和新增粒子入口。截图在 `build/verification/mob-render-audit/`。

历史混合工作区于2026-10-18通过串行构建、8项JUnit及87项GameTest，配方和标签解析错误为0；当时还完成45种生物、2个弹体和7个效果图标的资源检查，以及14个重点GPU模型样本。此处仅保留历史来源，相关截图和测试数量不能代替本次候选提交树的隔离验证；本次发布包的生产类一致性与资源边界需重新核对。

本体源码未修改，构建继续固定使用同一份0.1.10依赖。Better Combat接入沿用本体SourceMeleeGoal桥接；本轮未安装Better Combat做完整联机实战。长期生存平衡、多人高延迟和大量特效同屏仍需游玩验证。

## 0.1.6 大棒模板升级

依赖包含装备升级模板的本体 0.1.10。铜／银／金→铁、铁→远古金属→秘银→艾德曼→振金共七条原生锻造配方，每条消耗模板、原棒、目标锭各一个；四种低阶直接制作配方保留，四种旧高阶工作台升级配方移除。复用本体转换逻辑继承品质、附魔、成长及拓展组件，并采用目标材质的属性与按比例换算的磨损。

2026-10-18 构建、8 项 JUnit 和 **68 项组合 GameTest** 全部通过。新增五项大棒用例验证真实配方加载、八种材质与锭的完整合法性矩阵、缺槽／错模板／越阶／降级／终阶拒绝、全组件及磨损继承、普通取出与 Shift 取出的真实锻造菜单消耗和旧预览防复制。

本轮只重新构建本体以取得已有新模板和扩展接口，没有编辑本体源码。组合验证固定使用 `build/verification/base/mite-0.1.10.jar`，SHA-256 为 `277c0fdfaec26c950b8ae7e601788530a8565894843d5fb4abd40e1a75342a97`，通过 `-PmiteJar=build/verification/base/mite-0.1.10.jar` 指定，避免其他会话继续出包时改变测试依赖。旧本体独立回归记录是历史结果，不能替代本轮本体的完整独立回归。

不安装 JEI 的标题页资源检查通过。JEI 19.57.0.451 实际客户端验证七条原生锻造配方、七个原生布局、产物 R 查询及默认锻造台左栏；四种高级棒旧配方在加载数据、原版合成分类和本体全部工作台分类中均为零。布局输出按原生 `recipe.assemble(...)` 的完整组件结果核对，包含继承品质。艾德曼棒→振金棒的实际页面截图为 `build/verification/forge-jei-audit/club-smithing-upgrade.png`。单一强化分类与振金熔炉回归也通过。

## 0.1.5 振金熔炉 JEI

振金熔炉原先只通过运行时策略提供热量五的能力，本体 JEI 枚举的是另一份固定熔炉索引，因此没有为它生成分类。现在仅在拓展内补充该索引，本体自动为它生成唯一的 `mite:furnace_modern_ite_vibranium_furnace` 分类，继续复用实际熔炼预览和原生配方布局，不复制配方计算。

熔炉界面的进度箭头优先打开振金熔炉分类，避免 JEI 的原版入口抢先打开已隐藏的普通冶炼分类。仅处理振金熔炉已同步的热量五界面与箭头区域；其他熔炉和区域保留原处理。JEI 显示保证获得的产物，5% 额外一件的概率通过物品说明展示。

按用户后续要求，本次不修改金属棒的合成、工作台规则或升级逻辑，等待本体的升级模板。

2026-10-18 构建及 8 项 JUnit、未安装 JEI 的 63 项组合 GameTest 全部通过。新增 3 项用例覆盖九种设备索引、每条振金页所有输入与燃料组合的实际预览、热量五配方、沙子温度分支、黏土 1～4 件批量及真实第 200 tick 产出。

JEI 19.57.0.451 实际客户端通过：振金炉只有一个对应分类，共 102 条配方，五个热量档的原生布局、炉用途 U 查询及产物 R 查询正常；箭头首个处理器确实打开该分类，左栏只有振金炉。普通炉、地狱岩炉和箭头区域外保留原处理，单一强化标签回归通过。截图为 `build/verification/forge-jei-audit/vibranium-furnace.png` 和 `vibranium-furnace-heat-5.png`。

## 0.1.4 单一强化标签

强化配方合并为唯一的 `modern_ite:forging` 分类，顶部标题统一为“强化锻造”。每条配方分别登记可用台的不可见催化剂，保持铁台等物品的 JEI 用途查询准确；不再把四种台注册为整个分类的全局催化剂。

独立的可选客户端 JEI 钩子根据当前排序后可见的配方生成原生左栏，同屏有多条配方时取可用台交集。只作用于强化分类，沿用本体铁砧的缓存/翻页处理方式，不改变其他分类。未安装 JEI 时跳过该钩子。

2026-10-18 最终构建通过 8 项 JUnit、未安装 JEI 的 60 项组合 GameTest 和标题页资源检查。安装 JEI 19.57.0.451 的真实客户端确认只有一个强化分类及一个顶部标签，1122 条强化配方和 50 个原生布局检查通过；各档装备左栏分别为 4、3、2、1 个台。铁、秘银、艾德曼、振金台的用途查询分别返回 132、468、762、1122 条有效配方。

同屏铁与振金配方仅列出共同可用的振金台；对铁剑的普通 R 查询、原生下一页切换及左栏更新均通过。截图保存在 `build/verification/forge-jei-audit/`，其中 `forging-tier-0.png` 展示单标签和四台左栏，`mixed-tier-0-and-3-single-tab.png` 展示混合页，`native-page-before-next.png` / `native-page-after-next.png` 展示实际翻页。本次未修改本体源码，生物绘制沿用 0.1.2 已通过的记录。

## 0.1.3 强化锻造 JEI

JEI 19.57.0.451 为客户端可选依赖，不嵌入发布 JAR；未安装时，客户端及独立服务器保持可运行。独立的四个最低台阶分类使用 JEI 公共催化剂接口，在原生左栏展示全部符合实际强化条件的锻造台。

| 装备材质 | 最高强化等级 | 左栏锻造台 |
|---|---|---|
| 铁 | 3 | 铁、秘银、艾德曼、振金 |
| 远古金属 | 3 | 秘银、艾德曼、振金 |
| 秘银 | 5 | 秘银、艾德曼、振金 |
| 艾德曼 | 7 | 艾德曼、振金 |
| 振金 | 10 | 振金 |

配方索引读取真实注册装备与服务端规则，包含每个有效强化等级的输入、输出、材料、斧和战锤、耐久消耗、耗时/失败概率区间及品质奖励。普通石和万能石分别展示，失败消耗规则在提示中说明。未注册全局物品子类型，也不隐藏或替换本体现有配方。仅强化界面的进度箭头提供入口，同类宝石镶嵌界面不启用入口，强化按钮保留原操作。

8 项 JUnit、未安装 JEI 的独立服务器 **60 项组合 GameTest** 全部通过；其中 6 项新增强化配方回归对照真实锻造操作。另保留了并行的护盾耐久修复和其 4 项测试。未安装 JEI 的标题页资源检查也通过。

真实安装 JEI 的客户端检查通过：共 **1122 张强化配方页**、**50 个原生配方布局**，完整核对全部装备/等级/强化石变体；箭头实际打开 JEI，宝石界面无强化入口；原生左侧工作站逐个检查并截图，分别为 **4、3、2、1** 个独立可见槽位。报告为 `build/verification/forge-jei-audit.json`，截图为 `build/verification/forge-jei-audit/forging-tier-0.png` 至 `forging-tier-3.png`。所有客户端测试只使用工程内新建的隔离测试世界，正常保存退出。

## 0.1.2 大力士僵尸贴图修复

来源 `RenderZombieFlyAway` 使用 `new ModelZombie()`，默认逻辑 UV 高度为 64；之前依据 PNG 的 64×32 物理尺寸误用了 32 高度，导致眼睛、身体和四肢采样错位。现在为大力士单独注册 64×64 逻辑 UV 模型，保留镜像肢体映射。

来源贴图的头部外层是不透明绿色，会遮住实际脸部，因此大力士专用模型保留空的 `hat` 节点，让原始脸部和眼睛正常显示；装备头盔仍由独立护甲层绘制。红色食尸鬼继续使用原来的 32 高度模型。本次只调整模型，未重绘或转换来源 PNG，也未修改本体工程。

0.1.2 构建和 8 项 JUnit、50 项组合 GameTest 全部通过。客户端实际绘制检查增加大力士这一第六个目标，六种生物均通过：大力士的旧 32 高度模型负对照与修复后有约 92.89% 的体表像素不同；修复后头部绘制有 156 个黑眼像素，保留不透明外帽的 64 高度模型负对照为 0，证明两个问题均已覆盖。头部、躯干和四肢逐顶点 UV 检查也通过。

大力士完整截图为 `build/verification/mob-render-audit/zombie_fly_away.png`，头部和两个错误模型的对照图位于同一目录。来源 PNG 的 SHA-256 仍为 `a43d30564026526e12ab075a8601767a1783470e1f017a0cc290aba035e920f6`，与旧 JAR 中对应文件一致。

## 0.1.1 生物渲染修复

- 流浪女巫、女巫领主、湮灭骷髅的来源发光图在全透明像素里保留白色 RGB。原版 `RenderType.eyes` 使用加法混合，不按这些像素的 alpha 遮罩绘制，导致全身发白；改为丢弃透明像素的自发光层，保持来源 PNG 原样。
- 亡灵恶魂保留来源的原版恶魂本体与自有发光层组合，修正透明混合，并按充能状态切换普通/攻击贴图。服务器同步预开火标记，沿用现有攻击冷却。
- 猪人领主显式选择僵尸猪灵材质，避免原生 `PiglinRenderer` 仅按原版实体类型查材质时抛出异常。对应实际游戏报告为 `crash-2026-10-18_14.06.36-client.txt`。
- 开发目录 `crash-2026-10-18_11.51.24-client.txt` 是 NeoForge early display 的 UnionFileSystem 初始化异常，发生在游戏初始化阶段；开发 `runClient` 任务现在只在本工程对应运行目录的 `config/fml.toml` 中设置 `earlyWindowControl = false`，新目录也会得到该修正，其他配置保留。

原有标题页检查只确认渲染器存在，没有执行实体绘制，无法发现这次问题。新增 `modernIteMobRenderAudit` 检查会在专用目录创建测试世界，实际调用全部 48 种实体的材质选择，并对这五种生物执行 GPU 绘制、读取像素及保存截图；结果写入 `build/verification/mob-render-audit.json`。它不操作启动器目录或用户存档。

最终绘制回归 **passed**，无失败：两种女巫发光层只影响约 1.07% / 0.87% 的体表像素，湮灭骷髅暗光下只影响约 1.43%，亡灵恶魂充能改变 6637 个像素，猪人领主材质选择和实际绘制均成功。女巫和湮灭骷髅各有一次旧加法绘制负对照，均能重现整身白罩，确认检查能捕获原错误。截图位于 `build/verification/mob-render-audit/`。测试世界在启动区块完成后再检查，最后正常保存退出。

此绘制验证使用原生 NeoForge 客户端、本体 0.1.9 与本拓展；未覆盖用户整合包的所有其他模组组合。

## 分批游戏内验收

在备份的测试存档中同时安装本体和拓展，先检查旧物品和品质/成长不变，再按以下顺序操作。

| 批次 | 操作 | 检查重点 |
|---|---|---|
| 日历 | 依次到第 128、144、208、224、256 天的日夜交界，再回拨时间并重登 | 128 仅蓝月；其余指定日为血月；装备进阶依据历史最高日期，不因回拨倒退；老怪不重抽装备 |
| 刷怪 | 双人重叠区块分别在地表和深层观察，血月与普通日对照；对照第100/101天 | 共享额度随日期增长、区块并集、局部32/48限制、自然24格距离、方块光照0、骑手及随从实际高度、碰撞与生成取消 |
| 开荒保护与昼长 | 新建世界推进至第二天夜间；跨维度、死亡、重登及回拨时间；第31/32天对照 | 37000日历tick永久结束保护，自动敌对入口均受限；早期白天双倍、夜晚正常，首次血月日恢复昼长，停时和睡眠语义保持 |
| 新生物 | 对来源表的 48 个身份逐一使用生成蛋/自然入口，制造目标后战斗并重登 | 模型、声音、换位/控制/召援/阶段、装备、掉落和保存；专属技能需要实际战斗观察 |
| 生成入口 | 第31/32/33天巡逻/前哨站、领主自然池/替换/骑手/召援、地底拆笼；玩家实际死亡 | 领主及同级强敌统一第33天，回拨不重新上锁；保留维度和事件条件，Boss编队与普通随从上限分别验证 |
| 生命 | 日期 95/96/127/128/169/170；135 级四甲六阶生命宝石；卸下一件后死亡重登 | 20/30/40 日期门槛、170 后 84 点、提升上限不回血、卸装截断、多排心形、营养最多 20 |
| 宝石 | 八孔同类型不同阶反复装拆、Shift 点击、关闭容器、破坏工作站 | 同类仅最高阶生效，装备和投影宝石不能同时取出复制，返回物品不丢失 |
| 成长 | 非护甲按 15 加锻造等级、护甲按采集级乘 3 加锻造等级；振金护甲锻造 10 时到 28，磨具点击不消耗；修理/升级/砂轮各一次 | 主装备宝石、历史磨具、独立锻造与成长都保留，牺牲装备不合并 |
| 强化 | 四档台和不同材质的斧/锤，强制观察成功及失败，过程中重登/关界面/拆台 | 来源材料和耐久消耗，失败不掉级，重新加载不重复消耗或产出 |
| 生活建筑 | 自然生成矿物与枫树、染色循环、光源核心、熔炉五级热量、私有箱、堡垒之眼和复活阵 | 实际获得路径、材质、交互、跨维度与阻塞落点、容器保存和唯一掉落 |
| 多人经济 | 钓鱼、成熟作物破坏/附魔采收、村民交易/补货/换职、破坏他人私人箱 | 凭证来源和概率、交易不重复追加、取消操作无奖励/广播、破坏记录准确 |

日期命令的原始 tick 起点为 `(显示日期 - 1) × 24000`，夜间可再加 18000。时间回退不会撤销历史最高日期，因此早期门槛测试使用新的测试世界或明确的测试恢复工具。

自动创建/NBT 检查并不等于所有 48 种技能均已完成真实战斗验收。多人表现、长时间平衡、控制效果舒适度和实际世界生成分布仍按此表记录游戏内结果。
