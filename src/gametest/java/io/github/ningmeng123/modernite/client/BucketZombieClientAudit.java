package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.modernite.client.entity.BucketHelmetModel;
import io.github.ningmeng123.modernite.client.entity.BucketZombieLayer;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native GPU controls for a bucket zombie's empty-slot species appearance. @author NingMeng123 */
public final class BucketZombieClientAudit {
    private BucketZombieClientAudit() {}

    public static JsonObject capture(Minecraft mc, Path screenshots) throws Exception {
        Zombie zombie = (Zombie)ExtremeMobs.type(ExtremeKind.ZOMBIE_IRON_BUCKET).create(mc.level);
        Zombie ordinary = EntityType.ZOMBIE.create(mc.level);
        check(zombie != null && ordinary != null, "Both registered zombie factories must create their render samples");
        prepare(mc, zombie); prepare(mc, ordinary);
        zombie.setBaby(true);
        check(!zombie.isBaby(), "The registered bucket-zombie species retains its adult-only behavior");
        Files.createDirectories(screenshots);
        var renderer = (LivingEntityRenderer<?, ?>)mc.getEntityRenderDispatcher().getRenderer(zombie);
        List<Object> layers = layers(renderer);
        List<Object> matching = layers.stream().filter(BucketZombieLayer.class::isInstance).toList();
        check(matching.size() == 1, "The registered iron-bucket zombie renderer needs exactly one species bucket layer");
        check(layers((LivingEntityRenderer<?, ?>)mc.getEntityRenderDispatcher().getRenderer(ordinary)).stream()
                        .noneMatch(BucketZombieLayer.class::isInstance),
                "An ordinary zombie must not acquire the bucket species layer");
        Field bucketField = BucketZombieLayer.class.getDeclaredField("bucket"); bucketField.setAccessible(true);
        BucketHelmetModel bucket = (BucketHelmetModel)bucketField.get(matching.getFirst());
        check(renderer.getModel() instanceof HumanoidModel<?>, "The actual species renderer uses a humanoid head");
        HumanoidModel<?> parent = (HumanoidModel<?>)renderer.getModel();
        JsonObject report = new JsonObject(); report.addProperty("author", "NingMeng123");
        report.addProperty("layer_count", matching.size()); report.addProperty("ordinary_zombie_layer_count", 0);
        JsonArray cases = new JsonArray(); report.add("cases", cases);
        try {
            for (boolean rotated : new boolean[] {false, true}) {
                zombie.yHeadRot = zombie.yHeadRotO = rotated ? 32 : 0;
                zombie.setXRot(rotated ? 16 : 0); zombie.xRotO = zombie.getXRot();
                JsonObject row = new JsonObject(); cases.add(row);
                row.addProperty("case", rotated ? "empty_head_rotated" : "empty_head_forward");
                Equipment before = new Equipment(zombie);
                try (NativeImage actual = MobRenderRegressionAudit.capture(mc, zombie, false, .35F);
                     NativeImage without = withoutBucket(mc, zombie, renderer)) {
                    int changed = difference(actual, without, 0, actual.getHeight());
                    int headChanged = difference(actual, without, 0, 110);
                    row.addProperty("changed_pixels", changed); row.addProperty("head_changed_pixels", headChanged);
                    check(headChanged > 100 && changed > 100, "An empty-head bucket zombie must visibly show its metal bucket in the native GPU draw");
                    sameHead(parent.head, bucket.head);
                    check(bucket.young == parent.young, "The species bucket must preserve its parent model's age transform");
                    if (rotated) check(Math.abs(bucket.head.yRot) > .1F && Math.abs(bucket.head.xRot) > .1F,
                            "The rotated control must exercise the actual rendered yaw and pitch");
                    Path png = screenshots.resolve(rotated ? "bucket-zombie-empty-head-rotated.png" : "bucket-zombie-empty-head.png");
                    actual.writeToFile(png); row.addProperty("screenshot", png.toString());
                    if (!rotated) { Path control = screenshots.resolve("bucket-zombie-missing-layer-control.png");
                        without.writeToFile(control); row.addProperty("missing_layer_control", control.toString()); }
                }
                before.unchanged(zombie); row.addProperty("equipment_unchanged", true);
            }
            zombie.yHeadRot = zombie.yHeadRotO = 0; zombie.setXRot(0); zombie.xRotO = 0;
            ItemStack custom = new ItemStack(Items.IRON_HELMET);
            custom.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 saved helmet")); custom.setDamageValue(7);
            for (ItemStack helmet : List.of(new ItemStack(ExtremeContent.BUCKET_HELMET.get()), custom)) {
                zombie.setItemSlot(EquipmentSlot.HEAD, helmet);
                Equipment before = new Equipment(zombie);
                JsonObject row = new JsonObject(); cases.add(row);
                row.addProperty("case", helmet.is(ExtremeContent.BUCKET_HELMET.get()) ? "real_bucket_helmet" : "custom_iron_helmet");
                try (NativeImage actual = MobRenderRegressionAudit.capture(mc, zombie, false, .35F);
                     NativeImage without = withoutBucket(mc, zombie, renderer)) {
                    int changed = difference(actual, without, 0, actual.getHeight()); row.addProperty("changed_pixels", changed);
                    check(changed == 0, "A real bucket or custom head item must render identically when only the fallback layer is removed");
                }
                before.unchanged(zombie); row.addProperty("equipment_and_components_unchanged", true);
            }
            zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); zombie.setInvisible(true);
            Equipment before = new Equipment(zombie);
            try (NativeImage actual = MobRenderRegressionAudit.capture(mc, zombie, false, .35F);
                 NativeImage without = withoutBucket(mc, zombie, renderer)) {
                int changed = difference(actual, without, 0, actual.getHeight());
                check(changed == 0, "An invisible bucket zombie must not reveal its species bucket");
                report.addProperty("invisible_changed_pixels", changed);
            }
            before.unchanged(zombie);
            report.addProperty("all_render_cases_preserve_equipment", true);
            report.addProperty("rotated_head_transform_copied", true);
            report.addProperty("registered_species_is_adult_only", true);
            report.addProperty("status", "passed"); return report;
        } finally { zombie.discard(); ordinary.discard(); }
    }

    private static void prepare(Minecraft mc, Zombie zombie) {
        zombie.setPos(mc.player.position()); zombie.setNoAi(true); zombie.tickCount = 0;
        zombie.setYRot(0); zombie.yRotO = 0; zombie.setXRot(0); zombie.xRotO = 0;
        zombie.yBodyRot = zombie.yBodyRotO = zombie.yHeadRot = zombie.yHeadRotO = 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> layers(LivingEntityRenderer<?, ?> renderer) throws Exception {
        Field field = LivingEntityRenderer.class.getDeclaredField("layers"); field.setAccessible(true);
        return (List<Object>)field.get(renderer);
    }

    private static NativeImage withoutBucket(Minecraft mc, Zombie zombie, LivingEntityRenderer<?, ?> renderer) throws Exception {
        List<Object> layers = layers(renderer), original = new ArrayList<>(layers);
        try { layers.removeIf(BucketZombieLayer.class::isInstance); return MobRenderRegressionAudit.capture(mc, zombie, false, .35F); }
        finally { layers.clear(); layers.addAll(original); }
    }

    private static void sameHead(ModelPart expected, ModelPart actual) {
        check(actual.x == expected.x && actual.y == expected.y && actual.z == expected.z
                        && actual.xRot == expected.xRot && actual.yRot == expected.yRot && actual.zRot == expected.zRot
                        && actual.xScale == expected.xScale && actual.yScale == expected.yScale && actual.zScale == expected.zScale,
                "The bucket must copy the final head position, yaw, pitch, roll and scale");
    }

    private static int difference(NativeImage a, NativeImage b, int firstRow, int lastRow) {
        int changed = 0;
        for (int y = firstRow; y < lastRow; y++) for (int x = 0; x < a.getWidth(); x++) {
            int p = a.getPixelRGBA(x, y), q = b.getPixelRGBA(x, y);
            int delta = Math.abs((p & 255) - (q & 255)) + Math.abs((p >>> 8 & 255) - (q >>> 8 & 255))
                    + Math.abs((p >>> 16 & 255) - (q >>> 16 & 255));
            if (delta > 12) changed++;
        }
        return changed;
    }

    private static final class Equipment {
        final ItemStack[] references = new ItemStack[EquipmentSlot.values().length];
        final ItemStack[] copies = new ItemStack[references.length];
        Equipment(Zombie zombie) { for (var slot : EquipmentSlot.values()) {
            references[slot.ordinal()] = zombie.getItemBySlot(slot); copies[slot.ordinal()] = references[slot.ordinal()].copy();
        } }
        void unchanged(Zombie zombie) { for (var slot : EquipmentSlot.values())
            check(zombie.getItemBySlot(slot) == references[slot.ordinal()] && ItemStack.matches(zombie.getItemBySlot(slot), copies[slot.ordinal()]),
                    "Rendering must retain the exact equipment instance and components in " + slot);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
