# 提灯重复手臂修复（2026-10-28）

开发署名：NingMeng123。

Punchy 2.8g 在 `ItemInHandRenderer.renderHandsWithItems` 的入口提前绘制双手；提灯随后通过 `RenderHandEvent` 绘制自己的提携手臂。只取消该事件无法撤回 Punchy 已绘制的手，导致持灯侧同时出现两只手。

`LanternPunchyCompatibilityMixin` 将 `DynamicCoreItem` 接入 Punchy 现有的 `isItemBlacklisted` 分支。Punchy 通过 `isHandBlacklisted` 和 `isBlacklistedForArm` 只让出对应持灯侧，另一手继续使用 Punchy；双持时两侧都让出。保留原来的皮肤、袖层、提环挂点、摆动和耐久动画。兼容类使用 `@Pseudo`，不增加 Punchy 硬依赖，不写入玩家的黑名单配置，并保留原方法的空值处理。

验证使用隔离世界和玩家安装目录中复制的 Punchy 2.8g、Player Animation Library 2.0.4、本体 `mite-0.2.18.jar`。构建及 38 项 JUnit 通过；全部三种保留材质、两种惯用手、主手／副手／双持共 36 项手臂归属检查通过。四张实际 GPU 截图调用完整的 `renderHandsWithItems`，覆盖 Punchy 的提前绘制入口；副手提灯时 Punchy 只请求另一只手的皮肤缓冲，双持提灯时 Punchy 请求皮肤缓冲次数为零。四张截图检查正常。另一次未安装 Punchy 的完整提灯客户端检查通过，所有测试世界正常保存退出。未重跑服务端玩法 GameTest，也未加载整合包的全部其他模组和材质包。

日志与报告位于 `build/verification/lantern-punchy/`：`final.log`、`client-audit.json`、`vanilla-client.log`、`vanilla-client-audit.json`、`package-audit.json`。完整第一人称截图位于同目录 `mob-render-audit/lantern-punchy-full-*.png`。客户端报告的 `lanterns.carry.punchy_compatibility` 保存专项结果。

成品为 `build/delivery/lantern-animation/modern-ite-0.2.18.jar`，1,610,591 字节，SHA-256 `4ac8742a39ddac78ece271ef57fd70e1670a7fff462cce51cb7780966882ece7`。333 个生产类及 751 份处理后资源逐字节匹配构建输出，不含测试类、本体类或嵌套 JAR。相对于玩家已安装的 `modern-ite-0.2.17.jar`，只新增上述兼容类，修改 Mixin 注册及版本号；其余生产文件完全一致。
