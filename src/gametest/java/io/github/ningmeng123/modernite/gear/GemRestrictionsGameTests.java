package io.github.ningmeng123.modernite.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Socket policy is enforced at both menu ends and at the authoritative inventory. @author NingMeng123 */
@GameTestHolder("modern_ite_feedback")
@PrefixGameTestTemplate(false)
public final class GemRestrictionsGameTests {
    private GemRestrictionsGameTests() {}

    @GameTest(template = "gear_empty")
    public static void allGemTypesRespectArmorPartsThroughBothMenusAndDirectInsertion(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, ExtremeGear.GEM_SETTING.get().defaultBlockState());
        var station = (GearStationEntity) h.getLevel().getBlockEntity(pos);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var server = new GearStationMenu(1, player.getInventory(), station, station.data, true);
        var projected = new SimpleContainer(9);
        var client = new GearStationMenu(2, player.getInventory(), projected, new SimpleContainerData(5), true);
        ItemStack[] gear = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)};
        // Explicit source-family expectations: tools, tools, head, chest, legs, feet.
        String[] allowed = {"damage,effect,durable", "damage,effect,durable", "health,protection,recover,durable,nutrition",
                "health,protection,recover,durable,fireproof,explosionproof,nutrition",
                "health,protection,recover,durable,fireproof,explosionproof,nutrition",
                "health,protection,recover,durable,speed,nutrition"};
        for (int i = 0; i < gear.length; i++) {
            station.setItem(0, gear[i]); projected.setItem(0, gear[i].copy());
            for (GemType type : GemType.values()) for (int rank = 1; rank <= 6; rank++) {
                ItemStack gem = ExtremeGear.gem(type, rank);
                boolean expected = List.of(allowed[i].split(",")).contains(type.id);
                for (int slot = 1; slot <= 8; slot++) {
                    h.assertTrue(station.canPlaceItem(slot, gem) == expected
                                    && server.getSlot(slot).mayPlace(gem) == expected
                                    && client.getSlot(slot).mayPlace(gem) == expected,
                            "Both menu projections and server policy agree for " + gear[i] + " / " + type);
                    station.setItem(slot, gem);
                    h.assertTrue(station.getItem(slot).isEmpty() != expected, "Direct writes cannot bypass socket restrictions");
                    station.setItem(slot, ItemStack.EMPTY);
                }
                player.getInventory().setItem(9, gem.copy());
                ItemStack moved = server.quickMoveStack(player, 9);
                h.assertTrue(moved.isEmpty() != expected, "Shift-click follows the same authoritative rule");
                if (expected) station.removeItem(1, 1);
                player.getInventory().clearContent();
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void historicalMisplacedGemsStaySavedAndCanBeTakenOutButCannotBeReinserted(GameTestHelper h) {
        var station = new GearStationEntity(BlockPos.ZERO, ExtremeGear.GEM_SETTING.get().defaultBlockState());
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        boots.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, GemType.FIREPROOF.ordinal() * 6 + 6));
        boots = ItemStack.parse(h.getLevel().registryAccess(), boots.save(h.getLevel().registryAccess())).orElseThrow();
        station.setItem(0, boots);
        h.assertTrue(ExtremeGear.rank(boots, GemType.FIREPROOF) == 6 && ExtremeGear.activeRank(boots, GemType.FIREPROOF) == 0,
                "The stored historical gem survives transport without an invalid effect");
        ItemStack recovered = station.removeItem(1, 1);
        h.assertTrue(recovered.is(ExtremeGear.gem(GemType.FIREPROOF, 6).getItem())
                        && ExtremeGear.rank(boots, GemType.FIREPROOF) == 0 && !station.canPlaceItem(1, recovered),
                "A legacy wrong-slot gem is removable once and cannot be inserted again");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void chestAndLegResistanceBothWorkAndStackWithoutNegativeDamage(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (GemType type : List.of(GemType.FIREPROOF, GemType.EXPLOSION_PROOF)) {
            for (EquipmentSlot slot : List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                ItemStack gear = new ItemStack(slot == EquipmentSlot.CHEST ? Items.IRON_CHESTPLATE : Items.IRON_LEGGINGS);
                gear.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, type.ordinal() * 6 + 6));
                player.setItemSlot(slot, gear);
                var source = type == GemType.FIREPROOF ? player.damageSources().inFire() : player.damageSources().explosion(null, null);
                var event = new LivingIncomingDamageEvent(player, new DamageContainer(source, 16F));
                GearEvents.incoming(event);
                h.assertTrue(Math.abs(event.getAmount() - 4F) < .00001F, "Either eligible armor part applies its rank-six resistance");
            }
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
            chest.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, type.ordinal() * 6 + 6));
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            var source = type == GemType.FIREPROOF ? player.damageSources().inFire() : player.damageSources().explosion(null, null);
            var event = new LivingIncomingDamageEvent(player, new DamageContainer(source, 16F));
            GearEvents.incoming(event);
            h.assertTrue(Math.abs(event.getAmount() - 1F) < .00001F, "Two rank-six pieces compose reductions and retain positive damage");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void gemBoxUseConsumesOnceAwardsOneGemAndBroadcastsOneOpeningSound(GameTestHelper h) {
        List<Player> players = new ArrayList<>();
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            var player = h.makeMockPlayer(mode);
            players.add(player);
            player.setPos(h.absolutePos(new BlockPos(2, 2, 2)).getCenter());
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExtremeGear.GEM_BOX.get(), 2));
            List<SoundEvent> sounds = new ArrayList<>();
            List<Float> pitches = new ArrayList<>();
            Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
                if (event.getLevel() == h.getLevel() && event.getPosition().equals(player.position()) && event.getSound() != null) {
                    sounds.add(event.getSound().value());
                    pitches.add(event.getOriginalPitch());
                }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                ExtremeGear.GEM_BOX.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
                ExtremeGear.GEM_BOX.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            h.assertTrue(player.getMainHandItem().getCount() == 2 && rewards(h, player) == 0,
                    "A single click starts the animation without consuming the box or rewarding early");
            h.assertTrue(player.getMainHandItem().has(ExtremeGear.GEM_BOX_OPEN_STARTED.get())
                            && sounds.equals(List.of(ExtremeGear.GEM_BOX_OPEN.get())),
                    "Repeated requests neither restart the opening nor replay its sound");
            h.assertTrue(pitches.equals(List.of(1F)), "The supplied crisp opening sound keeps its original pitch");
        }
        h.runAfterDelay(GemBoxItem.OPEN_TICKS - 1, () -> {
            for (Player player : players) {
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                h.assertTrue(player.getMainHandItem().getCount() == 2 && rewards(h, player) == 0,
                        "No box is charged and no gem is granted before the final animation tick");
            }
        });
        h.runAfterDelay(GemBoxItem.OPEN_TICKS, () -> {
            for (Player player : players) {
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                h.assertTrue(player.getMainHandItem().getCount() == (player.hasInfiniteMaterials() ? 2 : 1)
                                && rewards(h, player) == 1 && !player.getMainHandItem().has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()),
                        "Exactly one authoritative reward follows the complete opening, with native creative consumption");
            }
            h.succeed();
        });
    }

    @GameTest(template = "gear_empty")
    public static void switchingOrDroppingAClickedBoxCancelsWithoutChargingOrGrantingAGem(GameTestHelper h) {
        List<Player> players = new ArrayList<>();
        for (boolean dropped : List.of(false, true)) {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            players.add(player);
            ItemStack box = new ItemStack(ExtremeGear.GEM_BOX.get(), 2);
            player.setItemInHand(InteractionHand.OFF_HAND, box);
            ExtremeGear.GEM_BOX.get().use(h.getLevel(), player, InteractionHand.OFF_HAND);
            ItemStack orphan = box.copy();
            ItemStack saved = ItemStack.parse(h.getLevel().registryAccess(), box.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertFalse(saved.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()), "An in-progress opening is transient and cannot become a saved pending reward");
            if (dropped) ExtremeGear.GEM_BOX.get().onDroppedByPlayer(box, player);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            h.assertTrue(box.getCount() == 2 && !box.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()),
                    "Switching or dropping removes the synchronized animation without charging a box");
            ExtremeGear.GEM_BOX.get().inventoryTick(orphan, h.getLevel(), player, 1, false);
            h.assertFalse(orphan.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get()), "Split or transferred visual markers cannot resume without server ownership");
            player.getInventory().setItem(1, box);
        }
        h.runAfterDelay(GemBoxItem.OPEN_TICKS + 1, () -> {
            for (Player player : players) {
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                h.assertTrue(player.getInventory().countItem(ExtremeGear.GEM_BOX.get()) == 2 && rewards(h, player) == 0,
                        "An interrupted opening never replays, consumes or duplicates a reward later");
            }
            h.succeed();
        });
    }

    private static int rewards(GameTestHelper h, Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.getItem() instanceof EnhanceGemItem gem) {
            count += stack.getCount();
            h.assertValueEqual(gem.rank, 1, "Opening still rewards a rank-one gem");
        }
        return count;
    }

    @GameTest(template = "gear_empty")
    public static void revealedGemMatchesTheAwardAndMovingTheBoxSettlesThatResultExactlyOnce(GameTestHelper h) {
        List<Player> players = new ArrayList<>();
        ItemStack[] selected = {ItemStack.EMPTY, ItemStack.EMPTY};
        for (int index = 0; index < 2; index++) {
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(h.absolutePos(new BlockPos(1 + index, 2, 2)).getCenter());
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExtremeGear.GEM_BOX.get(), 2));
            ExtremeGear.GEM_BOX.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            players.add(player);
        }
        for (int tick : List.of(16, 26)) h.runAfterDelay(tick, () -> {
            List<SoundEvent> sounds = soundsFromTick(h, players.getFirst());
            h.assertTrue(sounds.equals(List.of(ExtremeGear.GEM_BOX_RUMMAGE.get())), "Each search stroke plays one soft rummaging cue");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(players.getLast()));
            h.assertTrue(GemBoxItem.revealedGem(players.getFirst().getMainHandItem()).isEmpty(), "Rummaging does not reveal a placeholder or select a reward early");
        });
        h.runAfterDelay(GemBoxItem.REVEAL_TICK, () -> {
            h.assertTrue(soundsFromTick(h, players.getFirst()).equals(List.of(ExtremeGear.GEM_BOX_REVEAL.get())), "Drawing the selected gem plays its crisp reveal cue once");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(players.getLast()));
            for (int index = 0; index < 2; index++) {
                Player player = players.get(index);
                selected[index] = GemBoxItem.revealedGem(player.getMainHandItem());
                h.assertTrue(!selected[index].isEmpty() && rewards(h, player) == 0, "The hand displays the actual selected gem before final delivery");
            }
            Player interrupted = players.getLast();
            // Use the real native inventory mutation, not a direct call to the interruption helper.
            interrupted.inventoryMenu.clicked(36, 0, ClickType.PICKUP, interrupted);
            h.assertTrue(rewards(h, interrupted) == 1 && awarded(interrupted, selected[1])
                            && interrupted.inventoryMenu.getCarried().is(ExtremeGear.GEM_BOX.get())
                            && interrupted.inventoryMenu.getCarried().getCount() == 1,
                    "Moving the revealed box pays exactly one box and delivers precisely its locked result before splitting the stack");
        });
        h.runAfterDelay(GemBoxItem.OPEN_TICKS, () -> {
            for (int index = 0; index < 2; index++) {
                Player player = players.get(index);
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                h.assertTrue(rewards(h, player) == 1 && awarded(player, selected[index]), "Completion and repeated ticks cannot reroll or duplicate the actual displayed gem");
            }
            h.succeed();
        });
    }

    private static boolean awarded(Player player, ItemStack selected) {
        return player.getInventory().items.stream().anyMatch(stack -> !stack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, selected) && stack.getCount() == 1);
    }

    @GameTest(template = "gear_empty")
    public static void endReturnCloneSettlesTheDisplayedGemInTheAlreadyCopiedInventory(GameTestHelper h) {
        Player original = h.makeMockPlayer(GameType.SURVIVAL);
        original.getInventory().clearContent();
        original.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExtremeGear.GEM_BOX.get(), 2));
        ExtremeGear.GEM_BOX.get().use(h.getLevel(), original, InteractionHand.MAIN_HAND);
        h.runAfterDelay(GemBoxItem.REVEAL_TICK, () -> {
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(original));
            ItemStack chosen = GemBoxItem.revealedGem(original.getMainHandItem());
            h.assertFalse(chosen.isEmpty(), "The old player has already shown the locked result");
            Player replacement = h.makeMockPlayer(GameType.SURVIVAL);
            replacement.getInventory().replaceWith(original.getInventory());
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, false));
            h.assertTrue(replacement.getInventory().countItem(ExtremeGear.GEM_BOX.get()) == 1
                            && rewards(h, replacement) == 1 && awarded(replacement, chosen),
                    "The End return consumes one copied box and delivers exactly its displayed result to the replacement player");
            h.assertTrue(rewards(h, original) == 0 && !replacement.getMainHandItem().has(ExtremeGear.GEM_BOX_OPEN_STARTED.get())
                            && !replacement.getMainHandItem().has(ExtremeGear.GEM_BOX_REVEALED_GEM.get()),
                    "Nothing is rewarded to the retired player and copied visual state is cleared");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(original));
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(replacement));
            h.assertValueEqual(rewards(h, replacement), 1, "Later ticks cannot duplicate the cloned pending result");
            h.succeed();
        });
    }

    private static List<SoundEvent> soundsFromTick(GameTestHelper h, Player player) {
        List<SoundEvent> sounds = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == h.getLevel() && event.getPosition().equals(player.position()) && event.getSound() != null)
                sounds.add(event.getSound().value());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try { NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)); }
        finally { NeoForge.EVENT_BUS.unregister(listener); }
        return sounds;
    }
}
