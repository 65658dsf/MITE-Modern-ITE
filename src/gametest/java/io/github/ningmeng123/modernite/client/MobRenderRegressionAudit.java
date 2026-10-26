package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.client.entity.BucketHelmetModel;
import io.github.ningmeng123.modernite.client.entity.SpeciesHeadLayer;
import io.github.ningmeng123.modernite.client.entity.VariantCreeperModel;
import io.github.ningmeng123.modernite.client.entity.VariantZombieModel;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Opt-in native GPU regression in a uniquely created disposable world. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT)
public final class MobRenderRegressionAudit {
    private static final int SIZE=320;
    private static final List<ExtremeKind> FOCUS=List.of(ExtremeKind.WITCH_LORD,ExtremeKind.WANDERING_WITCH,
            ExtremeKind.ANNIHILATION_SKELETON,ExtremeKind.ZOMBIE_PIGMAN_LORD,ExtremeKind.ZOMBIE_FLY_AWAY,
            ExtremeKind.ZOMBIE_GHAST,ExtremeKind.DRAGON_ZOMBIE,ExtremeKind.ZOMBIE_DOOR,ExtremeKind.ZOMBIE_IRON_BUCKET,
            ExtremeKind.FISHING_ROD_ZOMBIE,ExtremeKind.WITHER_SKELETON_ARROW,ExtremeKind.RUN_CREEPER,
            ExtremeKind.PHASE_CREEPER,ExtremeKind.CHAOS_CREEPER,ExtremeKind.SKELETON_ZOMBIE);
    private static boolean baked,started,finished,capturing,postRenderSeen,startupReady;
    private static int waiting,readyTicks,firstServerTick=-1;
    private static String worldName;
    private static AuditScreen screen;
    private static Mob renderedMob;
    private static JsonObject report;
    private MobRenderRegressionAudit() {}

