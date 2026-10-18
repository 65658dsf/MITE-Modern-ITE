package io.github.ningmeng123.modernite.mixin.entity.client;
import io.github.ningmeng123.mite.client.mob.R196MobRenderers;
import io.github.ningmeng123.mite.entity.R196Elemental;
import io.github.ningmeng123.modernite.entity.ElementalMaterial;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Source planks and ancient-metal artwork on the existing animated elemental. @author NingMeng123 */
@Mixin(R196MobRenderers.class)
public class ElementalTextureMixin {
    @Inject(method="elementalTexture",at=@At("HEAD"),cancellable=true)
    private static void modernIte$texture(R196Elemental mob,CallbackInfoReturnable<ResourceLocation> cir){
        int material=((ElementalMaterial)mob).modernIteMaterial();
        if(material!=0){String name=material==1?"plank":"ancient_metal";cir.setReturnValue(ResourceLocation.parse("modern_ite:textures/entity/source/earth_elemental/"+name+"/earth_elemental_"+name+".png"));}
    }
}
