package io.github.ningmeng123.modernite.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.MobAbilities;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Opt-in actual local-client movement audit in a new disposable world. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT)
public final class LaunchHeightClientAudit {
    private static boolean baked,started,finished;
    private static int elapsed,firstServerTick=-1,readyTicks,caseIndex,phase,phaseTicks;
    private static String worldName;
    private static JsonObject report,current;
    private static CompletableFuture<JsonObject> pending;
    private static double startY,apexY,maxClientVelocity;
    private static final ExtremeKind[] KINDS={ExtremeKind.ZOMBIE_FLY_AWAY,ExtremeKind.SKELETON_KNOCKBACK};
    private static final ResourceLocation TEST_HEALTH=ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"launch_audit_health");
    private LaunchHeightClientAudit(){}

    @EventBusSubscriber(modid=ModernIte.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener(){}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event){if(enabled())baked=true;}
    }
    private static boolean enabled(){return Boolean.getBoolean("modern_ite.launchHeightAudit");}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||finished||!baked)return;
        Minecraft mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try{
            if(!started){
                report=initial();
                if(mc.level!=null||mc.getConnection()!=null||mc.getSingleplayerServer()!=null){
                    report.getAsJsonArray("failures").add("Refused existing world or connection");
                    finished=true;report.addProperty("status","aborted_existing_world");
                    report.addProperty("safe_client_shutdown_requested",false);write();return;
                }
                if(mc.screen instanceof AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
                if(!(mc.screen instanceof TitleScreen))return;
                started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
                worldName="modern-ite-launch-audit-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8);
                report.addProperty("disposable_world",worldName);report.addProperty("game_directory",mc.gameDirectory.getAbsolutePath());
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings(worldName,GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(183994862L,false,false),
                        registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                return;
            }
            check(++elapsed<2400,"Timed out loading or measuring the dedicated client");
            if(mc.level==null||mc.player==null||mc.getConnection()==null||mc.getSingleplayerServer()==null)return;
            check(owned(mc),"Only the newly created launch audit world may be used");
            if(firstServerTick<0){firstServerTick=mc.getSingleplayerServer().getTickCount();mc.setScreen(new WaitingScreen());}
            readyTicks++;
            if(readyTicks<100||mc.getSingleplayerServer().getTickCount()-firstServerTick<100)return;
            step(mc);
        }catch(Throwable failure){
            if(report==null)report=initial();report.getAsJsonArray("failures").add(failure.toString());
            LogUtils.getLogger().error("MODERN_ITE_LAUNCH_CLIENT_AUDIT failed",failure);finish(mc);
        }
    }

    private static void step(Minecraft mc){
        if(phase==0){
            if(caseIndex==4){finish(mc);return;}
            ExtremeKind kind=KINDS[caseIndex%2];boolean snowWall=caseIndex>=2;
            current=new JsonObject();current.addProperty("kind",kind.id());current.addProperty("terrain",snowWall?"snow_layers_beside_wall":"flat_stone");
            report.getAsJsonArray("cases").add(current);
            pending=onServer(mc,player->{prepare(player,snowWall);return new JsonObject();});
            phase=1;phaseTicks=0;return;
        }
        if(phase==1){
            if(!pending.isDone())return;pending.join();
            check(++phaseTicks<300,"Client did not settle on the prepared test platform");
            double expected=caseIndex>=2?128.25:128;
            if(phaseTicks<40||!mc.player.onGround()||Math.abs(mc.player.getY()-expected)>.001)return;
            startY=mc.player.getY();apexY=startY;maxClientVelocity=0;
            current.addProperty("client_start_y",startY);current.addProperty("client_on_ground_before_hit",mc.player.onGround());
            ExtremeKind kind=KINDS[caseIndex%2];
            pending=onServer(mc,player->attack(player,kind));
            phase=2;phaseTicks=0;return;
        }
        // Observe the real LocalPlayer after its normal movement tick. No local velocity is injected.
        apexY=Math.max(apexY,mc.player.getY());maxClientVelocity=Math.max(maxClientVelocity,mc.player.getDeltaMovement().y);phaseTicks++;
        if(!pending.isDone())return;
        JsonObject hit=pending.join();
        if(!current.has("server_hit"))current.add("server_hit",hit);
        check(hit.get("accepted").getAsBoolean()&&hit.get("health_after").getAsDouble()<hit.get("health_before").getAsDouble(),"The real server melee hit must damage the player");
        check(hit.get("health_after").getAsDouble()>0,"The launch measurement requires a surviving target; fixture health must withstand the default weapon");
        if(phaseTicks<45)return;
        double rise=apexY-startY;
        current.addProperty("client_apex_y",apexY);current.addProperty("client_rise",rise);
        current.addProperty("largest_observed_client_vy",maxClientVelocity);current.addProperty("observed_client_ticks",phaseTicks);
        check(Math.abs(rise-15)<.1,"Actual client must rise about fifteen blocks: "+current);
        check(maxClientVelocity>1,"The actual client receives an upward launch");
        current.addProperty("status","passed");write();caseIndex++;phase=0;
    }

    private static void prepare(ServerPlayer player,boolean snowWall){
        var level=player.serverLevel();var server=level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_FALL_DAMAGE).set(false,server);level.setDayTime(18000);
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
            level.setBlockAndUpdate(new BlockPos(x,127,z),Blocks.STONE.defaultBlockState());
            for(int y=128;y<=148;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            if(snowWall)level.setBlockAndUpdate(new BlockPos(x,128,z),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3));
        }
        if(snowWall)for(int z=-8;z<=8;z++)for(int y=128;y<=132;y++)level.setBlockAndUpdate(new BlockPos(1,y,z),Blocks.DIRT.defaultBlockState());
        player.setGameMode(GameType.SURVIVAL);player.setInvulnerable(false);player.setNoGravity(false);player.removeAllEffects();
        player.getInventory().clearContent();player.setAbsorptionAmount(0);prepareTestHealth(player);
        player.getFoodData().setFoodLevel(20);player.getFoodData().setSaturation(20);player.invulnerableTime=0;player.fallDistance=0;
        player.getAttribute(Attributes.GRAVITY).setBaseValue(.08);player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        player.getAbilities().flying=false;player.getAbilities().mayfly=false;player.onUpdateAbilities();
        player.connection.teleport(.5,snowWall?128.25:128,.5,0,0);player.setDeltaMovement(Vec3.ZERO);player.hurtMarked=true;
    }

    private static JsonObject attack(ServerPlayer player,ExtremeKind kind){
        // The base mod recalculates a new player's base maximum health to six. Keep
        // its normal progression and the attacker's default sword while preventing a lethal fixture.
        prepareTestHealth(player);
        var level=player.serverLevel();Mob mob=ExtremeMobs.type(kind).create(level);check(mob!=null,"Create the real variant");
        MobAbilities.initialize(mob,(ExtremeMob)mob);mob.setNoAi(true);mob.moveTo(player.getX()-1,player.getY(),player.getZ(),-90,0);
        level.addFreshEntity(mob);JsonObject facts=new JsonObject();
        facts.addProperty("weapon",mob.getMainHandItem().getItem().toString());facts.addProperty("health_before",player.getHealth());
        facts.addProperty("maximum_health_with_test_modifier",player.getMaxHealth());
        facts.addProperty("server_start_y",player.getY());facts.addProperty("server_on_ground_before_hit",player.onGround());
        facts.addProperty("accepted",mob.doHurtTarget(player));facts.addProperty("health_after",player.getHealth());
        facts.addProperty("final_server_vy",player.getDeltaMovement().y);mob.discard();return facts;
    }

    private static void prepareTestHealth(ServerPlayer player){
        var health=player.getAttribute(Attributes.MAX_HEALTH);check(health!=null,"Player has the maximum health attribute");
        health.removeModifier(TEST_HEALTH);
        health.addTransientModifier(new AttributeModifier(TEST_HEALTH,100,AttributeModifier.Operation.ADD_VALUE));
        player.setHealth(player.getMaxHealth());
    }

    private static CompletableFuture<JsonObject> onServer(Minecraft mc,java.util.function.Function<ServerPlayer,JsonObject> action){
        var server=mc.getSingleplayerServer();UUID id=mc.player.getUUID();var future=new CompletableFuture<JsonObject>();
        server.execute(()->{try{ServerPlayer player=server.getPlayerList().getPlayer(id);check(player!=null,"The real server player is present");future.complete(action.apply(player));}catch(Throwable failure){future.completeExceptionally(failure);}});return future;
    }
    private static JsonObject initial(){JsonObject value=new JsonObject();value.addProperty("author","NingMeng123");value.addProperty("timestamp",Instant.now().toString());value.addProperty("status","running");value.addProperty("scope","Real integrated-server melee, native packets, and LocalPlayer movement on flat stone and snow beside a wall");value.add("cases",new JsonArray());value.add("failures",new JsonArray());return value;}
    private static boolean owned(Minecraft mc){return started&&worldName!=null&&mc.getSingleplayerServer()!=null&&worldName.equals(mc.getSingleplayerServer().getWorldData().getLevelName());}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void finish(Minecraft mc){if(finished)return;finished=true;boolean safe=owned(mc)||(started&&mc.level==null&&mc.getConnection()==null&&mc.getSingleplayerServer()==null);report.addProperty("status",report.getAsJsonArray("failures").isEmpty()?"passed":"failed");report.addProperty("safe_client_shutdown_requested",safe);write();if(safe)mc.stop();}
    private static void write(){try{Path path=Path.of(System.getProperty("modern_ite.launchHeightAuditOutput","build/verification/launch-height-client.json")).toAbsolutePath().normalize();Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n",StandardCharsets.UTF_8);LogUtils.getLogger().info("MODERN_ITE_LAUNCH_CLIENT_AUDIT {} report={}",report.get("status"),path);}catch(Exception failure){LogUtils.getLogger().error("Cannot write launch client audit",failure);}}
    private static final class WaitingScreen extends Screen {
        WaitingScreen(){super(Component.literal("Modern ITE isolated launch-height audit"));}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){graphics.fill(0,0,width,height,0xFF101820);graphics.drawCenteredString(font,title,width/2,height/2,0xFFFFFFFF);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean shouldCloseOnEsc(){return false;}
    }
}
