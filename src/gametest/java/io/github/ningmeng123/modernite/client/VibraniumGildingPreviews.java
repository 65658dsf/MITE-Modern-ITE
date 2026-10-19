package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
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
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

    private static JsonObject nativeMaterialControl(NativeImage baseline,NativeImage actual) {
        String[] names={"helmet","chest_and_arms","leggings_and_boots"};
        int[] edges={100,360,545,770};
        JsonObject result=new JsonObject();int total=0,totalQuiet=0,totalStars=0;
        for(int region=0;region<names.length;region++) {
            int pixels=0,quiet=0,stars=0,edgesSeen=0,edgesRetained=0;
            double sumBefore=0,sumAfter=0,sumBeforeSquared=0,sumAfterSquared=0,cross=0;
            for(int y=edges[region];y<edges[region+1];y++)for(int x=100;x<WIDTH-100;x++) {
                int before=baseline.getPixelRGBA(x,y),after=actual.getPixelRGBA(x,y);
                if(!nativeArmorPixel(before))continue;
                pixels++;
                if(delta(before,after)>45&&brightness(after)>brightness(before)+18)stars++;
                // The diffuse cloud is faint; native colors away from bright stars must remain close.
                if(delta(before,after)<=48) {
                    quiet++;double a=brightness(before),b=brightness(after);
                    sumBefore+=a;sumAfter+=b;sumBeforeSquared+=a*a;sumAfterSquared+=b*b;cross+=a*b;
                    int nextBefore=baseline.getPixelRGBA(x+1,y),nextAfter=actual.getPixelRGBA(x+1,y);
                    if(nativeArmorPixel(nextBefore)&&delta(nextBefore,nextAfter)<=48&&delta(before,nextBefore)>=18) {
                        edgesSeen++;
                        double originalEdge=brightness(nextBefore)-a,newEdge=brightness(nextAfter)-b;
                        if(Math.abs(originalEdge)>=6&&originalEdge*newEdge>0
                                &&Math.abs(newEdge)>=Math.abs(originalEdge)*.5&&Math.abs(newEdge)<=Math.abs(originalEdge)*1.6)edgesRetained++;
                        else if(Math.abs(originalEdge)<6&&Math.abs(newEdge-originalEdge)<8)edgesRetained++;
                    }
                }
            }
            double covariance=cross-sumBefore*sumAfter/Math.max(1,quiet);
            double varianceBefore=sumBeforeSquared-sumBefore*sumBefore/Math.max(1,quiet);
            double varianceAfter=sumAfterSquared-sumAfter*sumAfter/Math.max(1,quiet);
            double correlation=covariance/Math.sqrt(Math.max(.000001,varianceBefore*varianceAfter));
            require(pixels>300,"Material GPU control found too few native armor pixels: "+names[region]+" "+pixels);
            require(quiet>=pixels*.6,"Starfield replaces too much native armor color: "+names[region]+" "+quiet+"/"+pixels);
            require(correlation>.90,"Native armor texture/shading correlation was lost: "+names[region]+" "+correlation);
            require(edgesSeen>30&&edgesRetained>=edgesSeen*.8,"Native armor texture edges were lost: "+names[region]+" "+edgesRetained+"/"+edgesSeen);
            require(stars>15,"No visible star highlights were added to "+names[region]+": "+stars);
            JsonObject facts=new JsonObject();facts.addProperty("native_blue_and_cyan_armor_pixels",pixels);
            facts.addProperty("near_native_color_pixels",quiet);facts.addProperty("near_native_color_fraction",quiet/(double)pixels);
            facts.addProperty("native_texture_brightness_correlation",correlation);
            facts.addProperty("native_texture_edges",edgesSeen);facts.addProperty("retained_texture_edges",edgesRetained);
            facts.addProperty("retained_texture_edge_fraction",edgesRetained/(double)edgesSeen);
            facts.addProperty("visible_star_highlight_pixels",stars);
            result.add(names[region],facts);total+=pixels;totalQuiet+=quiet;totalStars+=stars;
        }
        require(totalStars>100,"Native-material control passed without enough visible star highlights: "+totalStars);
        result.addProperty("total_native_armor_pixels",total);result.addProperty("total_near_native_color_fraction",totalQuiet/(double)total);
        result.addProperty("total_visible_star_highlights",totalStars);result.addProperty("native_material_and_texture_preserved",true);
        result.addProperty("visible_star_overlay",true);return result;
    }

    private static boolean nativeArmorPixel(int color) {
        int red=color&255,green=color>>>8&255,blue=color>>>16&255;
        return blue>red*1.1F&&blue>green*1.05F&&green>30;
    }

    private static double brightness(int color){return ((color&255)+(color>>>8&255)+(color>>>16&255))/3D;}

    private static JsonObject nativeMaterialWithoutHeldItems(Minecraft mc,LivingEntity living,String title,String detail)throws Exception {
        var saved=equipment(living);
        // Exclude the emperor's blue weapon and vanilla's independent purple enchantment glint.
        living.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);living.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        for(var slot:ARMOR) {
            ItemStack stack=living.getItemBySlot(slot).copy();stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,false);
            living.setItemSlot(slot,stack);
        }
        try(NativeImage clearActual=render(mc,living,false,title,detail);
            NativeImage clearBaseline=withoutGilding(mc,living,title,detail)) {
            JsonObject result=nativeMaterialControl(clearBaseline,clearActual);
            result.addProperty("held_items_excluded_from_armor_sampling",true);result.addProperty("native_item_glint_isolated",true);return result;
        } finally {restoreEquipment(living,saved);}
    }

    private static JsonObject backSurfaceAudit(Minecraft mc,LivingEntity living,Path folder,String id)throws Exception {
        SavedPose pose=new SavedPose(living);SavedWalk walk=new SavedWalk(living.walkAnimation);
        String title="振金星空盔甲",detail="背面 · 原生振金材质 · 动态星光叠加";
        try {
            neutral(living);
            try(NativeImage baseline=withoutGilding(mc,living,true,title,detail);
                NativeImage actual=render(mc,living,true,title,detail)) {
                JsonObject result=armorSilhouetteControl(baseline,actual);
                actual.writeToFile(folder.resolve("cosmic-protection-v-"+id+"-back.png"));
                baseline.writeToFile(folder.resolve("cosmic-"+id+"-back-without-layer.png"));
                result.addProperty("portrait_scale",SCALE);result.addProperty("status","passed");return result;
            }
        } finally {pose.restore(living);walk.restore(living.walkAnimation);}
    }

    private static JsonObject armorSilhouetteControl(NativeImage baseline,NativeImage actual) {
        // Cosmic armor is inflated 0.01 model pixels beyond native armor: under one screen pixel
        // at this scale. One additional pixel covers rasterization at the alpha-cutout boundary.
        int margin=(int)Math.ceil(SCALE*.01F/16)+1;
        int nativePixels=0,newFringe=0,outside=0,clipped=0;
        for(int y=90;y<770;y++)for(int x=0;x<WIDTH;x++) {
            if(foreground(baseline.getPixelRGBA(x,y)))nativePixels++;
            if(!foreground(actual.getPixelRGBA(x,y)))continue;
            if(x<12||x>=WIDTH-12)clipped++;
            if(foreground(baseline.getPixelRGBA(x,y)))continue;
            newFringe++;boolean touchesNative=false;
            for(int dy=-margin;dy<=margin&&!touchesNative;dy++)for(int dx=-margin;dx<=margin;dx++) {
                int nx=x+dx,ny=y+dy;
                if(nx>=0&&nx<WIDTH&&ny>=90&&ny<770&&foreground(baseline.getPixelRGBA(nx,ny))) {
                    touchesNative=true;break;
                }
            }
            if(!touchesNative)outside++;
        }
        require(nativePixels>1000,"No native wearer silhouette was available for the geometry control");
        require(outside==0,"Cosmic armor renders geometry beyond the native armor silhouette: "+outside);
        require(clipped==0,"Armor portrait clips the wearer at the image edge: "+clipped);
        JsonObject result=new JsonObject();result.addProperty("native_silhouette_pixels",nativePixels);
        result.addProperty("raster_and_inflation_margin_pixels",margin);result.addProperty("new_pixels_within_margin",newFringe);
        result.addProperty("pixels_beyond_native_silhouette",outside);result.addProperty("clipped_edge_pixels",clipped);
        result.addProperty("no_additional_back_geometry",true);return result;
    }

    private static boolean foreground(int color){return delta(color,BACKGROUND)>24;}

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
            try(NativeImage front=renderHero(mc,mc.player,false,"振金星空盔甲","玩家 · 原生振金材质 · 动态星光叠加");
                NativeImage rear=renderHero(mc,mc.player,true,"振金星空盔甲","背面 · 原生振金材质 · 动态星光叠加")) {
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
                try(NativeImage image=renderHero(mc,emperor,false,"不朽尸王 · 振金星海","原生振金材质 · 动态星光叠加")) {
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
        String title="振金星空盔甲",detail="原生振金材质 · 动态星光叠加";
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
                result.add("native_material_preservation_gpu_control",nativeMaterialWithoutHeldItems(mc,living,title,detail));
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
                        require(attached.changed()>600,"Walking cosmic armor is not visible: "+attached);
                        result.add("walking_gilding_difference",attached.json());
                    }
                    result.add("native_walk_and_turn_difference",movement.json());
                    result.addProperty("native_walk_speed",living.walkAnimation.speed());
                    moving.writeToFile(folder.resolve("vibranium-protection-v-"+id+"-walking.png"));
                }
            }
        } finally {restoreEquipment(living,saved);neutral(living);walk.restore(living.walkAnimation);}
        result.add("back_surface_geometry_gpu_control",backSurfaceAudit(mc,living,folder,id));
        result.add("fixed_pose_animation",animationAudit(mc,living,folder,id));
        result.add("native_armor_glint_gpu_control",glintAudit(mc,living,folder,id));
        result.addProperty("status","passed");return result;
    }

    private static JsonObject glintAudit(Minecraft mc,LivingEntity living,Path folder,String id)throws Exception {
        var saved=equipment(living);SavedPose pose=new SavedPose(living);SavedWalk walk=new SavedWalk(living.walkAnimation);
        float savedAlpha=RenderSystem.getShaderGlintAlpha();
        long fixedNanos=Util.getNanos();
        JsonObject result=new JsonObject();
        try {
            neutral(living);
            living.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            living.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
            RenderSystem.setShaderGlintAlpha(1F);
            result.add("ordinary_protection_v",glintSourceAudit(mc,living,folder,id,false,fixedNanos));
            result.add("growth_protection_v",glintSourceAudit(mc,living,folder,id,true,fixedNanos));
            var clock=Util.timeSource;
            try {
                Util.timeSource=()->fixedNanos;
                equip(mc,living,4,true);
                result.add("ordinary_iv_unchanged",disabledControl(mc,living,"附魔光效门槛对照","全套保护IV · 原版光效保持"));
                equipGrowth(living,4);
                result.add("growth_iv_unchanged",disabledControl(mc,living,"附魔光效门槛对照","成长保护IV · 无额外光效"));
                equip(mc,living,5,false);
                result.add("iron_v_unchanged",disabledControl(mc,living,"附魔光效门槛对照","铁套保护V · 原版光效保持"));
            } finally {Util.timeSource=clock;}
            result.addProperty("native_glint_clock_frozen_only_during_test_draws",true);
            result.addProperty("native_util_clock_restored_after_each_draw",true);
            result.addProperty("fixed_cosmic_tick",160);
            result.addProperty("held_items_excluded",true);
            result.addProperty("single_native_glint_after_starlight",true);
            result.addProperty("status","passed");
        } finally {
            RenderSystem.setShaderGlintAlpha(savedAlpha);
            restoreEquipment(living,saved);pose.restore(living);walk.restore(living.walkAnimation);
        }
        return result;
    }

    private static JsonObject glintSourceAudit(Minecraft mc,LivingEntity living,Path folder,String id,
            boolean growth,long fixedNanos)throws Exception {
        String source=growth?"growth":"ordinary";
        if(growth)equipGrowth(living,5);else equip(mc,living,5,true);
        require(ArmorAuraRules.fullProtectionFive(living),"Glint positive control needs full vibranium protection V");
        for(var slot:ARMOR)require(living.getItemBySlot(slot).hasFoil()!=growth,
                "Ordinary V must have native foil; growth-only V must exercise the no-native-foil path");
        JsonObject result=new JsonObject();
        try(NativeImage enabled=glintRender(mc,living,fixedNanos,0)) {
            for(var slot:ARMOR)living.getItemBySlot(slot).set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,false);
            require(ArmorAuraRules.fullProtectionFive(living),"Explicit foil suppression must keep the starlight gate active");
            try(NativeImage disabled=glintRender(mc,living,fixedNanos,0);
                NativeImage oneNativePass=glintRender(mc,living,fixedNanos,1);
                NativeImage twoNativePasses=glintRender(mc,living,fixedNanos,2)) {
                PixelDifference visible=difference(disabled,enabled);
                require(visible.changed()>250,"No visible native glint over "+source+" protection V: "+visible);
                PixelDifference one=difference(oneNativePass,enabled),two=difference(twoNativePasses,enabled);
                require(one.changed()<20,"Protection V glint must match exactly one native armor glint after starlight: "+source+" "+one);
                require(two.changed()>250,"Duplicate-glint negative control does not distinguish a second native pass: "+source+" "+two);
                RenderSystem.setShaderGlintAlpha(0F);
                try(NativeImage trulyNoGlint=glintRender(mc,living,fixedNanos,0)) {
                    PixelDifference suppressed=difference(trulyNoGlint,disabled);
                    require(suppressed.changed()<20,"ENCHANTMENT_GLINT_OVERRIDE=false still adds glint: "+source+" "+suppressed);
                    result.add("override_false_vs_zero_glint_alpha",suppressed.json());
                } finally {RenderSystem.setShaderGlintAlpha(1F);}
                String prefix="cosmic-protection-v-"+id+"-"+source+"-glint";
                enabled.writeToFile(folder.resolve(prefix+"-on.png"));
                disabled.writeToFile(folder.resolve(prefix+"-off.png"));
                oneNativePass.writeToFile(folder.resolve(prefix+"-native-single-reference.png"));
                twoNativePasses.writeToFile(folder.resolve(prefix+"-native-double-negative-control.png"));
                try(NativeImage comparison=sideBySide(disabled,enabled)) {
                    comparison.writeToFile(folder.resolve(prefix+"-comparison.png"));
                }
                result.add("visible_native_glint",visible.json());
                result.add("difference_from_one_native_pass_after_starlight",one.json());
                result.add("difference_from_two_native_passes",two.json());
                result.addProperty("comparison",folder.resolve(prefix+"-comparison.png").toString());
                result.addProperty("comparison_columns","left: explicit glint override false; right: original native glint enabled");
                result.addProperty("native_has_foil_without_override",!growth);
                result.addProperty("starlight_gate_kept_enabled_when_glint_hidden",true);
                result.addProperty("status","passed");
            }
        }
        return result;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static NativeImage glintRender(Minecraft mc,LivingEntity living,long fixedNanos,int referencePasses)throws Exception {
        List<Object> layers=layers(mc,living);
        Object reference=null;
        if(referencePasses>0) {
            RenderLayer armor=(RenderLayer)layers.stream().filter(layer->layer instanceof HumanoidArmorLayer<?,?,?>).findFirst().orElseThrow();
            RenderLayerParent renderer=(RenderLayerParent)mc.getEntityRenderDispatcher().getRenderer(living);
            reference=new NativeGlintReferenceLayer(renderer,armor,referencePasses);layers.add(reference);
        }
        var clock=Util.timeSource;
        try {
            Util.timeSource=()->fixedNanos;
            return render(mc,living,false,"振金星光 · 原版附魔光效","同姿态 · 同星空时刻 · 原版紫色附魔光效");
        } finally {Util.timeSource=clock;if(reference!=null)layers.remove(reference);}
    }

    /** Independent reference: native armor's own slot/model/glint path, submitted after starlight. */
    private static final class NativeGlintReferenceLayer extends RenderLayer<LivingEntity,HumanoidModel<LivingEntity>> {
        private final RenderLayer<LivingEntity,HumanoidModel<LivingEntity>> nativeArmor;
        private final int passes;
        NativeGlintReferenceLayer(RenderLayerParent<LivingEntity,HumanoidModel<LivingEntity>> parent,
                RenderLayer<LivingEntity,HumanoidModel<LivingEntity>> nativeArmor,int passes) {
            super(parent);this.nativeArmor=nativeArmor;this.passes=passes;
        }
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,LivingEntity living,
                float limbSwing,float amount,float partialTick,float age,float yaw,float pitch) {
            require(buffers instanceof MultiBufferSource.BufferSource,"Native glint reference needs the actual immediate inventory buffer");
            var output=(MultiBufferSource.BufferSource)buffers;
            // A deliberately unknown wrapper keeps the production deferral hook out of this
            // reference. Only vanilla's own armorEntityGlint vertices reach the real GPU.
            MultiBufferSource glintOnly=type->type==RenderType.armorEntityGlint()?output.getBuffer(type):DiscardVertices.INSTANCE;
            var saved=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
            for(var slot:ARMOR) {
                saved.put(slot,living.getItemBySlot(slot));
                ItemStack stack=living.getItemBySlot(slot).copy();stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
                living.setItemSlot(slot,stack);
            }
            try {
                output.endBatch();
                for(int pass=0;pass<passes;pass++) {
                    nativeArmor.render(pose,glintOnly,light,living,limbSwing,amount,partialTick,age,yaw,pitch);
                    output.endBatch(RenderType.armorEntityGlint());
                }
            } finally {restoreEquipment(living,saved);}
        }
    }

    private enum DiscardVertices implements VertexConsumer {
        INSTANCE;
        @Override public VertexConsumer addVertex(float x,float y,float z){return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }

    private static void equipGrowth(LivingEntity living,int level) {
        for(int index=0;index<ARMOR.length;index++) {
            ItemStack stack=new ItemStack(vibranium(index));
            stack.set(GearProgression.PROGRESS.get(),new GearProgress(new UUID(0,index+1),5,0L,
                    Map.of(ResourceLocation.withDefaultNamespace("protection"),level),1));
            living.setItemSlot(ARMOR[index],stack);
        }
    }

    private static JsonObject animationAudit(Minecraft mc,LivingEntity living,Path folder,String id)throws Exception {
        JsonObject result=new JsonObject();
        String title="振金星空盔甲",detail="原生振金材质 · 动态星光叠加";
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
                            sample.add("back_surface_geometry",armorSilhouetteControl(backBaseline,back));
                            require(equalPose(originalPose,parentPose(mc,living)),"Back-surface animation changed the frozen skeleton");
                            if(firstBack==null) {
                                firstBack=new NativeImage(WIDTH,HEIGHT,false);firstBack.copyFrom(back);
                            } else {
                                int backChanges=difference(firstBack,back).changed();
                                require(backChanges>100,"Back armor starfield does not visibly animate at tick "+animationTick+": "+backChanges);
                                sample.addProperty("animated_back_armor_pixels",backChanges);
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
            result.addProperty("external_geometry","none");
            result.addProperty("no_additional_back_geometry",true);
            if(id.equals("player")) {
                Path animation=folder.resolve("cosmic-animation");Files.createDirectories(animation);
                for(int frame=0;frame<200;frame++) {
                    int animationTick=frame*2;
                    try(NativeImage front=clockedRender(mc,living,false,title,"正面 · 原生振金材质 · 动态星光叠加",animationTick,true);
                        NativeImage rear=clockedRender(mc,living,true,title,"背面 · 原生振金材质 · 动态星光叠加",animationTick,true);
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
        var clock=Util.timeSource;long fixedNanos=Util.getNanos();
        try {
            Util.timeSource=()->fixedNanos;
            try(NativeImage actual=render(mc,living,false,title,detail);
                NativeImage without=withoutGilding(mc,living,title,detail)) {
                PixelDifference difference=difference(without,actual);
                require(difference.changed()<20,"Disabled vibranium gilding still renders geometry: "+difference);
                JsonObject result=difference.json();result.addProperty("gilding_enabled",false);
                result.addProperty("same_native_glint_clock",true);return result;
            }
        } finally {Util.timeSource=clock;}
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
        return render(mc,living,rear,title,detail,SCALE);
    }

    private static NativeImage renderHero(Minecraft mc,LivingEntity living,boolean rear,String title,String detail) {
        return render(mc,living,rear,title,detail,SCALE);
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
            graphics.drawCenteredString(mc.font,"原生振金材质 · 动态星光叠加 · NingMeng123",WIDTH/2,818,0xFF8D9DAC);
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
