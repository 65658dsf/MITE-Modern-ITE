package io.github.ningmeng123.modernite.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Strong zombies stay unarmed through real generation, pickup and old saves. @author NingMeng123 */
@GameTestHolder("modern_ite_strong_zombie")
@PrefixGameTestTemplate(false)
public final class StrongZombieEquipmentGameTests {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private StrongZombieEquipmentGameTests() {}

    @GameTest(template = "gear_empty", batch = "strong_zombie_equipment")
    public static void actualLateSpawnsStayUnarmedWhileArmorGenerationRemainsAvailable(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            site.date(512, 512, true);
            boolean generatedArmor = false;
            for (int seed = 0; seed < 32; seed++) {
                Mob strong = site.mob(ExtremeMobs.type(ExtremeKind.ZOMBIE_FLY_AWAY));
                strong.getRandom().setSeed(90210L + seed);
                EventHooks.finalizeMobSpawn(strong, site.level, site.level.getCurrentDifficultyAt(strong.blockPosition()), MobSpawnType.SPAWN_EGG, null);
                h.assertTrue(site.level.addFreshEntity(strong), "The actual strong zombie completes spawn finalization and joins the server");
                assertEmptyHands(h, strong);
                for (var slot : ARMOR) generatedArmor |= !strong.getItemBySlot(slot).isEmpty();
                strong.discard(); SpawnPopulation.release(strong);
            }
            h.assertTrue(generatedArmor, "Late-date native and progression equipment rolls still produce actual worn armor");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "strong_zombie_equipment")
    public static void weaponsToolsBowsAndShieldsCannotBePickedUpOrAssignedToEitherHand(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            Mob strong = fresh(site, ExtremeKind.ZOMBIE_FLY_AWAY); strong.setCanPickUpLoot(true);
            for (var item : new net.minecraft.world.item.Item[]{Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE,
                    Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.SHIELD}) {
                ItemStack stack = new ItemStack(item);
                h.assertFalse(strong.canHoldItem(stack), "The strong zombie rejects held equipment before pickup: " + item);
                h.assertFalse(strong.wantsToPickUp(stack), "Ground pickup and pursuit cannot select this held item: " + item);
                h.assertFalse(strong.canTakeItem(stack), "Other equipment admission cannot accept this held item: " + item);
                h.assertTrue(strong.equipItemIfPossible(stack).isEmpty() && stack.getCount() == 1,
                        "A rejected direct pickup does not report consuming the ground stack: " + item);
                strong.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                strong.setItemSlot(EquipmentSlot.OFFHAND, stack.copy());
                assertEmptyHands(h, strong);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "strong_zombie_equipment")
    public static void allFourArmorSlotsRemainUsableAndRejectedArmorNeverFallsBackIntoAHand(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            Mob strong = fresh(site, ExtremeKind.ZOMBIE_FLY_AWAY); strong.setCanPickUpLoot(true);
            var pieces = new net.minecraft.world.item.Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
            for (int index = 0; index < ARMOR.length; index++) {
                strong.setItemSlot(ARMOR[index], ItemStack.EMPTY);
                ItemStack incoming = new ItemStack(pieces[index]);
                h.assertTrue(strong.canHoldItem(incoming) && strong.canTakeItem(incoming), "Empty worn armor slots retain their normal admission: " + ARMOR[index]);
                h.assertTrue(!strong.equipItemIfPossible(incoming).isEmpty() && strong.getItemBySlot(ARMOR[index]).is(pieces[index]),
                        "The normal pickup path equips actual armor in its proper slot: " + ARMOR[index]);
            }
            ItemStack rejected = new ItemStack(Items.LEATHER_HELMET);
            h.assertTrue(strong.equipItemIfPossible(rejected).isEmpty() && rejected.getCount() == 1,
                    "An inferior helmet cannot be consumed by native fallback to a forbidden hand slot");
            h.assertTrue(strong.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "Rejected armor preserves the better worn piece");
            assertEmptyHands(h, strong);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "strong_zombie_equipment")
    public static void oldArmedSavesLoseOnlyHeldItemsWhileOtherZombieRolesStillUseWeapons(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            Mob strong = fresh(site, ExtremeKind.ZOMBIE_FLY_AWAY);
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE); chest.setDamageValue(27);
            chest.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved strong zombie armor"));
            strong.setItemSlot(EquipmentSlot.CHEST, chest);
            CompoundTag oldSave = new CompoundTag(); strong.saveWithoutId(oldSave);
            ListTag hands = new ListTag(); hands.add(new ItemStack(Items.IRON_SWORD).save(site.level.registryAccess()));
            hands.add(new ItemStack(Items.SHIELD).save(site.level.registryAccess())); oldSave.put("HandItems", hands);
            strong.discard();
            Mob loaded = fresh(site, ExtremeKind.ZOMBIE_FLY_AWAY); loaded.load(oldSave);
            assertEmptyHands(h, loaded);
            h.assertTrue(ItemStack.matches(chest, loaded.getItemBySlot(EquipmentSlot.CHEST)), "Old-save migration preserves worn armor, name and damage components");
            h.assertTrue(site.level.addFreshEntity(loaded), "The migrated strong zombie rejoins the real server world");
            assertEmptyHands(h, loaded);
            h.assertTrue(ItemStack.matches(chest, loaded.getItemBySlot(EquipmentSlot.CHEST)), "Join equipment callbacks retain the saved armor");
            for (var kind : new ExtremeKind[]{ExtremeKind.ZOMBIE_KNOCKBACK, ExtremeKind.MINER_ZOMBIE}) {
                Mob ordinary = fresh(site, kind);
                ordinary.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                ordinary.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                h.assertTrue(ordinary.getMainHandItem().is(Items.IRON_SWORD) && ordinary.getOffhandItem().is(Items.SHIELD),
                        "The unarmed policy does not alter another zombie role: " + kind);
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "strong_zombie_equipment")
    public static void armoredStrongZombieStillDealsItsUnarmedLaunchAttack(GameTestHelper h) {
        try (var site = new SpawnBalanceGameTests.Site(h)) {
            Mob strong = fresh(site, ExtremeKind.ZOMBIE_FLY_AWAY);
            strong.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            strong.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            var target = h.makeMockPlayer(GameType.SURVIVAL);
            target.moveTo(strong.position().add(1, 0, 0));
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
            target.setOnGround(false); target.setDeltaMovement(0, -4, 0);
            h.assertTrue(strong.doHurtTarget(target) && target.getHealth() < 1000,
                    "An armored, unarmed strong zombie still lands a real melee hit");
            h.assertTrue(target.getDeltaMovement().y > 1.69 && target.getDeltaMovement().y < 1.70,
                    "The real hit retains the existing fixed upward launch while the victim is falling");
            h.assertTrue(strong.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "The launch attack preserves the worn armor");
            assertEmptyHands(h, strong);
        }
        h.succeed();
    }

    private static Mob fresh(SpawnBalanceGameTests.Site site, ExtremeKind kind) {
        Mob mob = site.mob(ExtremeMobs.type(kind)); MobAbilities.initialize(mob, (ExtremeMob) mob);
        mob.getPersistentData().putBoolean(SpawnPopulation.EXPLICIT_COMMAND, true); return mob;
    }

    private static void assertEmptyHands(GameTestHelper h, Mob mob) {
        h.assertTrue(mob.getMainHandItem().isEmpty() && mob.getOffhandItem().isEmpty(), "The strong zombie keeps both hands empty");
    }
}
