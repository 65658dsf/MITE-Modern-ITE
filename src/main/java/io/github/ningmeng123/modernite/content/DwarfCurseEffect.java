package io.github.ningmeng123.modernite.content;

import io.github.ningmeng123.modernite.ModernIte;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.EffectCure;

/** Synced presentation and attributes of the world-owned witch curse. @author NingMeng123 */
public final class DwarfCurseEffect extends MobEffect {
    public DwarfCurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B539E);
        var id = ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID, "dwarf_curse");
        for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.SCALE)) {
            addAttributeModifier(attribute, id, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -.5D);
        }
    }

    /** Native witch curses require disenchanting or killing their witch, rather than milk. */
    @Override public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {}
}
