package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Legacy elite reinforcements, also granted to ordinary lords by request. @author NingMeng123 */
public final class ZombieLordAbilities {
    public static final int SUMMON_INTERVAL = 51;
    public static final int MAX_SUMMONS = 24;
    private static final String REVISION = "modern_ite:zombie_lord_support_revision";
    private static final String MANUAL_SUPPORT = "modern_ite:zombie_lord_manual_support";
    private static final List<ResourceLocation> SUPPORT_TYPES = List.of(
            ResourceLocation.parse("modern_ite:miner_zombie"), ResourceLocation.parse("mite:ghoul"),
            ResourceLocation.parse("mite:wight"), ResourceLocation.parse("mite:revenant"));

    private ZombieLordAbilities() {}

    static boolean handles(ExtremeKind kind) {
        return kind == ExtremeKind.ZOMBIE_LORD || kind == ExtremeKind.ZOMBIE_ELITE_LORD;
    }

    static void initialize(Mob lord, ExtremeKind kind, MobMemory memory) {
        if (!handles(kind) || !(lord.level() instanceof ServerLevel level) || lord.getPersistentData().getInt(REVISION) >= 1) return;
        // Grace never reopens: old attempts made while it is still active could not have spawned any troops.
        if (kind == ExtremeKind.ZOMBIE_ELITE_LORD && memory.initialized
                && SpawnPopulation.explicit(lord) && ExtremeWorldState.graceActive(level)) {
            memory.summons = 0;
            memory.supportCooldown = 0;
        }
        lord.getPersistentData().putInt(REVISION, 1);
    }

    public static void tick(Mob lord, MobMemory memory, LivingEntity target) {
        if (!(lord instanceof ExtremeMob extreme) || !handles(extreme.extremeKind())
                || !(lord.level() instanceof ServerLevel) || !(target instanceof Player player)
                || !player.isAlive() || player.isRemoved() || player.level() != lord.level() || player.isCreative() || player.isSpectator() || memory.summons >= MAX_SUMMONS) return;
        // The old counter advances only while fighting a player, including the first 51 ticks.
        if (memory.supportCooldown <= 0) memory.supportCooldown = SUMMON_INTERVAL;
        if (--memory.supportCooldown > 0) return;
        memory.supportCooldown = SUMMON_INTERVAL;
        var type = BuiltInRegistries.ENTITY_TYPE.get(SUPPORT_TYPES.get(lord.getRandom().nextInt(SUPPORT_TYPES.size())));
        Mob support = MobAbilities.summonType(lord, type, 1, false, candidate -> {
            // Only these four troops inherit a manually requested lord encounter's admission.
            if (SpawnPopulation.explicit(lord)) candidate.getPersistentData().putBoolean(MANUAL_SUPPORT, true);
        });
        if (support != null) {
            support.setNoActionTime(-9600);
            support.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 0));
            memory.summons++;
        }
    }

    static boolean explicitSupport(Mob candidate) {
        return candidate.getPersistentData().getBoolean(MANUAL_SUPPORT)
                && candidate.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                && SUPPORT_TYPES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType()));
    }
}
