package io.github.ningmeng123.modernite.client.entity;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.GhastModel;
import net.minecraft.client.model.WitchModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Each registered identity has a client renderer; legacy artwork uses modern animation models. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ExtremeMobRenderers {
    private static final ModelLayerLocation CALLER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"zombie_caller"),"main");
    private static final ModelLayerLocation LEGACY_ZOMBIE=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"legacy_zombie"),"main");
    private static final ModelLayerLocation SKELETON_ZOMBIE=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"skeleton_zombie"),"main");
    private ExtremeMobRenderers(){}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(CALLER,CallerModel::layer);
        event.registerLayerDefinition(LEGACY_ZOMBIE,()->LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,32));
        event.registerLayerDefinition(SKELETON_ZOMBIE,()->LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64));
    }
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"textures/entity/source/"+name+".png");}
    private static String source(ExtremeKind k){return switch(k){
        case ZOMBIE_FLY_AWAY -> "zombie_fly_away";case ZOMBIE_CHAOS -> "zombie_chaos";case ZOMBIE_GHAST -> "zombie_ghast";
        case ZOMBIE_JUMP -> "zombie_jump";case ZOMBIE_INVISIBLE_LOOK_UP -> "zombie_invisible_look_up";case ZOMBIE_SIEGE -> "zombie_siege";
        case ZOMBIE_CALLER -> "zombie_caller";case SKELETON_ZOMBIE -> "zombies/skeleton_zombie";case RED_GHOUL -> "red_ghoul";
        case SKELETON_FAST_ARROW -> "skeleton_fast_arrow";case SKELETON_KNOCKBACK -> "skeleton_knockback";case SKELETON_BLAZE -> "skeleton_blaze";
        case RUN_CREEPER -> "run_creeper";case PHASE_CREEPER -> "phase_creeper";case CHAOS_CREEPER -> "chaos_creeper";
        case INVISIBLE_CREEPER,INVISIBLE_REVERSED_CREEPER -> "creeper_invisible";case BLACK_INFERNAL_CREEPER -> "black_infernal_creeper";
        case FLASH_CREEPER -> "creeper_flash";case WITHER_CREEPER -> "wither_creeper";case DARK_IRON_GOLEM -> "dark_iron_golem";
        case DRAGON_ZOMBIE -> "dragon_zombie";case WITCH_LORD -> "witch_lord";case WANDERING_WITCH -> "wandering_witch";
        case ANNIHILATION_SKELETON -> "skeleton/annihilation_skeleton";default -> null;
    };}
    @SuppressWarnings({"rawtypes","unchecked"})
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        for(var kind:ExtremeKind.values()){
            EntityRendererProvider provider=context->switch(kind.family){
                case ZOMBIE -> new ZombieRenderer(context){
                    {
                        if(kind==ExtremeKind.ZOMBIE_CALLER)this.model=new CallerModel(context.bakeLayer(CALLER));
                        if(kind==ExtremeKind.ZOMBIE_FLY_AWAY||kind==ExtremeKind.RED_GHOUL)this.model=new ZombieModel<>(context.bakeLayer(LEGACY_ZOMBIE));
                        if(kind==ExtremeKind.ZOMBIE_CHAOS||kind==ExtremeKind.ZOMBIE_INVISIBLE_LOOK_UP)this.model=new ZombieModel<Zombie>(context.bakeLayer(ModelLayers.ZOMBIE)){
                            @Override public void renderToBuffer(PoseStack p,VertexConsumer v,int light,int overlay,int color){super.renderToBuffer(p,v,light,overlay,0x0DFFFFFF);}
                        };
                    }
                    @Override public ResourceLocation getTextureLocation(Zombie zombie){
                        if(kind==ExtremeKind.MIMIC_ZOMBIE&&zombie.getCustomName()!=null&&Minecraft.getInstance().getConnection()!=null){
                            var player=Minecraft.getInstance().getConnection().getPlayerInfo(zombie.getCustomName().getString());
                            if(player!=null)return player.getSkin().texture();
                        }
                        return source(kind)==null?super.getTextureLocation(zombie):texture(source(kind));
                    }
                    @Override protected RenderType getRenderType(Zombie zombie,boolean visible,boolean translucent,boolean outline){return kind==ExtremeKind.ZOMBIE_CHAOS||kind==ExtremeKind.ZOMBIE_INVISIBLE_LOOK_UP?RenderType.entityTranslucent(getTextureLocation(zombie)):super.getRenderType(zombie,visible,translucent,outline);}
                    @Override protected void setupRotations(Zombie mob,PoseStack pose,float age,float yaw,float partial,float scale){
                        super.setupRotations(mob,pose,age,yaw,partial,scale);
                        if(kind==ExtremeKind.ZOMBIE_JUMP){pose.translate(0,mob.getBbHeight()+.1,0);pose.mulPose(Axis.ZP.rotationDegrees(180));}
                    }
                };
                case SKELETON -> new SkeletonRenderer(context){
                    {if(kind==ExtremeKind.SKELETON_ZOMBIE)this.model=new SkeletonModel<>(context.bakeLayer(SKELETON_ZOMBIE));if(kind==ExtremeKind.ANNIHILATION_SKELETON)addLayer(new EyesLayer<AbstractSkeleton,SkeletonModel<AbstractSkeleton>>(this){@Override public RenderType renderType(){return RenderType.eyes(texture("skeleton/annihilation_skeleton_glow"));}});}
                    @Override public ResourceLocation getTextureLocation(AbstractSkeleton skeleton){return source(kind)==null?super.getTextureLocation(skeleton):texture(source(kind));}
                };
                case CREEPER -> new CreeperRenderer(context){
                    {if(kind==ExtremeKind.INVISIBLE_CREEPER||kind==ExtremeKind.INVISIBLE_REVERSED_CREEPER)this.model=new CreeperModel<Creeper>(context.bakeLayer(ModelLayers.CREEPER)){
                        @Override public void renderToBuffer(PoseStack p,VertexConsumer v,int light,int overlay,int color){super.renderToBuffer(p,v,light,overlay,0x0DFFFFFF);}
                    };}
                    @Override public ResourceLocation getTextureLocation(Creeper creeper){return texture(source(kind));}
                    @Override protected RenderType getRenderType(Creeper creeper,boolean visible,boolean translucent,boolean outline){
                        return kind==ExtremeKind.INVISIBLE_CREEPER||kind==ExtremeKind.INVISIBLE_REVERSED_CREEPER?RenderType.entityTranslucent(getTextureLocation(creeper)):super.getRenderType(creeper,visible,translucent,outline);
                    }
                };
                case WITCH -> new WitchRenderer(context){
                    {addLayer(new EyesLayer<Witch,WitchModel<Witch>>(this){@Override public RenderType renderType(){return RenderType.eyes(texture(source(kind)+"_glow"));}});}
                    @Override public ResourceLocation getTextureLocation(Witch witch){return texture(source(kind));}
                };
                case GHAST -> new GhastRenderer(context){
                    {addLayer(new EyesLayer<Ghast,GhastModel<Ghast>>(this){@Override public RenderType renderType(){return RenderType.eyes(texture("revenant_ghast_glow"));}});}
                    @Override protected void scale(Ghast ghast,PoseStack pose,float partial){super.scale(ghast,pose,partial);pose.scale(1.5F,1.5F,1.5F);}
                };
                case GOLEM -> new IronGolemRenderer(context){@Override public ResourceLocation getTextureLocation(IronGolem golem){return texture(source(kind));}};
                case PILLAGER -> new PillagerRenderer(context);
                case PIGMAN -> new PiglinRenderer(context,ModelLayers.ZOMBIFIED_PIGLIN,ModelLayers.ZOMBIFIED_PIGLIN_INNER_ARMOR,ModelLayers.ZOMBIFIED_PIGLIN_OUTER_ARMOR,true);
            };
            event.registerEntityRenderer((EntityType)ExtremeMobs.type(kind),provider);
        }
        event.registerEntityRenderer(InfernalFireball.NB.get(),context->new ThrownItemRenderer<>(context,3F,true));
        event.registerEntityRenderer(InfernalFireball.INFERNAL.get(),context->new ThrownItemRenderer<>(context,3F,true));
    }
}
