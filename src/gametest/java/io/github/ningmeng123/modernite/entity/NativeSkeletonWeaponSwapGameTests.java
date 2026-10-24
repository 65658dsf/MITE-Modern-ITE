package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native mob ticks preserve spare weapons and respect empty/disarmed hands. @author NingMeng123 */
@GameTestHolder("modern_ite_weapon_swap")
@PrefixGameTestTemplate(false)
public final class NativeSkeletonWeaponSwapGameTests {
    private static final String STOCK = "modern_ite:stocked_weapon";
    private NativeSkeletonWeaponSwapGameTests() {}

    @GameTest(template = "gear_empty", batch = "native_weapon_swap")
    public static void emptyMainHandConsumesItsSpareWithoutEncodingEmptyOrDuplicatingIt(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            ItemStack bow = fixture.bow();
            fixture.stock(bow);
            fixture.skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            fixture.tickAtDistance(8D);
            h.assertTrue(ItemStack.matches(fixture.skeleton.getMainHandItem(), bow),
                    "The actual failing native tick equips the original spare bow from an empty main hand");
            h.assertTrue(fixture.stored().isEmpty(), "An empty old main hand leaves no backup weapon");
            fixture.skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            fixture.tickAtDistance(8D);
            h.assertTrue(fixture.skeleton.getMainHandItem().isEmpty() && fixture.stored().isEmpty(),
                    "Later ticks cannot resurrect or duplicate the consumed spare weapon");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "native_weapon_swap")
    public static void normalDistanceSwapsRetainBothWeaponsAndTheirComponents(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            ItemStack bow = fixture.bow(), sword = fixture.sword();
            fixture.skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow.copy());
            fixture.stock(sword);
            fixture.tickAtDistance(4D);
            h.assertTrue(ItemStack.matches(fixture.skeleton.getMainHandItem(), sword)
                            && ItemStack.matches(fixture.stored(), bow),
                    "Close combat stows the exact named, damaged and enchanted bow and equips the original sword");
            fixture.tickAtDistance(8D);
            h.assertTrue(ItemStack.matches(fixture.skeleton.getMainHandItem(), bow)
                            && ItemStack.matches(fixture.stored(), sword),
                    "Distant combat restores the exact bow and preserves all sword components in stock");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "native_weapon_swap")
    public static void disarmingCooldownKeepsTheSpareStoredUntilTheRealCooldownExpires(GameTestHelper h) {
        try (var fixture = new Fixture(h)) {
            ItemStack bow = fixture.bow();
            fixture.stock(bow);
            fixture.skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            fixture.skeleton.getPersistentData().putInt("mite_enchantment_disarmed_ticks", 40);
            fixture.tickAtDistance(8D);
            h.assertTrue(fixture.skeleton.getMainHandItem().isEmpty() && ItemStack.matches(fixture.stored(), bow),
                    "The addon cannot bypass the base disarming state by immediately pulling out its spare");
            for (int tick = 0; tick < 39; tick++)
                SourceEnchantmentCombat.tick(new EntityTickEvent.Pre(fixture.skeleton));
            fixture.tickAtDistance(8D);
            h.assertTrue(SourceEnchantmentCombat.disarmed(fixture.skeleton)
                            && fixture.skeleton.getMainHandItem().isEmpty() && ItemStack.matches(fixture.stored(), bow),
                    "The spare remains untouched through the last disarmed tick");
            SourceEnchantmentCombat.tick(new EntityTickEvent.Pre(fixture.skeleton));
            fixture.tickAtDistance(8D);
            h.assertTrue(!SourceEnchantmentCombat.disarmed(fixture.skeleton)
                            && ItemStack.matches(fixture.skeleton.getMainHandItem(), bow) && fixture.stored().isEmpty(),
                    "After the real base cooldown expires, the empty hand safely consumes its spare once");
        }
        h.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final IteReferenceSpawnGameTests.Settings settings = new IteReferenceSpawnGameTests.Settings();
        final R196Skeleton skeleton;
        final Cow target;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            settings.set("bone_lord_and_longdead_change_weapon_chance", 1D);
            settings.set("skeleton_force_melee_attack", false);
            skeleton = (R196Skeleton)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("mite:longdead"))
                    .create(helper.getLevel());
            target = EntityType.COW.create(helper.getLevel());
            if (skeleton == null || target == null) throw new AssertionError("Registered regression entities must construct");
            skeleton.moveTo(helper.absolutePos(new BlockPos(1, 180, 1)).getCenter());
            skeleton.setNoAi(true);
            skeleton.setNoGravity(true);
            target.setNoAi(true);
            target.setNoGravity(true);
            skeleton.getPersistentData().putBoolean("modern_ite:switch_weapon", true);
            skeleton.tickCount = 10;
            skeleton.setTarget(target);
        }

        ItemStack bow() {
            ItemStack bow = new ItemStack(Items.BOW);
            bow.set(DataComponents.CUSTOM_NAME, Component.literal("saved spare bow"));
            bow.setDamageValue(17);
            MobAbilities.enchant(skeleton, bow, "minecraft:power", 2);
            return bow;
        }

        ItemStack sword() {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("saved spare sword"));
            sword.setDamageValue(9);
            MobAbilities.enchant(skeleton, sword, "minecraft:sharpness", 3);
            return sword;
        }

        void stock(ItemStack stack) { skeleton.getPersistentData().put(STOCK, stack.save(skeleton.registryAccess())); }

        ItemStack stored() {
            return ItemStack.parseOptional(skeleton.registryAccess(), skeleton.getPersistentData().getCompound(STOCK));
        }

        void tickAtDistance(double distance) {
            target.moveTo(skeleton.position().add(distance, 0D, 0D));
            skeleton.getSensing().tick();
            helper.assertTrue(skeleton.getTarget() == target && skeleton.getSensing().hasLineOfSight(target),
                    "The real skeleton can see its accepted target at the requested swap distance");
            ExtremeNativeMobs.tick(new EntityTickEvent.Post(skeleton));
        }

        @Override public void close() {
            skeleton.discard();
            target.discard();
            settings.close();
        }
    }
}
