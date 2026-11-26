package io.github.ningmeng123.modernite.entity;

import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/** Damage shared by the dragon's direct skill hits and skill-triggered explosions. @author NingMeng123 */
public final class EnderDragonPlusDamage {
    public static final float SKILL_MULTIPLIER = 20F;

    private EnderDragonPlusDamage() {}

    public static <T> T skillExplosion(Entity source, Supplier<T> explode) {
        boolean alreadyMarked = BossSummonedMobs.isBossHazard(source);
        BossSummonedMobs.markBossHazard(source);
        try {
            return explode.get();
        } finally {
            if (!alreadyMarked) BossSummonedMobs.clearBossHazard(source);
        }
    }

    public static float scaleExplosionDamage(Entity source, float amount) {
        if ((source instanceof EnderDragon || source instanceof EndCrystal) && BossSummonedMobs.isBossHazard(source)) {
            return amount * SKILL_MULTIPLIER;
        }
        return amount;
    }
}
