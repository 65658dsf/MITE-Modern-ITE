package io.github.ningmeng123.modernite.mixin.entity;
import io.github.ningmeng123.mite.entity.R196Elemental;
import io.github.ningmeng123.modernite.entity.ElementalMaterial;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Extends existing earth-elemental identities rather than registering duplicate species. @author NingMeng123 */
@Mixin(R196Elemental.class)
public class ElementalMaterialMixin implements ElementalMaterial {
    @Unique private static final EntityDataAccessor<Integer> MODERN_ITE_MATERIAL=SynchedEntityData.defineId(R196Elemental.class,EntityDataSerializers.INT);
    @Unique private R196Elemental modernIte$self(){return (R196Elemental)(Object)this;}
    @Override public int modernIteMaterial(){return modernIte$self().getEntityData().get(MODERN_ITE_MATERIAL);}
    @Override public void modernIteMaterial(int value){modernIte$self().getEntityData().set(MODERN_ITE_MATERIAL,Math.clamp(value,0,2));}
    @Inject(method="defineSynchedData",at=@At("RETURN"))
    private void modernIte$define(SynchedEntityData.Builder builder,CallbackInfo ci){builder.define(MODERN_ITE_MATERIAL,0);}
    @Inject(method="setMaterial",at=@At("HEAD"),cancellable=true)
    private void modernIte$set(Block block,boolean heated,CallbackInfo ci){
        if(modernIte$self().isClay())return;
        int v=block==Blocks.OAK_PLANKS?1:BuiltInRegistries.BLOCK.getKey(block).equals(ResourceLocation.parse("mite:ancient_metal_block"))?2:0;
        modernIteMaterial(v);if(v!=0)ci.cancel();
    }
    @Inject(method="materialBlock",at=@At("HEAD"),cancellable=true)
    private void modernIte$block(CallbackInfoReturnable<Block> cir){
        if(modernIteMaterial()==1)cir.setReturnValue(Blocks.OAK_PLANKS);
        if(modernIteMaterial()==2)cir.setReturnValue(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mite:ancient_metal_block")));
    }
    @Inject(method={"isMagma"},at=@At("HEAD"),cancellable=true)
    private void modernIte$notMagma(CallbackInfoReturnable<Boolean> cir){if(modernIteMaterial()!=0)cir.setReturnValue(false);}
    @Inject(method="convertToMagma",at=@At("HEAD"),cancellable=true)
    private void modernIte$noConvert(CallbackInfo ci){if(modernIteMaterial()!=0)ci.cancel();}
    @Inject(method="isInvulnerableTo",at=@At("HEAD"),cancellable=true)
    private void modernIte$woodBurns(DamageSource source,CallbackInfoReturnable<Boolean> cir){if(modernIteMaterial()==1&&source.is(DamageTypeTags.IS_FIRE))cir.setReturnValue(false);}
    @Inject(method="getBlockHarvestLevel",at=@At("HEAD"),cancellable=true)
    private void modernIte$harvest(CallbackInfoReturnable<Integer> cir){if(modernIteMaterial()!=0)cir.setReturnValue(modernIteMaterial()==1?0:4);}
    @Inject(method="addAdditionalSaveData",at=@At("RETURN"))
    private void modernIte$save(CompoundTag t,CallbackInfo ci){t.putInt("ModernIteMaterial",modernIteMaterial());}
    @Inject(method="readAdditionalSaveData",at=@At("RETURN"))
    private void modernIte$load(CompoundTag t,CallbackInfo ci){modernIteMaterial(t.getInt("ModernIteMaterial"));}
    @Inject(method="finalizeSpawn",at=@At("RETURN"))
    private void modernIte$naturalMaterial(ServerLevelAccessor level,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData group,CallbackInfoReturnable<SpawnGroupData> cir){
        var earth=modernIte$self();if(earth.isClay()||earth.materialBlock()!=Blocks.STONE)return;
        if(earth.getRandom().nextBoolean())earth.setMaterial(Blocks.OAK_PLANKS,false);
        else if(io.github.ningmeng123.modernite.entity.MobAbilities.underground(earth.level())&&earth.getRandom().nextBoolean())
            earth.setMaterial(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mite:ancient_metal_block")),false);
    }
}
