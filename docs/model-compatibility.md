# 特殊生物模型兼容

开发与验证：NingMeng123，2026-10-26；0.2.14 模型补丁需要配套 MITE: Modern 0.2.14。

大力士僵尸、恶魂僵尸和红色食尸鬼现保留原模型与 UV，通过隐藏标准僵尸借用 Fresh Animations 动作。大力士的 64 高逻辑 UV、空帽与双臂上勾拳继续保留；恶魂僵尸保持无普通僵尸头的身体，通过真实动画挂点绘制独立恶魂头。龙头僵尸的同类挂点一起处理。红色食尸鬼保持原 64×32 皮肤。

黑暗地狱苦力怕改用本体的 `SourceCreeperModel`：原六部件和 64×32 皮肤／红眼继续绘制，隐藏标准模型只提供动作，避免 FA+Creepers 的随机 64×64 岩石／泥巴模型造成脸部和眼睛错位。带电层也使用独立原图集及对应动画，原版苦力怕的随机外观继续生效。没有修改属性、AI、爆炸或存档。

扩展新增 focused 客户端审核，执行：

```powershell
.\gradlew.bat build compileGametestJava --no-build-cache
.\gradlew.bat runClient '-PmodernIteModelCompatibilityAudit=true' --no-build-cache
```

默认审核目录为 `build/verification/model-compat-run`，需自行放入并启用当前用户的 FA / FA+Creepers 及 EMF / ETF。可通过 `modernIteClientDirectory` 和 `modernIteModelCompatibilityAuditOutput` 指定独立目录与报告。审核只创建一次性测试世界，检查三个僵尸及本体／扩展两种地狱苦力怕的真实 GPU 绘制、六骨骼角点、原 UV、独立恶魂头、双臂上挑、苦力怕膨胀、红眼、随机 UUID 和带电层。

此次交付从当前仓库 HEAD 加模型相关文件的隔离副本构建，保留共享工作目录中的其他进行中修改。用户需要同时替换本体与扩展两个 JAR，并在原存档、光影和联机环境中复核实际观感。

本轮本体 1763 项、扩展隔离副本 38 项 JUnit 和构建通过；有／无 EMF 的两次实际 GPU 审核均通过。检查了五种实体的源模型、六骨骼映射、专属头与上勾拳、两个带电层与身体姿态一致、黑暗地狱苦力怕八个 UUID 和头部红眼负对照。2026-10-26 用户授权提交当前模型补丁，其他机制修改不在此提交范围。
