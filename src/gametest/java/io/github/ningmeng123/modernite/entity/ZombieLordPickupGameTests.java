package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.entity.MobEquipmentUpgrades;
import io.github.ningmeng123.mite.entity.SeekBetterEquipmentGoal;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Lord pickup decisions preserve material and real attack damage through pursuit and collision paths. @author NingMeng123 */
@GameTestHolder("modern_ite_zombie_lord")
@PrefixGameTestTemplate(false)
public final class ZombieLordPickupGameTests {
    private static final ExtremeKind[] LORDS = {ExtremeKind.ZOMBIE_LORD, ExtremeKind.ZOMBIE_ELITE_LORD};
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("modern_ite_lord_pickup", Integer::compareTo);

    private ZombieLordPickupGameTests() {}

    @GameTest(template = "gear_empty", batch = "zombie_lord_pickup")
    public static void neitherLordReplacesVibraniumSwordOrHammerWithHigherQualityCopper(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            for (var kind : LORDS) for (var weapon : List.of("vibranium_sword", "vibranium_war_hammer")) {
                Mob lord = fresh(site, kind);
                ItemStack held = tool(weapon, Quality.AVERAGE), copper = tool("copper_sword", Quality.EXCELLENT);
                lord.setItemSlot(EquipmentSlot.MAINHAND, held);
                h.assertTrue(EquipmentDurability.quality(copper).ordinal() > EquipmentDurability.quality(held).ordinal(), "The copper temptation really has a higher quality grade");
                h.assertFalse(lord.canHoldItem(copper), "A lord refuses a lower material even when quality rises: " + kind + "/" + weapon);
                h.assertFalse(lord.wantsToPickUp(copper), "Native pickup interest retains the same material floor");
                h.assertFalse(MobEquipmentUpgrades.isUpgrade(lord, copper), "The shared equipment-pursuit policy cannot classify the copper downgrade as an upgrade");
                h.assertTrue(lord.equipItemIfPossible(copper).isEmpty() && copper.getCount() == 1, "Collision pickup reports rejection and leaves the incoming stack available");
                h.assertTrue(ItemStack.matches(held, lord.getMainHandItem()), "The existing vibranium weapon remains unchanged");
                var sharpness = site.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
                held.enchant(sharpness, 5);
                ItemStack unenchantedUpgrade = tool(weapon, Quality.EXCELLENT);
                h.assertFalse(lord.canHoldItem(unenchantedUpgrade), "Higher quality cannot erase the current weapon's Sharpness V");
                h.assertTrue(lord.equipItemIfPossible(unenchantedUpgrade).isEmpty(), "A damage-enchantment downgrade leaves the current weapon equipped");
                unenchantedUpgrade.enchant(sharpness, 5);
                h.assertTrue(lord.canHoldItem(unenchantedUpgrade), "The same-material quality upgrade remains admissible when it preserves the damage enchantment");
                lord.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_pickup", timeoutTicks = 240)
    public static void realEquipmentPursuitIgnoresCopperButAcceptsAReachableSameMaterialUpgrade(GameTestHelper h) {
        var fixture = new PursuitFixture(h);
        h.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { fixture.close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { fixture.close(); }
        });
        h.startSequence().thenWaitUntil(() -> {
            if (fixture.chunks.stream().anyMatch(chunk -> !h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())))
                for (int task = 0; task < 64 && h.getLevel().getChunkSource().pollTask(); task++) {}
            h.assertTrue(fixture.chunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())), "The real pickup fixture reaches native entity-ticking status");
        }).thenExecute(() -> {
            for (var kind : LORDS) {
                var lord = (PathfinderMob)fresh(fixture.site, kind);
                lord.moveTo(fixture.origin.getX() + .5, fixture.origin.getY(), fixture.origin.getZ() + .5);
                h.assertTrue(fixture.site.level.addFreshEntity(lord), "The manually requested lord enters the native entity manager");
                lord.setNoAi(false); lord.setOnGround(true); lord.setNoGravity(false); lord.setTarget(null);
                lord.setItemSlot(EquipmentSlot.MAINHAND, tool("vibranium_war_hammer", Quality.AVERAGE));
                ItemEntity copper = fixture.item(tool("copper_sword", Quality.EXCELLENT), 4);
                var rejected = new SeekBetterEquipmentGoal(lord);
                h.assertFalse(rejected.canUse(), "An actual visible copper ItemEntity cannot attract the lord's equipment pursuit: " + kind);
                h.assertTrue(copper.isAlive() && ItemStack.matches(copper.getItem(), tool("copper_sword", Quality.EXCELLENT)), "Rejecting pursuit leaves the ground copper weapon intact");
                ItemEntity upgrade = fixture.item(tool("vibranium_war_hammer", Quality.EXCELLENT), 5);
                h.assertTrue(lord.getNavigation().createPath(upgrade, 0) != null && lord.getNavigation().createPath(upgrade, 0).canReach(), "The same-material positive control has a genuine traversable native route");
                var accepted = new SeekBetterEquipmentGoal(lord);
                h.assertTrue(accepted.canUse() && accepted.getTargetItem() == upgrade, "The same real goal chooses the reachable same-material quality upgrade");
                accepted.start();
                h.assertTrue(accepted.canContinueToUse() && lord.getNavigation().getPath() != null, "The accepted upgrade starts ordinary movement navigation");
                accepted.stop(); lord.discard(); SpawnPopulation.release(lord); copper.discard(); upgrade.discard();
            }
        }).thenSucceed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_pickup")
    public static void pickupComparesAttackDamageWithoutAddingUnrelatedAttackSpeedModifiers(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            for (var kind : LORDS) {
                Mob lord = fresh(site, kind);
                ItemStack current = customHammer("current", 8, 0, Quality.AVERAGE);
                ItemStack weakFast = customHammer("weak_fast", 4, 1000, Quality.EXCELLENT);
                ItemStack strongSlow = customHammer("strong_slow", 12, -1000, Quality.EXCELLENT);
                lord.setItemSlot(EquipmentSlot.MAINHAND, current);
                h.assertFalse(lord.canHoldItem(weakFast), "A huge attack-speed modifier cannot disguise lower attack damage: " + kind);
                h.assertFalse(MobEquipmentUpgrades.isUpgrade(lord, weakFast), "The lower-damage custom weapon is also rejected by pursuit");
                h.assertTrue(lord.equipItemIfPossible(weakFast).isEmpty() && ItemStack.matches(current, lord.getMainHandItem()), "Collision pickup retains the current stronger weapon");
                h.assertTrue(lord.canHoldItem(strongSlow) && MobEquipmentUpgrades.isUpgrade(lord, strongSlow), "Unrelated negative attack speed does not hide a real same-material damage improvement");
                h.assertTrue(!lord.equipItemIfPossible(strongSlow).isEmpty() && ItemStack.matches(strongSlow, lord.getMainHandItem()), "The stronger same-material positive control passes the real pickup path");
                lord.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "zombie_lord_pickup")
    public static void emptyHandsArmorAndOtherZombieRolesKeepTheirNormalPickupRules(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            for (var kind : LORDS) {
                Mob lord = fresh(site, kind);
                lord.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                ItemStack copper = tool("copper_sword", Quality.EXCELLENT);
                h.assertTrue(lord.canHoldItem(copper) && !lord.equipItemIfPossible(copper).isEmpty(), "A genuinely empty hand may still take its first weapon: " + kind);
                lord.setItemSlot(EquipmentSlot.MAINHAND, tool("vibranium_war_hammer", Quality.AVERAGE));
                lord.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
                h.assertTrue(lord.canHoldItem(armor) && !lord.equipItemIfPossible(armor).isEmpty() && lord.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "The weapon material floor does not disable normal armor pickup");
                ItemStack shield = new ItemStack(Items.SHIELD);
                h.assertTrue(lord.canHoldItem(shield), "The mainhand weapon comparison does not change offhand shield admission");
                lord.discard();
            }
            for (var kind : new ExtremeKind[]{ExtremeKind.ZOMBIE_KNOCKBACK, ExtremeKind.MINER_ZOMBIE}) {
                Mob other = fresh(site, kind);
                other.setItemSlot(EquipmentSlot.MAINHAND, tool("vibranium_war_hammer", Quality.AVERAGE));
                ItemStack copper = tool("copper_sword", Quality.EXCELLENT);
                h.assertTrue(other.canHoldItem(copper) && MobEquipmentUpgrades.isUpgrade(other, copper), "Other zombie roles retain their existing quality-first policy: " + kind);
                h.assertTrue(!other.equipItemIfPossible(copper).isEmpty() && ItemStack.matches(copper, other.getMainHandItem()), "The lord-only restriction does not alter another zombie's collision pickup");
                other.discard();
            }
        }
        h.succeed();
    }

    private static Mob fresh(SpawnBalanceGameTests.Site site, ExtremeKind kind) {
        Mob mob = site.mob(ExtremeMobs.type(kind)); MobAbilities.initialize(mob, (ExtremeMob)mob);
        for (var slot : EquipmentSlot.values()) mob.setDropChance(slot, 0F);
        mob.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true); mob.setCanPickUpLoot(true); return mob;
    }
    private static ItemStack tool(String id, Quality quality) {
        ItemStack stack = MobAbilities.item("mite:" + id); EquipmentDurability.setQuality(stack, quality); return stack;
    }
    private static ItemStack customHammer(String suffix, double damage, double speed, Quality quality) {
        ItemStack stack = tool("vibranium_war_hammer", quality);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(ResourceLocation.parse("modern_ite:lord_pickup_" + suffix + "_damage"), damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(ResourceLocation.parse("modern_ite:lord_pickup_" + suffix + "_speed"), speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
        return stack;
    }

    private static final class PursuitFixture implements AutoCloseable {
        final SpawnBalanceGameTests.Site site;
        final GameTestHelper helper;
        final BlockPos origin;
        final int ticket = IDS.incrementAndGet();
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<Entity> items = new ArrayList<>();
        boolean closed;
        PursuitFixture(GameTestHelper h) {
            helper = h; site = new SpawnBalanceGameTests.Site(h); site.date(128, 128, true);
            origin = BlockPos.containing(site.origin);
            ChunkPos low = new ChunkPos(origin.offset(-3, 0, -3)), high = new ChunkPos(origin.offset(8, 0, 3));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                site.level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket);
                for (int cx = x - 2; cx <= x + 2; cx++) for (int cz = z - 2; cz <= z + 2; cz++) site.level.getChunk(cx, cz, ChunkStatus.FULL, true);
            }
            for (int x = -3; x <= 8; x++) for (int z = -3; z <= 3; z++) {
                site.level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                for (int y = 0; y < 3; y++) site.level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                site.level.setBlock(origin.offset(x, 3, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        ItemEntity item(ItemStack stack, int x) {
            ItemEntity item = new ItemEntity(site.level, origin.getX() + .5 + x, origin.getY(), origin.getZ() + .5, stack);
            item.setNoPickUpDelay(); item.setNoGravity(true); item.setDeltaMovement(0, 0, 0); items.add(item);
            helper.assertTrue(site.level.addFreshEntity(item), "The actual ground weapon joins the prepared level");
            helper.assertTrue(site.level.getEntitiesOfClass(ItemEntity.class, item.getBoundingBox().inflate(.1), value -> value == item).contains(item), "The native item query indexes the pickup candidate");
            return item;
        }
        @Override public void close() {
            if (closed) return; closed = true;
            items.forEach(Entity::discard); site.close();
            for (var chunk : chunks) site.level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket);
        }
    }
}
