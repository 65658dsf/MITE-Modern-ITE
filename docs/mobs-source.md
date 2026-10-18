# 生物来源与迁移规格

维护者：NingMeng123。

弓箭手瞄准强化：普通骷髅、本体骷髅与拓展骷髅的持弓射击，以及普通猪人的持弓目标，共用按箭矢原生阻力和重力计算的发射预判。服务器逐 tick 采样目标水平位置，停步和反向即时更新，传送不计入走位速度；预测速度最多 0.6 格/tick，提前量最多 8 格。静止目标散布为 0.15，移动目标为 0.35，各难度采用相同瞄准精度。预测点被实体方块遮挡时退回可见身体位置，目标完全不可见时拒绝射击。命中率验收以无遮挡的静止玩家为对象，范围 6～30 格，另含 15 格距离的正负 5 格高低差；详细实测见 [验收记录](verification.md)。

只把两个参考 JAR 当作被审计的程序与资源，不执行它们。Extreme 0.6.6 的 `events/ExtremeFishEvents.registerEntities` 实际注册 **44 个生物**；另迁移 ITE 2.0.28 的 **4 个正式生物**。48 个身份各有独立注册、刷怪蛋、属性、存读、战斗逻辑与客户端渲染器。掠夺者虽然现代 Minecraft 已有对应物种，本表两个身份仍保留 Extreme 独立战斗与生成语义。`modern_ite:` 为下表所有注册 ID 的命名空间。

“自然”均进入本体刷怪预算与 `SpawnPlacements`/碰撞检查，遵循难度、黑暗与维度条件。通用维度指主世界、下界与 MITE 地底世界，不包含末地；特殊召唤和刷怪蛋不受自然生成日期门槛限制。掠夺者改接现代巡逻和前哨站：33 天后替换原生掠夺者；65 天后已有旗手替换为首领并召唤 3 名随从，没有另加 5% 随机首领概率。

