package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.math.Axis;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/** Observes native atlas animation on real title-screen ticks; never advances an animation itself. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class VibraniumRuneClientAudit {
    private static final List<String> NATIVE_TOOLS = List.of("sword", "axe", "battle_axe", "dagger",
            "hatchet", "hoe", "knife", "mattock", "pickaxe", "scythe", "shovel", "war_hammer");
    private static final List<String> SIMPLY_SWORDS=List.of("longsword","twinblade","rapier","katana","sai",
            "spear","glaive","warglaive","cutlass","claymore","greataxe","greathammer","chakram","scythe","halberd");
    private static final List<ObservedSprite> SPRITES = new ArrayList<>();
    private static boolean baked, finished;
    private static int waiting, titleTicks, observationTick, captureSpan, itemDrawFrames;
    private static int firstSampleTick=-1,lastSampleTick=-1;
    private static AcceleratedProbe accelerated;
    private static JsonObject report;
    private static TextureAtlas atlas;
    private static NativeImage atlasPixels;

    private VibraniumRuneClientAudit() {}

    @EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event) {
            if (Boolean.getBoolean("modern_ite.runeAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("modern_ite.runeAudit") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (report == null) report = initialReport();
        if (mc.level != null || mc.getConnection() != null || mc.getSingleplayerServer() != null) {
            report.getAsJsonArray("failures").add("Refused to audit or close a client attached to a world/server");
            report.addProperty("status", "aborted_world_present");
            report.addProperty("safe_client_shutdown_requested", false);
            finished = true;
            closePixels();
            write();
            return;
        }
        try {
            check(++waiting <= 1800, "Timed out waiting for completed resource reload and title-screen observations");
            if (!baked || mc.getOverlay() != null) return;
            if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
            if (!(mc.screen instanceof TitleScreen)) return;
            // Minecraft.tick invokes textureManager.tick before ClientTickEvent.Post. Its
            // isLevelRunningNormally() explicitly returns true when level == null in 1.21.1.
            // No atlas tick, SpriteTicker, frame counter or texture upload is called here.
            if (++titleTicks < 20) return;
            if (SPRITES.isEmpty()) initialize(mc);
        } catch (Throwable failure) {
            report.getAsJsonArray("failures").add(failure.toString());
            LogUtils.getLogger().error("MODERN_ITE_RUNE_AUDIT observation", failure);
            finish(mc);
        }
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("modern_ite.runeAudit") || finished || SPRITES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getScreen() instanceof TitleScreen) || mc.getOverlay() != null
                || mc.level != null || mc.getConnection() != null || mc.getSingleplayerServer() != null) return;
        try {
            // Draw only the held-item path. GUI icons would independently mark sprites
            // visible and conceal the accelerated first-person mesh-cache regression.
            var graphics=event.getGuiGraphics();
            var buffers=mc.renderBuffers().bufferSource();
            graphics.flush();
            int columns=Math.max(1,(event.getScreen().width-32)/48);
            int rows=(SPRITES.size()+columns-1)/columns;
            float cellWidth=(event.getScreen().width-32F)/columns;
            float cellHeight=Math.min(56,(event.getScreen().height-32F)/rows);
            float scale=Math.min(38,Math.min(cellWidth,cellHeight)*.7F);
            boolean previousHand=accelerated.enterHand();
            try {
                Lighting.setupForFlatItems();
                for(int i=0;i<SPRITES.size();i++) {
                    ObservedSprite sprite=SPRITES.get(i);
                    graphics.pose().pushPose();
                    try {
                        graphics.pose().translate(16+(i%columns+.5F)*cellWidth,16+(i/columns+.5F)*cellHeight,150);
                        graphics.pose().scale(scale,-scale,scale);
                        graphics.pose().mulPose(Axis.YP.rotationDegrees(35));
                        accelerated.beforeDraw(mc,sprite,buffers);
                        mc.getItemRenderer().renderStatic(sprite.stack,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                                15728880,OverlayTexture.NO_OVERLAY,graphics.pose(),buffers,null,0);
                        sprite.itemDraws++;
                    } finally {graphics.pose().popPose();}
                }
                buffers.endBatch();
                for(ObservedSprite sprite:SPRITES)accelerated.afterDraw(mc,sprite);
            } finally {
                accelerated.leaveHand(previousHand);
                Lighting.setupFor3DItems();
            }
            itemDrawFrames++;
            if(itemDrawFrames<=20||waiting==lastSampleTick)return;
            if(firstSampleTick<0) {
                firstSampleTick=waiting;
                Path image=output().getParent().resolve("rune-audit").resolve("first-person-hand-context.png");
                Files.createDirectories(image.getParent());
                try(NativeImage frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(image);}
                report.addProperty("actual_first_person_draw_screenshot",image.toString());
            }
            capture();
            lastSampleTick=waiting;
            observationTick++;
            int span=lastSampleTick-firstSampleTick;
            if(span>=captureSpan&&(SPRITES.stream().allMatch(ObservedSprite::twoCyclesObserved)||span>=captureSpan*4)) {
                for(ObservedSprite sprite:SPRITES)sprite.complete();
                report.addProperty("native_client_ticks_elapsed",waiting);
                report.addProperty("actual_gpu_frame_samples",observationTick);
                report.addProperty("observation_span_ticks",span);
                report.addProperty("first_observed_client_tick",firstSampleTick);
                report.addProperty("last_observed_client_tick",lastSampleTick);
                report.addProperty("native_visible_item_draw_frames",itemDrawFrames);
                report.add("accelerated_rendering",accelerated.facts);
                finish(mc);
            }
        } catch (Throwable failure) {
            report.getAsJsonArray("failures").add("native visible item rendering: " + failure);
            LogUtils.getLogger().error("MODERN_ITE_RUNE_AUDIT item render", failure);
            finish(mc);
        }
    }

    private static JsonObject initialReport() {
        JsonObject result = new JsonObject();
        result.addProperty("author", "NingMeng123");
        result.addProperty("timestamp", Instant.now().toString());
        result.addProperty("property", "modern_ite.runeAudit=true");
        result.addProperty("scope", "FIRST_PERSON_RIGHT_HAND native item draws only at the title screen; real rendered-frame GPU atlas observations mapped to native client ticks");
        result.addProperty("world_created", false);
        result.addProperty("server_connected", false);
        result.addProperty("manual_animation_advance", false);
        result.addProperty("gui_item_icons_drawn",false);
        result.addProperty("real_draw_warmup_frames",20);
        result.addProperty("sample_phase","ScreenEvent.Render.Post after real held-item draws and native buffer flush");
        result.addProperty("native_title_texture_tick_condition", "Minecraft.isLevelRunningNormally: level == null || level.tickRateManager().runsNormally()");
        result.add("sprites", new JsonArray());
        result.add("failures", new JsonArray());
        return result;
    }

    private static void initialize(Minecraft mc) throws Exception {
        for (String tool : NATIVE_TOOLS) observe(mc, ResourceLocation.fromNamespaceAndPath("mite", "vibranium_" + tool));
        observe(mc, ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "club_vibranium"));
        int simplySwordsCount=0;
        for(String weapon:SIMPLY_SWORDS) {
            ResourceLocation item=ResourceLocation.fromNamespaceAndPath("mite","simplyswords_vibranium_"+weapon);
            if(BuiltInRegistries.ITEM.containsKey(item)){observe(mc,item);simplySwordsCount++;}
        }
        accelerated=new AcceleratedProbe();
        report.add("accelerated_rendering",accelerated.facts);
        check(SPRITES.size()==13+simplySwordsCount,"Unexpected vibranium audit item count");
        captureSpan = Math.max(48, SPRITES.stream().mapToInt(sprite -> sprite.cycleTicks * 2).max().orElseThrow());
        report.addProperty("native_tool_count", NATIVE_TOOLS.size());
        report.addProperty("addon_tool_count", 1);
        report.addProperty("optional_simplyswords_registered_count",simplySwordsCount);
        report.addProperty("title_screen_visible", mc.screen instanceof TitleScreen);
        report.addProperty("model_baking_completed", baked);
        report.addProperty("warmup_title_ticks", titleTicks);
        report.addProperty("required_observation_span_ticks", captureSpan);
    }

    /** Test-only, optional public AR hand scope plus read-only accelerated-consumer/cache evidence. */
    private static final class AcceleratedProbe {
        final JsonObject facts=new JsonObject();
        Method isHand,setHand,resetHand,getAccelerated,isAccelerated,usesPipeline,forcePipeline,resetPipeline;
        boolean present,expectedAccelerated,previousPipeline;

        AcceleratedProbe()throws Exception {
            Class<?> core;
            try{core=Class.forName("com.github.argon4w.acceleratedrendering.core.CoreFeature");}
            catch(ClassNotFoundException absent){facts.addProperty("mod_present",false);return;}
            present=true;
            isHand=core.getMethod("isRenderingHand");setHand=core.getMethod("setRenderingHand");resetHand=core.getMethod("resetRenderingHand");
            Class<?> feature=Class.forName("com.github.argon4w.acceleratedrendering.features.items.AcceleratedItemRenderingFeature");
            usesPipeline=feature.getMethod("shouldUseAcceleratedPipeline");
            forcePipeline=feature.getMethod("forceUseAcceleratedPipeline");
            resetPipeline=feature.getMethod("resetPipeline");
            Class<?> extension=Class.forName("com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension");
            getAccelerated=extension.getMethod("getAccelerated",VertexConsumer.class);
            isAccelerated=getAccelerated.getReturnType().getMethod("isAccelerated");
            boolean loaded=(Boolean)core.getMethod("isLoaded").invoke(null);
            boolean enabled=(Boolean)feature.getMethod("isEnabled").invoke(null);
            boolean pipeline=(Boolean)usesPipeline.invoke(null);
            boolean hand=(Boolean)feature.getMethod("shouldAccelerateInHand").invoke(null);
            expectedAccelerated=loaded&&enabled&&pipeline&&hand;
            facts.addProperty("mod_present",true);facts.addProperty("core_loaded",loaded);
            facts.addProperty("item_feature_enabled",enabled);facts.addProperty("accelerated_pipeline_selected",pipeline);
            facts.addProperty("hand_acceleration_enabled",hand);facts.addProperty("expected_accelerated_hand_draw",expectedAccelerated);
            facts.addProperty("hand_scope","same public CoreFeature.setRenderingHand/resetRenderingHand scope as native GameRenderer hand hook; real ItemRenderer FIRST_PERSON_RIGHT_HAND draws");
        }

        boolean enterHand()throws Exception {
            if(!present)return false;
            boolean previous=(Boolean)isHand.invoke(null);
            previousPipeline=(Boolean)usesPipeline.invoke(null);
            // AR deliberately pushes VANILLA around ScreenEvent dispatch. Push only our
            // own accelerated hand scope; resetPipeline pops it without removing that caller.
            forcePipeline.invoke(null);
            try {
                setHand.invoke(null);
                boolean actualPipeline=(Boolean)usesPipeline.invoke(null);
                facts.addProperty("pipeline_before_test_hand_scope",previousPipeline);
                facts.addProperty("actual_accelerated_pipeline_during_hand_draw",actualPipeline);
                facts.addProperty("actual_hand_flag_during_draw",(Boolean)isHand.invoke(null));
                facts.addProperty("screen_event_vanilla_override_scoped_to_test_hand_draw",true);
                check(actualPipeline,"AR accelerated pipeline override did not take effect during the real hand draw");
                return previous;
            } catch(Exception failure) {
                try{(previous?setHand:resetHand).invoke(null);}finally{resetPipeline.invoke(null);}
                throw failure;
            }
        }

        void leaveHand(boolean previous)throws Exception {
            if(!present)return;
            try{(previous?setHand:resetHand).invoke(null);}finally{resetPipeline.invoke(null);}
            check((Boolean)isHand.invoke(null)==previous,"AR hand scope was not restored");
            check((Boolean)usesPipeline.invoke(null)==previousPipeline,"AR caller pipeline scope was not restored");
            facts.addProperty("hand_scope_restored",true);
            facts.addProperty("caller_pipeline_scope_restored",true);
        }

        void beforeDraw(Minecraft mc,ObservedSprite sprite,MultiBufferSource.BufferSource buffers)throws Exception {
            if(!present)return;
            var model=mc.getItemRenderer().getModel(sprite.stack,null,null,0);
            boolean acceleratedConsumer=false;
            for(var pass:model.getRenderPasses(sprite.stack,true))for(var type:pass.getRenderTypes(sprite.stack,true)) {
                Object consumer=getAccelerated.invoke(null,buffers.getBuffer(type));
                if(consumer!=null&&(Boolean)isAccelerated.invoke(consumer))acceleratedConsumer=true;
            }
            if(acceleratedConsumer)sprite.acceleratedConsumerDraws++;
            sprite.acceleratedFacts.addProperty("accelerated_consumer_draws",sprite.acceleratedConsumerDraws);
            if(sprite.itemDraws==0)sprite.acceleratedFacts.addProperty("mesh_objects_before_first_draw",meshes(model).size());
        }

        void afterDraw(Minecraft mc,ObservedSprite sprite)throws Exception {
            if(!present)return;
            var model=mc.getItemRenderer().getModel(sprite.stack,null,null,0);
            Set<Object> meshes=meshes(model);
            sprite.acceleratedFacts.addProperty("mesh_objects_after_draw",meshes.size());
            if(!meshes.isEmpty()&&!sprite.previousMeshes.isEmpty()&&meshes.equals(sprite.previousMeshes))sprite.meshReuseDraws++;
            sprite.previousMeshes=meshes;
            sprite.acceleratedFacts.addProperty("same_mesh_objects_reused_after_draws",sprite.meshReuseDraws);
            sprite.acceleratedFacts.addProperty("native_model_class",model.getClass().getName());
        }

        private Set<Object> meshes(Object model)throws Exception {
            Set<Object> leaves=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            Field meshField=null;
            for(Class<?> type=model.getClass();type!=null&&meshField==null;type=type.getSuperclass()) {
                for(Field field:type.getDeclaredFields())if(field.getName().equals("meshes")&&Map.class.isAssignableFrom(field.getType())){meshField=field;break;}
            }
            if(meshField==null){facts.addProperty("mesh_cache_field_found",false);return leaves;}
            facts.addProperty("mesh_cache_field_found",true);
            meshField.setAccessible(true);meshLeaves(meshField.get(model),leaves);return leaves;
        }

        private void meshLeaves(Object value,Set<Object> leaves) {
            if(value instanceof Map<?,?> map)for(Object nested:map.values())meshLeaves(nested,leaves);
            else if(value!=null)leaves.add(value);
        }
    }

    private static void observe(Minecraft mc, ResourceLocation item) throws Exception {
        check(BuiltInRegistries.ITEM.containsKey(item), "Missing registered item " + item);
        var model = mc.getModelManager().getModel(ModelResourceLocation.inventory(item));
        check(model != null && model != mc.getModelManager().getMissingModel(), "Missing inventory model " + item);
        var cached = mc.getItemRenderer().getItemModelShaper().getItemModel(new ItemStack(BuiltInRegistries.ITEM.get(item)));
        check(cached != null && cached != mc.getModelManager().getMissingModel(), "Missing runtime item model " + item);
        TextureAtlasSprite sprite = cached.getParticleIcon();
        check(sprite != null && !sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing stitched sprite " + item);
        check(sprite.contents().name().equals(model.getParticleIcon().contents().name()), "Runtime and inventory sprites disagree for " + item);
        TextureAtlas actualAtlas = mc.getModelManager().getAtlas(sprite.atlasLocation());
        if (atlas == null) atlas = actualAtlas;
        check(atlas == actualAtlas, "Expected all audited item sprites in the same native item atlas");
        ObservedSprite observed = new ObservedSprite(mc, item, sprite);
        SPRITES.add(observed);
        report.getAsJsonArray("sprites").add(observed.facts);
    }

    private static void capture() throws Exception {
        RenderSystem.assertOnRenderThread();
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int packRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int packSkipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int packSkipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            // Keep Minecraft's binding cache consistent and restore the active unit's
            // original binding. No active texture unit is changed by this readback.
            GlStateManager._bindTexture(atlas.getId());
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            check(width > 0 && height > 0, "Native atlas has no GPU allocation");
            if (atlasPixels == null) {
                atlasPixels = new NativeImage(width, height, false);
                report.addProperty("atlas", atlas.location().toString());
                report.addProperty("atlas_width", width);
                report.addProperty("atlas_height", height);
            }
            check(atlasPixels.getWidth() == width && atlasPixels.getHeight() == height, "Atlas changed during observations");
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            atlasPixels.downloadTexture(0, false);
            for (ObservedSprite sprite : SPRITES) sprite.sample(atlasPixels, waiting);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packAlignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, packRowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, packSkipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, packSkipPixels);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GlStateManager._bindTexture(previousBinding);
        }
    }

    private static final class ObservedSprite {
        final ResourceLocation item;
        final TextureAtlasSprite sprite;
        final ItemStack stack;
        final JsonObject facts = new JsonObject();
        final JsonArray samples = new JsonArray();
        final Set<String> sourceHashes = new LinkedHashSet<>();
        final Map<String, int[]> gpuFrames = new LinkedHashMap<>();
        final List<String> timeline = new ArrayList<>();
        final List<Integer> sampleTicks=new ArrayList<>();
        final Set<String> cycleSeen=new LinkedHashSet<>();
        final JsonObject acceleratedFacts=new JsonObject();
        final int width, height, cycleTicks;
        final boolean interpolated;
        int[] previous;
        int transitions, maximumChangedPixels, itemDraws,completeCycles;
        int acceleratedConsumerDraws,meshReuseDraws;
        Set<Object> previousMeshes=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        String cycleAnchor;

        ObservedSprite(Minecraft mc, ResourceLocation item, TextureAtlasSprite sprite) throws Exception {
            this.item = item; this.sprite = sprite;
            stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
            width = sprite.contents().width(); height = sprite.contents().height();
            int[] indices = sprite.contents().getUniqueFrames().toArray();
            var metadata = sprite.contents().metadata().getSection(AnimationMetadataSection.SERIALIZER).orElseThrow();
            interpolated = metadata.isInterpolatedFrames();
            int[] duration = {0};
            metadata.forEachFrame((index, time) -> duration[0] += time);
            cycleTicks = duration[0] > 0 ? duration[0] : indices.length * metadata.getDefaultFrameTime();
            check(indices.length > 1 && cycleTicks > 0, "Expected a native multi-frame rune animation: " + item);
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(sprite.contents().name().getNamespace(), "textures/" + sprite.contents().name().getPath() + ".png");
            Resource resource = mc.getResourceManager().getResource(texture).orElseThrow();
            byte[] png;
            try (var stream = resource.open()) { png = stream.readAllBytes(); }
            facts.addProperty("item", item.toString());
            facts.addProperty("sprite", sprite.contents().name().toString());
            facts.addProperty("texture_resource", texture.toString());
            facts.addProperty("source_pack_id", resource.sourcePackId());
            facts.addProperty("source_png_sha256", hash(png));
            facts.addProperty("frame_width", width); facts.addProperty("frame_height", height);
            facts.addProperty("metadata_unique_frame_indices", indices.length);
            facts.addProperty("metadata_default_frame_ticks", metadata.getDefaultFrameTime());
            facts.addProperty("animation_cycle_ticks", cycleTicks);
            facts.addProperty("interpolated", interpolated);
            facts.addProperty("atlas_x", sprite.getX()); facts.addProperty("atlas_y", sprite.getY());
            JsonArray packs = new JsonArray();
            for (Resource candidate : mc.getResourceManager().getResourceStack(texture)) {
                JsonObject pack = new JsonObject();
                pack.addProperty("pack_id", candidate.sourcePackId());
                try (var stream = candidate.open()) { pack.addProperty("png_sha256", hash(stream.readAllBytes())); }
                packs.add(pack);
            }
            facts.add("resource_pack_stack", packs);
            ResourceLocation model = ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "models/item/" + item.getPath() + ".json");
            mc.getResourceManager().getResource(model).ifPresent(value -> facts.addProperty("model_source_pack_id", value.sourcePackId()));
            try (NativeImage source = NativeImage.read(new ByteArrayInputStream(png))) {
                int columns = source.getWidth() / width;
                check(columns > 0, "Source strip narrower than stitched frame: " + item);
                for (int index : indices) sourceHashes.add(hash(pixels(source, index % columns * width, index / columns * height, width, height)));
                facts.addProperty("source_strip_width", source.getWidth());
                facts.addProperty("source_strip_height", source.getHeight());
            }
            facts.addProperty("source_unique_visible_frame_hashes", sourceHashes.size());
            JsonArray sourceHashArray = new JsonArray(); sourceHashes.forEach(sourceHashArray::add);
            facts.add("source_visible_frame_sha256", sourceHashArray);
            facts.add("samples", samples);
            facts.add("accelerated_hand_draw",acceleratedFacts);
        }

        void sample(NativeImage image, int tick) throws Exception {
            int[] current = pixels(image, sprite.getX(), sprite.getY(), width, height);
            String sha = hash(current);
            int changed = previous == null ? 0 : differentVisiblePixels(previous, current);
            if (changed > 0) transitions++;
            maximumChangedPixels = Math.max(maximumChangedPixels, changed);
            gpuFrames.putIfAbsent(sha, current);
            if(cycleAnchor==null)cycleAnchor=sha;
            if(!timeline.isEmpty()&&!timeline.get(timeline.size()-1).equals(sha)&&sha.equals(cycleAnchor)
                    &&cycleSeen.containsAll(sourceHashes)) {
                completeCycles++;cycleSeen.clear();
            }
            cycleSeen.add(sha);
            timeline.add(sha);
            sampleTicks.add(tick);
            JsonObject sample = new JsonObject();
            sample.addProperty("observed_client_tick", tick);
            sample.addProperty("gpu_frame_sample_index",timeline.size()-1);
            sample.addProperty("client_ticks_since_previous_sample",sampleTicks.size()==1?0:tick-sampleTicks.get(sampleTicks.size()-2));
            sample.addProperty("gpu_visible_pixel_sha256", sha);
            sample.addProperty("changed_visible_pixels_from_previous_tick", changed);
            samples.add(sample);
            previous = current;
        }

        void complete() throws Exception {
            int repeats=0,comparisons=0,maximumGap=0;
            Map<Integer,String> observedTicks=new LinkedHashMap<>();
            for(int sample=0;sample<timeline.size();sample++) {
                observedTicks.put(sampleTicks.get(sample),timeline.get(sample));
                if(sample>0)maximumGap=Math.max(maximumGap,sampleTicks.get(sample)-sampleTicks.get(sample-1));
            }
            for(var sample:observedTicks.entrySet()) {
                String following=observedTicks.get(sample.getKey()+cycleTicks);
                if(following!=null){comparisons++;if(sample.getValue().equals(following))repeats++;}
            }
            facts.addProperty("actual_gpu_unique_sprite_hashes", gpuFrames.size());
            facts.addProperty("actual_gpu_visible_transition_count", transitions);
            facts.addProperty("maximum_visible_pixels_changed_between_ticks", maximumChangedPixels);
            facts.addProperty("cycle_repeat_comparisons", comparisons);
            facts.addProperty("cycle_repeat_matches", repeats);
            facts.addProperty("cycle_repeat_rule","compare only GPU samples with actual client tick difference exactly equal to the metadata cycle; diagnostic only because visibility scheduling may pause animation");
            facts.addProperty("observed_complete_gpu_cycles",completeCycles);
            facts.addProperty("maximum_client_tick_gap_between_gpu_frames",maximumGap);
            facts.addProperty("actual_observation_span_ticks",sampleTicks.get(sampleTicks.size()-1)-sampleTicks.get(0));
            facts.addProperty("sample_count", timeline.size());
            facts.addProperty("native_item_draw_count", itemDraws);
            JsonArray hashes = new JsonArray(); gpuFrames.keySet().forEach(hashes::add);
            facts.add("actual_gpu_sprite_sha256", hashes);
            Path contact = output().getParent().resolve("rune-audit").resolve(item.getNamespace() + "-" + item.getPath() + "-gpu-frames.png");
            Files.createDirectories(contact.getParent());
            try (NativeImage sheet = new NativeImage(width * gpuFrames.size(), height, true)) {
                int column = 0;
                for (int[] frame : gpuFrames.values()) {
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) sheet.setPixelRGBA(column * width + x, y, frame[y * width + x]);
                    column++;
                }
                sheet.writeToFile(contact);
            }
            facts.addProperty("gpu_unique_frame_contact_sheet", contact.toString());
            boolean passed = itemDraws>20&&twoCyclesObserved()
                    &&sampleTicks.get(sampleTicks.size()-1)-sampleTicks.get(0)>=cycleTicks*2;
            if (!interpolated) passed &= sourceHashes.equals(gpuFrames.keySet());
            if(accelerated.expectedAccelerated)passed&=acceleratedConsumerDraws>20&&meshReuseDraws>10;
            facts.addProperty("status", passed ? "passed" : "failed");
            if (!passed) report.getAsJsonArray("failures").add(item + ": native GPU atlas did not reproduce all source rune frames and two complete real-tick cycles");
        }

        boolean twoCyclesObserved(){return gpuFrames.size()>=sourceHashes.size()&&gpuFrames.size()>1&&maximumChangedPixels>0&&completeCycles>=2;}
    }

    private static int[] pixels(NativeImage image, int x0, int y0, int width, int height) {
        check(x0 >= 0 && y0 >= 0 && x0 + width <= image.getWidth() && y0 + height <= image.getHeight(), "Sprite frame exceeds image bounds");
        int[] result = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int color = image.getPixelRGBA(x0 + x, y0 + y);
            result[y * width + x] = (color >>> 24) == 0 ? 0 : color;
        }
        return result;
    }

    private static int differentVisiblePixels(int[] before, int[] after) {
        int changed = 0;
        for (int i = 0; i < before.length; i++) if (before[i] != after[i] && ((before[i] | after[i]) >>> 24) != 0) changed++;
        return changed;
    }

    private static String hash(int[] pixels) throws Exception {
        byte[] bytes = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            for (int channel = 0; channel < 4; channel++) bytes[i * 4 + channel] = (byte)(color >>> (channel * 8));
        }
        return hash(bytes);
    }

    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static Path output() { return Path.of(System.getProperty("modern_ite.runeAuditOutput", "build/verification/vibranium-rune-audit.json")).toAbsolutePath().normalize(); }
    private static void closePixels() { if (atlasPixels != null) { atlasPixels.close(); atlasPixels = null; } }

    private static void finish(Minecraft mc) {
        if (finished) return;
        finished = true;
        closePixels();
        report.addProperty("status", report.getAsJsonArray("failures").isEmpty() ? "passed" : "failed");
        report.addProperty("safe_client_shutdown_requested", true);
        write();
        mc.stop();
    }

    private static void write() {
        try {
            Files.createDirectories(output().getParent());
            Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            LogUtils.getLogger().info("MODERN_ITE_RUNE_AUDIT {} report={}", report.get("status").getAsString(), output());
        } catch (Exception failure) { LogUtils.getLogger().error("Cannot write vibranium rune audit", failure); }
    }
}
