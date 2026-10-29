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
        report.addProperty("scope", "Native glassy embossed hexagrams, visible moving rainbow gradients, fixed type rims and independent flashes");
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
        report.add("independent_hexagram_flashes", independentHexagramFlashes(mc, folder));
        report.add("embossed_hexagram_relief", embossedHexagramRelief(mc, folder));
        report.add("glass_color_gradient", glassColorGradient(mc, folder));
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
                            "Each gem category renders exactly one six-pointed glass star: " + type.id);
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
                check(observed.sparkleVertices == 8 * ChestGemLayer.FLASH_VERTICES,
                        "Each star emits its independent flash rays and bright core, even when the current instant is quiet");
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
                        facts.addProperty("breathing_and_transparency_regression", true);
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
            // Measure the ordinary translucent face outside a brief intentionally bright flash.
            long seed = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits();
            while (ChestGemLayer.flashStrength(player.tickCount + 1F, 0, seed) > 0F) player.tickCount++;
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

    private static JsonObject independentHexagramFlashes(Minecraft mc, Path folder) throws Exception {
        PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, PlayerSkin.Model.WIDE, false);
        UUID identity = UUID.fromString("7c458b8b-d8ab-4eac-b83f-0b43b2414a77");
        long seed = identity.getMostSignificantBits() ^ identity.getLeastSignificantBits();
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(identity, "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        JsonObject facts = new JsonObject();
        try {
            stage(mc, player);
            List<List<Integer>> starts = new java.util.ArrayList<>();
            for (int index = 0; index < 25; index++) starts.add(new java.util.ArrayList<>());
            boolean[] wasLit = new boolean[25];
            float isolatedPeak = -1;
            int differentIdentitySamples = 0;
            for (int sample = 0; sample <= 4800; sample++) {
                float ticks = sample * .25F;
                for (int index = 0; index < 25; index++) {
                    float strength = ChestGemLayer.flashStrength(ticks, index, seed);
                    check(strength >= 0 && strength <= 1, "Every flash envelope remains finite and within its alpha range");
                    boolean lit = strength > 0;
                    // A flash already in progress at t=0 has an unknown negative start time.
                    if (sample > 0 && lit && !wasLit[index]) starts.get(index).add(sample);
                    wasLit[index] = lit;
                    if (strength != ChestGemLayer.flashStrength(ticks, index, seed ^ 0x5196AB9321L))
                        differentIdentitySamples++;
                }
                if (isolatedPeak < 0 && ticks > 20 && ChestGemLayer.flashStrength(ticks, 0, seed) > .99F) {
                    boolean alone = true;
                    for (int index = 1; index < 8; index++) alone &= ChestGemLayer.flashStrength(ticks, index, seed) == 0;
                    if (alone) isolatedPeak = ticks;
                }
            }
            check(isolatedPeak > 0, "A reproducible instant exists where one star peaks while its seven neighbors remain quiet");
            check(new java.util.HashSet<>(starts).size() == 25,
                    "All 25 stars have different start sequences rather than a shared row or global flash phase");
            int minimumInterval = Integer.MAX_VALUE, maximumInterval = 0;
            for (List<Integer> sequence : starts) {
                check(sequence.size() >= 8, "Every individual star flashes repeatedly during the one-minute observation");
                java.util.Set<Integer> intervals = new java.util.HashSet<>();
                for (int index = 1; index < sequence.size(); index++) {
                    int interval = sequence.get(index) - sequence.get(index - 1);
                    intervals.add(interval);
                    minimumInterval = Math.min(minimumInterval, interval);
                    maximumInterval = Math.max(maximumInterval, interval);
                    check(interval >= 200 && interval <= 560,
                            "Individual flash starts remain 2.5 to 7 seconds apart without clipped time-window edges");
                }
                check(intervals.size() > 1, "Each star uses changing intervals instead of a fixed-rate blink");
            }
            check(differentIdentitySamples > 100, "Different entity identities retain independent flash schedules");
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chest.set(ExtremeGear.SOCKETS.get(), fullSockets()); player.setItemSlot(EquipmentSlot.CHEST, chest);
            ObservedBuffers group = new ObservedBuffers(mc);
            try (NativeImage independent = frameAt(mc, player, isolatedPeak, group)) {
                check(group.starVertices == 8 * ChestGemLayer.STAR_VERTICES,
                        "All eight six-pointed stars retain their complete geometry during an isolated flash");
                check(group.sparkleAlphas.get(0) > 150,
                        "The selected native star emits a clearly bright flash core at its own peak");
                for (int index = 1; index < 8; index++) check(group.sparkleAlphas.get(index) == 0,
                        "Every neighboring star's actual flash vertices stay transparent during another star's flash");
                facts.add("actual_hexagram_geometry", hexagramShape(group));
                independent.writeToFile(folder.resolve("hexagram-independent-eight-one-flashing.png"));
            }
            chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.HEALTH, 1)));
            ObservedBuffers quietVertices = new ObservedBuffers(mc), peakVertices = new ObservedBuffers(mc), releasedVertices = new ObservedBuffers(mc);
            try (NativeImage quiet = frameAt(mc, player, isolatedPeak - 12F, quietVertices);
                    NativeImage peak = frameAt(mc, player, isolatedPeak, peakVertices);
                    NativeImage released = frameAt(mc, player, isolatedPeak + 12F, releasedVertices);
                    NativeImage comparison = new NativeImage(WIDTH * 3, HEIGHT, false)) {
                check(quietVertices.sparkleAlphas.get(0) == 0 && releasedVertices.sparkleAlphas.get(0) == 0,
                        "The native flash rays and core are completely transparent before and after the short flash");
                check(peakVertices.sparkleVertices == ChestGemLayer.FLASH_VERTICES
                                && peakVertices.sparkleAlphas.get(0) > 150,
                        "The native single-star peak includes its rays and bright core rather than only the breathing halo");
                int changed = differenceWithin(quiet, peak, peakVertices);
                check(changed > 10, "The short isolated flash visibly changes the actual GPU star image");
                StarBounds center = peakVertices.baseStars.get(0);
                int brightCore = 0, cx = Math.round(center.centerX()), cy = Math.round(center.centerY());
                for (int y = cy - 3; y <= cy + 3; y++) for (int x = cx - 3; x <= cx + 3; x++) {
                    int before = quiet.getPixelRGBA(x, y), during = peak.getPixelRGBA(x, y);
                    if (delta(before, during) > 40 && minimumChannel(during) > minimumChannel(before) + 15) brightCore++;
                }
                check(brightCore > 0, "Actual pixels show a pale bright center, not only a stronger colored outline");
                copy(quiet, comparison, 0, 0); copy(peak, comparison, 0, 1); copy(released, comparison, 0, 2);
                quiet.writeToFile(folder.resolve("hexagram-single-quiet.png"));
                peak.writeToFile(folder.resolve("hexagram-single-flash.png"));
                released.writeToFile(folder.resolve("hexagram-single-released.png"));
                Path png = folder.resolve("hexagram-flash-comparison.png"); comparison.writeToFile(png);
                facts.addProperty("flash_comparison", png.toString()); facts.addProperty("flash_changed_pixels", changed);
                facts.addProperty("bright_center_pixels", brightCore);
                facts.addProperty("peak_flash_alpha", peakVertices.sparkleAlphas.get(0));
                facts.addProperty("flash_alpha_before", quietVertices.sparkleAlphas.get(0));
                facts.addProperty("flash_alpha_after", releasedVertices.sparkleAlphas.get(0));
            }
            int previousAlpha = -1;
            JsonArray partialTickAlphas = new JsonArray();
            for (float offset : new float[]{-.75F, -.5F, -.25F}) {
                ObservedBuffers partial = new ObservedBuffers(mc);
                try (NativeImage ignored = frameAt(mc, player, isolatedPeak + offset, partial)) {
                    int alpha = partial.sparkleAlphas.get(0);
                    check(alpha > previousAlpha && alpha > 0,
                            "Actual flash vertices brighten smoothly across fractional ticks without snapping to integer ticks");
                    previousAlpha = alpha; partialTickAlphas.add(alpha);
                }
            }
            facts.add("rising_fractional_tick_alphas", partialTickAlphas);
            facts.addProperty("isolated_peak_ticks", isolatedPeak);
            facts.addProperty("independently_timed_stars", 25);
            facts.addProperty("observed_ticks", 1200);
            facts.addProperty("minimum_start_interval_ticks", minimumInterval / 4F);
            facts.addProperty("maximum_start_interval_ticks", maximumInterval / 4F);
            facts.addProperty("all_stars_have_irregular_independent_intervals", true);
            facts.addProperty("identity_changes_schedule", true);
            facts.addProperty("native_neighbors_quiet_during_one_flash", true);
            facts.addProperty("native_flash_alpha_zero_outside_event", true);
            facts.addProperty("fractional_tick_interpolation", true);
            facts.addProperty("status", "passed_actual_gpu_rendering");
            return facts;
        } finally { player.discard(); }
    }

    private static int minimumChannel(int pixel) {
        return Math.min(pixel & 255, Math.min(pixel >>> 8 & 255, pixel >>> 16 & 255));
    }

    private static JsonObject embossedHexagramRelief(Minecraft mc, Path folder) throws Exception {
        PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, PlayerSkin.Model.WIDE, false);
        UUID identity = UUID.fromString("c43cce3b-460c-420b-8885-4ad6c40a0517");
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(identity, "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        JsonObject facts = new JsonObject();
        try {
            stage(mc, player);
            long seed = identity.getMostSignificantBits() ^ identity.getLeastSignificantBits();
            while (ChestGemLayer.flashStrength(player.tickCount + 1F, 0, seed) > 0F) player.tickCount++;
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            try (NativeImage empty = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.PROTECTION, 1)));
                ObservedBuffers observed = new ObservedBuffers(mc);
                try (NativeImage front = frame(mc, player, .18F, observed)) {
                    check(observed.baseStars.size() == 1, "One source gem produces one complete embossed star");
                    facts.add("actual_relief_geometry", reliefGeometry(observed));
                    facts.add("actual_hexagram_geometry", hexagramShape(observed));
                    int edgePixels = 0;
                    java.util.Set<Integer> seen = new java.util.HashSet<>();
                    StarBounds star = observed.baseStars.get(0);
                    for (int edge = 0; edge < 12; edge++) {
                        float[] a = star.outline.get(edge), b = star.outline.get((edge + 1) % 12);
                        int cx = Math.round((a[0] + b[0]) * .5F), cy = Math.round((a[1] + b[1]) * .5F);
                        for (int y = cy - 1; y <= cy + 1; y++) for (int x = cx - 1; x <= cx + 1; x++) {
                            if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT || !seen.add(y * WIDTH + x)) continue;
                            if (delta(empty.getPixelRGBA(x, y), front.getPixelRGBA(x, y)) > 12) edgePixels++;
                        }
                    }
                    check(edgePixels > 8, "The actual GPU image contains visible contour pixels around the six-pointed rim");
                    facts.addProperty("changed_pixels_near_contour", edgePixels);
                    front.writeToFile(folder.resolve("hexagram-relief-front.png"));
                    ObservedBuffers side = new ObservedBuffers(mc);
                    try (NativeImage oblique = frame(mc, player, .9F, side);
                            NativeImage gallery = new NativeImage(WIDTH * 3, HEIGHT, false)) {
                        reliefGeometry(side);
                        check(side.starVertices == observed.starVertices,
                                "Turning the body retains the real side walls, bevel and front facets");
                        check(difference(front, oblique) > 100, "The native angled view exposes the attached shallow relief");
                        copy(empty, gallery, 0, 0); copy(front, gallery, 0, 1); copy(oblique, gallery, 0, 2);
                        oblique.writeToFile(folder.resolve("hexagram-relief-oblique.png"));
                        Path preview = folder.resolve("hexagram-relief-comparison.png"); gallery.writeToFile(preview);
                        facts.addProperty("preview", preview.toString());
                    }
                }
            }
            facts.addProperty("real_side_walls_and_bevel", true);
            facts.addProperty("separate_lit_face_normals", true);
            facts.addProperty("outline_visible_in_native_pixels", true);
            facts.addProperty("status", "passed_actual_gpu_rendering");
            return facts;
        } finally { player.discard(); }
    }

    /** Inspect the native transformed mesh, not the production depth constants. */
    private static JsonObject reliefGeometry(ObservedBuffers observed) {
        check(!observed.baseStars.isEmpty(), "Relief checks observe an actual complete star mesh");
        float sideThickness = 0, bevelDepth = 0, peakDepth = 0;
        int distinctNormals = 0, bevelRange = 0;
        for (StarBounds star : observed.baseStars) {
            check(star.vertices.size() == 192, "The emitted relief contains side, outline, bevel and face surfaces");
            MeshVertex back = star.vertices.get(0), front = star.vertices.get(3);
            float ax = front.x - back.x, ay = front.y - back.y, az = front.z - back.z;
            float thickness = (float)Math.sqrt(ax * ax + ay * ay + az * az);
            check(thickness > .15F && thickness < 2F, "The actual side wall has a visible but shallow nonzero thickness");
            ax /= thickness; ay /= thickness; az /= thickness;
            float bevel = depth(star.vertices.get(98), star, ax, ay, az);
            float peak = depth(star.vertices.get(144), star, ax, ay, az);
            check(bevel > thickness + .05F && peak > bevel + .05F,
                    "The actual bevel slopes forward and its face center rises above the inset edge");
            check(peak < 3F, "The relief remains shallow rather than becoming a large projecting badge");
            java.util.Set<String> normals = new java.util.HashSet<>();
            int darkest = Integer.MAX_VALUE, brightest = 0;
            for (int index = 0; index < star.vertices.size(); index++) {
                MeshVertex v = star.vertices.get(index);
                float length = (float)Math.sqrt(v.nx * v.nx + v.ny * v.ny + v.nz * v.nz);
                check(Math.abs(length - 1F) < .02F, "Every actual side/bevel/facet vertex has a unit lighting normal");
                normals.add(Math.round(v.nx * 100F) + ":" + Math.round(v.ny * 100F) + ":" + Math.round(v.nz * 100F));
                float forward = v.nx * ax + v.ny * ay + v.nz * az;
                if (index < 48) check(Math.abs(forward) < .02F, "Real wall normals face sideways rather than imitating a flat face");
                if (index >= 96 && index < 144) {
                    check(forward > .1F && forward < .99F, "Real bevel normals lean between the side and front surfaces");
                    int value = (v.color >> 16 & 255) + (v.color >> 8 & 255) + (v.color & 255);
                    darkest = Math.min(darkest, value); brightest = Math.max(brightest, value);
                }
                if (index >= 144) check(forward > .8F, "The inset glass facets face forward with their own sloped normals");
            }
            check(normals.size() >= 12, "The real relief uses multiple face directions instead of one flat normal");
            check(brightest - darkest > 10, "The narrow bevel contains distinct light and dark edge colors");
            sideThickness = thickness; bevelDepth = bevel; peakDepth = peak;
            distinctNormals = normals.size(); bevelRange = brightest - darkest;
        }
        JsonObject facts = new JsonObject();
        facts.addProperty("side_thickness_render_units", sideThickness);
        facts.addProperty("bevel_depth_render_units", bevelDepth);
        facts.addProperty("raised_center_depth_render_units", peakDepth);
        facts.addProperty("distinct_unit_normal_directions", distinctNormals);
        facts.addProperty("bevel_light_dark_rgb_sum_range", bevelRange);
        facts.addProperty("verified_from_emitted_geometry", true);
        return facts;
    }

    private static float depth(MeshVertex v, StarBounds star, float ax, float ay, float az) {
        return (v.x - star.originX) * ax + (v.y - star.originY) * ay + (v.z - star.originZ) * az;
    }

    private static JsonObject glassColorGradient(Minecraft mc, Path folder) throws Exception {
        JsonObject facts = new JsonObject();
        int greatestStep = 0;
        int smallestChannelRange = 255;
        for (GemType type : GemType.values()) {
            java.util.Set<Integer> palette = new java.util.HashSet<>();
            boolean differentPhase = false;
            int previous = ChestGemLayer.animatedColor(type, 0F, 0);
            int[] minimum = {255, 255, 255}, maximum = {0, 0, 0};
            for (int step = 1; step <= 960; step++) {
                float ticks = step * .25F;
                int current = ChestGemLayer.animatedColor(type, ticks, 0);
                palette.add(current);
                for (int shift : new int[]{0, 8, 16}) {
                    greatestStep = Math.max(greatestStep, Math.abs((current >> shift & 255) - (previous >> shift & 255)));
                    minimum[shift / 8] = Math.min(minimum[shift / 8], current >> shift & 255);
                    maximum[shift / 8] = Math.max(maximum[shift / 8], current >> shift & 255);
                }
                differentPhase |= current != ChestGemLayer.animatedColor(type, ticks, 1);
                previous = current;
            }
            for (int channel = 0; channel < 3; channel++) {
                int range = maximum[channel] - minimum[channel];
                check(range > 150, "Every face channel visibly traverses the rainbow rather than a small warm/cool tint");
                smallestChannelRange = Math.min(smallestChannelRange, range);
            }
            check(palette.size() > 100 && differentPhase,
                    "Each glass face continuously changes through a large palette, with different phases for neighboring stars");
            check(delta(ChestGemLayer.glassColor(type, 60F, 0, -ChestGemLayer.STAR_SIZE * .3F, 0F),
                            ChestGemLayer.glassColor(type, 60F, 0, ChestGemLayer.STAR_SIZE * .3F, 0F)) > 100,
                    "One star face has a spatial color transition at the same instant instead of a uniform flat tint");
        }
        check(greatestStep <= 3, "Quarter-tick rainbow steps remain smooth rather than abruptly switching colors");
        PlayerSkin skin = new PlayerSkin(mc.player.getSkin().texture(), null, null, null, PlayerSkin.Model.WIDE, false);
        UUID identity = UUID.fromString("b93ecfe2-97d3-40fd-8d05-97130c55bb78");
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(identity, "NingMeng123")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        List<NativeImage> frames = new java.util.ArrayList<>();
        try {
            stage(mc, player);
            long seed = identity.getMostSignificantBits() ^ identity.getLeastSignificantBits();
            java.util.Map<Integer, Integer> times = new java.util.LinkedHashMap<>();
            for (int ticks = 60; ticks <= 1440 && times.size() < 3; ticks += 60)
                if (ChestGemLayer.flashStrength(ticks, 0, seed) == 0F)
                    times.putIfAbsent(ChestGemLayer.animatedColor(GemType.PROTECTION, ticks, 0), ticks);
            check(times.size() == 3, "Three quiet gradient phases exist at the same breathing-light phase");
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.PROTECTION, 1)));
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            ObservedBuffers first = null;
            java.util.Set<Integer> emittedColors = new java.util.HashSet<>();
            int fewestSpatialColors = Integer.MAX_VALUE;
            int minimumFaceAlpha = 255, maximumFaceAlpha = 0;
            JsonArray ticksUsed = new JsonArray();
            for (int ticks : times.values()) {
                ObservedBuffers observed = new ObservedBuffers(mc);
                NativeImage actual = frameAt(mc, player, ticks, observed); frames.add(actual);
                if (first == null) first = observed;
                check(observed.sparkleAlphas.get(0) == 0, "The glass-gradient control excludes the separate white flash");
                check(observed.baseColors.equals(first.baseColors),
                        "The native gem-type rim remains exactly the same color while its glass face moves through the rainbow");
                StarBounds actualStar = observed.baseStars.get(0), firstStar = first.baseStars.get(0);
                for (int vertex = 0; vertex < 144; vertex++)
                    check(actualStar.vertices.get(vertex).color == firstStar.vertices.get(vertex).color,
                            "All actual side, outline and bevel vertex colors remain fixed for type identification");
                emittedColors.add(actualStar.vertices.get(144).color); ticksUsed.add(ticks);
                java.util.Set<Integer> spatialColors = new java.util.HashSet<>();
                for (int vertex = 144; vertex < 192; vertex++) {
                    MeshVertex face = actualStar.vertices.get(vertex);
                    spatialColors.add(face.color);
                    int alpha = face.alpha;
                    minimumFaceAlpha = Math.min(minimumFaceAlpha, alpha); maximumFaceAlpha = Math.max(maximumFaceAlpha, alpha);
                }
                check(spatialColors.size() >= 4, "The emitted native face has several simultaneous gradient colors");
                fewestSpatialColors = Math.min(fewestSpatialColors, spatialColors.size());
                actual.writeToFile(folder.resolve("glass-gradient-tick-" + ticks + ".png"));
            }
            check(emittedColors.size() == 3, "Actual native face-center vertices have three distinct rainbow colors");
            check(minimumFaceAlpha >= 90 && maximumFaceAlpha <= 135,
                    "The colored glass face remains translucent while giving the rainbow enough visible contrast");
            int changed = 0, highContrastPixels = 0, greatestNativeChannelDifference = 0;
            for (int index = 1; index < frames.size(); index++) {
                changed = Math.max(changed, differenceWithin(frames.get(0), frames.get(index), first));
                int contrast = 0;
                for (int y = Math.max(0, (int)Math.floor(first.minY)); y <= Math.min(HEIGHT - 1, (int)Math.ceil(first.maxY)); y++)
                    for (int x = Math.max(0, (int)Math.floor(first.minX)); x <= Math.min(WIDTH - 1, (int)Math.ceil(first.maxX)); x++) {
                        int before = frames.get(0).getPixelRGBA(x, y), after = frames.get(index).getPixelRGBA(x, y);
                        int channelDifference = 0;
                        for (int shift : new int[]{0, 8, 16})
                            channelDifference = Math.max(channelDifference,
                                    Math.abs((before >>> shift & 255) - (after >>> shift & 255)));
                        greatestNativeChannelDifference = Math.max(greatestNativeChannelDifference, channelDifference);
                        if (channelDifference > 40) contrast++;
                    }
                highContrastPixels = Math.max(highContrastPixels, contrast);
            }
            check(changed > 30 && highContrastPixels >= 15,
                    "At least fifteen actual star pixels change by more than forty channel levels without flash or breathing changes");
            try (NativeImage gallery = new NativeImage(WIDTH * 3, HEIGHT, false)) {
                for (int index = 0; index < frames.size(); index++) copy(frames.get(index), gallery, 0, index);
                Path preview = folder.resolve("glass-gradient-comparison.png"); gallery.writeToFile(preview);
                facts.addProperty("preview", preview.toString());
            }
            facts.add("quiet_gradient_ticks", ticksUsed);
            facts.addProperty("native_gradient_changed_pixels", changed);
            facts.addProperty("high_contrast_gradient_pixels", highContrastPixels);
            facts.addProperty("greatest_native_channel_difference", greatestNativeChannelDifference);
            facts.addProperty("minimum_channel_range_over_full_cycle", smallestChannelRange);
            facts.addProperty("minimum_simultaneous_face_vertex_colors", fewestSpatialColors);
            facts.addProperty("minimum_face_alpha", minimumFaceAlpha); facts.addProperty("maximum_face_alpha", maximumFaceAlpha);
            facts.addProperty("greatest_channel_step_per_quarter_tick", greatestStep);
            facts.addProperty("all_ten_types_gently_change_color", true);
            facts.addProperty("neighboring_stars_have_different_color_phases", true);
            facts.addProperty("native_gradient_without_flash_or_breathing_changes", true);
            facts.addProperty("spatial_rainbow_gradient_visible_at_one_instant", true);
            facts.addProperty("gem_type_rim_colors_stay_fixed", true);
            facts.addProperty("status", "passed_actual_gpu_rendering");
            return facts;
        } finally { frames.forEach(NativeImage::close); player.discard(); }
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
                    Path detailAnimation = folder.resolve("detail-animation"); Files.createDirectories(detailAnimation);
                    for (int frame = 0; frame < 60; frame++) {
                        player.tickCount = 100 + frame * 4;
                        ObservedBuffers animated = new ObservedBuffers(mc);
                        try (NativeImage image = frame(mc, player, .18F, animated)) {
                            check(animated.starVertices == full.starVertices,
                                    "Every animation frame retains the same 25 chest stars and equipment sources");
                            assertSelectedColors(animated, all);
                            image.writeToFile(animation.resolve(String.format("frame-%03d.png", frame)));
                        }
                        try (NativeImage detail = frameDetail(mc, player, .18F)) {
                            detail.writeToFile(detailAnimation.resolve(String.format("frame-%03d.png", frame)));
                            if (frame == 5) detail.writeToFile(folder.resolve("glass-stars-detail.png"));
                        }
                    }
                    player.tickCount = 120;
                    facts.addProperty("animation_frame_folder", animation.toString());
                    facts.addProperty("animation_frames", 60); facts.addProperty("animation_tick_step", 4);
                    facts.addProperty("detail_animation_frame_folder", detailAnimation.toString());
                    facts.addProperty("detail_preview", folder.resolve("glass-stars-detail.png").toString());
                    facts.addProperty("detail_native_render_zoom", 3);
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
                    check(laterVertices.starVertices == random.starVertices,
                            "Animation time changes glass color and light while preserving the random star selection");
                    assertSelectedColors(laterVertices, selected);
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
        for (int index = 0; index < selected.size(); index++) {
            int actual = observed.baseColors.get(index);
            GemType nearest = null; int nearestDistance = Integer.MAX_VALUE;
            for (var entry : TYPE_RIM_COLORS.entrySet()) {
                int distance = colorDistance(actual, entry.getValue());
                if (distance < nearestDistance) { nearest = entry.getKey(); nearestDistance = distance; }
            }
            check(nearest == selected.get(index).type(),
                    "Gentle animated color remains identifiable as the selected source gem at display position " + index
                            + ", expected=" + selected.get(index).type() + ", nearest GPU calibration=" + nearest);
        }
    }

    private static int colorDistance(int a, int b) {
        int r = (a >> 16 & 255) - (b >> 16 & 255);
        int g = (a >> 8 & 255) - (b >> 8 & 255);
        int blue = (a & 255) - (b & 255);
        return r * r + g * g + blue * blue;
    }

    private static void chestRegion(ObservedBuffers observed) {
        check(observed.minX > 180F && observed.maxX < 350F && observed.minY > 145F && observed.maxY < 310F,
                "Every emitted base/glow/sparkle vertex lies within the standing native player's chest region");
    }

    /** Verify the rendered star geometry, independent of the production coordinate helper. */
    private static JsonObject medalRows(ObservedBuffers observed, int count) {
        check(observed.baseStars.size() == count, "Each actual base-star mesh is observed separately");
        hexagramShape(observed);
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

    /** Inspect the emitted contour rather than trusting a shape name or vertex-count constant. */
    private static JsonObject hexagramShape(ObservedBuffers observed) {
        for (StarBounds star : observed.baseStars) {
            check(star.outline.size() == 12, "The actual outer mesh has twelve alternating contour corners");
            float minimum = Float.POSITIVE_INFINITY, maximum = 0;
            float[] radii = new float[12];
            int tips = 0;
            for (int index = 0; index < 12; index++) {
                float[] p = star.outline.get(index);
                float x = p[0] - star.originX, y = p[1] - star.originY, z = p[2] - star.originZ;
                radii[index] = (float)Math.sqrt(x * x + y * y + z * z);
                minimum = Math.min(minimum, radii[index]); maximum = Math.max(maximum, radii[index]);
            }
            for (int index = 0; index < 12; index++) {
                if (radii[index] > radii[(index + 11) % 12] && radii[index] > radii[(index + 1) % 12]) tips++;
                float expected = index % 2 == 0 ? maximum : minimum;
                check(Math.abs(radii[index] - expected) < .03F,
                        "The six tips and six inset corners have equal alternating radii in the real mesh");
            }
            check(tips == 6, "The actual contour has six distinct long tips, not five");
            check(Math.abs(minimum / maximum - 1F / (float)Math.sqrt(3D)) < .005F,
                    "The actual inset-to-tip ratio forms the two overlapping equilateral triangles of a hexagram");
        }
        JsonObject facts = new JsonObject();
        facts.addProperty("observed_stars", observed.baseStars.size());
        facts.addProperty("outline_corners_per_star", 12); facts.addProperty("long_tips_per_star", 6);
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
        return frame(mc, entity, yaw, observed, light, 1F);
    }

    private static NativeImage frameAt(Minecraft mc, LivingEntity entity, float ticks, ObservedBuffers observed) {
        entity.tickCount = (int)Math.floor(ticks);
        return frame(mc, entity, .18F, observed, LightTexture.FULL_BRIGHT, ticks - entity.tickCount);
    }

    /** A closer native camera for presentation; actual star size and normal-frame checks stay unchanged. */
    private static NativeImage frameDetail(Minecraft mc, LivingEntity entity, float yaw) {
        return frame(mc, entity, yaw, null, LightTexture.FULL_BRIGHT, 1F, 3F);
    }

    private static NativeImage frame(Minecraft mc, LivingEntity entity, float yaw, ObservedBuffers observed,
                                     int light, float partialTick) {
        return frame(mc, entity, yaw, observed, light, partialTick, 1F);
    }

    private static NativeImage frame(Minecraft mc, LivingEntity entity, float yaw, ObservedBuffers observed,
                                     int light, float partialTick, float zoom) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix()); VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        var dispatcher = mc.getEntityRenderDispatcher();
        try {
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, WIDTH, HEIGHT, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1);
            PoseStack pose = new PoseStack();
            pose.translate(WIDTH / 2F, zoom == 1F ? HEIGHT - 28F : HEIGHT * .5F * (1F + zoom), 50);
            pose.scale(240F * zoom, 240F * zoom, -240F * zoom);
            pose.mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI + yaw));
            Lighting.setupForEntityInInventory(); dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, partialTick, pose,
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
        float originX, originY, originZ;
        final List<float[]> outline = new java.util.ArrayList<>();
        final List<MeshVertex> vertices = new java.util.ArrayList<>();
        void include(float x, float y) {
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        float centerX() { return (minX + maxX) * .5F; }
        float centerY() { return (minY + maxY) * .5F; }
    }

    private static final class MeshVertex {
        final float x, y, z;
        float nx, ny, nz;
        int color, alpha;
        MeshVertex(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
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
                MeshVertex baseVertex;
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    starVertices++; if (glow) glowVertices++;
                    firstBaseVertex = false; sparkleVertex = false; baseVertex = null;
                    if (!glow) {
                        int position = baseVertices % ChestGemLayer.BASE_VERTICES;
                        firstBaseVertex = position == 0;
                        int group = baseVertices++ / ChestGemLayer.BASE_VERTICES;
                        if (baseStars.size() <= group) baseStars.add(new StarBounds());
                        StarBounds star = baseStars.get(group); star.include(x, y);
                        baseVertex = new MeshVertex(x, y, z); star.vertices.add(baseVertex);
                        if (position < 48 && position % 4 == 0) {
                            star.outline.add(new float[]{x, y, z});
                            if (star.outline.size() == 12) {
                                for (float[] point : star.outline) {
                                    star.originX += point[0] / 12F;
                                    star.originY += point[1] / 12F;
                                    star.originZ += point[2] / 12F;
                                }
                            }
                        }
                    } else {
                        glowStar = (glowVertices - 1) / ChestGemLayer.GLOW_VERTICES;
                        if (sparkleAlphas.size() <= glowStar) sparkleAlphas.add(0);
                        sparkleVertex = (glowVertices - 1) % ChestGemLayer.GLOW_VERTICES >= ChestGemLayer.HALO_VERTICES;
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
                    if (baseVertex != null) { baseVertex.color = r << 16 | g << 8 | b; baseVertex.alpha = a; }
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
                @Override public VertexConsumer setNormal(float x, float y, float z) {
                    if (baseVertex != null) { baseVertex.nx = x; baseVertex.ny = y; baseVertex.nz = z; }
                    nativeBuffer.setNormal(x, y, z); return this;
                }
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
