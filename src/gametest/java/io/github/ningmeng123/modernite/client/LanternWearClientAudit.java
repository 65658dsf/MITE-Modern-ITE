package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.ClientHooks;

/** Native equip-height regression for network-style lantern durability updates. @author NingMeng123 */
final class LanternWearClientAudit {
    private static final String[] RENDER_FIELDS = {
        "mainHandItem", "offHandItem", "mainHandHeight", "oMainHandHeight", "offHandHeight", "oOffHandHeight"
    };
    private LanternWearClientAudit() {}

    static JsonObject capture(Minecraft mc) throws Exception {
        var player = mc.player;
        var inventory = player.getInventory();
        var renderer = mc.gameRenderer.itemInHandRenderer;
        var items = BuiltInRegistries.ITEM.stream().filter(item -> item instanceof DynamicCoreItem)
            .map(item -> (DynamicCoreItem) item).sorted(Comparator.comparingInt(item -> item.material.ordinal())).toList();
        check(items.size() == 3 && !player.isHandsBusy(), "Wear audit requires the three retained lanterns and free hands");
        var savedItems = new ArrayList<>(inventory.items);
        ItemStack savedOffhand = player.getOffhandItem();
        int savedSelected = inventory.selected;
        Field hookSlot = field(ClientHooks.class, "slotMainHand");
        Field strength = field(LivingEntity.class, "attackStrengthTicker");
        Object savedHookSlot = hookSlot.get(null), savedStrength = strength.get(player);
        Object[] savedRenderer = new Object[RENDER_FIELDS.length];
        for (int i = 0; i < RENDER_FIELDS.length; i++) savedRenderer[i] = field(ItemInHandRenderer.class, RENDER_FIELDS[i]).get(renderer);
        JsonObject result = new JsonObject();
        JsonArray samples = new JsonArray(); result.add("samples", samples);
        try {
            strength.setInt(player, 1000);
            for (int material = 0; material < items.size(); material++) for (InteractionHand hand : InteractionHand.values()) {
                inventory.selected = savedSelected;
                ItemStack stack = new ItemStack(items.get(material));
                stage(mc, hand, stack, hookSlot);
                JsonObject sample = new JsonObject(); samples.add(sample);
                sample.addProperty("material", items.get(material).material.name()); sample.addProperty("hand", hand.name());
                int maximum = stack.getMaxDamage();
                int updates = 0;
                for (int damage : new int[] {1, 2, 3, maximum / 2, maximum - 1, maximum}) {
                    // Inventory synchronization replaces the stack object, including the final fuel point.
                    ItemStack updated = player.getItemInHand(hand).copy(); updated.setDamageValue(damage);
                    player.setItemInHand(hand, updated);
                    for (int tick = 0; tick < 4; tick++) {
                        renderer.tick();
                        for (String name : new String[] {"mainHandHeight", "oMainHandHeight", "offHandHeight", "oOffHandHeight"})
                            check(field(ItemInHandRenderer.class, name).getFloat(renderer) == 1F,
                                "Durability update lowered " + name + ": " + items.get(material).material + "/" + hand + "/" + damage);
                        check(field(ItemInHandRenderer.class, hand == InteractionHand.MAIN_HAND ? "mainHandItem" : "offHandItem").get(renderer) == updated,
                            "Native renderer must immediately cache the synchronized durability stack");
                    }
                    updates++;
                }
                sample.addProperty("damage_updates_without_dip", updates);
                sample.addProperty("last_fuel_point_without_dip", true);
                stage(mc, hand, stack, hookSlot);
                player.setItemInHand(hand, new ItemStack(items.get((material + 1) % items.size())));
                expectDip(renderer, hand, "different lantern material");
                stage(mc, hand, stack, hookSlot);
                player.setItemInHand(hand, ItemStack.EMPTY);
                expectDip(renderer, hand, "empty hand");
                stage(mc, hand, stack, hookSlot);
                ItemStack renamed = stack.copy(); renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Wear audit"));
                player.setItemInHand(hand, renamed);
                expectDip(renderer, hand, "non-damage component");
                if (hand == InteractionHand.MAIN_HAND) {
                    stage(mc, hand, stack, hookSlot);
                    inventory.selected = (savedSelected + 1) % 9;
                    player.setItemInHand(hand, stack.copy());
                    expectDip(renderer, hand, "selected hotbar slot");
                }
                sample.addProperty("material_empty_name_and_slot_controls", true);
            }
            result.addProperty("scope", "Actual ItemInHandRenderer.tick -> ClientHooks -> item hook, with replaced inventory stacks");
            result.addProperty("native_tick_count", items.size() * 55);
            result.addProperty("status", "passed");
            return result;
        } finally {
            inventory.selected = savedSelected;
            for (int i = 0; i < savedItems.size(); i++) inventory.items.set(i, savedItems.get(i));
            player.setItemInHand(InteractionHand.OFF_HAND, savedOffhand);
            strength.set(player, savedStrength); hookSlot.set(null, savedHookSlot);
            for (int i = 0; i < RENDER_FIELDS.length; i++) field(ItemInHandRenderer.class, RENDER_FIELDS[i]).set(renderer, savedRenderer[i]);
        }
    }

    private static void stage(Minecraft mc, InteractionHand hand, ItemStack stack, Field hookSlot) throws Exception {
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        mc.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        mc.player.setItemInHand(hand, stack);
        var renderer = mc.gameRenderer.itemInHandRenderer;
        field(ItemInHandRenderer.class, "mainHandItem").set(renderer, mc.player.getMainHandItem());
        field(ItemInHandRenderer.class, "offHandItem").set(renderer, mc.player.getOffhandItem());
        for (int i = 2; i < RENDER_FIELDS.length; i++) field(ItemInHandRenderer.class, RENDER_FIELDS[i]).setFloat(renderer, 1F);
        hookSlot.setInt(null, mc.player.getInventory().selected);
    }

    private static void expectDip(ItemInHandRenderer renderer, InteractionHand hand, String reason) throws Exception {
        renderer.tick();
        String changed = hand == InteractionHand.MAIN_HAND ? "mainHandHeight" : "offHandHeight";
        String unchanged = hand == InteractionHand.MAIN_HAND ? "offHandHeight" : "mainHandHeight";
        check(field(ItemInHandRenderer.class, changed).getFloat(renderer) < .99F, "Must retain equip animation for " + reason);
        check(field(ItemInHandRenderer.class, unchanged).getFloat(renderer) == 1F, "Other hand must stay equipped for " + reason);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
