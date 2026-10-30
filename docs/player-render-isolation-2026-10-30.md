# 宝石盒模型隔离与队友大头同步

作者：NingMeng123。版本保持现有0.2.18。

宝石盒第一人称手臂复用了共享玩家模型，绘制后未恢复模型属性；EMF/ETF还会在实体分发器入口重置同皮肤玩家共享的部件。只在PlayerRenderer内保存已经晚于该重置。

现在第一人称手臂、PlayerRenderer直接入口及EntityRenderDispatcher外层都以作用域保存和恢复共享状态，覆盖玩家与原生内外盔甲部件的位置、旋转、缩放、可见性、模型标志和持箱支点。非持箱玩家通常继续原生路径；若嵌套进入正在借用的模型，也保存并恢复外层状态。EMF持箱快照按进入前状态恢复，正常结束和异常都不会把当前玩家姿势遗留给下一个玩家。

大头效果加入现有可见效果观察者同步表。已患大头的玩家进入视野时立即同步；效果新增、修改、移除及自然结束均使用原生效果包，同步不为每刻的正常倒计时重复发包。本机视角缩放规则不变。

2026-10-30验证：隔离真实客户端启用EMF/ETF、Fresh Animations与现有渲染依赖，通过两个真实玩家共用同一个皮肤渲染器和完整铁盔甲的轮换绘制；第一人称、第三人称、嵌套非持箱玩家、受控异常后的完整模型恢复均通过。客户端还经原生效果处理器验证远端玩家头与帽子2倍/恢复1倍，以及本机FOV不受远端效果影响。报告位于`build/verification/player-render-isolation/audit.json`；未使用玩家实际游戏目录。

服务端`BucketHeadSyncGameTests`覆盖真实ChunkMap跟踪与效果生命周期。测试的受控渲染异常清理第三方上下文仅属于夹具，生产代码只恢复自身借用的模型状态。

## 实机追加：ISMAH盔甲手与Traben 3D Armor

用户实际安装的JAR哈希与此前发布包一致；追加的关键路径是ISMAH 6.1.1在`PlayerRenderer.renderHand`里直接修改共享外层盔甲子树的可见性。旧第一人称作用域只保存玩家模型，未恢复这些盔甲子节点；后续原生盔甲层只重开顶层部件，靴甲的子节点依旧隐藏。EMF Compat的绘制前姿态回放也会覆盖盔甲双臂，皮肤最终托箱姿态不会自动传播到独立盔甲根。

第一人称现在保存玩家及实际内外盔甲模型，保留各EMF根的进入前快照；ISMAH准备单臂后记录其姿态和完整可见性掩码，在EMF最终动画后恢复。第三人称在实际选定的盔甲模型完成动画设置后对齐皮肤双臂，并在盔甲自身的EMF最终回调恢复顶层姿态。Traben子模型的缩放和细节动画继续保留。

隔离目录使用用户实际ISMAH、NEA、EMF/ETF、EMF Compat、Touch'n Motion、Punchy及资源包设置。全套铁甲与秘银甲共48个第一/第三人称场景，通过实际顶点发射时的双臂、双腿矩阵，以及第一人称几何轴、覆盖、偏移和单臂可见性检查；每种材质另检查普通队友的本帧皮肤/盔甲关节、可见腿部和未持箱状态。严格的共享模型快照恢复、嵌套及异常检查也通过。

参考画面保留8像素、每通道差16的亚像素舍入预算，模型矩阵绝对容差为1e-6，结构和可见性精确比较；原始像素/PNG哈希及差异统计均记录。队友首次渲染前已穿好整套装备，避免把Touch'n Motion真实换甲动作误当作残留。

负对照保留相同的新测试和真实模组组合，只在独立构建副本换回旧发布包的三个宝石盒类族并移除本轮两条兼容钩子。旧版第三个场景（首次第一人称后再第三人称）实测外层腿部顶点数为0，重现靴甲子部件消失；新版本同场景及全部48帧通过。报告分别位于`build/verification/gem-box-armored-hand-actual/audit.json`和`build/verification/gem-box-armor-old-release-control/audit.json`。

### 重跑客户端检查

在隔离客户端目录中准备上述实际模组和已启用的Traben资源包，指定本体JAR后执行：

```powershell
.\gradlew.bat -I tools/verification/gem-box-armor-alignment-client.gradle runClient '-PmiteJar=本体JAR的绝对路径' '-PmodernIteClientDirectory=隔离客户端目录'
.\gradlew.bat -I tools/verification/player-render-isolation-client.gradle runClient '-PmiteJar=本体JAR的绝对路径' '-PmodernIteClientDirectory=隔离客户端目录'
```

检查器只创建一次性测试世界并自行退出；通过与否以对应JSON的`status`为准。渲染失败会被记录后安全退出，所以仅检查Gradle退出码不足以判断画面通过。客户端模组及资源包不随源码仓库分发。
