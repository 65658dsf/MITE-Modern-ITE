# 僵尸盗贼持门与格挡

维护者：NingMeng123。

`modern_ite:zombie_door` 使用完整的两段原版铁门模型。门以实际持门手的手腕为锚点，跟随行走和攻击动作；平时垂在身体一侧，举门时移到胸前，另一只手收回支撑。左右惯用手均适配，动作在客户端平滑过渡。

主手持铁门时，按[共享攻击意图](defense-intent.md)识别持械逼近、近战戒备、远程瞄准和实际来箭，包括当前仇恨目标之外的玩家；非玩家目标保留原有 8 格可见目标规则。防御周期为 5 tick 起手、30 tick 有效格挡、30 tick 放门休息。起手和举门期间减速并暂停近战，休息期间可正常进攻。

有效格挡保护身体前方 120°，可以挡住普通近战和非穿透箭。背后攻击、环境伤害、绕过盾牌的伤害及穿透箭仍可造成伤害。正面斧击当次被门挡住，同时打断举门并造成 60 tick 破防，伴随声音反馈。

丢失铁门或死亡立即放下门；目标撤离、松弓或失去视线后完成当前有限防御窗口，不隔墙追踪，也不会持续刷新窗口。防御进度和减速属于临时战斗状态，读档后清除，不改变原有召援机制。

自动化验证入口：`DoorGuardGameTests` 检查真实伤害链与防御周期；`MobRenderRegressionAudit` 检查实际客户端模型和 GPU 绘制。当前轮次的运行结果见工作目录 `build/verification/door-guard/`。

2026-10-19 验证通过：Java 21 / NeoForge 21.1.259，使用工作区本体 `mite-0.2.0.jar`；构建、14 项 JUnit、138 项服务端 GameTest（包含 9 项门盾测试）全部通过。实际客户端 GPU 检查包含左右手共 14 种姿态、空手不绘门、共享模型姿态复位以及同步首帧控制；测试仅使用工程内新建的隔离世界并正常退出。

近战 AI 在实际伤害之前会先触发挥手，举门期间同时拦截这一步，避免门随无效攻击重试抖动；正常休息或破防后恢复挥手。零伤害斧接触不会触发破防。客户端首次追踪已经举门的僵尸时，立即显示完整格挡姿态，正常举门仍保留平滑起手。

构建产物：`build/door-guard-build/libs/modern-ite-0.2.0.jar`。其中 166 个生产类逐字节匹配编译输出，未包含 GameTest 或客户端审计类。SHA-256：`359f061915fe7155ea3432180e666da92cad0666919d582245d6b791cd6ab4da`。

最终日志：`build/verification/door-guard-final-build.log`、`door-guard-final-gametest.log`、`door-guard-final-client.log`；GPU 和包报告分别为 `build/verification/door-guard/render-audit.json`、`package-audit.json`，姿态拼图为同目录下 `mob-render-audit/zombie_door-poses.png`。

本工作区原默认依赖路径指向已不存在的本体 0.1.10，验证时显式指定现有依赖：

```powershell
./gradlew.bat '-PmiteJar=../miteto1.21.1neoforge/build/libs/mite-0.2.0.jar' build runGameTestServer
```
