package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.modernite.client.entity.TimeLordSkeletonModel;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.MobAbilities;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Real resource-pack animation, native negative controls and equipped GPU samples. @author NingMeng123 */
final class TimeLordFreshAnimationsAudit {
    private TimeLordFreshAnimationsAudit() {}

    static JsonObject capture(Minecraft mc, Path directory) throws Exception {
        Files.createDirectories(directory);
        var lord = (AbstractSkeleton) ExtremeMobs.type(ExtremeKind.TIME_LORD).create(mc.level);
        require(lord != null, "The actual Time Lord factory must create a client skeleton");
        lord.setNoAi(true);lord.moveTo(mc.player.position());
        var renderer = (LivingEntityRenderer<?, ?>) mc.getEntityRenderDispatcher().getRenderer(lord);
        require(renderer.getModel() instanceof TimeLordSkeletonModel, "Time Lord must use its explicit skeleton resource-pack animation adapter");
        var model = (TimeLordSkeletonModel) renderer.getModel();
        var modelField = LivingEntityRenderer.class.getDeclaredField("model");modelField.setAccessible(true);
        boolean expected = ModList.get().isLoaded("entity_model_features") &&
                (mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("optifine/cem/skeleton.jem")).isPresent()
                        || mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("emf/cem/skeleton.jem")).isPresent());
        JsonObject report = new JsonObject();
        report.addProperty("emf_loaded", ModList.get().isLoaded("entity_model_features"));
        report.addProperty("fresh_animations_expected", expected);
        JsonArray frames = new JsonArray();report.add("frames", frames);
        try {
            for (int frame = 0; frame < 3; frame++) {
                String name = frame == 0 ? "idle" : frame == 1 ? "walking" : "melee";
                lord.tickCount = 40 + frame * 20;
                for (int step = 0; step < 6; step++) lord.walkAnimation.update(frame == 0 ? 0 : .4F, 1);
                lord.setAggressive(frame == 2);
                lord.attackAnim = frame == 2 ? .65F : 0;lord.oAttackAnim = frame == 2 ? .45F : 0;
                lord.setItemSlot(EquipmentSlot.MAINHAND, MobAbilities.item("mite:mithril_sword"));
                lord.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.CLOCK));
                JsonObject facts = new JsonObject();frames.add(facts);facts.addProperty("pose", name);
                try (NativeImage actual = MobRenderRegressionAudit.capture(mc, lord, false, .35F)) {
                    actual.writeToFile(directory.resolve("time-lord-fa-" + name + ".png"));
                    facts.addProperty("fresh_animations_applied", model.freshAnimationsApplied());
                    require(model.freshAnimationsApplied() == expected, "The actual rendered Time Lord must follow the installed skeleton animation: " + name);
                    var nativeControl = new SkeletonModel<AbstractSkeleton>(SkeletonModel.createBodyLayer().bakeRoot());
                    modelField.set(renderer, nativeControl);
                    try (NativeImage nativeImage = MobRenderRegressionAudit.capture(mc, lord, false, .35F)) {
                        int difference = difference(actual, nativeImage);facts.addProperty("native_control_changed_pixels", difference);
                        nativeImage.writeToFile(directory.resolve("time-lord-native-" + name + ".png"));
                        if (expected) require(difference > 100, "Installed skeleton CEM motion must visibly change actual GPU output: " + name);
                    } finally { modelField.set(renderer, model); }
                }
            }
            for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                lord.setItemSlot(slot, MobAbilities.item("mite:mithril_" + switch (slot) {
                    case HEAD -> "helmet";case CHEST -> "chestplate";case LEGS -> "leggings";default -> "boots";
                }));
            try (NativeImage equipped = MobRenderRegressionAudit.capture(mc, lord, false, .35F)) {
                equipped.writeToFile(directory.resolve("time-lord-fa-equipped.png"));
                require(model.freshAnimationsApplied() == expected, "Armor and held equipment must retain the same skeleton animation path");
            }
            report.addProperty("equipped_draw_succeeded", true);
            report.addProperty("status", "passed");
            return report;
        } finally { modelField.set(renderer, model);lord.discard(); }
    }

    private static int difference(NativeImage first, NativeImage second) {
        int different = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++)
            if (first.getPixelRGBA(x, y) != second.getPixelRGBA(x, y)) different++;
        return different;
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
