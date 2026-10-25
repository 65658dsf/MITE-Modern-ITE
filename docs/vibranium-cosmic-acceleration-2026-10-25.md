# 振金星空与 Accelerated Rendering 的绘制顺序

开发者：NingMeng123  
日期：2026-10-25

## 复现与此前验证的缺口

用户安装的是上一轮星空修复 JAR，SHA-256 为 `b24537d9482983f1715f4e9f5676dea216373c8e8e343274aebdb4c21c762f42`，与交付文件一致。关闭光影后，实际模组组合中的世界人物和背包人物仍然没有星空。用户日志显示客户端在关闭光影状态冷启动，因此不能只归因于运行中切换光影的残留状态。

上一轮 [光影通用兼容层](vibranium-cosmic-universal-2026-10-25.md) 的真实 GPU 验证包含主模组、扩展、Iris 和 Sodium；没有覆盖用户其他渲染模组。原验证范围不足以证明安装组合正常，这一结论必须保留。

本轮在独立开发客户端复制了完整用户模组清单。`build/verification/cosmic-fullmods-20261025/inputs.json` 记录 43 个复制 JAR；`mite-0.2.10.jar` 和 `modern-ite-0.2.10.jar` 被排除，由两项目的开发源码加载。第一组复现没有启用额外用户资源包，因此不能把该次失败直接归因于 Fresh Animations 或 3D Armor 材质包。

复现证据：

- `build/verification/cosmic-fullmods-20261025/off/before/report.json`：12 例截图完成；shader ready 为 true，保护 V 阳性门控成立，捕获前后 OpenGL 错误均为 0，帧末 pending 均为 0。
- 同目录 `visual-review.json`：直接复核普通保护 V、固定 160 时刻、背面和背包四张阳性截图，均只有原盔甲、没有可见星光；状态明确为 `visual_failure_reproduced`。
- 自动仪器状态只说明截图和采样完成，不表示画面通过。该组恰好说明 ready、门控、GL 零错误和队列清空都不能替代甲面像素复核。

## 开发运行环境的字段映射适配

本地安装 AR 版本是 `acceleratedrendering-1.0.14-1.21.1-alpha.jar`。它的两个文本匿名类 Mixin 使用发行版捕获字段名，而 NeoForge userdev 使用带参数映射的字段名。为了让同一模组进入隔离开发环境，仅对复制 JAR 的三处 UTF-8 常量进行了字段名映射：

| 类 | 发行字段 | userdev 字段 |
| --- | --- | --- |
| `features/text/mixins/FormattedText$2Mixin.class` | `val$text` | `val$p_130776_` |
| `features/text/mixins/FormattedText$3Mixin.class` | `val$text` | `val$p_130763_` |
| `features/text/mixins/FormattedText$3Mixin.class` | `val$style` | `val$p_130764_` |

完整记录位于 `build/verification/cosmic-fullmods-20261025/dev-adaptations.json`。原 JAR SHA-256 是 `f567f7a88c241e5e4266ee1804bd89e31f4fd6faf31745ed14f4a0a0a0e199cc`，开发副本为 `050d8a52788add3d90ada6f3c49af8d3f412d0934d9dce88f6bf0c8642ff3208`。适配没有更改实体加速、模型缓存、渲染顺序或 shader 逻辑，也没有修改用户安装目录。开发验证副本不应作为 AR 更新包交付给用户。

## AR 下的绘制顺序问题

