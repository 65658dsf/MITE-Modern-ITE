# MITE: Modern ITE 0.3.6

November 17, 2026. Developed by NingMeng123.

## Changes

- Fixed nutrition gems to add capacity above the level-based satiation and nutrition limits. The combined armor-gem bonus can add up to 20 extra points to both reserves. At level 35 or higher, four rank-six nutrition gems raise both capacities to 40. Removing armor clamps stored reserves to the remaining capacity.
- Fixed Efficiency gems to combine their rank with ordinary Efficiency before calculating the squared mining bonus. Growth Efficiency contributes separately, and the combined bonus follows the effective-tool, environment, hunger and level rules.
- Corrected durability gems to add 3.75 percentage points of durability-negation chance per rank, reaching 22.5 percentage points at rank six. Gem bonuses combine with ordinary and growth Unbreaking through one roll per durability point, including direct native durability consumption.
- Fixed speed gems to add their movement bonus to the Speed enchantment through the shared equipment-speed calculation. The combined equipment bonus also applies alongside potion movement effects.
- Updated English and Chinese gem tooltips to describe the capacity, Efficiency, durability and speed rules.
- Updated the minimum required MITE: Modern version to 0.3.6.
- Restored the one-in-five bat-mount generation for skeleton variants and eligible creepers. Ordinary skeletons use ordinary bats, while qualifying skeleton variants and creepers use vampire bats.
- Joined bat riders and their fresh mounts through the same natural-spawn transaction. Failed finalization or world admission releases the temporary mount and population reservation, and worker-thread mount requests are completed on the main thread.
- Fixed raid-wave membership, leader and health accounting after a raider fails to enter the world. Native special-spawn callers now receive the actual admission result, including Warden spawning.
- Fixed missing structure residents, including end-city shulkers, igloo zombie villagers and mansion residents. Fresh residents generated on worker threads enter population admission on the main thread, and deferred residents can retry after protection or crowding restrictions clear.
- Restored mithril lantern crafting and its creative-tab, search and JEI entries. Mithril and adamantium lanterns use the corresponding legacy light-source recipes, and vibranium upgrades an adamantium lantern through the same tiered layout. Manufacturing difficulty follows the updated ingredients.
- Increased mithril, adamantium and vibranium lantern durability to 9,600, 19,200 and 28,800, providing 40, 80 and 120 minutes of continuous offhand use at 20 TPS.

Build file `MITE-Modern-ITE-0.3.6.jar`.

Requires MITE: Modern 0.3.6 or newer, Minecraft 1.21.1, Java 21 and NeoForge 21.1.259 or newer within the 21.1 series. Install the addon and core mod on both client and server.

## 中文更新日志

- 修复饱食宝石容量加成。宝石会在等级提供的基础容量上，额外增加饱食度和营养容量，总加成最多额外增加 20 点。35 级及以上穿戴四件各镶嵌六阶饱食宝石的护甲时，两项容量均可达到 40 点。卸下护甲后，储量按剩余容量裁减。
- 修复效率宝石与普通效率附魔的叠加。宝石阶数先与普通效率等级合并，再计算平方挖掘加成。成长效率单独计算，合并后的效果受有效工具、环境、饥饿和等级规则约束。
- 校正耐久宝石效果，每阶额外增加 3.75 个百分点的免耗概率，六阶为 22.5 个百分点。宝石与普通、成长耐久附魔的概率合并，每点耐久只结算一次随机判定，并覆盖原生直接耐久消耗。
- 修复速度宝石与移速附魔的叠加，通过统一的装备移速计算将两者加成相加，并计入药水移动效果。
- 更新中英文宝石提示，明确容量、效率、耐久及速度效果。
- 最低本体依赖更新为 MITE: Modern 0.3.6。
- 恢复骷髅变种和符合条件的苦力怕五分之一概率生成蝙蝠坐骑的规则。普通骷髅使用普通蝙蝠，符合条件的骷髅变种与苦力怕使用吸血蝙蝠。
- 蝙蝠骑手与新建坐骑通过同一自然生成事务入场。初始化或入场失败时回收临时坐骑和人口预留，生成线程上的坐骑请求交由主线程完成。
- 修复袭击者入场失败后的波次成员、队长和生命总量状态，原生特殊生成调用会获得真实入场结果，覆盖监守者等生成入口。
- 修复末地城潜影贝、雪屋僵尸村民及林地府邸居民等结构居民丢失的问题。生成线程创建的新居民在主线程执行人口准入，暂存居民可在保护或拥挤限制解除后重试入场。
- 恢复秘银提灯配方及创造栏、搜索与 JEI 展示。秘银与艾德曼提灯使用对应旧版光源配方，振金按同一分级布局使用艾德曼提灯升级，制造难度按更新后的材料计算。
- 秘银、艾德曼、振金提灯耐久分别提高至 9600、19200、28800，20 TPS 下副手连续使用时间分别为 40、80、120 分钟。

构建文件为 `MITE-Modern-ITE-0.3.6.jar`。

需要 MITE: Modern 0.3.6 或更新版本、Minecraft 1.21.1、Java 21，以及 NeoForge 21.1.259 或更新的 21.1 系列版本。客户端和服务端均须安装附属及本体。

开发者 NingMeng123。
