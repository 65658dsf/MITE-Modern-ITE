# 本体扩展接口与修改范围

开发署名：NingMeng123。

本体项目为 `../miteto1.21.1neoforge`。用户在另一会话维护的当前版本为 **0.1.9**，本拓展依赖该版本。本次不把本体合并到拓展 JAR。

| 本体位置 | 修改用途 |
|---|---|
| `api/MiteExtensions.java` | 提供单个可选策略；未安装拓展时默认返回原行为。外部工具、护甲、附魔使用公开档案注册 |
| `rules/LunarCalendar.java`、`rules/BloodMoonEquipmentRules.java` | 将统一日历与血月计数交给可选策略；天气、睡眠、刷怪和装备继续复用原入口 |
| `worldgen/R196MobSpawning.java` | 可选深浅独立预算、生成参数与生物池；骑手计入实际所在高度区间，加入失败整组回滚；每区块只抽一次生成轮数 |
| `survival/PlayerHealthEvents.java`、`PlayerFoodEvents.java`、`FoodConsumption.java` | 可选生命、营养容量和自然恢复规则，保留原同步方式 |
| 食物数据、GUI 和 AppleSkin 视图 | 容量读取同一玩家规则，客户端使用已有营养同步数据 |
| `progression/GearProgress*.java`、`EnchantmentEffectBridge.java` | 为额外三阶预留 28 级保存范围；实际材质上限由原规则加可选策略决定；扩展成长经验和副词条档案 |
| `item/EquipmentDurability.java`、装备注册档案 | 维护结果保留主装备拓展组件，识别外部物品；牺牲装备不作为数据来源 |
| `crafting/CraftingQuality.java`、`TimedCraftingController.java` | 为宝典等维修类配方提供保留品质、免制造经验消耗、允许受损主装备的明确入口 |
| `crafting/MaterialAnvilRules.java` | 新增物品的制作难度入口 |
| `enchantment/R196EnchantmentCatalog.java` | 外部附魔定义、适用性、兼容关系入口 |
| `furnace/FurnaceThermalController.java` | 新熔炉、燃料、热量要求和完成回调 |
| `entity/MonsterDayScaling.java` | 普通怪物生命成长显式限制 1024，拓展 Boss 可独立设置上限 |
| `mixin/RangedAttributeAccess.java` 和 Mixin 配置 | 拓展启用时提高生命属性可表示范围，让高血量 Boss 实际生效 |

`MobShields.java` 是开始工作前已经存在的未提交修改，未由本任务恢复或覆盖。`MCMOD.md` 等其他会话文件保留。本仓库只保存独立拓展工程；本体项目的提交与推送由本体自己的开发流程管理。

组合检查发现生成资源的六个附魔标签中混入 `mite_equipment_recycling` 的两个 GameTest 物品 ID；已只清理这两个测试引用，防止发布 JAR 中因缺失物品导致整张标签加载失败。后续在本体带测试注册器运行数据生成时，应确认这些测试 ID 不再次进入发布标签。

新增 JUnit 和 GameTest 仅验证接口和保存边界。已有本体回归记录见验收文档；其他会话后续新增行为仍以本体自己的回归测试为准。
