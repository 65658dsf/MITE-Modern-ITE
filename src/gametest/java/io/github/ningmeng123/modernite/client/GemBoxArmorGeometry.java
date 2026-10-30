package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.HumanoidArm;

/** Independent emitted-vertex attachment oracle for native skin and CEM armor arms. @author NingMeng123 */
final class GemBoxArmorGeometry {
    private static final double EPSILON = 1E-7D;
    private static final double MIN_AXIS_COSINE = Math.cos(Math.toRadians(35D));
    private final Map<HumanoidArm, Samples> samples = new EnumMap<>(HumanoidArm.class);

    void skin(HumanoidArm arm, float x, float y, float z) { add(arm, x, y, z, false); }
    void armor(HumanoidArm arm, float x, float y, float z) { add(arm, x, y, z, true); }

    private void add(HumanoidArm arm, float x, float y, float z, boolean armor) {
        require(arm != null && Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z),
                "Emitted armor/skin geometry must have a known hand and finite coordinates");
        Samples target = samples.computeIfAbsent(arm, ignored -> new Samples());
        if (armor) { ++target.armorCount; target.armor.add(new Point(x, y, z)); }
        else { ++target.skinCount; target.skin.add(new Point(x, y, z)); }
    }

    JsonObject verify() {
        JsonObject result = new JsonObject();
        result.addProperty("method", "distinct emitted vertices; PCA axes; world AABB centers; projection onto the native skin axes");
        result.addProperty("scope", "native original arm skin only, without sleeve vertices, and actual matching CEM armor vertices in one common render space");
        result.addProperty("maximum_axis_angle_degrees", 35D);
        result.addProperty("minimum_longitudinal_skin_coverage", .8D);
        result.addProperty("minimum_projected_axis_span_ratio", .95D);
        result.addProperty("maximum_lateral_center_offset_in_skin_cross_section_radii", .9D);
        for (HumanoidArm arm : HumanoidArm.values()) {
            Samples sample = samples.get(arm);
            require(sample != null && sample.skin.size() >= 8 && sample.armor.size() >= 8,
                    arm + " must submit both complete native skin and armor geometry with at least eight distinct vertices");
            result.add(arm.name().toLowerCase(java.util.Locale.ROOT), verify(arm, sample));
        }
        result.addProperty("passed", true);
        return result;
    }

    private static JsonObject verify(HumanoidArm side, Samples sample) {
        List<Point> skin = List.copyOf(sample.skin), armor = List.copyOf(sample.armor);
        Principal skinPca = principal(skin), armorPca = principal(armor);
        require(skinPca.values[0] > EPSILON * EPSILON && skinPca.values[0] > skinPca.values[1] * 1.5D,
                side + " original skin vertices must identify an elongated arm rather than a sleeve or unrelated object");
        Point axis = skinPca.axes[0], across = skinPca.axes[1], depth = skinPca.axes[2];
        Point skinCenter = bounds(skin).center(), armorCenter = bounds(armor).center();
        Point delta = armorCenter.subtract(skinCenter);
        double axialCenter = delta.dot(axis);
        double lateralCenter = Math.sqrt(Math.max(0D, delta.dot(delta) - axialCenter * axialCenter));
        double axisCosine = Math.min(1D, Math.abs(axis.dot(armorPca.axes[0])));
        Interval skinLong = project(skin, axis), armorLong = project(armor, axis);
        Interval skinAcross = project(skin, across), armorAcross = project(armor, across);
        Interval skinDepth = project(skin, depth), armorDepth = project(armor, depth);
        double length = skinLong.span(), width = skinAcross.span(), thickness = skinDepth.span();
        require(Math.min(length, Math.min(width, thickness)) > EPSILON, side + " native arm bounds must have a nonzero volume");
        double overlap = Math.max(0D, Math.min(skinLong.maximum, armorLong.maximum) - Math.max(skinLong.minimum, armorLong.minimum));
        double coverage = overlap / length;
        double lengthRatio = armorLong.span() / length;
        double widthRatio = armorAcross.span() / width;
        double depthRatio = armorDepth.span() / thickness;
        double volumeRatio = lengthRatio * widthRatio * depthRatio;
        double crossRadius = Math.hypot(width, thickness) * .5D;
        double missingLow = Math.max(0D, armorLong.minimum - skinLong.minimum) / length;
        double missingHigh = Math.max(0D, skinLong.maximum - armorLong.maximum) / length;
        JsonObject facts = new JsonObject();
        facts.addProperty("skin_submitted_vertices", sample.skinCount);
        facts.addProperty("armor_submitted_vertices", sample.armorCount);
        facts.addProperty("skin_distinct_vertices", skin.size());
        facts.addProperty("armor_distinct_vertices", armor.size());
        facts.add("skin_aabb_center", vector(skinCenter));
        facts.add("armor_aabb_center", vector(armorCenter));
        facts.add("skin_long_axis", vector(axis));
        facts.add("armor_long_axis", vector(armorPca.axes[0]));
        facts.add("skin_pca_eigenvalues", numbers(skinPca.values));
        facts.add("armor_pca_eigenvalues", numbers(armorPca.values));
        facts.addProperty("unoriented_axis_angle_degrees", Math.toDegrees(Math.acos(axisCosine)));
        facts.add("skin_longitudinal_interval", numbers(new double[]{skinLong.minimum, skinLong.maximum}));
        facts.add("armor_on_skin_axis_interval", numbers(new double[]{armorLong.minimum, armorLong.maximum}));
        facts.addProperty("longitudinal_skin_coverage", coverage);
        facts.addProperty("longitudinal_span_ratio", lengthRatio);
        facts.addProperty("cross_section_width_ratio", widthRatio);
        facts.addProperty("cross_section_depth_ratio", depthRatio);
        facts.addProperty("skin_axis_bounds_volume_ratio", volumeRatio);
        facts.addProperty("axial_center_shift_in_skin_lengths", axialCenter / length);
        facts.addProperty("lateral_center_shift_in_skin_cross_section_radii", lateralCenter / crossRadius);
        facts.addProperty("missing_low_endpoint_in_skin_lengths", missingLow);
        facts.addProperty("missing_high_endpoint_in_skin_lengths", missingHigh);
        // Traben outer_armor.jem contains an unrotated 12x4x4 shell with child
        // inflation (Y=1.1666667, X/Z=1.5): expected ratios are 1.1667,1.5,1.5.
        // These deliberately wider limits tolerate rib/cap detail, deduplication,
        // small asymmetries and different UV face counts, but reject a fist-sized
        // shell, a sideways/reversed-from-the-joint arm, or a detached parallel arm.
        require(axisCosine + EPSILON >= MIN_AXIS_COSINE, failure(side, "armor long axis is tilted away from the native arm", facts));
        require(coverage + EPSILON >= .8D && missingLow <= .2D + EPSILON && missingHigh <= .2D + EPSILON,
                failure(side, "armor does not cover the native arm's longitudinal endpoints", facts));
        require(lengthRatio + EPSILON >= .95D && widthRatio + EPSILON >= .95D && depthRatio + EPSILON >= .95D,
                failure(side, "armor inflation is missing or the shell has collapsed relative to native skin", facts));
        require(volumeRatio + EPSILON >= .97D && lengthRatio <= 2.25D && widthRatio <= 2.5D && depthRatio <= 2.5D,
                failure(side, "armor extent differs implausibly from an inflated arm shell", facts));
        require(Math.abs(axialCenter) <= length * .35D + EPSILON && lateralCenter <= crossRadius * .9D + EPSILON,
                failure(side, "armor shell center has drifted away from its supporting native skin arm", facts));
        facts.addProperty("passed", true);
        return facts;
    }

    private static String failure(HumanoidArm arm, String reason, JsonObject facts) { return arm + ": " + reason + "; geometry=" + facts; }
    private static Interval project(List<Point> points, Point direction) {
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (Point point : points) { double value = point.dot(direction); low = Math.min(low, value); high = Math.max(high, value); }
        return new Interval(low, high);
    }
    private static Bounds bounds(List<Point> points) {
        double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Point point : points) for (int axis = 0; axis < 3; ++axis) {
            double value = point.component(axis); low[axis] = Math.min(low[axis], value); high[axis] = Math.max(high[axis], value);
        }
        return new Bounds(new Point(low[0], low[1], low[2]), new Point(high[0], high[1], high[2]));
    }

    /** Jacobi eigensolver for a symmetric 3x3 covariance matrix; no renderer or optional library state. */
    private static Principal principal(List<Point> points) {
        double[] mean = new double[3];
        for (Point point : points) for (int i = 0; i < 3; ++i) mean[i] += point.component(i) / points.size();
        double[][] matrix = new double[3][3], vectors = {{1D, 0D, 0D}, {0D, 1D, 0D}, {0D, 0D, 1D}};
        for (Point point : points) for (int row = 0; row < 3; ++row) for (int column = 0; column < 3; ++column)
            matrix[row][column] += (point.component(row) - mean[row]) * (point.component(column) - mean[column]) / points.size();
        for (int sweep = 0; sweep < 32; ++sweep) {
            int p = 0, q = 1;
            for (int row = 0; row < 3; ++row) for (int column = row + 1; column < 3; ++column)
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[p][q])) { p = row; q = column; }
            if (Math.abs(matrix[p][q]) < 1E-20D) break;
            double angle = .5D * Math.atan2(2D * matrix[p][q], matrix[q][q] - matrix[p][p]);
            double c = Math.cos(angle), s = Math.sin(angle), pp = matrix[p][p], qq = matrix[q][q], pq = matrix[p][q];
            for (int i = 0; i < 3; ++i) if (i != p && i != q) {
                double ip = matrix[i][p], iq = matrix[i][q];
                matrix[i][p] = matrix[p][i] = c * ip - s * iq;
                matrix[i][q] = matrix[q][i] = s * ip + c * iq;
            }
            matrix[p][p] = c * c * pp - 2D * c * s * pq + s * s * qq;
            matrix[q][q] = s * s * pp + 2D * c * s * pq + c * c * qq;
            matrix[p][q] = matrix[q][p] = 0D;
            for (int i = 0; i < 3; ++i) {
                double ip = vectors[i][p], iq = vectors[i][q];
                vectors[i][p] = c * ip - s * iq; vectors[i][q] = s * ip + c * iq;
            }
        }
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
        order.sort((a, b) -> Double.compare(matrix[b][b], matrix[a][a]));
        double[] values = new double[3]; Point[] axes = new Point[3];
        for (int i = 0; i < 3; ++i) {
            int column = order.get(i); values[i] = Math.max(0D, matrix[column][column]);
            Point axis = new Point(vectors[0][column], vectors[1][column], vectors[2][column]);
            int largest = 0;
            for (int j = 1; j < 3; ++j) if (Math.abs(axis.component(j)) > Math.abs(axis.component(largest))) largest = j;
            axes[i] = axis.component(largest) < 0D ? axis.multiply(-1D) : axis;
        }
        return new Principal(axes, values);
    }

    private static JsonArray vector(Point point) { return numbers(new double[]{point.x, point.y, point.z}); }
    private static JsonArray numbers(double[] values) { JsonArray result = new JsonArray(); for (double value : values) result.add(value); return result; }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    private static final class Samples {
        int skinCount, armorCount;
        final Set<Point> skin = new LinkedHashSet<>(), armor = new LinkedHashSet<>();
    }
    private record Point(double x, double y, double z) {
        double component(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        double dot(Point other) { return x * other.x + y * other.y + z * other.z; }
        Point subtract(Point other) { return new Point(x - other.x, y - other.y, z - other.z); }
        Point multiply(double value) { return new Point(x * value, y * value, z * value); }
    }
    private record Bounds(Point low, Point high) { Point center() { return new Point((low.x + high.x) * .5D, (low.y + high.y) * .5D, (low.z + high.z) * .5D); } }
    private record Interval(double minimum, double maximum) { double span() { return maximum - minimum; } }
    private record Principal(Point[] axes, double[] values) {}
}
