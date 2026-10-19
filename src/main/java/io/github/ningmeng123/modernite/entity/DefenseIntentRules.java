package io.github.ningmeng123.modernite.entity;

/** Observable intent thresholds and native projectile geometry. @author NingMeng123 */
public final class DefenseIntentRules {
    public static final double PLAYER_RANGE = 32D;
    public static final double APPROACH_RANGE = 6D;
    public static final double APPROACH_SPEED = .03D;
    public static final double MELEE_WARNING = .75D;
    public static final int INTENT_TICKS = 2;
    public static final int RECENT_ATTACK_TICKS = 20;
    public static final int PROJECTILE_TICKS = 12;
    public static final double PROJECTILE_PADDING = .3D;
    public static final double AIR_DRAG = (double) .99F;
    private static final double MELEE_COSINE = Math.cos(Math.toRadians(60D));
    private static final double RANGED_COSINE = Math.cos(Math.toRadians(10D));

    private DefenseIntentRules() {}

    public static boolean meleeFacing(double dx, double dz, double lookX, double lookZ) {
        return horizontalFacing(dx, dz, lookX, lookZ, MELEE_COSINE);
    }

    private static boolean horizontalFacing(double dx, double dz, double lookX, double lookZ, double cosine) {
        double distance = Math.hypot(dx, dz), look = Math.hypot(lookX, lookZ);
        return distance < 1.0E-7D || look > 1.0E-7D
                && (dx * lookX + dz * lookZ) / (distance * look) >= cosine - 1.0E-10D;
    }

    public static boolean rangedFacing(double dx, double dy, double dz, double lookX, double lookY, double lookZ) {
        if (!horizontalFacing(dx, dz, lookX, lookZ, RANGED_COSINE)) return false;
        double angle = Math.atan2(lookY, Math.hypot(lookX, lookZ));
        double targetAngle = Math.atan2(dy, Math.hypot(dx, dz));
        return Math.abs(angle - targetAngle) <= Math.toRadians(25D) + 1.0E-10D;
    }

    public static boolean approaching(double distanceSquared, double towardSpeed, boolean facing, int observations) {
        return facing && distanceSquared <= APPROACH_RANGE * APPROACH_RANGE
                && towardSpeed >= APPROACH_SPEED - 1.0E-10D && observations >= INTENT_TICKS;
    }

    public static boolean combatFamily(String family) {
        return switch (family) {
            case "SWORD", "AXE", "BATTLE_AXE", "HATCHET", "DAGGER", "KNIFE", "SCYTHE", "CLUB", "CUDGEL", "WAR_HAMMER" -> true;
            default -> false;
        };
    }

    public static int priority(String kind) {
        return switch (kind) {
            case "PROJECTILE" -> 3;
            case "SWING", "RECENT_ATTACK" -> 2;
            case "RANGED_AIM", "MELEE_READY" -> 1;
            default -> 0;
        };
    }

    /** Equal threats keep their focus; a different arrow must arrive at least two ticks earlier. */
    public static boolean replace(int currentPriority, double currentImpact, boolean currentPreferred,
            int candidatePriority, double candidateImpact, boolean candidatePreferred) {
        if (candidatePriority != currentPriority) return candidatePriority > currentPriority;
        if (candidatePriority == 3) {
            if (currentPreferred) return candidateImpact <= currentImpact - 2D;
            if (candidatePreferred) return !(currentImpact <= candidateImpact - 2D);
            return candidateImpact < currentImpact;
        }
        return candidatePreferred && !currentPreferred;
    }

    @FunctionalInterface public interface Obstruction {
        /** First blocking fraction in this segment; positive infinity means unobstructed. */
        double firstBlock(double fromX, double fromY, double fromZ, double toX, double toY, double toZ);
    }

    /** Segment/slab intersection, including a projectile that begins inside the threatened box. */
    public static double intersection(double x, double y, double z, double endX, double endY, double endZ,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double entry = 0D, exit = 1D;
        double[] start = {x, y, z}, delta = {endX - x, endY - y, endZ - z};
        double[] minimum = {minX, minY, minZ}, maximum = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0E-12D) {
                if (start[axis] < minimum[axis] || start[axis] > maximum[axis]) return Double.POSITIVE_INFINITY;
            } else {
                double first = (minimum[axis] - start[axis]) / delta[axis];
                double last = (maximum[axis] - start[axis]) / delta[axis];
                entry = Math.max(entry, Math.min(first, last));
                exit = Math.min(exit, Math.max(first, last));
                if (entry > exit) return Double.POSITIVE_INFINITY;
            }
        }
        return entry;
    }

    /** Forecast ordinary arrow ticks without moving entities or loading distant chunks. */
    public static double projectileImpact(double x, double y, double z, double vx, double vy, double vz,
            double gravity, double drag, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            Obstruction obstruction) {
        if (!Double.isFinite(x + y + z + vx + vy + vz + gravity + drag)
                || vx * vx + vy * vy + vz * vz < .0001D) return Double.POSITIVE_INFINITY;
        // An arrow already travelling away from the defender is not a nearby projectile threat.
        double centerX = (minX + maxX) * .5D, centerZ = (minZ + maxZ) * .5D;
        if ((centerX - x) * vx + (centerZ - z) * vz < -1.0E-8D) return Double.POSITIVE_INFINITY;
        for (int tick = 0; tick < PROJECTILE_TICKS; tick++) {
            double endX = x + vx, endY = y + vy, endZ = z + vz;
            double contact = intersection(x, y, z, endX, endY, endZ, minX, minY, minZ, maxX, maxY, maxZ);
            double wall = obstruction.firstBlock(x, y, z, endX, endY, endZ);
            if (Double.isFinite(contact) && contact < wall) return tick + contact;
            if (Double.isFinite(wall)) return Double.POSITIVE_INFINITY;
            x = endX; y = endY; z = endZ;
            vx *= drag; vy = vy * drag - gravity; vz *= drag;
        }
        return Double.POSITIVE_INFINITY;
    }
}
