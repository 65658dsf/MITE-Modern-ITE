package io.github.ningmeng123.modernite.content;
import java.util.*;
import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
/** Namespaced control effects replace unstable legacy numeric potion IDs. @author NingMeng123 */
public final class ExtremeEffects {
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,ModernIte.MOD_ID);
    public static final Map<String,DeferredHolder<MobEffect,MobEffect>> VALUES=new LinkedHashMap<>();
    static {
        String[] names={"chaos","flash","occlusion","reversed","fast_sensitivity","look_up","big_head","bucket_head","rabies","dwarf_curse","time_dilation"};
        int[] colors={0x9966cc,0xffffff,0xaa6622,0x5533aa,0xff5555,0x55aaff,0x88bb44,0x747b83,0xb53427,0x785331,0x63d7ed};
        for(int i=0;i<names.length;i++){
            final int color=colors[i];final String name=names[i];
            VALUES.put(name,EFFECTS.register(name,()->{
                MobEffect effect=name.equals("dwarf_curse")?new DwarfCurseEffect():new ControlEffect(color);
                // Fixed strength, like the twofold model scale; refreshing cannot stack penalties.
                if(name.equals("big_head"))effect.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                        ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"big_head_movement_penalty"),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,amplifier->-.45D);
                return effect;
            }));
        }
    }
    private static final class ControlEffect extends MobEffect { ControlEffect(int color){super(MobEffectCategory.HARMFUL,color);} }
    public static void register(IEventBus bus){EFFECTS.register(bus);}
}
