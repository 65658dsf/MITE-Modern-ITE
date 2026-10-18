package io.github.ningmeng123.modernite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adds the extension device to the base thermal index without touching deferred registries. @author NingMeng123 */
@Mixin(value = R196FurnaceRules.class, remap = false)
public abstract class FurnaceProfilesMixin {
    @ModifyReturnValue(method = "profiles()Ljava/util/Map;", at = @At("RETURN"))
    private static Map<String, R196FurnaceRules.Profile> modernite$includeVibranium(
            Map<String, R196FurnaceRules.Profile> original) {
        if (original.containsKey("modern_ite:vibranium_furnace")) return original;
        Map<String, R196FurnaceRules.Profile> profiles = new LinkedHashMap<>(original);
        profiles.put("modern_ite:vibranium_furnace", new R196FurnaceRules.Profile(5, true, 200));
        return Collections.unmodifiableMap(profiles);
    }
}
