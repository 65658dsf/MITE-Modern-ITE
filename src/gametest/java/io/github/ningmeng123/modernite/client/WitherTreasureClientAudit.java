package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.content.wither.WitherTreasureContent;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import io.github.ningmeng123.modernite.gear.GemBoxItem;
import io.github.ningmeng123.modernite.gear.GemType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Actual GPU samples of the Wither chest, its hinged lid and all reward shapes. @author NingMeng123 */
final class WitherTreasureClientAudit {
    private static final int WIDTH = 480, HEIGHT = 360;
    private static final int[] PHASES = {-1, 8, 16, 24, 35, 40, 52, 55};
    private WitherTreasureClientAudit() {}

    static JsonObject capture(Minecraft mc, Path screenshots) throws Exception {
        check(Boolean.getBoolean("modern_ite.witherTreasureAudit"), "The Wither GPU audit requires its explicit launch property");
        check(mc.level != null && mc.player != null, "The owned disposable audit world must be ready");
        Path folder = screenshots.resolve("wither-treasure");
        Files.createDirectories(folder);
        JsonObject result = new JsonObject();
        result.addProperty("author", "NingMeng123");
        result.addProperty("actual_opengl_rendering", true);
        result.addProperty("scope", "Real inventory ItemRenderer, both native hand events, complete EntityRenderDispatcher/PlayerRenderer and isolated hinged-model GPU buffers");
        result.addProperty("shader_pack_visual_validation", false);
        ItemStack treasure = new ItemStack(WitherTreasureContent.TREASURE.get());
        check(treasure.getItem() instanceof GemBoxItem, "The actual Wither treasure participates in the shared timed chest animation");
        int closedQuads = geometry(mc, treasure, result);
        int partsQuads = quads(mc.getModelManager().getModel(GemBoxModelRendering.bodyModel(treasure))).size()
                + quads(mc.getModelManager().getModel(GemBoxModelRendering.lidModel(treasure))).size();
        result.addProperty("animated_parts_quads", partsQuads);
        check(partsQuads >= 24, "The open Wither body and lid contain real cuboid geometry");
        List<Reward> rewards = List.of(new Reward("gem-rank-6", ExtremeGear.gem(GemType.DAMAGE, 6)),
                new Reward("diamonds", new ItemStack(Items.DIAMOND, 16)), new Reward("maximum-book", maximumBook(mc)));
        JsonArray sequences = new JsonArray(); result.add("sequences", sequences);
        try (GemBoxMovementAudit.State state = new GemBoxMovementAudit.State(mc);
                NativeImage storyboard = new NativeImage(WIDTH * PHASES.length, HEIGHT * rewards.size() * 3, false)) {
            state.stage(GemBoxMovementAudit.Mode.IDLE, 0);
            mc.player.setMainArm(HumanoidArm.RIGHT);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            try (NativeImage gui = inventory(mc, treasure)) {
                gui.writeToFile(folder.resolve("wither-treasure-inventory.png"));
                check(foreground(gui) > 1000, "The native inventory renderer displays the complete volumetric Wither treasure");
                result.addProperty("inventory_foreground_pixels", foreground(gui));
            }
            int group = 0;
            List<NativeImage> shownRewards = new ArrayList<>();
            try {
                for (Reward reward : rewards) {
                    ItemStack animated = treasure.copy();
                    mc.player.setItemInHand(InteractionHand.MAIN_HAND, animated);
                    JsonObject sequence = new JsonObject(); sequences.add(sequence);
                    sequence.addProperty("reward", reward.name());
                    int rewardQuads = checkedQuads(mc.getItemRenderer().getModel(reward.stack(), mc.level, mc.player, 0));
                    sequence.addProperty("reward_baked_quads", rewardQuads);
                    JsonArray phases = new JsonArray(); sequence.add("phases", phases);
                    NativeImage previousChest = null;
                    try {
                        for (int column = 0; column < PHASES.length; column++) {
                            int elapsed = PHASES[column];
                            stage(mc, animated, elapsed, reward.stack());
                            ItemStack preview = GemBoxItem.revealedGem(animated);
                            boolean revealed = elapsed >= GemBoxItem.REVEAL_TICK;
                            check(revealed ? ItemStack.matches(preview, reward.stack()) : preview.isEmpty(),
                                    "The synchronized preview contains the exact chosen reward only after tick 36");
                            JsonObject phase = new JsonObject(); phases.add(phase);
                            phase.addProperty("elapsed_ticks", elapsed);
                            phase.addProperty("reward_revealed", revealed);
                            phase.addProperty("lid_degrees", GemBoxModelRendering.lidAngle(
                                    GemBoxItem.openingProgress(animated, mc.level, 1)));
                            ObservedBuffers firstBuffers = new ObservedBuffers(mc), thirdBuffers = new ObservedBuffers(mc);
                            try (NativeImage first = first(mc, firstBuffers); NativeImage third = third(mc, thirdBuffers);
                                    NativeImage chest = isolatedChest(mc, animated)) {
                                save(first, folder, reward.name() + "-" + elapsed + "-first", storyboard, group * 3, column);
                                save(third, folder, reward.name() + "-" + elapsed + "-third", storyboard, group * 3 + 1, column);
                                save(chest, folder, reward.name() + "-" + elapsed + "-chest", storyboard, group * 3 + 2, column);
                                check(firstBuffers.rightHand && firstBuffers.leftHand, "Both real skin hands remain visible throughout the Wither opening");
                                int expected = ((elapsed < 0 ? closedQuads : partsQuads) + (revealed ? rewardQuads : 0)) * 4;
                                check(firstBuffers.atlasVertices == expected && thirdBuffers.atlasVertices == expected,
                                        "Both native views draw one Wither chest and precisely its visible reward: " + reward.name()
                                                + "/" + elapsed + " got " + firstBuffers.atlasVertices + "," + thirdBuffers.atlasVertices + " expected " + expected);
                                phase.addProperty("first_atlas_vertices", firstBuffers.atlasVertices);
                                phase.addProperty("third_atlas_vertices", thirdBuffers.atlasVertices);
                                if (previousChest != null && (elapsed == 8 || elapsed == 16)) {
                                    int delta = difference(previousChest, chest);
                                    phase.addProperty("isolated_lid_changed_pixels", delta);
                                    check(delta > 100, "A fixed-camera isolated GPU render visibly moves the actual lid, independently of hand motion");
                                }
                                if (elapsed == 52) shownRewards.add(copy(first));
                                if (previousChest != null) previousChest.close();
                                previousChest = copy(chest);
                            }
                        }
                    } finally { if (previousChest != null) previousChest.close(); }
                    group++;
                }
                JsonArray differences = new JsonArray(); result.add("shown_reward_pixel_differences", differences);
                for (int a = 0; a < shownRewards.size(); a++) for (int b = a + 1; b < shownRewards.size(); b++) {
                    int delta = difference(shownRewards.get(a), shownRewards.get(b));
                    differences.add(delta);
                    check(delta > 20, "Gem, diamond and enchanted-book previews render visibly distinct native GPU pixels");
                }
            } finally { shownRewards.forEach(NativeImage::close); }
            Path preview = folder.resolve("wither-treasure-opening-storyboard.png");
            storyboard.writeToFile(preview);
            result.addProperty("preview", preview.toString());
            result.addProperty("columns", "closed; tick 8 lid opening; tick 16 lid raised; tick 24 rummage; tick 35 finding; tick 40 extraction; tick 52 showing; tick 55 showing");
            result.addProperty("rows", "first person, third person, isolated chest for rank-6 gem, diamonds, maximum enchanted book");
            result.add("hand_combinations", hands(mc, treasure, rewards, folder, closedQuads, partsQuads));
            result.add("raw_animation", animation(mc, treasure, rewards.getFirst().stack(), folder));
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            try (NativeImage empty = first(mc, new ObservedBuffers(mc))) {
                check(foreground(empty) == 0, "Removing the chest fully releases its custom hand rendering");
            }
        }
        result.addProperty("status", "passed_actual_gpu_rendering");
        return result;
    }

