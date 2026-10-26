package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Explosion;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Suppress only newly generated loot in one infernal block callback. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class InfernalExplosionLoot {
    private static final ThreadLocal<Explosion> BLOCK_CALLBACK = new ThreadLocal<>();
    private InfernalExplosionLoot() {}

    /** Read-only marker supplied by the explosion mixin, independent of source-entity lifetime. */
    public interface Access { boolean modernIte$isInfernalExplosion(); }

    public static boolean isInfernalExplosion(Explosion explosion) {
        return explosion instanceof Access access && access.modernIte$isInfernalExplosion();
    }

    public static boolean isDirectKill(DamageSource source) {
        return source.is(DamageTypeTags.IS_EXPLOSION)
                && source.getDirectEntity() instanceof ExtremeMob extreme
                && extreme.extremeKind() == ExtremeKind.BLACK_INFERNAL_CREEPER
                && source.getDirectEntity().getPersistentData().getBoolean("modern_ite:processing_blast");
    }

    public static boolean isDestroying(Explosion explosion) { return BLOCK_CALLBACK.get() == explosion; }

    public static void withoutBlockDrops(Explosion explosion, Runnable callback) {
        Explosion previous = BLOCK_CALLBACK.get();
        BLOCK_CALLBACK.set(explosion);
        try { callback.run(); }
        finally { if (previous == null) BLOCK_CALLBACK.remove(); else BLOCK_CALLBACK.set(previous); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void newLoot(EntityJoinLevelEvent event) {
        Explosion explosion = BLOCK_CALLBACK.get();
        if (explosion != null && explosion.getDirectSourceEntity() != null
                && explosion.getDirectSourceEntity().level() == event.getLevel()
                && (event.getEntity() instanceof ItemEntity || event.getEntity() instanceof ExperienceOrb))
            event.setCanceled(true);
    }
}
