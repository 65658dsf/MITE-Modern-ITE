package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Opt-in isolated title-screen audit; never opens a world or connects to a server. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT)
public final class ModernIteClientAudit {
    private static boolean baked, finished;
    private static int waiting;
    private ModernIteClientAudit() {}

    @EventBusSubscriber(modid = ModernIte.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event) {
            if (Boolean.getBoolean("modern_ite.clientAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("modern_ite.clientAudit") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        // This property belongs only to the dedicated launch. A world/connection aborts all automation.
        if (mc.level != null || mc.getConnection() != null || mc.getSingleplayerServer() != null) {
            finished = true;
            JsonObject report = report(mc);
            report.getAsJsonArray("failures").add("Refused to audit or close a client attached to a world/server");
            report.addProperty("status", "aborted_world_present");
            report.addProperty("safe_client_shutdown_requested", false);
            write(report);
            return;
        }
        if (++waiting > 1800) {
            finished = true;
            JsonObject report = report(mc);
            report.getAsJsonArray("failures").add("Timed out waiting for the first title screen and completed resource reload");
            finish(mc, report);
            return;
        }
        if (!baked || mc.getOverlay() != null) return;
        if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
            return;
        }
        if (!(mc.screen instanceof TitleScreen)) return;
        finished = true;
        JsonObject report = report(mc);
        attempt(report, "native entity-renderer instances", () -> renderers(mc, report));
        attempt(report, "menu screen factories", () -> menus(report));
        attempt(report, "all registered item models and sprites", () -> items(mc, report));
        attempt(report, "all custom effect sprites", () -> effects(mc, report));
        attempt(report, "compiled cosmic armor shader", () -> report.add("cosmic_shader",VibraniumGildingPreviews.verifyShaderReady()));
        finish(mc, report);
    }

    private static JsonObject report(Minecraft mc) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("timestamp", Instant.now().toString());
        report.addProperty("property", "modern_ite.clientAudit=true");
        report.addProperty("scope", "native post-reload renderer instances; registered menu screen factories; baked inventory models and quad/particle sprites; effect atlas sprites");
        report.addProperty("model_baking_completed", baked);
        report.addProperty("title_screen_visible", mc.screen instanceof TitleScreen);
        report.addProperty("world_created", mc.level != null);
        report.addProperty("server_connected", mc.getConnection() != null || mc.getSingleplayerServer() != null);
        report.addProperty("world_loading_requested", false);
        report.addProperty("gameplay_verified", false);
        report.addProperty("menus_visually_rendered", false);
        report.add("failures", new JsonArray());
        return report;
    }

