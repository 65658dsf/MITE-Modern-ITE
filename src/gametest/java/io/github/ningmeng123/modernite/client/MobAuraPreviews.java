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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Native mob particles and cosmic armor inside the disposable GPU audit world. @author NingMeng123 */
final class MobAuraPreviews {
    private static final int WIDTH=640, HEIGHT=720, FEET=580;
    private static final float SCALE=180;
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final ExtremeKind[] KINDS={ExtremeKind.SKELETON_BLAZE,ExtremeKind.SKELETON_LIGHTNING,
            ExtremeKind.SKELETON_FLOATING,ExtremeKind.ANNIHILATION_SKELETON,ExtremeKind.UNDYING_ZOMBIE_EMPEROR};
    private static final String[] LABELS={"烈焰骷髅","闪电骷髅","漂浮骷髅","湮灭骷髅","不朽尸王","振金星空盔甲"};
    private static final String[] DETAILS={"火焰环绕 · 熔岩火星","电光环绕 · 电弧火花","脚下云朵 · 漂浮气流",
            "虚空紫环 · 暗影烟雾","猩红灵焰 · 振金星空盔甲","原生振金材质 · 动态星光叠加"};
    private MobAuraPreviews() {}

    static JsonArray capture(Minecraft mc) throws Exception {
        if(mc.level==null||mc.player==null)throw new IllegalStateException("Aura previews require the audit world");
        Path folder=Path.of(System.getProperty("modern_ite.mobRenderAuditOutput","build/verification/mob-render-audit.json"))
                .toAbsolutePath().normalize().getParent().resolve("mob-render-audit");
        Files.createDirectories(folder);
        JsonArray results=new JsonArray();
        var equipment=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
        for(var slot:EquipmentSlot.values())if(slot!=EquipmentSlot.BODY)equipment.put(slot,mc.player.getItemBySlot(slot).copy());
        var playerState=new SavedPose(mc.player);
        long dayTime=mc.level.getDayTime();
        boolean daylight=mc.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        var particleSetting=mc.options.particles().get();
        try(NativeImage sheet=new NativeImage(WIDTH*2,HEIGHT*3,false)) {
            mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
            mc.level.setDayTime(6000);
            mc.gameRenderer.lightTexture().tick();
            mc.gameRenderer.lightTexture().updateLightTexture(1);
            for(var slot:equipment.keySet())mc.player.setItemSlot(slot,ItemStack.EMPTY);
            for(int index=0;index<6;index++) {
                LivingEntity living;
                Mob mob=null;
                String id;
                if(index<KINDS.length) {
                    mob=ExtremeMobs.type(KINDS[index]).create(mc.level);
                    if(mob==null)throw new IllegalStateException("Cannot create aura preview "+KINDS[index]);
                    MobAbilities.initialize(mob,(ExtremeMob)mob);
                    mob.setNoAi(true);
                    mob.setPos(mc.player.position().add(2,0,0));
                    mc.level.addEntity(mob);
                    living=mob;
                    id=KINDS[index].id();
                } else {
                    living=mc.player;
                    equipProtectionFive(mc);
                    id="player_protection_v";
                }
                try {
                    neutralPose(living);
                    // setLevel clears both active and pending particles while keeping the loaded native providers/atlas.
                    mc.particleEngine.setLevel(mc.level);
                    try(NativeImage baseline=render(mc,living,index,false)) {
                        for(int frame=0;frame<40;frame++) {
                            // Offset the entity phase with its actual id so every sample shows the same positive clock.
                            living.tickCount=120+frame-Math.floorMod(living.getId()*7,120);
                            if(mob!=null)ExtremeMobParticles.tick(new ClientTickEvent.Post());
                            mc.particleEngine.tick();
                        }
                        int count=Integer.parseInt(mc.particleEngine.countParticles());
                        if(mob!=null&&count==0)throw new IllegalStateException("Aura preview emitted no native particles: "+id);
                        if(mob==null&&count!=0)throw new IllegalStateException("Protection V cosmic armor must not emit external particles: "+count);
                        try(NativeImage image=render(mc,living,index,mob!=null)) {
                            int changed=0,outsideModel=0;
                            for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
                                int before=baseline.getPixelRGBA(x,y),after=image.getPixelRGBA(x,y);
                                if(delta(before,after)>18) {
                                    changed++;
                                    if(delta(before,0xFF201810)<18)outsideModel++;
                                }
                                sheet.setPixelRGBA((index%2)*WIDTH+x,(index/2)*HEIGHT+y,after);
                            }
                            if(mob!=null&&outsideModel<20)throw new IllegalStateException("Native aura has no visible particles outside the model: "+id+" ("+outsideModel+")");
                            Path file=folder.resolve("aura_"+id+".png");
                            image.writeToFile(file);
                            JsonObject result=new JsonObject();
                            result.addProperty("entity",id);result.addProperty("label",LABELS[index]);
                            result.addProperty("file",file.toString());result.addProperty("native_particle_count",count);
                            result.addProperty("changed_pixels",changed);result.addProperty("particle_pixels_outside_model",outsideModel);
                            result.addProperty("native_equipment_initialized",mob!=null);
                            result.addProperty("full_protection_v",ArmorAuraRules.fullProtectionFive(living));
                            result.addProperty("preview_mode",mob==null?"cosmic_armor":"native_mob_particles");
                            results.add(result);
                        }
                    }
                } finally {
                    if(mob!=null)mob.discard();
                    mc.particleEngine.setLevel(mc.level);
                }
            }
            sheet.writeToFile(folder.resolve("aura-previews.png"));
            results.addAll(VibraniumGildingPreviews.capture(mc,folder));
        } finally {
            for(var entry:equipment.entrySet())mc.player.setItemSlot(entry.getKey(),entry.getValue());
            playerState.restore(mc.player);
            mc.options.particles().set(particleSetting);
            mc.level.setDayTime(dayTime);
            mc.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(daylight,null);
            mc.gameRenderer.lightTexture().tick();
            mc.gameRenderer.lightTexture().updateLightTexture(1);
            mc.particleEngine.setLevel(mc.level);
        }
        return results;
    }

    private static void equipProtectionFive(Minecraft mc) {
        var protection=mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        Item[] pieces={vibranium("helmet"),vibranium("chestplate"),vibranium("leggings"),vibranium("boots")};
        for(int index=0;index<ARMOR.length;index++) {
            ItemStack stack=new ItemStack(pieces[index]);stack.enchant(protection,5);
            mc.player.setItemSlot(ARMOR[index],stack);
        }
    }

    private static Item vibranium(String piece) {
        var id=ResourceLocation.fromNamespaceAndPath("mite","vibranium_"+piece);
        if(!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalStateException("Missing native vibranium armor: "+id);
        return BuiltInRegistries.ITEM.get(id);
    }

    private static void neutralPose(LivingEntity living) {
        living.setYRot(0);living.yRotO=0;living.setXRot(0);living.xRotO=0;
        living.yBodyRot=living.yBodyRotO=living.yHeadRot=living.yHeadRotO=0;
        living.attackAnim=living.oAttackAnim=0;living.setPose(Pose.STANDING);living.setInvisible(false);
    }

    private static NativeImage render(Minecraft mc,LivingEntity living,int index,boolean particles) {
        TextureTarget target=new TextureTarget(WIDTH,HEIGHT,true,Minecraft.ON_OSX);
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting=RenderSystem.getVertexSorting();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        float[] shaderColor=RenderSystem.getShaderColor().clone();
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try {
            target.setClearColor(16F/255,24F/255,32F/255,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,WIDTH,HEIGHT,0,-1000,1000),VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShaderFogStart(10000);RenderSystem.setShaderFogEnd(10001);
            GuiGraphics graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            Quaternionf rotation=new Quaternionf().rotationZ((float)Math.PI).rotateX(-.12F).rotateY((float)Math.PI+.35F);
            InventoryScreen.renderEntityInInventory(graphics,WIDTH/2F,FEET,SCALE,new Vector3f(),rotation,null,living);
            graphics.flush();mc.renderBuffers().bufferSource().endBatch();
            if(particles) {
                // SingleQuadParticle supplies camera-relative world positions directly to the shader,
                // so reproduce the native inventory entity transform in the global model-view stack.
                modelView.identity().translate(WIDTH/2F,FEET,50).scale(SCALE,SCALE,-SCALE).rotate(rotation);
                RenderSystem.applyModelViewMatrix();
                PreviewCamera camera=new PreviewCamera(living.position(),new Quaternionf(rotation).conjugate());
                RenderSystem.disableCull();
                mc.particleEngine.render(mc.gameRenderer.lightTexture(),camera,1,null,type->true);
                RenderSystem.enableCull();
                modelView.identity();RenderSystem.applyModelViewMatrix();
            }
            RenderSystem.disableDepthTest();
            graphics.pose().pushPose();graphics.pose().scale(2,2,2);
            graphics.drawCenteredString(mc.font,LABELS[index],WIDTH/4,17,0xFFF2F5FA);
            graphics.pose().popPose();
            graphics.drawCenteredString(mc.font,DETAILS[index],WIDTH/2,664,0xFFC3D1DD);
            graphics.drawCenteredString(mc.font,index<KINDS.length?"原版渲染器与真实粒子 · NingMeng123":"原生振金材质与动态星光 · NingMeng123",WIDTH/2,690,0xFF8195A6);
            graphics.flush();RenderSystem.enableDepthTest();
            return Screenshot.takeScreenshot(target);
        } finally {
            mc.renderBuffers().bufferSource().endBatch();
            modelView.popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,sorting);
            RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
            mc.getMainRenderTarget().bindWrite(true);target.destroyBuffers();
        }
    }

    private static int delta(int a,int b) {
        return Math.abs((a&255)-(b&255))+Math.abs((a>>>8&255)-(b>>>8&255))+Math.abs((a>>>16&255)-(b>>>16&255));
    }

    private static final class PreviewCamera extends Camera {
        PreviewCamera(Vec3 position,Quaternionf rotation){setPosition(position);rotation().set(rotation);}
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
