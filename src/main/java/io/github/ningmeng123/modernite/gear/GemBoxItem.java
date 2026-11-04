package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.modernite.ModernIte;
import com.google.common.collect.MapMaker;
import java.util.List;
import java.util.Map;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** One click plays a complete lid opening before one authoritative reward. @author NingMeng123 */
@EventBusSubscriber(modid = ModernIte.MOD_ID)
public class GemBoxItem extends Item {
    public static final int OPEN_TICKS = 56;
    public static final int REVEAL_TICK = 36;
    private static final Map<Player, Opening> OPENINGS = new MapMaker().weakKeys().makeMap();
    private static final class Opening {
        final ItemStack stack;
        final InteractionHand hand;
        final Level level;
        final long started;
        final GemBoxItem item;
        ItemStack reward;
        int rummageStage = -1;
        Opening(ItemStack stack, InteractionHand hand, Level level, long started, GemBoxItem item) {
            this.stack = stack; this.hand = hand; this.level = level; this.started = started; this.item = item;
        }
        ItemStack stack() { return stack; }
        InteractionHand hand() { return hand; }
        Level level() { return level; }
        long started() { return started; }
    }

    public GemBoxItem(Properties properties) { super(properties); }

    protected ItemStack createOpeningReward(Player player) {
        return ExtremeGear.gem(GemType.values()[player.getRandom().nextInt(GemType.values().length)], 1);
    }

    protected boolean consumesInCreative() { return false; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(this) || player.isSpectator() || !player.isAlive()) return InteractionResultHolder.fail(held);
        if (level.isClientSide) return InteractionResultHolder.consume(held);
        if (OPENINGS.containsKey(player)) return InteractionResultHolder.consume(held);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(held);
        if (!level.isClientSide) {
            long started = level.getGameTime();
            clearVisuals(held);
            held.set(ExtremeGear.GEM_BOX_OPEN_STARTED.get(), started);
            OPENINGS.put(player, new Opening(held, hand, level, started, this));
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
        ItemContainerContents reward = stack.get(ExtremeGear.GEM_BOX_REVEALED_REWARD.get());
        if (reward != null) return reward.copyOne();
        int selected = stack.getOrDefault(ExtremeGear.GEM_BOX_REVEALED_GEM.get(), 0);
        return selected < 1 || selected > GemType.values().length ? ItemStack.EMPTY
                : ExtremeGear.gem(GemType.values()[selected - 1], 1);
    }

    public static void setRevealedReward(ItemStack box, ItemStack reward) {
        if (reward.isEmpty()) box.remove(ExtremeGear.GEM_BOX_REVEALED_REWARD.get());
        else box.set(ExtremeGear.GEM_BOX_REVEALED_REWARD.get(), ItemContainerContents.fromItems(List.of(reward)));
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
            opening.reward = opening.item.createOpeningReward(player);
            if (opening.reward.isEmpty()) {
                cancel(player);
                player.getCooldowns().removeCooldown(opening.item);
                return;
            }
            setRevealedReward(opening.stack, opening.reward);
            if (opening.reward.getItem() instanceof EnhanceGemItem gem)
                opening.stack.set(ExtremeGear.GEM_BOX_REVEALED_GEM.get(), gem.type.ordinal() + 1);
            player.getInventory().setChanged();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ExtremeGear.GEM_BOX_REVEAL.get(), SoundSource.PLAYERS, .75F, 1F);
        }
        if (elapsed < OPEN_TICKS) return;
        complete(player, opening);
    }

    private static void complete(Player player, Opening opening) {
        if (OPENINGS.get(player) != opening) return;
        cancel(player);
        if (opening.reward == null || opening.reward.isEmpty() || opening.stack.isEmpty()) return;
        if (!player.hasInfiniteMaterials() || opening.item.consumesInCreative()) opening.stack.shrink(1);
        award(player, opening.reward);
    }

    private static void award(Player player, ItemStack selected) {
        ItemStack reward = selected.copy();
        if (!io.github.ningmeng123.mite.crafting.TimedCraftingController.insertWithoutDiscard(player.getInventory(), reward))
            player.drop(reward, false);
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
            clearVisuals(opening.stack());
            player.getInventory().setChanged();
        }
    }

    @Override public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        if (!player.level().isClientSide) {
            Opening opening = OPENINGS.get(player);
            if (opening != null && opening.stack() == stack) interrupt(player);
        }
        clearVisuals(stack);
        return true;
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && (stack.has(ExtremeGear.GEM_BOX_OPEN_STARTED.get())
                || stack.has(ExtremeGear.GEM_BOX_REVEALED_REWARD.get()) || stack.has(ExtremeGear.GEM_BOX_REVEALED_GEM.get()))) {
            Opening opening = OPENINGS.get(player);
            if (opening == null || opening.stack() != stack) {
                clearVisuals(stack);
                player.getInventory().setChanged();
            }
        }
    }

    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || !ItemStack.isSameItem(oldStack, newStack)) return true;
        ItemStack previous = oldStack.copy(), next = newStack.copy();
        clearVisuals(previous);
        clearVisuals(next);
        return !ItemStack.isSameItemSameComponents(previous, next);
    }

    private static void clearVisuals(ItemStack stack) {
        stack.remove(ExtremeGear.GEM_BOX_OPEN_STARTED.get());
        stack.remove(ExtremeGear.GEM_BOX_REVEALED_GEM.get());
        stack.remove(ExtremeGear.GEM_BOX_REVEALED_REWARD.get());
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
            if (!stack.is(opening.item) || !Long.valueOf(opening.started).equals(stack.get(ExtremeGear.GEM_BOX_OPEN_STARTED.get()))) continue;
            if (payment.isEmpty()) payment = stack;
            clearVisuals(stack);
        }
        // Inventory.replaceWith transfers stack references, so locate payment before clearing old state.
        cancel(event.getOriginal());
        cancel(replacement);
        if (opening.reward != null && !opening.reward.isEmpty() && !payment.isEmpty()) {
            if (!replacement.hasInfiniteMaterials() || opening.item.consumesInCreative()) payment.shrink(1);
            award(replacement, opening.reward);
        }
        replacement.getInventory().setChanged();
    }
}
