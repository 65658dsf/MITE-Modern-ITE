package io.github.ningmeng123.modernite.content;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persists the soul bound to an array and synchronizes its skin. @author NingMeng123 */
public final class ResurrectionBlockEntity extends BlockEntity {
    @Nullable private ResolvableProfile owner;

    public ResurrectionBlockEntity(BlockPos pos, BlockState state) {
        super(ExtremeContent.RESURRECTION_ENTITY.get(), pos, state);
    }

    @Nullable public ResolvableProfile getOwnerProfile() { return owner; }
    public boolean hasOwner() { return owner != null && owner.id().isPresent(); }
    public boolean isOwner(Player player) { return hasOwner() && owner.id().get().equals(player.getUUID()); }
    public String ownerName() { return owner == null ? "" : owner.name().orElse(""); }

    public void setOwner(Player player) {
        owner = new ResolvableProfile(player.getGameProfile());
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.put("owner", ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, owner).getOrThrow());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.contains("owner") ? ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, tag.get("owner")).result().orElse(null) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
