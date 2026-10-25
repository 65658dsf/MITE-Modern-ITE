# 振金星空盔甲光影兼容层

开发者：NingMeng123  
日期：2026-10-25

后续回归：[完整模组安装组合中的 AR 绘制顺序与 Iris 开关残留包装修复](vibranium-cosmic-acceleration-2026-10-25.md)。本页记录第一轮光影兼容方案；这里的最小模组测试范围不能替代后续完整安装组合的验证。

## 问题与目标

用户提供的 BSL 截图出现贴在盔甲表面的明显十字星纹，Photon 截图则没有可见星空。目标是最后一个 GIF 中的视线投影、多层流动星点与原盔甲共存的效果。

实际渲染代码属于 `MITE Modern ITE` 扩展，而非同级 `miteto1.21.1neoforge` 主模组。此前 `VibraniumCosmicIrisCompat` 在光影启用时放弃 `vibranium_cosmic.vsh/fsh`，改为 CPU 生成 UV 动态纹理，并使用 `GameRenderer.getRendertypeEntityTranslucentEmissiveShader()` 提交。这既丢失了原程序的视线投影和八层星场，也把最终显示交给各光影包不同的材质管线。

## 两个光影包的证据

证据来自用户给出的 ZIP，未修改光影包。以下路径均为 ZIP 内路径。

| 光影 | 关键代码 | 对旧实现的影响 |
| --- | --- | --- |
| BSL v10.1.1 | `shaders/program/gbuffers_spidereyes.glsl` 取 `texture * color`，对 RGB 做 `pow(..., 2.2) * EMISSIVE_INTENSITY`，主要输出 `DRAWBUFFERS:0` | 发光纹理可以进入颜色目标，但仍只是 UV 图案，无法恢复原星空程序的投影、分层和连续动画；光影还会重新解释其颜色和亮度。 |
| Photon v1.3b / Minecraft 1.21.1 | `shaders/world0/gbuffers_spidereyes.fsh` 在未定义 `USE_SEPARATE_ENTITY_DRAWS` 时包含 `gbuffers_all_solid.fsh`；`settings.glsl` 只有在 `MC_VERSION >= 260100` 时定义该宏 | 当前 1.21.1 实际走实体 G-buffer 编码，不能把这个程序当作普通 RGBA 发光叠加。 |
| Photon v1.3b 的该分支 | `gbuffers_all_solid.fsh:21-28` 输出 colortex1，材质选项可追加 colortex2；302 行丢弃 alpha 小于 0.1 的片元；389-396 行将颜色、材质、法线和光照打包；`shaders.properties:311` 设置 `blend.gbuffers_spidereyes = off` | 旧兼容层要求的半透明颜色叠加与目标/混合语义不成立。像素函数和提交次数正常，不能证明最终合成图中有星点。用户截图证实该组合没有显示预期星空。 |

Photon 还包含使用 colortex13 的透明分支，但其 `USE_SEPARATE_ENTITY_DRAWS` 条件在这份包的 Minecraft 1.21.1 配置下不成立，不能将该分支误记为此次实际执行路径。单独把 alpha 提高或改用另一种原版 RenderType，也无法普遍解决不同包的 G-buffer、混合和色调映射差异。

本地文件 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `BSL_v10.1.1.zip` | `131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668` |
| `photon_v1.3b.zip` | `120897768EAA8CB1F14A3E98F88DFF009AE3204EE3B96904A4FE8586EDFC1EAD` |
| `iris-neoforge-1.8.14-beta.1+mc1.21.1.jar` | `60D5F8BF52F25E9986440F4A4270A6BD986D9CFF994510E1108AC1547063B314` |

## Iris 的边界与执行时机

