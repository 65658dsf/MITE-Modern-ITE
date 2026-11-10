# 3D 宝石资源包与检视动画

作者：NingMeng123

适用 Minecraft 1.21.1 / NeoForge。资源包是可选外观扩展，覆盖 Modern ITE 的 60 种宝石与 5 种宝石碎片，共 65 个物品。关闭资源包会恢复模组原本的宝石外观。

## 外观与使用

- 使用 NeoForge 自带的 `neoforge:obj` 加载器，模型包含真实的台面、冠部、腰棱和底部尖锥。六个等级分别使用三至八边切型，每个模型有 30–80 个面。
- 切面保留像素色块与彩色反光。白光扫过、尖角闪烁后，静止 1.5–5 秒再播放下一轮；各宝石使用错开的循环时间。
- 检视动画随主模组提供，需要安装 Punchy。手持普通宝石或宝石碎片时按默认键 `I`，可在游戏按键设置中调整；即使关闭 3D 资源包，全部 65 个物品仍可使用检视。
- 检视按参考视频改为双手配合：先托起宝石，另一只手接过，举到眼前缓慢转动观察反光，最后收回。默认动画速度下约 5.2 秒，用户调整 Punchy 动画速度后时长会相应改变。左主手和副手持有宝石时，动作按实际持握侧镜像。
- 换手仅发生在第一人称画面中，保留原宝石的数量、自定义名称及其他组件，不会移动或替换背包里的物品。Minecraft 原本的方块手臂负责托举和交接，没有新增手指模型。

将 `build/resourcepacks/MITE-Modern-ITE-3D-Gems-1.21.1.zip` 放进实例的 `resourcepacks`，在游戏资源包界面启用并置于其他宝石外观包之上。检视还需要包含本次动画资源的 Modern ITE 主模组 JAR。

更新双手检视时，替换游戏实例 `mods` 目录中的 Modern ITE 主模组 JAR 即可；3D 资源包本身无需重新制作。检视资源始终由主模组提供，关闭外观包仍可使用。

## 重新生成

在仓库根目录执行：

```powershell
node tools/generate_gem_inspection.mjs
node tools/generate_gem_3d_pack.mjs
node tools/generate_gem_inspection.mjs --check
node tools/generate_gem_3d_pack.mjs --check
```

前者生成主模组中的 Punchy 检视资源；后者生成 `resourcepacks/modern-ite-gems-3d` 与可安装 ZIP。`--check` 检查生成结果是否与源码一致。模型几何位于 `tools/gem_3d_mesh.mjs`，检视动作位于 `tools/gem_3d_inspection.mjs`。

检视兼容使用 Punchy 3.1b 的原生双臂动画和物品显示覆盖。生成器提供基础片段及 `_right`、`_left` 变体；`GemInspectionSource.java` 按播放编号保存原宝石快照，`GemInspectionPunchyCompatibilityMixin.java` 将动画中的 `modern_ite:inspection_source` 占位符解析为副本。停止动画、更换片段和退出服务器时清理快照；不依赖额外模型加载器，也不写入玩家背包。

## 验证范围

原生客户端验证已在启用和关闭资源包两种状态下通过：65 个模型与贴图成功加载，GPU 图集实际出现动画帧变化；Punchy 为每种物品的两只手共解析出 130 个专用检视片段，均未回退到通用动画。还使用原生物品渲染器导出了 GUI 展示，最终预览包含 120 帧转台图。

新版双手检视在 `build/verification` 下创建的独立临时世界中完成真实按键链路检查：由原生 `I` 键映射触发 Punchy，自然播放 5.2 秒动画，捕获完整第一人称手臂和物品画面。最终 3D 版本及关闭资源包后的普通宝石均通过右主手、左主手、右惯用手的副手三种情况；换手前后保留数量 7 及自定义名称，客户端和服务端背包均未改变。逐帧画面已复核双手会合、接过宝石、举起观察和收回的全过程。

用于展示的流畅预览另以原生 `PoseHandler.playAtTime` 按 30 fps 采样 157 个手部姿态，再通过完整第一人称渲染器绘制。此过程明确手动指定了手部动画时刻；真实按键和自然播放的功能证据由此前的三种持握场景单独提供。贴图闪光仍沿用游戏原生时钟。

独立文件审计确认六个模型封闭、朝向正确、UV 位于各自切面格内；全部 65 张动画贴图为 64×1792、包含 28 个不同帧，透明区域在帧间保持一致，冷却为 30–100 游戏刻；ZIP 的 205 个文件与生成目录逐字节相同。

报告与预览位于：

- `build/verification/gem-3d-pack/final/audit.json`：启用 3D 包，结果 `passed`。
- `build/verification/gem-3d-pack/no-pack/audit.json`：关闭 3D 包，结果 `passed`。
- `build/verification/gem-3d-pack/independent-audit.json`：独立几何、贴图、动画时序与压缩包检查。
- `build/verification/gem-3d-pack/gems-3d-turntable.gif`：120 个原生渲染角度组成的 30 fps 转台预览。
- `build/verification/gem-inspection/native-v5/audit.json`：最终 3D 宝石检视的真实按键、三种持握方式、物品显示交接和背包不变检查；同目录包含自然播放截图及 `smooth-preview` 下的 157 帧姿态预览。
- `build/verification/gem-inspection/default-v5/audit.json`：关闭资源包后的普通宝石完成同样三种持握检查，并输出 157 帧姿态预览。
- `build/verification/gem-inspection/gem-inspection-comparison.gif`：普通宝石与 3D 宝石的双手检视对照预览。
- `build/verification/gem-inspection/parser-result.txt`：真实 Punchy 解析器对三段检视资源及 65 个绑定的检查。
- `final/`、`no-pack/` 同目录中的 PNG：原生 GUI 与转台渲染结果。

3D 模型、贴图和初版绑定检查在标题界面运行；新版检视实拍使用单独创建的临时测试世界，没有操作用户存档。尚未验证其他物品同时占用检视按键的所有双手组合。自然播放截图的录制速度受测试渲染开销影响，重放它们时应使用报告中的实际时间戳；`smooth-preview` 则按明确的 30 fps 姿态时刻生成，可以按该帧率播放。