| 注册 ID | 中文 | 源类短名 | 当前生存生成入口 | 关键能力 |
|---|---|---|---|---|
| zombie_door | 僵尸盗贼 | EntityZombieDoor | 自然；通用维度 | 铁门武器、专属曲线生命/攻击、召 10 僵尸、10% 转移骷髅援军 |
| miner_zombie | 矿工僵尸 | EntityMinerZombie | 自然；33 天起，主世界/地底 | 振金效率 X 镐、真实挖掘阶段耗时÷4.8、免缴械 |
| zombie_chaos | 混乱僵尸 | EntityZombieChaos | 自然；通用维度 | 5% 可见度、命中混乱 200 tick |
| zombie_fly_away | 大力士僵尸 | EntityZombieFlyAway | 自然；通用维度 | 低基础攻击、强力击退与按日增长的抛飞高度、速度 II |
| zombie_ghast | 恶魂僵尸 | EntityZombieGhast | 自然；33 天起 | 64 格内预测瞄准 NB 火球，下界五连发 |
| zombie_jump | 倒立僵尸 | EntityZombieJump | 自然；通用维度 | 倒立渲染、命中颠倒 120 tick、专属生命曲线 |
| zombie_knockback | 击退僵尸 | EntityZombieKnockback | 自然；通用维度 | 强水平击退、速度增益 |
| zombie_invisible_look_up | 仰头僵尸 | EntityZombieInvisibleLookUp | 自然；通用维度 | 5% 可见度、命中强制仰头 200 tick |
| zombie_pumpkin | 万圣僵尸 | EntityZombiePumpkin | 自然；通用维度 | 南瓜头、60 基础生命、命中遮蔽 600 tick |
| zombie_iron_bucket | 铁桶僵尸 | EntityZombieIronBucket | 自然；通用维度 | 铁桶头盔、60 基础生命 |
| zombie_siege | 攻城尸 | EntityZombieSiege | 自然；通用维度 | 幼年僵尸骑手、面向目标投掷骑手；修正源空目标判断 |
| dragon_zombie | 龙头僵尸 | EntityDragonZombie | 自然；通用维度 | 每秒为 16×8×16 范围其他僵尸刷新力量/速度 |
| random_zombie | 随机僵尸 | EntityRandomZombie | 自然；通用维度 | 随机铜至振金整套装备，随机磨损；掉绿宝石 |
| fishing_rod_zombie | 大头僵尸 | FishingRodEntity | 自然；通用维度 | 古金钓竿；15 格可见目标施加大头。源没有有效鱼钩技能调用 |
| mirror_zombie | 镜像僵尸 | EntityMirrorZombie | 自然；33 天起 | 复制目标装备/效果并扩展生命，复制装备不掉落、不共享 ItemStack |
| mimic_zombie | 拟态僵尸 | EntityMimicZombie | 玩家实际死亡时生成 | 保留死亡玩家名字，在线玩家皮肤，固定身份持久保存 |
| zombie_caller | 唤尸 | EntityZombieCaller | 自然；通用维度；地底锁敌或玩家攻击启动 | 开始召 4 僵尸，200 tick 再召 3，400 tick 自爆并出 7–9 蠹虫 |
| zombie_boss | 僵尸BOSS | EntityZombieBoss | 地底玩家成功破坏刷怪笼后 50% 挑战，并伴女巫领主 | 只接受玩家直接近战、伤害÷5、压制力量、远距雷击、挑战重置、伤害榜奖励 |
| zombie_elite_lord | 僵尸精英领主 | EntityZombieEliteLord | 自然；33 天起；末影龙援军 | 振金装备、专属生命曲线、最多 24 次矿工/食尸鬼/尸妖/亡魂召援 |
| undying_zombie_emperor | 不朽尸皇 | EntityUndyingZombieEmperor | 自然；128 天起，主世界/地底 | 双倍领主曲线生命、振金保护 V/锋利 V 棍、一次性 2 僵尸领主+2 流浪女巫 |
| exchanger | 转移骷髅 | EntityExchanger | 自然；64 天起；盗贼援军 | 预警约 3 秒后交换双方 XYZ；任一穿戴物固定附魔阻止；免缴械 |
| mirror_skeleton | 镜像骷髅 | EntityMirrorSkeleton | 自然；通用维度 | 复制装备/效果/生命，复制物不掉落 |
| skeleton_fast_arrow | 速射手骷髅 | EntitySkeletonFastArrow | 自然；通用维度 | 30 tick 射击间隔 |
| skeleton_knockback | 击飞骷髅 | EntitySkeletonKnockback | 自然；下界/地底 | 木剑近战、抛飞目标 |
| skeleton_lightning | 闪电骷髅 | EntitySkeletonLightning | 自然；96 天起，下界/地底 | 箭命中玩家的最终伤害 6，闪电表现 |
| skeleton_floating | 漂浮骷髅 | EntitySkeletonFloating | 自然；下界/地底 | 命中施加悬浮 200 tick |
| skeleton_blaze | 烈焰骷髅 | EntitySkeletonBlaze | 自然；33 天起 | 火矢 II、火焰免疫、5% 烈焰粉 |
| wither_skeleton_arrow | 凋零骷髅射手 | EntityWitherSkeletonArrow | 自然；下界；凋零召援 | 古金弓、冲击 II、命中凋零 300 tick、煤/稀有头颅 |
| skeleton_elite_lord | 骷髅暴君 | EntitySkeletonEliteLord | 自然；通用维度；末影龙援军 | 振金近战装备、专属生命曲线、最多召 6 骷髅；与本体骷髅领主不同身份 |
| skeleton_zombie | 骷髅僵尸 | EntitySkeletonZombie | 自然；通用维度 | 弓/锈铁剑双武器，5/6 格阈值切换，备用武器存读 |
| run_creeper | 疾跑苦力怕 | EntityRunCreeper | 自然；通用维度 | 0.5 基础移动速度 |
| phase_creeper | 相位苦力怕 | EntityPhaseCreeper | 自然；通用维度 | 2–4 次相位闪避充能，恢复充能并追击传送 |
| chaos_creeper | 混乱苦力怕 | EntityChaosCreeper | 自然；64 天起 | 爆炸伤害附混乱 |
| invisible_creeper | 隐形苦力怕 | EntityInvisibleCreeper | 自然；通用维度 | 隐身效果与 5% 可见渲染 |
| invisible_reversed_creeper | 隐形颠倒苦力怕 | EntityInvisibleReversedCreeper | 自然；通用维度 | 隐形，爆炸伤害附颠倒 |
| black_infernal_creeper | 黑暗地狱苦力怕 | EntityBlackInfernalCreeper | 自然；主世界 | 专属生命曲线、大范围双强度爆炸，玩家击杀掉复活传送阵 |
| flash_creeper | 闪光苦力怕 | EntityFlashCreeper | 自然；通用维度 | 爆炸伤害附闪光 |
| wither_creeper | 凋零苦力怕 | EntityWitherCreeper | 自然；通用维度 | 爆炸伤害附凋零 |
| witch_lord | 女巫领主 | EntityWitchLord | 自然；96 天起，主世界/地底；挑战随从 | 370 基础生命、25 tick 投药、最多召 2 流浪女巫，并检查附近女巫数量 |
| red_ghoul | 红色食尸鬼 | EntityRedGhoul | 自然；通用维度 | 攻击玩家/牲畜，命中高灵敏度 120 tick；源为非亡灵生物 |
| revenant_ghast | 亡灵恶魂 | EntityRevenantGhast | 自然；地底128 天起，额外1/4概率 | 预测目标的不可反弹地狱爆裂火球、独立方块/生物爆炸强度 |
| dark_iron_golem | 暗黑铁傀儡 | EntityDarkIronGolem | 自然；下界/地底 | 专属生命/攻击公式、15 向上抛飞；只受镐、爆炸、溺水和雪球伤害 |
| pillager | 掠夺者 | EntityPillager | 33 天后巡逻/前哨站替换；首领召援 | 自定义弩武器、现代弩 AI、持久化不自然消失 |
| pillager_leader | 掠夺者首领 | EntityPillagerLeader | 65 天后原生旗手替换 | 320 基础生命、旗帜与装备、一次性召 3 掠夺者 |
| zombie_lord | 僵尸领主 | ITE EntityZombieLord + Extreme 对应 Mixin | 自然；尸皇随从 | 振金整装、免缴械、专属曲线生命、钻石奖励 |
| wandering_witch | 流浪女巫 | ITE EntityWanderingWitch + Extreme 对应 Mixin | 自然；主世界/地底65 天起；尸皇/女巫领主随从 | 100 基础生命、50 tick 投药、逃跑减速、最多8蝙蝠、真实诅咒 |
| zombie_pigman_lord | 僵尸猪人领主 | ITE EntityZombiePigmanLord + Extreme 对应 Mixin | 普通僵尸猪人1/5替换；自然下界/地底，主世界限血月 | 振金整装、免缴械、石英奖励；保留父类最终生效的固定生命基线 |
| annihilation_skeleton | 湮灭骷髅 | ITE EntityAnnihilationSkeleton + Extreme 对应 Mixin | 自然；主世界33天起额外1/4，下界/地底；刷怪蛋 | 30基础生命、专属3倍攻击曲线、精金护甲、仅玩家直接近战可伤、受攻击后计时消失、免药水/击退 |

