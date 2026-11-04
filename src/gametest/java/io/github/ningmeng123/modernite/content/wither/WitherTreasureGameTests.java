package io.github.ningmeng123.modernite.content.wither;

import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.modernite.gear.EnhanceGemItem;
import io.github.ningmeng123.modernite.gear.GemType;
import io.github.ningmeng123.modernite.gear.GemBoxItem;
import io.github.ningmeng123.modernite.gear.ExtremeGear;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Real registry rewards, inventory settlement and repeat-use protection. @author NingMeng123 */
@GameTestHolder("modern_ite_wither") @PrefixGameTestTemplate(false)
public final class WitherTreasureGameTests {
    private WitherTreasureGameTests() {}

    @GameTest(template = "gear_empty")
    public static void everyRewardUsesItsRequestedGemRankOrDiamondRange(GameTestHelper h) {
        var random = RandomSource.create(841260L);
        for (var reward : new WitherTreasureRewards.Reward[]{WitherTreasureRewards.Reward.GEM_3,
                WitherTreasureRewards.Reward.GEM_4, WitherTreasureRewards.Reward.GEM_5, WitherTreasureRewards.Reward.GEM_6}) {
            var types = new HashSet<GemType>();
            for (int roll = 0; roll < 256; roll++) {
                ItemStack stack = WitherTreasureItem.createReward(h.getLevel(), random, reward);
                h.assertTrue(stack.getCount() == 1 && stack.getItem() instanceof EnhanceGemItem, "A gem award is exactly one real enhancement gem");
                var gem = (EnhanceGemItem) stack.getItem();
                h.assertTrue(gem.rank == reward.ordinal() + 3, "The selected treasure branch fixes the gem rank");
                types.add(gem.type);
            }
            h.assertTrue(types.size() == GemType.values().length, "Every gem category participates at this rank");
        }
        var counts = new HashSet<Integer>();
        for (int roll = 0; roll < 256; roll++) {
            ItemStack stack = WitherTreasureItem.createReward(h.getLevel(), random, WitherTreasureRewards.Reward.DIAMONDS);
            h.assertTrue(stack.is(Items.DIAMOND) && stack.getCount() >= 10 && stack.getCount() <= 20, "Diamonds stay within the inclusive 10 to 20 range");
            counts.add(stack.getCount());
        }
        h.assertTrue(counts.size() == 11, "Both endpoints and every intermediate diamond amount are reachable");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void booksHaveOneEnabledMaximumEnchantmentIncludingModdedOnes(GameTestHelper h) {
        var random = RandomSource.create(632830L);
        boolean modded = false;
        for (int roll = 0; roll < 256; roll++) {
            ItemStack book = WitherTreasureItem.createReward(h.getLevel(), random, WitherTreasureRewards.Reward.ENCHANTED_BOOK);
            h.assertTrue(book.is(Items.ENCHANTED_BOOK) && book.getCount() == 1, "The book branch yields one enchanted book");
            var enchantments = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            h.assertTrue(enchantments.size() == 1, "Each reward book has exactly one enchantment");
            for (var entry : enchantments.entrySet()) {
                h.assertTrue(ConfiguredEnchantments.enabled(entry.getKey()) && !entry.getKey().is(EnchantmentTags.CURSE), "Disabled enchantments and curses do not enter the reward pool");
                h.assertTrue(entry.getIntValue() == entry.getKey().value().getMaxLevel(), "A book uses the actual current maximum level");
                modded |= entry.getKey().unwrapKey().orElseThrow().location().getNamespace().equals("modern_ite");
            }
        }
        h.assertTrue(modded, "ITE enchantments participate in random maximum-level books");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void openingConsumesOneTreasureAndCooldownRejectsDuplicateUse(GameTestHelper h) {
        var player = player(h, GameType.SURVIVAL);
        var treasure = WitherTreasureContent.TREASURE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(treasure, 5));
        treasure.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        long started = player.getMainHandItem().get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
        h.assertTrue(player.getMainHandItem().getCount() == 5 && occupiedRewardSlots(player) == 0,
                "Clicking starts the opening without charging or granting a reward early");
        treasure.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getMainHandItem().get(ExtremeGear.GEM_BOX_OPEN_STARTED.get()) == started, "Duplicate requests cannot restart the animation");
        advance(h, player, started, GemBoxItem.REVEAL_TICK);
        ItemStack shown = GemBoxItem.revealedGem(player.getMainHandItem());
        h.assertTrue(!shown.isEmpty() && player.getMainHandItem().getCount() == 5 && occupiedRewardSlots(player) == 0,
                "The selected reward appears at tick 36 before settlement");
        advance(h, player, started, GemBoxItem.OPEN_TICKS - 1);
        h.assertTrue(player.getMainHandItem().getCount() == 5 && occupiedRewardSlots(player) == 0, "No payment or reward occurs before tick 56");
        advance(h, player, started, GemBoxItem.OPEN_TICKS);
        advance(h, player, started, GemBoxItem.OPEN_TICKS + 1);
        treasure.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getMainHandItem().getCount() == 4 && occupiedRewardSlots(player) == 1 && awarded(player, shown),
                "Exactly one payment delivers the displayed reward; repeated ticks and cooldown requests cannot duplicate it");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void finalTreasureKeepsItsReplacementAndCreativeStillPays(GameTestHelper h) {
        for (GameType type : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            var player = player(h, type);
            var treasure = WitherTreasureContent.TREASURE.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(treasure));
            var result = treasure.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
            long started = player.getMainHandItem().get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            advance(h, player, started, GemBoxItem.OPEN_TICKS);
            h.assertTrue(!player.getMainHandItem().is(treasure), "Opening the last treasure consumes it in survival and creative");
            h.assertTrue(occupiedRewardSlots(player) == 1, "The final treasure's reward survives replacement of the selected slot");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void aFullInventoryDropsExactlyOneRewardWithoutLosingTheTreasurePayment(GameTestHelper h) {
        for (GameType type : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            var player = player(h, type);
            BlockPos pos = h.absolutePos(new BlockPos(4, 3, 4));
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            var treasure = WitherTreasureContent.TREASURE.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(treasure, 5));
            var area = new AABB(pos).inflate(3);
            var before = h.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            treasure.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            long started = player.getMainHandItem().get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            advance(h, player, started, GemBoxItem.REVEAL_TICK);
            ItemStack shown = GemBoxItem.revealedGem(player.getMainHandItem());
            advance(h, player, started, GemBoxItem.OPEN_TICKS);
            advance(h, player, started, GemBoxItem.OPEN_TICKS + 1);
            var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().filter(entity -> !before.contains(entity.getUUID())).toList();
            h.assertTrue(player.getMainHandItem().getCount() == 4, "A full survival or creative inventory still pays exactly one treasure");
            h.assertTrue(dropped.size() == 1 && ItemStack.isSameItemSameComponents(dropped.getFirst().getItem(), shown)
                    && dropped.getFirst().getItem().getCount() == shown.getCount(), "A full survival or creative inventory drops exactly the displayed reward stack beside the player");
            dropped.forEach(ItemEntity::discard);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void configuredCapsAndDisabledEnchantmentsControlNewBooks(GameTestHelper h) {
        var previousDisabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        var previousCaps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        var previousSnapshot = MiteConfig.effectiveSnapshot();
        boolean remote = previousSnapshot != MiteConfig.localSnapshot();
        try {
            MiteConfig.clearServerSnapshot();
            var enchantments = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                    .map(holder -> holder.key().location().toString()).toList();
            MiteConfig.DISABLED_ENCHANTMENTS.set(enchantments.stream().filter(id -> !id.equals("modern_ite:crit")).toList());
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of("modern_ite:crit=7"));
            MiteConfig.refreshEnchantmentRules();
            ItemStack book = WitherTreasureItem.createReward(h.getLevel(), RandomSource.create(2), WitherTreasureRewards.Reward.ENCHANTED_BOOK);
            var stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            h.assertTrue(stored.size() == 1, "The only enabled enchantment remains obtainable");
            for (var entry : stored.entrySet()) h.assertTrue(entry.getKey().unwrapKey().orElseThrow().location().toString().equals("modern_ite:crit")
                    && entry.getIntValue() == 7, "New books respect a modded enchantment's configured maximum");
            MiteConfig.DISABLED_ENCHANTMENTS.set(enchantments);
            MiteConfig.refreshEnchantmentRules();
            var player = player(h, GameType.SURVIVAL);
            var treasure = WitherTreasureContent.TREASURE.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(treasure, 5));
            treasure.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            long started = player.getMainHandItem().get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            player.getRandom().setSeed(rewardSeed(85, 100));
            advance(h, player, started, GemBoxItem.REVEAL_TICK);
            h.assertTrue(player.getMainHandItem().getCount() == 5 && occupiedRewardSlots(player) == 0
                    && !player.getCooldowns().isOnCooldown(treasure) && !player.getMainHandItem().has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()),
                    "An empty book pool cancels the opening without taking payment or awarding an empty book");
        } finally {
            MiteConfig.DISABLED_ENCHANTMENTS.set(previousDisabled);
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(previousCaps);
            MiteConfig.refreshEnchantmentRules();
            if (remote) MiteConfig.receiveServerSnapshot(previousSnapshot);
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void stoppingBeforeRevealCancelsButMovingAfterRevealSettlesExactlyOnce(GameTestHelper h) {
        var treasure = WitherTreasureContent.TREASURE.get();
        for (boolean revealed : List.of(false, true)) {
            var player = player(h, GameType.SURVIVAL);
            ItemStack stack = new ItemStack(treasure, 5);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            treasure.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            long started = stack.get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            advance(h, player, started, revealed ? GemBoxItem.REVEAL_TICK : GemBoxItem.REVEAL_TICK - 1);
            ItemStack shown = GemBoxItem.revealedGem(stack);
            // Exercise the native container hook before it moves the paying stack to the cursor.
            player.inventoryMenu.clicked(36, 0, ClickType.PICKUP, player);
            h.assertTrue(player.inventoryMenu.getCarried().is(treasure) && player.inventoryMenu.getCarried().getCount() == (revealed ? 4 : 5),
                    "An inventory move pays only after the actual result has been revealed");
            h.assertTrue(occupiedRewardSlots(player) == (revealed ? 1 : 0) && (!revealed || awarded(player, shown)),
                    "A revealed result settles precisely once before the stack can split");
            GemBoxItem.interrupt(player);
            advance(h, player, started, GemBoxItem.OPEN_TICKS + 1);
            h.assertTrue(occupiedRewardSlots(player) == (revealed ? 1 : 0), "Later ticks or repeated interruption cannot replay a moved opening");
            h.assertTrue(!stack.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()) && !stack.has(ExtremeGear.GEM_BOX_REVEALED_REWARD.get()),
                    "Moving clears all transient animation data");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void ordinaryAndWitherBoxesShareOneOpeningPerPlayer(GameTestHelper h) {
        for (boolean treasureFirst : List.of(false, true)) {
            var player = player(h, GameType.SURVIVAL);
            GemBoxItem first = treasureFirst ? WitherTreasureContent.TREASURE.get() : ExtremeGear.GEM_BOX.get();
            GemBoxItem second = treasureFirst ? ExtremeGear.GEM_BOX.get() : WitherTreasureContent.TREASURE.get();
            ItemStack paying = new ItemStack(first, 2), waiting = new ItemStack(second, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, paying);
            player.setItemInHand(InteractionHand.OFF_HAND, waiting);
            first.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            long started = paying.get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            second.use(h.getLevel(), player, InteractionHand.OFF_HAND);
            h.assertTrue(!waiting.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()), "A different box in the other hand cannot start a parallel opening");
            advance(h, player, started, GemBoxItem.REVEAL_TICK);
            ItemStack shown = GemBoxItem.revealedGem(paying);
            second.use(h.getLevel(), player, InteractionHand.OFF_HAND);
            h.assertTrue(!waiting.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()) && ItemStack.isSameItemSameComponents(shown, GemBoxItem.revealedGem(paying)),
                    "A second box cannot replace or reroll the already displayed reward");
            advance(h, player, started, GemBoxItem.OPEN_TICKS);
            h.assertTrue(paying.getCount() == 1 && waiting.getCount() == 2 && occupiedRewardSlots(player) == 1 && awarded(player, shown),
                    "Only the first box pays for one reward");
        }
        var creative = player(h, GameType.CREATIVE);
        ItemStack ordinary = new ItemStack(ExtremeGear.GEM_BOX.get(), 2);
        creative.setItemInHand(InteractionHand.MAIN_HAND, ordinary);
        ExtremeGear.GEM_BOX.get().use(h.getLevel(), creative, InteractionHand.MAIN_HAND);
        advance(h, creative, ordinary.get(ExtremeGear.GEM_BOX_OPEN_STARTED.get()), GemBoxItem.OPEN_TICKS);
        h.assertTrue(ordinary.getCount() == 2 && occupiedRewardSlots(creative) == 1, "Ordinary gem boxes retain their original creative exemption");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void revealedGemDiamondAndBookStacksSurvivePreviewAndSettleWithoutReroll(GameTestHelper h) {
        for (int[] tickets : List.of(new int[]{0, 65}, new int[]{65, 85}, new int[]{85, 100})) {
            var player = player(h, GameType.SURVIVAL);
            var treasure = WitherTreasureContent.TREASURE.get();
            ItemStack stack = new ItemStack(treasure, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            treasure.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            long started = stack.get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            player.getRandom().setSeed(rewardSeed(tickets[0], tickets[1]));
            advance(h, player, started, GemBoxItem.REVEAL_TICK);
            ItemStack shown = GemBoxItem.revealedGem(stack);
            h.assertTrue(shown.getItem() instanceof EnhanceGemItem || shown.is(Items.DIAMOND) || shown.is(Items.ENCHANTED_BOOK),
                    "The preview is the actual registered gem, diamond stack or enchanted book");
            if (tickets[0] == 65) h.assertTrue(shown.is(Items.DIAMOND) && shown.getCount() >= 10 && shown.getCount() <= 20, "The diamond preview preserves its complete amount");
            if (tickets[0] == 85) h.assertTrue(shown.is(Items.ENCHANTED_BOOK) && shown.has(DataComponents.STORED_ENCHANTMENTS), "The book preview carries the actual selected enchantment");
            ItemStack saved = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(!saved.has(ExtremeGear.GEM_BOX_REVEALED_REWARD.get()) && !saved.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()),
                    "Saving cannot persist a pending reward or resume an abandoned animation");
            ItemStack previewCopy = GemBoxItem.revealedGem(stack);
            previewCopy.setCount(1);
            previewCopy.remove(DataComponents.STORED_ENCHANTMENTS);
            h.assertTrue(ItemStack.isSameItemSameComponents(shown, GemBoxItem.revealedGem(stack)) && shown.getCount() == GemBoxItem.revealedGem(stack).getCount(),
                    "Inspecting or rendering the preview cannot mutate the server's locked reward");
            GemBoxItem.interrupt(player);
            GemBoxItem.interrupt(player);
            advance(h, player, started, GemBoxItem.OPEN_TICKS);
            h.assertTrue(stack.getCount() == 1 && occupiedRewardSlots(player) == 1 && awarded(player, shown), "Stopping after reveal pays once and preserves the complete visible stack");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void dropDeathDimensionLogoutAndCloneSettleRevealedTreasureOnce(GameTestHelper h) {
        for (int action = 0; action < 5; action++) {
            var original = player(h, GameType.CREATIVE);
            var treasure = WitherTreasureContent.TREASURE.get();
            ItemStack stack = new ItemStack(treasure, 2);
            original.setItemInHand(InteractionHand.MAIN_HAND, stack);
            treasure.use(h.getLevel(), original, InteractionHand.MAIN_HAND);
            long started = stack.get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            advance(h, original, started, GemBoxItem.REVEAL_TICK);
            ItemStack shown = GemBoxItem.revealedGem(stack);
            Player recipient = original;
            switch (action) {
                case 0 -> treasure.onDroppedByPlayer(stack, original);
                case 1 -> GemBoxItem.death(new LivingDeathEvent(original, original.damageSources().generic()));
                case 2 -> GemBoxItem.dimension(new PlayerEvent.PlayerChangedDimensionEvent(original,
                        net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER));
                case 3 -> GemBoxItem.logout(new PlayerEvent.PlayerLoggedOutEvent(original));
                case 4 -> {
                    recipient = player(h, GameType.CREATIVE);
                    recipient.getInventory().replaceWith(original.getInventory());
                    GemBoxItem.clone(new PlayerEvent.Clone(recipient, original, false));
                    h.assertTrue(occupiedRewardSlots(original) == 0, "The cloned result is never awarded to the retired player");
                }
            }
            GemBoxItem.interrupt(original);
            GemBoxItem.interrupt(recipient);
            advance(h, recipient, started, GemBoxItem.OPEN_TICKS + 1);
            h.assertTrue(recipient.getInventory().countItem(treasure) == 1 && occupiedRewardSlots(recipient) == 1 && awarded(recipient, shown),
                    "Every interruption path pays one treasure even in creative and delivers exactly the locked reward once");
            h.assertTrue(!stack.has(ExtremeGear.GEM_BOX_REVEALED_REWARD.get()) && !stack.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()),
                    "Interruption clears preview data on the paying stack");
        }
        h.succeed();
    }

    private static Player player(GameTestHelper h, GameType type) {
        Player player = h.makeMockPlayer(type);
        type.updatePlayerAbilities(player.getAbilities());
        player.getInventory().clearContent();
        return player;
    }

    private static void advance(GameTestHelper h, Player player, long started, int elapsed) {
        long previous = h.getLevel().getGameTime();
        var data = (ServerLevelData) h.getLevel().getLevelData();
        try {
            data.setGameTime(started + elapsed);
            GemBoxItem.tick(player);
        } finally { data.setGameTime(previous); }
    }

    private static long rewardSeed(int first, int end) {
        long seed = 0;
        for (;;) {
            int ticket = RandomSource.create(seed).nextInt(100);
            if (ticket >= first && ticket < end) return seed;
            seed++;
        }
    }

    private static boolean awarded(Player player, ItemStack selected) {
        return player.getInventory().items.stream().anyMatch(stack -> !stack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, selected) && stack.getCount() == selected.getCount());
    }

    private static int occupiedRewardSlots(net.minecraft.world.entity.player.Player player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !(stack.getItem() instanceof GemBoxItem)) count++;
        }
        return count;
    }
}
