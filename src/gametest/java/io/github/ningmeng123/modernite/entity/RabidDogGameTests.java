package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.content.ExtremeEffects;
import io.github.ningmeng123.modernite.world.ExtremeWorldState;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real bite, persistence, interaction and admission checks for the new hostile species. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class RabidDogGameTests {
    private RabidDogGameTests() {}

    private static RabidDog dog(GameTestHelper h) {
        RabidDog dog = RabidDogContent.RABID_DOG.get().create(h.getLevel());
        h.assertTrue(dog != null, "The registered rabid dog can be constructed");
        dog.moveTo(h.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        return dog;
    }

    @GameTest(template = "gear_empty")
    public static void successfulBiteAppliesExactlySixSecondsButRejectedBiteDoesNot(GameTestHelper h) {
        RabidDog dog = dog(h);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(dog.position().add(.7, 0, 0));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        player.setInvulnerable(true);
        h.assertFalse(dog.doHurtTarget(player), "Invulnerable players reject a real bite");
        h.assertFalse(player.hasEffect(ExtremeEffects.VALUES.get("rabies")), "A rejected bite never applies rabies");
        player.setInvulnerable(false);
        h.assertTrue(dog.doHurtTarget(player), "An unprotected player receives the real bite");
        h.assertTrue(player.getHealth() < 1000, "The bite dealt damage");
        var effect = player.getEffect(ExtremeEffects.VALUES.get("rabies"));
        h.assertTrue(effect != null && effect.getDuration() == 120 && effect.getAmplifier() == 0,
                "A successful bite applies exactly six seconds of rabies");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void intrinsicSpeedSurvivesReloadAndReturnsAfterEffectRemoval(GameTestHelper h) {
        RabidDog dog = dog(h);
        var speed = dog.getEffect(MobEffects.MOVEMENT_SPEED);
        h.assertTrue(speed != null && speed.isInfiniteDuration() && speed.getAmplifier() == 0,
                "A newly created dog starts with permanent Speed I");
        double expected = dog.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * 1.2;
        h.assertTrue(Math.abs(dog.getAttributeValue(Attributes.MOVEMENT_SPEED) - expected) < .000001,
                "Speed I really increases movement by twenty percent");
        CompoundTag tag = new CompoundTag();
        dog.saveWithoutId(tag);
        RabidDog restored = dog(h);
        restored.load(tag);
        h.assertTrue(restored.hasEffect(MobEffects.MOVEMENT_SPEED), "Saved dogs retain their innate effect");
        restored.removeEffect(MobEffects.MOVEMENT_SPEED);
        restored.setNoAi(true);
        restored.tick();
        h.assertTrue(restored.hasEffect(MobEffects.MOVEMENT_SPEED), "Removing effects does not permanently disable innate Speed I");
        h.assertTrue(Math.abs(restored.getAttributeValue(Attributes.MOVEMENT_SPEED) - expected) < .000001,
                "Reapplying innate speed never compounds the modifier");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void dogIsHostileAndCannotBecomeAnOrdinaryPet(GameTestHelper h) {
        RabidDog dog = dog(h);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 4));
        dog.mobInteract(player, InteractionHand.MAIN_HAND);
        dog.tame(player);
        h.assertFalse(dog.isTame(), "Neither bones nor the ordinary tame entry point tame a rabid dog");
        h.assertTrue(player.getMainHandItem().getCount() == 4, "Rejected taming does not consume the player's bones");
        h.assertFalse(dog.isFood(new ItemStack(Items.BEEF)), "Meat never triggers feeding or breeding");
        h.assertFalse(dog.canBeLeashed(), "The hostile dog cannot be captured as a leashed pet");
        h.assertTrue(dog.getType().getCategory() == MobCategory.MONSTER && SpawnPopulation.hostile(dog.getType()),
                "The animal-shaped dog is counted against the hostile budget");
        h.assertTrue(dog.removeWhenFarAway(10000) && dog.shouldDespawnInPeaceful(),
                "The dog uses hostile distance and peaceful despawning");
        h.assertTrue(dog.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof MeleeAttackGoal),
                "A real melee pursuit goal is installed");
        h.assertFalse(dog.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof BreedGoal || g.getGoal() instanceof FollowOwnerGoal),
                "Pet following and breeding goals are absent");
        h.succeed();
    }

    @GameTest(template = "gear_empty", batch = "spawn_balance")
    public static void naturalPoolRespectsGraceAndOnlyUsesOverworldAndUnderworld(GameTestHelper h) {
        var level = h.getLevel();
        var progress = BloodMoonInvasionData.get(level);
        long oldDay = progress.highestDay(), oldTime = level.getDayTime();
        boolean dirty = progress.isDirty(), graceEnded = ExtremeWorldState.graceEnded(level);
        var startSetting=io.github.ningmeng123.modernite.config.IteConfig.setting("extreme_mob_spawn_limit_day");
        Object startBefore=startSetting.get();
        try {
            // Exercise the historical sixty-four-day boundary independently of the new day-three factory preset.
            startSetting.set(65);io.github.ningmeng123.modernite.config.IteConfig.refresh();
            level.setDayTime(0);
            progress.restoreProgress(1);
            ExtremeWorldState.restoreGraceEnded(level, false);
            var entries = new ArrayList<R196MobSpawning.Entry>();
            var monsters = new MiteExtensions.SpawnContext(level, R196SpawnRules.Category.MONSTER, false);
            ExtremeMobs.addSpawnEntries(monsters, h.absolutePos(BlockPos.ZERO), entries);
            h.assertFalse(entries.stream().anyMatch(e -> e.type() == RabidDogContent.RABID_DOG.get()),
                    "The actual extension spawn pool excludes rabid dogs during grace");
            ExtremeWorldState.restoreGraceEnded(level, true);
            entries.clear();
            ExtremeMobs.addSpawnEntries(monsters, h.absolutePos(BlockPos.ZERO), entries);
            h.assertFalse(entries.stream().anyMatch(e -> e.type() == RabidDogContent.RABID_DOG.get()),
                    "Ending grace alone does not bypass the 64-complete-day ITE floor");
            level.setDayTime(63*24_000L);
            progress.restoreProgress(64);
            h.assertFalse(RabidDogContent.maySpawn(level), "Rabid dogs remain locked on day 64");
            level.setDayTime(64*24_000L);
            progress.restoreProgress(65);
            entries.clear();
            ExtremeMobs.addSpawnEntries(monsters, h.absolutePos(BlockPos.ZERO), entries);
            h.assertTrue(entries.stream().anyMatch(e -> e.type() == RabidDogContent.RABID_DOG.get()),
                    "The actual extension spawn pool includes rabid dogs on day 65 after grace");
            var nether = level.getServer().getLevel(Level.NETHER);
            var end = level.getServer().getLevel(Level.END);
            h.assertTrue(nether != null && end != null, "Standard non-overworld dimensions exist");
            h.assertFalse(RabidDogContent.maySpawn(nether) || RabidDogContent.maySpawn(end),
                    "Rabid dogs do not spill into Nether or End pools");
        } finally {
            startSetting.set(startBefore);io.github.ningmeng123.modernite.config.IteConfig.refresh();
            level.setDayTime(oldTime);
            progress.restoreProgress(oldDay);
            progress.setDirty(dirty);
            ExtremeWorldState.restoreGraceEnded(level, graceEnded);
        }
        h.succeed();
    }
}
