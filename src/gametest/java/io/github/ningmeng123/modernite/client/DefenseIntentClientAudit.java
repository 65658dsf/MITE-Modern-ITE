package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.modernite.client.entity.DoorShieldLayer;
import io.github.ningmeng123.modernite.entity.DefenseGuardFocus;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Native client tick and GPU evidence for defensive turns, isolated from played worlds. @author NingMeng123 */
final class DefenseIntentClientAudit {
    private static final int SIZE=320;
    private static final String[] POSES={"initial","side-tick-1","side-tick-3","side-aligned",
            "reverse-tick-1","reverse-aligned","wrap-aligned","moving-tick-1","moving-tick-8"};
    private DefenseIntentClientAudit() {}

    static JsonObject capture(Minecraft mc,Path folder)throws Exception {
        Files.createDirectories(folder);
        JsonObject report=new JsonObject();JsonArray cases=new JsonArray();report.add("cases",cases);
        report.addProperty("author","NingMeng123");
        report.addProperty("state_source","Ordinary zombie calls native startUsingItem(OFF_HAND) on the isolated server; its actual entity metadata initializes the detached client zombie. Door uses its native synchronized guard metadata.");
        report.addProperty("tick_scope","Real ClientLevel.tickNonPassenger including native entity tick events, Mob.tick and tickHeadTurn; native LookControl.tick is explicitly supplied the server's 25 degree look request because clients do not run serverAiStep.");
        report.addProperty("moving_scope","Eight ticks retain nonzero horizontal velocity and use native lerpTo to apply remote entity movement, with network movement yaw deliberately opposed to the defensive head/body direction.");
        report.addProperty("head_limit_degrees_per_tick",25);report.addProperty("body_limit_degrees_per_tick",15);
        JsonArray columns=new JsonArray();for(String pose:POSES)columns.add(pose);report.add("contact_sheet_columns",columns);
        report.addProperty("contact_sheet_rows","ordinary shield right main arm; ordinary shield left main arm; door right main arm; door left main arm");
        ShieldMetadata shieldMetadata=nativeShieldMetadata(mc);
        require(shieldMetadata.nativeOffhandUse(),"The isolated server must really start native offhand shield use");
        report.addProperty("native_server_offhand_use",shieldMetadata.nativeOffhandUse());
        try(NativeImage sheet=new NativeImage(SIZE*POSES.length,SIZE*4,true)) {
            for(int row=0;row<4;row++) {
                boolean door=row>=2,left=(row&1)!=0;
                Zombie mob=door?(Zombie)ExtremeMobs.type(ExtremeKind.ZOMBIE_DOOR).create(mc.level):EntityType.ZOMBIE.create(mc.level);
                require(mob!=null,"The real entity factory must create the defensive client mob");
                try {
                    prepare(mc,mob,left,door,shieldMetadata);
                    JsonObject facts=new JsonObject();cases.add(facts);
                    facts.addProperty("entity",door?"modern_ite:zombie_door":"minecraft:zombie");
                    facts.addProperty("main_arm",left?"left":"right");
                    facts.addProperty("using_real_offhand_shield",!door&&MobShields.defending(mob));
                    facts.addProperty("native_guard_state",DefenseGuardFocus.guarding(mob));
                    require(DefenseGuardFocus.guarding(mob),"Client guard recognition must use the actual synchronized entity state");
                    JsonArray trace=new JsonArray(),poses=new JsonArray();facts.add("ticks",trace);facts.add("native_gpu_poses",poses);
                    capturePose(mc,mob,folder,row,0,sheet,poses,door,left);
                    float maximumHead=0,maximumBody=0;
                    int tick=0;
                    for(int phase=0;phase<3;phase++) {
                        float wanted=phase==0?90F:phase==1?-90F:170F;
                        for(int step=1;step<=16;step++) {
                            float previousHead=mob.yHeadRot,previousBody=mob.yBodyRot;
                            double radians=Math.toRadians(wanted);
                            mob.getLookControl().setLookAt(mob.getX()-Math.sin(radians)*8D,mob.getEyeY(),mob.getZ()+Math.cos(radians)*8D,25F,25F);
                            mob.getLookControl().tick();
                            mob.setDeltaMovement(Vec3.ZERO);
                            mc.level.tickNonPassenger(mob);
                            require(DefenseGuardFocus.guarding(mob),"A real client tick must retain its synchronized shield or door guard");
                            float head=Math.abs(Mth.wrapDegrees(mob.yHeadRot-previousHead));
                            float body=Math.abs(Mth.wrapDegrees(mob.yBodyRot-previousBody));
                            float expected=Mth.approachDegrees(previousBody,mob.yHeadRot,15F);
                            require(head<=25.001F,"Actual head turn exceeded 25 degrees: "+head);
                            require(body<=15.001F,"Actual client body turn exceeded 15 degrees: "+body);
                            require(Math.abs(Mth.wrapDegrees(mob.yBodyRot-expected))<.001F,"Native tickHeadTurn must approach the observed head from the previous body yaw");
                            maximumHead=Math.max(maximumHead,head);maximumBody=Math.max(maximumBody,body);
                            JsonObject sample=new JsonObject();trace.add(sample);sample.addProperty("tick",++tick);sample.addProperty("requested_yaw",wanted);
                            sample.addProperty("head_yaw",mob.yHeadRot);sample.addProperty("body_yaw",mob.yBodyRot);
                            sample.addProperty("head_delta",head);sample.addProperty("body_delta",body);
                            int column=phase==0?(step==1?1:step==3?2:step==16?3:-1):phase==1?(step==1?4:step==16?5:-1):step==16?6:-1;
                            if(column>=0)capturePose(mc,mob,folder,row,column,sheet,poses,door,left);
                        }
                        require(Math.abs(Mth.wrapDegrees(mob.yHeadRot-wanted))<.001F&&Math.abs(Mth.wrapDegrees(mob.yBodyRot-wanted))<.001F,
                                "Repeated actual client ticks must finish the side turn, reversal and angle-wrap cases");
                    }
                    double moved=0D;
                    for(int step=1;step<=8;step++) {
                        float previousHead=mob.yHeadRot,previousBody=mob.yBodyRot;
                        Vec3 previousPosition=mob.position();
                        mob.getLookControl().setLookAt(mob.getX(),mob.getEyeY(),mob.getZ()+8D,25F,25F);
                        mob.getLookControl().tick();
                        float lookHead=mob.yHeadRot;
                        // Remote mobs move through native position interpolation, not local travel integration.
                        // A conflicting movement yaw forces BodyRotationControl's native moving branch.
                        mob.setDeltaMovement(.08D,0D,.04D);
                        mob.lerpTo(mob.getX()+.08D,mob.getY(),mob.getZ()+.04D,0F,0F,1);
                        mc.level.tickNonPassenger(mob);
                        double distance=mob.position().distanceTo(previousPosition);moved+=distance;
                        float head=Math.abs(Mth.wrapDegrees(mob.yHeadRot-previousHead));
                        float body=Math.abs(Mth.wrapDegrees(mob.yBodyRot-previousBody));
                        float expected=Mth.approachDegrees(previousBody,mob.yHeadRot,15F);
                        require(distance>.01D&&mob.getDeltaMovement().horizontalDistanceSqr()>.001D,
                                "The moving control must really change client position and retain horizontal velocity");
                        require(DefenseGuardFocus.guarding(mob),"Native movement tick must retain the synchronized guard");
                        require(head<=25.001F&&body<=15.001F,"Native moving-body control must preserve defensive head/body turn limits");
                        require(Math.abs(Mth.wrapDegrees(mob.yHeadRot-lookHead))<.001F,
                                "Native moving-body clamping must not overwrite the bounded defensive head yaw");
                        require(Math.abs(Mth.wrapDegrees(mob.yBodyRot-expected))<.001F,
                                "Moving guard body must approach from its previous body yaw rather than snap to movement yaw");
                        maximumHead=Math.max(maximumHead,head);maximumBody=Math.max(maximumBody,body);
                        JsonObject sample=new JsonObject();trace.add(sample);sample.addProperty("tick",++tick);sample.addProperty("moving",true);
                        sample.addProperty("network_movement_yaw",0);sample.addProperty("requested_yaw",0);
                        sample.addProperty("actual_horizontal_distance",distance);sample.addProperty("head_yaw",mob.yHeadRot);sample.addProperty("body_yaw",mob.yBodyRot);
                        sample.addProperty("head_delta",head);sample.addProperty("body_delta",body);
                        if(step==1||step==8)capturePose(mc,mob,folder,row,step==1?7:8,sheet,poses,door,left);
                    }
                    facts.addProperty("moving_client_ticks",8);facts.addProperty("actual_moving_distance",moved);
                    facts.addProperty("client_ticks",tick);facts.addProperty("maximum_head_turn",maximumHead);facts.addProperty("maximum_body_turn",maximumBody);
                    require(maximumHead>=24.99F&&maximumBody>=14.99F,"Side-turn test must exercise both limits rather than a stationary control");
                    facts.addProperty("status","passed");
                } finally {mob.discard();}
            }
            Path png=folder.resolve("defense-intent-poses.png");sheet.writeToFile(png);report.addProperty("contact_sheet",png.toString());
        }
        report.addProperty("status","passed");return report;
    }