    private static JsonArray hands(Minecraft mc, ItemStack treasure, List<Reward> rewards, Path folder,
            int closedQuads, int partsQuads) throws Exception {
        JsonArray samples = new JsonArray();
        try (NativeImage sheet = new NativeImage(WIDTH * 4, HEIGHT * rewards.size() * 2, false)) {
            int column = 0;
            for (HumanoidArm dominant : HumanoidArm.values()) for (InteractionHand hand : InteractionHand.values()) {
                mc.player.setMainArm(dominant);
                for (int rewardIndex = 0; rewardIndex < rewards.size(); rewardIndex++) {
                    Reward reward = rewards.get(rewardIndex);
                    mc.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    ItemStack animated = treasure.copy(); stage(mc, animated, 52, reward.stack());
                    mc.player.setItemInHand(hand, animated);
                    ObservedBuffers firstBuffers = new ObservedBuffers(mc), thirdBuffers = new ObservedBuffers(mc);
                    try (NativeImage first = first(mc, firstBuffers); NativeImage third = third(mc, thirdBuffers)) {
                        String name = dominant.name().toLowerCase(Locale.ROOT) + "-" + hand.name().toLowerCase(Locale.ROOT) + "-" + reward.name();
                        save(first, folder, "hand-" + name + "-first", sheet, rewardIndex * 2, column);
                        save(third, folder, "hand-" + name + "-third", sheet, rewardIndex * 2 + 1, column);
                        int expected = (partsQuads + quads(mc.getItemRenderer().getModel(reward.stack(), mc.level, mc.player, 0)).size()) * 4;
                        check(firstBuffers.rightHand && firstBuffers.leftHand && firstBuffers.atlasVertices == expected && thirdBuffers.atlasVertices == expected,
                                "Every dominant-arm and selected-hand combination renders both palms, one chest and one exact reward: " + name);
                        JsonObject sample = new JsonObject(); samples.add(sample);
                        sample.addProperty("dominant_arm", dominant.name()); sample.addProperty("hand", hand.name());
                        sample.addProperty("reward", reward.name()); sample.addProperty("first_atlas_vertices", firstBuffers.atlasVertices);
                        sample.addProperty("third_atlas_vertices", thirdBuffers.atlasVertices);
                    }
                }
                column++;
            }
            sheet.writeToFile(folder.resolve("wither-treasure-hand-combinations.png"));
        }
        mc.player.setMainArm(HumanoidArm.RIGHT);
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, treasure.copy());
        mc.player.setItemInHand(InteractionHand.OFF_HAND, treasure.copy());
        ObservedBuffers both = new ObservedBuffers(mc);
        try (NativeImage image = first(mc, both)) {
            check(both.atlasVertices == closedQuads * 4 && foreground(image) > 500,
                    "Treasures in both hands still draw exactly one shared central chest");
        }
        return samples;
    }

    private static JsonObject animation(Minecraft mc, ItemStack treasure, ItemStack reward, Path folder) throws Exception {
        Path frames = folder.resolve("animation"); Files.createDirectories(frames);
        mc.player.setMainArm(HumanoidArm.RIGHT);
        ItemStack animated = treasure.copy(); mc.player.setItemInHand(InteractionHand.MAIN_HAND, animated);
        mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (int elapsed = 0; elapsed < GemBoxItem.OPEN_TICKS; elapsed++) {
            mc.player.tickCount = 120 + elapsed;
            stage(mc, animated, elapsed, reward);
            try (NativeImage image = first(mc, new ObservedBuffers(mc))) {
                check(foreground(image) > 500, "Every frame of the actual 56-tick Wither opening remains visible");
                image.writeToFile(frames.resolve(String.format(Locale.ROOT, "frame-%03d.png", elapsed)));
            }
        }
        JsonObject result = new JsonObject(); result.addProperty("directory", frames.toString());
        result.addProperty("frames", GemBoxItem.OPEN_TICKS); result.addProperty("frames_per_second", 20);
        result.addProperty("filename_pattern", "frame-%03d.png"); return result;
    }

    private static int geometry(Minecraft mc, ItemStack treasure, JsonObject result) {
        check(GemBoxModelRendering.bodyModel(treasure).equals(GemBoxModelRendering.WITHER_BODY_MODEL)
                        && GemBoxModelRendering.lidModel(treasure).equals(GemBoxModelRendering.WITHER_LID_MODEL),
                "Wither treasure selects its own real baked body and lid resources");
        BakedModel model = mc.getItemRenderer().getModel(treasure, mc.level, mc.player, 0);
        check(model.isGui3d(), "The inventory Wither treasure uses a native 3D model");
        List<BakedQuad> faces = quads(model);
        int count = checkedQuads(model);
        check(count >= 24, "The closed Wither treasure contains cuboid geometry rather than a generated sprite");
        float[] low = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] high = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (BakedQuad face : faces) {
            int[] vertices = face.getVertices();
            for (int vertex = 0; vertex < vertices.length; vertex += 8) for (int axis = 0; axis < 3; axis++) {
                float coordinate = Float.intBitsToFloat(vertices[vertex + axis]);
                low[axis] = Math.min(low[axis], coordinate); high[axis] = Math.max(high[axis], coordinate);
            }
        }
        JsonObject facts = new JsonObject(); facts.addProperty("closed_quads", count);
        for (int axis = 0; axis < 3; axis++) {
            check(high[axis] - low[axis] > .3F, "The actual Wither chest has substantial native model volume on every axis");
            facts.addProperty(new String[]{"width", "height", "depth"}[axis], high[axis] - low[axis]);
        }
        for (var id : new net.minecraft.client.resources.model.ModelResourceLocation[]{GemBoxModelRendering.WITHER_BODY_MODEL, GemBoxModelRendering.WITHER_LID_MODEL}) {
            BakedModel part = mc.getModelManager().getModel(id);
            check(part != mc.getModelManager().getMissingModel(), "The actual model manager resolves the Wither animation part " + id);
            checkedQuads(part);
        }
        result.add("baked_model", facts); return count;
    }

    private static int checkedQuads(BakedModel model) {
        List<BakedQuad> faces = quads(model);
        check(!faces.isEmpty(), "Every sampled reward or chest part has real baked faces");
        for (BakedQuad face : faces) check(!face.getSprite().contents().name().getPath().contains("missingno"),
                "Every actual baked face resolves its texture");
        return faces.size();
    }

    private static List<BakedQuad> quads(BakedModel model) {
        List<BakedQuad> result = new ArrayList<>(model.getQuads(null, null, RandomSource.create(196)));
        for (Direction direction : Direction.values()) result.addAll(model.getQuads(null, direction, RandomSource.create(196)));
        return result;
    }

    private static ItemStack maximumBook(Minecraft mc) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var enchantment = mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, enchantment.value().getMaxLevel()); book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    private static void stage(Minecraft mc, ItemStack chest, int elapsed, ItemStack reward) {
        chest.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
        chest.remove(ExtremeGear.GEM_BOX_REVEALED_REWARD.get());
        chest.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
        if (elapsed >= 0) chest.set(ExtremeGear.GEM_BOX_OPEN_STARTED.get(), mc.level.getGameTime() - elapsed);
        if (elapsed >= GemBoxItem.REVEAL_TICK) GemBoxItem.setRevealedReward(chest, reward);
    }

    private static NativeImage inventory(Minecraft mc, ItemStack chest) {
        return frame(mc, false, pose -> {
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.pose().translate(WIDTH / 2F - 96, HEIGHT / 2F - 96, 0);
            graphics.pose().scale(12, 12, 1); graphics.renderItem(chest, 0, 0); graphics.flush();
        });
    }

    private static NativeImage first(Minecraft mc, ObservedBuffers buffers) {
        return frame(mc, true, pose -> {
            boolean active = mc.player.getMainHandItem().getItem() instanceof GemBoxItem
                    || mc.player.getOffhandItem().getItem() instanceof GemBoxItem;
            for (InteractionHand hand : InteractionHand.values()) {
                var event = new RenderHandEvent(hand, pose, buffers, LightTexture.FULL_BRIGHT, 1, 0, 0, 0, mc.player.getItemInHand(hand));
                NeoForge.EVENT_BUS.post(event);
                check(event.isCanceled() == active, "The custom two-hand pose owns precisely the native chest hand events");
            }
        });
    }

    private static NativeImage third(Minecraft mc, ObservedBuffers buffers) {
        return frame(mc, false, pose -> {
            pose.translate(WIDTH / 2F, HEIGHT - 22F, 50); pose.scale(145, 145, -145);
            pose.mulPose(new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI + .35F));
            Lighting.setupForEntityInInventory(); GemBoxDrawPoseAudit.renderThroughDispatcher(mc, pose, buffers);
        });
    }

    private static NativeImage isolatedChest(Minecraft mc, ItemStack chest) {
        return frame(mc, false, pose -> {
            pose.translate(WIDTH / 2F, HEIGHT / 2F + 65, 50); pose.scale(220, -220, 220);
            pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(20)).rotateY((float) Math.toRadians(-30)));
            GemBoxModelRendering.render(mc.player, chest, pose, mc.renderBuffers().bufferSource(), LightTexture.FULL_BRIGHT);
        });
    }

    private static NativeImage frame(Minecraft mc, boolean perspective, Draw draw) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        try {
            target.setClearColor(16F / 255, 24F / 255, 32F / 255, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(perspective
                    ? new Matrix4f().setPerspective((float) Math.toRadians(70), WIDTH / (float) HEIGHT, .05F, 100)
                    : new Matrix4f().setOrtho(0, WIDTH, HEIGHT, 0, -1000, 1000),
                    perspective ? VertexSorting.DISTANCE_TO_ORIGIN : VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1);
            Lighting.setupFor3DItems(); draw.render(new PoseStack()); mc.renderBuffers().bufferSource().endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch(); view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); Lighting.setupFor3DItems();
            mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static final class ObservedBuffers implements MultiBufferSource {
        private final Minecraft mc;
        private int atlasVertices;
        private boolean rightHand, leftHand;
        private ObservedBuffers(Minecraft mc) { this.mc = mc; }
        @Override public VertexConsumer getBuffer(RenderType type) {
            if (type.toString().contains(mc.player.getSkin().texture().toString())) StackWalker.getInstance().forEach(frame -> {
                if (frame.getMethodName().equals("renderRightHand")) rightHand = true;
                if (frame.getMethodName().equals("renderLeftHand")) leftHand = true;
            });
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

    private static void save(NativeImage image, Path folder, String name, NativeImage sheet, int row, int column) throws Exception {
        image.writeToFile(folder.resolve("wither-treasure-" + name + ".png"));
        check(foreground(image) > 500, "The actual GPU sample remains visible: " + name);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
            sheet.setPixelRGBA(column * WIDTH + x, row * HEIGHT + y, image.getPixelRGBA(x, y));
    }

    private static int foreground(NativeImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int color = image.getPixelRGBA(x, y);
            if (Math.abs((color & 255) - 16) + Math.abs((color >>> 8 & 255) - 24) + Math.abs((color >>> 16 & 255) - 32) > 18) count++;
        }
        return count;
    }

    private static int difference(NativeImage first, NativeImage second) {
        int changed = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            int a = first.getPixelRGBA(x, y), b = second.getPixelRGBA(x, y);
            if (Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))
                    + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255)) > 24) changed++;
        }
        return changed;
    }

    private static NativeImage copy(NativeImage source) {
        NativeImage result = new NativeImage(source.getWidth(), source.getHeight(), false); result.copyFrom(source); return result;
    }
    private record Reward(String name, ItemStack stack) {}
    @FunctionalInterface private interface Draw { void render(PoseStack pose); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
