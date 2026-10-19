package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Mirror equipment and real source-goal attacks through the optional controller. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class MirrorCombatGameTests {
    private static final ResourceLocation RUNTIME_DAMAGE = ResourceLocation.fromNamespaceAndPath("modern_ite", "mirror_test_damage");
    private static final ResourceLocation RUNTIME_SPEED = ResourceLocation.fromNamespaceAndPath("modern_ite", "mirror_test_speed");

    private MirrorCombatGameTests() {}

    private record Fight(PathfinderMob mirror, Player player, Vec3 origin) implements AutoCloseable {
        SourceMeleeGoal goal() { return SourceMeleeGoal.first(mirror); }
        void holdPositions() {
            mirror.setPos(origin);
            player.setPos(origin.add(0D, 0D, .9D));
            mirror.setDeltaMovement(Vec3.ZERO);
            player.setDeltaMovement(Vec3.ZERO);
        }
        @Override public void close() {
            BetterMobCombat.cancel(mirror);
            mirror.discard();
            player.discard();
        }
    }

    private static Fight fight(GameTestHelper h) {
        var created = ExtremeMobs.type(ExtremeKind.MIRROR_ZOMBIE).create(h.getLevel());
        h.assertTrue(created instanceof PathfinderMob, "The registered mirror zombie creates a real pathfinding mob");
        var mirror = (PathfinderMob)created;
        MobAbilities.initialize(mirror, (ExtremeMob)mirror);
        // Run the actual goal explicitly so navigation, random goals and gravity cannot move the impact fixture.
        mirror.setNoAi(true);
        mirror.setNoGravity(true);
        mirror.tickCount = 100;
        mirror.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
        mirror.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        mirror.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2D);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setNoGravity(true);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        player.setHealth(1000F);
        var anchor = h.absolutePos(new BlockPos(2, 10, 2));
        var origin = new Vec3(anchor.getX() + .5D, Math.max(128D, anchor.getY() + 64D), anchor.getZ() + .5D);
        var fight = new Fight(mirror, player, origin);
        fight.holdPositions();
        mirror.setTarget(player);
        mirror.setYRot(0F);
        mirror.setYHeadRot(0F);
        mirror.yBodyRot = 0F;
        return fight;
    }

    private static void mirrorEquipment(GameTestHelper h, Fight fight) {
        var state = (ExtremeMob)fight.mirror();
        state.extremeMemory().clock = 19;
        MobAbilities.tick(fight.mirror(), state);
        h.assertTrue(ItemStack.isSameItemSameComponents(fight.mirror().getMainHandItem(), fight.player().getMainHandItem()),
                "The real mirror ability copies the player's current item and components");
        h.assertTrue(fight.mirror().getTarget() == fight.player() && fight.goal() != null,
                "Mirroring preserves the player target and source melee scheduling");
    }

    private static void equip(GameTestHelper h, Fight fight, ItemStack weapon) {
        fight.player().setItemSlot(EquipmentSlot.MAINHAND, weapon);
        mirrorEquipment(h, fight);
        refreshEquipment(h, fight);
    }

    private static void refreshEquipment(GameTestHelper h, Fight fight) {
        // setItemSlot alone does not exercise LivingEntity's native equipment modifier refresh.
        fight.mirror().tick();
        fight.holdPositions();
        fight.mirror().setYRot(0F);
        fight.mirror().setYHeadRot(0F);
        fight.mirror().yBodyRot = 0F;
        h.assertTrue(fight.mirror().getTarget() == fight.player(), "A native entity tick retains the mirrored combat target");
    }

    private static BetterCombatBridge.Profile profile(GameTestHelper h, Fight fight) {
        var profile = BetterCombatBridge.profile(fight.mirror(), 0);
        h.assertTrue(profile != null && profile.windupTicks() > 0 && profile.cooldownTicks() >= profile.windupTicks(),
                "The copied weapon resolves a usable real Better Combat profile after native equipment refresh");
        h.assertTrue(BetterMobCombat.supported(fight.mirror()), "The mirror zombie uses the main mod's armed combat controller");
        return profile;
    }

    private static void advance(Fight fight, int ticks, boolean requestAttacks) {
        for (int i = 0; i < ticks; ++i) {
            ++fight.mirror().tickCount;
            fight.player().invulnerableTime = 0;
            fight.holdPositions();
            if (requestAttacks) fight.goal().tick();
            else fight.goal().canUse(); // Advance the source goal's fallback cooldown without requesting a new attack.
            // Model a completed nearby route; active navigation would clamp head yaw toward the body.
            fight.mirror().getNavigation().stop();
            fight.mirror().getLookControl().setLookAt(fight.player(), 30F, 30F);
            fight.mirror().getLookControl().tick();
            BetterMobCombat.tick(fight.mirror());
        }
    }

    private static BetterCombatBridge.Profile delayedHit(GameTestHelper h, Fight fight) {
        var profile = profile(h, fight);
        h.assertTrue(fight.goal().canUse(), "The registered mirror's real source melee goal can reach its target");
        float before = fight.player().getHealth();
        fight.player().invulnerableTime = 0;
        fight.goal().tick();
        fight.mirror().getNavigation().stop();
        fight.mirror().getLookControl().tick();
        h.assertTrue(BetterMobCombat.attacking(fight.mirror()), "SourceMeleeGoal.tick starts a pending weapon attack");
        h.assertValueEqual(fight.player().getHealth(), before, "The goal applies no immediate damage before the weapon windup");
        advance(fight, profile.windupTicks() - 1, true);
        h.assertValueEqual(fight.player().getHealth(), before, "Repeated goal requests cannot deal damage before the configured impact");
        advance(fight, 1, true);
        h.assertTrue(fight.player().getHealth() < before, "BetterMobCombat.tick completes the requested attack through real native player damage");
        return profile;
    }

    private static void recover(Fight fight, BetterCombatBridge.Profile profile) {
        advance(fight, profile.cooldownTicks() + 24, false);
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void repeatedMirroringRetainsDelayedHitsWhenTheBodyFacesAway(GameTestHelper h) {
        if (!BetterCombatBridge.enabled()) { h.succeed(); return; }
        try (var fight = fight(h)) {
            equip(h, fight, new ItemStack(Items.IRON_SWORD));
            var goal = fight.goal();
            var weapon = fight.mirror().getMainHandItem();
            var profile = profile(h, fight);
            fight.mirror().setYRot(180F);
            fight.mirror().yBodyRot = 180F;
            fight.mirror().setYHeadRot(0F);
            h.assertTrue(goal.canUse(), "The copied sword enables real source melee");
            float before = fight.player().getHealth();
            goal.tick();
            fight.mirror().getNavigation().stop();
            fight.mirror().getLookControl().tick();
            h.assertTrue(BetterMobCombat.attacking(fight.mirror()), "The real source goal begins its sword windup while the body faces away");
            mirrorEquipment(h, fight);
            h.assertTrue(fight.goal() == goal && fight.mirror().getMainHandItem() == weapon,
                    "An unchanged mirror sync retains the running goal and held stack");
            h.assertValueEqual(fight.player().getHealth(), before, "Repeated copying does not bypass the pending windup");
            advance(fight, profile.windupTicks() - 1, true);
            h.assertValueEqual(fight.player().getHealth(), before, "The pending sword hit remains delayed after copying");
            advance(fight, 1, true);
            h.assertTrue(Math.abs(fight.mirror().getYRot() - fight.mirror().getYHeadRot()) > 150F,
                    "Source LookControl aims at the player while the stationary body still faces away");
            h.assertTrue(fight.player().getHealth() < before, "Head-aimed mirror melee reaches actual damage despite the old body direction");
            recover(fight, profile);
            mirrorEquipment(h, fight);
            h.assertTrue(fight.goal() == goal, "Later mirror syncs preserve the same source melee goal");
            delayedHit(h, fight);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void absentBetterCombatLeavesCopiedSwordMeleeImmediate(GameTestHelper h) {
        if (BetterCombatBridge.enabled()) { h.succeed(); return; }
        try (var fight = fight(h)) {
            equip(h, fight, new ItemStack(Items.IRON_SWORD));
            h.assertFalse(BetterMobCombat.supported(fight.mirror()), "Without Better Combat the copied sword keeps native combat");
            h.assertTrue(fight.goal().canUse(), "The mirror zombie retains its actual melee goal without the optional mod");
            float before = fight.player().getHealth();
            fight.goal().tick();
            h.assertTrue(fight.player().getHealth() < before, "The actual source goal immediately damages its player target without Better Combat");
            h.assertFalse(BetterMobCombat.attacking(fight.mirror()), "The native fallback leaves no pending compatibility attack");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void copiedModWeaponsSurviveNativeEquipmentAndRuntimeComponentRefresh(GameTestHelper h) {
        if (!BetterCombatBridge.enabled()) { h.succeed(); return; }
        var ids = new ArrayList<>(List.of(ResourceLocation.parse("mite:copper_sword")));
        if (SimplySwordsCompat.enabled()) ids.add(ResourceLocation.parse("simplyswords:diamond_greathammer"));
        for (var id : ids) {
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The copied mod weapon is really registered: " + id);
            try (var fight = fight(h)) {
                equip(h, fight, new ItemStack(BuiltInRegistries.ITEM.get(id)));
                var initial = profile(h, fight);
                if (id.getNamespace().equals("simplyswords"))
                    h.assertTrue(initial.twoHanded() && initial.cooldownTicks() > 20, "The fixture exercises a real slow two-handed Simply Swords weapon");
                recover(fight, delayedHit(h, fight));
                var goal = fight.goal();
                fight.player().getMainHandItem().set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(RUNTIME_DAMAGE, 4D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(RUNTIME_SPEED, -3.2D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .build());
                mirrorEquipment(h, fight);
                refreshEquipment(h, fight);
                h.assertTrue(fight.goal() == goal, "Changing components on the same held item preserves melee scheduling: " + id);
                h.assertTrue(fight.mirror().getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(RUNTIME_DAMAGE),
                        "The native mob tick applies the copied runtime attribute component: " + id);
                delayedHit(h, fight);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", templateNamespace = "modern_ite")
    public static void copiedEmptyHandsBowAndToolsRetainMeleeAndCanReturnToSword(GameTestHelper h) {
        try (var fight = fight(h)) {
            equip(h, fight, new ItemStack(Items.IRON_SWORD));
            var goal = fight.goal();
            if (BetterCombatBridge.enabled()) recover(fight, delayedHit(h, fight));
            for (var stack : List.of(ItemStack.EMPTY, new ItemStack(Items.BOW), new ItemStack(Items.IRON_PICKAXE))) {
                equip(h, fight, stack);
                h.assertTrue(fight.goal() == goal && goal.canUse(), "Changing the copied equipment retains the same usable zombie melee goal");
                h.assertFalse(BetterMobCombat.supported(fight.mirror()), "Empty hands, bow and pickaxe use the ordinary zombie melee path");
                float before = fight.player().getHealth();
                fight.player().invulnerableTime = 0;
                goal.tick();
                h.assertTrue(fight.player().getHealth() < before, "The real melee goal still damages the player after copying " + stack);
                advance(fight, 24, false);
            }
            equip(h, fight, new ItemStack(Items.IRON_SWORD));
            h.assertTrue(fight.goal() == goal, "Returning to the sword preserves the original melee goal");
            if (BetterCombatBridge.enabled()) delayedHit(h, fight);
            else {
                float before = fight.player().getHealth();
                fight.player().invulnerableTime = 0;
                goal.tick();
                h.assertTrue(fight.player().getHealth() < before, "Returning to a sword also retains immediate native melee without Better Combat");
            }
        }
        h.succeed();
    }
}
