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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Opt-in native GPU regression in a uniquely created disposable world. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT)
public final class MobRenderRegressionAudit {
    private static final int SIZE=320;
    private static final List<ExtremeKind> FOCUS=List.of(ExtremeKind.WITCH_LORD,ExtremeKind.WANDERING_WITCH,
            ExtremeKind.REVENANT_GHAST,ExtremeKind.ANNIHILATION_SKELETON,ExtremeKind.ZOMBIE_PIGMAN_LORD);
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
            check(identities.size()==48,"Expected all 48 registered source identities");
            JsonArray samples=new JsonArray();report.add("native_gpu_samples",samples);
            for(ExtremeKind kind:FOCUS) {
                JsonObject facts=new JsonObject();samples.add(facts);facts.addProperty("entity",kind.id());
                try{sample(mc,kind,facts);}catch(Throwable failure){fail("native GPU render "+kind.id(),failure);}
            }
            report.addProperty("focused_gpu_sample_count",samples.size());
        }catch(Throwable failure){fail("render audit",failure);}
        finally{finish(mc);}
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
            ResourceLocation normal=texture(mc,mob,facts,"texture");
            try(NativeImage actual=capture(mc,mob)) {
                PixelStats stats=stats(actual);facts.add("pixels",stats.json());
                check(stats.body>200,"GPU output contains too few non-background body pixels");
                Path png=screenshots().resolve(kind.id()+".png");Files.createDirectories(png.getParent());actual.writeToFile(png);
                facts.addProperty("screenshot",png.toString());
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
                if(mob instanceof Ghast ghast) {
                    check(stats.white/(double)stats.body<.20D,"Ghast body is saturated white; possible additive full-body glow mask");
                    ghast.setCharging(true);ResourceLocation charging=texture(mc,mob,facts,"charging_texture");
                    check(!normal.equals(charging),"Native ghast charging must select its shooting texture");
                    try(NativeImage charged=capture(mc,mob)) {
                        PixelStats chargeStats=stats(charged);facts.add("charging_pixels",chargeStats.json());
                        Path chargedPng=screenshots().resolve("revenant_ghast_charging.png");charged.writeToFile(chargedPng);
                        facts.addProperty("charging_screenshot",chargedPng.toString());
                        int changed=changedAll(actual,charged);facts.addProperty("charging_changed_pixels",changed);
                        check(chargeStats.body>200&&changed>0,"Actual GPU output must change when the ghast charges");
                        check(chargeStats.white/(double)chargeStats.body<.20D,"Charging ghast body is saturated white");
                    }
                }
                facts.addProperty("native_draw_succeeded",true);
            }
        }finally{mob.discard();}
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
    private static NativeImage capture(Minecraft mc,Mob mob){return capture(mc,mob,false);}
    private static NativeImage capture(Minecraft mc,Mob mob,boolean dim) {
        TextureTarget target=new TextureTarget(SIZE,SIZE,true,Minecraft.ON_OSX);
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());VertexSorting sorting=RenderSystem.getVertexSorting();
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try {
            target.setClearColor(16F/255,24F/255,32F/255,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,SIZE,SIZE,0,-1000,1000),VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(1,1,1,1);
            var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            renderedMob=mob;postRenderSeen=false;
            var rotation=new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.PI+.35F);
            if(!dim)InventoryScreen.renderEntityInInventory(graphics,SIZE/2F,SIZE-35F,mob instanceof Ghast?32F:110F,new Vector3f(),rotation,null,mob);
            else {
                // InventoryScreen's exact native transform/lighting/dispatch sequence, with packed
                // light0 instead of its hard-coded full light. This distinguishes source-identical
                // red base/eye texels from a missing emissive layer.
                graphics.pose().pushPose();graphics.pose().translate(SIZE/2F,SIZE-35F,50);
                graphics.pose().scale(110,110,-110);graphics.pose().mulPose(rotation);Lighting.setupForEntityInInventory();
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
        j.addProperty("scope","real ClientLevel; all 48 native texture lookups; five native InventoryScreen GPU draws; alpha/body pixel regression; ghast charge rendering");
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
