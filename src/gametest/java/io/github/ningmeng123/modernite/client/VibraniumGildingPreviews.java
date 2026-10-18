package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.modernite.entity.ArmorAuraRules;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMob;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.github.ningmeng123.modernite.entity.MobAbilities;
import io.github.ningmeng123.modernite.client.entity.VibraniumCosmicShaders;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Native armor portraits and actual GPU controls for complete cosmic vibranium armor. @author NingMeng123 */
final class VibraniumGildingPreviews {
    private static final int WIDTH=768,HEIGHT=864,FEET=715;
    private static final float SCALE=250;
    private static final float WING_SCALE=190;
    private static final int BACKGROUND=0xFF201810;
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final String[] PIECES={"helmet","chestplate","leggings","boots"};
    private static final Item[] IRON={Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS};

    private VibraniumGildingPreviews() {}

    static JsonObject verifyShaderReady()throws Exception {
        requireNoGlError("before cosmic shader audit");
        require(VibraniumCosmicShaders.isReady(),"Vibranium cosmic shader was not compiled and registered");
        JsonObject result=new JsonObject();JsonArray programs=new JsonArray();
        for(Field field:VibraniumCosmicShaders.class.getDeclaredFields()) {
            if(!Modifier.isStatic(field.getModifiers())||!ShaderInstance.class.isAssignableFrom(field.getType()))continue;
            field.setAccessible(true);ShaderInstance shader=(ShaderInstance)field.get(null);
            require(shader!=null&&shader.getId()>0,"Cosmic shader instance is missing: "+field.getName());
            require(GL20.glGetProgrami(shader.getId(),GL20.GL_LINK_STATUS)==GL11.GL_TRUE,
                    "Cosmic shader program failed to link: "+field.getName()+" "+GL20.glGetProgramInfoLog(shader.getId()));
            JsonObject program=new JsonObject();program.addProperty("field",field.getName());program.addProperty("program_id",shader.getId());
            program.addProperty("gl_link_status",true);programs.add(program);
        }
        require(!programs.isEmpty(),"No actual cosmic ShaderInstance was available for the native GL audit");
        requireNoGlError("cosmic shader link audit");
        result.add("native_shader_programs",programs);result.addProperty("compiled_and_linked",true);
        result.addProperty("gl_error",0);result.addProperty("status","passed");return result;
    }

    private static void requireNoGlError(String operation) {
        int error=GL11.glGetError();
        require(error==GL11.GL_NO_ERROR,"OpenGL error during "+operation+": 0x"+Integer.toHexString(error));
    }

    private static JsonObject exposedSkinControl(NativeImage baseline,NativeImage actual) {
        JsonObject result=new JsonObject();
        for(int region=0;region<2;region++) {
            int pixels=0,changed=0,start=region==0?210:400,end=region==0?400:600;
            for(int y=start;y<end;y++)for(int x=150;x<WIDTH-150;x++) {
                int color=baseline.getPixelRGBA(x,y);
                int red=color&255,green=color>>>8&255,blue=color>>>16&255;
                // Actual baseline skin colors identify the visible face and bare forearms, rather than fixed rectangles.
                if(red>50&&red<245&&blue<150&&red-green>18&&green-blue>5&&red>green*1.12F) {
                    pixels++;if(delta(color,actual.getPixelRGBA(x,y))>24)changed++;
                }
            }
            String name=region==0?"face":"bare_forearms";
            require(pixels>300,"Exposed-skin GPU control found too few real "+name+" pixels: "+pixels);
            require(changed<pixels*.1F,"Cosmic armor covers exposed "+name+" skin: "+changed+"/"+pixels);
            JsonObject facts=new JsonObject();facts.addProperty("baseline_skin_pixels",pixels);facts.addProperty("changed_skin_pixels",changed);
            facts.addProperty("unchanged_skin_fraction",(pixels-changed)/(double)pixels);result.add(name,facts);
        }
        result.addProperty("open_face_and_bare_forearms_preserved",true);return result;
    }

    private static int starfieldChangesOnArmor(NativeImage baseline,NativeImage first,NativeImage later) {
        int changed=0;
        for(int y=100;y<770;y++)for(int x=100;x<WIDTH-100;x++) {
            int pixel=baseline.getPixelRGBA(x,y),red=pixel&255,green=pixel>>>8&255,blue=pixel>>>16&255;
            if(blue>red*1.1F&&blue>=green*.85F&&green>40&&delta(first.getPixelRGBA(x,y),later.getPixelRGBA(x,y))>24)changed++;
        }
        return changed;
    }

    private static JsonObject fullArmorCoverageControl(NativeImage baseline,NativeImage actual) {
        String[] names={"helmet","chest_and_arms","leggings_and_boots"};
        int[] edges={100,360,545,770};
        JsonObject result=new JsonObject();int total=0,totalChanged=0;
        for(int region=0;region<names.length;region++) {
            int pixels=0,changed=0;
            for(int y=edges[region];y<edges[region+1];y++)for(int x=100;x<WIDTH-100;x++) {
                int color=baseline.getPixelRGBA(x,y),red=color&255,green=color>>>8&255,blue=color>>>16&255;
                // Blue armor and its cyan highlights differ from the greenish skin undersleeves.
                if(blue>red*1.1F&&blue>green*1.05F&&green>30) {
                    pixels++;if(delta(color,actual.getPixelRGBA(x,y))>24)changed++;
                }
            }
            require(pixels>300,"Full-armor GPU control found too few native armor pixels: "+names[region]+" "+pixels);
            require(changed>=pixels*.9F,"Animated cosmic surface does not visibly cover native armor: "+names[region]+" "+changed+"/"+pixels);
            JsonObject facts=new JsonObject();facts.addProperty("native_blue_and_cyan_armor_pixels",pixels);
            facts.addProperty("replaced_pixels",changed);facts.addProperty("replaced_fraction",changed/(double)pixels);
            result.add(names[region],facts);total+=pixels;totalChanged+=changed;
        }
        result.addProperty("total_native_armor_pixels",total);result.addProperty("total_replaced_fraction",totalChanged/(double)total);
        result.addProperty("complete_cosmic_surface",true);return result;
    }

