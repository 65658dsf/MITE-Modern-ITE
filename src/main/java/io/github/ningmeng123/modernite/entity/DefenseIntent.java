package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.mixin.entity.ArrowThreatAccessor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Shared, server-observable threats for shields and carried doors. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class DefenseIntent {
    public enum Kind { APPROACH, MELEE_READY, SWING, RECENT_ATTACK, RANGED_AIM, PROJECTILE }

    /** Weak entity references keep cached defense choices from retaining a world or defender. */
    public static final class Threat {
        private final Kind kind;
        private final WeakReference<Entity> source;
        private final WeakReference<Player> attacker;
        private final Vec3 guardPoint;
        private final double impactTicks;

        public Threat(Kind kind, Entity source, Player attacker, Vec3 guardPoint, double impactTicks) {
            this.kind = kind;
            this.source = new WeakReference<>(source);
            this.attacker = new WeakReference<>(attacker);
            this.guardPoint = guardPoint;
            this.impactTicks = impactTicks;
        }

        public Kind kind() { return kind; }
        @Nullable public Entity source() { return source.get(); }
        @Nullable public Player attacker() { return attacker.get(); }
        public Vec3 guardPoint() { return guardPoint; }
        public double impactTicks() { return impactTicks; }
    }

    private static final Map<Mob, Evaluation> EVALUATIONS = new WeakHashMap<>();
    private static final Map<Player, Motion> MOTION = new WeakHashMap<>();
    private static final Map<Mob, Map<Player, Integer>> ATTACKS = new WeakHashMap<>();
    private record Motion(ResourceKey<Level> dimension, long tick, Vec3 baseline, Vec3 position, Vec3 velocity, boolean consecutive) {}
    private static final class Seen {
        int tick = Integer.MIN_VALUE;
        int approachTicks;
        int rangedTicks;
    }
    private static final class Evaluation {
        int tick = Integer.MIN_VALUE;
        Threat threat;
        final Map<Player, Seen> players = new WeakHashMap<>();
    }

    private DefenseIntent() {}

    /** Track accepted attack attempts, including shielded attempts that never reduce health. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(AttackEntityEvent event) {
        if (!event.isCanceled() && event.getTarget() instanceof Mob mob && !mob.level().isClientSide
                && validPlayer(mob, event.getEntity())) {
            ATTACKS.computeIfAbsent(mob, ignored -> new WeakHashMap<>()).put(event.getEntity(), mob.tickCount);
        }
    }

    @SubscribeEvent
    public static void observePlayer(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) motion(player);
    }

    private static Vec3 motion(Player player) {
        long tick = player.level().getGameTime();
        ResourceKey<Level> dimension = player.level().dimension();
        Vec3 position = player.position();
        Motion previous = MOTION.get(player);
        if (previous != null && !previous.dimension.equals(dimension)) previous = null;
        Vec3 baseline;
        boolean consecutive;
        if (previous != null && previous.tick == tick) {
            if (position.equals(previous.position)) return previous.velocity;
            baseline = previous.baseline;
            consecutive = previous.consecutive;
        } else {
            consecutive = previous != null && tick - previous.tick == 1L;
            baseline = consecutive ? previous.position : position;
        }
        Vec3 velocity = consecutive ? position.subtract(baseline) : Vec3.ZERO;
        if (!finite(velocity) || velocity.lengthSqr() > 16D) velocity = Vec3.ZERO;
        velocity = new Vec3(velocity.x, 0D, velocity.z);
        MOTION.put(player, new Motion(dimension, tick, baseline, position, velocity, consecutive));
        return velocity;
    }

    @Nullable public static Threat evaluate(Mob mob) { return evaluate(mob, null); }

    /** Each defender scans its surroundings at most once per actual entity tick. */
    @Nullable public static Threat evaluate(Mob mob, @Nullable Threat preferred) {
        if (mob.level().isClientSide || !mob.isAlive() || mob.isRemoved()) return null;
        Evaluation evaluation = EVALUATIONS.computeIfAbsent(mob, ignored -> new Evaluation());
        if (evaluation.tick == mob.tickCount) return evaluation.threat;
        List<Threat> threats = new ArrayList<>();
        List<Player> players = new ArrayList<>(mob.level().getEntitiesOfClass(Player.class,
                mob.getBoundingBox().inflate(DefenseIntentRules.PLAYER_RANGE), player -> validPlayer(mob, player)));
        if (mob.getTarget() instanceof Player player && validPlayer(mob, player) && !players.contains(player)) players.add(player);
        Map<Player, Integer> recent = ATTACKS.get(mob);
        for (Player player : players) {
            if (mob.distanceToSqr(player) > DefenseIntentRules.PLAYER_RANGE * DefenseIntentRules.PLAYER_RANGE) continue;
            playerThreats(mob, player, evaluation, recent, threats);
        }
        for (AbstractArrow arrow : mob.level().getEntitiesOfClass(AbstractArrow.class,
                mob.getBoundingBox().inflate(DefenseIntentRules.PLAYER_RANGE))) {
            Threat projectile = projectile(mob, arrow);
            if (projectile != null) threats.add(projectile);
        }
        Threat selected = null;
        Entity preferredSource = preferred == null ? null : preferred.source();
        for (Threat candidate : threats) {
            if (selected == null || DefenseIntentRules.replace(DefenseIntentRules.priority(selected.kind.name()),
                    selected.impactTicks, selected.source() == preferredSource,
                    DefenseIntentRules.priority(candidate.kind.name()), candidate.impactTicks, candidate.source() == preferredSource)) {
                selected = candidate;
            }
        }
        evaluation.tick = mob.tickCount;
        evaluation.threat = selected;
        return selected;
    }

    private static boolean validPlayer(Mob mob, Player player) {
        return player.isAlive() && !player.isRemoved() && player.level() == mob.level()
                && !player.isCreative() && !player.isSpectator() && !player.getAbilities().invulnerable
                && !mob.isAlliedTo(player);
    }

    private static void playerThreats(Mob mob, Player player, Evaluation evaluation,
            @Nullable Map<Player, Integer> recent, List<Threat> threats) {
        Vec3 movement = motion(player);
        Seen seen = evaluation.players.computeIfAbsent(player, ignored -> new Seen());
        boolean consecutive = seen.tick != Integer.MIN_VALUE && mob.tickCount - seen.tick == 1;
        Vec3 guardPoint = player.getEyePosition();
        if (!visible(mob, guardPoint)) {
            seen.approachTicks = seen.rangedTicks = 0;
            seen.tick = mob.tickCount;
            return;
        }
        Vec3 toMob = mob.getBoundingBox().getCenter().subtract(player.getEyePosition());
        Vec3 look = player.getLookAngle();
        boolean facing = DefenseIntentRules.meleeFacing(toMob.x, toMob.z, look.x, look.z);
        double reach = SourcePlayerReach.meleeReach(player, mob) + DefenseIntentRules.MELEE_WARNING;
        Vec3 nearest = SourcePlayerReach.closestPoint(mob.getBoundingBox(), player.getEyePosition());
        boolean inReach = player.getEyePosition().distanceToSqr(nearest) <= reach * reach;
        boolean weapon = meleeWeapon(player.getMainHandItem());
        boolean ready = weapon && facing && inReach && !player.isUsingItem();
        if (ready) threats.add(new Threat(Kind.MELEE_READY, player, player, guardPoint, Double.POSITIVE_INFINITY));
        // Mining also animates a swing. Only a weapon aimed through this body is a windup cue;
        // accepted attacks with an empty hand or a farming tool are covered by attack memory.
        var bodyContact = mob.getBoundingBox().inflate(.3D).clip(player.getEyePosition(),
                player.getEyePosition().add(look.scale(reach)));
        boolean aimedAtBody = bodyContact.isPresent() && player.level().clip(new ClipContext(player.getEyePosition(),
                bodyContact.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
        if (weapon && facing && inReach && aimedAtBody && player.swinging && !player.isUsingItem())
            threats.add(new Threat(Kind.SWING, player, player, guardPoint, 0D));
        Integer attackedAt = recent == null ? null : recent.get(player);
        if (attackedAt != null && mob.tickCount >= attackedAt
                && mob.tickCount - attackedAt <= DefenseIntentRules.RECENT_ATTACK_TICKS) {
            threats.add(new Threat(Kind.RECENT_ATTACK, player, player, guardPoint, 0D));
        }
        double horizontalDistance = Math.hypot(toMob.x, toMob.z);
        double closing = horizontalDistance < 1.0E-7D ? 0D : (movement.x * toMob.x + movement.z * toMob.z) / horizontalDistance;
        boolean approaching = weapon && facing && !player.isUsingItem()
                && mob.distanceToSqr(player) <= DefenseIntentRules.APPROACH_RANGE * DefenseIntentRules.APPROACH_RANGE
                && closing >= DefenseIntentRules.APPROACH_SPEED - 1.0E-10D;
        seen.approachTicks = approaching ? consecutive ? seen.approachTicks + 1 : 1 : 0;
        if (DefenseIntentRules.approaching(mob.distanceToSqr(player), closing, approaching, seen.approachTicks))
            threats.add(new Threat(Kind.APPROACH, player, player, guardPoint, Double.POSITIVE_INFINITY));
        boolean aiming = rangedWeapon(player)
                && DefenseIntentRules.rangedFacing(toMob.x, toMob.y, toMob.z, look.x, look.y, look.z);
        seen.rangedTicks = aiming ? consecutive ? seen.rangedTicks + 1 : 1 : 0;
        if (seen.rangedTicks >= DefenseIntentRules.INTENT_TICKS)
            threats.add(new Threat(Kind.RANGED_AIM, player, player, guardPoint, Double.POSITIVE_INFINITY));
        seen.tick = mob.tickCount;
    }

    static boolean meleeWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var profile = EquipmentDurability.toolProfile(stack);
        if (profile != null) return DefenseIntentRules.combatFamily(profile.family().name());
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem || stack.is(Items.MACE)) return true;
        if (stack.getItem() instanceof DiggerItem || stack.getItem() instanceof HoeItem || stack.is(Items.SHEARS)) return false;
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                        && entry.slot().test(EquipmentSlot.MAINHAND) && entry.modifier().amount() > 0D);
    }

    private static boolean rangedWeapon(Player player) {
        ItemStack using = player.getUseItem();
        if (player.isUsingItem() && (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem
                || using.getItem() instanceof TridentItem)) return true;
        return chargedCrossbow(player.getMainHandItem()) || chargedCrossbow(player.getOffhandItem());
    }

    private static boolean chargedCrossbow(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack);
    }

    @Nullable private static Threat projectile(Mob mob, AbstractArrow arrow) {
        if (arrow.isRemoved() || arrow.isNoPhysics() || arrow.getPierceLevel() > 0 || arrow.shakeTime > 0
                || arrow instanceof ArrowThreatAccessor flags && flags.modernite$isInGround()
                || !(arrow.getOwner() instanceof Player player) || !validPlayer(mob, player)
                || arrow.distanceToSqr(mob) > DefenseIntentRules.PLAYER_RANGE * DefenseIntentRules.PLAYER_RANGE
                || !visible(mob, arrow.position())) return null;
        Vec3 position = arrow.position(), velocity = arrow.getDeltaMovement();
        AABB box = mob.getBoundingBox().inflate(DefenseIntentRules.PROJECTILE_PADDING);
        double impact = DefenseIntentRules.projectileImpact(position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z, arrow.getGravity(), arrow.isInWater() ? .6F : DefenseIntentRules.AIR_DRAG,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                (x, y, z, endX, endY, endZ) -> {
                    Vec3 from = new Vec3(x, y, z), to = new Vec3(endX, endY, endZ);
                    HitResult hit = mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow));
                    return hit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY
                            : from.distanceTo(hit.getLocation()) / Math.max(1.0E-12D, from.distanceTo(to));
                });
        return Double.isFinite(impact) ? new Threat(Kind.PROJECTILE, arrow, player, position, impact) : null;
    }

    private static boolean visible(Mob mob, Vec3 point) {
        return mob.level().clip(new ClipContext(mob.getEyePosition(), point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