    @EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void complete(ModelEvent.BakingCompleted event) {
            if(enabled())baked=true;
        }
    }
    private static boolean enabled(){return Boolean.getBoolean("modern_ite.mobRenderAudit");}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!enabled()||finished||!baked)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        try {
            if(!started) {
                // Never attach this test to an already played world, multiplayer session or other server.
                if(mc.level!=null||mc.getConnection()!=null||mc.getSingleplayerServer()!=null) {
                    report=initialReport();report.getAsJsonArray("failures").add("Refused existing world or connection");
                    report.addProperty("status","aborted_existing_world");report.addProperty("safe_client_shutdown_requested",false);
                    finished=true;write();return;
                }
                if(mc.screen instanceof AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
                if(!(mc.screen instanceof TitleScreen))return;
                started=true;report=initialReport();
                report.addProperty("original_render_distance",mc.options.renderDistance().get());
                report.addProperty("original_simulation_distance",mc.options.simulationDistance().get());
                report.addProperty("original_pause_on_lost_focus",mc.options.pauseOnLostFocus);
                // This deliberately launched instance owns its options and fresh world. Small
                // distances avoid creating unrelated startup chunks during a renderer-only audit.
                // Native simulation-distance validation starts at5: set(2) silently restores12.
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
                report.addProperty("audit_render_distance",mc.options.renderDistance().get());
                report.addProperty("audit_simulation_distance",mc.options.simulationDistance().get());
                report.addProperty("audit_pause_on_lost_focus",mc.options.pauseOnLostFocus);
                worldName="modern-ite-render-audit-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8);
                report.addProperty("disposable_world",worldName);report.addProperty("created_only_new_world",true);
                report.addProperty("game_directory",mc.gameDirectory.getAbsolutePath());
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(183994862L,false,false),
                        registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                return;
            }
            check(++waiting<2400,"Timed out loading the new disposable render world");
            if(mc.level!=null&&mc.player!=null&&mc.getConnection()!=null&&mc.getSingleplayerServer()!=null) {
                check(ownedWorld(mc),"The active world does not belong to this render audit");
                if(firstServerTick<0)firstServerTick=mc.getSingleplayerServer().getTickCount();
                readyTicks++;int serverTicks=mc.getSingleplayerServer().getTickCount()-firstServerTick;
                if(screen==null){screen=new AuditScreen();mc.setScreen(screen);}
                if(!startupReady&&readyTicks>=100&&serverTicks>=100){
                    report.addProperty("client_ready_ticks_before_capture",readyTicks);
                    report.addProperty("server_ready_ticks_before_capture",serverTicks);
                    startupReady=true;
                }
            }
        }catch(Throwable failure){fail("client lifecycle",failure);finish(mc);}
    }

    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?,?> event) {
        if(renderedMob!=null&&event.getEntity()==renderedMob)postRenderSeen=true;
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!enabled()||finished||capturing||!startupReady||screen==null||screen.frames<1)return;
        Minecraft mc=Minecraft.getInstance();if(mc.screen!=screen||mc.getOverlay()!=null)return;
        capturing=true;
        try {
            check(ownedWorld(mc),"Only the newly created audit world may be rendered");
            if (Boolean.getBoolean("modern_ite.modelCompatibilityAudit")) {
                report.addProperty("scope", "Five source species with borrowed CEM animation, native UVs, attached ghast head, uppercut and stable creeper faces");
                report.add("model_compatibility", ModelCompatibilityAudit.capture(mc, screenshots()));
                return;
            }
            if (Boolean.getBoolean("modern_ite.reversedPlayerAudit")) {
                report.addProperty("scope", "Native upside-down player predicate, pose matrices and equipped GPU renders for both skins");
                report.add("reversed_player", ReversedPlayerClientAudit.capture(mc, screenshots()));
                return;
            }
            if (Boolean.getBoolean("modern_ite.bucketHeadAudit")) {
                report.addProperty("scope", "Bucket and pumpkin screen occlusion in all three perspectives, plus independent bucket head layer");
                report.add("bucket_head", BucketHeadClientAudit.capture(mc, screenshots()));
                return;
            }
            if (Boolean.getBoolean("modern_ite.defenseAudit")) {
                report.addProperty("scope", "Native shield and door guard client ticks, bounded side turns and GPU pose direction");
                report.add("defense_intent", DefenseIntentClientAudit.capture(mc, screenshots()));
                return;
            }
            if (Boolean.getBoolean("modern_ite.forgingAudit")) {
                report.addProperty("scope", "Forging UI hammer animation, impact sparks and inactive-state GPU controls");
                JsonObject forging = ForgingClientAudit.capture(mc);
                report.add("forging", forging);
                report.addProperty("render_target_width", forging.get("render_width").getAsInt());
                report.addProperty("render_target_height", forging.get("render_height").getAsInt());
                return;
            }
            if (Boolean.getBoolean("modern_ite.lanternAudit")) {
                report.addProperty("scope", "Three retained lantern models, native GPU item displays and instant local lightmap switching");
                report.add("lanterns", LanternClientAudit.capture(mc));
                return;
            }
            if (Boolean.getBoolean("modern_ite.doorGuardAudit")) {
                report.addProperty("scope", "Door zombie native GPU poses, both main arms, walking, melee, empty-hand and shared-model controls");
                JsonObject facts=new JsonObject();facts.addProperty("entity",ExtremeKind.ZOMBIE_DOOR.id());report.add("door_guard",facts);
                sample(mc,ExtremeKind.ZOMBIE_DOOR,facts);
                return;
            }
            if (Boolean.getBoolean("modern_ite.bigHeadAudit")) {
                report.addProperty("scope", "Player big-head model, native FOV hook and effect cleanup");
                verifyPlayerHead(mc);
                return;
            }
            if (Boolean.getBoolean("modern_ite.bigHeadZombieAudit")) {
                report.addProperty("scope", "Big-head zombie GPU silhouette with native and resource-pack models");
                JsonObject facts=new JsonObject();report.add("big_head_zombie",facts);
                sample(mc,ExtremeKind.FISHING_ROD_ZOMBIE,facts);
                return;
            }
            if (Boolean.getBoolean("modern_ite.endermanHeadAudit") || Boolean.getBoolean("modern_ite.mobAdditionsAudit")) {
                report.addProperty("scope", "Enderman head and native eyes; rabid dog model, native texture and new status icons");
                report.add("enderman_big_head", EndermanHeadClientAudit.capture(mc, screenshots()));
                report.add("rabid_dog", RabidDogClientAudit.capture(mc, screenshots()));
                return;
            }
            JsonArray identities=new JsonArray();report.add("texture_lookups",identities);
            for(ExtremeKind kind:ExtremeKind.values()) {
                JsonObject facts=new JsonObject();identities.add(facts);facts.addProperty("entity",kind.id());
                try {
                    Mob mob=create(mc,kind);
                    try {texture(mc,mob,facts,"texture");facts.addProperty("lookup_succeeded",true);}
                    finally{mob.discard();}
                }catch(Throwable failure){fail("texture lookup "+kind.id(),failure);}
            }
            report.addProperty("entity_texture_lookup_count",identities.size());
            check(identities.size()==45,"Expected 45 active identities after removing the ghast and duplicate pillagers");
            JsonArray samples=new JsonArray();report.add("native_gpu_samples",samples);
            for(ExtremeKind kind:FOCUS) {
                JsonObject facts=new JsonObject();samples.add(facts);facts.addProperty("entity",kind.id());
                try{sample(mc,kind,facts);}catch(Throwable failure){fail("native GPU render "+kind.id(),failure);}
            }
            report.addProperty("focused_gpu_sample_count",samples.size());
            verifyPlayerHead(mc);
            report.add("enderman_big_head", EndermanHeadClientAudit.capture(mc, screenshots()));
            report.add("rabid_dog", RabidDogClientAudit.capture(mc, screenshots()));
            verifyParticleAuras(mc);
            report.add("aura_previews",MobAuraPreviews.capture(mc));
            report.add("lanterns",LanternClientAudit.capture(mc));
        }catch(Throwable failure){fail("render audit",failure);}
        finally{finish(mc);}
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void verifyPlayerHead(Minecraft mc){
        LivingEntityRenderer renderer=(LivingEntityRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
        HumanoidModel model=(HumanoidModel)renderer.getModel();
        var effect=ExtremeEffects.VALUES.get("big_head");
        MobEffectInstance prior=mc.player.getEffect(effect);MobEffectInstance saved=prior==null?null:new MobEffectInstance(prior);
        JsonObject facts=new JsonObject();report.add("player_big_head",facts);
        try{
            mc.player.addEffect(new MobEffectInstance(effect,200));
            model.setupAnim(mc.player,0,0,0,0,0);
            facts.addProperty("effect_head_scale",model.head.xScale);facts.addProperty("effect_hat_scale",model.hat.xScale);
            check(model.head.xScale==2&&model.hat.xScale==2,"The real player renderer must enlarge both head layers after setupAnim");
            mc.player.removeEffect(effect);model.setupAnim(mc.player,0,0,0,0,0);
            facts.addProperty("cleared_head_scale",model.head.xScale);facts.addProperty("cleared_hat_scale",model.hat.xScale);
            check(model.head.xScale==1&&model.hat.xScale==1,"Removing big_head must restore the shared player model");
            facts.add("movement_and_fov",BigHeadClientAudit.capture(mc));
        }finally{
            mc.player.removeEffect(effect);if(saved!=null)mc.player.addEffect(saved);
            model.setupAnim(mc.player,0,0,0,0,0);
        }
    }

    private static void verifyParticleAuras(Minecraft mc) {
        JsonObject facts=new JsonObject();report.add("particle_auras",facts);
        for(var kind:new ExtremeKind[]{ExtremeKind.UNDYING_ZOMBIE_EMPEROR,ExtremeKind.SKELETON_LIGHTNING,
                ExtremeKind.SKELETON_FLOATING,ExtremeKind.SKELETON_BLAZE,ExtremeKind.ANNIHILATION_SKELETON}) {
            Mob mob=create(mc,kind);mob.setPos(mc.player.position().add(2,0,0));
            mob.tickCount=-mob.getId()*7;
            mc.level.addEntity(mob);
            try {
                int before=Integer.parseInt(mc.particleEngine.countParticles());
                io.github.ningmeng123.modernite.client.ExtremeMobParticles.tick(new ClientTickEvent.Post());
                mc.particleEngine.tick();
                int added=Integer.parseInt(mc.particleEngine.countParticles())-before;
                facts.addProperty(kind.id(),added);
                check(added>0,"The actual client particle event must emit the requested aura: "+kind);
            } finally {mob.discard();}
        }
    }

    private static Mob create(Minecraft mc,ExtremeKind kind) {
        Mob mob=ExtremeMobs.type(kind).create(mc.level);check(mob!=null,"Entity factory returned null: "+kind.id());
        mob.setPos(mc.player.getX(),mc.player.getY(),mc.player.getZ());mob.setNoAi(true);mob.tickCount=0;
        mob.setYRot(0);mob.setXRot(0);mob.yBodyRot=mob.yBodyRotO=0;mob.yHeadRot=mob.yHeadRotO=0;
        // Detached visual samples have no server AI, random equipment or world-side interactions.
        return mob;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static ResourceLocation texture(Minecraft mc,Mob mob,JsonObject facts,String key) {
        EntityRenderer renderer=mc.getEntityRenderDispatcher().getRenderer(mob);
        check(renderer!=null,"No native renderer for "+mob.getType());
        ResourceLocation location=renderer.getTextureLocation(mob);
        check(location!=null&&mc.getResourceManager().getResource(location).isPresent(),"Native renderer selected a missing texture: "+location);
        facts.addProperty(key,location.toString());facts.addProperty("renderer",renderer.getClass().getName());return location;
    }

    private static void sample(Minecraft mc,ExtremeKind kind,JsonObject facts)throws Exception {
        Mob mob=create(mc,kind);
        try {
            if(kind==ExtremeKind.ZOMBIE_DOOR)mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_DOOR));
            if(kind==ExtremeKind.ZOMBIE_IRON_BUCKET)mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ExtremeContent.BUCKET_HELMET.get()));
            ResourceLocation normal=texture(mc,mob,facts,"texture");
            try(NativeImage actual=capture(mc,mob)) {
                PixelStats stats=stats(actual);facts.add("pixels",stats.json());
                check(stats.body>200,"GPU output contains too few non-background body pixels");
                Path png=screenshots().resolve(kind.id()+".png");Files.createDirectories(png.getParent());actual.writeToFile(png);
                facts.addProperty("screenshot",png.toString());
                if(kind==ExtremeKind.ZOMBIE_FLY_AWAY) {
                    var renderer=mc.getEntityRenderDispatcher().getRenderer(mob);
                    check(renderer instanceof LivingEntityRenderer<?,?> living&&living.getModel() instanceof HumanoidModel<?>,"The fly-away zombie uses its humanoid body model");
                    var model=(HumanoidModel<?>)((LivingEntityRenderer<?,?>)renderer).getModel();
                    check(uvOutOfRange(model,facts,"actual_body_uv")==0,"The actual head, torso, arms and legs must all stay within normalized texture UVs");
                    double actualMaximumV=maximumV(facts.getAsJsonObject("actual_body_uv"));facts.addProperty("actual_maximum_v",actualMaximumV);
                    check(Math.abs(actualMaximumV-.5D)<.0001D,"The compressed source image uses the64-high logical mesh, with body UV maxV0.5");
                    Field cubes=ModelPart.class.getDeclaredField("cubes");cubes.setAccessible(true);
                    int hatCubes=((List<?>)cubes.get(model.hat)).size();facts.addProperty("actual_hat_cubes",hatCubes);
                    check(hatCubes==0,"The modern adaptation must remove the opaque, incorrectly placed outer head shell");
                    try(NativeImage head=headOnly(mc,mob);NativeImage hat=plain64HatControl(mc,mob)) {
                        int black=blackPixels(head),covered=blackPixels(hat);
                        facts.addProperty("head_black_eye_pixels",black);facts.addProperty("plain64_hat_head_black_pixels",covered);
                        Path headPng=screenshots().resolve("zombie_fly_away_head.png"),hatPng=screenshots().resolve("negative-control-plain64-hat-zombie_fly_away.png");
                        head.writeToFile(headPng);hat.writeToFile(hatPng);facts.addProperty("head_screenshot",headPng.toString());facts.addProperty("plain64_hat_negative_control",hatPng.toString());
                        check(black>8,"The actual head-only native render must show the source's black eyes");
                        check(covered<black*.5D,"A plain64-high mesh retaining the opaque hat must occlude the black eyes in the negative control");
                    }
                    try(NativeImage bad=legacyZombie32Control(mc,mob,facts)) {
                        int changed=changedBody(actual,bad);double fraction=changed/(double)stats.body;
                        facts.addProperty("legacy32_body_pixels_changed",changed);facts.addProperty("legacy32_body_changed_fraction",fraction);
                        Path badPng=screenshots().resolve("negative-control-legacy32-zombie_fly_away.png");bad.writeToFile(badPng);
                        facts.addProperty("legacy32_negative_control",badPng.toString());
                        check(fraction>.20D,"The former 32-high UV model must visibly differ across more than20 percent of the fixed body: "+fraction);
                    }
                }
                if(kind==ExtremeKind.WITCH_LORD||kind==ExtremeKind.WANDERING_WITCH||kind==ExtremeKind.ANNIHILATION_SKELETON) {
                    // Reference uses the same real renderer, pose and GPU path, with layers removed only
                    // during this isolated sample, then restored in finally. No equipment is present.
                    try(NativeImage without=withoutLayers(mc,mob)) {
                        PixelStats baseline=stats(without);int changed=changedBody(without,actual);
                        double fraction=changed/(double)Math.max(1,baseline.body);
                        facts.addProperty("body_pixels_changed_by_layers",changed);facts.addProperty("body_layer_changed_fraction",fraction);
                        check(baseline.body>200,"The native base-body reference must be visible");
                        if(kind!=ExtremeKind.ANNIHILATION_SKELETON)check(changed>0,"The front-facing emissive eyes must actually change visible GPU pixels");
                        check(fraction<.15D,"Glow layers changed more than 15 percent of the native body; possible white mask ignoring alpha: "+fraction);
                        if(kind==ExtremeKind.ANNIHILATION_SKELETON)try(NativeImage dim=capture(mc,mob,true);NativeImage dimBase=withoutLayers(mc,mob,true)) {
                            int dimChanged=changedBody(dimBase,dim);double dimFraction=dimChanged/(double)Math.max(1,stats(dimBase).body);
                            facts.addProperty("dim_light_body_pixels_changed_by_layers",dimChanged);facts.addProperty("dim_light_body_layer_changed_fraction",dimFraction);
                            check(dimChanged>0&&dimFraction<.15D,"Dim native draw must visibly illuminate the eyes without changing the body: "+dimFraction);
                            Path dimPng=screenshots().resolve("annihilation_skeleton_dim.png");dim.writeToFile(dimPng);facts.addProperty("dim_screenshot",dimPng.toString());
                        }
                        if(kind==ExtremeKind.WITCH_LORD||kind==ExtremeKind.ANNIHILATION_SKELETON)try(NativeImage negative=legacyEyesControl(mc,mob,
                                ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"textures/entity/source/"+(kind==ExtremeKind.ANNIHILATION_SKELETON?"skeleton/annihilation_skeleton_glow":"witch_lord_glow")+".png"))) {
                            double negativeFraction=changedBody(without,negative)/(double)baseline.body;
                            facts.addProperty("legacy_eyes_negative_control_changed_fraction",negativeFraction);
                            check(negativeFraction>.15D,"The same GPU assertion must reject the former additive white-mask render");
                            Path control=screenshots().resolve("negative-control-legacy-eyes-"+kind.id()+".png");negative.writeToFile(control);
                            facts.addProperty("legacy_eyes_negative_control",control.toString());
                        }
                    }
                }
                verifyVariant(mc,mob,kind,actual,facts);
                facts.addProperty("native_draw_succeeded",true);
            }
        }finally{mob.discard();}
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void verifyVariant(Minecraft mc,Mob mob,ExtremeKind kind,NativeImage actual,JsonObject facts)throws Exception {
        LivingEntityRenderer renderer=(LivingEntityRenderer)mc.getEntityRenderDispatcher().getRenderer(mob);
        Object model=renderer.getModel();facts.addProperty("model",model.getClass().getName());
        if(kind==ExtremeKind.SKELETON_ZOMBIE)SkeletonZombieModelAudit.verify(mc,mob,facts);
        if(kind==ExtremeKind.WITCH_LORD||kind==ExtremeKind.WANDERING_WITCH)
            WitchFreshAnimationsAudit.verify(mc,mob,facts,screenshots());
        if(kind==ExtremeKind.ZOMBIE_GHAST||kind==ExtremeKind.DRAGON_ZOMBIE){
            check(model instanceof VariantZombieModel,"Species head requires the dedicated headless humanoid");
            Field cubes=ModelPart.class.getDeclaredField("cubes");cubes.setAccessible(true);
            check(((List)cubes.get(((VariantZombieModel)model).head)).isEmpty(),"The ordinary zombie head must be removed");
            Field layers=LivingEntityRenderer.class.getDeclaredField("layers");layers.setAccessible(true);
            check(((List<?>)layers.get(renderer)).stream().anyMatch(SpeciesHeadLayer.class::isInstance),"A real species head layer must be installed");
            try(NativeImage without=withoutLayers(mc,mob)){
                int changed=changedAll(actual,without);facts.addProperty("species_head_changed_pixels",changed);
                check(changed>100,"The ghast or dragon head must produce visible native pixels");
            }
        }
        if(kind==ExtremeKind.ZOMBIE_DOOR){
            try(NativeImage without=withoutLayers(mc,mob)){
                int changed=changedAll(actual,without);facts.addProperty("door_shield_changed_pixels",changed);
                check(changed>100,"The full two-panel door shield must be visible");
            }
            check(mob instanceof ExtremeMobs.ZombieVariant,"The door zombie must expose its synchronized guard state");
            facts.add("door_guard",DoorGuardClientAudit.capture(mc,(ExtremeMobs.ZombieVariant)mob,screenshots()));
        }
        if(kind==ExtremeKind.ZOMBIE_IRON_BUCKET){
            ItemStack bucket=mob.getItemBySlot(EquipmentSlot.HEAD);
            var replacement=IClientItemExtensions.of(bucket).getHumanoidArmorModel(mob,bucket,EquipmentSlot.HEAD,(HumanoidModel<?>)model);
            check(replacement instanceof BucketHelmetModel,"The wearable bucket must replace the vanilla iron helmet model");
            var bucketTexture=bucket.getItem().getArmorTexture(bucket,mob,EquipmentSlot.HEAD,ArmorMaterials.IRON.value().layers().getFirst(),false);
            check(bucketTexture!=null,"The bucket needs its own opaque metal texture rather than a helmet's face cutouts");
            facts.addProperty("bucket_armor_texture",bucketTexture.toString());
            try(var stream=mc.getResourceManager().getResourceOrThrow(bucketTexture).open();var metal=NativeImage.read(stream)){
                int transparent=0;
                for(int y=0;y<metal.getHeight();y++)for(int x=0;x<metal.getWidth();x++)if((metal.getPixelRGBA(x,y)>>>24)==0)transparent++;
                facts.addProperty("bucket_texture_transparent_pixels",transparent);
                check(transparent==0,"The bucket side walls and roof must remain opaque");
            }
            mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
            try(NativeImage iron=capture(mc,mob)){
                int changed=changedAll(actual,iron);facts.addProperty("bucket_vs_helmet_changed_pixels",changed);
                check(changed>50,"The native bucket silhouette must visibly differ from an iron helmet");
            }finally{mob.setItemSlot(EquipmentSlot.HEAD,bucket);}
        }
        if(kind==ExtremeKind.FISHING_ROD_ZOMBIE){
            check(model instanceof VariantZombieModel,"The big-head zombie requires its dedicated model");
            var zombie=(VariantZombieModel)model;
            Field modelField=LivingEntityRenderer.class.getDeclaredField("model");modelField.setAccessible(true);
            var normal=new ZombieModel<Zombie>(mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE));
            JsonArray frames=new JsonArray();facts.add("rendered_head_frames",frames);
            try{
                for(int frame=0;frame<4;frame++){
                    try(NativeImage enlarged=capture(mc,mob)){
                        JsonObject sample=new JsonObject();frames.add(sample);
                        sample.addProperty("frame",frame);
                        sample.addProperty("head_scale",zombie.head.xScale);sample.addProperty("hat_scale",zombie.hat.xScale);
                        check(zombie.head.xScale==2&&zombie.hat.xScale>=2&&zombie.hat.xScale<=3,
                                "Real redraws must preserve doubled heads without accumulating scale");
                        modelField.set(renderer,normal);
                        try(NativeImage control=capture(mc,mob)){
                            int enlargedWidth=headWidth(enlarged),normalWidth=headWidth(control);
                            int changed=changedAll(enlarged,control);
                            sample.addProperty("enlarged_head_pixel_width",enlargedWidth);
                            sample.addProperty("normal_head_pixel_width",normalWidth);
                            sample.addProperty("head_changed_pixels",changed);
                            enlarged.writeToFile(screenshots().resolve("fishing_rod_zombie_frame_"+frame+".png"));
                            if(frame==0)control.writeToFile(screenshots().resolve("fishing_rod_zombie_normal_head.png"));
                            check(changed>100&&enlargedWidth>normalWidth*1.45F,
                                    "The rendered head must visibly exceed an ordinary zombie head, including resource-pack geometry: "+enlargedWidth+" vs "+normalWidth);
                        }finally{modelField.set(renderer,model);}
                    }
                }
            }finally{modelField.set(renderer,model);}
        }
        if(kind==ExtremeKind.ZOMBIE_FLY_AWAY){
            var strong=(VariantZombieModel)model;strong.attackTime=.5F;strong.setupAnim((Zombie)mob,0,0,0,0,0);
            facts.addProperty("uppercut_right_arm",strong.rightArm.xRot);facts.addProperty("uppercut_left_arm",strong.leftArm.xRot);
            check(strong.rightArm.xRot==strong.leftArm.xRot&&strong.rightArm.xRot< -2,"Both arms must perform the golem uppercut together");
            float priorAttack=mob.attackAnim,priorOldAttack=mob.oAttackAnim;
            mob.attackAnim=mob.oAttackAnim=.5F;
            try(NativeImage uppercut=capture(mc,mob)){
                int changed=changedAll(actual,uppercut);facts.addProperty("uppercut_changed_pixels",changed);
                check(changed>100,"The native strong-zombie uppercut must visibly change both arms");
                Path uppercutPng=screenshots().resolve("zombie_fly_away_uppercut.png");uppercut.writeToFile(uppercutPng);
                facts.addProperty("uppercut_screenshot",uppercutPng.toString());
            }finally{mob.attackAnim=priorAttack;mob.oAttackAnim=priorOldAttack;strong.attackTime=0;}
        }
        if(kind==ExtremeKind.WITHER_SKELETON_ARROW)check(renderer.getTextureLocation(mob).toString().equals("minecraft:textures/entity/skeleton/wither_skeleton.png"),"The wither archer requires the native wither skeleton skin");
        if(kind==ExtremeKind.RUN_CREEPER||kind==ExtremeKind.PHASE_CREEPER||kind==ExtremeKind.CHAOS_CREEPER){
            check(model instanceof VariantCreeperModel,"The creeper variant must use its distinct geometry and animation");
            if(kind==ExtremeKind.PHASE_CREEPER||kind==ExtremeKind.CHAOS_CREEPER)try(NativeImage without=withoutLayers(mc,mob)){
                int changed=changedAll(actual,without);facts.addProperty("variant_effect_layer_pixels",changed);
                check(changed>20,"The glowing phase eyes or rotating chaos arrow must be visible");
            }
            if(kind==ExtremeKind.CHAOS_CREEPER){
                var chaos=(VariantCreeperModel)model;var entity=(net.minecraft.world.entity.monster.Creeper)mob;
                chaos.setupAnim(entity,0,0,0,0,0);float before=chaos.chaosArrow().yRot;
                chaos.setupAnim(entity,0,0,20,0,0);float after=chaos.chaosArrow().yRot;
                check(before!=after,"The visible chaos arrow must rotate with time");facts.addProperty("arrow_rotation_at_tick20",after);
            }
        }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static NativeImage withoutLayers(Minecraft mc,Mob mob)throws Exception {
        return withoutLayers(mc,mob,false);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static NativeImage withoutLayers(Minecraft mc,Mob mob,boolean dim)throws Exception {
        var renderer=mc.getEntityRenderDispatcher().getRenderer(mob);
        check(renderer instanceof LivingEntityRenderer,"Reference requires a living renderer");
        Field field=LivingEntityRenderer.class.getDeclaredField("layers");field.setAccessible(true);
        List layers=(List)field.get(renderer);List saved=new ArrayList(layers);
        layers.clear();try{return capture(mc,mob,dim);}finally{layers.addAll(saved);}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static NativeImage legacyEyesControl(Minecraft mc,Mob mob,ResourceLocation texture)throws Exception {
        LivingEntityRenderer renderer=(LivingEntityRenderer)mc.getEntityRenderDispatcher().getRenderer(mob);
        Field field=LivingEntityRenderer.class.getDeclaredField("layers");field.setAccessible(true);
        List layers=(List)field.get(renderer);List saved=new ArrayList(layers);layers.clear();
        layers.add(new EyesLayer(renderer) {
            @Override public RenderType renderType(){return RenderType.eyes(texture);}
        });
        try{return capture(mc,mob);}finally{layers.clear();layers.addAll(saved);}
    }
    private static NativeImage legacyZombie32Control(Minecraft mc,Mob mob,JsonObject facts)throws Exception {
        var renderer=mc.getEntityRenderDispatcher().getRenderer(mob);
        Field field=LivingEntityRenderer.class.getDeclaredField("model");field.setAccessible(true);Object saved=field.get(renderer);
        var legacy=new ZombieModel<Zombie>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,32).bakeRoot());
        check(uvOutOfRange(legacy,facts,"legacy32_body_uv")==0,"The old32-high mesh has legal normalized UVs; its vertical basis is the source of the mismatch");
        double oldMaximumV=maximumV(facts.getAsJsonObject("legacy32_body_uv"));facts.addProperty("legacy32_maximum_v",oldMaximumV);
        check(Math.abs(oldMaximumV-1D)<.0001D,"The old32-high control must normalize body coordinates to maxV1.0");
        field.set(renderer,legacy);try{return capture(mc,mob);}finally{field.set(renderer,saved);}
    }
    private static double maximumV(JsonObject parts){return parts.entrySet().stream().mapToDouble(entry->entry.getValue().getAsJsonObject().get("max_v").getAsDouble()).max().orElseThrow();}
    private static NativeImage plain64HatControl(Minecraft mc,Mob mob)throws Exception {
        var renderer=mc.getEntityRenderDispatcher().getRenderer(mob);Field field=LivingEntityRenderer.class.getDeclaredField("model");field.setAccessible(true);Object saved=field.get(renderer);
        field.set(renderer,new ZombieModel<Zombie>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64).bakeRoot()));
        try{return headOnly(mc,mob);}finally{field.set(renderer,saved);}
    }
    private static NativeImage headOnly(Minecraft mc,Mob mob)throws Exception {
        var model=(HumanoidModel<?>)((LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(mob)).getModel();
        ModelPart[] parts={model.head,model.hat,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};boolean[] saved=new boolean[parts.length];
        for(int i=0;i<parts.length;i++){saved[i]=parts[i].visible;parts[i].visible=i<2;}
        try{return capture(mc,mob);}finally{for(int i=0;i<parts.length;i++)parts[i].visible=saved[i];}
    }
    private static int blackPixels(NativeImage image){int count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int p=image.getPixelRGBA(x,y);if((p&255)<=12&&(p>>>8&255)<=12&&(p>>>16&255)<=12)count++;}return count;}
    private static int uvOutOfRange(HumanoidModel<?> model,JsonObject facts,String key)throws Exception {
        Field cubesField=ModelPart.class.getDeclaredField("cubes");cubesField.setAccessible(true);
        Field polygonsField=ModelPart.Cube.class.getDeclaredField("polygons");polygonsField.setAccessible(true);
        JsonObject parts=new JsonObject();facts.add(key,parts);int invalid=0;
        var body=java.util.Map.of("head",model.head,"torso",model.body,"right_arm",model.rightArm,"left_arm",model.leftArm,"right_leg",model.rightLeg,"left_leg",model.leftLeg);
        for(var part:body.entrySet()) {
            int vertices=0,bad=0;float minimumU=Float.POSITIVE_INFINITY,minimumV=Float.POSITIVE_INFINITY,maximumU=Float.NEGATIVE_INFINITY,maximumV=Float.NEGATIVE_INFINITY;
            for(Object cube:(List<?>)cubesField.get(part.getValue()))for(Object polygon:(Object[])polygonsField.get(cube)) {
                Field verticesField=polygon.getClass().getDeclaredField("vertices");verticesField.setAccessible(true);
                for(Object vertex:(Object[])verticesField.get(polygon)) {
                    Field uField=vertex.getClass().getDeclaredField("u"),vField=vertex.getClass().getDeclaredField("v");uField.setAccessible(true);vField.setAccessible(true);
                    float u=uField.getFloat(vertex),v=vField.getFloat(vertex);vertices++;
                    minimumU=Math.min(minimumU,u);maximumU=Math.max(maximumU,u);minimumV=Math.min(minimumV,v);maximumV=Math.max(maximumV,v);
                    if(!Float.isFinite(u)||!Float.isFinite(v)||u<0||v<0||u>1||v>1)bad++;
                }
            }
            check(vertices>0,"The actual body part must contain baked geometry: "+part.getKey());
            JsonObject result=new JsonObject();parts.add(part.getKey(),result);result.addProperty("vertices",vertices);result.addProperty("outside_normalized_uv",bad);
            result.addProperty("min_u",minimumU);result.addProperty("max_u",maximumU);result.addProperty("min_v",minimumV);result.addProperty("max_v",maximumV);invalid+=bad;
        }return invalid;
    }
    private static NativeImage capture(Minecraft mc,Mob mob){return capture(mc,mob,false);}
    private static NativeImage capture(Minecraft mc,Mob mob,boolean dim){return capture(mc,mob,dim,.35F);}
    static NativeImage capture(Minecraft mc,Mob mob,boolean dim,float viewYaw) {
        return capture(mc,mob,dim,viewYaw,110F);
    }
    static NativeImage capture(Minecraft mc,Mob mob,boolean dim,float viewYaw,float scale) {
        TextureTarget target=new TextureTarget(SIZE,SIZE,true,Minecraft.ON_OSX);
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());VertexSorting sorting=RenderSystem.getVertexSorting();
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try {
            target.setClearColor(16F/255,24F/255,32F/255,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,SIZE,SIZE,0,-1000,1000),VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(1,1,1,1);
            var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            renderedMob=mob;postRenderSeen=false;
            var rotation=new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI+viewYaw);
            if(!dim)InventoryScreen.renderEntityInInventory(graphics,SIZE/2F,SIZE-35F,scale,new Vector3f(),rotation,null,mob);
            else {
                // InventoryScreen's exact native transform/lighting/dispatch sequence, with packed
                // light0 instead of its hard-coded full light. This distinguishes source-identical
                // red base/eye texels from a missing emissive layer.
                graphics.pose().pushPose();graphics.pose().translate(SIZE/2F,SIZE-35F,50);
                graphics.pose().scale(scale,scale,-scale);graphics.pose().mulPose(rotation);Lighting.setupForEntityInInventory();
                var dispatcher=mc.getEntityRenderDispatcher();dispatcher.setRenderShadow(false);
                try{RenderSystem.runAsFancy(()->dispatcher.render(mob,0,0,0,0,1,graphics.pose(),graphics.bufferSource(),0));graphics.flush();}
                finally{dispatcher.setRenderShadow(true);graphics.pose().popPose();Lighting.setupFor3DItems();}
            }
            graphics.flush();mc.renderBuffers().bufferSource().endBatch();
            check(postRenderSeen,"The actual native LivingEntityRenderer post-render event must fire");
            return Screenshot.takeScreenshot(target);
        }finally {
            renderedMob=null;mc.renderBuffers().bufferSource().endBatch();
            modelView.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
            mc.getMainRenderTarget().bindWrite(true);target.destroyBuffers();
        }
    }

    private record PixelStats(int body,int white) {
        JsonObject json(){JsonObject j=new JsonObject();j.addProperty("body",body);j.addProperty("near_pure_white",white);j.addProperty("near_pure_white_fraction",white/(double)Math.max(1,body));return j;}
    }
    private static boolean foreground(int p) {return Math.abs((p&255)-16)+Math.abs((p>>>8&255)-24)+Math.abs((p>>>16&255)-32)>18;}
    private static int headWidth(NativeImage image) {
        int width=0;
        // Restrict to the head: a normal zombie's raised hands reach the rows below this.
        for(int y=0;y<100;y++){
            int first=SIZE,last=-1;
            for(int x=0;x<SIZE;x++)if(foreground(image.getPixelRGBA(x,y))){first=Math.min(first,x);last=x;}
            width=Math.max(width,last-first+1);
        }
        return width;
    }
    private static PixelStats stats(NativeImage image) {
        int body=0,white=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int p=image.getPixelRGBA(x,y);if(!foreground(p))continue;body++;
            if((p&255)>=253&&(p>>>8&255)>=253&&(p>>>16&255)>=253)white++;
        }return new PixelStats(body,white);
    }
    private static int delta(int a,int b){return Math.abs((a&255)-(b&255))+Math.abs((a>>>8&255)-(b>>>8&255))+Math.abs((a>>>16&255)-(b>>>16&255));}
    private static int changedBody(NativeImage base,NativeImage actual) {
        int changed=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(foreground(base.getPixelRGBA(x,y))&&delta(base.getPixelRGBA(x,y),actual.getPixelRGBA(x,y))>12)changed++;return changed;
    }
    private static int changedAll(NativeImage a,NativeImage b) {
        int changed=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(delta(a.getPixelRGBA(x,y),b.getPixelRGBA(x,y))>12)changed++;return changed;
    }
    private static JsonObject initialReport() {
        JsonObject j=new JsonObject();j.addProperty("author","NingMeng123");j.addProperty("timestamp",Instant.now().toString());
        j.addProperty("scope","real ClientLevel; all 45 active texture lookups; "+FOCUS.size()+" native InventoryScreen GPU draws; alpha/body and zombie UV controls; variant heads, shield, wearable bucket and attack poses");
        j.addProperty("property","modern_ite.mobRenderAudit=true");j.addProperty("existing_world_loaded",false);j.addProperty("multiplayer_connected",false);
        j.addProperty("render_target_width",SIZE);j.addProperty("render_target_height",SIZE);j.add("failures",new JsonArray());return j;
    }
    private static boolean ownedWorld(Minecraft mc){return started&&worldName!=null&&mc.getSingleplayerServer()!=null&&worldName.equals(mc.getSingleplayerServer().getWorldData().getLevelName());}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void fail(String label,Throwable failure) {
        if(report==null)report=initialReport();report.getAsJsonArray("failures").add(label+": "+failure);
        LogUtils.getLogger().error("MODERN_ITE_MOB_RENDER_AUDIT {}",label,failure);
    }
    private static void finish(Minecraft mc) {
        if(finished)return;finished=true;if(report==null)report=initialReport();
        boolean safe=ownedWorld(mc)||(started&&mc.level==null&&mc.getConnection()==null&&mc.getSingleplayerServer()==null);
        report.addProperty("status",report.getAsJsonArray("failures").isEmpty()?"passed":"failed");
        report.addProperty("safe_client_shutdown_requested",safe);report.addProperty("only_audit_owned_world_used",safe);write();
        if(safe)mc.stop();
    }
    private static Path output(){return Path.of(System.getProperty("modern_ite.mobRenderAuditOutput","build/verification/mob-render-audit.json")).toAbsolutePath().normalize();}
    private static Path screenshots(){return output().getParent().resolve("mob-render-audit");}
    private static void write() {
        try {Path path=output();Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n",StandardCharsets.UTF_8);
            LogUtils.getLogger().info("MODERN_ITE_MOB_RENDER_AUDIT {} report={}",report.get("status").getAsString(),path);
        }catch(Exception failure){LogUtils.getLogger().error("Cannot write native mob-render audit",failure);}
    }
    private static final class AuditScreen extends Screen {
        int frames;
        AuditScreen(){super(Component.literal("Modern ITE isolated mob render audit"));}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            graphics.fill(0,0,width,height,0xFF101820);graphics.drawCenteredString(font,title,width/2,height/2,0xFFFFFFFF);graphics.flush();frames++;
        }
        @Override public boolean shouldCloseOnEsc(){return false;}
        @Override public boolean isPauseScreen(){return false;}
    }
}