    private static JsonObject armorCoverageWithoutHeldItems(Minecraft mc,LivingEntity living,String title,String detail,
            NativeImage baseline,NativeImage actual)throws Exception {
        ItemStack main=living.getMainHandItem(),off=living.getOffhandItem();
        if(main.isEmpty()&&off.isEmpty())return fullArmorCoverageControl(baseline,actual);
        // An emperor's blue weapon is not an armor surface and may occlude its helmet/chest.
        living.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);living.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        try(NativeImage clearActual=render(mc,living,false,title,detail);
            NativeImage clearBaseline=withoutGilding(mc,living,title,detail)) {
            JsonObject result=fullArmorCoverageControl(clearBaseline,clearActual);
            result.addProperty("held_items_excluded_from_armor_sampling",true);return result;
        } finally {living.setItemSlot(EquipmentSlot.MAINHAND,main);living.setItemSlot(EquipmentSlot.OFFHAND,off);}
    }

    private static JsonObject wingAudit(Minecraft mc,LivingEntity living,Path folder,String id)throws Exception {
        SavedPose pose=new SavedPose(living);SavedWalk walk=new SavedWalk(living.walkAnimation);
        String title="振金星海 · 渐变光翼",detail="背面 · 双侧光翼与背部连接验收";
        try {
            neutral(living);
            try(NativeImage baseline=withoutGilding(mc,living,true,title,detail);
                NativeImage actual=render(mc,living,true,title,detail)) {
                JsonObject result=wingGeometryControl(baseline,actual);
                actual.writeToFile(folder.resolve("cosmic-protection-v-"+id+"-back.png"));
                baseline.writeToFile(folder.resolve("cosmic-"+id+"-back-without-layer.png"));
                result.addProperty("hero_scale",WING_SCALE);result.addProperty("status","passed");return result;
            }
        } finally {pose.restore(living);walk.restore(living.walkAnimation);}
    }

    private static JsonObject wingGeometryControl(NativeImage baseline,NativeImage actual) {
        Bounds wearer=wearerBounds(baseline);
        int left=0,right=0,clipped=0;
        for(int y=90;y<770;y++)for(int x=0;x<WIDTH;x++) {
            int before=baseline.getPixelRGBA(x,y),after=actual.getPixelRGBA(x,y);
            if(foreground(after)&&(x<12||x>=WIDTH-12))clipped++;
            if(delta(before,BACKGROUND)<18&&delta(before,after)>24) {
                if(x<wearer.minX()-3)left++;
                if(x>wearer.maxX()+3)right++;
            }
        }
        require(left>250&&right>250,"Both luminous back wings must extend visibly beyond the wearer: "+left+" / "+right);
        require(clipped==0,"The wide hero portrait clips luminous wings at the image edge: "+clipped);

        // Flood the rendered foreground from the torso. Both wing cores must connect to
        // this same silhouette; separately floating decorations do not satisfy attachment.
        int seed=-1;double nearest=Double.POSITIVE_INFINITY;
        double centerY=FEET-WING_SCALE*1.05;
        for(int y=90;y<770;y++)for(int x=WIDTH/2-30;x<=WIDTH/2+30;x++) {
            if(!foreground(baseline.getPixelRGBA(x,y))||!foreground(actual.getPixelRGBA(x,y)))continue;
            double distance=(x-WIDTH/2D)*(x-WIDTH/2D)+(y-centerY)*(y-centerY);
            if(distance<nearest){nearest=distance;seed=y*WIDTH+x;}
        }
        require(seed>=0,"Cannot locate the actual wearer torso in the wing portrait");
        boolean[] seen=new boolean[WIDTH*HEIGHT];int[] queue=new int[WIDTH*HEIGHT];int begin=0,end=0;
        queue[end++]=seed;seen[seed]=true;
        int connectedLeft=0,connectedRight=0;
        while(begin<end) {
            int pixel=queue[begin++],x=pixel%WIDTH,y=pixel/WIDTH;
            if(delta(baseline.getPixelRGBA(x,y),BACKGROUND)<18) {
                if(x<wearer.minX()-3)connectedLeft++;
                if(x>wearer.maxX()+3)connectedRight++;
            }
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                if(dx==0&&dy==0)continue;
                int nextX=x+dx,nextY=y+dy;
                if(nextX<0||nextX>=WIDTH||nextY<90||nextY>=770)continue;
                int next=nextY*WIDTH+nextX;
                if(!seen[next]&&foreground(actual.getPixelRGBA(nextX,nextY))) {seen[next]=true;queue[end++]=next;}
            }
        }
        require(connectedLeft>Math.max(100,left*.15)&&connectedRight>Math.max(100,right*.15),
                "Luminous wing cores must connect to the actual back/body silhouette: "+connectedLeft+" / "+connectedRight);
        JsonObject result=new JsonObject();result.addProperty("left_wing_pixels_outside_wearer",left);
        result.addProperty("right_wing_pixels_outside_wearer",right);
        result.addProperty("left_wing_pixels_connected_to_body",connectedLeft);result.addProperty("right_wing_pixels_connected_to_body",connectedRight);
        result.addProperty("clipped_edge_pixels",clipped);result.addProperty("bilateral_back_attached_geometry",true);
        result.addProperty("external_geometry_is_intentional",true);return result;
    }

    private static int outsideWearerChanges(NativeImage baseline,NativeImage first,NativeImage later) {
        Bounds wearer=wearerBounds(baseline);int changed=0;
        for(int y=90;y<770;y++)for(int x=12;x<WIDTH-12;x++)if((x<wearer.minX()-3||x>wearer.maxX()+3)
                &&delta(first.getPixelRGBA(x,y),later.getPixelRGBA(x,y))>18)changed++;
        return changed;
    }

    private static Bounds wearerBounds(NativeImage image) {
        int minX=WIDTH,maxX=-1,minY=HEIGHT,maxY=-1;
        for(int y=90;y<770;y++)for(int x=12;x<WIDTH-12;x++)if(foreground(image.getPixelRGBA(x,y))) {
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        require(maxX>=minX&&maxY>=minY,"The baseline portrait has no visible wearer");return new Bounds(minX,maxX,minY,maxY);
    }

    private static boolean foreground(int color){return delta(color,BACKGROUND)>24;}

    private record Bounds(int minX,int maxX,int minY,int maxY) {}

    /** Observe real rendered hue transport; a uniform flash cannot improve a spatially shifted match. */
    private static JsonObject wingColorFlowAudit(Minecraft mc,LivingEntity living,Path folder,String id)throws Exception {
        String title="振金星海 · 渐变光翼",detail="固定姿态 · 翼面色带从根部流向翼尖";
        try(NativeImage baseline=withoutGilding(mc,living,true,title,detail);
            NativeImage zero=clockedRender(mc,living,true,title,detail,0);
            NativeImage five=clockedRender(mc,living,true,title,detail,5);
            NativeImage ten=clockedRender(mc,living,true,title,detail,10);
            NativeImage repeat=clockedRender(mc,living,true,title,detail,80)) {
            Bounds wearer=wearerBounds(baseline);float[] fixed=parentPose(mc,living);
            require(living.tickCount==160&&living.walkAnimation.speed()==0,"Color-flow audit requires a frozen native pose");
            JsonObject result=new JsonObject();JsonArray sides=new JsonArray();
            for(int side:new int[]{-1,1}) {
                WingColors start=wingColors(baseline,zero,wearer,side);
                WingColors next=wingColors(baseline,five,wearer,side);
                WingColors later=wingColors(baseline,ten,wearer,side);
                WingColors loop=wingColors(baseline,repeat,wearer,side);
                FlowMatch first=flowMatch(start,next,42),second=flowMatch(start,later,54);
                FlowMatch continuous=flowMatch(next,later,42),repeated=flowMatch(start,loop,3);
                String name=side<0?"left":"right";
                require(first.shift()>=3&&continuous.shift()>=3,
                        "Wing hue must advance outward rather than flash in place: "+name+" "+first+" / "+continuous);
                require(second.shift()>first.shift()+2&&second.shift()<=first.shift()*2.8+5,
                        "Wing hue transport must progress continuously between 0/5/10 ticks: "+name+" "+first+" / "+second);
                require(first.improvement()>.12&&second.improvement()>.20&&continuous.improvement()>.12,
                        "Spatial transport must explain wing colors better than a stationary brightness change: "+name);
                require(repeated.error()<Math.max(.0035,second.stationaryError()*.45),
                        "Wing palette must return after its complete 80-tick color cycle: "+name+" "+repeated);
                JsonObject facts=new JsonObject();facts.addProperty("side",name);
                facts.addProperty("chromatic_wing_pixels_at_tick_0",start.pixels());
                facts.add("tick_0_to_5",first.json());facts.add("tick_0_to_10",second.json());
                facts.add("tick_5_to_10",continuous.json());facts.add("tick_0_to_80",repeated.json());
                double overlapFive=wingMaskOverlap(baseline,zero,five,wearer,side);
                double overlapTen=wingMaskOverlap(baseline,zero,ten,wearer,side);
                require(overlapFive>.80&&overlapTen>.80,"Color flow must preserve the wing silhouette: "+name+" "+overlapFive+" / "+overlapTen);
                facts.addProperty("silhouette_iou_at_tick_5",overlapFive);facts.addProperty("silhouette_iou_at_tick_10",overlapTen);
                sides.add(facts);
            }
            require(equalPose(fixed,parentPose(mc,living)),"Color-flow capture moved the native skeleton");
            zero.writeToFile(folder.resolve("cosmic-"+id+"-flow-tick-000.png"));
            five.writeToFile(folder.resolve("cosmic-"+id+"-flow-tick-005.png"));
            ten.writeToFile(folder.resolve("cosmic-"+id+"-flow-tick-010.png"));
            repeat.writeToFile(folder.resolve("cosmic-"+id+"-flow-tick-080.png"));
            result.add("wing_sides",sides);result.addProperty("color_period_ticks",80);
            result.addProperty("observed_root_to_tip_hue_advection",true);
            result.addProperty("brightness_normalized",true);result.addProperty("near_white_glints_excluded",true);
            result.addProperty("fixed_parent_pose",true);result.addProperty("status","passed");return result;
        }
    }

    private static WingColors wingColors(NativeImage baseline,NativeImage actual,Bounds wearer,int side) {
        int edge=side<0?wearer.minX():wearer.maxX();
        double[] red=new double[WIDTH/2],blue=new double[WIDTH/2];int[] samples=new int[WIDTH/2];int pixels=0;
        for(int distance=5;distance<red.length;distance++) {
            int x=edge+side*distance;if(x<12||x>=WIDTH-12)break;
            for(int y=90;y<770;y++) {
                if(delta(baseline.getPixelRGBA(x,y),BACKGROUND)>=18)continue;
                int color=actual.getPixelRGBA(x,y),r=color&255,g=color>>>8&255,b=color>>>16&255;
                int high=Math.max(r,Math.max(g,b)),low=Math.min(r,Math.min(g,b));
                // Bright, chromatic blade cores; exclude dark shells, background and near-white flecks.
                if(g<95||high-low<35||r>g+25||b>g+25)continue;
                red[distance]+=r/(double)g;blue[distance]+=b/(double)g;samples[distance]++;pixels++;
            }
            if(samples[distance]>0){red[distance]/=samples[distance];blue[distance]/=samples[distance];}
        }
        require(pixels>250,"Too few actual chromatic wing pixels to measure palette transport: "+pixels);
        double[] smoothRed=new double[red.length],smoothBlue=new double[blue.length];int[] smoothCount=new int[samples.length];
        int columns=0;double meanRed=0,meanBlue=0;
        for(int index=7;index<red.length-2;index++) {
            if(samples[index]<3)continue;
            for(int offset=-2;offset<=2;offset++)if(samples[index+offset]>=3) {
                smoothRed[index]+=red[index+offset];smoothBlue[index]+=blue[index+offset];smoothCount[index]++;
            }
            smoothRed[index]/=smoothCount[index];smoothBlue[index]/=smoothCount[index];
            meanRed+=smoothRed[index];meanBlue+=smoothBlue[index];columns++;
        }
        require(columns>=35,"Too few wing-profile columns to observe spatial color transport: "+columns);
        meanRed/=columns;meanBlue/=columns;double variance=0;
        for(int index=0;index<red.length;index++)if(smoothCount[index]>0)
            variance+=square(smoothRed[index]-meanRed)+square(smoothBlue[index]-meanBlue);
        require(variance/columns>.004,"Wing hue is nearly uniform; a clear spatial gradient is required");
        return new WingColors(smoothRed,smoothBlue,smoothCount,pixels,columns);
    }

    private static FlowMatch flowMatch(WingColors first,WingColors later,int maximumShift) {
        FlowMatch best=null;
        for(int shift=-maximumShift;shift<=maximumShift;shift++) {
            double error=0,stationary=0;int samples=0;
            for(int index=7;index<first.red().length-2;index++) {
                int shifted=index+shift;
                if(shifted<7||shifted>=later.red().length-2||first.counts()[index]==0||later.counts()[index]==0||later.counts()[shifted]==0)continue;
                error+=square(first.red()[index]-later.red()[shifted])+square(first.blue()[index]-later.blue()[shifted]);
                stationary+=square(first.red()[index]-later.red()[index])+square(first.blue()[index]-later.blue()[index]);samples++;
            }
            if(samples<Math.max(30,Math.min(first.columns(),later.columns())*.45))continue;
            FlowMatch match=new FlowMatch(shift,error/samples,stationary/samples,samples);
            if(best==null||match.error()<best.error())best=match;
        }
        require(best!=null,"Wing color profiles have insufficient spatial overlap");return best;
    }

    private static double wingMaskOverlap(NativeImage baseline,NativeImage first,NativeImage later,Bounds wearer,int side) {
        int intersection=0,union=0;
        for(int y=90;y<770;y++)for(int x=12;x<WIDTH-12;x++) {
            if(side<0?x>=wearer.minX()-3:x<=wearer.maxX()+3)continue;
            if(delta(baseline.getPixelRGBA(x,y),BACKGROUND)>=18)continue;
            boolean before=foreground(first.getPixelRGBA(x,y)),after=foreground(later.getPixelRGBA(x,y));
            if(before||after)union++;if(before&&after)intersection++;
        }
        require(union>250,"Wing silhouette is missing from the color-flow audit");return intersection/(double)union;
    }

    private static double square(double value){return value*value;}
    private record WingColors(double[] red,double[] blue,int[] counts,int pixels,int columns) {}
    private record FlowMatch(int shift,double error,double stationaryError,int samples) {
        double improvement(){return 1-error/Math.max(.000001,stationaryError);}
        JsonObject json(){JsonObject result=new JsonObject();result.addProperty("outward_shift_pixels",shift);
            result.addProperty("transport_match_error",error);result.addProperty("stationary_match_error",stationaryError);
            result.addProperty("error_reduction_after_transport",improvement());result.addProperty("matched_columns",samples);return result;}
    }

    static JsonArray capture(Minecraft mc,Path folder)throws Exception {
        JsonArray results=new JsonArray();
        JsonObject shader=verifyShaderReady();shader.addProperty("entity","vibranium_cosmic_shader");results.add(shader);
        var savedEquipment=equipment(mc.player);
        SavedPose savedPose=new SavedPose(mc.player);
        SavedWalk savedWalk=new SavedWalk(mc.player.walkAnimation);
        try {
            for(var slot:savedEquipment.keySet())mc.player.setItemSlot(slot,ItemStack.EMPTY);
            equip(mc,mc.player,5,true);neutral(mc.player);
            results.add(audit(mc,mc.player,folder,"player"));
            try(NativeImage front=renderHero(mc,mc.player,false,"振金星海 · 渐变光翼","玩家 · 正面 · 蓝灰星空与背部光翼");
                NativeImage rear=renderHero(mc,mc.player,true,"振金星海 · 渐变光翼","玩家 · 背面 · 背部连接的双侧渐变光翼")) {
                front.writeToFile(folder.resolve("cosmic-protection-v-player.png"));
                rear.writeToFile(folder.resolve("cosmic-protection-v-player-back.png"));
                front.writeToFile(folder.resolve("vibranium-protection-v-player.png"));
                rear.writeToFile(folder.resolve("vibranium-protection-v-player-back.png"));
                equip(mc,mc.player,0,true);
                try(NativeImage plain=renderHero(mc,mc.player,false,"普通振金套装","未附魔 · 原生蓝灰与青色振金护甲")) {
                    plain.writeToFile(folder.resolve("vibranium-unenchanted-player.png"));
                    plain.writeToFile(folder.resolve("cosmic-unenchanted-player.png"));
                    comparison(folder,plain,front);
                } finally {equip(mc,mc.player,5,true);}
            }
            for(var slot:ARMOR)mc.player.setItemSlot(slot,ItemStack.EMPTY);
            Mob emperor=ExtremeMobs.type(ExtremeKind.UNDYING_ZOMBIE_EMPEROR).create(mc.level);
            require(emperor!=null,"Cannot create emperor for the vibranium gilding GPU audit");
            MobAbilities.initialize(emperor,(ExtremeMob)emperor);
            emperor.setNoAi(true);emperor.setPos(mc.player.position().add(2,0,0));mc.level.addEntity(emperor);
            try {
                neutral(emperor);
                require(ArmorAuraRules.fullProtectionFive(emperor),"Native emperor must wear full vibranium protection V");
                results.add(audit(mc,emperor,folder,"emperor"));
                try(NativeImage image=renderHero(mc,emperor,false,"不朽尸王 · 振金星海","原生振金全套保护 V · 蓝灰星空与渐变光翼")) {
                    image.writeToFile(folder.resolve("cosmic-protection-v-emperor.png"));
                    image.writeToFile(folder.resolve("vibranium-protection-v-emperor.png"));
                }
            } finally {emperor.discard();}
        } finally {
            restoreEquipment(mc.player,savedEquipment);savedPose.restore(mc.player);savedWalk.restore(mc.player.walkAnimation);
        }
        return results;
    }

    private static JsonObject audit(Minecraft mc,LivingEntity living,Path folder,String id)throws Exception {
        String title="振金全套保护 V · 深空护甲",detail="甲面星空 · 真实着色器与装备条件验收";
        JsonObject result=new JsonObject();result.addProperty("entity","vibranium_gilding_"+id);
        result.addProperty("gpu_capture",true);result.addProperty("author","NingMeng123");
        long count=layers(mc,living).stream().filter(VibraniumGildingPreviews::gilding).count();
        require(count==1,"Expected exactly one vibranium gilding layer on "+id+", found "+count);
        result.addProperty("installed_gilding_layers",count);
        var saved=equipment(living);SavedWalk walk=new SavedWalk(living.walkAnimation);
        try {
            require(ArmorAuraRules.fullProtectionFive(living),"Full vibranium V positive control has incorrect equipment");
            neutral(living);
            try(NativeImage actual=render(mc,living,false,title,detail);
                NativeImage without=withoutGilding(mc,living,title,detail)) {
                PixelDifference visible=difference(without,actual);
                require(visible.changed()>600,"Vibranium armor has too few visible gilding pixels: "+visible);
                result.add("full_v_layer_difference",visible.json());
                if(id.equals("player"))result.add("exposed_skin_gpu_control",exposedSkinControl(without,actual));
                result.add("standing_parent_pose",verifyParentPose(mc,living));
                result.add("full_armor_coverage_gpu_control",armorCoverageWithoutHeldItems(mc,living,title,detail,without,actual));
                actual.writeToFile(folder.resolve("vibranium-protection-v-"+id+"-geometry.png"));
                without.writeToFile(folder.resolve("vibranium-protection-v-"+id+"-without-layer.png"));

                equip(mc,living,5,false);
                require(!ArmorAuraRules.fullProtectionFive(living),"Full iron V must not activate vibranium gilding");
                result.add("iron_v_gpu_control",disabledControl(mc,living,title,detail));

                equip(mc,living,5,true);living.setItemSlot(EquipmentSlot.CHEST,enchanted(mc,IRON[1],5));
                require(!ArmorAuraRules.fullProtectionFive(living),"A mixed material V set must not activate gilding");
                result.add("mixed_material_gpu_control",disabledControl(mc,living,title,detail));

                equip(mc,living,5,true);living.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);
                require(!ArmorAuraRules.fullProtectionFive(living),"Missing-chestplate control must disable gilding");
                result.add("missing_piece_gpu_control",disabledControl(mc,living,title,detail));

                equip(mc,living,5,true);living.setItemSlot(EquipmentSlot.FEET,enchanted(mc,vibranium(3),4));
                require(!ArmorAuraRules.fullProtectionFive(living),"A protection IV piece must disable gilding");
                result.add("one_piece_iv_gpu_control",disabledControl(mc,living,title,detail));

                restoreEquipment(living,saved);
                // Drive the same native walk state used by LivingEntityRenderer, rather than a detached clock animation.
                for(int frame=0;frame<3;frame++)living.walkAnimation.update(1,1);
                living.setXRot(-12);living.xRotO=-12;
                living.yHeadRot=living.yHeadRotO=28;
                try(NativeImage moving=render(mc,living,false,title,detail)) {
                    result.add("walking_parent_pose",verifyParentPose(mc,living));
                    PixelDifference movement=difference(actual,moving);
                    require(movement.changed()>1000,"Native walking and head turning did not visibly alter the wearer: "+movement);
                    try(NativeImage movingWithout=withoutGilding(mc,living,title,detail)) {
                        PixelDifference attached=difference(movingWithout,moving);
                        require(attached.changed()>600,"Walking cosmic armor and wings are not visible: "+attached);
                        result.add("walking_gilding_difference",attached.json());
                    }
                    result.add("native_walk_and_turn_difference",movement.json());
                    result.addProperty("native_walk_speed",living.walkAnimation.speed());
                    moving.writeToFile(folder.resolve("vibranium-protection-v-"+id+"-walking.png"));
                }
            }
        } finally {restoreEquipment(living,saved);neutral(living);walk.restore(living.walkAnimation);}
        result.add("back_attached_wings_gpu_control",wingAudit(mc,living,folder,id));
        result.add("fixed_pose_animation",animationAudit(mc,living,folder,id));
        result.addProperty("status","passed");return result;
    }

    private static JsonObject animationAudit(Minecraft mc,LivingEntity living,Path folder,String id)throws Exception {
        JsonObject result=new JsonObject();
        String title="振金星海 · 渐变光翼",detail="固定姿态 · 蓝灰星空与流动渐变";
        var saved=equipment(living);SavedPose pose=new SavedPose(living);SavedWalk walk=new SavedWalk(living.walkAnimation);
        try {
            neutral(living);
            // Isolate the cosmic animation from vanilla's independent, wall-clock purple item glint.
            // The real enchantments remain present, so the production material/enchantment gate is unchanged.
            for(var slot:saved.keySet()) {
                ItemStack stack=living.getItemBySlot(slot).copy();
                if(!stack.isEmpty())stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,false);
                living.setItemSlot(slot,stack);
            }
            require(ArmorAuraRules.fullProtectionFive(living),"Glint isolation must preserve full vibranium protection V");
            mc.particleEngine.setLevel(mc.level);
            float[] originalPose=null;
            NativeImage firstResidual=null;
            NativeImage firstActual=null;
            NativeImage firstBack=null;
            try {
                JsonArray samples=new JsonArray();result.add("clock_samples",samples);
                for(int animationTick:new int[]{0,20,40}) {
                    try(NativeImage baseline=withoutGilding(mc,living,title,detail);
                        NativeImage actual=clockedRender(mc,living,false,title,detail,animationTick)) {
                        float[] currentPose=parentPose(mc,living);
                        if(originalPose==null)originalPose=currentPose;
                        else require(equalPose(originalPose,currentPose),"Animated gilding must not move the wearer skeleton");
                        PixelDifference attached=difference(baseline,actual);
                        require(attached.changed()>600,"Cosmic armor has too few visible pixels: "+attached);
                        JsonObject sample=attached.json();sample.addProperty("animation_tick",animationTick);
                        sample.addProperty("fixed_entity_tick",living.tickCount);
                        sample.addProperty("walk_speed",living.walkAnimation.speed());
                        sample.addProperty("parent_pose_frozen",true);samples.add(sample);
                        try(NativeImage residual=layerResidual(baseline,actual)) {
                            if(firstResidual==null) {
                                firstResidual=new NativeImage(WIDTH,HEIGHT,false);firstResidual.copyFrom(residual);
                                firstActual=new NativeImage(WIDTH,HEIGHT,false);firstActual.copyFrom(actual);
                            }
                            else {
                                PixelDifference changed=difference(firstResidual,residual);
                                require(changed.changed()>150,"Starfield does not visibly animate at tick "+animationTick+": "+changed);
                                sample.add("isolated_layer_change_from_tick_0",changed.json());
                                int starfieldChanges=starfieldChangesOnArmor(baseline,firstActual,actual);
                                require(starfieldChanges>100,"Cosmic animation only changes decorations rather than the blue armor surface at tick "+animationTick+": "+starfieldChanges);
                                sample.addProperty("starfield_changed_pixels_on_armor",starfieldChanges);
                            }
                        }
                        actual.writeToFile(folder.resolve("cosmic-"+id+"-tick-"+String.format(java.util.Locale.ROOT,"%03d",animationTick)+".png"));
                        try(NativeImage backBaseline=withoutGilding(mc,living,true,title,detail);
                            NativeImage back=clockedRender(mc,living,true,title,detail,animationTick)) {
                            sample.add("back_wings",wingGeometryControl(backBaseline,back));
                            require(equalPose(originalPose,parentPose(mc,living)),"Back-wing animation changed the frozen skeleton");
                            if(firstBack==null) {
                                firstBack=new NativeImage(WIDTH,HEIGHT,false);firstBack.copyFrom(back);
                            } else {
                                int wingChanges=outsideWearerChanges(backBaseline,firstBack,back);
                                require(wingChanges>50,"Back wings do not visibly animate at tick "+animationTick+": "+wingChanges);
                                sample.addProperty("animated_wing_pixels_outside_wearer",wingChanges);
                            }
                            back.writeToFile(folder.resolve("cosmic-"+id+"-back-tick-"+String.format(java.util.Locale.ROOT,"%03d",animationTick)+".png"));
                        }
                    }
                }
            } finally {if(firstResidual!=null)firstResidual.close();if(firstActual!=null)firstActual.close();if(firstBack!=null)firstBack.close();}
            require(living.tickCount==160&&living.walkAnimation.speed()==0,"Animation capture changed its fixed native entity clock or walk state");
            require(Integer.parseInt(mc.particleEngine.countParticles())==0,"Vibranium gilding animation must have zero external particles");
            result.addProperty("external_particle_count",0);
            result.addProperty("native_item_glint_isolated",true);
            result.addProperty("animation_clock_only_changes_inside_native_gilding_layer",true);
            result.addProperty("fixed_parent_pose",true);
            result.addProperty("external_geometry","back_attached_light_wings");
            result.addProperty("back_wings_attached_to_body",true);
            result.add("observed_wing_color_flow",wingColorFlowAudit(mc,living,folder,id));
            if(id.equals("player")) {
                Path animation=folder.resolve("cosmic-animation");Files.createDirectories(animation);
                for(int frame=0;frame<200;frame++) {
                    int animationTick=frame*2;
                    try(NativeImage front=clockedRender(mc,living,false,title,"正面 · 固定姿态 · 蓝灰振金星空",animationTick,true);
                        NativeImage rear=clockedRender(mc,living,true,title,"背面 · 固定姿态 · 渐变色带沿翼刃流动",animationTick,true);
                        NativeImage combined=sideBySide(front,rear)) {
                        require(equalPose(originalPose,parentPose(mc,living)),"Animation output moved the fixed skeleton at frame "+frame);
                        combined.writeToFile(animation.resolve(String.format(java.util.Locale.ROOT,"frame%03d.png",frame)));
                    }
                }
                result.addProperty("frames",200);result.addProperty("ticks_per_frame",2);
                result.addProperty("capture_span_ticks",400);result.addProperty("suggested_frame_duration_ms",100);
                result.addProperty("columns","front,rear");result.addProperty("frame_width",WIDTH*2);result.addProperty("frame_height",HEIGHT);
                result.addProperty("frame_folder",animation.toString());
            }
            require(Integer.parseInt(mc.particleEngine.countParticles())==0,"Animation export emitted external particles");
        } finally {
            restoreEquipment(living,saved);pose.restore(living);walk.restore(living.walkAnimation);
            mc.particleEngine.setLevel(mc.level);
        }
        result.addProperty("status","passed");return result;
    }

    private static NativeImage layerResidual(NativeImage baseline,NativeImage actual) {
        NativeImage result=new NativeImage(WIDTH,HEIGHT,false);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
            int before=baseline.getPixelRGBA(x,y),after=actual.getPixelRGBA(x,y);
            int red=Math.max(0,Math.min(255,128+(after&255)-(before&255)));
            int green=Math.max(0,Math.min(255,128+(after>>>8&255)-(before>>>8&255)));
            int blue=Math.max(0,Math.min(255,128+(after>>>16&255)-(before>>>16&255)));
            result.setPixelRGBA(x,y,0xFF000000|blue<<16|green<<8|red);
        }
        return result;
    }

    private static float[] parentPose(Minecraft mc,LivingEntity living) {
        var renderer=(LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(living);
        HumanoidModel<?> parent=(HumanoidModel<?>)renderer.getModel();
        ModelPart[] parts={parent.head,parent.hat,parent.body,parent.rightArm,parent.leftArm,parent.rightLeg,parent.leftLeg};
        float[] values=new float[parts.length*9];int index=0;
        for(ModelPart part:parts)for(float value:new float[]{part.x,part.y,part.z,part.xRot,part.yRot,part.zRot,part.xScale,part.yScale,part.zScale})values[index++]=value;
        return values;
    }

    private static boolean equalPose(float[] reference,float[] actual) {
        for(int index=0;index<reference.length;index++)if(Math.abs(reference[index]-actual[index])>.00001F)return false;
        return true;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static NativeImage clockedRender(Minecraft mc,LivingEntity living,boolean rear,String title,String detail,int animationTick)throws Exception {
        return clockedRender(mc,living,rear,title,detail,animationTick,false);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static NativeImage clockedRender(Minecraft mc,LivingEntity living,boolean rear,String title,String detail,int animationTick,boolean hero)throws Exception {
        var renderer=(LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(living);
        List<Object> layers=layers(mc,living);
        int index=-1;for(int next=0;next<layers.size();next++)if(gilding(layers.get(next))){index=next;break;}
        require(index>=0,"Animation capture requires the native vibranium gilding layer");
        Object original=layers.get(index);
        layers.set(index,new ClockedGildingLayer((RenderLayerParent)renderer,(RenderLayer)original,animationTick));
        try{return hero?renderHero(mc,living,rear,title,detail):render(mc,living,rear,title,detail);}finally{layers.set(index,original);}
    }

    /** Test-only clock adapter: native setupAnim and all other layers keep the same entity tick. */
    private static final class ClockedGildingLayer extends RenderLayer<LivingEntity,HumanoidModel<LivingEntity>> {
        private final RenderLayer<LivingEntity,HumanoidModel<LivingEntity>> delegate;
        private final int animationTick;
        ClockedGildingLayer(RenderLayerParent<LivingEntity,HumanoidModel<LivingEntity>> parent,
                RenderLayer<LivingEntity,HumanoidModel<LivingEntity>> delegate,int animationTick) {
            super(parent);this.delegate=delegate;this.animationTick=animationTick;
        }
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,LivingEntity living,
                float limbSwing,float amount,float partialTick,float age,float headYaw,float headPitch) {
            int savedTick=living.tickCount;
            try {
                living.tickCount=animationTick;
                delegate.render(pose,buffers,light,living,limbSwing,amount,partialTick,age,headYaw,headPitch);
            } finally {living.tickCount=savedTick;}
        }
    }

    private static JsonObject disabledControl(Minecraft mc,LivingEntity living,String title,String detail)throws Exception {
        try(NativeImage actual=render(mc,living,false,title,detail);
            NativeImage without=withoutGilding(mc,living,title,detail)) {
            PixelDifference difference=difference(without,actual);
            require(difference.changed()<20,"Disabled vibranium gilding still renders geometry: "+difference);
            JsonObject result=difference.json();result.addProperty("gilding_enabled",false);return result;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> layers(Minecraft mc,LivingEntity living)throws Exception {
        var renderer=mc.getEntityRenderDispatcher().getRenderer(living);
        require(renderer instanceof LivingEntityRenderer<?,?>,"Gilding audit requires a living renderer");
        Field field=LivingEntityRenderer.class.getDeclaredField("layers");field.setAccessible(true);
        return (List<Object>)field.get(renderer);
    }

    private static boolean gilding(Object layer){return layer.getClass().getSimpleName().equals("VibraniumGildingLayer");}

    private static JsonObject verifyParentPose(Minecraft mc,LivingEntity living)throws Exception {
        var renderer=(LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(living);
        require(renderer.getModel() instanceof HumanoidModel<?>,"Gilding requires a humanoid parent model");
        HumanoidModel<?> parent=(HumanoidModel<?>)renderer.getModel();
        Object layer=layers(mc,living).stream().filter(VibraniumGildingPreviews::gilding).findFirst().orElseThrow();
        String[] names={"head","hat","body","right_arm","left_arm","right_leg","left_leg"};
        ModelPart[] parentParts={parent.head,parent.hat,parent.body,parent.rightArm,parent.leftArm,parent.rightLeg,parent.leftLeg};
        for(String fieldName:new String[]{"outer","inner"}) {
            Field field=layer.getClass().getDeclaredField(fieldName);field.setAccessible(true);
            HumanoidModel<?> attached=(HumanoidModel<?>)field.get(layer);
            ModelPart[] attachedParts={attached.head,attached.hat,attached.body,attached.rightArm,attached.leftArm,attached.rightLeg,attached.leftLeg};
            for(int index=0;index<names.length;index++) {
                ModelPart source=parentParts[index],target=attachedParts[index];
                float[] before={source.x,source.y,source.z,source.xRot,source.yRot,source.zRot,source.xScale,source.yScale,source.zScale};
                float[] after={target.x,target.y,target.z,target.xRot,target.yRot,target.zRot,target.xScale,target.yScale,target.zScale};
                for(int component=0;component<before.length;component++)require(Math.abs(before[component]-after[component])<.00001F,
                        "Cosmic model does not copy the actual parent pose: "+fieldName+" "+names[index]+" component "+component);
            }
            require(parent.young==attached.young&&parent.crouching==attached.crouching,"Cosmic model loses parent age or crouching state: "+fieldName);
        }
        JsonObject result=new JsonObject();result.addProperty("matched_parts",names.length);
        result.addProperty("matched_models",2);
        result.addProperty("head_x_rotation",parent.head.xRot);result.addProperty("head_y_rotation",parent.head.yRot);
        result.addProperty("right_arm_x_rotation",parent.rightArm.xRot);result.addProperty("left_arm_x_rotation",parent.leftArm.xRot);
        result.addProperty("right_leg_x_rotation",parent.rightLeg.xRot);result.addProperty("left_leg_x_rotation",parent.leftLeg.xRot);
        result.addProperty("crouching",parent.crouching);result.addProperty("exact_parent_pose_copied",true);return result;
    }

    private static NativeImage withoutGilding(Minecraft mc,LivingEntity living,String title,String detail)throws Exception {
        return withoutGilding(mc,living,false,title,detail);
    }

    private static NativeImage withoutGilding(Minecraft mc,LivingEntity living,boolean rear,String title,String detail)throws Exception {
        List<Object> layers=layers(mc,living),saved=new ArrayList<>(layers);
        layers.removeIf(VibraniumGildingPreviews::gilding);
        try{return render(mc,living,rear,title,detail);}finally{layers.clear();layers.addAll(saved);}
    }

    private static NativeImage render(Minecraft mc,LivingEntity living,boolean rear,String title,String detail) {
        return render(mc,living,rear,title,detail,rear?WING_SCALE:SCALE);
    }

    private static NativeImage renderHero(Minecraft mc,LivingEntity living,boolean rear,String title,String detail) {
        return render(mc,living,rear,title,detail,WING_SCALE);
    }

    private static NativeImage render(Minecraft mc,LivingEntity living,boolean rear,String title,String detail,float viewScale) {
        TextureTarget target=new TextureTarget(WIDTH,HEIGHT,true,Minecraft.ON_OSX);
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());VertexSorting sorting=RenderSystem.getVertexSorting();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        float[] color=RenderSystem.getShaderColor().clone();
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try {
            target.setClearColor(16F/255,24F/255,32F/255,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,WIDTH,HEIGHT,0,-1000,1000),VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShaderFogStart(10000);RenderSystem.setShaderFogEnd(10001);
            GuiGraphics graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            Quaternionf rotation=new Quaternionf().rotationZ((float)Math.PI).rotateX(-.12F)
                    .rotateY(rear?.36F:(float)Math.PI+.28F);
            InventoryScreen.renderEntityInInventory(graphics,WIDTH/2F,FEET,viewScale,new Vector3f(),rotation,null,living);
            graphics.flush();mc.renderBuffers().bufferSource().endBatch();
            RenderSystem.disableDepthTest();
            graphics.pose().pushPose();graphics.pose().scale(2,2,2);
            graphics.drawCenteredString(mc.font,title,WIDTH/4,20,0xFFFFEAB3);graphics.pose().popPose();
            graphics.drawCenteredString(mc.font,detail,WIDTH/2,785,0xFFE8D5A2);
            graphics.drawCenteredString(mc.font,"整甲动态星空 · 背部发光双翼 · NingMeng123",WIDTH/2,818,0xFF8D9DAC);
            graphics.flush();RenderSystem.enableDepthTest();
            requireNoGlError("cosmic armor inventory draw");
            NativeImage image=Screenshot.takeScreenshot(target);requireNoGlError("cosmic armor framebuffer read");return image;
        } finally {
            mc.renderBuffers().bufferSource().endBatch();modelView.popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
            mc.getMainRenderTarget().bindWrite(true);target.destroyBuffers();
        }
    }

    private static void comparison(Path folder,NativeImage plain,NativeImage gilded)throws Exception {
        try(NativeImage comparison=sideBySide(plain,gilded)) {
            comparison.writeToFile(folder.resolve("vibranium-protection-v-comparison.png"));
            comparison.writeToFile(folder.resolve("cosmic-protection-v-comparison.png"));
        }
    }

    private static NativeImage sideBySide(NativeImage left,NativeImage right) {
        NativeImage result=new NativeImage(WIDTH*2,HEIGHT,false);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
            result.setPixelRGBA(x,y,left.getPixelRGBA(x,y));result.setPixelRGBA(WIDTH+x,y,right.getPixelRGBA(x,y));
        }
        return result;
    }

    private static EnumMap<EquipmentSlot,ItemStack> equipment(LivingEntity living) {
        var result=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
        for(var slot:EquipmentSlot.values())if(slot!=EquipmentSlot.BODY)result.put(slot,living.getItemBySlot(slot).copy());
        return result;
    }

    private static void restoreEquipment(LivingEntity living,EnumMap<EquipmentSlot,ItemStack> equipment) {
        for(var entry:equipment.entrySet())living.setItemSlot(entry.getKey(),entry.getValue());
    }

    private static void equip(Minecraft mc,LivingEntity living,int level,boolean vibranium) {
        for(int index=0;index<ARMOR.length;index++)living.setItemSlot(ARMOR[index],enchanted(mc,vibranium?vibranium(index):IRON[index],level));
    }

    private static Item vibranium(int index) {
        var id=ResourceLocation.fromNamespaceAndPath("mite","vibranium_"+PIECES[index]);
        require(BuiltInRegistries.ITEM.containsKey(id),"Missing native vibranium armor: "+id);
        return BuiltInRegistries.ITEM.get(id);
    }

    private static ItemStack enchanted(Minecraft mc,Item item,int level) {
        ItemStack result=new ItemStack(item);
        if(level>0)result.enchant(mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),level);
        return result;
    }

    private static void neutral(LivingEntity living) {
        living.tickCount=160;living.setYRot(0);living.yRotO=0;living.setXRot(0);living.xRotO=0;
        living.yBodyRot=living.yBodyRotO=living.yHeadRot=living.yHeadRotO=0;
        living.attackAnim=living.oAttackAnim=0;living.setPose(Pose.STANDING);living.setInvisible(false);
        living.walkAnimation.update(0,1);
    }

    private static PixelDifference difference(NativeImage reference,NativeImage actual) {
        int changed=0,outside=0,farOutside=0;
        int minX=WIDTH,maxX=-1,minY=HEIGHT,maxY=-1;
        for(int y=80;y<770;y++)for(int x=12;x<WIDTH-12;x++)if(delta(reference.getPixelRGBA(x,y),BACKGROUND)>24) {
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        for(int y=80;y<770;y++)for(int x=12;x<WIDTH-12;x++) {
            int before=reference.getPixelRGBA(x,y),after=actual.getPixelRGBA(x,y);
            if(delta(before,after)>24) {
                changed++;
                if(delta(before,BACKGROUND)<18)outside++;
                if(x<minX-28||x>maxX+28||y<minY-28||y>maxY+28)farOutside++;
            }
        }
        return new PixelDifference(changed,outside,farOutside);
    }

    private static int delta(int first,int second) {
        return Math.abs((first&255)-(second&255))+Math.abs((first>>>8&255)-(second>>>8&255))+Math.abs((first>>>16&255)-(second>>>16&255));
    }

    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}

    private record PixelDifference(int changed,int outsideReference,int farOutsideReference) {
        JsonObject json(){JsonObject result=new JsonObject();result.addProperty("changed_pixels",changed);result.addProperty("pixels_outside_reference",outsideReference);result.addProperty("pixels_far_outside_wearer",farOutsideReference);return result;}
    }

    private record SavedWalk(float speedOld,float speed,float position) {
        SavedWalk(WalkAnimationState walk)throws Exception{this(read(walk,"speedOld"),read(walk,"speed"),read(walk,"position"));}
        private static float read(WalkAnimationState walk,String name)throws Exception{Field field=WalkAnimationState.class.getDeclaredField(name);field.setAccessible(true);return field.getFloat(walk);}
        void restore(WalkAnimationState walk)throws Exception{write(walk,"speedOld",speedOld);write(walk,"speed",speed);write(walk,"position",position);}
        private static void write(WalkAnimationState walk,String name,float value)throws Exception{Field field=WalkAnimationState.class.getDeclaredField(name);field.setAccessible(true);field.setFloat(walk,value);}
    }

    private record SavedPose(int ticks,float yaw,float oldYaw,float pitch,float oldPitch,float body,float oldBody,
            float head,float oldHead,float attack,float oldAttack,Pose pose,boolean invisible) {
        SavedPose(LivingEntity living){this(living.tickCount,living.getYRot(),living.yRotO,living.getXRot(),living.xRotO,
                living.yBodyRot,living.yBodyRotO,living.yHeadRot,living.yHeadRotO,living.attackAnim,living.oAttackAnim,living.getPose(),living.isInvisible());}
        void restore(LivingEntity living) {
            living.tickCount=ticks;living.setYRot(yaw);living.yRotO=oldYaw;living.setXRot(pitch);living.xRotO=oldPitch;
            living.yBodyRot=body;living.yBodyRotO=oldBody;living.yHeadRot=head;living.yHeadRotO=oldHead;
            living.attackAnim=attack;living.oAttackAnim=oldAttack;living.setPose(pose);living.setInvisible(invisible);
        }
    }
}
