# 宝石箱立体模型与完整开箱动作

开发者：NingMeng123。日期：2026-10-29。模组版本保持0.2.16。

## 操作与表现

手持宝石箱时双手托住一个立体箱子，支持左右主手及副手。拿起、呼吸和行走会轻微带动箱子；两只手都持箱也只显示一个。游泳、攀爬、飞行、使用其他物品、挥动武器等冲突动作保留原生处理。

单击右键即可自动完成动作，不需要持续按住。正常20TPS下总计56tick（2.8秒）：

| 时间 | 动作 |
|---|---|
| 0～4tick | 双手托稳箱子 |
| 4～16tick | 箱盖绕后侧铰链平滑打开到100° |
| 16～36tick | 左手托箱，右手伸入中空箱体翻找 |
| 36～48tick | 服务器选定并同步本次宝石，右手把该宝石掏出 |
| 48～56tick | 展示本次宝石，随后扣除一个箱子并交付同一颗一级宝石 |

结果出现前切换或丢弃会取消，不扣箱、不发奖；结果已经出现后再中断，会扣除一个箱子并立即兑现已显示的结果，不能免费预览后重抽。容器移动或拆分前先结算，死亡、退出、普通跨维度及末地片尾的玩家重建也处理对应状态。创造模式保留不消耗箱子的原规则。动画标记只同步，不写入待领奖的存档状态。

## 模型与音效

箱体由底板、四壁、黑色内衬、金色边框和铰链组成，箱盖、锁扣与顶部蓝宝石作为活动部分。完整模型45个立方体、270面；箱体25个、箱盖20个，关闭时的GUI与掉落物也是真实立体模型。生成源为 `tools/gem_box_model.mjs`；两个资源生成入口都保留完整及拆分模型。

- 开盖：用户提供的MP3完整转为 `assets/modern_ite/sounds/item/gem_box_open.ogg`，3.030秒、44100Hz单声道Vorbis，固定音调，不裁剪尾音。
- 翻找：程序合成 `gem_box_rummage.ogg`，0.42秒轻柔摩擦及小碰撞，在16、26tick附近播放。
- 取出：程序合成 `gem_box_reveal.ogg`，0.72秒晶体提示声，36tick选定结果时播放。

三种声音均有中英文字幕。原音频来源、哈希与转换参数位于 `tools/sound_sources/gem_box_open.json`；两段合成音的生成器为 `tools/generate_gem_action_sounds.py`，参数与哈希保存在同目录对应JSON，输出可复现。

## 验证入口

- 构建与JUnit：`build/verification/gem-box-build.log`。
- 服务端：`GemRestrictionsGameTests`覆盖开箱完成前不扣不发、重复请求不重启音效、实际结果与奖品一致、翻找及取物声音、容器实际点击中断、丢弃/切槽取消、末地重建结算。日志为 `build/verification/gem-box-server-final.log`。
- 客户端：`runClient -PmodernIteGemBoxAudit=true`创建独立测试世界，真实烘焙模型、原生双手事件、完整玩家渲染器与GPU截图覆盖左右主手/主副手、呼吸、行走、拿起及8个开箱关键帧。用两种不同宝石验证展示的是对应实物，并检查恢复无残留。报告为 `build/verification/gem-box-client/audit.json`，图片位于其 `mob-render-audit/` 子目录。
- 客户端使用Minecraft原生Vorbis解码器确认用户音效完整3.03秒、单声道且无削波。实际特定光影包的最终观感不由离屏截图替代。

## 本轮结果与交付

构建成功，38项JUnit及30项 `modern_ite_feedback` 服务端GameTest全部通过。服务端最终日志为 `build/verification/gem-box-server-verified.log`。回归中硬化了旧爆炸夹具：先等待目标位置达到原生实体tick状态，再加入范围测试实体；保留原有可见性、伤害、击退和半径断言，未修改任何爆炸生产逻辑。

实际GPU检查通过，包含左右主手/主副手、两手各持箱不重复、8个动作关键帧、两种本次实物、恢复姿态、完整原生第一人称渲染链。额外安装Punchy2.8g、Iris1.8.14-beta.1、Sodium0.8.13、EMF3.3.12、ETF7.2.6、Cloth Config15.0.140后的检查也通过；Punchy在托箱时让出双手，取下后恢复且配置未改动。兼容报告为 `build/verification/gem-box-client-compat/audit.json`，日志为 `gem-box-client-compat.log`。这组检查不声称已目测所有光影包。

交付目录为 `build/releases/gem-box-3d-2026-10-29/`，其中 `modern-ite-0.2.16.jar` 保持原版本号，本轮只需更新附属（联机时两端同步）。`verification.json`记录SHA-256和验证范围。预览视频 `gem-box-opening-preview.mp4` 由56张真实客户端渲染帧按20fps合成，音轨按游戏时点混合实际三段音效；GIF为同一渲染序列的无声版本。

## 第三人称兼容与双手可见性修复

第三人称托箱姿势会被 Not Enough Animations 1.12.6 / playerAnimator 2.0.4 在 `PlayerModel.setupAnim` 后续阶段覆盖，原来的 `HumanoidModel.setupAnim` 尾部钩子太早。现在在完整玩家动画调用返回后设置双臂，并同步左右袖子；右臂的翻找位移使用绝对值，重复渲染不累加。第三人称仍保留单箱、开盖、左手托箱、右手翻找和展示本次宝石的动作。

