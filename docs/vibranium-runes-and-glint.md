# 振金符文与原版附魔流光（0.2.0）

开发署名：NingMeng123。验收日期：2026-10-19。本轮源码按盔甲渲染、武器符文、验证和文档分类提交。

原生振金剑停帧的原因是 Accelerated Rendering 的缓存网格绘制绕过了 Sodium 常规四边形路径中的可见纹理标记。补丁在实际物品渲染入口，仅为 12 种原生振金工具、振金棍棒和 15 种 Simply Swords 振金武器标记其真实模型纹理。使用 Sodium 的公开 API，延迟绑定且不构成硬依赖；没有手动推进帧或修改玩家的优化配置。

15 种简易武器新增独立符文动画：长剑、双刃剑、细剑、武士刀、钗、矛、长柄刀、战刃、弯刀、巨剑、巨斧、巨锤、环刃、镰刀、戟。沿用现有振金材质与模型，像素轮廓、握柄和非金属区域保持；金属上的小符文来自原生振金剑字形，每种 8 帧，3 tick/帧，共 1.2 秒。生成器为 `tools/generate_simplyswords_vibranium_runes.py`，`--check` 已通过。资源覆盖位于 `assets/mite/textures/item/simplyswords_vibranium_*.png` 及对应 `.mcmeta`，仅在安装 Simply Swords 并注册相应武器时使用。

全套振金保护Ⅴ的甲面按“原材质、透明星光、一层原版紫色附魔流光”的顺序绘制。普通附魔和成长保护Ⅴ都支持；显式关闭流光、保护Ⅳ和其他材质保持原行为。光翼仍已移除。实际盔甲模型被复用，未知渲染器保留原版路径，暂存缓冲有上限并在帧结束清理。

## 实际验证

- 同一套 Sodium 0.8.13 + Accelerated Rendering 1.0.14 下，修复前 28 件武器在 512 个原生 tick 内均只有 1 帧、0 次像素变化；修复后全部恢复原有 4/8 帧，每件至少完成 2 轮有序循环。两轮中缓存网格均实际复用，28 份 PNG 及源帧哈希完全一致。
- 另外移除测试实例中的 Sodium 与 Accelerated Rendering，28 件武器全部再次通过，验证可选依赖缺省路径。
- 玩家和不朽尸王的普通Ⅴ、成长Ⅴ图像都与“星光之后仅绘制一次原版流光”的独立参考逐像素相同，与双重流光负对照明显不同；显式关闭、保护Ⅳ和铁套对照不变。
- 构建、14 项 JUnit、GameTest 源码编译通过。本轮客户端修改未重跑服务端 GameTest。
- 包内 165 个生产类、528 份 JSON、3 份 shader、30 份简易武器动画资源均与当前输出/源码逐字节匹配，无测试类、Sodium、Simply Swords 实现或嵌套 JAR 混入。

AR 发布版的文本 mixin 使用开发环境中不存在的匿名类字段。为运行开发测试，仅在隔离副本停用了无关文本加速；404 个 class（包括全部 50 个物品渲染 class）保持原字节。手持测试在无存档的标题页中，以真实第一人称物品渲染路径与原版时钟采样 GPU 图集；测试恢复 AR 手持管线状态，没有手动推进动画。没有改动用户实例中的模组、配置或存档。

## 文件与证据

- 2026-10-19 符文与流光验收时的历史构建：`build/libs/modern-ite-0.2.0.jar`，1,158,761 字节。下列 SHA-256 用于标识该次已验收构建。
- SHA-256：`7ceb6213d69e8867adec7f998040c75d4d1e42aca7eae31291d7d7eda12c2e16`。
- 前后对照：`build/verification/rune-compat-before.json`、`rune-compat-after.json`、`rune-compat-comparison.json`。
- 无钠对照与构建：`build/verification/rune-native-control.json`、`rune-native-build.log`。
- 盔甲 GPU 检查：`build/verification/mob-render-audit.json`、`rune-glint-render.log`。
- 包一致性：`build/verification/release-audit.json`、`rune-glint-release.log`。
- 流光开关对比：`build/verification/mob-render-audit/cosmic-protection-v-player-ordinary-glint-comparison.png`（左关闭，右开启）。星光 GIF 为隔离星空动画的检查，主动关闭了独立的原版流光。

前后完整运行目录未作整目录字节快照。“注册补丁”这一代码差异依据任务编辑记录及 Gradle 编译日志；PNG 与帧数据的一致性由独立脚本直接重读并验证。真实整合包战斗场景仍可继续人工观察。
