package io.github.ningmeng123.modernite.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;

/** Nether fortress locator using the native eye entity and structure query. @author NingMeng123 */
public final class FortressEyeItem extends Item {
    public FortressEyeItem(Properties properties) { super(properties.stacksTo(16)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(!level.dimension().equals(Level.NETHER)) return InteractionResultHolder.fail(stack);
        if(level instanceof ServerLevel server) {
            var target=server.findNearestMapStructure(TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("modern_ite","fortresses")), player.blockPosition(),100,false);
            if(target==null) return InteractionResultHolder.fail(stack);
            var eye=new EyeOfEnder(level,player.getX(),player.getEyeY(),player.getZ()); eye.setItem(new ItemStack(this)); eye.signalTo(target);
            if(!level.addFreshEntity(eye))return InteractionResultHolder.fail(stack);
            if(!player.isCreative()) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
