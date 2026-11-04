package io.github.ningmeng123.modernite.content.wither;

import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** The actual summoning pattern, rather than nearby skulls, owns this encounter. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class WitherEncounters {
    public static final String SUMMON_KNOWN = "modern_ite:wither_summon_known";
    public static final String ENHANCED = "modern_ite:enhanced_wither";
    public static final int TREASURE_COUNT = 5;

    private WitherEncounters() {}

    public static boolean isEnhanced(Entity entity) {
        return entity instanceof WitherBoss && entity.getPersistentData().getBoolean(ENHANCED);
    }

    public static void markSummon(WitherBoss wither, boolean special) {
        wither.getPersistentData().putBoolean(SUMMON_KNOWN, true);
        wither.getPersistentData().putBoolean(ENHANCED, special);
    }

    /** Ignore WitherPlus's pending nearby-skull marker when the real pattern is known. */
    public static boolean syncSpecialSummon(WitherBoss wither, boolean special) {
        if (wither.getPersistentData().getBoolean(SUMMON_KNOWN)) return isEnhanced(wither);
        markSummon(wither, special);
        return special;
    }

    /** WitherPlus's saved root marker also migrates encounters from older saves. */
    public static void loadSpecialSummon(WitherBoss wither, boolean special) {
        markSummon(wither, special);
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide || !isEnhanced(event.getEntity())) return;
        WitherBoss wither = (WitherBoss) event.getEntity();
        ItemStack treasure = new ItemStack(WitherTreasureContent.TREASURE.get(), TREASURE_COUNT);
        ItemEntity drop = new ItemEntity(wither.level(), wither.getX(), wither.getY(), wither.getZ(), treasure);
        drop.setExtendedLifetime();
        event.getDrops().add(drop);
    }
}
