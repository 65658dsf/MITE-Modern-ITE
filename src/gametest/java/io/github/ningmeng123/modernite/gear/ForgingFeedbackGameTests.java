package io.github.ningmeng123.modernite.gear;

import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual server sounds follow forge work, saved progress, and terminal outcomes. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ForgingFeedbackGameTests {
    private record PlayedSound(int progress, SoundEvent sound, SoundSource source, float volume) {}

    private ForgingFeedbackGameTests() {}

    private static GearStationEntity preparedForge(GameTestHelper h, int tier) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        if (h.getLevel().getBlockEntity(pos) instanceof GearStationEntity previous) previous.clearContent();
        h.getLevel().setBlockAndUpdate(pos, ExtremeGear.FORGES.get(tier).get().defaultBlockState());
        var station = (GearStationEntity) h.getLevel().getBlockEntity(pos);
        station.setItem(0, new ItemStack(Items.GOLD_INGOT));
        station.setItem(1, new ItemStack(Items.IRON_INGOT));
        station.setItem(2, new ItemStack(ExtremeGear.STONES.get(0).get()));
        station.setItem(5, new ItemStack(Items.IRON_SWORD));
        station.setItem(6, new ItemStack(MiteTools.get("vibranium_axe")));
        station.setItem(7, new ItemStack(MiteTools.get("vibranium_war_hammer")));
        return station;
    }

    private static final class SoundCapture implements AutoCloseable {
        private final List<PlayedSound> sounds = new ArrayList<>();
        private final Consumer<PlayLevelSoundEvent.AtPosition> listener;

        SoundCapture(GameTestHelper h, GearStationEntity station) {
            listener = event -> {
                if (event.getLevel() == h.getLevel() && event.getPosition().equals(station.getBlockPos().getCenter())
                        && event.getSound() != null)
                    sounds.add(new PlayedSound(station.data.get(1), event.getSound().value(),
                            event.getSource(), event.getOriginalVolume()));
            };
            NeoForge.EVENT_BUS.addListener(listener);
        }

        @Override public void close() { NeoForge.EVENT_BUS.unregister(listener); }
    }

    private static void tick(GameTestHelper h, GearStationEntity station, int count) {
        for (int i = 0; i < count; i++)
            GearStationEntity.serverTick(h.getLevel(), station.getBlockPos(), station.getBlockState(), station);
    }

    @GameTest(template = "gear_empty")
    public static void allFourForgeTiersStrikeOnTimeAcrossReloadAndStopAfterSuccess(GameTestHelper h) {
        for (int tier = 0; tier < 4; tier++) {
            var station = preparedForge(h, tier);
            station.start();
            h.assertTrue(station.running(), "Every registered forge tier starts a valid iron operation");
            var saved = station.saveWithoutMetadata(h.getLevel().registryAccess());
            saved.putBoolean("doomed", false);
            station.loadWithComponents(saved, h.getLevel().registryAccess());
            try (var capture = new SoundCapture(h, station)) {
                tick(h, station, 8);
                h.assertTrue(capture.sounds.size() == 1 && capture.sounds.getFirst().progress() == 8,
                        "Actual forge work plays its first strike at the visible hammer's contact tick");

                saved = station.saveWithoutMetadata(h.getLevel().registryAccess());
                station.loadWithComponents(saved, h.getLevel().registryAccess());
                tick(h, station, 48);
                h.assertTrue(capture.sounds.stream().map(PlayedSound::progress).toList().equals(List.of(8, 32, 56)),
                        "Reload resumes the strike cadence without repeating the previous contact sound");
                h.assertTrue(capture.sounds.stream().allMatch(sound -> sound.sound() == SoundEvents.ANVIL_USE
                                && sound.source() == SoundSource.BLOCKS && sound.volume() > 0 && sound.volume() < 1),
                        "Work strikes use audible block anvil sounds quieter than the completion sound");

                tick(h, station, station.data.get(2));
                h.assertTrue(!station.running() && ExtremeGear.grade(station.getItem(8)) == 1
                                && station.getItem(5).isEmpty(),
                        "Adding strike feedback preserves the actual successful upgrade and single output");
                h.assertTrue(station.getItem(0).isEmpty() && station.getItem(1).isEmpty() && station.getItem(2).isEmpty(),
                        "The animated operation still consumes its recipe materials once");
                h.assertTrue(station.getItem(6).getDamageValue() == 2048 && station.getItem(7).getDamageValue() == 2048,
                        "Strike feedback preserves the successful operation's axe and hammer costs");
                PlayedSound completion = capture.sounds.getLast();
                h.assertTrue(completion.sound() == SoundEvents.ANVIL_USE && completion.volume() == 1,
                        "The existing success sound still plays once at completion");
                h.assertTrue(capture.sounds.stream().filter(sound -> sound.volume() == 1).count() == 1,
                        "Repeated work strikes never duplicate the success sound");
                int soundCount = capture.sounds.size();
                tick(h, station, 72);
                h.assertTrue(capture.sounds.size() == soundCount,
                        "A completed forge stays silent on subsequent server ticks");
            }
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void failureAtContactTickPlaysOnlyFailureAndStopsFurtherStrikes(GameTestHelper h) {
        var station = preparedForge(h, 3);
        station.getItem(5).setDamageValue(100);
        int remaining = station.getItem(5).getMaxDamage() - station.getItem(5).getDamageValue();
        station.start();
        var saved = station.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.putBoolean("doomed", true);
        saved.putInt("fail_at", 8);
        station.loadWithComponents(saved, h.getLevel().registryAccess());
        try (var capture = new SoundCapture(h, station)) {
            tick(h, station, 8);
            h.assertTrue(capture.sounds.size() == 1 && capture.sounds.getFirst().sound() == SoundEvents.ANVIL_BREAK
                            && capture.sounds.getFirst().volume() == 1,
                    "Failure on a hammer contact tick emits its completion sound without an extra work strike");
            h.assertTrue(!station.running() && station.getItem(8).isEmpty() && ExtremeGear.grade(station.getItem(5)) == 0,
                    "Sound feedback preserves failure without producing an upgraded output");
            h.assertTrue(station.getItem(5).getMaxDamage() - station.getItem(5).getDamageValue() == Math.round(remaining * .95F),
                    "The failed operation still charges five percent of remaining equipment durability");
            h.assertTrue(station.getItem(6).getDamageValue() == 1024 && station.getItem(7).getDamageValue() == 1024,
                    "A failure still charges half the axe and hammer wear");
            tick(h, station, 72);
            h.assertTrue(capture.sounds.size() == 1, "A failed forge never continues its work strike loop");
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void idleAndGemStationsNeverPlayForgingStrikes(GameTestHelper h) {
        var station = preparedForge(h, 0);
        try (var capture = new SoundCapture(h, station)) {
            tick(h, station, 72);
            h.assertTrue(capture.sounds.isEmpty(), "Loaded equipment and tools stay silent until forging starts");
        }
        BlockPos pos = station.getBlockPos();
        station.clearContent();
        h.getLevel().setBlockAndUpdate(pos, ExtremeGear.GEM_SETTING.get().defaultBlockState());
        var gem = (GearStationEntity) h.getLevel().getBlockEntity(pos);
        gem.setItem(0, new ItemStack(Items.IRON_CHESTPLATE));
        gem.setItem(1, ExtremeGear.gem(GemType.HEALTH, 6));
        gem.start();
        try (var capture = new SoundCapture(h, gem)) {
            tick(h, gem, 72);
            h.assertTrue(capture.sounds.isEmpty(), "Gem setting never plays a forging work strike");
        }
        h.assertTrue(ExtremeGear.rank(gem.getItem(0), GemType.HEALTH) == 6,
                "Excluding gem stations preserves their actual socketed equipment");
        h.succeed();
    }
}
