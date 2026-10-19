package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.modernite.client.entity.DoorShieldLayer;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real renderer pose comparisons; runs only in the owning disposable audit world. @author NingMeng123 */
final class DoorGuardClientAudit {
    private static final int SIZE=320;
    private static final String[] COLUMNS={"carry-front","carry-oblique","raising-front","block-front",
            "block-oblique","block-walking","melee-oblique"};
    private DoorGuardClientAudit() {}

    static JsonObject capture(Minecraft mc,ExtremeMobs.ZombieVariant zombie,Path folder)throws Exception {
        Files.createDirectories(folder);
        JsonObject report=new JsonObject();JsonArray samples=new JsonArray();report.add("poses",samples);
        JsonArray columns=new JsonArray();for(String column:COLUMNS)columns.add(column);
        report.add("contact_sheet_columns",columns);report.addProperty("contact_sheet_rows","right main arm; left main arm");
        report.addProperty("state_source","Real synchronized entity data; only the detached sample's interpolation floats are fixed for reproducible frames");
        var renderer=mc.getEntityRenderDispatcher().getRenderer(zombie);
        require(renderer instanceof LivingEntityRenderer<?,?>,"The door audit needs the actual living renderer");
        Field layerField=LivingEntityRenderer.class.getDeclaredField("layers");layerField.setAccessible(true);
        List<?> layers=(List<?>)layerField.get(renderer);
        long doors=layers.stream().filter(DoorShieldLayer.class::isInstance).count();
        long items=layers.stream().filter(ItemInHandLayer.class::isInstance).count();
        report.addProperty("door_layer_count",doors);report.addProperty("vanilla_held_item_layer_count",items);
        require(doors==1,"The actual renderer must install exactly one complete door layer");
        require(items==0,"The ordinary held-item layer must not duplicate the full-size door");

        ItemStack priorItem=zombie.getMainHandItem();boolean priorLeft=zombie.isLeftHanded();
        float priorAttack=zombie.attackAnim,priorOldAttack=zombie.oAttackAnim;
        byte priorState=zombie.getEntityData().get(guardAccessor());
        float priorProgress=readFloat(zombie,"doorGuardProgress"),priorOldProgress=readFloat(zombie,"doorGuardProgressOld");
        SavedWalk walk=new SavedWalk(zombie.walkAnimation);
        try(NativeImage sheet=new NativeImage(COLUMNS.length*SIZE,2*SIZE,true)) {
            report.add("tracking_sync_control",verifyTrackingSync(zombie));
            for(int row=0;row<2;row++) {
                boolean left=row==1;String arm=left?"left":"right";float oblique=left?-.65F:.65F;
                zombie.setLeftHanded(left);zombie.attackAnim=zombie.oAttackAnim=0;walk.neutral(zombie.walkAnimation);
                zombie.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);stage(zombie,(byte)0,0);
                // Reuse the real renderer/model before and after armed poses to detect leaked arm pivots.
                try(NativeImage emptyBefore=render(mc,zombie,0)) {
                    zombie.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_DOOR));
                    stage(zombie,(byte)0,0);
                    try(NativeImage carry=pose(mc,zombie,folder,arm,COLUMNS[0],0,row,0,sheet,samples);
                            NativeImage side=pose(mc,zombie,folder,arm,COLUMNS[1],oblique,row,1,sheet,samples)) {
                        stage(zombie,(byte)1,.5F);
                        try(NativeImage raising=pose(mc,zombie,folder,arm,COLUMNS[2],0,row,2,sheet,samples)) {
                            int changed=difference(carry,raising);
                            report.addProperty(arm+"_raising_changed_pixels",changed);
                            require(changed>100,"Raising the door must visibly change the native pose: "+arm);
                        }
                        stage(zombie,(byte)2,1);
                        try(NativeImage block=pose(mc,zombie,folder,arm,COLUMNS[3],0,row,3,sheet,samples);
                                NativeImage blockSide=pose(mc,zombie,folder,arm,COLUMNS[4],oblique,row,4,sheet,samples)) {
                            int changed=difference(carry,block),sideChanged=difference(side,blockSide);
                            report.addProperty(arm+"_front_guard_changed_pixels",changed);
                            report.addProperty(arm+"_oblique_guard_changed_pixels",sideChanged);
                            require(changed>200&&sideChanged>200,"Carrying and actively guarding must visibly differ from both views: "+arm);
                            for(int frame=0;frame<3;frame++)zombie.walkAnimation.update(1,1);
                            try(NativeImage moving=pose(mc,zombie,folder,arm,COLUMNS[5],oblique,row,5,sheet,samples)) {
                                int movingChanged=difference(blockSide,moving);
                                report.addProperty(arm+"_walking_changed_pixels",movingChanged);
                                require(zombie.walkAnimation.speed()>.9F&&movingChanged>100,"The native walking state must visibly animate the guarding zombie: "+arm);
                            }
                        }
                        walk.neutral(zombie.walkAnimation);stage(zombie,(byte)0,0);
                        zombie.attackAnim=zombie.oAttackAnim=.5F;
                        try(NativeImage melee=pose(mc,zombie,folder,arm,COLUMNS[6],oblique,row,6,sheet,samples)) {
                            int changed=difference(side,melee);report.addProperty(arm+"_melee_changed_pixels",changed);
                            require(changed>100,"A lowered door must still allow a visible native melee swing: "+arm);
                        }
                    }
                    zombie.attackAnim=zombie.oAttackAnim=0;stage(zombie,(byte)2,1);
                    zombie.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                    try(NativeImage empty=render(mc,zombie,0);NativeImage noDoor=withoutDoorLayer(mc,zombie,layerField,0)) {
                        int changed=difference(empty,noDoor);report.addProperty(arm+"_empty_hand_door_layer_changed_pixels",changed);
                        require(changed==0,"An empty main hand must never render the door, even if a stale guard state arrives: "+arm);
                        Path png=folder.resolve("zombie_door-"+arm+"-empty.png");empty.writeToFile(png);
                        report.addProperty(arm+"_empty_hand_screenshot",png.toString());
                    }
                    stage(zombie,(byte)0,0);
                    try(NativeImage emptyAfter=render(mc,zombie,0)) {
                        int changed=difference(emptyBefore,emptyAfter);report.addProperty(arm+"_shared_model_reset_changed_pixels",changed);
                        require(changed==0,"Guarding and melee must restore the shared model's empty-hand pose exactly: "+arm);
                    }
                }
            }
            Path png=folder.resolve("zombie_door-poses.png");sheet.writeToFile(png);report.addProperty("contact_sheet",png.toString());
            report.addProperty("status","passed");return report;
        } finally {
            zombie.setItemSlot(EquipmentSlot.MAINHAND,priorItem);zombie.setLeftHanded(priorLeft);
            zombie.attackAnim=priorAttack;zombie.oAttackAnim=priorOldAttack;walk.restore(zombie.walkAnimation);
            zombie.getEntityData().set(guardAccessor(),priorState);
            writeFloat(zombie,"doorGuardProgress",priorProgress);writeFloat(zombie,"doorGuardProgressOld",priorOldProgress);
            // Leave this shared model ready for the next independent entity sample.
            try(NativeImage ignored=render(mc,zombie,.35F)) {}
        }
    }

    private static NativeImage pose(Minecraft mc,ExtremeMobs.ZombieVariant zombie,Path folder,String arm,String pose,
            float view,int row,int column,NativeImage sheet,JsonArray samples)throws Exception {
        NativeImage image=render(mc,zombie,view);boolean complete=false;
        try {
            Path png=folder.resolve("zombie_door-"+arm+"-"+pose+".png");image.writeToFile(png);
            JsonObject sample=new JsonObject();samples.add(sample);sample.addProperty("main_arm",arm);sample.addProperty("pose",pose);
            sample.addProperty("view_yaw_radians",view);sample.addProperty("raised",zombie.isDoorRaised());sample.addProperty("blocking",zombie.isDoorBlocking());
            sample.addProperty("guard_progress",zombie.getDoorGuardProgress(1));sample.addProperty("walk_speed",zombie.walkAnimation.speed());
            sample.addProperty("attack_progress",zombie.attackAnim);sample.addProperty("screenshot",png.toString());
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)sheet.setPixelRGBA(column*SIZE+x,row*SIZE+y,image.getPixelRGBA(x,y));
            complete=true;return image;
        } finally {if(!complete)image.close();}
    }

    private static NativeImage render(Minecraft mc,ExtremeMobs.ZombieVariant zombie,float view) {
        return MobRenderRegressionAudit.capture(mc,zombie,false,view);
    }

    private static JsonObject verifyTrackingSync(ExtremeMobs.ZombieVariant zombie)throws Exception {
        JsonObject facts=new JsonObject();zombie.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_DOOR));
        EntityDataAccessor<Byte> state=guardAccessor();
        zombie.getEntityData().set(state,(byte)0);
        writeFloat(zombie,"doorGuardProgress",0);writeFloat(zombie,"doorGuardProgressOld",0);
        zombie.getEntityData().set(state,(byte)2);
        facts.addProperty("already_blocking_first_frame_start",zombie.getDoorGuardProgress(0));
        facts.addProperty("already_blocking_first_frame_end",zombie.getDoorGuardProgress(1));
        require(zombie.isDoorBlocking()&&zombie.getDoorGuardProgress(0)==1&&zombie.getDoorGuardProgress(1)==1,
                "The first tracking update for an already blocking zombie must display the fully raised door immediately");

        zombie.getEntityData().set(state,(byte)0);
        writeFloat(zombie,"doorGuardProgress",0);writeFloat(zombie,"doorGuardProgressOld",0);
        zombie.getEntityData().set(state,(byte)1);
        facts.addProperty("raising_first_frame_start",zombie.getDoorGuardProgress(0));
        facts.addProperty("raising_first_frame_end",zombie.getDoorGuardProgress(1));
        require(zombie.isDoorRaised()&&!zombie.isDoorBlocking()&&zombie.getDoorGuardProgress(0)==0&&zombie.getDoorGuardProgress(1)==0,
                "A normal synchronized raise must begin at zero and retain its interpolation");

        writeFloat(zombie,"doorGuardProgressOld",.2F);writeFloat(zombie,"doorGuardProgress",.4F);
        zombie.getEntityData().set(state,(byte)2);
        facts.addProperty("ongoing_raise_start_after_block_update",zombie.getDoorGuardProgress(0));
        facts.addProperty("ongoing_raise_end_after_block_update",zombie.getDoorGuardProgress(1));
        require(zombie.getDoorGuardProgress(0)==.2F&&zombie.getDoorGuardProgress(1)==.4F,
                "A block update during an existing raise must preserve its smooth interpolation");
        stage(zombie,(byte)0,0);return facts;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static NativeImage withoutDoorLayer(Minecraft mc,ExtremeMobs.ZombieVariant zombie,Field layerField,float view)throws Exception {
        List layers=(List)layerField.get(mc.getEntityRenderDispatcher().getRenderer(zombie));List saved=new ArrayList(layers);
        layers.removeIf(DoorShieldLayer.class::isInstance);
        try{return render(mc,zombie,view);}finally{layers.clear();layers.addAll(saved);}
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Byte> guardAccessor()throws Exception {
        Field field=ExtremeMobs.ZombieVariant.class.getDeclaredField("DATA_DOOR_GUARD");field.setAccessible(true);
        return (EntityDataAccessor<Byte>)field.get(null);
    }

    private static void stage(ExtremeMobs.ZombieVariant zombie,byte state,float progress)throws Exception {
        zombie.getEntityData().set(guardAccessor(),state);
        writeFloat(zombie,"doorGuardProgress",progress);writeFloat(zombie,"doorGuardProgressOld",progress);
        require(zombie.isDoorBlocking()==(state==2),"The native synchronized guard flag must match the captured stage");
        require(Math.abs(zombie.getDoorGuardProgress(1)-progress)<.0001F,"The native renderer must receive the captured guard interpolation");
    }

    private static float readFloat(Object owner,String name)throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.getFloat(owner);
    }
    private static void writeFloat(Object owner,String name,float value)throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.setFloat(owner,value);
    }
    private static int difference(NativeImage first,NativeImage second) {
        int changed=0;
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++) {
            int a=first.getPixelRGBA(x,y),b=second.getPixelRGBA(x,y);
            if(Math.abs((a&255)-(b&255))+Math.abs((a>>>8&255)-(b>>>8&255))+Math.abs((a>>>16&255)-(b>>>16&255))>12)changed++;
        }
        return changed;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}

    private record SavedWalk(float speedOld,float speed,float position) {
        SavedWalk(WalkAnimationState walk)throws Exception{this(readFloat(walk,"speedOld"),readFloat(walk,"speed"),readFloat(walk,"position"));}
        void neutral(WalkAnimationState walk)throws Exception{writeFloat(walk,"speedOld",0);writeFloat(walk,"speed",0);writeFloat(walk,"position",0);}
        void restore(WalkAnimationState walk)throws Exception{writeFloat(walk,"speedOld",speedOld);writeFloat(walk,"speed",speed);writeFloat(walk,"position",position);}
    }
}
