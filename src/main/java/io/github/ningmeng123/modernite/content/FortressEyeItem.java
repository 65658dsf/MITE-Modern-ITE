package io.github.ningmeng123.modernite.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Locates the eye's designated Nether structure using the native eye entity. @author NingMeng123 */
public final class FortressEyeItem extends Item {
    private final TagKey<Structure> structures;

    public FortressEyeItem(Properties properties) {
        this(properties, "fortresses");
    }

    FortressEyeItem(Properties properties, String structureTag) {
        super(properties.stacksTo(16));
        this.structures = TagKey.create(Registries.STRUCTURE, ExtremeContent.id(structureTag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.dimension().equals(Level.NETHER)) return InteractionResultHolder.fail(stack);
        if (level instanceof ServerLevel server) {
            var target = server.findNearestMapStructure(structures, player.blockPosition(), 100, false);
            if (target == null) {
                player.displayClientMessage(Component.translatable("message.modern_ite.structure_eye_missing"), true);
                return InteractionResultHolder.fail(stack);
            }
            var eye = new EyeOfEnder(level, player.getX(), player.getEyeY(), player.getZ());
            eye.setItem(new ItemStack(this));
            eye.signalTo(target);
            if (!level.addFreshEntity(eye)) return InteractionResultHolder.fail(stack);
            if (!player.isCreative()) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
