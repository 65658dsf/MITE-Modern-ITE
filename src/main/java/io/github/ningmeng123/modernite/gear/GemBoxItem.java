package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.modernite.ModernIte;
import com.google.common.collect.MapMaker;
import java.util.Map;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** One click plays a complete lid opening before one authoritative reward. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public final class GemBoxItem extends Item {
    public static final int OPEN_TICKS = 56;
    public static final int REVEAL_TICK = 36;
    private static final Map<Player, Opening> OPENINGS = new MapMaker().weakKeys().makeMap();
    private static final class Opening {
        final ItemStack stack;
        final InteractionHand hand;
        final Level level;
        final long started;
        GemType reward;
        int rummageStage = -1;
        Opening(ItemStack stack, InteractionHand hand, Level level, long started) {
            this.stack = stack; this.hand = hand; this.level = level; this.started = started;
        }
        ItemStack stack() { return stack; }
        InteractionHand hand() { return hand; }
        Level level() { return level; }
        long started() { return started; }
    }

    public GemBoxItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(this)) return InteractionResultHolder.fail(held);
        if (level.isClientSide) return InteractionResultHolder.consume(held);
        if (OPENINGS.containsKey(player)) return InteractionResultHolder.consume(held);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(held);
        if (!level.isClientSide) {
            long started = level.getGameTime();
            held.set(ExtremeGear.GEM_BOX_OPEN_STARTED.get(), started);
            OPENINGS.put(player, new Opening(held, hand, level, started));
            player.getCooldowns().addCooldown(this, OPEN_TICKS);
            player.getInventory().setChanged();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ExtremeGear.GEM_BOX_OPEN.get(),
                    SoundSource.PLAYERS, 1F, 1F);
        }
        // No use-key hold or native swing is required; releasing the mouse leaves this animation running.
        return InteractionResultHolder.consume(held);
    }

    public static float openingProgress(ItemStack stack, Level level, float partialTick) {
        Long started = stack.get(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
        return started == null ? 0F : Mth.clamp((level.getGameTime() - started + partialTick) / OPEN_TICKS, 0F, 1F);
    }

    public static ItemStack revealedGem(ItemStack stack) {
        int selected = stack.getOrDefault(ExtremeGear.GEM_BOX_REVEALED_GEM.get(), 0);
        return selected < 1 || selected > GemType.values().length ? ItemStack.EMPTY
                : ExtremeGear.gem(GemType.values()[selected - 1], 1);
    }

    public static void tick(Player player) {
        if (player.level().isClientSide) return;
        Opening opening = OPENINGS.get(player);
        if (opening == null) return;
        if (!player.isAlive() || player.isSpectator() || player.level() != opening.level()
                || player.getItemInHand(opening.hand()) != opening.stack() || player.isUsingItem() || player.swinging
                || !Long.valueOf(opening.started()).equals(opening.stack().get(ExtremeGear.GEM_BOX_OPEN_STARTED.get()))) {
            interrupt(player);
            return;
        }
        long elapsed = player.level().getGameTime() - opening.started();
        if (elapsed >= 16 && elapsed < REVEAL_TICK) {
            int stage = (int)(elapsed - 16) / 10;
            if (stage > opening.rummageStage) {
                opening.rummageStage = stage;
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ExtremeGear.GEM_BOX_RUMMAGE.get(), SoundSource.PLAYERS, .55F, 1F);
            }
        }
        if (elapsed >= REVEAL_TICK && opening.reward == null) {
            opening.reward = GemType.values()[player.getRandom().nextInt(GemType.values().length)];
            opening.stack.set(ExtremeGear.GEM_BOX_REVEALED_GEM.get(), opening.reward.ordinal() + 1);
            player.getInventory().setChanged();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ExtremeGear.GEM_BOX_REVEAL.get(), SoundSource.PLAYERS, .75F, 1F);
        }
        if (elapsed < OPEN_TICKS) return;
        complete(player, opening);
    }

    private static void complete(Player player, Opening opening) {
        cancel(player);
        if (opening.reward == null || opening.stack.isEmpty()) return;
        if (!player.hasInfiniteMaterials()) opening.stack.shrink(1);
        award(player, opening.reward);
    }

    private static void award(Player player, GemType selected) {
        ItemStack reward = ExtremeGear.gem(selected, 1);
        if (!player.getInventory().add(reward)) player.drop(reward, false);
        player.getInventory().setChanged();
    }

    /** Once visible, the chosen result settles on interruption so it cannot be previewed and rerolled for free. */
    public static void interrupt(Player player) {
        if (player.level().isClientSide) return;
        Opening opening = OPENINGS.get(player);
        if (opening != null && opening.reward != null) complete(player, opening);
        else cancel(player);
    }

    private static void cancel(Player player) {
        if (player.level().isClientSide) return;
        Opening opening = OPENINGS.remove(player);
        if (opening != null) {
            opening.stack().remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            opening.stack().remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
            player.getInventory().setChanged();
        }
    }

    @Override public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        if (!player.level().isClientSide) {
            Opening opening = OPENINGS.get(player);
            if (opening != null && opening.stack() == stack) interrupt(player);
        }
        stack.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
        stack.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
        return true;
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && stack.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get())) {
            Opening opening = OPENINGS.get(player);
            if (opening == null || opening.stack() != stack) {
                stack.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
                stack.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
                player.getInventory().setChanged();
            }
        }
    }

    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || !ItemStack.isSameItem(oldStack, newStack)) return true;
        ItemStack previous = oldStack.copy(), next = newStack.copy();
        previous.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
        next.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
        previous.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
        next.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
        return !ItemStack.isSameItemSameComponents(previous, next);
    }

    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event) { tick(event.getEntity()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof Player player) interrupt(player); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { interrupt(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { interrupt(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        Player replacement = event.getEntity();
        if (replacement.level().isClientSide) return;
        Opening opening = OPENINGS.get(event.getOriginal());
        if (opening == null) return;
        // End credits clone the inventory before this event, without the normal dimension/death callbacks.
        // Settle against that copied inventory, never against the retired player's now-discarded copy.
        ItemStack payment = ItemStack.EMPTY;
        for (int slot = 0; slot < replacement.getInventory().getContainerSize(); slot++) {
            ItemStack stack = replacement.getInventory().getItem(slot);
            if (!stack.is(ExtremeGear.GEM_BOX.get()) || !Long.valueOf(opening.started).equals(stack.get(ExtremeGear.GEM_BOX_OPEN_STARTED.get()))) continue;
            if (payment.isEmpty()) payment = stack;
            stack.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
            stack.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
        }
        // Inventory.replaceWith transfers stack references, so locate payment before clearing old state.
        cancel(event.getOriginal());
        cancel(replacement);
        if (opening.reward != null && !payment.isEmpty()) {
            if (!replacement.hasInfiniteMaterials()) payment.shrink(1);
            award(replacement, opening.reward);
        }
        replacement.getInventory().setChanged();
    }
}
