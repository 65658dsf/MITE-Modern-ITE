package io.github.ningmeng123.modernite.content;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.curse.WitchCurseData;
import io.github.ningmeng123.mite.curse.WitchCursing;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real witch entry points, attribute stacking, curing and world-owned persistence. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class DwarfCurseGameTests {
    private DwarfCurseGameTests() {}

    @GameTest(template = "gear_empty")
    public static void operatorApplyStatusAndClearUseTheRealCommandDispatcher(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Witch witch = witch(h);
        try (var session = new Session(h)) {
            var source = session.player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            var dispatcher = session.player.server.getCommands().getDispatcher();
            h.assertTrue(dispatcher.execute("mite curse apply @s dwarf_curse 20", source) == 1, "The new literal merges into the real apply command");
            h.assertTrue(DwarfCurse.pendingOrActive(session.player) && !session.player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")),
                    "The optional command delay schedules a pending curse");
            h.assertTrue(dispatcher.execute("mite curse status @s", source) == 17, "The existing status command recognizes the extra curse");
            h.assertTrue(dispatcher.execute("mite curse clear @s", source) == 1, "The existing clear command removes a pending dwarf curse");
            h.assertTrue(dispatcher.execute("mite curse apply @s dwarf_curse", source) == 1, "The default command applies immediately");
            h.assertTrue(session.player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "The command realizes the actual effect");
            h.assertTrue(dispatcher.execute("mite curse clear @s", source) == 1 && !session.player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")),
                    "The existing clear command restores the active player's attributes");
            h.assertTrue(dispatcher.execute("mite curse status @s", source) == 0, "The status command correctly reports the cleared state");
        } finally { clear(witch); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void curseHalvesCurrentAndMaximumHealthAttackAndActualDimensionsOnlyOnce(GameTestHelper h) {
        Witch witch = witch(h);
        try (var session = new Session(h)) {
            ServerPlayer player = session.player;
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(12);
            player.setHealth(30);
            float height = player.getBbHeight();
            h.assertTrue(DwarfCurse.schedule(player, witch, 0), "The new curse schedules successfully");
            PlayerCurses.reconcile(player);
            close(h, player.getMaxHealth(), 20, "Maximum health halves");
            close(h, player.getHealth(), 15, "Already injured current health also halves once");
            close(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 6, "Attack attribute halves");
            close(h, player.getBbHeight(), height / 2, "The actual player collision height halves to about one block");
            for (int i = 0; i < 8; i++) PlayerCurses.reconcile(player);
            close(h, player.getHealth(), 15, "Repeated reconciliation never halves health again");
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
            close(h, player.getMaxHealth(), 30, "Later base health growth remains halved");
            close(h, player.getHealth(), 15, "Growing the cap never heals the cursed player");
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("dwarf_curse"), -1, 5));
            close(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 6, "Effect levels cannot compound the fixed curse strength");
            h.assertTrue(PlayerCurses.cure(player), "The native cure also removes the extension curse");
            close(h, player.getMaxHealth(), 60, "Cure restores the current uncursed health cap");
            close(h, player.getHealth(), 15, "Cure gives no free health");
            close(h, player.getBbHeight(), height, "Cure restores the collision dimensions");
        } finally { clear(witch); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void delayAndMutualExclusionPreserveTheOriginalSixteenCurses(GameTestHelper h) {
        Witch witch = witch(h);
        try (var session = new Session(h)) {
            ServerPlayer player = session.player;
            h.assertTrue(DwarfCurse.schedule(player, witch, 6000), "The native delay is supported");
            PlayerCurses.reconcile(player);
            h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "A pending curse has no penalties");
            h.assertFalse(PlayerCurses.schedule(player, witch, R196Curse.EQUIPMENT_DECAY, 0), "A pending dwarf curse blocks another curse");
            var data = DwarfCurseData.get(player.serverLevel());
            data.replace(new DwarfCurseData.Entry(player.getUUID(), witch.getUUID(), player.server.overworld().getGameTime(), false));
            PlayerCurses.reconcile(player);
            h.assertTrue(player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "The exact deadline realizes the curse");
            PlayerCurses.cure(player);
            h.assertTrue(PlayerCurses.schedule(player, witch, R196Curse.EQUIPMENT_DECAY, 0), "An original curse still schedules normally");
            h.assertFalse(DwarfCurse.schedule(player, witch, 0), "An original curse blocks the new curse");
            PlayerCurses.reconcile(player);
            h.assertTrue(PlayerCurses.has(player, R196Curse.EQUIPMENT_DECAY), "The original curse still realizes without replacement");
        } finally { clear(witch); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void nativeWitchCastingCanSelectTheNewCurseAndKeepsTheOriginalDelay(GameTestHelper h) {
        Witch witch = witch(h);
        try (var session = new Session(h)) {
            ServerPlayer player = session.player;
            boolean found = false;
            for (int seed = 0; seed < 1024; seed++) {
                witch.getPersistentData().putInt(WitchCursing.SEED, seed);
                if (DwarfCurse.selected(witch, player.getGameProfile().getName())) { found = true; break; }
            }
            h.assertTrue(found, "The deterministic witch/player choices include dwarf curse");
            for (int attempt = 0; attempt < 128 && !DwarfCurse.pendingOrActive(player); attempt++) WitchCursing.tryCurse(witch, player);
            var entry = DwarfCurseData.get(player.serverLevel()).entry(player.getUUID());
            h.assertTrue(entry != null, "The real native witch casting path schedules dwarf curse");
            h.assertTrue(entry.realizationTime() == player.server.overworld().getGameTime() + WitchCursing.REALIZATION_DELAY,
                    "Natural witch casting preserves the original delay");
            h.assertTrue(WitchCurseData.get(player.serverLevel()).entry(player.getUUID()) == null, "The replacement schedules only one curse");
            h.assertTrue(DwarfCurse.selected(witch, player.getGameProfile().getName()), "Repeated selection stays stable for the caster and player");
        } finally { clear(witch); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void milkCannotLiftTheCurseButTheRealDisenchantingBottleCan(GameTestHelper h) {
        Witch witch = witch(h);
        try (var session = new Session(h)) {
            ServerPlayer player = session.player;
            DwarfCurse.schedule(player, witch, 0);
            PlayerCurses.reconcile(player);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
            player.getFoodData().setFoodLevel(0);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
            ItemStack bucket = new ItemStack(Items.MILK_BUCKET).finishUsingItem(player.serverLevel(), player);
            h.assertTrue(bucket.is(Items.BUCKET) && !player.hasEffect(MobEffects.POISON), "Actual milk consumption clears ordinary poison");
            h.assertTrue(player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "Milk leaves the curse and its attributes intact");
            var bonus = ResourceLocation.fromNamespaceAndPath("modern_ite", "dwarf_test_bonus");
            player.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(new AttributeModifier(bonus, 8, AttributeModifier.Operation.ADD_VALUE));
            double cursedAttack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float health = player.getHealth();
            ItemStack bottle = new ItemStack(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()).finishUsingItem(player.serverLevel(), player);
            h.assertTrue(bottle.is(Items.GLASS_BOTTLE) && !DwarfCurse.pendingOrActive(player), "Actual disenchanting bottle removes the world record");
            h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "Disenchanting removes the synchronized curse effect");
            close(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), cursedAttack * 2, "Curing preserves unrelated attack modifiers");
            close(h, player.getHealth(), health, "Disenchanting does not heal");
            h.assertFalse(PlayerCurses.cure(player), "A repeated cure has no remaining record to remove");
        } finally { clear(witch); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void saveReloadDimensionsAndMirrorRecoveryDoNotRepeatHealthLoss(GameTestHelper h) {
        Witch witch = witch(h);
        try (var session = new Session(h)) {
            ServerPlayer player = session.player;
            DwarfCurse.schedule(player, witch, 0);
            PlayerCurses.reconcile(player);
            var data = DwarfCurseData.get(player.serverLevel());
            var saved = data.save(new CompoundTag(), player.registryAccess());
            var loaded = DwarfCurseData.load(saved, player.registryAccess());
            h.assertTrue(data.entry(player.getUUID()).equals(loaded.entry(player.getUUID())), "World persistence round-trips caster and realized state");
            data.replace(loaded.entry(player.getUUID()));
            float health = player.getHealth();
            var original = player.serverLevel();
            try {
                for (var dimension : java.util.List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
                    var level = player.server.getLevel(dimension);
                    h.assertTrue(DwarfCurseData.get(level) == data, "Dimensions share one curse ledger");
                    player.setServerLevel(level);
                    // Recreate a missing client/player effect mirror before restoring its saved world record.
                    var restored = data.entry(player.getUUID());
                    data.remove(player.getUUID());
                    player.removeEffect(ExtremeEffects.VALUES.get("dwarf_curse"));
                    h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "The mirror-recovery fixture starts without the effect");
                    data.replace(restored);
                    PlayerCurses.reconcile(player);
                    h.assertTrue(player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "Reconciliation restores a missing effect mirror");
                    close(h, player.getHealth(), health, "Dimension recovery never repeats the initial health loss");
                    close(h, player.getAttributeValue(Attributes.SCALE), .5, "Recovery installs one scale modifier");
                }
            } finally { player.setServerLevel(original); }
        } finally { clear(witch); }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void playerDeathAndRespawnKeepTheCurseButOnlyCasterDeathLiftsIt(GameTestHelper h) {
        Witch witch = witch(h), unrelated = witch(h);
        try (var session = new Session(h)) {
            DwarfCurse.schedule(session.player, witch, 0);
            PlayerCurses.reconcile(session.player);
            var entry = DwarfCurseData.get(session.player.serverLevel()).entry(session.player.getUUID());
            ServerPlayer original = session.player;
            original.kill();
            h.assertTrue(original.isDeadOrDying(), "Native death runs");
            original.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            session.player = original.connection.player;
            h.assertTrue(session.player != original && session.player.isAlive(), "Native respawn creates a replacement player");
            PlayerCurses.reconcile(session.player);
            h.assertTrue(entry.equals(DwarfCurseData.get(session.player.serverLevel()).entry(session.player.getUUID())), "Death and respawn retain the original caster record");
            h.assertTrue(session.player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "The replacement regains the curse effect");
            unrelated.kill();
            h.assertTrue(DwarfCurse.pendingOrActive(session.player), "An unrelated witch's death does not lift it");
            witch.kill();
            h.assertFalse(DwarfCurse.pendingOrActive(session.player), "The caster's actual death removes the world record");
            h.assertFalse(session.player.hasEffect(ExtremeEffects.VALUES.get("dwarf_curse")), "Caster death immediately removes the active penalty");
        } finally { clear(witch); clear(unrelated); }
        h.succeed();
    }

    private static Witch witch(GameTestHelper h) {
        Witch witch = EntityType.WITCH.create(h.getLevel());
        witch.moveTo(h.absolutePos(new BlockPos(2, 10, 2)).getCenter());
        witch.setNoAi(true);
        witch.setNoGravity(true);
        h.getLevel().addFreshEntity(witch);
        return witch;
    }

    private static void clear(Witch witch) {
        PlayerCurses.removeForWitch(witch);
        if (!witch.isRemoved()) witch.discard();
    }

    private static void close(GameTestHelper h, double value, double expected, String message) {
        h.assertTrue(Math.abs(value - expected) < 1.0E-5D, message + ": " + value + " vs " + expected);
    }

    private static final class Session implements AutoCloseable {
        ServerPlayer player;
        final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "dwarf-" + UUID.randomUUID().toString().substring(0, 8)), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(h.absolutePos(new BlockPos(3, 10, 3)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
        }
        @Override public void close() {
            PlayerCurses.cure(player);
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