第一人称按用户指定，只将双手向两侧展开并抬高。箱子保持原来的位置、距离和大小（基础Y=-0.32、Z=-1.18、缩放0.72），两手静止掌位从 `(±0.305,-0.285,0.18)` 调整为 `(±0.43,-0.18,0.22)`，右手翻找时从新的托箱位置平滑伸入箱内。

实际客户端复现报告 `build/verification/gem-box-thirdperson-before/audit.json` 在原代码下失败。修复后 `gem-box-thirdperson-fixed/audit.json` 及最终的 `gem-box-visible-hands/audit.json` 均通过，运行目录为 `gem-box-player-animations-run`。该环境包含原有 Punchy / Iris / Sodium / EMF / ETF / Cloth Config，以及 NEA、playerAnimator、SWAY、EMF兼容核和NEA兼容扩展，共11个附加模组，使用原生玩家模型。

新增第三人称检查在真正绘制皮肤顶点时验证双臂及袖子的几何方向与贴合，覆盖左右主手/主副手的闭箱、16、24、35、40、52tick共24帧；切回木棍后几何和图像恢复。第一人称检查仍经过完整 `ItemInHandRenderer.renderHandsWithItems`，在闭箱及取出阶段分别重画“仅隐藏左手”和“仅隐藏右手”的对照场景，以最终图像差分确认手臂真正可见。800×600、70°视场的闭箱实帧中，右手可见10903像素、左手15677像素，箱子遮挡或屏幕外的顶点不计为可见。截图和差分蒙版位于最终报告的 `mob-render-audit/` 子目录。

本次附属最终构建日志为 `build/verification/gem-box-hands-final-build.log`，38项JUnit通过。与主模组环刃削弱一起合并交付于主工程 `build/releases/gem-box-hands-chakram-2026-10-29/`，请使用其中两个0.2.16 JAR；`gem-box-two-hands.png` 和 `gem-box-third-person.png` 为本次真实客户端渲染截图，`gem-box-render-audit.json` 与 `verification.json` 记录检查结果及交付哈希。

## 仅站立或慢走时显示，以及实际 FA 动画环境

用户实际游戏实例启用 FA Player、Polaris 和 Punchies 等资源包，并同时安装 Touch n Motion；与先前使用原生玩家模型的开发测试环境不同。FA 在 EMF 的绘制期更新手臂，EMF Compat 和 Touch n Motion 的 `onAnimationEnd` 也会更新姿态，因此仅在 `setupAnim` 之后设置双臂还不够。

新增可选 `GemBoxEmfFinalPoseMixin`，在 `EMFModelPartRoot.animate` 的尾部恢复本次托箱的双臂、双袖及其子部件的绝对位置、旋转和缩放。只操作这四棵部件树，身体和腿继续执行原生动作。快照以弱引用关联当前根模型和玩家，核验 EMF 原生实体上下文，并在不托箱或第一人称渲染前清除，避免模型复用造成姿态残留。未安装 EMF 时仍使用原生玩家模型路径。

箱子仅在地面站立、普通步行或潜行慢走时显示双手托举。奔跑、水平移动超过0.24格/tick、离地、骑乘、游泳、滑翔或攀爬时隐藏手中箱子。第一和第三人称均拦截对应箱子的原生单手回退；两个手槽都拿箱子时也都隐藏。其他物品保留其正常显示，停下后恢复托箱。箱子大小、第一人称位置和距离未改；物品栏图标、掉落物以及开箱奖励结算不受移动显示规则影响。

验证使用独立运行目录 `build/verification/gem-box-real-movement-run`，从用户实际目录复制13个相关渲染/动画模组、21个启用的文件资源包及对应配置，保留资源包顺序。用户原目录未修改。AR在开发命名环境的两个文本匿名类捕获字段需要局部映射：只在测试副本改三个字段名，590个JAR条目中其余588个完全一致，包括全部渲染/EMF代码；报告为 `dev-mapped/field-remap-report.json`，正常游戏不需要替换AR。

第三人称检查现通过完整 `EntityRenderDispatcher.render`，由 ETF/EMF 自行建立真实实体上下文；不手动伪造渲染状态。第一人称移动隐藏检查通过完整 `GameRenderer.renderItemInHand`，包含 AR 原生提交；瞬时装备夹具通过 Punchy 公开同步请求和原生tick完成装备更新。检查使用实际副手物品及空手对照，不能以空白画面或零顶点冒充通过。

实际环境报告 `build/verification/gem-box-real-movement/audit.json` 通过24个第三人称开箱关键帧、原生第一人称链以及32组移动矩阵（左右主手/主副手×站立、慢走、普通走路、潜行、奔跑、两种独立超速来源、离地），另检查双箱奔跑、停止恢复及普通物品姿态恢复。最终日志为 `gem-box-real-movement-final.log`。本次交付目录为 `build/releases/gem-box-walk-only-2026-10-29/`，只更新附属，版本保持0.2.16。

不安装任何附加渲染/动画模组的原生客户端也通过相同的32组移动矩阵与24个开箱关键帧，报告为 `build/verification/gem-box-movement-vanilla/audit.json`，验证新增EMF兼容保持可选。构建与38项JUnit通过，日志为 `build/verification/gem-box-movement-build.log`。交付JAR不包含审计类、临时诊断日志或第三方测试映射JAR。