[Accelerated Rendering](https://github.com/Argon4W/AcceleratedRendering) 缓存模型网格并批量提交 GPU 变换和绘制。对本地 1.0.14 JAR 的相关类与字节码核对表明：

- `core.mixins.buffers.BufferSourceMixin` 会给原版 `MultiBufferSource.BufferSource` 接入加速缓冲，Iris 的 `FullyBufferedMultiBufferSource` 也有专门兼容 Mixin。因此类名仍是原版 `BufferSource`，或 `instanceof BufferSource` 成立，都不等于其网格按原版即时顺序提交。
- 身体与原盔甲可以先进入 AR 的批处理，而自定义程序化星空另走即时 shader。即使只让星空网格避开加速，底甲仍可能在之后绘制，将先画出的星空覆盖。世界与 GUI 人物预览都经过这个顺序。
- 只更改星空 `VertexConsumer`、换一个原版 buffer，或仅依赖 `endBatch()` 的原始语义，不能保证先画底甲再画星空。作用范围必须包含同一穿戴者的底甲和叠加层。

## 只对星空穿戴者使用原生管线

新增 `VibraniumCosmicAccelerationCompat` 和 `VibraniumCosmicAccelerationMixin`，在完整的 `LivingEntityRenderer.render(LivingEntity, ...)` 调用范围内调整 AR 管线。

进入条件是 AR 存在、实体加速已启用、当前管线为加速、并且实体满足原 `ArmorAuraRules.fullProtectionFive`。满足时调用 AR 提供的 `useVanillaPipeline()`，让这一次身体、底甲、附加层与 GUI 人物遵守同一顺序；返回或抛出异常时通过 `finally` 调用 `resetPipeline()`。只有确实入栈的调用才出栈。外层已使用原生管线时不会重复入栈。

这些方法来自 AR 自身的 [AcceleratedEntityRenderingFeature](https://github.com/Argon4W/AcceleratedRendering/blob/main/src/main/java/com/github/argon4w/acceleratedrendering/features/entities/AcceleratedEntityRenderingFeature.java)。本地 JAR 的 `javap -c` 也确认：`useVanillaPipeline()` 向控制栈 push `VANILLA`，`resetPipeline()` pop，`getPipelineSetting()` 在栈为空时读取配置默认值。兼容代码通过反射访问这些公开方法，不新增 AR 必选依赖。

这个作用域不改全局配置、不卸载 AR、不改装备激活条件。未满足星空条件的其他实体继续使用原选择；星空穿戴者该次渲染的附属内容属于同一作用域。未安装 AR 时直接保留原路径；不提供这些方法的其他 AR 版本会记录一次兼容警告，不能据此承诺该版本已修复。

本补丁不修改原附魔光泽逻辑。无光影下的星空即时绘制、原生 glint 延后处理，以及光影启用时的 `AFTER_LEVEL` 星空回放条件保持原设计。下文额外使用真实未代理渲染层验证普通保护 V 的星空和 glint 共存；测试层的固定时钟代理不能替代全部 glint 场景，成长附魔 V 的完整 glint 顺序不纳入本轮新增四例的结论。

## 生命周期审计暴露的第二条边界

可选 `modern_ite.cosmicLifecycleAudit=true` 在原 12 例之后追加四例：启用 BSL、关闭、再次启用、再次关闭并资源重载。资源重载等待 Future 完成并重新安装测试层，每例之后再等待至少 40 个正常世界帧和 40 tick 才采样。

首个 scoped-AR 候选的 `off/after/report.json` 已给出有用的调用方区别：冷启动关闭光影时是 `FullyBufferedMultiBufferSource`，首次启用 BSL 后变成 `net.irisshaders.iris.layer.BufferSourceWrapper`，再次关闭甚至重载资源后仍可能保留这个 wrapper。它实现 `MultiBufferSource`，但不是 `BufferSource` 子类；Iris 的本地 JAR 提供公开 `getOriginal()` 返回包装源。

本地 `entity_render_context.MixinEntityRenderDispatcher.iris$beginEntityRender` 在实体 ID 设置存在且正在绘制世界时创建这个 wrapper，并不以当前启用光影包为条件，解释了同会话关闭后的包装仍然出现。`BufferSourceWrapper` 没有独立目标或储存，只将类型映射后转交原缓冲；正常实体链的父级是 `FullyBufferedMultiBufferSource`。

`off/after/visual-review.json` 记录这个候选只修复了冷启动世界与背包；两次关闭光影的生命周期截图仍缺星光。原即时路径的 `outputBuffers()` 对未知 wrapper 返回 null，可以解释该条独立失败。它与 AR 延后底甲覆盖是两个不同边界，不能把首个 scoped-AR 候选宣称为全部完成。

最终候选在 `VibraniumCosmicIrisCompat` 中增加 `BufferWrapperAccess`，只识别 Iris 的这个已知包装类型，并反射调用其公开 `getOriginal()`。本地 JAR 的字节码确认它直接返回委托的 `MultiBufferSource`。`outputBuffers()` 先解包再按原规则选择可冲刷的 `BufferSource`；因此世界关闭光影、GUI 星空和延后 glint 都经过相同入口。最多解包八层，遇到自引用、无有效源或不可用反射即停止，未知包装继续保留原有回退行为。未安装 Iris 时该访问器为空，不新增 Iris 必选依赖。

这没有改变光影开启时世界星空的 `AFTER_LEVEL` 回放条件，也没有把任何未知第三方缓冲当成全局主缓冲。静态检查确认 AR 包围方法使用 `finally` 恢复且无 AR 时可跳过；本轮图像审计没有通过注入渲染异常验证恢复，也没有单独运行卸载 AR 的新客户端。原生 glint 的捕获与一次 EQUAL 深度绘制保持不变；固定时钟的 `ControlledGildingLayer` 代理会改变真实层识别，因此不能用这组星空截图宣称所有 glint 顺序都重新验证通过。

## 保证同一次提交完成即时绘制

继续核对本地 Iris JAR 发现，`FullyBufferedMultiBufferSource.endBatch(RenderType)` 的字节码仅为 `return`，即使关闭光影也不会冲刷指定类型。只有无参 `endBatch()` 会收集、排序和绘制各类型的 mesh，然后清理已提交状态。前一候选虽然通过单人物截图，但可能依赖下一层开头或延后 glint 的全量冲刷才真正画出星空；不能据此保证多个实体各自的时钟和镜头 uniform 对应自己的提交。

最终候选保留提交前的无参 `endBatch()`，确保先画原盔甲，同时把星空网格提交后的按类型冲刷改为无参 `endBatch()`。前置冲刷已经清空原缓冲，后置冲刷通常只剩本次星空。这样当前提交在进入下个实体、修改 uniform 之前完成，不依赖后续层偶然带出。该修改不影响 `AFTER_LEVEL` 使用的独立网格回放。

## 验证记录

最终受控层矩阵使用完整的 43 个复制模组和用户选择的 21 个文件资源包，资源包选项共 24 项，记录于 `build/verification/cosmic-full-instance-20261025/inputs.json`。运行使用 AMD Radeon(TM) 610M，独立新建单人世界，没有打开用户存档。该组每张 1600×900 原始截图均由审核代理通过 `view_image` 直接检查；图像结论与 GL、队列计数分开记录。

最终构建总计 20 例：16 例受控层矩阵，以及 4 例真实未代理层的世界/GUI 补充检查。汇总证据为 `build/verification/cosmic-full-instance-20261025/final-candidate-summary.json`，各组保留独立的 `report.json` 和 `visual-review.json`。原始仪器报告状态仍表示采样完成待目视审核，最终目视结论以对应的审核文件和汇总为准。

| 范围 | 当前证据与结果 |
| --- | --- |
| 完整复制模组、无额外资源包、关闭光影的原问题 | `cosmic-fullmods-20261025/off/before/` 已复现；12 例截图，四张阳性图直接复核失败。 |
| 第一个 scoped-AR 候选 | `cosmic-fullmods-20261025/off/after/` 完成 16 例仪器采样；六张图直接复核显示冷启动世界/GUI、两次 BSL 开启恢复，两个关闭生命周期失败。不能计为最终通过。 |
| wrapper 修正后的前一候选，完整模组但无额外资源包 | `cosmic-fullmods-20261025/off/after-v2/` 的 16 例仪器采样通过；存盘 `visual-review.json` 记录冷启动世界、GUI、两次 BSL 启用和两次关闭共六张阳性图直接复核通过。此组尚无最终的提交后无参冲刷修正，单独保留为候选证据。 |
| 最终候选，同时包含用户真实资源包的 16 例 | `cosmic-full-instance-20261025/off/after-v3-clean/report.json` 和 `visual-review.json`：退出码 0，16 例仪器采样及 16 张图逐一复核均通过。包含阳性、未附魔/IV/缺胸甲/铁甲负例、禁层对照、160/200 动画、背面、低墙遮挡、GUI，以及同会话 BSL 开→关→开→关加资源重载。全部采样前后 GL 错误为 0、帧末 pending 为 0。此前中止启动仅记在 `aborted-attempts.json`，不计为有效验证。 |
| AR 作用域退出后的控制栈状态 | 最终全部 20 例均记录实体加速功能仍启用，帧末当前管线和默认管线都是 `ACCELERATED`，`pipeline_restored_to_default=true`。这证明正常帧退出后没有把其他实体永久留在原生管线，异常注入恢复仍属于静态检查范围。 |
| 真实未代理渲染层，关闭光影 | `cosmic-full-instance-20261025/off/native-off/`：退出码 0，世界和 GUI 两例仪器及直接目视复核均通过。真实 `VibraniumGildingLayer` 数为 1、代理层为 0，自然时钟，无显式 glint override；世界和背包星场可见，普通附魔光泽保留。GL 错误和 pending 均为 0。 |
| 真实未代理渲染层，Photon | `cosmic-full-instance-20261025/off/native-photon/`：退出码 0，实际启用 `photon_v1.3b.zip`，世界和 GUI 两例仪器及直接目视复核均通过。同样为真实层 1、代理层 0、自然时钟且无 glint override；世界星场清楚，GUI 胸肩可见紫色附魔光泽与星场共存。GL 错误和 pending 均为 0。静态截图证明二者共存，不单独证明 glint 的连续时间运动。 |
| 前一候选的编译与单元回归 | 加入 AR scope 与 Iris wrapper 的候选 `build` 已通过；38 个 JUnit 测试，0 失败、0 错误。即时冲刷修正后的重新构建另见最终构建记录。 |
| 前一候选产物检查 | `build/libs/modern-ite-0.2.10.jar` 当时为 1,489,433 字节、276 个 class；SHA-256 为 `53a9312cd37ee5c22fc4630e21e3c9ad73a00261049af165df61167c8d471083`。包含新 AR scope 与 Iris wrapper 处理，不含审计类、旧像素生成类或打包的 AR/Iris 依赖。三份原 cosmic shader 资源与 v1 完全一致。此哈希只记录前一候选，不能作为最终交付哈希。 |
| 最终构建与交付 | 提交前后均使用无参冲刷的最终构建已通过 `build` 和 `compileGametestJava`；交付文件已复制到 `build/releases/modern-ite-0.2.10-cosmic-compat-v2.jar`，为 1,489,436 字节、276 个 class，SHA-256 为 `64d4ad201ce58b3e7a05a28878180d90d2db4763393464f43e9a8aa5936488a3`。与构建 JAR 哈希一致。 |

最终仍保留光影兼容层的 TAA/TAAU、世界曲率、透明折射、景深等未覆盖边界。这里的修改解决管线选择和顺序，不代表重建任意光影包的后处理，也不代表任意第三方模型和所有 AR 版本都已通过。
