package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.client.gear.ChestGemLayer;
import io.github.ningmeng123.modernite.client.entity.VibraniumCosmicShaders;
import io.github.ningmeng123.modernite.client.entity.VibraniumGildingLayer;
import io.github.ningmeng123.modernite.entity.ArmorAuraRules;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemSockets;
import io.github.ningmeng123.modernite.gear.GemType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Complete native GPU renders of equipped gems collected into a chest medal. @author NingMeng123 */
public final class ChestGemClientAudit {
    private static final int WIDTH = 512, HEIGHT = 512;
    private static final List<GemType> TYPES = List.of(GemType.HEALTH, GemType.PROTECTION, GemType.RECOVER,
            GemType.DURABILITY, GemType.FIREPROOF, GemType.EXPLOSION_PROOF, GemType.NUTRITION, GemType.HEALTH);
    private static final java.util.Map<GemType, Integer> TYPE_RIM_COLORS = new java.util.EnumMap<>(GemType.class);
    private ChestGemClientAudit() {}

    public static JsonObject capture(Minecraft mc, Path folder) throws Exception {
        Files.createDirectories(folder);
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("scope", "Native combined armor/weapon chest medals, stable random 5x5 selection and animated light");
        JsonArray samples = new JsonArray(); report.add("samples", samples);
        int index = 0;
        try (NativeImage preview = new NativeImage(WIDTH * 4, HEIGHT * 2, false)) {
            for (PlayerSkin.Model shape : PlayerSkin.Model.values()) {
                PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, shape, false);
                RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")) {
                    @Override public PlayerSkin getSkin() { return skin; }
                };
                try {
                    stage(mc, player);
                    ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
                    player.setItemSlot(EquipmentSlot.CHEST, chest);
                    var sockets = fullSockets();
                    JsonObject facts = new JsonObject(); samples.add(facts);
                    facts.addProperty("skin", shape.id());
                    check(mc.getEntityRenderDispatcher().getSkinMap().get(shape)
                                    == mc.getEntityRenderDispatcher().getRenderer(player),
                            "The real skin map supplies the tested " + shape + " player renderer");
                    ObservedBuffers emptyVertices = new ObservedBuffers(mc);
                    try (NativeImage empty = frame(mc, player, .18F, emptyVertices)) {
                        check(emptyVertices.starVertices == 0, "An unmodified chestplate emits no star vertices");
                        empty.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-empty.png"));
                        copy(empty, preview, index, 0);
                        chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY);
                        try (NativeImage explicitEmpty = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                            check(difference(empty, explicitEmpty) == 0, "Eight empty sockets leave the exact original armor pixels");
                        }
                        chest.set(ExtremeGear.SOCKETS.get(), sockets);
                        check(ChestGemLayer.visibleSockets(chest).size() == 8, "All eight occupied authoritative sockets remain separately visible");
                        ObservedBuffers fullVertices = new ObservedBuffers(mc);
                        try (NativeImage full = frame(mc, player, .18F, fullVertices)) {
                            int changed = difference(empty, full);
                            check(changed > 100, "Eight socketed gems must visibly change the complete chest GPU image");
                            int expected = expectedVertices(chest);
                            check(fullVertices.starVertices == expected,
                                    "The registered layer emits exactly eight chest stars, expected=" + expected
                                            + ", actual=" + fullVertices.starVertices);
                            facts.addProperty("socket_count", 8);
                            facts.addProperty("changed_pixels", changed);
                            facts.addProperty("star_vertices", fullVertices.starVertices);
                            facts.addProperty("expected_star_vertices", expected);
                            facts.addProperty("distinct_chest_gem_colors", colorFamilies(empty, full));
                            check(colorFamilies(empty, full) >= 3, "Different socket types retain visibly different type-specific star colors");
                            Path png = folder.resolve("chest-gems-" + shape.id() + "-eight.png"); full.writeToFile(png);
                            facts.addProperty("screenshot", png.toString()); copy(full, preview, index, 1);
                            player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING);
                            ObservedBuffers crouchVertices = new ObservedBuffers(mc);
                            try (NativeImage crouched = frame(mc, player, .18F, crouchVertices)) {
                                check(crouchVertices.starVertices == expected, "Crouching retains all eight chest-attached star models");
                                check(difference(full, crouched) > 100, "The native crouching model visibly changes the attached chest pose");
                                crouched.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-crouched.png"));
                                copy(crouched, preview, index, 2);
                            }
                            player.setShiftKeyDown(false); player.setPose(Pose.STANDING);
                            ObservedBuffers turnVertices = new ObservedBuffers(mc);
                            try (NativeImage turned = frame(mc, player, .8F, turnVertices)) {
                                check(turnVertices.starVertices == expected, "Turning the complete body retains all attached star models");
                                turned.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-turned.png"));
                                copy(turned, preview, index, 3);
                            }
                        }
                        chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.HEALTH, 1)));
                        try (NativeImage health = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                            chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.PROTECTION, 6)));
                            try (NativeImage protection = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                                int replaced = difference(health, protection);
                                check(replaced > 5, "Changing one authoritative socket visibly replaces its star color for the gem type");
                                facts.addProperty("single_socket_replacement_changed_pixels", replaced);
                                health.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-health-one.png"));
                                protection.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-protection-six.png"));
                            }
                        }
                        chest.remove(ExtremeGear.SOCKETS.get());
                        try (NativeImage removed = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                            check(difference(empty, removed) == 0, "Removing the gems restores every original chestplate and skin pixel");
                        }
                        chest.set(ExtremeGear.SOCKETS.get(), sockets);
                        player.setInvisible(true);
                        ObservedBuffers invisibleVertices = new ObservedBuffers(mc);
                        try (NativeImage invisibleGems = frame(mc, player, .18F, invisibleVertices)) {
                            check(invisibleVertices.starVertices == expectedVertices(chest),
                                    "Native invisible wearers retain their visible armor's eight star models");
                            chest.remove(ExtremeGear.SOCKETS.get());
                            ObservedBuffers invisibleEmptyVertices = new ObservedBuffers(mc);
                            try (NativeImage invisibleEmpty = frame(mc, player, .18F, invisibleEmptyVertices)) {
                                int changed = difference(invisibleEmpty, invisibleGems);
                                check(invisibleEmptyVertices.starVertices == 0 && changed > 100,
                                        "Invisible bodies keep native armor and its clearly visible colored socket decorations");
                                invisibleGems.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-invisible-eight.png"));
                                invisibleEmpty.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-invisible-empty.png"));
                                facts.addProperty("invisible_armor_gems_changed_pixels", changed);
                            }
                        }
                        player.setInvisible(false);
                        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.set(ExtremeGear.SOCKETS.get(), sockets);
                        player.setItemSlot(EquipmentSlot.HEAD, helmet);
                        ObservedBuffers helmetStars = new ObservedBuffers(mc);
                        try (NativeImage helmetOnly = frame(mc, player, .18F, helmetStars)) {
                            check(helmetStars.starVertices == 8 * ChestGemLayer.STAR_VERTICES,
                                    "Helmet gems appear at the chest even without a worn chestplate");
                            chestRegion(helmetStars);
                            helmetOnly.writeToFile(folder.resolve("medal-stars-" + shape.id() + "-helmet-gems-at-chest.png"));
                        }
                        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        player.setItemSlot(EquipmentSlot.CHEST, helmet);
                        ObservedBuffers wrongSlot = new ObservedBuffers(mc);
                        try (NativeImage ignored = frame(mc, player, .18F, wrongSlot)) {
                            check(wrongSlot.starVertices == 0, "A helmet placed in the wrong equipment slot cannot supply chest stars");
                        }
                    }
                    facts.addProperty("empty_and_removed_restore_exact_pixels", true);
                    facts.addProperty("crouch_and_body_turn", true);
                    facts.addProperty("invisible_armor_and_correct_equipment_slot_controls", true);
                } finally { player.discard(); }
                index++;
            }
            Path png = folder.resolve("chest-gems-preview.png"); preview.writeToFile(png);
            report.addProperty("preview", png.toString());
            report.addProperty("preview_columns", "empty chest; eight gems; crouching; turned body");
        }
        var stand = EntityType.ARMOR_STAND.create(mc.level);
        check(stand != null, "The real armor stand renderer control can be created");
        try {
            stage(mc, stand);
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE); stand.setItemSlot(EquipmentSlot.CHEST, chest);
            try (NativeImage empty = frame(mc, stand, .18F, new ObservedBuffers(mc))) {
                chest.set(ExtremeGear.SOCKETS.get(), fullSockets());
                ObservedBuffers standVertices = new ObservedBuffers(mc);
                try (NativeImage full = frame(mc, stand, .18F, standVertices)) {
                    check(standVertices.starVertices == expectedVertices(chest), "An armor stand emits the same eight chest stars");
                    check(difference(empty, full) > 100, "Armor-stand chest gems reach the actual GPU");
                    Path png = folder.resolve("chest-gems-armor-stand.png"); full.writeToFile(png);
                    report.addProperty("armor_stand_screenshot", png.toString());
                    report.addProperty("armor_stand_vertices", standVertices.starVertices);
                }
            }
        } finally { stand.discard(); }
        report.add("compact_count_layouts", countLayouts(mc, folder));
        report.add("type_star_colors", typeColors(mc, folder));
        report.add("translucent_glow_effects", translucentGlow(mc, folder));
        report.add("combined_equipment_medals", combinedEquipmentMedals(mc, folder));
        report.add("vibranium_protection_v", cosmicArmor(mc, folder));
        report.addProperty("status", "passed_actual_gpu_rendering");
        return report;
    }

    private static JsonObject countLayouts(Minecraft mc, Path folder) throws Exception {
        PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, PlayerSkin.Model.WIDE, false);
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        JsonObject report = new JsonObject();
        JsonArray samples = new JsonArray(); report.add("samples", samples);
        try {
            stage(mc, player);
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            try (NativeImage empty = frame(mc, player, .18F, new ObservedBuffers(mc));
                    NativeImage gallery = new NativeImage(WIDTH * 4, HEIGHT * 2, false)) {
                for (int count = 1; count <= 8; count++) {
                    GemSockets sockets = GemSockets.EMPTY;
                    for (int slot = 0; slot < count; slot++)
                        sockets = sockets.with(slot, code(TYPES.get(slot), slot % 6 + 1));
                    chest.set(ExtremeGear.SOCKETS.get(), sockets);
                    ObservedBuffers observed = new ObservedBuffers(mc);
                    try (NativeImage actual = frame(mc, player, .18F, observed)) {
                        int changed = difference(empty, actual);
                        JsonObject rows = medalRows(observed, count);
                        check(changed > 20, "The medal " + count + "-gem layout remains readable in the actual player image");
                        check(observed.starVertices == expectedVertices(chest),
                                "The compact " + count + "-gem layout renders every occupied socket star exactly once");
                        Path png = folder.resolve("compact-gems-" + count + ".png"); actual.writeToFile(png);
                        copy(actual, gallery, (count - 1) / 4, (count - 1) % 4);
                        JsonObject facts = new JsonObject(); samples.add(facts);
                        facts.addProperty("occupied_sockets", count); facts.addProperty("changed_pixels", changed);
                        facts.addProperty("star_vertices", observed.starVertices); facts.addProperty("screenshot", png.toString());
                        facts.add("actual_emitted_medal_rows", rows);
                    }
                }
                Path png = folder.resolve("medal-stars-counts.png"); gallery.writeToFile(png);
                report.addProperty("preview", png.toString());
                report.addProperty("preview_order", "one through eight occupied sockets; each chest medal uses up to five stars per row");
                report.addProperty("rows_verified_from_actual_emitted_geometry", true);
                int first = code(GemType.HEALTH, 1), second = code(GemType.PROTECTION, 2);
                chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, first).with(1, second));
                try (NativeImage adjacent = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                    // Only slot occupancy changes; the ordered pair and authoritative stored codes remain intact.
                    chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, first).with(7, second));
                    ObservedBuffers observed = new ObservedBuffers(mc);
                    try (NativeImage sparse = frame(mc, player, .18F, observed)) {
                        check(observed.starVertices == expectedVertices(chest),
                                "Separated authoritative socket indices still draw exactly their two chest stars");
                        check(difference(adjacent, sparse) == 0,
                                "Empty socket holes cannot scatter an identical ordered gem pair across the chest");
                        Path sparsePng = folder.resolve("compact-gems-sparse-two.png"); sparse.writeToFile(sparsePng);
                        report.addProperty("sparse_two_screenshot", sparsePng.toString());
                        report.addProperty("sparse_and_adjacent_pair_restore_exact_pixels", true);
                    }
                }
            }
            report.addProperty("status", "passed_actual_gpu_rendering"); return report;
        } finally { player.discard(); }
    }

    private static JsonObject typeColors(Minecraft mc, Path folder) throws Exception {
        PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, PlayerSkin.Model.WIDE, false);
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        JsonObject report = new JsonObject();
        JsonArray samples = new JsonArray(); report.add("samples", samples);
        List<NativeImage> rendered = new java.util.ArrayList<>();
        try {
            stage(mc, player);
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            try (NativeImage empty = frame(mc, player, .18F, new ObservedBuffers(mc));
                    NativeImage gallery = new NativeImage(WIDTH * 5, HEIGHT * 2, false)) {
                int index = 0;
                for (GemType type : GemType.values()) {
                    chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(type, 1)));
                    ObservedBuffers observed = new ObservedBuffers(mc);
                    NativeImage actual = frame(mc, player, .18F, observed);
                    rendered.add(actual);
                    check(observed.starVertices == expectedVertices(chest),
                            "Each gem category renders exactly one five-point star: " + type.id);
                    TYPE_RIM_COLORS.put(type, observed.baseColors.get(0));
                    check(difference(empty, actual) > 20, "The single " + type.id + " star reaches the real GPU image");
                    for (int earlier = 0; earlier < rendered.size() - 1; earlier++)
                        check(difference(rendered.get(earlier), actual) > 5,
                                "Every gem category has a visibly distinct rendered star color: " + type.id);
                    Path png = folder.resolve("star-color-" + type.id + ".png"); actual.writeToFile(png);
                    copy(actual, gallery, index / 5, index % 5);
                    JsonObject facts = new JsonObject(); samples.add(facts);
                    facts.addProperty("gem_type", type.id); facts.addProperty("star_vertices", observed.starVertices);
                    facts.addProperty("star_rgb", String.format("#%06X", ChestGemLayer.starColor(type) & 0xFFFFFF));
                    facts.addProperty("screenshot", png.toString());
                    chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(type, 6)));
                    try (NativeImage highestRank = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                        check(difference(actual, highestRank) == 0,
                                "Ranks one through six use the same type color and common star silhouette: " + type.id);
                    }
                    facts.addProperty("rank_one_and_six_identical_pixels", true);
                    index++;
                }
                Path png = folder.resolve("star-colors.png"); gallery.writeToFile(png);
                report.addProperty("preview", png.toString());
                report.addProperty("preview_order", "GemType enum order, five columns and two rows");
            }
            report.addProperty("all_ten_type_colors_visibly_distinct", true);
            report.addProperty("rank_changes_preserve_star_color_and_shape", true);
            report.addProperty("status", "passed_actual_gpu_rendering"); return report;
        } finally { rendered.forEach(NativeImage::close); player.discard(); }
    }

    private static JsonObject translucentGlow(Minecraft mc, Path folder) throws Exception {
        PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, PlayerSkin.Model.WIDE, false);
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        JsonObject facts = new JsonObject();
        try {
            stage(mc, player);
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chest.set(ExtremeGear.SOCKETS.get(), fullSockets());
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            ObservedBuffers observed = new ObservedBuffers(mc);
            player.tickCount = 120;
            try (NativeImage peak = frame(mc, player, .18F, observed)) {
                check(observed.starVertices == expectedVertices(chest), "Translucent stars retain the exact complete geometry count");
                check(observed.translucentVertices > 0 && observed.maxAlpha < 255,
                        "Every star pass uses real non-opaque vertex alpha rather than an opaque color imitation");
                check(observed.glowVertices == 8 * ChestGemLayer.GLOW_VERTICES,
                        "Each occupied socket has one explicitly emissive glow pass");
                check(observed.fullBrightGlowVertices == observed.glowVertices,
                        "The entire glow pass uses the full-bright lightmap independently of the chestplate");
                facts.addProperty("minimum_alpha", observed.minAlpha);
                facts.addProperty("maximum_alpha", observed.maxAlpha);
                facts.addProperty("translucent_vertices", observed.translucentVertices);
                facts.addProperty("emissive_glow_vertices", observed.glowVertices);
                check(observed.sparkleVertices == 8 * 32 && observed.sparkleAlphas.stream().anyMatch(alpha -> alpha > 0),
                        "Each star emits its small tip-glint mesh, with visible highlights where the light band passes");
                facts.addProperty("sparkle_vertices", observed.sparkleVertices);
                facts.addProperty("peak_sparkle_alpha", observed.sparkleAlphas.stream().mapToInt(Integer::intValue).max().orElse(0));
                peak.writeToFile(folder.resolve("chest-stars-glow-tick-120.png"));
                player.tickCount = 135;
                ObservedBuffers midpointVertices = new ObservedBuffers(mc);
                try (NativeImage midpoint = frame(mc, player, .18F, midpointVertices)) {
                    player.tickCount = 150;
                    ObservedBuffers troughVertices = new ObservedBuffers(mc);
                    try (NativeImage trough = frame(mc, player, .18F, troughVertices);
                            NativeImage preview = new NativeImage(WIDTH * 3, HEIGHT, false)) {
                        // Inspect only the emitted star region: native age-based arm breathing
                        // outside this box must not count as proof of animated star lighting.
                        int changed = Math.max(differenceWithin(peak, midpoint, observed),
                                Math.max(differenceWithin(midpoint, trough, observed), differenceWithin(peak, trough, observed)));
                        check(changed > 10, "The actual GPU image must show gentle light breathing across the fixed tick samples");
                        copy(peak, preview, 0, 0); copy(midpoint, preview, 0, 1); copy(trough, preview, 0, 2);
                        midpoint.writeToFile(folder.resolve("chest-stars-glow-tick-135.png"));
                        trough.writeToFile(folder.resolve("chest-stars-glow-tick-150.png"));
                        Path png = folder.resolve("chest-stars-glow-phases.png"); preview.writeToFile(png);
                        facts.addProperty("pulse_preview", png.toString());
                        facts.addProperty("pulse_changed_pixels", changed);
                        check(!observed.sparkleAlphas.equals(midpointVertices.sparkleAlphas)
                                        || !observed.sparkleAlphas.equals(troughVertices.sparkleAlphas),
                                "Actual per-star tip highlights change as the diagonal light band moves across the medal");
                        facts.addProperty("moving_tip_glints_verified_from_emitted_alpha", true);
                    }
                }
            }
            player.tickCount = 120;
            ObservedBuffers darkObserved = new ObservedBuffers(mc);
            try (NativeImage dark = frame(mc, player, .18F, darkObserved, 0)) {
                check(darkObserved.glowVertices == 8 * ChestGemLayer.GLOW_VERTICES
                                && darkObserved.fullBrightGlowVertices == darkObserved.glowVertices,
                        "Dark ambient lighting still receives the native full-bright emissive glow pass");
                chest.remove(ExtremeGear.SOCKETS.get());
                try (NativeImage darkEmpty = frame(mc, player, .18F, new ObservedBuffers(mc), 0)) {
                    int changed = differenceWithin(darkEmpty, dark, darkObserved);
                    check(changed > 20, "The emitted glow remains visibly present in the actual dark-ambient GPU image");
                    facts.addProperty("dark_ambient_star_changed_pixels", changed);
                }
                dark.writeToFile(folder.resolve("chest-stars-glow-dark.png"));
            }
            facts.addProperty("emissive_lightmap_survives_dark_ambient", true);

            // Use one star and two real armor textures to prove that the rendered body of the
            // star transmits the surface below it, rather than checking only a transparent halo.
            chest.remove(ExtremeGear.SOCKETS.get());
            try (NativeImage diamondEmpty = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.HEALTH, 1)));
                ObservedBuffers single = new ObservedBuffers(mc);
                try (NativeImage diamondStar = frame(mc, player, .18F, single)) {
                    ItemStack iron = new ItemStack(Items.IRON_CHESTPLATE);
                    player.setItemSlot(EquipmentSlot.CHEST, iron);
                    try (NativeImage ironEmpty = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                        iron.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.HEALTH, 1)));
                        try (NativeImage ironStar = frame(mc, player, .18F, new ObservedBuffers(mc));
                                NativeImage comparison = new NativeImage(WIDTH * 4, HEIGHT, false)) {
                            int cx = Math.round(single.baseStars.get(0).centerX());
                            int cy = Math.round(single.baseStars.get(0).centerY());
                            int transmitted = 0, changedCenters = 0;
                            for (int y = Math.max(0, cy - 1); y <= Math.min(HEIGHT - 1, cy + 1); y++)
                                for (int x = Math.max(0, cx - 1); x <= Math.min(WIDTH - 1, cx + 1); x++) {
                                    int de = diamondEmpty.getPixelRGBA(x, y), ie = ironEmpty.getPixelRGBA(x, y);
                                    int ds = diamondStar.getPixelRGBA(x, y), is = ironStar.getPixelRGBA(x, y);
                                    if (delta(de, ds) <= 12 || delta(ie, is) <= 12) continue;
                                    changedCenters++;
                                    if (delta(de, ie) < 25 || delta(ds, is) < 8) continue;
                                    long direction = 0;
                                    for (int shift : new int[]{0, 8, 16})
                                        direction += ((de >>> shift & 255) - (ie >>> shift & 255))
                                                * ((ds >>> shift & 255) - (is >>> shift & 255));
                                    if (direction > 0) transmitted++;
                                }
                            check(changedCenters > 0 && transmitted > 0,
                                    "Actual pixels inside the star retain a visible, same-direction change from the native armor texture beneath it");
                            copy(diamondEmpty, comparison, 0, 0); copy(diamondStar, comparison, 0, 1);
                            copy(ironEmpty, comparison, 0, 2); copy(ironStar, comparison, 0, 3);
                            Path png = folder.resolve("chest-stars-transparency-armor-comparison.png"); comparison.writeToFile(png);
                            facts.addProperty("armor_comparison", png.toString());
                            facts.addProperty("star_center_transmitting_pixels", transmitted);
                            facts.addProperty("star_center_changed_pixels", changedCenters);
                            facts.addProperty("sample_center_x", cx); facts.addProperty("sample_center_y", cy);
                        }
                    }
                }
            }
            facts.addProperty("status", "passed_actual_gpu_rendering");
            return facts;
        } finally { player.discard(); }
    }

    private static JsonObject combinedEquipmentMedals(Minecraft mc, Path folder) throws Exception {
        PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, PlayerSkin.Model.WIDE, false);
        UUID identity = UUID.fromString("a48278cc-5fb9-4dc2-a1b7-69b9d485ac13");
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(identity, "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        EquipmentSlot[] slots = {EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        var items = new net.minecraft.world.item.Item[]{Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET,
                Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.IRON_SWORD};
        java.util.Map<EquipmentSlot, ItemStack> gear = new java.util.EnumMap<>(EquipmentSlot.class);
        JsonObject facts = new JsonObject();
        JsonArray sources = new JsonArray(); facts.add("individual_equipment_sources", sources);
        try {
            stage(mc, player);
            for (int index = 0; index < slots.length; index++) {
                ItemStack piece = new ItemStack(items[index]);
                gear.put(slots[index], piece); player.setItemSlot(slots[index], piece);
            }
            try (NativeImage empty = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                for (EquipmentSlot slot : slots) {
                    gear.get(slot).set(ExtremeGear.SOCKETS.get(), sourceSockets(slot, 5));
                    ObservedBuffers isolated = new ObservedBuffers(mc);
                    try (NativeImage actual = frame(mc, player, .18F, isolated)) {
                        check(isolated.starVertices == 5 * ChestGemLayer.STAR_VERTICES,
                                "Every stored gem on equipped " + slot.getName() + " contributes one chest star");
                        chestRegion(isolated); medalRows(isolated, 5);
                        check(difference(empty, actual) > 50,
                                "Gems from " + slot.getName() + " visibly reach the actual chest GPU image");
                        check(differenceOutside(empty, actual, isolated, 3) == 0,
                                "Changing " + slot.getName() + " sockets changes only chest pixels, never that equipment's native surface");
                        JsonObject source = new JsonObject(); sources.add(source);
                        source.addProperty("slot", slot.getName()); source.addProperty("chest_stars", 5);
                        source.addProperty("changed_pixels", difference(empty, actual));
                        source.addProperty("changed_pixels_outside_chest", 0);
                        actual.writeToFile(folder.resolve("combined-medals-source-" + slot.getName() + ".png"));
                    }
                    check(ExtremeGear.sockets(gear.get(slot)).equals(sourceSockets(slot, 5)),
                            "Drawing a chest medal never changes its source " + slot.getName() + " socket data");
                    gear.get(slot).remove(ExtremeGear.SOCKETS.get());
                }
                for (EquipmentSlot slot : slots) gear.get(slot).set(ExtremeGear.SOCKETS.get(), sourceSockets(slot, 5));
                ObservedBuffers full = new ObservedBuffers(mc);
                try (NativeImage decorated = frame(mc, player, .18F, full)) {
                    check(full.starVertices == 25 * ChestGemLayer.STAR_VERTICES,
                            "Five gems from each of four armor pieces and the main-hand weapon form exactly 25 chest stars");
                    var all = ChestGemLayer.visibleSockets(player);
                    check(all.size() == 25 && ChestGemLayer.selectSockets(all, 123).equals(all),
                            "Up to 25 occupied equipment sockets remain visible without random omission");
                    assertSelectedColors(full, all);
                    chestRegion(full);
                    facts.add("actual_emitted_medal_rows", medalRows(full, 25));
                    check(difference(empty, decorated) > 100, "The 5x5 chest medal is visible over the complete native armor set");
                    check(differenceOutside(empty, decorated, full, 3) == 0,
                            "All 25 gem decorations stay within the emitted chest bounds");
                    Path png = folder.resolve("combined-medals-full-equipment-25.png"); decorated.writeToFile(png);
                    facts.addProperty("screenshot", png.toString()); facts.addProperty("full_set_vertices", full.starVertices);
                    Path animation = folder.resolve("medal-animation"); Files.createDirectories(animation);
                    for (int frame = 0; frame < 20; frame++) {
                        player.tickCount = 100 + frame * 4;
                        ObservedBuffers animated = new ObservedBuffers(mc);
                        try (NativeImage image = frame(mc, player, .18F, animated)) {
                            check(animated.starVertices == full.starVertices && animated.baseColors.equals(full.baseColors),
                                    "Every animation frame retains the same 25 chest stars and equipment sources");
                            image.writeToFile(animation.resolve(String.format("frame-%03d.png", frame)));
                        }
                    }
                    player.tickCount = 120;
                    facts.addProperty("animation_frame_folder", animation.toString());
                    facts.addProperty("animation_frames", 20); facts.addProperty("animation_tick_step", 4);
                    player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING);
                    ObservedBuffers crouch = new ObservedBuffers(mc);
                    try (NativeImage crouched = frame(mc, player, .18F, crouch)) {
                        check(crouch.starVertices == full.starVertices && difference(decorated, crouched) > 100,
                                "All 25 chest stars follow native crouching with unchanged equipment sources");
                        crouched.writeToFile(folder.resolve("combined-medals-crouched.png"));
                    }
                    player.setShiftKeyDown(false); player.setPose(Pose.STANDING);
                    ObservedBuffers turnedVertices = new ObservedBuffers(mc);
                    try (NativeImage turned = frame(mc, player, .8F, turnedVertices)) {
                        check(turnedVertices.starVertices == full.starVertices,
                                "The full 5x5 medal follows the turned body without attaching to hands or legs");
                        turned.writeToFile(folder.resolve("combined-medals-turned.png"));
                    }
                }
                for (EquipmentSlot slot : slots) gear.get(slot).remove(ExtremeGear.SOCKETS.get());
                ObservedBuffers removed = new ObservedBuffers(mc);
                try (NativeImage cleared = frame(mc, player, .18F, removed)) {
                    check(removed.starVertices == 0 && difference(empty, cleared) == 0,
                            "Clearing every source socket component restores every original armor, weapon and skin pixel");
                }
            }
            for (EquipmentSlot slot : slots) gear.get(slot).set(ExtremeGear.SOCKETS.get(), sourceSockets(slot, 8));
            var candidates = ChestGemLayer.visibleSockets(player);
            check(candidates.size() == 40, "All forty authoritative sockets are collected before the 25-star display limit");
            List<ChestGemLayer.SocketGem> expected = new java.util.ArrayList<>();
            for (EquipmentSlot slot : slots) expected.addAll(ChestGemLayer.visibleSockets(gear.get(slot), slot));
            check(candidates.equals(expected), "Collected sockets preserve each equipped source and its original occupied-hole order");
            long seed = identity.getMostSignificantBits() ^ identity.getLeastSignificantBits();
            var selected = ChestGemLayer.selectSockets(candidates, seed);
            check(selected.size() == 25 && new java.util.HashSet<>(selected).size() == 25,
                    "The forty-gem control selects exactly 25 distinct socket records without replacement");
            check(selected.equals(ChestGemLayer.selectSockets(candidates, seed)),
                    "The same identity and same equipment always retain exactly the same random selection");
            int last = -1;
            for (var gem : selected) {
                int original = candidates.indexOf(gem);
                check(original > last, "Randomly selected stars retain their authoritative source/slot display order");
                last = original;
            }
            java.util.Set<ChestGemLayer.SocketGem> reached = new java.util.HashSet<>();
            for (long variation = 0; variation < 512; variation++) {
                var sample = ChestGemLayer.selectSockets(candidates, variation);
                check(sample.size() == 25 && new java.util.HashSet<>(sample).size() == 25 && candidates.containsAll(sample),
                        "Every sampled seed chooses 25 actual socket records without repetition");
                reached.addAll(sample);
            }
            check(reached.size() == 40, "Every source socket can be chosen across stable random identity seeds");
            ObservedBuffers random = new ObservedBuffers(mc);
            try (NativeImage actual = frame(mc, player, .18F, random)) {
                check(random.starVertices == 25 * ChestGemLayer.STAR_VERTICES,
                        "The actual native renderer caps a forty-gem set at exactly 25 stars");
                assertSelectedColors(random, selected); chestRegion(random); medalRows(random, 25);
                actual.writeToFile(folder.resolve("combined-medals-random-25-of-40.png"));
                player.tickCount = 135;
                ObservedBuffers laterVertices = new ObservedBuffers(mc);
                try (NativeImage later = frame(mc, player, .18F, laterVertices)) {
                    check(laterVertices.starVertices == random.starVertices && laterVertices.baseColors.equals(random.baseColors),
                            "Animation time changes light while preserving the exact random star type sequence");
                    for (int index = 0; index < 25; index++) {
                        check(Math.abs(laterVertices.baseStars.get(index).centerX() - random.baseStars.get(index).centerX()) < .01F
                                        && Math.abs(laterVertices.baseStars.get(index).centerY() - random.baseStars.get(index).centerY()) < .01F,
                                "Animation never reshuffles or moves a selected chest star");
                    }
                    check(differenceWithin(actual, later, random) > 10,
                            "The fixed selected 25-star group shows actual animated light across native GPU frames");
                    later.writeToFile(folder.resolve("combined-medals-random-25-animated.png"));
                }
            }
            player.tickCount = 120;
            for (EquipmentSlot slot : slots) gear.get(slot).remove(ExtremeGear.SOCKETS.get());
            ItemStack sword = gear.get(EquipmentSlot.MAINHAND);
            sword.set(ExtremeGear.SOCKETS.get(), sourceSockets(EquipmentSlot.MAINHAND, 5));
            ObservedBuffers weapon = new ObservedBuffers(mc);
            try (NativeImage ignored = frame(mc, player, .18F, weapon)) {
                check(weapon.starVertices == 5 * ChestGemLayer.STAR_VERTICES, "The held socketed weapon supplies its five chest stars");
            }
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            pickaxe.set(ExtremeGear.SOCKETS.get(), sourceSockets(EquipmentSlot.MAINHAND, 2));
            player.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
            ObservedBuffers switched = new ObservedBuffers(mc);
            try (NativeImage ignored = frame(mc, player, .18F, switched)) {
                check(switched.starVertices == 2 * ChestGemLayer.STAR_VERTICES,
                        "Switching the main hand reads the replacement tool's two sockets instead of the old weapon");
            }
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            checkNoStars(mc, player, "Unequipping the main-hand weapon immediately removes its chest medal stars");
            ItemStack helmet = gear.get(EquipmentSlot.HEAD);
            helmet.set(ExtremeGear.SOCKETS.get(), sourceSockets(EquipmentSlot.HEAD, 8));
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.CHEST, helmet);
            checkNoStars(mc, player, "A helmet misplaced in the chest slot cannot supply any chest stars");
            player.setItemSlot(EquipmentSlot.CHEST, gear.get(EquipmentSlot.CHEST));
            player.setItemSlot(EquipmentSlot.MAINHAND, helmet);
            checkNoStars(mc, player, "An armor item carried in the main hand cannot be counted as worn armor or a weapon");
            for (var item : new net.minecraft.world.item.Item[]{Items.SHIELD, Items.BOW, Items.APPLE}) {
                ItemStack unsupported = new ItemStack(item); unsupported.set(ExtremeGear.SOCKETS.get(), fullSockets());
                player.setItemSlot(EquipmentSlot.MAINHAND, unsupported);
                checkNoStars(mc, player, "Unsupported hand items cannot create fake socket decorations: " + item);
            }
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.OFFHAND, pickaxe);
            checkNoStars(mc, player, "The inactive off hand does not add a second weapon's chest stars");
            facts.addProperty("all_five_equipment_sources_visible_at_chest", true);
            facts.addProperty("outside_chest_pixels_unchanged", true);
            facts.addProperty("five_rows_of_five", true);
            facts.addProperty("collected_candidates", candidates.size());
            facts.addProperty("randomly_selected_stars", selected.size());
            facts.addProperty("seeds_checked", 512);
            facts.addProperty("candidates_reached_across_seeds", reached.size());
            facts.addProperty("random_selection_stable_across_ticks", true);
            facts.addProperty("actual_star_colors_match_selected_sockets", true);
            facts.addProperty("main_hand_changes_update_stars", true);
            facts.addProperty("clearing_all_sockets_restores_exact_pixels", true);
            facts.addProperty("wrong_slot_and_unsupported_items_ignored", true);
            facts.addProperty("status", "passed_actual_gpu_rendering");
            return facts;
        } finally { player.discard(); }
    }

    private static void checkNoStars(Minecraft mc, LivingEntity entity, String message) {
        ObservedBuffers observed = new ObservedBuffers(mc);
        try (NativeImage ignored = frame(mc, entity, .18F, observed)) { check(observed.starVertices == 0, message); }
    }

    private static GemSockets sourceSockets(EquipmentSlot source, int count) {
        GemType[] types = switch (source) {
            case MAINHAND -> new GemType[]{GemType.DAMAGE, GemType.EFFICIENCY, GemType.DURABILITY};
            case FEET -> new GemType[]{GemType.SPEED, GemType.HEALTH, GemType.PROTECTION, GemType.RECOVER, GemType.DURABILITY};
            default -> new GemType[]{GemType.HEALTH, GemType.PROTECTION, GemType.RECOVER, GemType.NUTRITION, GemType.DURABILITY};
        };
        int rank = switch (source) { case CHEST -> 1; case HEAD -> 2; case LEGS -> 3; case FEET -> 4; default -> 5; };
        GemSockets sockets = GemSockets.EMPTY;
        for (int index = 0; index < count; index++) sockets = sockets.with(index, code(types[index % types.length], rank));
        return sockets;
    }

    private static void assertSelectedColors(ObservedBuffers observed, List<ChestGemLayer.SocketGem> selected) {
        check(observed.baseColors.size() == selected.size(), "Each selected socket has exactly one observed base-star color");
        for (int index = 0; index < selected.size(); index++)
            check(observed.baseColors.get(index).equals(TYPE_RIM_COLORS.get(selected.get(index).type())),
                    "The actual emitted star color matches its selected source socket at display position " + index);
    }

    private static void chestRegion(ObservedBuffers observed) {
        check(observed.minX > 180F && observed.maxX < 350F && observed.minY > 145F && observed.maxY < 310F,
                "Every emitted base/glow/sparkle vertex lies within the standing native player's chest region");
    }

    /** Verify the rendered star geometry, independent of the production coordinate helper. */
    private static JsonObject medalRows(ObservedBuffers observed, int count) {
        check(observed.baseStars.size() == count, "Each actual base-star mesh is observed separately");
        int rows = (count + 4) / 5;
        check(rows <= 5, "At most five actual medal rows are emitted");
        float referenceCenter = 0, previousY = 0, spacing = 0, gap = 0;
        JsonArray rowCounts = new JsonArray();
        for (int row = 0; row < rows; row++) {
            int rowSize = Math.min(5, count - row * 5); rowCounts.add(rowSize);
            float rowY = observed.baseStars.get(row * 5).centerY(), centerX = 0;
            for (int column = 0; column < rowSize; column++) {
                int index = row * 5 + column; StarBounds b = observed.baseStars.get(index);
                check(Math.abs(b.centerY() - rowY) < .5F, "Stars within each actual medal row share a horizontal line");
                centerX += b.centerX();
                if (column > 0) {
                    StarBounds previous = observed.baseStars.get(index - 1);
                    float dx = b.centerX() - previous.centerX();
                    check(Math.abs(dx) > b.maxX - b.minX + 2F, "Neighboring actual star silhouettes have a visible gap");
                    if (spacing == 0) spacing = dx;
                    else check(Math.abs(dx - spacing) < .5F, "All actual medal columns use consistent spacing");
                }
            }
            centerX /= rowSize;
            if (row == 0) referenceCenter = centerX;
            else {
                check(Math.abs(centerX - referenceCenter) < .5F, "Every partly-filled row is centered beneath the preceding rows");
                float dy = rowY - previousY;
                check(dy > 10F, "Actual medal rows retain a readable vertical gap");
                if (gap == 0) gap = dy;
                else check(Math.abs(dy - gap) < .5F, "All actual medal rows use a consistent vertical interval");
            }
            previousY = rowY;
        }
        JsonObject facts = new JsonObject();
        facts.add("stars_in_each_row", rowCounts); facts.addProperty("row_count", rows);
        facts.addProperty("row_gap_pixels", gap); facts.addProperty("column_spacing_pixels", Math.abs(spacing));
        facts.addProperty("verified_from_emitted_geometry", true);
        return facts;
    }

    private static JsonObject cosmicArmor(Minecraft mc, Path folder) throws Exception {
        PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, PlayerSkin.Model.WIDE, false);
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        var priorClock = Util.timeSource;
        long fixedNanos = Util.getNanos();
        try {
            stage(mc, player);
            var protection = mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
            var slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            var pieces = new String[]{"helmet", "chestplate", "leggings", "boots"};
            for (int index = 0; index < slots.length; index++) {
                var id = ResourceLocation.fromNamespaceAndPath("mite", "vibranium_" + pieces[index]);
                check(BuiltInRegistries.ITEM.containsKey(id), "Native vibranium armor exists: " + id);
                ItemStack armor = new ItemStack(BuiltInRegistries.ITEM.get(id)); armor.enchant(protection, 5);
                player.setItemSlot(slots[index], armor);
            }
            check(ArmorAuraRules.fullProtectionFive(player), "The real complete vibranium protection-V set activates starlight");
            check(VibraniumCosmicShaders.isReady(), "The actual starlight shader is loaded for the native GPU control");
            var renderer = (net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>)mc.getEntityRenderDispatcher().getRenderer(player);
            var field = net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredField("layers"); field.setAccessible(true);
            var layers = (List<?>)field.get(renderer);
            int starlight = -1, gems = -1;
            for (int index = 0; index < layers.size(); index++) {
                if (layers.get(index) instanceof VibraniumGildingLayer<?, ?>) starlight = index;
                if (layers.get(index) instanceof ChestGemLayer<?, ?>) gems = index;
            }
            check(starlight >= 0 && gems > starlight, "Socket gems are registered after the real starlight armor layer");
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            // Use the native BufferSource here: an observing wrapper deliberately disables the
            // production starlight path, so it cannot validate armor/gem draw ordering.
            Util.timeSource = () -> fixedNanos;
            JsonObject facts = new JsonObject();
            try (NativeImage empty = frame(mc, player, .18F, null)) {
                chest.set(ExtremeGear.SOCKETS.get(), fullSockets());
                check(ChestGemLayer.visibleSockets(chest).size() == 8, "The glowing chestplate retains all eight real sockets");
                try (NativeImage full = frame(mc, player, .18F, null)) {
                    int changed = difference(empty, full);
                    check(changed > 100 && colorFamilies(empty, full) >= 3,
                            "Eight colored gems remain visibly readable over actual protection-V starlight and native glint");
                    empty.writeToFile(folder.resolve("chest-gems-vibranium-protection-v-empty.png"));
                    Path png = folder.resolve("chest-gems-vibranium-protection-v-eight.png"); full.writeToFile(png);
                    try (NativeImage comparison = new NativeImage(WIDTH * 2, HEIGHT, false)) {
                        copy(empty, comparison, 0, 0); copy(full, comparison, 0, 1);
                        comparison.writeToFile(folder.resolve("chest-gems-vibranium-protection-v-comparison.png"));
                    }
                    facts.addProperty("screenshot", png.toString()); facts.addProperty("changed_pixels", changed);
                    facts.addProperty("distinct_gem_color_families", colorFamilies(empty, full));
                }
                chest.remove(ExtremeGear.SOCKETS.get());
                try (NativeImage cleared = frame(mc, player, .18F, null)) {
                    check(difference(empty, cleared) == 0, "Removing gems leaves exactly the original fixed-clock starlight and glint pixels");
                }
            }
            facts.addProperty("native_buffer_source", true); facts.addProperty("full_protection_v_gate", true);
            facts.addProperty("starlight_shader_ready", true); facts.addProperty("starlight_layer_index", starlight);
            facts.addProperty("gem_layer_index", gems); facts.addProperty("fixed_tick", 120);
            facts.addProperty("empty_and_removed_restore_exact_pixels", true);
            return facts;
        } finally { Util.timeSource = priorClock; player.discard(); }
    }

    private static void stage(Minecraft mc, LivingEntity entity) {
        entity.setPos(mc.player.position().add(0, 10, 0)); entity.setNoGravity(true);
        entity.yBodyRot = entity.yBodyRotO = 0; entity.yHeadRot = entity.yHeadRotO = 0;
        entity.setXRot(0); entity.xRotO = 0; entity.tickCount = 120;
        entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); entity.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static GemSockets fullSockets() {
        GemSockets sockets = GemSockets.EMPTY;
        for (int slot = 0; slot < 8; slot++) sockets = sockets.with(slot, code(TYPES.get(slot), slot % 6 + 1));
        return sockets;
    }

    private static int code(GemType type, int rank) { return type.ordinal() * 6 + rank; }

    private static int expectedVertices(ItemStack chest) {
        return (int)ExtremeGear.sockets(chest).values().stream().filter(code -> code != 0).count()
                * ChestGemLayer.STAR_VERTICES;
    }

    private static NativeImage frame(Minecraft mc, LivingEntity entity, float yaw, ObservedBuffers observed) {
        return frame(mc, entity, yaw, observed, LightTexture.FULL_BRIGHT);
    }

    private static NativeImage frame(Minecraft mc, LivingEntity entity, float yaw, ObservedBuffers observed, int light) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix()); VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        var dispatcher = mc.getEntityRenderDispatcher();
        try {
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, WIDTH, HEIGHT, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1);
            PoseStack pose = new PoseStack(); pose.translate(WIDTH / 2F, HEIGHT - 28F, 50); pose.scale(240, 240, -240);
            pose.mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI + yaw));
            Lighting.setupForEntityInInventory(); dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, 1, pose,
                    observed == null ? mc.renderBuffers().bufferSource() : observed, light));
            mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            dispatcher.setRenderShadow(true); mc.renderBuffers().bufferSource().endBatch();
            view.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(projection, sorting);
            Lighting.setupFor3DItems(); mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static final class StarBounds {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        void include(float x, float y) {
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        float centerX() { return (minX + maxX) * .5F; }
        float centerY() { return (minY + maxY) * .5F; }
    }

    private static final class ObservedBuffers implements MultiBufferSource {
        final Minecraft mc; int starVertices, baseVertices;
        final List<StarBounds> baseStars = new java.util.ArrayList<>();
        final List<Integer> baseColors = new java.util.ArrayList<>();
        final List<Integer> sparkleAlphas = new java.util.ArrayList<>();
        int minAlpha = 255, maxAlpha, translucentVertices, glowVertices, fullBrightGlowVertices;
        int sparkleVertices;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        ObservedBuffers(Minecraft mc) { this.mc = mc; }
        @Override public VertexConsumer getBuffer(RenderType type) {
            VertexConsumer nativeBuffer = mc.renderBuffers().bufferSource().getBuffer(type);
            if (!type.toString().contains(ChestGemLayer.STAR_TEXTURE.toString())) return nativeBuffer;
            boolean glow = type.toString().contains("entity_translucent_emissive");
            return new VertexConsumer() {
                boolean firstBaseVertex, sparkleVertex;
                int glowStar;
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    starVertices++; if (glow) glowVertices++;
                    firstBaseVertex = false; sparkleVertex = false;
                    if (!glow) {
                        firstBaseVertex = baseVertices % (ChestGemLayer.STAR_VERTICES - ChestGemLayer.GLOW_VERTICES) == 0;
                        int group = baseVertices++ / (ChestGemLayer.STAR_VERTICES - ChestGemLayer.GLOW_VERTICES);
                        if (baseStars.size() <= group) baseStars.add(new StarBounds());
                        baseStars.get(group).include(x, y);
                    } else {
                        glowStar = (glowVertices - 1) / ChestGemLayer.GLOW_VERTICES;
                        if (sparkleAlphas.size() <= glowStar) sparkleAlphas.add(0);
                        sparkleVertex = (glowVertices - 1) % ChestGemLayer.GLOW_VERTICES >= 80;
                        if (sparkleVertex) sparkleVertices++;
                    }
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    nativeBuffer.addVertex(x, y, z); return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) {
                    minAlpha = Math.min(minAlpha, a); maxAlpha = Math.max(maxAlpha, a);
                    if (a > 0 && a < 255) translucentVertices++;
                    if (firstBaseVertex) baseColors.add(r << 16 | g << 8 | b);
                    if (sparkleVertex) sparkleAlphas.set(glowStar, Math.max(a, sparkleAlphas.get(glowStar)));
                    nativeBuffer.setColor(r, g, b, a); return this;
                }
                @Override public VertexConsumer setUv(float u, float v) { nativeBuffer.setUv(u, v); return this; }
                @Override public VertexConsumer setUv1(int u, int v) { nativeBuffer.setUv1(u, v); return this; }
                @Override public VertexConsumer setUv2(int u, int v) {
                    if (glow && u == (LightTexture.FULL_BRIGHT & 65535)
                            && v == (LightTexture.FULL_BRIGHT >>> 16 & 65535)) fullBrightGlowVertices++;
                    nativeBuffer.setUv2(u, v); return this;
                }
                @Override public VertexConsumer setNormal(float x, float y, float z) { nativeBuffer.setNormal(x, y, z); return this; }
            };
        }
    }

    private static int difference(NativeImage first, NativeImage second) {
        int changed = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) if (delta(first.getPixelRGBA(x, y), second.getPixelRGBA(x, y)) > 12) changed++;
        return changed;
    }

    private static int differenceWithin(NativeImage first, NativeImage second, ObservedBuffers bounds) {
        int changed = 0;
        int minX = Math.max(0, (int)Math.floor(bounds.minX));
        int maxX = Math.min(WIDTH - 1, (int)Math.ceil(bounds.maxX));
        int minY = Math.max(0, (int)Math.floor(bounds.minY));
        int maxY = Math.min(HEIGHT - 1, (int)Math.ceil(bounds.maxY));
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++)
            if (delta(first.getPixelRGBA(x, y), second.getPixelRGBA(x, y)) > 12) changed++;
        return changed;
    }

    private static int differenceOutside(NativeImage first, NativeImage second, ObservedBuffers bounds, int padding) {
        int changed = 0;
        int minX = (int)Math.floor(bounds.minX) - padding, maxX = (int)Math.ceil(bounds.maxX) + padding;
        int minY = (int)Math.floor(bounds.minY) - padding, maxY = (int)Math.ceil(bounds.maxY) + padding;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            if (x >= minX && x <= maxX && y >= minY && y <= maxY) continue;
            if (delta(first.getPixelRGBA(x, y), second.getPixelRGBA(x, y)) > 12) changed++;
        }
        return changed;
    }

    private static int colorFamilies(NativeImage empty, NativeImage gems) {
        java.util.Set<Integer> families = new java.util.HashSet<>();
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int p = gems.getPixelRGBA(x, y); if (delta(p, empty.getPixelRGBA(x, y)) < 30) continue;
            int r = p & 255, g = p >>> 8 & 255, b = p >>> 16 & 255;
            if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 35)
                families.add((r > g * 1.2 ? 1 : 0) | (g > b * 1.2 ? 2 : 0) | (b > r * 1.2 ? 4 : 0));
        }
        return families.size();
    }

    private static int delta(int a, int b) { return Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255)) + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255)); }
    private static void copy(NativeImage image, NativeImage sheet, int row, int column) {
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) sheet.setPixelRGBA(column * WIDTH + x, row * HEIGHT + y, image.getPixelRGBA(x, y));
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
