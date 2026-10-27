package io.github.ningmeng123.modernite.client;

import com.google.gson.JsonObject;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.github.ningmeng123.modernite.content.TimeDilation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import java.util.UUID;

/** Native LocalPlayer physics in the owned disposable audit world. @author NingMeng123 */
public final class TimeDilationClientAudit {
    private TimeDilationClientAudit() {}

    public static JsonObject capture(Minecraft mc) throws Exception {
        var slowed = new AuditPlayer(mc);
        var normal = new AuditPlayer(mc);
        slowed.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
        slowed.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(.1);
        slowed.resetAttackStrengthTicker();
        for (int tick = 0; tick < 80; tick++) mc.level.tickNonPassenger(slowed);
        for (int tick = 0; tick < 16; tick++) mc.level.tickNonPassenger(normal);
        require(slowed.steps == 16 && normal.steps == 16, "The native LocalPlayer must advance exactly sixteen simulation steps in eighty world ticks");
        require(slowed.position().distanceToSqr(normal.position()) < 1E-8, "Local physics must cover the same distance as sixteen ordinary ticks");
        require(Math.abs(slowed.getAttackStrengthScale(0) - .08F) < 1E-5, "The client attack indicator must recharge at one fifth speed");
        require(!TimeDilation.active(slowed), "The client must stop slowing at expiry while awaiting the native removal packet");
        mc.level.tickNonPassenger(slowed);
        require(slowed.steps == 17, "The client's next tick must resume ordinary simulation");
        var sprite = mc.getMobEffectTextures().get(ExtremeEffects.VALUES.get("time_dilation"));
        require(!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Time Dilation must resolve a status icon");
        JsonObject report = new JsonObject();
        report.addProperty("status", "passed");
        report.addProperty("world_ticks", 80);
        report.addProperty("player_simulation_steps", 16);
        report.addProperty("native_physics_matches_sixteen_ticks", true);
        report.addProperty("client_attack_recovery", .08);
        report.addProperty("effect_expires_and_recovers", true);
        report.addProperty("effect_icon", sprite.contents().name().toString());
        report.add("native_input_and_rendering", inputAndRendering(mc));
        return report;
    }

    private static JsonObject inputAndRendering(Minecraft mc) throws Exception {
        var originalPlayer = mc.player;
        var originalScreen = mc.screen;
        var originalMode = mc.gameMode.getPlayerMode();
        var player = new AuditPlayer(mc);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        var delay = Minecraft.class.getDeclaredField("rightClickDelay");
        delay.setAccessible(true);
        int oldDelay = delay.getInt(mc);
        var handler = MouseHandler.class.getDeclaredMethod("turnPlayer", double.class);
        handler.setAccessible(true);
        var dx = MouseHandler.class.getDeclaredField("accumulatedDX"); dx.setAccessible(true);
        var dy = MouseHandler.class.getDeclaredField("accumulatedDY"); dy.setAccessible(true);
        double oldDx = dx.getDouble(mc.mouseHandler), oldDy = dy.getDouble(mc.mouseHandler);
        boolean smooth = mc.options.smoothCamera;
        JsonObject facts = new JsonObject();
        mc.player = player;
        mc.gameMode.setLocalMode(GameType.SURVIVAL);
        mc.level.addEntity(player);
        try {
            mc.setScreen(null);
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
            player.getInventory().selected = 0;
            KeyMapping.click(mc.options.keyHotbarSlots[2].getKey());
            delay.setInt(mc, 20);
            for (int tick = 0; tick < 80; tick++) {
                mc.tick();
                if (tick < 4) require(player.getInventory().selected == 0, "A buffered hotbar key must wait for the fifth player step");
                if (tick == 4) require(player.getInventory().selected == 2, "The native key handler must consume the retained hotbar click on tick five");
            }
            require(player.steps == 16, "Native Minecraft.tick must run sixteen player updates in eighty world ticks");
            require(delay.getInt(mc) == 4, "Native placement/use delay must decrement sixteen times, not eighty");
            mc.tick();
            require(player.steps == 17 && delay.getInt(mc) == 3, "Input, physics and use delay must resume immediately at expiry");
            facts.addProperty("native_minecraft_ticks", 80);
            facts.addProperty("native_player_updates", 16);
            facts.addProperty("buffered_hotbar_consumed_on_fifth_tick", true);
            facts.addProperty("use_delay_twenty_to_four", true);

            // Test the transformed native mouse turn without changing sensitivity settings.
            mc.options.smoothCamera = false;
            player.setYRot(0); player.setXRot(0);
            dx.setDouble(mc.mouseHandler, 40); dy.setDouble(mc.mouseHandler, 20);
            handler.invoke(mc.mouseHandler, 1D);
            float yaw = player.getYRot(), pitch = player.getXRot();
            player.setYRot(0); player.setXRot(0);
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 120));
            handler.invoke(mc.mouseHandler, 1D);
            require(Math.abs(player.getYRot() * 5 - yaw) < 1E-4 && Math.abs(player.getXRot() * 5 - pitch) < 1E-4,
                    "Native yaw and pitch input must both turn at one fifth speed");
            facts.addProperty("native_mouse_turn_multiplier", .2);

            player.setDeltaMovement(new Vec3(1, 0, 0));
            for (int tick = 0; tick < 5; tick++) mc.tick();
            var camera = new Camera();
            double previous = -Double.MAX_VALUE;
            int samples = 0;
            for (int phase = 0; phase < 5; phase++) {
                for (float partial : new float[]{0, .5F, .99F}) {
                    camera.setup(mc.level, player, false, false, partial);
                    double current = camera.getPosition().x;
                    require(current > previous, "The actual camera must progress continuously through all five slow-tick phases");
                    previous = current; samples++;
                }
                if (phase < 4) mc.tick();
            }
            facts.addProperty("monotonic_native_camera_samples", samples);

            player.setDeltaMovement(Vec3.ZERO);
            // Reset the phase before exercising complete native inventory press/release pairs.
            player.removeEffect(ExtremeEffects.VALUES.get("time_dilation")); mc.tick();
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("time_dilation"), 80));
            player.inventoryMenu.getSlot(9).set(new ItemStack(Items.STICK, 3));
            mc.setScreen(new InventoryScreen(player));
            require(mc.screen instanceof AbstractContainerScreen<?>, "The actual inventory screen must open");
            var inventory = (AbstractContainerScreen<?>) mc.screen;
            var left = AbstractContainerScreen.class.getDeclaredField("leftPos"); left.setAccessible(true);
            var top = AbstractContainerScreen.class.getDeclaredField("topPos"); top.setAccessible(true);
            var slot = player.inventoryMenu.getSlot(9);
            double x = left.getInt(inventory) + slot.x + 8, y = top.getInt(inventory) + slot.y + 8;
            inventory.mouseClicked(x, y, 0); inventory.mouseReleased(x, y, 0);
            require(player.inventoryMenu.getCarried().isEmpty(), "Inventory press/release must be retained before a player action tick");
            for (int tick = 0; tick < 4; tick++) mc.tick();
            require(player.inventoryMenu.getCarried().isEmpty(), "Inventory input must not execute during the first four world ticks");
            mc.tick();
            require(player.inventoryMenu.getCarried().is(Items.STICK) && player.inventoryMenu.getCarried().getCount() == 3,
                    "The fifth tick must execute native inventory pickup and release without losing or duplicating items: screen="
                            + inventory.getClass().getSimpleName() + ", carried=" + player.inventoryMenu.getCarried() + ", slot=" + slot.getItem());
            var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            inventory.render(graphics, (int) x, (int) y, .5F); graphics.flush();
            facts.addProperty("native_inventory_pickup_on_fifth_tick", true);
            facts.addProperty("native_player_model_rendered", true);
            facts.addProperty("status", "passed");
            return facts;
        } finally {
            player.removeAllEffects();
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            mc.player = originalPlayer;
            mc.gameMode.setLocalMode(originalMode);
            player.discard();
            PlayerTimeDilationActions.beforeTick();
            mc.setScreen(originalScreen);
            delay.setInt(mc, oldDelay);
            dx.setDouble(mc.mouseHandler, oldDx); dy.setDouble(mc.mouseHandler, oldDy);
            mc.options.smoothCamera = smooth;
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static final class AuditPlayer extends LocalPlayer {
        int steps;
        AuditPlayer(Minecraft mc) {
            super(mc, mc.level, mc.getConnection(), mc.player.getStats(), mc.player.getRecipeBook(), false, false);
            input = new Input();
            setUUID(UUID.randomUUID());
            moveTo(mc.player.position().add(0, 100, 0));
        }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public void aiStep() { steps++; super.aiStep(); }
    }
}