    private record ShieldMetadata(List<SynchedEntityData.DataValue<?>> values,boolean nativeOffhandUse) {}

    private static ShieldMetadata nativeShieldMetadata(Minecraft mc)throws Exception {
        return mc.getSingleplayerServer().submit(()->{
            Zombie source=EntityType.ZOMBIE.create(mc.getSingleplayerServer().overworld());
            require(source!=null,"The isolated server must create the ordinary shield mob");
            try {
                source.setNoAi(true);source.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
                source.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(MiteShields.get("iron")));
                source.startUsingItem(InteractionHand.OFF_HAND);
                var metadata=source.getEntityData().getNonDefaultValues();
                require(metadata!=null,"Actual offhand use must produce synchronized entity metadata");
                return new ShieldMetadata(List.copyOf(metadata),MobShields.defending(source));
            } finally {source.discard();}
        }).get(15,TimeUnit.SECONDS);
    }

    private static void prepare(Minecraft mc,Zombie mob,boolean left,boolean door,ShieldMetadata metadata)throws Exception {
        mob.setPos(mc.player.position().add(0,12,0));mob.setNoGravity(true);mob.setNoAi(true);mob.setLeftHanded(left);
        mob.setYRot(0);mob.setXRot(0);mob.yBodyRot=mob.yBodyRotO=0;mob.yHeadRot=mob.yHeadRotO=0;
        if(door) {
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_DOOR));
            Field field=ExtremeMobs.ZombieVariant.class.getDeclaredField("DATA_DOOR_GUARD");field.setAccessible(true);
            @SuppressWarnings("unchecked") EntityDataAccessor<Byte> accessor=(EntityDataAccessor<Byte>)field.get(null);
            mob.getEntityData().set(accessor,(byte)2);
            require(((ExtremeMobs.ZombieVariant)mob).isDoorBlocking(),"Native door guard metadata must be blocking");
        } else {
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            mob.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(MiteShields.get("iron")));
            mob.startUsingItem(InteractionHand.OFF_HAND);
            mob.getEntityData().assignValues(metadata.values());
            mob.setLeftHanded(left);
            require(MobShields.defending(mob),"The server's native metadata must put the client into real offhand shield use");
        }
    }

    private static void capturePose(Minecraft mc,Zombie mob,Path folder,int row,int column,NativeImage sheet,
            JsonArray poses,boolean door,boolean left)throws Exception {
        try(NativeImage actual=MobRenderRegressionAudit.capture(mc,mob,false,.4F)) {
            var renderer=mc.getEntityRenderDispatcher().getRenderer(mob);
            require(renderer instanceof LivingEntityRenderer<?,?>,"Defense audit requires a real native living renderer");
            var model=((LivingEntityRenderer<?,?>)renderer).getModel();
            require(model instanceof HumanoidModel<?>,"Defensive mob must use the actual humanoid model");
            HumanoidModel<?> humanoid=(HumanoidModel<?>)model;
            float head=Mth.wrapDegrees(mob.yHeadRot-mob.yBodyRot)*Mth.DEG_TO_RAD;
            require(Math.abs(humanoid.head.yRot-head)<.001F,"Native rendered head direction must equal the actual head/body yaw difference");
            var arm=door?(left?humanoid.leftArm:humanoid.rightArm):(left?humanoid.rightArm:humanoid.leftArm);
            float expected=door?(left?.45F:-.45F):(left?-.5235988F:.5235988F)+Mth.clamp(head,-.5235988F,.5235988F);
            require(Math.abs(arm.yRot-expected)<.001F,"Displayed shield/door brace must use its actual body/head pose rule");
            JsonObject sample=new JsonObject();poses.add(sample);sample.addProperty("pose",POSES[column]);
            sample.addProperty("body_yaw",mob.yBodyRot);sample.addProperty("head_yaw",mob.yHeadRot);
            sample.addProperty("rendered_relative_head_radians",humanoid.head.yRot);sample.addProperty("shield_arm_yaw_radians",arm.yRot);
            sample.addProperty("expected_shield_arm_yaw_radians",expected);
            if(door)sample.addProperty("door_panel_yaw_from_body_radians",left?-.03F:.03F);
            int changed;
            try(NativeImage noDefense=withoutDefense(mc,mob,door)) {changed=difference(actual,noDefense);}
            require(changed>100,"Native GPU output must contain visible defensive equipment pixels: "+changed);
            sample.addProperty("defensive_equipment_gpu_pixels",changed);
            Path png=folder.resolve((door?"door":"shield")+"-"+(left?"left":"right")+"-"+POSES[column]+".png");
            actual.writeToFile(png);sample.addProperty("screenshot",png.toString());
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)sheet.setPixelRGBA(column*SIZE+x,row*SIZE+y,actual.getPixelRGBA(x,y));
        }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static NativeImage withoutDefense(Minecraft mc,Zombie mob,boolean door)throws Exception {
        if(!door) {
            ItemStack shield=mob.getOffhandItem();mob.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
            try{return MobRenderRegressionAudit.capture(mc,mob,false,.4F);}
            finally{mob.setItemSlot(EquipmentSlot.OFFHAND,shield);}
        }
        Field field=LivingEntityRenderer.class.getDeclaredField("layers");field.setAccessible(true);
        List layers=(List)field.get(mc.getEntityRenderDispatcher().getRenderer(mob));List saved=new ArrayList(layers);
        layers.removeIf(DoorShieldLayer.class::isInstance);
        try{return MobRenderRegressionAudit.capture(mc,mob,false,.4F);}
        finally{layers.clear();layers.addAll(saved);}
    }

    private static int difference(NativeImage a,NativeImage b) {
        int changed=0;
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++) {
            int first=a.getPixelRGBA(x,y),second=b.getPixelRGBA(x,y);
            if(Math.abs((first&255)-(second&255))+Math.abs((first>>>8&255)-(second>>>8&255))+Math.abs((first>>>16&255)-(second>>>16&255))>12)changed++;
        }
        return changed;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
