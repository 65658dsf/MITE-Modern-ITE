package io.github.ningmeng123.modernite.advancement;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import io.github.ningmeng123.modernite.entity.ExtremeMobs;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Checks the loaded legacy advancements using server players and native gameplay triggers. @author NingMeng123 */
@GameTestHolder("modern_ite_legacy_advancements")
@PrefixGameTestTemplate(false)
public final class LegacyAdvancementGameTests {
    @GameTest(template = "gear_empty")
    public static void craftingAwardsOnlyAfterTakingEachRecipeOutput(GameTestHelper helper) {
        try (var session = new Session(helper, "ite-crafter")) {
            var player = session.player;
            var level = helper.getLevel();
            for (String recipeId : List.of("mite:vibranium_ingot", "mite:vibranium_anvil", "modern_ite:vibranium_furnace",
                    "modern_ite:resurrection_array", "modern_ite:gem_setting")) {
                var id = ResourceLocation.parse(recipeId);
                var holder = level.getRecipeManager().byKey(id).orElseThrow();
                var recipe = (ShapedRecipe)holder.value();
                var grid = new TransientCraftingContainer(player.inventoryMenu, recipe.getWidth(), recipe.getHeight());
                for (int i = 0; i < recipe.getIngredients().size(); i++) {
                    var ingredient = recipe.getIngredients().get(i);
                    if (!ingredient.isEmpty()) grid.setItem(i, ingredient.getItems()[0].copy());
                }
                var input = grid.asCraftInput();
                helper.assertTrue(recipe.matches(input, level), "Valid crafting grid for " + recipeId);
                var output = recipe.assemble(input, level.registryAccess());
                player.getInventory().add(output.copy());
                CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), output);
                CriteriaTriggers.TICK.trigger(player);
                helper.assertFalse(earned(player, id.getPath()), "Possessing or previewing " + recipeId + " cannot earn it");
                var result = new ResultContainer();
                result.setRecipeUsed(holder);
                result.setItem(0, output);
                var slot = new ResultSlot(player, grid, result, 0, 0, 0);
                slot.onTake(player, slot.remove(output.getCount()));
                helper.assertTrue(earned(player, id.getPath()), "Taking the crafted " + recipeId + " earns it");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void invincibleRequiresAllFourArmorSlots(GameTestHelper helper) {
        try (var session = new Session(helper, "ite-armorer")) {
            var player = session.player;
            var helmet = stack("mite:vibranium_helmet");
            player.setItemInHand(InteractionHand.MAIN_HAND, helmet.copy());
            player.setItemSlot(EquipmentSlot.CHEST, stack("mite:vibranium_chestplate"));
            player.setItemSlot(EquipmentSlot.LEGS, stack("mite:vibranium_leggings"));
            player.setItemSlot(EquipmentSlot.FEET, stack("mite:vibranium_boots"));
            player.setItemSlot(EquipmentSlot.HEAD, stack("mite:adamantium_helmet"));
            CriteriaTriggers.TICK.trigger(player);
            helper.assertFalse(earned(player, "vibranium_armor"), "Holding the fourth piece cannot complete a mixed suit");
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            CriteriaTriggers.TICK.trigger(player);
            helper.assertTrue(earned(player, "vibranium_armor"), "Wearing four vibranium pieces completes the challenge");
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void fortuneRequiresTheVoucherAsFishingLoot(GameTestHelper helper) {
        try (var session = new Session(helper, "ite-angler")) {
            var player = session.player;
            var rod = new ItemStack(Items.FISHING_ROD);
            var voucher = stack("modern_ite:voucher_fishing");
            var dropped = helper.spawnItem(voucher.getItem(), 1, 1, 1);
            var snag = new FishingHook(player, helper.getLevel(), 0, 0) {
                @Override public Entity getHookedIn() { return dropped; }
            };
            CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, snag, List.of());
            helper.assertFalse(earned(player, "fish_fortune"), "Snagging a discarded voucher does not count as fishing one up");
            var hook = new FishingHook(player, helper.getLevel(), 0, 0);
            CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, List.of(new ItemStack(Items.COD)));
            helper.assertFalse(earned(player, "fish_fortune"), "An ordinary catch cannot grant the voucher achievement");
            CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, List.of(voucher));
            helper.assertTrue(earned(player, "fish_fortune"), "The voucher in the actual catch earns the achievement");
            hook.discard();
            snag.discard();
            dropped.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void emptyHandBatRideAndSharedBossKill(GameTestHelper helper) {
        try (var first = new Session(helper, "ite-helper"); var last = new Session(helper, "ite-finisher");
             var bystander = new Session(helper, "ite-witness")) {
            var bat = helper.spawn(EntityType.BAT, 1, 2, 1);
            first.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            first.player.interactOn(bat, InteractionHand.MAIN_HAND);
            helper.assertFalse(earned(first.player, "ride_bat"), "An occupied hand cannot earn the bat ride");
            first.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            first.player.interactOn(bat, InteractionHand.MAIN_HAND);
            helper.assertTrue(first.player.getVehicle() == bat && earned(first.player, "ride_bat"), "A successful bat mount earns it");
            first.player.stopRiding();
            bat.discard();

            var boss = ExtremeMobs.type(ExtremeKind.ZOMBIE_BOSS).create(helper.getLevel());
            boss.moveTo(first.player.position());
            boss.setHealth(10F);
            boss.hurt(boss.damageSources().playerAttack(first.player), 10F);
            boss.invulnerableTime = 0;
            boss.hurt(boss.damageSources().playerAttack(last.player), 10000F);
            helper.assertTrue(boss.isDeadOrDying() && earned(first.player, "kill_zombie_boss") && earned(last.player, "kill_zombie_boss"),
                    "Both the contributing player and the finisher earn the boss achievement");
            helper.assertFalse(earned(bystander.player, "kill_zombie_boss"), "A nearby spectator without damage receives no achievement");
            boss.discard();
        }
        helper.succeed();
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow());
    }

    private static boolean earned(ServerPlayer player, String name) {
        var holder = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("modern_ite", "legacy/" + name));
        if (holder == null) throw new AssertionError("Missing loaded advancement " + name);
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static final class Session implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;

        Session(GameTestHelper helper, String name) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        }

        @Override public void close() {
            try {
                player.server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
