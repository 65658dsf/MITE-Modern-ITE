package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real damaging hits must reliably apply the shared fifteen-block launch. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ReliableLaunchGameTests {
    private static final ExtremeKind[] KINDS={ExtremeKind.ZOMBIE_FLY_AWAY,ExtremeKind.SKELETON_KNOCKBACK};
    private static final double LAUNCH_SPEED=1.6929821475097433;
    private ReliableLaunchGameTests(){}

    @GameTest(template="gear_empty")
    public static void everyDamagingHitLaunchesToTheSameFifteenBlockHeight(GameTestHelper h){
        for(var kind:KINDS)for(boolean grounded:new boolean[]{true,false})
            for(double initialY:new double[]{-8,-4,-.8,0,.6,3})for(double resistance:new double[]{0,.5,1}){
                Player target=target(h);target.setOnGround(grounded);target.setDeltaMovement(.2,initialY,-.1);
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance);
                Mob attacker=attacker(h,kind,target);
                float health=target.getHealth();
                h.assertTrue(attacker.doHurtTarget(target)&&target.getHealth()<health,"The real melee hit damages its target: "+kind);
                assertLaunch(h,target,kind+" grounded="+grounded+" initialY="+initialY+" resistance="+resistance);
                assertFifteenBlockAscent(h,target,"Every damaging hit: "+kind);
            }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void fullyAbsorbedHitsStillLaunchThePlayer(GameTestHelper h){
        for(var kind:KINDS)for(boolean grounded:new boolean[]{true,false}){
            Player target=target(h);target.setOnGround(grounded);target.setDeltaMovement(0,-4,0);
            target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100);target.setAbsorptionAmount(100);
            Mob attacker=attacker(h,kind,target);float health=target.getHealth();
            h.assertTrue(attacker.doHurtTarget(target),"A hit spent on absorption is an accepted melee hit");
            h.assertTrue(target.getHealth()==health&&target.getAbsorptionAmount()<100,"The attack spends absorption without reducing health");
            assertLaunch(h,target,"Fully absorbed hit: "+kind);
            assertFifteenBlockAscent(h,target,"Fully absorbed hit: "+kind);
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void rejectedAndCanceledHitsNeverAddTheLaunch(GameTestHelper h){
        for(var kind:KINDS){
            Player target=target(h);Mob attacker=attacker(h,kind,target);
            Vec3 initial=new Vec3(.12,-.8,.08);target.setDeltaMovement(initial);target.setInvulnerable(true);
            h.assertFalse(attacker.doHurtTarget(target),"Invulnerability rejects the melee hit");
            h.assertTrue(target.getDeltaMovement().equals(initial),"Invulnerability preserves the original motion");
            target.setInvulnerable(false);
            Consumer<LivingIncomingDamageEvent> cancel=event->{if(event.getEntity()==target)event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LivingIncomingDamageEvent.class,cancel);
            try{
                h.assertFalse(attacker.doHurtTarget(target),"A canceled damage event rejects the melee hit");
                h.assertTrue(target.getDeltaMovement().equals(initial),"Canceled damage adds no launch");
            }finally{NeoForge.EVENT_BUS.unregister(cancel);}
            h.assertTrue(target.hurt(target.damageSources().generic(),100),"A real high-damage reference hit establishes the native last-hurt threshold");
            target.invulnerableTime=20;target.setDeltaMovement(initial);float health=target.getHealth();
            h.assertFalse(attacker.doHurtTarget(target),"The weak mob hit is rejected by the established native damage cooldown");
            h.assertTrue(target.getHealth()==health&&target.getDeltaMovement().equals(initial),"Damage cooldown does not create another launch");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void aFullyBlockedShieldHitKeepsOnlyNativeMotion(GameTestHelper h){
        for(var kind:KINDS){
            Player reference=shieldTarget(h),target=shieldTarget(h);
            Mob attacker=attacker(h,kind,target);
            h.assertFalse(reference.hurt(reference.damageSources().mobAttack(attacker),2),"The shield completely blocks the native reference attack");
            Vec3 referenceMotion=reference.getDeltaMovement();float health=target.getHealth();
            h.assertFalse(attacker.doHurtTarget(target),"The shield completely blocks the registered monster's melee");
            h.assertTrue(target.getHealth()==health&&Math.abs(target.getDeltaMovement().y-referenceMotion.y)<.000001,
                "A fully blocked hit adds no special vertical motion beyond native shield behavior");
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void theServerPlayerReceivesMotionThatProducesAFifteenBlockAscent(GameTestHelper h){
        try(var peer=new Peer(h)){
            ServerPlayer target=peer.player;
            var tracker=new ServerEntity(h.getLevel(),target,1,true,packet->{});
            for(var kind:KINDS)for(double initialY:new double[]{-.0784,-4}){
                target.invulnerableTime=0;target.setOnGround(initialY>-.1);target.setDeltaMovement(0,initialY,0);
                target.hurtMarked=false;peer.packets.clear();
                Mob attacker=attacker(h,kind,target);
                h.assertTrue(attacker.doHurtTarget(target),"The actual connected survival player takes the mob's melee hit");
                assertLaunch(h,target,"Real ServerPlayer: "+kind);
                h.assertTrue(target.hurtMarked,"Every launch marks the real player for native synchronization");
                tracker.sendChanges();
                var motion=peer.packets.stream().filter(ClientboundSetEntityMotionPacket.class::isInstance)
                    .map(ClientboundSetEntityMotionPacket.class::cast).filter(packet->packet.getId()==target.getId())
                    .reduce((earlier,later)->later).orElseThrow(()->new AssertionError("The victim's own connection must receive a motion packet"));
                h.assertTrue(Math.abs(motion.getYa()-LAUNCH_SPEED)<1D/8000,
                    "ServerEntity sends the final upward velocity to the victim's own connection");
                Player decodedTarget=target(h);decodedTarget.setOnGround(initialY>-.1);
                decodedTarget.setDeltaMovement(motion.getXa(),motion.getYa(),motion.getZa());
                assertFifteenBlockAscent(h,decodedTarget,"Quantized native motion packet: "+kind);
                h.assertFalse(target.hurtMarked,"The real tracker consumes the synchronization mark");
            }
        }
        h.succeed();
    }

    private static Mob attacker(GameTestHelper h,ExtremeKind kind,Player target){
        Mob attacker=ExtremeMobs.type(kind).create(h.getLevel());
        h.assertTrue(attacker!=null,"The registered launch mob creates");
        MobAbilities.initialize(attacker,(ExtremeMob)attacker);attacker.setNoAi(true);
        attacker.moveTo(target.position().add(-1,0,0));attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
        for(var slot:EquipmentSlot.values())attacker.setItemSlot(slot,ItemStack.EMPTY);
        return attacker;
    }

    private static Player target(GameTestHelper h){
        Player target=h.makeMockPlayer(GameType.SURVIVAL);prepareTarget(h,target);return target;
    }

    private static void prepareTarget(GameTestHelper h,Player target){
        BlockPos anchor=h.absolutePos(BlockPos.ZERO);
        target.moveTo(anchor.getX()+1.5,Math.max(128D,anchor.getY()+64D),anchor.getZ()+1.5);
        target.getInventory().clearContent();target.removeAllEffects();target.setNoGravity(false);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        target.getAttribute(Attributes.GRAVITY).setBaseValue(.08);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        h.getLevel().getChunkAt(target.blockPosition());
        h.assertTrue(h.getLevel().noCollision(target,target.getBoundingBox()),"The launch target starts in open air");
    }

    private static Player shieldTarget(GameTestHelper h){
        Player target=target(h);target.setNoGravity(true);target.setYRot(90);target.setXRot(0);
        target.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));target.startUsingItem(InteractionHand.OFF_HAND);
        for(int tick=0;tick<6;tick++)target.tick();
        h.assertTrue(target.isBlocking(),"The vanilla shield has completed its blocking delay");
        target.setDeltaMovement(0,-.8,0);target.setOnGround(false);return target;
    }

    private static void assertLaunch(GameTestHelper h,Player target,String context){
        h.assertTrue(Math.abs(target.getDeltaMovement().y-LAUNCH_SPEED)<.000001,"Every accepted hit sets the shared positive launch velocity: "+context);
    }

    private static void assertFifteenBlockAscent(GameTestHelper h,Player target,String context){
        double start=target.getY(),apex=start;
        int ticks=0;
        while(target.getDeltaMovement().y>0&&ticks++<100){
            target.travel(Vec3.ZERO);apex=Math.max(apex,target.getY());
            h.assertFalse(target.verticalCollision,"Open air never truncates the test ascent: "+context);
        }
        h.assertTrue(ticks<100&&target.getDeltaMovement().y<=0,"Real Player.travel reaches a finite apex: "+context);
        h.assertTrue(Math.abs(apex-start-15)<.01,"Real Player.travel rises approximately fifteen blocks: "+context+" rise="+(apex-start));
    }

    private static final class Peer implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final List<Packet<?>> packets=new ArrayList<>();
        private Peer(GameTestHelper h){
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"NingMeng123"),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,player,cookie){
                @Override public void send(Packet<?> packet){capture(packet);}
                @Override public void send(Packet<?> packet,PacketSendListener listener){capture(packet);}
            };
            player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);
            player.moveTo(h.absolutePos(new BlockPos(1,160,1)).getCenter());h.getLevel().addNewPlayer(player);
            for(int tick=0;tick<60;tick++)player.tick();
            prepareTarget(h,player);packets.clear();
        }
        private void capture(Packet<?> packet){
            if(packet instanceof ClientboundBundlePacket bundle)bundle.subPackets().forEach(this::capture);else packets.add(packet);
        }
        @Override public void close(){player.discard();channel.finishAndReleaseAll();}
    }
}
