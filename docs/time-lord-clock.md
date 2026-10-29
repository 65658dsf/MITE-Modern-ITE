# 时间领主胸前挂钟

开发署名：NingMeng123。2026-10-29。

`TimeLordClockLayer` 替换时间领主的原生持物层：剑及其他物品继续走原生持握／本体武器动画，副手时钟改在躯干上绘制，并加金色挂绳。使用实际装备的时钟与原生物品渲染，保留材质包和时钟表盘动画。胸部挂点随原版或Fresh Animations的躯干姿态推进；穿胸甲时挂点外移，避免被护甲盖住。

只改变显示。服务端装备、品质成长、掉落、技能和召援均不修改；旧实体无需迁移或重新生成即可使用新显示。

本轮工作区0.2.18配套构建执行 `build compileGametestJava` 与38项JUnit通过。有／无EMF的两次原生图形客户端均通过：三个待机／行走／近战姿态及秘银甲样本都有可见的胸前时钟，移除时钟的负对照只改变紧凑的胸前区域，持剑继续正常显示。Fresh Animations 1.10.4配EMF 3.3.12、ETF 7.2.6的三个样本分别改变1050、1051、964像素，穿甲样本619像素；无EMF四样本分别1150、1150、1193、1192像素。原生LocalPlayer的1/5输入与动画回归一起通过。

报告及截图保存在 `build/verification/time-lord-clock/fa/` 和 `fallback/`，两次测试只创建新的隔离测试世界，均正常保存退出。只改客户端显示，因此未重跑服务端全量回归。

交付目录 `build/delivery/time-lord-chest-clock/`，包含 `modern-ite-0.2.18.jar`、本轮固定使用的 `mite-0.2.18.jar` 和双JAR压缩包；边界审计见 `build/verification/time-lord-clock/package-audit.json`。源码提交只包含挂钟及其审计文档，工作区已有版本与宝石显示修改单独保留。
