# 胸甲镶嵌宝石显示

开发署名：NingMeng123。日期：2026-10-29。

穿上镶嵌宝石的胸甲后，胸前直接显示对应宝石的实际物品图案。类型、颜色、切面与阶级外观沿用宝石物品模型和纹理，替换或取出宝石时同步更新。

宝石大小缩至初版的60%，在胸口中央组成紧凑的小徽饰。布局随实际镶嵌数量调整：一颗居中，两颗竖向，三颗三角，四颗菱形，更多时组成小型环状或中心加外圈。八颗形成胸口中央的小八边菱形环，保留胸甲本身的纹理和留白。

显示顺序仍沿用镶嵌台八孔的先后顺序，空孔不占布局位置；重复镶嵌的宝石分别显示。绘制读取当前胸甲的 `gem_sockets_v1` 数据，不改变宝石、装备或属性数值。

宝石贴在胸甲正面，并随身体转动、下蹲和幼体缩放移动；普通和纤细玩家、穿甲人形生物及盔甲架共用显示层。该层在原胸甲及振金星光之后绘制。隐身时仍可见的胸甲继续显示宝石。

生产代码位于 `client/gear/ChestGemLayer.java` 与 `ChestGemRendering.java`。装备组件沿用既有保存和网络同步，客户端渲染类只在客户端加载。

## 实机验证

`ChestGemClientAudit` 通过完整原生玩家和盔甲架渲染器检查实际GPU图像及宝石顶点。使用隔离新世界运行：

```powershell
.\gradlew.bat -I tools/chest-gems-audit.init.gradle runClient `
  -PmodernIteClientDirectory=build/verification/chest-gems-20261029/client
```

默认报告为该目录的 `client-audit.json`，截图位于 `mob-render-audit/`。结果与交付包记录见 [验收记录](verification.md)。
