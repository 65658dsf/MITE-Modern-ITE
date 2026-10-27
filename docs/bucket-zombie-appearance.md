# 铁桶僵尸空头槽外观修复

开发者：NingMeng123。日期：2026-10-27。

## 原因与处理

本体在历史最高日期第1～35天清空新生怪物的出生装备，并保存装备锁；Modern ITE也会跳过这些实体的固定装备初始化。因此铁桶僵尸的头盔槽为空，依赖装备的铁桶模型没有机会绘制。已经保存的锁定实体在日期增加或重新加载后仍不会补装备，旧实体也可能持续没有铁桶外观。

新增客户端渲染层`BucketZombieLayer`，只为头盔槽为空的铁桶僵尸绘制物种铁桶，复制最终头部的位置、转角和缩放。该层不创建或修改物品，不增加护甲、掉落或日晒保护，也不改变前35天的出生装备规则；已有空头槽实体无需改写存档即可显示。头盔槽非空时仍使用实际装备的原有显示，隐形实体不绘制该装饰层。真实铁桶损坏后留下的空头槽也适用装饰外观，装备效果仍以实际槽位为准。

生产入口位于`src/main/java/io/github/ningmeng123/modernite/client/entity/BucketZombieLayer.java`，由`ExtremeMobRenderers`仅安装到铁桶僵尸渲染器。

## 验证状态

**通过。** 隔离源码快照完成构建、38项JUnit、全部GameTest源码编译和实际GPU回归。空头槽正面与转头画面相对移除该层的负对照分别改变9355和9771像素；真实铁桶、自定义铁头盔和隐形负对照均为0像素差异。所有装备实例与组件保持不变。

回归使用实际注册的渲染器，比较保留与仅移除新增渲染层的帧缓冲：空头槽正面及转头俯仰应出现铁桶；真实铁桶、带名称和损耗的铁头盔以及隐形负对照应保持原画面；普通僵尸不得安装该层。各情况还检查全部装备实例和组件不被渲染修改。注册的铁桶僵尸仍保持成年形态。

兼容测试环境：EMF 3.3.12、ETF 7.2.6、Sodium 0.8.13、Fresh Animations 1.10.4及Traben 3D Armor 1.0.1。Accelerated Rendering 1.0.14在开发运行环境启动时发生无关的`FormattedText$2Mixin`／`val$text`错误，无法在该开发启动中完成组合验证；这不作为铁桶修复通过或失败的证据。

隔离快照、构建与客户端日志位于`build/verification/bucket-zombie/`。最终构建和客户端日志为`verified-build-client.log`，报告为`client-audit.json`，图像为`mob-render-audit/bucket-zombie-empty-head.png`及同目录的移除渲染层负对照。客户端入口为`modern_ite.mobRenderAudit=true`与`modern_ite.bucketZombieAudit=true`。本次没有重跑服务端GameTest，也没有连接用户的多人服务器；测试只使用新建的一次性世界。

最终包的307个生产类、747份资源与隔离编译输出逐字节一致；相对原安装包仅新增桶渲染层并改变同一渲染器源码产生的类文件，全部资源保持一致，无测试类、本体类或嵌套JAR。包核对为`package-audit.json`，SHA-256为`1dd52c024432be1e7f142b98802b64b86ec33319273e65b191d4b1e58d2bb96d`。共享工作区同期进行的怪物索敌改动未进入此包。

已替换`D:\我的世界\.minecraft\versions\mite–1.21.1\mods\modern-ite-0.2.14.jar`，只需重启客户端；已有空头槽铁桶僵尸无需更改存档或服务端即可恢复外观。原包备份在`D:\Coding\LemonDev\minecraft\.releases\0.2.14-bucket-zombie-20261027\original\modern-ite-0.2.14.jar`，安装核对为`installation.json`。
