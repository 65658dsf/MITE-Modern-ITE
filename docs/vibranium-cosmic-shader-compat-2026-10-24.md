# 振金星空盔甲与 Iris 光影兼容

开发者：NingMeng123。日期：2026-10-24。

全套振金保护Ⅴ的条件保持原规则，普通附魔与成长附魔均可满足。未穿齐、混合材质、保护Ⅳ以及其他装备不激活星光。此补丁只修改客户端绘制，不修改附魔、装备或伤害数值。

## 原因与实现

用户提供的 Complementary Reimagined r5.2.2 压缩包 SHA-256 为 `ddc6a083b94d302977bce7be56091b79f17905dabfe2f5b0903e2a2b95ee137c`。本地 Iris NeoForge 1.8.14-beta.1 的 SHA-256 为 `60d5f8bf52f25e9986440f4a4270a6bd986d9cff994510e1108ac1547063b314`。

Iris 的 `MixinShaderInstance.iris$shouldSkipThis()` 在默认 `allowUnknownShaders=false` 且世界光影接管着色器时，拒绝非 `ExtendedShader`／`FallbackShader` 的普通自定义着色器；其后通过 `DepthColorStorage.disableDepthColor()` 关闭绘制输出。原星空层的独立 GLSL 正好属于这一类。另一个问题是 Iris 的 `FullyBufferedMultiBufferSource.UnflushableWrapper.endBatch()` 为空操作，原来依靠立即刷新来锁定单实体 uniform 的假设也不成立。

光影关闭时继续使用原有视线投影 GLSL。通过 Iris 公开 API 检测光影启用后，星光改为客户端生成的透明动态纹理，使用原版 `entityTranslucentEmissive` 着色器入口提交到调用方的真实缓冲。Iris 会将其映射到 `ENTITIES_EYES_TRANS`／`SpiderEyes`，用户这份光影的 `program/gbuffers_spidereyes.glsl` 支持该程序并保留纹理透明度。兼容路径用贴图表面的星点动画，保留原材质，不模拟原 GLSL 的视线投影。

兼容渲染类型保留原生emissive的透明混合、颜色写入和深度测试，并加上与原生盔甲、原星空层相同的 `VIEW_OFFSET_Z_LAYERING`。直接使用不带这一状态的原生emissive类型仍会失败：原生盔甲的视图缩放会把甲面拉向相机，在4格处约为0.000977格，大于星空模型额外0.01模型像素对应的0.000625格，星点因此被底甲深度挡住。不能只凭模型略微膨胀来判断它处于底甲之前。

星图按实际振金盔甲贴图的透明度遮罩生成，不覆盖原有空洞。星点保持半透明，裁掉低于该程序0.1阈值的像素；统一使用世界时钟，每2 tick更新一次，完整动画周期20秒，两张纹理在资源重载时释放并重新建立。阴影 pass 不添加星光几何。原版附魔流光沿 Iris 的原生批处理绘制，不放入模组自建的延迟缓冲。Iris 类通过反射按公开 API 名称绑定，未安装 Iris 时不产生硬依赖。

不修改光影压缩包，也不修改用户的 Iris 配置或启用未知着色器的全局选项。

## 验证与边界

纯像素测试覆盖透明空洞、半透明遮罩、Iris裁剪阈值和星点透明度上限。专用 `VibraniumShaderWorldAudit` 在独立目录新建世界，以真实第三人称世界渲染和原生帧缓冲截图检查光影开启／关闭，记录 Iris 实际状态与星光路径。该流程不使用物品栏肖像或离屏盔甲预览代替世界光影验证。

运行开关为 JVM 属性 `modern_ite.cosmicShaderWorldAudit=true`，期望状态为 `modern_ite.cosmicExpectedShaderPack=true` 或 `false`，输出目录由 `modern_ite.cosmicShaderWorldAuditOutput` 指定；客户端运行目录必须位于工程 `build/verification/` 下。两次运行应使用相同 Iris、Sodium 和光影压缩包，仅改变隔离配置的 `enableShaders`。截图仍需逐张检查；仅有着色器链接成功、缓冲提交次数或整幅画面的像素变化，均不足以单独证明星空视觉正确。

真实GPU对照使用 NVIDIA GeForce RTX 5060 Laptop GPU、OpenGL 3.2.0 NVIDIA 610.74，在1600×900的实际世界视角分别拍摄六种装备状态。关闭光影的六图位于 `build/verification/cosmic-iris-world/off/capture/`：两张全Ⅴ图可清楚看到白／青星点随时间改变；无附魔、全Ⅳ、缺胸甲和铁套Ⅴ均无星点，脸、手臂及缺胸后的皮肤未被覆盖，原GLSL路径通过此场景的人工检查。

开启光影的第二轮六图位于 `build/verification/cosmic-iris-world/on/capture-v2/`。尽管Iris确实启用、生成了两张动态纹理并提交360／316次阳性绘制，图上仍无可辨星点，这一轮明确判为视觉失败，不能以渲染计数替代验收。

补上相同视图偏置后的第三轮六图位于 `build/verification/cosmic-iris-world/on/capture-v3/`，已逐张人工检查通过：全Ⅴ与稍后同装备帧的头盔、肩甲、胸甲、腿和靴上均出现清晰的白／青细星点，两个时刻的位置与亮度有所变化；原蓝灰底纹和紫色附魔流光仍可见，脸及裸露手臂未被覆盖。无附魔、全Ⅳ、缺胸和铁套Ⅴ四类负例均无星点。Iris实际启用，`allowUnknownShaders=false`；实际绘制着色器为 `net.irisshaders.iris.pipeline.programs.ExtendedShader`，两个阳性分别执行456／406次星光绘制，负例均为0。

第三轮同时回读了实际GPU纹理：首次外／内层有1888／765个可见透明度像素，稍后为1914／723，四份GPU纹理与对应生成图逐像素完全一致。原始自动报告仍标记“截图完成、待人工检查”，另外的 `build/verification/cosmic-iris-world/visual-review.json` 记录这次逐图复核、图像校验和及其限制。3项新增纯像素测试与原测试合计41项JUnit无失败；生产JAR与GameTest源码编译通过。本次改动限客户端，没有用这些绘制检查替代服务端玩法回归。

两种路径的可见效果有所区别：光影开启时是附着在甲面UV上的较小、较均匀细星点，按世界时钟漂移和闪烁；光影关闭时的原GLSL采用视线投影，星点大小和分布更不均匀，投影会随视角变化。兼容层解决星光消失，未声称还原完全相同的视差效果。此轮覆盖指定Iris、Sodium和Complementary版本、独立平坦世界的正面日间第三人称场景，没有验证所有光影包、长期多人场景或运行中反复切换光影。

标题页不代表世界管线，状态断言安排在世界正常渲染后。隔离启动配置使用ASCII光影副本名，避免Java Properties将直接写入的中文UTF-8文件名误读；用户原包保持不变。
