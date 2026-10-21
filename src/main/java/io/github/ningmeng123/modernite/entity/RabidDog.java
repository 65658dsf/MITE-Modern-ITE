package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.modernite.content.ExtremeEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;

/** A hostile wolf species whose successful bite causes six seconds of rabies. @author NingMeng123 */
public final class RabidDog extends Wolf implements Enemy {
    public static final int RABIES_TICKS = 6 * 20;

    public RabidDog(EntityType<? extends RabidDog> type, Level level) {
        super(type, level);
        xpReward = 5;
        setHealth(getMaxHealth());
        if (!level.isClientSide) ensureSpeed();
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, .4F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide && target instanceof Player player && player.isAlive()
                && !player.isCreative() && !player.isSpectator()) {
            player.addEffect(new MobEffectInstance(ExtremeEffects.VALUES.get("rabies"), RABIES_TICKS), this);
        }
        return hit;
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive()) ensureSpeed();
    }

    private void ensureSpeed() {
        var speed = getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed == null || speed.getAmplifier() == 0 && !speed.isInfiniteDuration()) {
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 0, false, false, true));
        }
    }

    @Override public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND || SpawnPopulation.explicit(this)) return true;
        if (!(level() instanceof ServerLevel server) || !RabidDogContent.maySpawn(server)
                || level.getDifficulty() == Difficulty.PEACEFUL) return false;
        return level.getBrightness(LightLayer.BLOCK, blockPosition()) == 0
                && level.getMaxLocalRawBrightness(blockPosition()) <= 7
                && level.getBlockState(blockPosition()).getFluidState().isEmpty();
    }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public void tame(Player player) {}
    @Override public void setTame(boolean tame, boolean applySideEffects) { super.setTame(false, false); }
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public boolean canMate(Animal other) { return false; }
    @Override public Wolf getBreedOffspring(ServerLevel level, AgeableMob other) { return null; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean isAngry() { return true; }
    @Override public boolean removeWhenFarAway(double distance) { return true; }
    @Override protected boolean shouldDespawnInPeaceful() { return true; }
    @Override protected int getBaseExperienceReward() { return xpReward; }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOwnerUUID(null);
        setTame(false, false);
        setOrderedToSit(false);
        setInSittingPose(false);
        if (!level().isClientSide) ensureSpeed();
    }
}
