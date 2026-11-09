# 宝石物品扫光

开发署名：NingMeng123。

全部 65 种宝石物品（十类属性各六阶、朴素宝石二至六阶）使用原版动画贴图。在背包、快捷栏和手持物品中，细白光从右下扫向左上，经过上方尖角时短促闪亮。八角宝石使用左上角作为闪点。

原有 16×16 像素造型、配色与透明轮廓保留。白光只改变已有像素的 RGB；所有帧的 alpha 相同，避免 `item/generated` 合并动画帧轮廓时产生额外侧面。胸口六芒星和宝石箱不使用此动画。

每次扫光约 1.35 秒，采用 27 个逐 tick 的渐进亮度帧。每次结束后完全恢复原图，冷却 30～100 tick（正常客户端 tick 速度下为 1.5～5 秒）。各物品 ID 使用不同的固定种子生成十二轮冷却时序，轮内时长变化、不同宝石错开播放；十二轮结束后重复，同种同阶物品共用贴图时钟。它不是每件物品各自维护的随机计时器。

`.png.mcmeta` 显式指定 16×16 帧和 1 tick/帧，关闭插值，以免长冷却帧提前向亮帧渐变。Minecraft 原版贴图动画按客户端 tick 更新，每秒 20 次；不会把预览 GIF 的 50 FPS 作为游戏内帧率。

生成入口：

```powershell
node tools/generate_gem_assets.mjs
node tools/generate_gem_assets.mjs --check
.\gradlew.bat build
```

`tools/gem_shine.mjs` 定义扫光、尖角闪光和冷却时序，`tools/gem_textures/classic_b.json` 保留原始静态像素源。PNG 第零帧始终是该原图，其余可见动画帧只存储一次，冷却和多轮播放通过元数据引用复用，不复制整段贴图。

Sodium 的“仅播放可见纹理”与 Accelerated Rendering 缓存物品网格一起使用时，缓存路径会绕过普通四边形的纹理可见标记。`VibraniumSpriteActivity` 将原有振金兼容扩展到 60 种属性宝石和明确的五种朴素宝石：在原生物品绘制入口标记当前模型使用的纹理，包括冷却中的静止帧，使原版时钟继续运行。该入口覆盖背包和手持；Sodium 通过可选公开 API 延迟绑定，不需要关闭加速或改变玩家配置。

2026-11-09 验证：生成器 `--check`、Gradle 构建和 40 项 JUnit 通过；独立解码确认全部 65 对贴图和元数据的原图、透明轮廓、方向、27 个活动帧及循环接缝冷却有效。130 个资源与构建 JAR 逐字节一致。隔离客户端原生资源重载通过，65 种宝石的模型、物品渲染器模型缓存和图集均非缺失。报告位于 `build/verification/gem-shine/`；此客户端检查覆盖资源加载，不代表所有第三方整合包组合均已实机验收。

运行实际 GPU 动画回归时，使用独立开发客户端目录，装载 Sodium 0.8.13 和开发映射后的 Accelerated Rendering 1.0.14，开启 `animate_only_visible_textures`、物品加速、手持加速及 GUI 加速。开发映射仅改两个文本 Mixin 的捕获字段名，50 个物品渲染类与玩家安装版逐字节一致。GUI 与手持必须分开启动，避免一个上下文为另一个上下文标记可见纹理而掩盖停帧。

```powershell
.\gradlew.bat -I tools/verification/gem-shine-client.gradle runClient `
  -PmodernIteClientDirectory=build/verification/gem-shine-compat/client `
  -PgemShinePhase=visible-after-hand
.\gradlew.bat -I tools/verification/gem-shine-client.gradle runClient `
  -PmodernIteClientDirectory=build/verification/gem-shine-compat/client `
  -PgemShinePhase=visible-after-gui -PgemShineGui=true
```

回归复用 `VibraniumRuneClientAudit`，在标题页真实调用物品渲染器并采样 GPU 图集，确认源贴图、原版时序、加速缓冲和网格缓存复用，不手动推进动画，也不进入玩家存档。报告位于 `build/verification/gem-shine-compat/<phase>/audit.json`。

2026-11-09 兼容回归：`visible-before-hand` 在开启可见纹理优化时复现全部 65 颗宝石连续 350 tick 均只有 1 帧、0 次像素变化。补丁后 `visible-after-hand` 和 `visible-after-gui-hidden` 分别通过：每颗实际 GPU 都出现全部 28 个源帧，至少完成两轮可见闪光，真实加速缓冲和缓存网格持续复用。GUI 报告同时读取并确认 Sodium 运行时可见纹理优化为开启。早先的 `before-hand`/`before-gui` 使用关闭该优化的配置，是“全部纹理更新”对照，不作为冻结复现证据；中途进入世界而中止的 GUI 记录亦不作为通过证据。

修复构建通过 40 项 JUnit；130 个动画资源和三个相关生产 class 与 JAR 内容逐字节一致，测试 class 未打包。部署时备份原包，再将修复 JAR 安装到用户指定实例并复核 SHA-256；记录为 `build/verification/gem-shine-compat/install.json`。测试启动脚本默认隐藏自己的无世界窗口，避免影响用户操作。
