package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.registry.MiteShields;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Addon shield wear is verified through actual blocks and native event budgets. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ShieldWearGameTests {
    private ShieldWearGameTests() {}

    private static final class Session implements AutoCloseable {
        private final LivingEntity defender;
        private final Player attacker;
        private final ItemStack shield;
        private final InteractionHand hand;
        private final String fixture;

        private Session(GameTestHelper h, boolean player, InteractionHand hand, String material) {
            this.hand = hand;
            fixture = " (player=" + player + ", hand=" + hand + ", material=" + material + ")";
            if (player) defender = h.makeMockPlayer(GameType.SURVIVAL);
            else {
                var zombie = EntityType.ZOMBIE.create(h.getLevel());
                zombie.setNoAi(true);
                defender = zombie;
            }
            defender.setNoGravity(true);
            defender.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 10, 1))));
            defender.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            defender.setHealth(200F);
            defender.setYRot(0F);
            defender.setYHeadRot(0F);
            defender.yBodyRot = 0F;
            shield = new ItemStack(MiteShields.get(material));
            defender.setItemInHand(hand, shield);
            defender.startUsingItem(hand);
            for (int tick = 0; tick < 6; tick++) defender.tick();
            h.assertTrue(defender.isBlocking(), "The equipped shield completes its native raise delay" + fixture);
            attacker = h.makeMockPlayer(GameType.SURVIVAL);
            attacker.setNoGravity(true);
            attacker.setPos(defender.position().add(0D, 0D, 2D));
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertTrue(defender.isDamageSourceBlocked(defender.damageSources().playerAttack(attacker)),
                    "The actual damage source is in the front blocking arc" + fixture);
        }

        private void hit(float damage) {
            defender.invulnerableTime = 0;
            defender.hurt(defender.damageSources().playerAttack(attacker), damage);
        }

        @Override public void close() {
            defender.discard();
            attacker.discard();
        }
    }

    @GameTest(template = "gear_empty")
    public static void everyShieldUsesTriplePlayerAndHalfMonsterWearInEitherHand(GameTestHelper h) {
        for (boolean player : List.of(false, true)) for (InteractionHand hand : InteractionHand.values())
            for (var definition : MiteShields.definitions()) {
                try (Session session = new Session(h, player, hand, definition.material())) {
                    float health = session.defender.getHealth();
                    session.hit(4F);
                    int expectedWear = player ? 15 : 2;
                    h.assertValueEqual(session.shield.getDamageValue(), expectedWear,
                            "Four damage spends triple player wear or half monster wear rounded down" + session.fixture);
                    h.assertValueEqual(session.defender.getHealth(), health,
                            "The successful block still prevents health damage" + session.fixture);
                    session.attacker.setPos(session.defender.position().add(0D, 0D, -2D));
                    session.hit(4F);
                    h.assertValueEqual(session.shield.getDamageValue(), expectedWear,
                            "A rear attack cannot spend additional shield durability" + session.fixture);
                    h.assertTrue(session.defender.getHealth() < health,
                            "The rear attack bypasses the raised shield" + session.fixture);
                }
            }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void smallHitsStayFreeAndTheEventBudgetControlsScaledWear(GameTestHelper h) {
        for (boolean player : List.of(false, true)) for (InteractionHand hand : InteractionHand.values())
            for (String material : List.of("iron", "copper")) {
                try (Session session = new Session(h, player, hand, material)) {
                    session.hit(2.99F);
                    h.assertValueEqual(session.shield.getDamageValue(), 0,
                            "The native threshold still exempts budgets below three" + session.fixture);
                    boolean[] seen = {false};
                    Consumer<LivingShieldBlockEvent> listener = event -> {
                        if (event.getEntity() != session.defender) return;
                        seen[0] = true;
                        event.setShieldDamage(7F);
                    };
                    NeoForge.EVENT_BUS.addListener(listener);
                    try {
                        session.hit(4F);
                        h.assertTrue(seen[0], "The real damage pipeline posts the shield event" + session.fixture);
                        h.assertValueEqual(session.shield.getDamageValue(), player ? 24 : 4,
                                "Seven authorized damage uses its own wear budget before scaling" + session.fixture);
                    } finally {
                        NeoForge.EVENT_BUS.unregister(listener);
                    }
                }
            }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void canceledAndZeroBudgetEventsDoNotConsumeShieldDurability(GameTestHelper h) {
        for (boolean player : List.of(false, true)) for (InteractionHand hand : InteractionHand.values())
            for (String material : List.of("iron", "copper")) for (boolean canceled : List.of(false, true)) {
                try (Session session = new Session(h, player, hand, material)) {
                    session.shield.setDamageValue(17);
                    float health = session.defender.getHealth();
                    boolean[] seen = {false};
                    Consumer<LivingShieldBlockEvent> listener = event -> {
                        if (event.getEntity() != session.defender) return;
                        seen[0] = true;
                        if (canceled) event.setCanceled(true); else event.setShieldDamage(0F);
                    };
                    NeoForge.EVENT_BUS.addListener(listener);
                    try {
                        session.hit(4F);
                        String fixture = session.fixture + " (canceled=" + canceled + ")";
                        h.assertTrue(seen[0], "The actual block reaches the cancellable event" + fixture);
                        h.assertValueEqual(session.shield.getDamageValue(), 17,
                                "Canceled blocks and zero wear budgets preserve existing wear" + fixture);
                        h.assertTrue(canceled ? session.defender.getHealth() < health : session.defender.getHealth() == health,
                                "Only canceling the block permits health damage" + fixture);
                    } finally {
                        NeoForge.EVENT_BUS.unregister(listener);
                    }
                }
            }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void finalDurabilityPointStillBreaksBothWearersShieldsAndEndsUse(GameTestHelper h) {
        for (boolean player : List.of(false, true)) for (InteractionHand hand : InteractionHand.values())
            for (String material : List.of("iron", "rusted_iron")) {
                try (Session session = new Session(h, player, hand, material)) {
                    session.shield.setDamageValue(session.shield.getMaxDamage() - 1);
                    session.hit(4F);
                    h.assertTrue(session.shield.isEmpty() && session.defender.getItemInHand(session.hand).isEmpty(),
                            "Even halved monster wear consumes the final durability point" + session.fixture);
                    h.assertFalse(session.defender.isUsingItem(),
                            "Native break handling immediately ends use in either hand" + session.fixture);
                }
            }
        h.succeed();
    }
}