## 属性、来源与基线边界

现有本体 `MonsterDayScaling`（C4）是通用怪物日期成长的唯一实现。旧 ITE 的 `15+day/16`、`8+day/24` 等继承性线性日增量已替换为固定基础值，避免再叠一套通用日期增长。属性只在新实体构造/首次旧种初始化时设置；读档保留保存的实际属性，定期 C4 刷新使用唯一 modifier，不重复乘算。

保留物种自身的挑战机制：转移骷髅/盗贼/矿工/领主/倒立的 `rate`、`healthRate` 曲线，僵尸BOSS `r=min(366,最高天数/10)` 的 `20+45r` 生命，暗黑铁傀儡和黑暗地狱苦力怕专属曲线，以及湮灭骷髅的三倍攻击曲线。它们区别于通用继承增长。显式Boss在出生前标记 `modern_ite:boss`；addon启用时最大生命属性范围扩大至一百万，普通怪 C4 仍明确上限1024。暗黑铁傀儡与黑暗地狱苦力怕继续属于普通怪，该上限也会截断它们在极晚天数的专属公式数值；不按计算结果临时改为Boss身份。

原生末影龙保留现代阶段行为，源有效增量包括7500生命、飞行×1.3、碰撞击退×1.5、头部接触60/85、48格水晶与每次治疗2、摧毁水晶反噬5、低血箭免疫/减伤、半血援军。没有调用源私有且从未被调用的 `throwFireBall`。原生凋零1300生命、600tick出生蓄力、双生物强度出生爆炸、出生/半血各15随从及友军伤害豁免。测试古代龙 `EntityTest` 未纳入。

