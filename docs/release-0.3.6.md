# MITE: Modern ITE 0.3.6

November 16, 2026. Developed by NingMeng123.

## Changes

- Fixed nutrition gems to add capacity above the level-based satiation and nutrition limits. The combined armor-gem bonus can add up to 20 extra points to both reserves. At level 35 or higher, four rank-six nutrition gems raise both capacities to 40. Removing armor clamps stored reserves to the remaining capacity.
- Fixed Efficiency gems to combine their rank with ordinary Efficiency before calculating the squared mining bonus. Growth Efficiency contributes separately, and the combined bonus follows the effective-tool, environment, hunger and level rules.
- Corrected durability gems to add 3.75 percentage points of durability-negation chance per rank, reaching 22.5 percentage points at rank six. Gem bonuses combine with ordinary and growth Unbreaking through one roll per durability point, including direct native durability consumption.
- Fixed speed gems to add their movement bonus to the Speed enchantment through the shared equipment-speed calculation. The combined equipment bonus also applies alongside potion movement effects.
- Updated English and Chinese gem tooltips to describe the capacity, Efficiency, durability and speed rules.
- Updated the minimum required MITE: Modern version to 0.3.6.

Build file `MITE-Modern-ITE-0.3.6.jar`.

Requires MITE: Modern 0.3.6 or newer, Minecraft 1.21.1, Java 21 and NeoForge 21.1.259 or newer within the 21.1 series. Install the addon and core mod on both client and server.

## 中文更新日志

- 修复饱食宝石容量加成。宝石会在等级提供的基础容量上，额外增加饱食度和营养容量，总加成最多额外增加 20 点。35 级及以上穿戴四件各镶嵌六阶饱食宝石的护甲时，两项容量均可达到 40 点。卸下护甲后，储量按剩余容量裁减。
- 修复效率宝石与普通效率附魔的叠加。宝石阶数先与普通效率等级合并，再计算平方挖掘加成。成长效率单独计算，合并后的效果受有效工具、环境、饥饿和等级规则约束。
- 校正耐久宝石效果，每阶额外增加 3.75 个百分点的免耗概率，六阶为 22.5 个百分点。宝石与普通、成长耐久附魔的概率合并，每点耐久只结算一次随机判定，并覆盖原生直接耐久消耗。
- 修复速度宝石与移速附魔的叠加，通过统一的装备移速计算将两者加成相加，并计入药水移动效果。
- 更新中英文宝石提示，明确容量、效率、耐久及速度效果。
- 最低本体依赖更新为 MITE: Modern 0.3.6。

构建文件为 `MITE-Modern-ITE-0.3.6.jar`。

需要 MITE: Modern 0.3.6 或更新版本、Minecraft 1.21.1、Java 21，以及 NeoForge 21.1.259 或更新的 21.1 系列版本。客户端和服务端均须安装附属及本体。

开发者 NingMeng123。
