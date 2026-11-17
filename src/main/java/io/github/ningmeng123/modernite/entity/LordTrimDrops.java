package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.modernite.ModernIte;
import java.util.List;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Existing lord identities share one optional vanilla trim reward per death. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class LordTrimDrops {
    private static final int DROP_CHANCE_PERCENT = 10;
    private static final List<Item> TEMPLATES = List.of(
            Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE);

    private LordTrimDrops() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof Mob mob) || !isLord(mob)
                || !mob.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
                || mob.getRandom().nextInt(100) >= DROP_CHANCE_PERCENT) return;
        var template = TEMPLATES.get(mob.getRandom().nextInt(TEMPLATES.size()));
        event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), new ItemStack(template)));
    }

    static boolean isLord(Mob mob) {
        if (mob instanceof R196Skeleton skeleton && skeleton.isLord()) return true;
        if (!(mob instanceof ExtremeMob extreme)) return false;
        ExtremeKind kind = extreme.extremeKind();
        return ZombieLordAbilities.handles(kind) || switch (kind) {
            case ZOMBIE_PIGMAN_LORD, SKELETON_ELITE_LORD, WITCH_LORD, TIME_LORD -> true;
            default -> false;
        };
    }
}