主证据是两个 JAR 的静态反编译：Extreme `ExtremeFishEvents`、各 `entity/Entity*.java`、`mixin/entity`、强制 ITE `mixin/compat/Entity*.java` 和三维度 `BiomeGen*Mixin`；父 ITE `entity`、`trans/entity`、`Configs`。没有仅凭汉语名字推断生物能力。可选 ITF/商店等兼容分支不作为主实现依据。

## 修复与验证边界

已修复源问题：攻城尸不可能成立的空目标条件；流浪女巫 NBT 读写反向；镜像共享玩家物品堆及复制品掉落漏洞；无上限/可能不终止的召唤尝试改为有界碰撞检查；无效附魔样本递归改为实际注册项抽样；召唤一次标记、剩余次数、相位充能、备用武器、诅咒/蝙蝠计数、名字和伤害榜持久保存；现代生命属性1024截断；固定附魔按位置交换实际语义检查全部穿戴物；源无效代金券纹理不凭空注册。

`ExtremeMobGameTests` 的7个场景覆盖48身份创建与保存/加载、实际镜像复制隔离、固定附魔阻止真实XYZ交换、7500/1300生命及C4不复乘、矿工源AI与挖掘接入、不可反弹NB火球威力存读，以及两种掠夺者使用实际 addon 弩生成箭并消耗装填组件。根任务本轮40项GameTest包含上述7项并通过；后续小修以最终统一复跑报告为准。

需要真实客户端/生存场景继续确认的项目：48种动画/半透明/盔甲层与玩家皮肤、复合状态效果画面、跨区块重载中的随从寻路、三维度长期刷怪分布、巡逻/前哨站旗手替换、地底刷怪笼挑战的完整掉落结算、末影龙与凋零整场战斗平衡。本表不把注册数或自动测试当成这些人工验证已完成的证明。

原生父ITE武器机制补差：普通猪人10%持弓、2.5%精英与128天后群体增益；猪人默认不猎杀动物（源配置false）；骨领主/长死人25%生成备用武器并保存后按5/6格切换，已有本体守卫备用武器不重复接管；普通骷髅15%持弓强制近战。源压缩骷髅初始停滞为20tick。源吸血蝙蝠50%中毒480tick、猪人50%点燃5秒、岩浆怪50%点燃100秒按实际配置恢复。

资源尺寸按PNG IHDR核对：大力士/红色食尸鬼64×32使用自定义32高层，普通骷髅原生层64×32，骷髅僵尸64×64使用源完整人形四肢，唤尸64×80、女巫64×128、暗黑铁傀儡128×128、苦力怕/恶魂64×32。真实客户端视觉仍需结合最终截图确认。