    @SuppressWarnings("unchecked")
    private static void renderers(Minecraft mc, JsonObject report) throws ReflectiveOperationException {
        // The actual reload constructs every renderer against the native model-layer context.
        // Read its published instances rather than constructing entities in a synthetic/played world.
        Field field = EntityRenderDispatcher.class.getDeclaredField("renderers");
        field.setAccessible(true);
        Map<EntityType<?>, EntityRenderer<?>> instances = (Map<EntityType<?>, EntityRenderer<?>>)field.get(mc.getEntityRenderDispatcher());
        Field playersField = EntityRenderDispatcher.class.getDeclaredField("playerRenderers");
        playersField.setAccessible(true);
        Map<?, EntityRenderer<?>> players = (Map<?, EntityRenderer<?>>)playersField.get(mc.getEntityRenderDispatcher());
        Field layersField = LivingEntityRenderer.class.getDeclaredField("layers"); layersField.setAccessible(true);
        int gildings=0;
        Set<EntityRenderer<?>> unique=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        unique.addAll(instances.values());unique.addAll(players.values());
        for(EntityRenderer<?> renderer:unique)if(renderer instanceof LivingEntityRenderer<?,?> living
                &&living.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>) {
            long count=((java.util.List<?>)layersField.get(living)).stream().filter(layer->layer instanceof io.github.ningmeng123.modernite.client.entity.VibraniumGildingLayer<?,?>).count();
            check(count==1,"Each humanoid and player renderer needs exactly one vibranium gilding layer: "+renderer.getClass().getName());gildings++;
        }
        check(players.size()==2,"Both ordinary and slim player skins are audited");
        report.addProperty("gilding_humanoid_renderers",gildings);report.addProperty("gilding_player_skins",players.size());
        JsonArray entries = new JsonArray(); report.add("renderers", entries);
        Set<EntityType<?>> creatures = new HashSet<>();
        check(ExtremeKind.values().length == 46, "Expected 46 creature identities including the Time Lord");
        for (ExtremeKind kind : ExtremeKind.values()) {
            EntityType<?> type = ExtremeMobs.type(kind);
            creatures.add(type);
            JsonObject facts = new JsonObject(); entries.add(facts);
            facts.addProperty("entity", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            EntityRenderer<?> renderer = instances.get(type);
            check(renderer != null, "Native resource reload did not instantiate renderer for " + kind.id());
            facts.addProperty("renderer", renderer.getClass().getName());
            facts.addProperty("native_reload_instantiated", true);
            if (renderer instanceof LivingEntityRenderer<?, ?> living) {
                check(living.getModel() != null, "Native living renderer has no baked model: " + kind.id());
                facts.addProperty("model", living.getModel().getClass().getName());
            }
        }
        JsonArray extra = new JsonArray(); report.add("projectile_renderers", extra);
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream().filter(entry -> entry.getKey().location().getNamespace().equals(ModernIte.MOD_ID))
                .filter(entry -> !creatures.contains(entry.getValue())).forEach(entry -> {
                    check(instances.get(entry.getValue()) != null, "Missing projectile renderer: " + entry.getKey().location());
                    extra.add(entry.getKey().location().toString());
                });
        report.addProperty("creature_renderer_count", entries.size());
        report.addProperty("projectile_renderer_count", extra.size());
    }

    private static void menus(JsonObject report) {
        boolean gem = MenuScreens.getScreenFactory(ExtremeGear.STATION_MENU.get()).isPresent();
        boolean forge = MenuScreens.getScreenFactory(ExtremeGear.FORGE_MENU.get()).isPresent();
        report.addProperty("gem_menu_screen_factory_registered", gem);
        report.addProperty("forge_menu_screen_factory_registered", forge);
        check(gem && forge, "Gem and independent-forge menus must both have actual client screen factories");
    }

    private static void items(Minecraft mc, JsonObject report) {
        JsonArray entries = new JsonArray(); report.add("items", entries);
        var manager = mc.getModelManager();
        var missing = manager.getMissingModel();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(ModernIte.MOD_ID)) continue;
            JsonObject facts = new JsonObject(); entries.add(facts); facts.addProperty("item", id.toString());
            attempt(report, id.toString(), () -> {
                BakedModel model = manager.getModel(ModelResourceLocation.inventory(id));
                check(model != null && model != missing, "Missing baked inventory model: " + id);
                BakedModel cached = mc.getItemRenderer().getItemModelShaper().getItemModel(new ItemStack(entry.getValue()));
                check(cached != null && cached != missing, "Missing native item-renderer model cache: " + id);
                Set<String> sprites = new HashSet<>();
                sprite(model.getParticleIcon(), sprites, id);
                if (id.getPath().equals("club_vibranium")) {
                    var contents = model.getParticleIcon().contents();
                    long frames = contents.getUniqueFrames().count();
                    var animation = contents.metadata().getSection(AnimationMetadataSection.SERIALIZER).orElseThrow();
                    check(contents.width() == 128 && contents.height() == 128,
                        "Vibranium club must stitch one square frame, not the entire animation strip");
                    check(frames == 8 && animation.getDefaultFrameTime() == 3,
                        "Vibranium club must retain all eight source frames at three ticks per frame");
                    facts.addProperty("animation_frame_width", contents.width());
                    facts.addProperty("animation_frame_height", contents.height());
                    facts.addProperty("animation_frames", frames);
                    facts.addProperty("animation_frame_ticks", animation.getDefaultFrameTime());
                }
                int quads = quadSprites(model, null, sprites, id);
                for (Direction face : Direction.values()) quads += quadSprites(model, face, sprites, id);
                facts.addProperty("model", model.getClass().getName());
                facts.addProperty("quad_count", quads);
                facts.addProperty("not_missing", true);
                JsonArray names = new JsonArray(); sprites.stream().sorted().forEach(names::add); facts.add("sprites", names);
            });
        }
        report.addProperty("item_model_count", entries.size());
        check(!entries.isEmpty(), "No Modern ITE item registrations were audited");
    }

    private static int quadSprites(BakedModel model, Direction face, Set<String> sprites, ResourceLocation item) {
        var quads = model.getQuads(null, face, RandomSource.create(0));
        for (var quad : quads) sprite(quad.getSprite(), sprites, item);
        return quads.size();
    }
    private static void sprite(TextureAtlasSprite sprite, Set<String> names, ResourceLocation owner) {
        check(sprite != null && !sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing stitched sprite for " + owner);
        names.add(sprite.contents().name().toString());
    }

    private static void effects(Minecraft mc, JsonObject report) {
        JsonArray entries = new JsonArray(); report.add("effects", entries);
        for (var entry : BuiltInRegistries.MOB_EFFECT.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(ModernIte.MOD_ID)) continue;
            JsonObject facts = new JsonObject(); entries.add(facts); facts.addProperty("effect", id.toString());
            attempt(report, "effect " + id, () -> {
                var holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(entry.getValue());
                TextureAtlasSprite sprite = mc.getMobEffectTextures().get(holder);
                sprite(sprite, new HashSet<>(), id);
                facts.addProperty("sprite", sprite.contents().name().toString());
                facts.addProperty("not_missing", true);
            });
        }
        report.addProperty("effect_sprite_count", entries.size());
        check(entries.size() == ExtremeEffects.VALUES.size(), "Expected every registered custom effect atlas sprite");
    }

    @FunctionalInterface private interface Audit { void run() throws Exception; }
    private static void attempt(JsonObject report, String label, Audit audit) {
        try { audit.run(); }
        catch (Throwable failure) {
            report.getAsJsonArray("failures").add(label + ": " + failure);
            LogUtils.getLogger().error("MODERN_ITE_CLIENT_AUDIT {}", label, failure);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void finish(Minecraft mc, JsonObject report) {
        report.addProperty("status", report.getAsJsonArray("failures").isEmpty() ? "passed" : "failed");
        report.addProperty("safe_client_shutdown_requested", true);
        write(report);
        // Stops only this deliberately launched, unconnected Minecraft instance through its normal lifecycle.
        mc.stop();
    }
    private static void write(JsonObject report) {
        Path output = Path.of(System.getProperty("modern_ite.clientAuditOutput", "build/verification/client-audit.json")).toAbsolutePath().normalize();
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            LogUtils.getLogger().info("MODERN_ITE_CLIENT_AUDIT {} report={}", report.get("status").getAsString(), output);
        } catch (Exception failure) {
            LogUtils.getLogger().error("Cannot write Modern ITE client audit to {}", output, failure);
        }
    }
}
