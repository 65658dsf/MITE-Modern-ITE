package io.github.ningmeng123.modernite.content;
import java.util.*;
import io.github.ningmeng123.modernite.ModernIte;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
/** Namespaced control effects replace unstable legacy numeric potion IDs. @author NingMeng123 */
public final class ExtremeEffects {
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,ModernIte.MOD_ID);
    public static final Map<String,DeferredHolder<MobEffect,MobEffect>> VALUES=new LinkedHashMap<>();
    static { String[] names={"chaos","flash","occlusion","reversed","fast_sensitivity","look_up","big_head"}; int[] colors={0x9966cc,0xffffff,0xaa6622,0x5533aa,0xff5555,0x55aaff,0x88bb44};for(int i=0;i<names.length;i++){final int color=colors[i];VALUES.put(names[i],EFFECTS.register(names[i],()->new ControlEffect(color)));} }
    private static final class ControlEffect extends MobEffect { ControlEffect(int color){super(MobEffectCategory.HARMFUL,color);} }
    public static void register(IEventBus bus){EFFECTS.register(bus);}
}
