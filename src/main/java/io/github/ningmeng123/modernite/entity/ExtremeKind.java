package io.github.ningmeng123.modernite.entity;

/** Source identities, rather than aliases for similarly named R196 species. @author NingMeng123 */
public enum ExtremeKind {
    ZOMBIE_DOOR("僵尸盗贼", Family.ZOMBIE, 0, 2), MINER_ZOMBIE("矿工僵尸", Family.ZOMBIE, 33, 2),
    ZOMBIE_CHAOS("混乱僵尸", Family.ZOMBIE, 0, 10), ZOMBIE_FLY_AWAY("大力士僵尸", Family.ZOMBIE, 0, 10),
    ZOMBIE_GHAST("恶魂僵尸", Family.ZOMBIE, 33, 3), ZOMBIE_JUMP("倒立僵尸", Family.ZOMBIE, 0, 5),
    ZOMBIE_KNOCKBACK("击退僵尸", Family.ZOMBIE, 0, 6), ZOMBIE_INVISIBLE_LOOK_UP("仰头僵尸", Family.ZOMBIE, 0, 10),
    ZOMBIE_PUMPKIN("万圣僵尸", Family.ZOMBIE, 0, 5), ZOMBIE_IRON_BUCKET("铁桶僵尸", Family.ZOMBIE, 0, 20),
    ZOMBIE_SIEGE("攻城尸", Family.ZOMBIE, 0, 5), DRAGON_ZOMBIE("龙头僵尸", Family.ZOMBIE, 0, 3),
    RANDOM_ZOMBIE("随机僵尸", Family.ZOMBIE, 0, 10), FISHING_ROD_ZOMBIE("大头僵尸", Family.ZOMBIE, 0, 5),
    MIRROR_ZOMBIE("镜像僵尸", Family.ZOMBIE, 33, 1), MIMIC_ZOMBIE("拟态僵尸", Family.ZOMBIE, 0, 0),
    ZOMBIE_CALLER("唤尸", Family.ZOMBIE, 0, 1), ZOMBIE_BOSS("僵尸BOSS", Family.ZOMBIE, 0, 0),
    ZOMBIE_ELITE_LORD("僵尸精英领主", Family.ZOMBIE, 33, 2), UNDYING_ZOMBIE_EMPEROR("不朽尸皇", Family.ZOMBIE, 128, 1),
    EXCHANGER("转移骷髅", Family.SKELETON, 64, 7), MIRROR_SKELETON("镜像骷髅", Family.SKELETON, 0, 5),
    SKELETON_FAST_ARROW("速射手骷髅", Family.SKELETON, 0, 5), SKELETON_KNOCKBACK("击飞骷髅", Family.SKELETON, 0, 5),
    SKELETON_LIGHTNING("闪电骷髅", Family.SKELETON, 96, 1), SKELETON_FLOATING("漂浮骷髅", Family.SKELETON, 0, 3),
    SKELETON_BLAZE("烈焰骷髅", Family.SKELETON, 33, 10), WITHER_SKELETON_ARROW("凋零骷髅射手", Family.SKELETON, 0, 5),
    SKELETON_ELITE_LORD("骷髅暴君", Family.SKELETON, 0, 2), SKELETON_ZOMBIE("骷髅僵尸", Family.SKELETON, 0, 20),
    RUN_CREEPER("疾跑苦力怕", Family.CREEPER, 0, 5), PHASE_CREEPER("相位苦力怕", Family.CREEPER, 0, 2),
    CHAOS_CREEPER("混乱苦力怕", Family.CREEPER, 64, 2), INVISIBLE_CREEPER("隐形苦力怕", Family.CREEPER, 0, 5),
    INVISIBLE_REVERSED_CREEPER("隐形颠倒苦力怕", Family.CREEPER, 0, 2), BLACK_INFERNAL_CREEPER("黑暗地狱苦力怕", Family.CREEPER, 0, 2),
    FLASH_CREEPER("闪光苦力怕", Family.CREEPER, 0, 3), WITHER_CREEPER("凋零苦力怕", Family.CREEPER, 0, 5),
    WITCH_LORD("女巫领主", Family.WITCH, 96, 1), RED_GHOUL("红色食尸鬼", Family.ZOMBIE, 0, 10),
    DARK_IRON_GOLEM("暗黑铁傀儡", Family.GOLEM, 0, 1),
    ZOMBIE_LORD("僵尸领主", Family.ZOMBIE, 0, 2), WANDERING_WITCH("流浪女巫", Family.WITCH, 65, 1),
    ZOMBIE_PIGMAN_LORD("僵尸猪人领主", Family.PIGMAN, 0, 1), ANNIHILATION_SKELETON("湮灭骷髅", Family.SKELETON, 0, 1);

    public enum Family { ZOMBIE, SKELETON, CREEPER, WITCH, GHAST, GOLEM, PILLAGER, PIGMAN }
    public final String chinese;
    public final Family family;
    public final int firstDay, speciesFirstDay, weight;
    ExtremeKind(String chinese, Family family, int firstDay, int weight) {
        int sharedDay=ExtremeSpawnProgression.firstDay("modern_ite:"+name().toLowerCase(java.util.Locale.ROOT));
        this.chinese = chinese; this.family = family; this.speciesFirstDay = firstDay;
        this.firstDay = Math.max(sharedDay,firstDay); this.weight = weight;
    }
    /** Keep the species floor while allowing the shared setting to move in either direction. */
    public int naturalFirstDay(int iteDay) { return Math.max(Math.max(1,iteDay),speciesFirstDay); }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public boolean boss() { return switch (this) {
        case ZOMBIE_BOSS, ZOMBIE_ELITE_LORD, UNDYING_ZOMBIE_EMPEROR, SKELETON_ELITE_LORD,
                WITCH_LORD, ANNIHILATION_SKELETON -> true;
        default -> false;
    }; }
    public boolean mirrors() { return this == MIRROR_ZOMBIE || this == MIRROR_SKELETON; }
    public boolean fireproof() { return boss() || switch (this) {
        case EXCHANGER, ZOMBIE_DOOR, DRAGON_ZOMBIE, ZOMBIE_PUMPKIN, FISHING_ROD_ZOMBIE,
                SKELETON_BLAZE, SKELETON_LIGHTNING, WITHER_SKELETON_ARROW, BLACK_INFERNAL_CREEPER,
                DARK_IRON_GOLEM, ZOMBIE_LORD, ZOMBIE_PIGMAN_LORD -> true;
        default -> false;
    }; }
}
