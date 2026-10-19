package io.github.ningmeng123.modernite.mixin.entity;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Grounded arrows are not incoming attacks even if their saved velocity is nonzero. @author NingMeng123 */
@Mixin(AbstractArrow.class)
public interface ArrowThreatAccessor {
    @Accessor("inGround") boolean modernite$isInGround();
}
