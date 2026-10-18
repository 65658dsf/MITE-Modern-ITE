package io.github.ningmeng123.modernite.content;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/** UUID ownership and native persisted chest inventory. @author NingMeng123 */
public final class VibraniumChestEntity extends ChestBlockEntity {
    private UUID owner; private String ownerName="";
    public VibraniumChestEntity(BlockPos pos,BlockState state) { super(ExtremeContent.CHEST_ENTITY.get(),pos,state); }
    public void setOwner(Player player) { owner=player.getUUID(); ownerName=player.getName().getString(); setChanged(); }
    public boolean isOwner(Player player) { return owner!=null && owner.equals(player.getUUID()); }
    public String ownerName() { return ownerName; }
    @Override public boolean canOpen(Player player) { if(!player.isCreative()&&!isOwner(player)) {player.displayClientMessage(Component.translatable("message.modern_ite.chest_locked",ownerName),true);return false;}return super.canOpen(player); }
    @Override public boolean stillValid(Player player) { return (player.isCreative()||isOwner(player))&&super.stillValid(player); }
    @Override protected Component getDefaultName() { return Component.translatable("block.modern_ite.vibranium_chest"); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.saveAdditional(tag,registries); if(owner!=null)tag.putUUID("owner",owner); tag.putString("owner_name",ownerName);tag.putInt("modern_ite_version",1); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.loadAdditional(tag,registries);owner=tag.hasUUID("owner")?tag.getUUID("owner"):null; ownerName=tag.getString("owner_name"); }
}
