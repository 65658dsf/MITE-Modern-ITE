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
import net.minecraft.client.model.dragon.DragonHeadModel;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
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
    private static final ModelLayerLocation STRONG_ZOMBIE=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"zombie_fly_away_uv64"),"main");
    private static final ModelLayerLocation SKELETON_ZOMBIE=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"skeleton_zombie"),"main");
    private static final ModelLayerLocation SOURCE_WITCH=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"source_witch"),"main");
    private static final ModelLayerLocation HEADLESS_ZOMBIE=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"headless_zombie"),"main");
    private static final ModelLayerLocation GHAST_HEAD=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"zombie_ghast_head"),"main");
    private static final ModelLayerLocation DRAGON_HEAD=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"zombie_dragon_head"),"main");
    private static final ModelLayerLocation RUN_CREEPER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"run_creeper"),"main");
    private static final ModelLayerLocation PHASE_CREEPER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"phase_creeper"),"main");
    private static final ModelLayerLocation CHAOS_CREEPER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"chaos_creeper"),"main");
    private ExtremeMobRenderers(){}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(CALLER,CallerModel::layer);
        event.registerLayerDefinition(LEGACY_ZOMBIE,()->LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,32));
        event.registerLayerDefinition(STRONG_ZOMBIE,ExtremeMobRenderers::strongZombieLayer);
        event.registerLayerDefinition(SKELETON_ZOMBIE,()->LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64));
        event.registerLayerDefinition(SOURCE_WITCH,WitchModel::createBodyLayer);
        event.registerLayerDefinition(HEADLESS_ZOMBIE,VariantZombieModel::headlessLayer);
        event.registerLayerDefinition(GHAST_HEAD,SpeciesHeadLayer::ghastLayer);
        event.registerLayerDefinition(DRAGON_HEAD,DragonHeadModel::createHeadLayer);
        event.registerLayerDefinition(RUN_CREEPER,()->VariantCreeperModel.layer(ExtremeKind.RUN_CREEPER));
        event.registerLayerDefinition(PHASE_CREEPER,()->VariantCreeperModel.layer(ExtremeKind.PHASE_CREEPER));
        event.registerLayerDefinition(CHAOS_CREEPER,()->VariantCreeperModel.layer(ExtremeKind.CHAOS_CREEPER));
    }
    private static LayerDefinition strongZombieLayer(){
        // The source ModelZombie uses 64-high UVs even though this PNG is only 32 pixels tall.
        var mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);
        // The source skin's opaque green outer head would cover its actual face and eyes.
        // Keep the required hat node empty; separately rendered armor helmets still work.
        mesh.getRoot().addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64);
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
                        if(kind==ExtremeKind.ZOMBIE_FLY_AWAY)this.model=new VariantZombieModel(context.bakeLayer(STRONG_ZOMBIE),kind);
                        if(kind==ExtremeKind.FISHING_ROD_ZOMBIE||kind==ExtremeKind.ZOMBIE_DOOR)this.model=new VariantZombieModel(context.bakeLayer(ModelLayers.ZOMBIE),kind);
                        if(kind==ExtremeKind.ZOMBIE_GHAST||kind==ExtremeKind.DRAGON_ZOMBIE){
                            this.model=new VariantZombieModel(context.bakeLayer(HEADLESS_ZOMBIE),kind);
                            addLayer(new SpeciesHeadLayer(this,kind,context.bakeLayer(kind==ExtremeKind.ZOMBIE_GHAST?GHAST_HEAD:DRAGON_HEAD)));
                        }
                        if(kind==ExtremeKind.ZOMBIE_DOOR){
                            layers.removeIf(layer->layer instanceof ItemInHandLayer);
                            addLayer(new DoorShieldLayer(this));
                        }
                        if(kind==ExtremeKind.RED_GHOUL)this.model=new ZombieModel<>(context.bakeLayer(LEGACY_ZOMBIE));
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
                case SKELETON -> new SkeletonRenderer<AbstractSkeleton>(context,
                        kind==ExtremeKind.WITHER_SKELETON_ARROW?ModelLayers.WITHER_SKELETON:ModelLayers.SKELETON,
                        kind==ExtremeKind.WITHER_SKELETON_ARROW?ModelLayers.WITHER_SKELETON_INNER_ARMOR:ModelLayers.SKELETON_INNER_ARMOR,
                        kind==ExtremeKind.WITHER_SKELETON_ARROW?ModelLayers.WITHER_SKELETON_OUTER_ARMOR:ModelLayers.SKELETON_OUTER_ARMOR){
                    {if(kind==ExtremeKind.SKELETON_ZOMBIE)this.model=new SkeletonZombieModel(context.bakeLayer(SKELETON_ZOMBIE),()->context.bakeLayer(ModelLayers.SKELETON));if(kind==ExtremeKind.ANNIHILATION_SKELETON)addLayer(new SourceGlowLayer<AbstractSkeleton,SkeletonModel<AbstractSkeleton>>(this,skeleton->texture("skeleton/annihilation_skeleton_glow")));}
                    @Override public ResourceLocation getTextureLocation(AbstractSkeleton skeleton){return kind==ExtremeKind.WITHER_SKELETON_ARROW?ResourceLocation.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png"):source(kind)==null?super.getTextureLocation(skeleton):texture(source(kind));}
                    @Override protected void scale(AbstractSkeleton skeleton,PoseStack pose,float partial){super.scale(skeleton,pose,partial);if(kind==ExtremeKind.WITHER_SKELETON_ARROW)pose.scale(1.2F,1.2F,1.2F);}
                };
                case CREEPER -> new CreeperRenderer(context){
                    {
                    if(kind==ExtremeKind.RUN_CREEPER||kind==ExtremeKind.PHASE_CREEPER||kind==ExtremeKind.CHAOS_CREEPER)
                        this.model=new VariantCreeperModel(context.bakeLayer(kind==ExtremeKind.RUN_CREEPER?RUN_CREEPER:kind==ExtremeKind.PHASE_CREEPER?PHASE_CREEPER:CHAOS_CREEPER),kind);
                    if(kind==ExtremeKind.PHASE_CREEPER)addLayer(new SourceGlowLayer<Creeper,CreeperModel<Creeper>>(this,creeper->texture("phase_creeper_glow")));
                    if(kind==ExtremeKind.CHAOS_CREEPER)addLayer(new ChaosArrowLayer(this));
                    if(kind==ExtremeKind.INVISIBLE_CREEPER||kind==ExtremeKind.INVISIBLE_REVERSED_CREEPER)this.model=new CreeperModel<Creeper>(context.bakeLayer(ModelLayers.CREEPER)){
                        @Override public void renderToBuffer(PoseStack p,VertexConsumer v,int light,int overlay,int color){super.renderToBuffer(p,v,light,overlay,0x0DFFFFFF);}
                    };}
                    @Override public ResourceLocation getTextureLocation(Creeper creeper){return kind==ExtremeKind.PHASE_CREEPER?ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png"):texture(source(kind));}
                    @Override protected RenderType getRenderType(Creeper creeper,boolean visible,boolean translucent,boolean outline){
                        return kind==ExtremeKind.INVISIBLE_CREEPER||kind==ExtremeKind.INVISIBLE_REVERSED_CREEPER?RenderType.entityTranslucent(getTextureLocation(creeper)):super.getRenderType(creeper,visible,translucent,outline);
                    }
                };
                case WITCH -> new WitchRenderer(context){
                    {
                        this.model=new SourceWitchModel(context.bakeLayer(SOURCE_WITCH),()->context.bakeLayer(ModelLayers.WITCH));
                        addLayer(new SourceGlowLayer<Witch,WitchModel<Witch>>(this,witch->texture(source(kind)+"_glow")));
                    }
                    @Override public ResourceLocation getTextureLocation(Witch witch){return texture(source(kind));}
                };
                case GHAST -> new GhastRenderer(context){
                    {addLayer(new SourceGlowLayer<Ghast,GhastModel<Ghast>>(this,ghast->texture(ghast.isCharging()?"revenant_ghast_shooting_glow":"revenant_ghast_glow")));}
                    // The source renderer pairs vanilla ghast bodies with its separate custom glow textures.
                    @Override public ResourceLocation getTextureLocation(Ghast ghast){return ResourceLocation.withDefaultNamespace("textures/entity/ghast/"+(ghast.isCharging()?"ghast_shooting":"ghast")+".png");}
                    @Override protected void scale(Ghast ghast,PoseStack pose,float partial){super.scale(ghast,pose,partial);pose.scale(1.5F,1.5F,1.5F);}
                };
                case GOLEM -> new IronGolemRenderer(context){@Override public ResourceLocation getTextureLocation(IronGolem golem){return texture(source(kind));}};
                case PILLAGER -> new PillagerRenderer(context);
                case PIGMAN -> new PiglinRenderer(context,ModelLayers.ZOMBIFIED_PIGLIN,ModelLayers.ZOMBIFIED_PIGLIN_INNER_ARMOR,ModelLayers.ZOMBIFIED_PIGLIN_OUTER_ARMOR,true){
                    @Override public ResourceLocation getTextureLocation(Mob pigman){return ResourceLocation.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png");}
                };
            };
            event.registerEntityRenderer((EntityType)ExtremeMobs.type(kind),provider);
        }
        event.registerEntityRenderer(InfernalFireball.NB.get(),context->new ThrownItemRenderer<>(context,3F,true));
        event.registerEntityRenderer(InfernalFireball.INFERNAL.get(),context->new ThrownItemRenderer<>(context,3F,true));
    }
}
