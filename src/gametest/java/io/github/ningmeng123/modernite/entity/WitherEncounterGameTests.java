package io.github.ningmeng123.modernite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.content.wither.WitherEncounters;
import io.github.ningmeng123.modernite.content.wither.WitherTreasureContent;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native skull placement and death transactions preserve distinct encounters. @author NingMeng123 */
@GameTestHolder("modern_ite_wither")
@PrefixGameTestTemplate(false)
public final class WitherEncounterGameTests {
    private WitherEncounterGameTests() {}

    @GameTest(template = "gear_empty")
    public static void threeOrdinarySkullsSummonTheOrdinaryWither(GameTestHelper h) {
        for (Direction face : new Direction[]{Direction.UP, Direction.SOUTH}) {
            WitherBoss wither = summon(h, new ItemStack(Items.WITHER_SKELETON_SKULL), 2, face);
            try {
                assertProfile(h, wither, false);
                h.assertFalse(wither.hasCustomName(), "The ordinary summon keeps its native name");
                if (ModList.get().isLoaded("witherplus"))
                    h.assertFalse(brainActive(wither), "WitherPlus leaves a real ordinary-skull summon inactive");
            } finally { wither.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void explicitEncounterIdentitySurvivesNativeSaveAndReload(GameTestHelper h) {
        for (boolean enhanced : new boolean[]{false, true}) {
            WitherBoss original = create(h, enhanced);
            WitherBoss restored = EntityType.WITHER.create(h.getLevel());
            h.assertTrue(restored != null, "The saved Wither registry type creates a fresh instance");
            try {
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                restored.load(saved);
                restored.setUUID(UUID.randomUUID());
                h.assertTrue(WitherEncounters.isEnhanced(restored) == enhanced,
                        "The encounter identity survives the real save/load pipeline before rejoining");
                h.assertTrue(h.getLevel().addFreshEntity(restored), "The saved Wither rejoins the level");
                assertProfile(h, restored, enhanced);
            } finally { original.discard(); restored.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void enhancedDeathsAlwaysDropFiveAndOrdinaryDeathsDropNone(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (boolean enhanced : new boolean[]{false, true}) {
            for (int looting : new int[]{-1, 0, 10}) {
                WitherBoss wither = create(h, enhanced);
                var observed = new ArrayList<ItemStack>();
                var calls = new int[1];
                Consumer<LivingDropsEvent> inspect = event -> {
                    if (event.getEntity() != wither) return;
                    calls[0]++;
                    event.getDrops().forEach(item -> observed.add(item.getItem().copy()));
                    h.assertTrue(event.isRecentlyHit() == (looting >= 0),
                            "The native transaction distinguishes environmental death from a recent player kill");
                    event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDropsEvent.class, inspect);
                try {
                    wither.setInvulnerableTicks(0);
                    wither.setHealth(1F);
                    if (looting < 0) {
                        h.assertTrue(wither.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE),
                                "An environmental source performs the actual death transaction");
                    } else {
                        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                        if (looting > 0) sword.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                                .getHolderOrThrow(Enchantments.LOOTING), looting);
                        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
                        h.assertTrue(wither.hurt(h.getLevel().damageSources().playerAttack(player), Float.MAX_VALUE),
                                "The real player source kills the Wither with looting " + looting);
                    }
                    h.assertTrue(!wither.isAlive() && calls[0] == 1, "One native death emits exactly one drops event");
                    int treasure = observed.stream().filter(stack -> stack.is(WitherTreasureContent.TREASURE.get()))
                            .mapToInt(ItemStack::getCount).sum();
                    h.assertTrue(treasure == (enhanced ? 5 : 0),
                            "The enhanced flag alone fixes treasure count, independent of recent player damage and looting");
                    h.assertTrue(observed.stream().filter(stack -> stack.is(Items.NETHER_STAR))
                            .mapToInt(ItemStack::getCount).sum() == 1, "The existing Nether Star remains in the same transaction");
                } finally {
                    NeoForge.EVENT_BUS.unregister(inspect);
                    wither.discard();
                }
            }
        }
        h.succeed();
    }

    static WitherBoss create(GameTestHelper h, boolean enhanced) {
        WitherBoss wither = EntityType.WITHER.create(h.getLevel());
        h.assertTrue(wither != null, "The real Wither registry type creates");
        wither.moveTo(h.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        WitherEncounters.markSummon(wither, enhanced);
        if (ModList.get().isLoaded("witherplus")) setBrainSpecial(wither, enhanced);
        wither.makeInvulnerable();
        h.assertTrue(h.getLevel().addFreshEntity(wither), "The marked Wither passes production level initialization");
        wither.setNoAi(true);
        wither.setNoGravity(true);
        return wither;
    }

    static void assertProfile(GameTestHelper h, WitherBoss wither, boolean enhanced) {
        h.assertTrue(WitherEncounters.isEnhanced(wither) == enhanced, "The encounter identity matches its summon ingredients");
        h.assertTrue(wither.getMaxHealth() == 1300F, "Ordinary and special summons both retain the 1300-health profile");
        h.assertTrue(wither.getInvulnerableTicks() == (enhanced ? 600 : 220), "Only the special summon receives the 600-tick charge");
    }

    static WitherBoss summon(GameTestHelper h, ItemStack finalSkull, boolean unrelatedSpecialSkull) {
        return summon(h, finalSkull, unrelatedSpecialSkull ? -1 : 2, Direction.UP);
    }

    static WitherBoss summon(GameTestHelper h, ItemStack skull, int skullSlot, Direction face) {
        return summon(h, h.getLevel(), h.absolutePos(new BlockPos(1, 4, 2)), skull, skullSlot, face);
    }

    static WitherBoss summon(GameTestHelper h, ServerLevel level, BlockPos left, ItemStack skull, int skullSlot, Direction face) {
        for (BlockPos pos : BlockPos.betweenClosed(left.offset(-1, -3, -3), left.offset(3, 1, 1)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        Direction towardStem = face.getOpposite();
        BlockPos crossbar = left.relative(towardStem);
        for (int x = 0; x < 3; x++) level.setBlock(crossbar.east(x), Blocks.SOUL_SAND.defaultBlockState(), 2);
        level.setBlock(crossbar.east().relative(towardStem), Blocks.SOUL_SAND.defaultBlockState(), 2);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "WitherTest"));
        player.setGameMode(GameType.CREATIVE);
        player.moveTo(Vec3.atBottomCenterOf(left.offset(1, -2, 1)));
        if (skullSlot < 0) {
            level.setBlock(left.offset(1, -1, 1), Blocks.SOUL_SAND.defaultBlockState(), 2);
            placeSkull(h, player, left.offset(1, 0, 1), skull, Direction.UP);
        }
        var summoned = new ArrayList<WitherBoss>();
        Consumer<EntityJoinLevelEvent> capture = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof WitherBoss wither
                    && wither.position().distanceToSqr(left.getCenter()) < 36) summoned.add(wither);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, capture);
        try {
            for (int slot = 0; slot < 3; slot++) {
                BlockPos position = left.east(slot);
                placeSkull(h, player, position, slot == skullSlot ? skull : new ItemStack(Items.WITHER_SKELETON_SKULL), face);
                if (slot == skullSlot && slot < 2) {
                    h.assertTrue(level.getBlockState(position).is(face == Direction.UP
                            ? Blocks.WITHER_SKELETON_SKULL : Blocks.WITHER_SKELETON_WALL_SKULL), "The chosen mounting orientation was placed");
                    SkullBlockEntity head = (SkullBlockEntity) level.getBlockEntity(position);
                    h.assertTrue(head.getPersistentData().getBoolean("WitherPlusSpecial"), "The placed special skull retains its identity");
                    var update = head.getUpdatePacket().getTag();
                    var replica = new SkullBlockEntity(position, head.getBlockState());
                    replica.handleUpdateTag(update, level.registryAccess());
                    h.assertTrue(replica.getPersistentData().getBoolean("WitherPlusSpecial"), "The skull update packet preserves the special texture marker");
                }
            }
            h.assertTrue(summoned.size() == 1, "Placing the third actual skull summons exactly one Wither");
            WitherBoss wither = summoned.getFirst();
            h.assertTrue(wither.isAddedToLevel(), "The created Wither was admitted to " + level.dimension().location());
            wither.setNoAi(true);
            wither.setNoGravity(true);
            for (int x = 0; x < 3; x++) h.assertTrue(level.getBlockState(left.east(x)).isAir(),
                    "The actual Wither spawn consumes each skull in the matched pattern");
            h.assertTrue(level.getBlockState(crossbar.east().relative(towardStem)).isAir(), "The summon consumed the soul sand stem");
            return wither;
        } catch (RuntimeException | Error failure) {
            summoned.forEach(WitherBoss::discard);
            throw failure;
        } finally { NeoForge.EVENT_BUS.unregister(capture); }
    }

    private static void placeSkull(GameTestHelper h, FakePlayer player, BlockPos position, ItemStack skull, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, skull.copy());
        player.setYRot(180F);
        player.setXRot(face == Direction.UP ? 90F : 0F);
        BlockPos support = position.relative(face.getOpposite());
        var hit = new BlockHitResult(Vec3.atCenterOf(support).relative(face, .5D), face, support, false);
        var result = player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        h.assertTrue(result.consumesAction(), "The actual skull item places through the server interaction: item="
                + skull.getItem() + ", pos=" + position + ", result=" + result
                + ", instabuild=" + player.getAbilities().instabuild
                + ", cooldown=" + io.github.ningmeng123.mite.world.PlacementDelay.remainingTicks(player)
                + ", target=" + player.serverLevel().getBlockState(position));
    }

    static Object brain(WitherBoss wither) {
        try { return wither.getClass().getMethod("wp$getBrain").invoke(wither); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("WitherPlus must apply its real WitherBrainHolder mixin", failure); }
    }

    static boolean brainActive(WitherBoss wither) {
        try { return (boolean) brain(wither).getClass().getMethod("active").invoke(brain(wither)); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("The real WitherPlus active predicate must be callable", failure); }
    }

    static void setBrainSpecial(WitherBoss wither, boolean special) {
        try { brain(wither).getClass().getMethod("setSpecialSummon", boolean.class).invoke(brain(wither), special); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("The real WitherPlus summon setter must be callable", failure); }
    }
}
