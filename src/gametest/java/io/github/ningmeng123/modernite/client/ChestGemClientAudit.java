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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Complete registered player and armor-stand GPU renders of authoritative chest sockets. @author NingMeng123 */
public final class ChestGemClientAudit {
    private static final int WIDTH = 512, HEIGHT = 512;
    private static final List<GemType> TYPES = List.of(GemType.HEALTH, GemType.PROTECTION, GemType.RECOVER,
            GemType.DURABILITY, GemType.FIREPROOF, GemType.EXPLOSION_PROOF, GemType.NUTRITION, GemType.HEALTH);
    private ChestGemClientAudit() {}

    public static JsonObject capture(Minecraft mc, Path folder) throws Exception {
        Files.createDirectories(folder);
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("scope", "Full native registered entity renderers, physical socket-item atlas geometry and GPU color changes");
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
                        check(emptyVertices.atlasVertices == 0, "An unmodified chestplate emits no gem item atlas vertices");
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
                            int expected = expectedVertices(mc, chest);
                            check(fullVertices.atlasVertices == expected,
                                    "The registered layer emits exactly eight actual gem item models, expected=" + expected
                                            + ", actual=" + fullVertices.atlasVertices);
                            facts.addProperty("socket_count", 8);
                            facts.addProperty("changed_pixels", changed);
                            facts.addProperty("gem_vertices", fullVertices.atlasVertices);
                            facts.addProperty("expected_item_vertices", expected);
                            facts.addProperty("distinct_chest_gem_colors", colorFamilies(empty, full));
                            check(colorFamilies(empty, full) >= 3, "Different socket types retain visibly different native item colors");
                            Path png = folder.resolve("chest-gems-" + shape.id() + "-eight.png"); full.writeToFile(png);
                            facts.addProperty("screenshot", png.toString()); copy(full, preview, index, 1);
                            player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING);
                            ObservedBuffers crouchVertices = new ObservedBuffers(mc);
                            try (NativeImage crouched = frame(mc, player, .18F, crouchVertices)) {
                                check(crouchVertices.atlasVertices == expected, "Crouching retains all eight chest-attached gem models");
                                check(difference(full, crouched) > 100, "The native crouching model visibly changes the attached chest pose");
                                crouched.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-crouched.png"));
                                copy(crouched, preview, index, 2);
                            }
                            player.setShiftKeyDown(false); player.setPose(Pose.STANDING);
                            ObservedBuffers turnVertices = new ObservedBuffers(mc);
                            try (NativeImage turned = frame(mc, player, .8F, turnVertices)) {
                                check(turnVertices.atlasVertices == expected, "Turning the complete body retains all attached gem models");
                                turned.writeToFile(folder.resolve("chest-gems-" + shape.id() + "-turned.png"));
                                copy(turned, preview, index, 3);
                            }
                        }
                        chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.HEALTH, 1)));
                        try (NativeImage health = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                            chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, code(GemType.PROTECTION, 6)));
                            try (NativeImage protection = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                                int replaced = difference(health, protection);
                                check(replaced > 5, "Changing one authoritative socket visibly replaces its actual type and rank");
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
                            check(invisibleVertices.atlasVertices == expectedVertices(mc, chest),
                                    "Native invisible wearers retain their visible armor's eight gem models");
                            chest.remove(ExtremeGear.SOCKETS.get());
                            ObservedBuffers invisibleEmptyVertices = new ObservedBuffers(mc);
                            try (NativeImage invisibleEmpty = frame(mc, player, .18F, invisibleEmptyVertices)) {
                                int changed = difference(invisibleEmpty, invisibleGems);
                                check(invisibleEmptyVertices.atlasVertices == 0 && changed > 100,
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
                        ObservedBuffers unrelated = new ObservedBuffers(mc);
                        try (NativeImage ignored = frame(mc, player, .18F, unrelated)) {
                            check(unrelated.atlasVertices == 0, "Gems in another armor slot never appear on an empty chest");
                        }
                    }
                    facts.addProperty("empty_and_removed_restore_exact_pixels", true);
                    facts.addProperty("crouch_and_body_turn", true);
                    facts.addProperty("invisible_and_other_armor_slot_controls", true);
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
                    check(standVertices.atlasVertices == expectedVertices(mc, chest), "An armor stand emits the same eight native chest gem models");
                    check(difference(empty, full) > 100, "Armor-stand chest gems reach the actual GPU");
                    Path png = folder.resolve("chest-gems-armor-stand.png"); full.writeToFile(png);
                    report.addProperty("armor_stand_screenshot", png.toString());
                    report.addProperty("armor_stand_vertices", standVertices.atlasVertices);
                }
            }
        } finally { stand.discard(); }
        report.add("compact_count_layouts", countLayouts(mc, folder));
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
                        check(changed > 20, "The compact " + count + "-gem layout remains readable in the actual player image");
                        check(observed.atlasVertices == expectedVertices(mc, chest),
                                "The compact " + count + "-gem layout renders every actual occupied item exactly once");
                        Path png = folder.resolve("compact-gems-" + count + ".png"); actual.writeToFile(png);
                        copy(actual, gallery, (count - 1) / 4, (count - 1) % 4);
                        JsonObject facts = new JsonObject(); samples.add(facts);
                        facts.addProperty("occupied_sockets", count); facts.addProperty("changed_pixels", changed);
                        facts.addProperty("gem_vertices", observed.atlasVertices); facts.addProperty("screenshot", png.toString());
                    }
                }
                Path png = folder.resolve("compact-gems-counts.png"); gallery.writeToFile(png);
                report.addProperty("preview", png.toString());
                report.addProperty("preview_order", "one through four across the first row; five through eight across the second row");
                int first = code(GemType.HEALTH, 1), second = code(GemType.PROTECTION, 2);
                chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, first).with(1, second));
                try (NativeImage adjacent = frame(mc, player, .18F, new ObservedBuffers(mc))) {
                    // Only slot occupancy changes; the ordered pair and authoritative stored codes remain intact.
                    chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, first).with(7, second));
                    ObservedBuffers observed = new ObservedBuffers(mc);
                    try (NativeImage sparse = frame(mc, player, .18F, observed)) {
                        check(observed.atlasVertices == expectedVertices(mc, chest),
                                "Separated authoritative socket indices still draw exactly their two native gem models");
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

    private static int expectedVertices(Minecraft mc, ItemStack chest) {
        int count = 0;
        for (int code : ExtremeGear.sockets(chest).values()) {
            if (code == 0) continue;
            var model = mc.getItemRenderer().getModel(ExtremeGear.fromCode(code), mc.level, mc.player, 0);
            count += model.getQuads(null, null, RandomSource.create(42)).size() * 4;
            for (Direction direction : Direction.values()) count += model.getQuads(null, direction, RandomSource.create(42)).size() * 4;
        }
        return count;
    }

    private static NativeImage frame(Minecraft mc, LivingEntity entity, float yaw, ObservedBuffers observed) {
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
                    observed == null ? mc.renderBuffers().bufferSource() : observed, LightTexture.FULL_BRIGHT));
            mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            dispatcher.setRenderShadow(true); mc.renderBuffers().bufferSource().endBatch();
            view.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(projection, sorting);
            Lighting.setupFor3DItems(); mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static final class ObservedBuffers implements MultiBufferSource {
        final Minecraft mc; int atlasVertices;
        ObservedBuffers(Minecraft mc) { this.mc = mc; }
        @Override public VertexConsumer getBuffer(RenderType type) {
            VertexConsumer nativeBuffer = mc.renderBuffers().bufferSource().getBuffer(type);
            if (!type.toString().contains("textures/atlas/blocks.png")) return nativeBuffer;
            return new VertexConsumer() {
                @Override public VertexConsumer addVertex(float x, float y, float z) { atlasVertices++; nativeBuffer.addVertex(x, y, z); return this; }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { nativeBuffer.setColor(r, g, b, a); return this; }
                @Override public VertexConsumer setUv(float u, float v) { nativeBuffer.setUv(u, v); return this; }
                @Override public VertexConsumer setUv1(int u, int v) { nativeBuffer.setUv1(u, v); return this; }
                @Override public VertexConsumer setUv2(int u, int v) { nativeBuffer.setUv2(u, v); return this; }
                @Override public VertexConsumer setNormal(float x, float y, float z) { nativeBuffer.setNormal(x, y, z); return this; }
            };
        }
    }

    private static int difference(NativeImage first, NativeImage second) {
        int changed = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) if (delta(first.getPixelRGBA(x, y), second.getPixelRGBA(x, y)) > 12) changed++;
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
