package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real survival-player hits expose zero damage rejected before NeoForge's incoming event. @author NingMeng123 */
@GameTestHolder("modern_ite_golem")
@PrefixGameTestTemplate(false)
public final class IronGolemMeleeGameTests {
    private static final String PROFILE = "modern_ite:source_profile";
    private static final String BOOST = "modern_ite:golem_boost";

    private IronGolemMeleeGameTests() {}

    @GameTest(template = "gear_empty")
    public static void freshGolemDamagesSurvivalPlayerAndKeepsNativeSwingAndLift(GameTestHelper h) {
        try (Site site = new Site(h)) {
            IronGolem golem = site.golem(); site.join(golem);
            site.hit(golem, site.player, 25F);
            h.assertTrue(golem.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 25D,
                    "The actual ordinary golem join installs a positive native melee input");
            h.assertTrue(golem.getAttackAnimationTick() == 10, "Native damaging melee starts the ten-tick golem attack animation");
            h.assertTrue(site.player.getDeltaMovement().y > .39D,
                    "The completed native melee hit retains the golem's upward impulse");
            for (int tick = 1; tick <= 10; tick++) {
                golem.aiStep();
                h.assertTrue(golem.getAttackAnimationTick() == 10 - tick,
                        "The native attack animation counts down exactly once per actual AI step: " + tick);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void serializedOldZeroDamageProfileRepairsOnRealJoin(GameTestHelper h) {
        try (Site site = new Site(h)) {
            IronGolem legacy = site.golem();
            legacy.getPersistentData().putBoolean(PROFILE, true);
            legacy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0D);
            CompoundTag saved = new CompoundTag(); legacy.saveWithoutId(saved); saved.remove("UUID");
            IronGolem loaded = site.golem(); loaded.load(saved); site.place(loaded);
            h.assertTrue(loaded.getPersistentData().getBoolean(PROFILE)
                            && loaded.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 0D,
                    "The real serialized reload reproduces an already initialized pre-fix golem");
            site.join(loaded);
            site.hit(loaded, site.player, 25F);
            h.assertTrue(loaded.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 25D,
                    "The real entity join repairs old zero damage despite its initialized profile");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void alreadyLoadedZeroDamageProfileRepairsThroughProductionTick(GameTestHelper h) {
        try (Site site = new Site(h)) {
            IronGolem golem = site.golem(); site.join(golem);
            golem.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0D);
            h.assertTrue(golem.getPersistentData().getBoolean(PROFILE), "The in-world fixture already owns the saved source profile");
            golem.tickCount = 10;
            ExtremeNativeMobs.tick(new EntityTickEvent.Post(golem));
            site.hit(golem, site.player, 25F);
            h.assertTrue(golem.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 25D,
                    "The production periodic update repairs a zero-damage golem without unloading it");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void existingBoostedGolemTracksEnabledAndDisabledFixedDamage(GameTestHelper h) {
        try (BoostSetting setting = new BoostSetting(); Site site = new Site(h)) {
            setting.set(true);
            IronGolem golem = site.golem(); site.join(golem);
            golem.getPersistentData().putBoolean(BOOST, true);
            golem.tickCount = 10; ExtremeNativeMobs.tick(new EntityTickEvent.Post(golem));
            site.hit(golem, site.player, 28F);
            setting.set(false);
            golem.tickCount = 20; ExtremeNativeMobs.tick(new EntityTickEvent.Post(golem));
            site.hit(golem, site.player, 25F);
            setting.set(true);
            golem.tickCount = 30; ExtremeNativeMobs.tick(new EntityTickEvent.Post(golem));
            site.hit(golem, site.player, 28F);
            h.assertTrue(golem.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 25D,
                    "Boost toggling changes the fixed incoming damage without rewriting native melee input");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void savedNonzeroCustomAttackSurvivesJoinAndPeriodicRepair(GameTestHelper h) {
        try (Site site = new Site(h)) {
            IronGolem golem = site.golem();
            golem.getPersistentData().putBoolean(PROFILE, true);
            golem.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(37.5D);
            site.join(golem);
            h.assertTrue(golem.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 37.5D,
                    "An existing positive custom attack remains intact on the real entity join");
            golem.tickCount = 10; ExtremeNativeMobs.tick(new EntityTickEvent.Post(golem));
            h.assertTrue(golem.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 37.5D,
                    "The zero-input migration does not overwrite a positive custom attack during ticks");
            site.hit(golem, site.player, 25F);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void zeroInputRepairNeverRewritesDarkIronGolemAttributes(GameTestHelper h) {
        try (Site site = new Site(h)) {
            Mob dark = ExtremeMobs.type(ExtremeKind.DARK_IRON_GOLEM).create(h.getLevel());
            h.assertTrue(dark != null, "The registered dark iron golem creates"); site.mobs.add(dark); site.place(dark);
            MobAbilities.initialize(dark, (ExtremeMob)dark);
            double attack = dark.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            h.assertTrue(attack > 25D, "The dark iron golem starts with its independent special attack profile");
            dark.tickCount = 10; ExtremeNativeMobs.tick(new EntityTickEvent.Post(dark));
            h.assertTrue(dark.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == attack,
                    "Ordinary golem synchronization retains the dark golem's actual special attack");
            dark.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0D);
            dark.tickCount = 20; ExtremeNativeMobs.tick(new EntityTickEvent.Post(dark));
            h.assertTrue(dark.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 0D,
                    "Even zero input on a different golem entity type is outside the ordinary-golem migration");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void ordinaryGolemRetainsFixedDamageAgainstAnEnemyTarget(GameTestHelper h) {
        try (Site site = new Site(h)) {
            IronGolem golem = site.golem(); site.join(golem);
            Mob zombie = EntityType.ZOMBIE.create(h.getLevel());
            h.assertTrue(zombie != null, "The native hostile reference creates"); site.mobs.add(zombie); site.place(zombie);
            site.join(zombie); stripDefenses(zombie);
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D); zombie.setHealth(1000F);
            zombie.setPos(golem.position().add(1D, 0D, 0D));
            site.hit(golem, zombie, 25F);
        }
        h.succeed();
    }

    private static void stripDefenses(LivingEntity target) {
        if (target instanceof ServerPlayer player) player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) if (slot != EquipmentSlot.BODY) target.setItemSlot(slot, ItemStack.EMPTY);
        target.removeAllEffects(); target.stopUsingItem(); target.setAbsorptionAmount(0F); target.setInvulnerable(false);
        for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE)) {
            var value = target.getAttribute(attribute);
            if (value != null) { value.removeModifiers(); value.setBaseValue(0D); }
        }
        target.invulnerableTime = 0; target.hurtTime = 0; target.setOnGround(false); target.setDeltaMovement(Vec3.ZERO);
    }

    private static final class Site implements AutoCloseable {
        final GameTestHelper helper;
        final Vec3 origin;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final List<Mob> mobs = new ArrayList<>();

        Site(GameTestHelper helper) {
            this.helper = helper; origin = helper.absolutePos(new BlockPos(1, 160, 1)).getCenter();
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "golem-melee"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener listener) {}
            };
            try {
                player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setPos(origin.add(1D, 0D, 0D));
                helper.getLevel().addNewPlayer(player);
                // ServerPlayer's separate sixty-tick login immunity otherwise rejects every real melee hit.
                for (int tick = 0; tick < 60; tick++) player.tick();
                stripDefenses(player);
                player.getAttribute(Attributes.MAX_HEALTH).removeModifiers();
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); player.setHealth(200F);
                helper.assertTrue(!player.isCreative() && !player.isSpectator() && !player.getAbilities().invulnerable,
                        "The registered real server-player fixture is vulnerable survival");
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        IronGolem golem() {
            IronGolem result = EntityType.IRON_GOLEM.create(helper.getLevel());
            helper.assertTrue(result != null, "The native ordinary iron golem creates"); mobs.add(result); place(result);
            return result;
        }

        void place(Mob mob) { mob.setPos(origin); mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired(); }

        void join(Mob mob) {
            helper.assertTrue(helper.getLevel().addFreshEntity(mob), "The actual entity manager accepts the fixture and runs production joins");
            mob.setNoAi(true); mob.setNoGravity(true);
        }

        void hit(IronGolem attacker, LivingEntity target, float expected) {
            stripDefenses(target);
            if (target == player) player.setHealth(200F);
            target.setPos(attacker.position().add(1D, 0D, 0D));
            helper.assertTrue(attacker.getMainHandItem().isEmpty() && !BetterMobCombat.supported(attacker)
                            && !BetterMobCombat.attacking(attacker),
                    "Empty-handed ordinary golems stay outside the optional Better Combat weapon scheduler");
            float before = target.getHealth();
            List<Float> incoming = new ArrayList<>();
            Consumer<LivingIncomingDamageEvent> observer = event -> {
                if (event.getEntity() == target && event.getSource().getEntity() == attacker
                        && event.getSource().getDirectEntity() == attacker) incoming.add(event.getAmount());
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, observer);
            boolean affected;
            try { affected = attacker.doHurtTarget(target); }
            finally { NeoForge.EVENT_BUS.unregister(observer); }
            helper.assertTrue(affected, "Native IronGolem.doHurtTarget must really damage its survival target rather than only broadcast a swing; nativeInput="
                    + attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) + " incomingEvents=" + incoming);
            helper.assertTrue(incoming.size() == 1 && Math.abs(incoming.getFirst() - expected) < .0001F,
                    "The actual NeoForge incoming event sees fixed " + expected + " damage exactly once; observed=" + incoming);
            helper.assertTrue(Math.abs(before - target.getHealth() - expected) < .0001F,
                    "A vulnerable unarmored target loses the fixed damage through the completed native pipeline; expected="
                            + expected + " actual=" + (before - target.getHealth()));
            helper.assertTrue(!BetterMobCombat.attacking(attacker), "Native golem damage does not create a delayed weapon attack state");
        }

        @Override public void close() {
            try { mobs.forEach(Mob::discard); player.discard(); }
            finally { channel.finishAndReleaseAll(); }
        }
    }

    private static final class BoostSetting implements AutoCloseable {
        private final Object before = IteConfig.setting("can_boost_iron_golem").get();
        void set(boolean enabled) { IteConfig.setting("can_boost_iron_golem").set(enabled); IteConfig.refresh(); }
        @Override public void close() { IteConfig.setting("can_boost_iron_golem").set(before); IteConfig.refresh(); }
    }
}