核对了 [Iris 官方 1.21.1 分支](https://github.com/IrisShaders/Iris/tree/1.21.1) 的以下实现，并对本地安装 JAR 使用 `javap -c` 交叉核对 `finalizeLevelRendering()` 与 `shouldOverrideShaders()`。这项检查确认管线行为，不等于画面测试通过。

- [MixinGameRenderer](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/main/java/net/irisshaders/iris/mixin/MixinGameRenderer.java) 将原版 `getRendertypeEntityTranslucentEmissiveShader()` 映射到 `ENTITIES_EYES_TRANS`；[ShaderKey](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/main/java/net/irisshaders/iris/pipeline/programs/ShaderKey.java) 再映射到 `ProgramId.SpiderEyes`。
- [MixinShaderInstance](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/main/java/net/irisshaders/iris/mixin/MixinShaderInstance.java) 会在接管世界管线时按配置跳过未知 shader。仅恢复自定义 shader、仍在原实体阶段绘制，不能保证可见。
- [MixinLevelRenderer](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/main/java/net/irisshaders/iris/mixin/MixinLevelRenderer.java) 在 `LevelRenderer.renderLevel` 返回前调用 `pipeline.finalizeLevelRendering()`。
- [IrisRenderingPipeline](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java) 的 `finalizeLevelRendering()` 先将 `isRenderingWorld` 置为 false，再执行 composite 和 final；`shouldOverrideShaders()` 返回 `isRenderingWorld && isMainBound`。
- [FinalPassRenderer](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/main/java/net/irisshaders/iris/pipeline/FinalPassRenderer.java) 共享 Minecraft 主渲染目标的深度附件，结束时重新绑定主目标。常规路径下后置表面仍可使用真实世界深度进行遮挡判断。

因此选择 NeoForge `RenderLevelStageEvent.Stage.AFTER_LEVEL`：此事件位于 `LevelRenderer.renderLevel` 调用完成之后，Iris 的 composite/final 已完成，且第一人称手部渲染尚未清除世界深度。不能简单移到 `GameRenderer.renderLevel` 方法末尾，否则可能拿到已被手部覆盖或清除的深度。

## 本次实现

`ShaderPackSurfaceRenderer` 提供与光影包名称无关的透明表面回放：

1. 世界渲染开始时清理上帧残留，并开启本帧收集。
2. 在原盔甲层产生星空时，立即把已经摆好姿势的模型写成 `MeshData`；记录模型视图矩阵、投影、雾参数、颜色以及该次星空时间、相机角度。队列不保留可变模型或实体引用。
3. `AFTER_LEVEL` 重新绑定主渲染目标，恢复捕获状态，使用同一个 `vibranium_cosmic.vsh/fsh` 绘制表面。沿用盔甲纹理 alpha 遮罩、颜色写入、LEQUAL 深度测试及原表面偏移。
4. 恢复后续手部、GUI 和其他事件需要的 GL/RenderSystem 状态；帧结束、资源重载和中断路径释放待绘制网格。队列有数量上限。

`VibraniumCosmicIrisCompat` 仅通过可选 Iris 公共 API 判断光影启用和阴影阶段，不依赖 BSL/Photon 私有程序，不修改光影包，也不要求玩家启用未知 shader。阴影阶段跳过星空。无光影和物品栏预览继续使用原 shader 的即时路径。光影世界中的原盔甲、饰纹、附魔光泽继续由正常材质管线绘制。

旧 UV 动态星图和仅供其使用的 `VibraniumStarlightPixels` 已弃用。原 GLSL 中的视线投影、八层星场、20 秒周期和真实盔甲开孔遮罩继续作为效果来源，避免维护两套明显不同的星空算法。

## 兼容范围与限制

这是针对 Minecraft 1.21.1 / NeoForge / Iris 管线的通用兼容层，目标是不依赖某个光影包的颜色附件与实体 shader。未测试的光影包不能据此宣称全部通过；其他光影加载器也需单独核对时机。

- 常规不透明方块与实体遮挡依靠共享世界深度；应实测墙后不穿透、人物前后相互遮挡和盔甲开孔。
- TAA 抖动、TAAU 内部分辨率缩放或特殊投影可能造成星点与经过重建的边缘轻微不一致。兼容层不能自动复现任意光影包内部的重投影算法。
- 世界曲率、鱼眼等在光影顶点程序中进行的位置变形，不在原模型矩阵里；变形较强时回放表面可能偏离盔甲。
- 透明水、玻璃等可能不写深度，且水折射是在光影内部合成的；后置星空不会自动参与这些介质的折射、吸收或排序。
- 星空在主要光影后处理之后叠加，不会自动参与包内景深、运动模糊、泛光、曝光和体积雾。优先保留可见且接近原程序的星空，不能保证其与每个包的全部后处理完全一致。
- Iris 后续颜色空间转换仍可能作用于最终画面；“后置”不表示绕过所有后续显示处理。

## 验证记录

本轮使用 NeoForge 21.1.259、Iris 1.8.14-beta.1、Sodium 0.8.13、NVIDIA GeForce RTX 5060 Laptop GPU / OpenGL 3.2.0 NVIDIA 610.74，1600×900 的独立平坦世界。光影保持包内默认设置；未修改玩家实例或存档。实际图片、自动报告和单独的人工图像复核记录共同作为证据，不将提交次数或整屏差值等同于星点可见性。

| 检查 | 状态 / 证据 |
| --- | --- |
| 编译、打包及相关测试 | `gradlew.bat build compileGametestJava --console=plain` 成功；38 项 JUnit 无失败、错误或跳过。删除的 3 项仅测试已移除的 UV 动态纹理像素算法。未重跑服务端玩法 GameTest。 |
| 关闭光影：原星空、开孔、动画 | `build/verification/cosmic-universal-20261025/off/v1/`，12 案例通过。保留 Iris/Sodium 但关闭光影，兼容补绘次数为 0；原 GLSL 星点、开孔、固定 160/200 tick 差异正常。 |
| BSL v10.1.1：真实世界最终 framebuffer 中的星空和动画 | `bsl/v2/`，12 案例通过。原程序星点恢复，盔甲底纹与裸露皮肤保留，背面正常。`bsl/v1` 因测试代码未处理创造背包重定向而中断，v2 修正审核并完整重跑，生产实现未因此修改。 |
| Photon v1.3b：真实世界最终 framebuffer 中的星空和动画 | `photon/v1/`，12 案例通过。恢复可见青白星点；原材质仍由 Photon 着色。实际补绘着色器为 `net.minecraft.client.renderer.ShaderInstance`，无需 emissive/G-buffer 路径。 |
| 同装备、固定时刻启用/禁用星空对照 | 三组均捕获关层、160 tick、200 tick 图，隔离附魔流光；直接检查甲面星点有无及位置变化。无附魔、保护Ⅳ、缺胸甲、铁套Ⅴ四组负例均无星点。 |
| 不透明遮挡及帧结束队列清空 | 三组低墙遮挡对照均未见下半身星点穿墙；全部 36 案例在渲染后及截图读回后的 GL 错误均为 0，帧末 pending 均为 0。阴影跳过由公开 API 分支控制，未单独捕获阴影 framebuffer。 |
| 物品栏/GUI 状态恢复 | 三组实际生存背包均可看到原程序星光，槽位和文字正常；关闭界面后的负例正常。 |
| 资源重载、运行中切换光影、未装 Iris | 本轮未执行；资源释放和可选依赖路径完成静态检查，不能替代实际场景验证。 |

每个最终目录有 12 张原始 PNG、`report.json` 和 `visual-review.json`。自动报告有意保留 `captured_real_world_frames_pending_visual_review`，人工复核结论单独记录；总仪器结果在根目录 `world-audit-summary.json`。审核的临时 layer 代理用于固定时钟/关层，会影响原版流光延后检测，不能用它声称验证了所有普通/成长附魔的流光顺序。当前静态帧验证覆盖星场相位变化，不等于完整 20 秒动画流畅度或长时间多人性能测试。测试实例仅含两项目与 Iris/Sodium，未覆盖玩家所有可选渲染模组组合。

本地修复包：`build/releases/modern-ite-0.2.10-cosmic-compat.jar`，1,483,238 字节，SHA-256 `b24537d9482983f1715f4e9f5676dea216373c8e8e343274aebdb4c21c762f42`。272 个生产类与编译输出逐字节一致；原星空三个 shader 资源与源码一致；包内不含审核类或旧星图路径。包一致性记录为同目录 `modern-ite-0.2.10-cosmic-compat-audit.json`。使用时替换旧 `modern-ite` 扩展 JAR，避免同一 mod 的两个版本同时存在。

旧世界审计曾将兼容层提交次数和动态图注册数量作为主要断言。这些断言只能证明代码执行，不能证明 Photon 的最终画面存在星空。此次验收应保留真实 GPU 截图、最终 framebuffer 对照和队列/错误状态，并明确未执行的项目。
