package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.MobAbilities;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Native integrated-server detonation and ordinary world-frame particle captures. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT)
public final class InfernalExplosionClientAudit {
    private enum Phase { TITLE, LOADING, PREPARING, SETTLING, BASELINE, FIRING, OBSERVING, NEAR_PREPARING, NEAR_WARMING, NEAR_CAPTURE, NEAR_RETURNING, CHECKING_DROPS, DONE }
    private static final int[] SAMPLE_TICKS={5,20,45,80,120,220};
    private static final double BLAST_X=number("X",300.5),BLAST_Y=number("Y",-60),BLAST_Z=number("Z",.5);
    private static final double CAMERA_X=number("CameraX",BLAST_X),CAMERA_Y=number("CameraY",BLAST_Y+25),CAMERA_Z=number("CameraZ",BLAST_Z+96);
    private static final float CAMERA_YAW=(float)number("CameraYaw",180),CAMERA_PITCH=(float)number("CameraPitch",-3);
    private static final JsonObject REPORT=new JsonObject();
    private static JsonObject current;
    private static Phase phase=Phase.TITLE;
    private static String worldName;
    private static int elapsed,settlingTicks,cloudTicks=-1,sampleIndex;
    private static CompletableFuture<JsonObject> pending;
    private static NativeImage baseline;
    private static boolean baselineCaptured;
    private static boolean engineCleared;
    private static int nearView=-1,nearTicks;
    private static long lastFrameNanos;
    private static final List<Double> BASELINE_FRAME_MS=new ArrayList<>();
    private static final List<Double> RISING_FRAME_MS=new ArrayList<>();
    private static final List<Double> MATURE_FRAME_MS=new ArrayList<>();
    private InfernalExplosionClientAudit(){}
    private static boolean enabled(){return Boolean.getBoolean("modern_ite.infernalExplosionAudit");}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||phase==Phase.DONE)return;
        Minecraft mc=Minecraft.getInstance();
        try{
            if(phase==Phase.TITLE){
                check(mc.level==null&&mc.getConnection()==null&&mc.getSingleplayerServer()==null,"Refused an existing world or connection");
                if(mc.getOverlay()!=null)return;
                if(mc.screen instanceof AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
                if(!(mc.screen instanceof TitleScreen))return;
                Path directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.toString().replace('\\','/').contains("/build/verification/"),"Audit requires an isolated game directory under build/verification");
                REPORT.addProperty("author","NingMeng123");REPORT.addProperty("timestamp",Instant.now().toString());
                REPORT.addProperty("status","running");REPORT.addProperty("game_directory",directory.toString());
                REPORT.addProperty("scope","Real black infernal Creeper detonation, client payload reception and screen-space cloud ascent/growth through native world frames");
                REPORT.addProperty("manual_pixel_review_required",true);REPORT.add("failures",new JsonArray());
                REPORT.add("cases",new JsonArray());REPORT.addProperty("proof_directory",output().getParent().toString());
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.renderDistance().set(8);mc.options.simulationDistance().set(5);mc.options.fov().set(70);
                mc.options.particles().set(ParticleStatus.ALL);mc.options.cloudStatus().set(CloudStatus.OFF);
                worldName="infernal-explosion-audit-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8);
                REPORT.addProperty("disposable_world",worldName);phase=Phase.LOADING;
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings(worldName,GameType.SPECTATOR,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261025L,false,false),
                        access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                return;
            }
            check(++elapsed<3600,"Timed out creating or observing the isolated explosion world");
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
            check(owned(mc),"Only the newly created disposable audit world may be changed");
            mc.options.hideGui=true;mc.getToasts().clear();
            float yaw=nearView>=0?135:CAMERA_YAW,pitch=nearView==0?60:nearView==1?-75:CAMERA_PITCH;
            mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
            if(phase==Phase.OBSERVING||phase==Phase.NEAR_PREPARING||phase==Phase.NEAR_WARMING||phase==Phase.NEAR_CAPTURE||phase==Phase.NEAR_RETURNING)cloudTicks++;
            if(phase==Phase.LOADING){
                if(mc.screen!=null)return;
                beginCase();
                pending=onServer(mc,InfernalExplosionClientAudit::prepare);phase=Phase.PREPARING;
            }else if(phase==Phase.PREPARING&&pending.isDone()){
                current.add("server_fixture",pending.join());phase=Phase.SETTLING;
            }else if(phase==Phase.SETTLING){
                if(++settlingTicks>=100&&mc.screen==null){check(InfernalExplosionParticles.activeClouds()==0,"Baseline has no explosion clouds");phase=Phase.BASELINE;}
            }else if(phase==Phase.BASELINE&&baselineCaptured){
                pending=onServer(mc,InfernalExplosionClientAudit::detonate);phase=Phase.FIRING;
            }else if(phase==Phase.FIRING){
                if(pending.isDone()){current.add("server_detonation",pending.join());}
                if(InfernalExplosionParticles.activeClouds()>0){
                    current.addProperty("client_received_cloud",true);
                    cloudTicks=0;phase=Phase.OBSERVING;
                }
            }else if(phase==Phase.OBSERVING){
                if(pending.isDone()&&!current.has("server_detonation"))current.add("server_detonation",pending.join());
                if(cloudTicks>400){
                    check(pending.isDone(),"The actual server explosion completed");
                    check(sampleIndex==SAMPLE_TICKS.length,"All requested explosion phases were captured");
                    check(InfernalExplosionParticles.activeClouds()==0,"Client cloud emitter expires after its animation");
                    current.addProperty("cloud_lifecycle_expired",true);current.addProperty("cloud_lifecycle_checked_at_tick",cloudTicks);
                    pending=onServer(mc,InfernalExplosionClientAudit::dropCounts);phase=Phase.CHECKING_DROPS;
                }
            }else if(phase==Phase.CHECKING_DROPS&&pending.isDone()){
                JsonObject drops=pending.join();current.add("server_drops_after_animation",drops);
                assertNoDrops(drops,"The fully processed real explosion must leave no item entities or XP orbs");
                reportFrameIntervals();current.addProperty("status",current.getAsJsonArray("failures").isEmpty()?"passed":"failed");write();
                finish(mc);
            }else if(phase==Phase.NEAR_PREPARING&&pending.isDone()){
                pending.join();nearTicks=0;phase=Phase.NEAR_WARMING;
            }else if(phase==Phase.NEAR_WARMING&&++nearTicks>=8){
                phase=Phase.NEAR_CAPTURE;
            }else if(phase==Phase.NEAR_RETURNING&&pending.isDone()){
                pending.join();phase=Phase.OBSERVING;
            }
        }catch(Throwable failure){fail(mc,failure);}
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!enabled()||phase==Phase.DONE)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.screen!=null||mc.getOverlay()!=null)return;
        try{
            recordFrameInterval();
            if(phase==Phase.BASELINE&&!baselineCaptured){
                baseline=Screenshot.takeScreenshot(mc.getMainRenderTarget());
                Files.createDirectories(sampleFolder());baseline.writeToFile(sampleFolder().resolve("baseline.png"));
                baselineCaptured=true;
                lastFrameNanos=0;
            }else if(phase==Phase.OBSERVING&&sampleIndex<SAMPLE_TICKS.length&&cloudTicks>=SAMPLE_TICKS[sampleIndex]){
                try(NativeImage pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                    Path screenshot=sampleFolder().resolve("mushroom-cloud-"+SAMPLE_TICKS[sampleIndex]+"ticks.png");pixels.writeToFile(screenshot);
                    JsonObject facts=measure(pixels);facts.addProperty("client_ticks_since_cloud_received",cloudTicks);
                    facts.addProperty("screenshot",screenshot.toString());facts.addProperty("active_clouds",InfernalExplosionParticles.activeClouds());
                    facts.add("particle_batches",particleCounts());
                    current.getAsJsonArray("samples").add(facts);
                    if(SAMPLE_TICKS[sampleIndex]==120){
                        try{
                            check(facts.get("warm_pixels").getAsInt()>200,"Fully formed cloud contains visible orange/yellow fire");
                            check(facts.get("warm_width").getAsInt()>60&&facts.get("warm_height").getAsInt()>60,"Fire cloud spans a broad, tall screen area");
                            verifyGrowth();
                        }catch(IllegalStateException failure){recordCaseFailure(failure);}
                    }
                    if(SAMPLE_TICKS[sampleIndex]==80&&!engineCleared){clearParticleEngine(mc);engineCleared=true;}
                    if(SAMPLE_TICKS[sampleIndex]==120)check(InfernalExplosionParticles.activeClouds()>0,"The cloud remains active after mid-animation native particle clearing");
                    sampleIndex++;write();
                    if(sampleIndex==SAMPLE_TICKS.length){
                        nearView=0;pending=onServer(mc,player->nearCamera(player,0));phase=Phase.NEAR_PREPARING;
                    }
                    // GPU readback, PNG encoding and report I/O are audit work, not game frame cost.
                    lastFrameNanos=0;
                }
            }else if(phase==Phase.NEAR_CAPTURE){
                captureNearView(mc);
            }
        }catch(Throwable failure){fail(mc,failure);}
    }

    private static void beginCase(){
        current=new JsonObject();current.addProperty("name","far-origin-with-mid-animation-particle-clear");
        current.addProperty("status","running");current.add("samples",new JsonArray());current.add("near_views",new JsonArray());current.add("failures",new JsonArray());
        REPORT.getAsJsonArray("cases").add(current);
    }
    private static Path sampleFolder(){return output().getParent().resolve("far-origin");}
    private static double number(String key,double fallback){
        double value=Double.parseDouble(System.getProperty("modern_ite.infernalExplosionAudit"+key,Double.toString(fallback)));
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite infernal explosion audit coordinate: "+key);
        return value;
    }
    private static boolean engineContains(Minecraft mc,Particle particle) throws ReflectiveOperationException{
        Field active=ParticleEngine.class.getDeclaredField("particles");active.setAccessible(true);
        for(Object queue:((Map<?,?>)active.get(mc.particleEngine)).values())if(queue instanceof Collection<?> values&&values.contains(particle))return true;
        Field pending=ParticleEngine.class.getDeclaredField("particlesToAdd");pending.setAccessible(true);
        return ((Collection<?>)pending.get(mc.particleEngine)).contains(particle);
    }
    private static void clearParticleEngine(Minecraft mc) throws ReflectiveOperationException{
        JsonObject facts=new JsonObject();facts.addProperty("invoked_after_cloud_tick",cloudTicks);
        facts.addProperty("operation","Native ParticleEngine.clearParticles(), the same queue-clearing operation used during resource reload");
        facts.add("before",particleCounts());
        Method clear=ParticleEngine.class.getDeclaredMethod("clearParticles");clear.setAccessible(true);clear.invoke(mc.particleEngine);
        facts.add("after",particleCounts());current.add("mid_animation_particle_engine_clear",facts);
        check(InfernalExplosionParticles.activeClouds()>0,"The native particle queue clear occurs during active cloud playback");
    }
    private static JsonObject nearCamera(ServerPlayer player,int direction){
        if(direction==2)player.connection.teleport(CAMERA_X,CAMERA_Y,CAMERA_Z,CAMERA_YAW,CAMERA_PITCH);
        else player.connection.teleport(BLAST_X+12,BLAST_Y-2,BLAST_Z+12,135,direction==0?60:-75);
        JsonObject facts=new JsonObject();facts.addProperty("camera_x",player.getX());facts.addProperty("camera_y",player.getY());facts.addProperty("camera_z",player.getZ());
        facts.addProperty("yaw",player.getYRot());facts.addProperty("pitch",player.getXRot());return facts;
    }
    private static void captureNearView(Minecraft mc) throws Exception{
        String name=nearView==0?"inside-crater-looking-down":"inside-crater-looking-up";
        try(NativeImage pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            Path screenshot=sampleFolder().resolve(name+".png");pixels.writeToFile(screenshot);
            JsonObject facts=measure(pixels);facts.addProperty("view",name);facts.addProperty("screenshot",screenshot.toString());
            facts.addProperty("cloud_tick",cloudTicks);facts.addProperty("camera_x",mc.player.getX());facts.addProperty("camera_y",mc.player.getY());facts.addProperty("camera_z",mc.player.getZ());
            facts.addProperty("yaw",mc.player.getYRot());facts.addProperty("pitch",mc.player.getXRot());facts.add("particle_batches",particleCounts());
            current.getAsJsonArray("near_views").add(facts);
        }
        if(nearView==0){nearView=1;pending=onServer(mc,player->nearCamera(player,1));phase=Phase.NEAR_PREPARING;}
        else{nearView=-1;pending=onServer(mc,player->nearCamera(player,2));phase=Phase.NEAR_RETURNING;}
        write();lastFrameNanos=0;
    }
    private static void recordCaseFailure(Throwable failure){
        String message=failure.toString();current.getAsJsonArray("failures").add(message);REPORT.getAsJsonArray("failures").add(message);
        LogUtils.getLogger().error("MODERN_ITE_INFERNAL_VISIBILITY_SAMPLE failed",failure);
    }
    private static JsonObject prepare(ServerPlayer player){
        var level=player.serverLevel();var server=level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,server);
        // A newly created world starts inside the hostile-spawn grace period.
        // Use an ordinary date so blood-moon sky tint and storms do not hide the cloud.
        level.setDayTime((257L-1)*24_000L+6000);
        BloodMoonInvasionData.get(level).restoreProgress(257);
        ExtremeWorldState.observe(level);
        level.setWeatherParameters(6000,0,false,false);
        check(!ExtremeWorldState.graceActive(level),"The disposable world's normal hostile-spawn grace period has ended");
        player.setGameMode(GameType.SPECTATOR);player.setNoGravity(true);player.setInvulnerable(true);
        player.connection.teleport(CAMERA_X,CAMERA_Y,CAMERA_Z,CAMERA_YAW,CAMERA_PITCH);
        JsonObject facts=new JsonObject();facts.addProperty("camera_x",CAMERA_X);facts.addProperty("camera_y",CAMERA_Y);
        facts.addProperty("camera_z",CAMERA_Z);facts.addProperty("camera_yaw",CAMERA_YAW);facts.addProperty("camera_pitch",CAMERA_PITCH);
        facts.addProperty("explosion_center_x",BLAST_X);facts.addProperty("explosion_ground_y",BLAST_Y);
        facts.addProperty("explosion_center_z",BLAST_Z);facts.addProperty("particle_setting","ALL");
        facts.addProperty("historical_day",BloodMoonInvasionData.get(level).highestDay());
        facts.addProperty("do_tile_drops",level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS));
        facts.addProperty("spawn_grace_ended",!ExtremeWorldState.graceActive(level));return facts;
    }
    private static JsonObject detonate(ServerPlayer player){
        var level=player.serverLevel();Creeper creeper=(Creeper)ExtremeMobs.type(ExtremeKind.BLACK_INFERNAL_CREEPER).create(level);
        check(creeper!=null,"Create the real black infernal Creeper");
        MobAbilities.initialize(creeper,(ExtremeMob)creeper);creeper.setNoAi(true);creeper.moveTo(BLAST_X,BLAST_Y,BLAST_Z,0,0);
        check(level.addFreshEntity(creeper),"The real creeper enters the integrated server level");
        AtomicReference<Float> radius=new AtomicReference<>();
        Consumer<ExplosionEvent.Detonate> listener=event->{if(event.getExplosion().getDirectSourceEntity()==creeper)radius.set(event.getExplosion().radius());};
        NeoForge.EVENT_BUS.addListener(listener);
        try{
            Method explode=Creeper.class.getDeclaredMethod("explodeCreeper");explode.setAccessible(true);explode.invoke(creeper);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        finally{NeoForge.EVENT_BUS.unregister(listener);}
        check(radius.get()!=null,"A real uncancelled replacement explosion reached Detonate");
        JsonObject facts=new JsonObject();facts.addProperty("actual_explosion_power",radius.get());
        facts.addProperty("actual_detonation_observed",true);facts.addProperty("native_creeper_removed",creeper.isRemoved());
        JsonObject drops=dropCounts(player);facts.add("drops_immediately_after_detonation",drops);
        assertNoDrops(drops,"The real detonation must not immediately spawn items or XP");return facts;
    }
    private static JsonObject dropCounts(ServerPlayer player){
        var level=player.serverLevel();AABB region=new AABB(BLAST_X-64,BLAST_Y-64,BLAST_Z-64,BLAST_X+64,BLAST_Y+64,BLAST_Z+64);
        int items=level.getEntitiesOfClass(ItemEntity.class,region,entity->entity.isAlive()&&!entity.isRemoved()).size();
        int xp=level.getEntitiesOfClass(ExperienceOrb.class,region,entity->entity.isAlive()&&!entity.isRemoved()).size();
        JsonObject facts=new JsonObject();facts.addProperty("item_entities",items);facts.addProperty("xp_orbs",xp);
        facts.addProperty("scan_radius",64);facts.addProperty("server_game_tick",level.getGameTime());return facts;
    }
    private static void assertNoDrops(JsonObject facts,String message){
        check(facts.get("item_entities").getAsInt()==0&&facts.get("xp_orbs").getAsInt()==0,message+": "+facts);
    }
    private static JsonObject particleCounts() throws ReflectiveOperationException{
        Field cloudsField=InfernalExplosionParticles.class.getDeclaredField("CLOUDS");cloudsField.setAccessible(true);
        List<?> clouds=(List<?>)cloudsField.get(null);int tracked=0,alive=0;
        for(Object cloud:clouds){
            Field puffsField=cloud.getClass().getDeclaredField("puffs");puffsField.setAccessible(true);
            List<?> puffs=(List<?>)puffsField.get(cloud);tracked+=puffs.size();
            for(Object puff:puffs)if(puff instanceof Particle particle&&particle.isAlive())alive++;
        }
        int batches=0;Particle batch=null;
        for(Field field:InfernalExplosionParticles.class.getDeclaredFields()){
            if(!Modifier.isStatic(field.getModifiers())||!(field.getName().equals("batch")||field.getType().getSimpleName().contains("Batch")))continue;
            field.setAccessible(true);Object value=field.get(null);
            if(value!=null&&(!(value instanceof Particle particle)||particle.isAlive()))batches++;
            if(value instanceof Particle particle)batch=particle;
        }
        JsonObject facts=new JsonObject();facts.addProperty("tracked_child_particles",tracked);
        facts.addProperty("alive_child_particles",alive);facts.addProperty("active_batches",batches);
        if(batch!=null){
            var position=batch.getPos();facts.addProperty("batch_x",position.x);facts.addProperty("batch_y",position.y);facts.addProperty("batch_z",position.z);
            facts.addProperty("batch_distance_to_camera",position.distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
            facts.addProperty("render_bounding_box",batch.getRenderBoundingBox(0).toString());
            facts.addProperty("engine_contains_batch",engineContains(Minecraft.getInstance(),batch));
        }
        return facts;
    }
    private static void recordFrameInterval(){
        long now=System.nanoTime();
        if(lastFrameNanos!=0){
            double intervalMs=(now-lastFrameNanos)/1_000_000D;
            if(phase==Phase.SETTLING&&settlingTicks>=40&&settlingTicks<100)BASELINE_FRAME_MS.add(intervalMs);
            else if(phase==Phase.OBSERVING&&cloudTicks<120)RISING_FRAME_MS.add(intervalMs);
            else if(phase==Phase.OBSERVING&&cloudTicks>=120&&cloudTicks<=220)MATURE_FRAME_MS.add(intervalMs);
        }
        lastFrameNanos=now;
    }
    private static JsonObject frameSummary(List<Double> frames){
        JsonObject facts=new JsonObject();facts.addProperty("sample_count",frames.size());
        if(frames.isEmpty()){
            facts.add("mean_ms",JsonNull.INSTANCE);facts.add("p95_ms",JsonNull.INSTANCE);facts.add("max_ms",JsonNull.INSTANCE);return facts;
        }
        var sorted=new ArrayList<>(frames);Collections.sort(sorted);
        facts.addProperty("mean_ms",frames.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
        facts.addProperty("p95_ms",sorted.get(Math.max(0,(int)Math.ceil(sorted.size()*.95)-1)));
        facts.addProperty("max_ms",sorted.getLast());return facts;
    }
    private static void reportFrameIntervals(){
        if(current==null)return;JsonObject facts=new JsonObject();current.add("frame_intervals",facts);
        facts.addProperty("measurement","RenderFrameEvent.Post wall-clock intervals; frame caps and hardware affect these observations");
        facts.addProperty("screenshot_and_report_io_intervals_excluded",true);
        facts.add("settled_baseline_last_60_ticks",frameSummary(BASELINE_FRAME_MS));
        facts.add("cloud_rising_before_tick_120",frameSummary(RISING_FRAME_MS));
        facts.add("cloud_mature_ticks_120_through_220",frameSummary(MATURE_FRAME_MS));
    }
    private static JsonObject measure(NativeImage pixels){
        int changed=0,warm=0,minX=pixels.getWidth(),maxX=-1,minY=pixels.getHeight(),maxY=-1;
        for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){
            int rgba=pixels.getPixelRGBA(x,y),r=rgba&255,g=(rgba>>>8)&255,b=(rgba>>>16)&255;
            if(baseline!=null&&baseline.getWidth()==pixels.getWidth()&&baseline.getHeight()==pixels.getHeight()&&rgba!=baseline.getPixelRGBA(x,y))changed++;
            if(r>150&&g>75&&g<r*1.05&&b<125&&r>b*1.35){warm++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        }
        JsonObject facts=new JsonObject();facts.addProperty("frame_width",pixels.getWidth());facts.addProperty("frame_height",pixels.getHeight());
        facts.addProperty("changed_pixels",changed);facts.addProperty("warm_pixels",warm);
        facts.addProperty("warm_min_x",maxX<0?-1:minX);facts.addProperty("warm_max_x",maxX);
        facts.addProperty("warm_min_y",maxY<0?-1:minY);facts.addProperty("warm_max_y",maxY);
        facts.addProperty("warm_width",maxX<0?0:maxX-minX+1);facts.addProperty("warm_height",maxY<0?0:maxY-minY+1);return facts;
    }
    private static void verifyGrowth(){
        JsonArray samples=current.getAsJsonArray("samples");
        JsonObject earliest=samples.get(0).getAsJsonObject(),early=samples.get(1).getAsJsonObject();
        JsonObject first=earliest.get("warm_pixels").getAsInt()>0?earliest:early;
        JsonObject middle=samples.get(3).getAsJsonObject(),mature=samples.get(4).getAsJsonObject();
        check(early.get("warm_pixels").getAsInt()>0
                &&middle.get("warm_pixels").getAsInt()>0,"Each ascent sample contains actual visible fire");
        int earlyToMiddleRise=early.get("warm_min_y").getAsInt()-middle.get("warm_min_y").getAsInt();
        int middleToMatureRise=middle.get("warm_min_y").getAsInt()-mature.get("warm_min_y").getAsInt();
        int earlyToMiddleWidth=middle.get("warm_width").getAsInt()-early.get("warm_width").getAsInt();
        int middleToMatureWidth=mature.get("warm_width").getAsInt()-middle.get("warm_width").getAsInt();
        JsonObject facts=new JsonObject();current.add("screen_space_growth",facts);
        facts.addProperty("first_measurable_fire_tick",first.get("client_ticks_since_cloud_received").getAsInt());
        facts.addProperty("rise_pixels_20_to_80",earlyToMiddleRise);facts.addProperty("rise_pixels_80_to_120",middleToMatureRise);
        facts.addProperty("width_gain_pixels_20_to_80",earlyToMiddleWidth);facts.addProperty("width_gain_pixels_80_to_120",middleToMatureWidth);
        facts.addProperty("initial_width_fraction_of_mature",first.get("warm_width").getAsDouble()/mature.get("warm_width").getAsDouble());
        facts.addProperty("initial_height_fraction_of_mature",first.get("warm_height").getAsDouble()/mature.get("warm_height").getAsDouble());
        double frameWidth=mature.get("frame_width").getAsDouble(),frameHeight=mature.get("frame_height").getAsDouble();
        // These compare rendered pixels only. They do not duplicate the animation's timing or shape equations.
        check(earlyToMiddleRise>frameHeight*.02&&middleToMatureRise>frameHeight*.01,
                "The cloud visibly continues rising across the 20, 80 and 120 tick frames: "+facts);
        check(earlyToMiddleWidth>frameWidth*.02&&middleToMatureWidth>frameWidth*.01,
                "The cloud visibly continues spreading across the 20, 80 and 120 tick frames: "+facts);
        check(first.get("warm_width").getAsInt()<mature.get("warm_width").getAsInt()*.65
                        &&first.get("warm_height").getAsInt()<mature.get("warm_height").getAsInt()*.65,
                "The initial cloud must occupy substantially less width and height than the mature mushroom cloud: "+facts);
        check(first.get("warm_min_y").getAsInt()-mature.get("warm_min_y").getAsInt()>frameHeight*.08,
                "The first fire remains visibly lower than the mature mushroom-cloud top");
        facts.addProperty("status","passed");
    }
    private static CompletableFuture<JsonObject> onServer(Minecraft mc,java.util.function.Function<ServerPlayer,JsonObject> action){
        UUID id=mc.player.getUUID();var future=new CompletableFuture<JsonObject>();var server=mc.getSingleplayerServer();
        server.execute(()->{try{ServerPlayer player=server.getPlayerList().getPlayer(id);check(player!=null,"The server player exists");future.complete(action.apply(player));}catch(Throwable failure){future.completeExceptionally(failure);}});return future;
    }
    private static boolean owned(Minecraft mc){return worldName!=null&&mc.getSingleplayerServer()!=null&&worldName.equals(mc.getSingleplayerServer().getWorldData().getLevelName());}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void fail(Minecraft mc,Throwable failure){
        if(!REPORT.has("failures"))REPORT.add("failures",new JsonArray());REPORT.getAsJsonArray("failures").add(failure.toString());
        LogUtils.getLogger().error("MODERN_ITE_INFERNAL_EXPLOSION_AUDIT failed",failure);finish(mc);
    }
    private static void finish(Minecraft mc){
        if(phase==Phase.DONE)return;phase=Phase.DONE;
        REPORT.addProperty("status",REPORT.has("failures")&&!REPORT.getAsJsonArray("failures").isEmpty()?"failed":"passed");
        boolean safe=owned(mc);REPORT.addProperty("safe_client_shutdown_requested",safe);reportFrameIntervals();write();
        if(baseline!=null){baseline.close();baseline=null;}if(safe)mc.stop();
    }
    private static Path output(){
        Path original=Path.of(System.getProperty("modern_ite.infernalExplosionAuditOutput","build/verification/infernal-explosion/client-audit.json")).toAbsolutePath().normalize();
        return Path.of(System.getProperty("modern_ite.infernalVisibilityAuditOutput",original.getParent().resolve("visibility-regression/client-audit.json").toString())).toAbsolutePath().normalize();
    }
    private static void write(){
        try{Files.createDirectories(output().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)+"\n",StandardCharsets.UTF_8);}
        catch(Exception failure){LogUtils.getLogger().error("Cannot write infernal explosion audit",failure);}
    }
}
