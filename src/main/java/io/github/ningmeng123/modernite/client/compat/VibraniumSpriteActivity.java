package io.github.ningmeng123.modernite.client.compat;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Keep rendered vibranium sprites active when cached meshes bypass Sodium's quad path. @author NingMeng123 */
public final class VibraniumSpriteActivity {
    private static final Set<String> NATIVE_TOOLS = Set.of("vibranium_sword", "vibranium_axe",
            "vibranium_battle_axe", "vibranium_dagger", "vibranium_hatchet", "vibranium_hoe",
            "vibranium_knife", "vibranium_mattock", "vibranium_pickaxe", "vibranium_scythe",
            "vibranium_shovel", "vibranium_war_hammer");
    // Exact paths from MITE's optional SimplySwordsCompat registration, never a broad prefix match.
    private static final Set<String> SIMPLY_SWORDS_WEAPONS = Set.of(
            "simplyswords_vibranium_longsword", "simplyswords_vibranium_twinblade",
            "simplyswords_vibranium_rapier", "simplyswords_vibranium_katana", "simplyswords_vibranium_sai",
            "simplyswords_vibranium_spear", "simplyswords_vibranium_glaive", "simplyswords_vibranium_warglaive",
            "simplyswords_vibranium_cutlass", "simplyswords_vibranium_claymore", "simplyswords_vibranium_greataxe",
            "simplyswords_vibranium_greathammer", "simplyswords_vibranium_chakram",
            "simplyswords_vibranium_scythe", "simplyswords_vibranium_halberd");
    // Item rendering is on the render thread. Weak keys release replaced models after resource reloads.
    private static final Map<BakedModel, List<TextureAtlasSprite>> MODEL_SPRITES = new WeakHashMap<>();
    private static boolean disabled;

    private VibraniumSpriteActivity() {}

    public static void markRendered(ItemStack stack, BakedModel model) {
        if (disabled || stack.isEmpty() || model == null || !supported(stack)) return;
        MethodHandle marker = SodiumBinding.MARK_ACTIVE;
        if (marker == null) return;
        try {
            for (TextureAtlasSprite sprite : MODEL_SPRITES.computeIfAbsent(model, VibraniumSpriteActivity::sprites))
                marker.invokeExact(sprite);
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            if (failure instanceof ThreadDeath fatal) throw fatal;
            disabled = true;
            MODEL_SPRITES.clear();
            LogUtils.getLogger().warn("Disabled optional Sodium vibranium sprite activity compatibility", failure);
        }
    }

    private static boolean supported(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("mite")
                && (NATIVE_TOOLS.contains(id.getPath()) || SIMPLY_SWORDS_WEAPONS.contains(id.getPath()))
                || id.getNamespace().equals("modern_ite") && id.getPath().equals("club_vibranium");
    }

    private static List<TextureAtlasSprite> sprites(BakedModel model) {
        Set<TextureAtlasSprite> sprites = Collections.newSetFromMap(new IdentityHashMap<>());
        RandomSource random = RandomSource.create(42L);
        for (Direction face : Direction.values()) {
            random.setSeed(42L);
            collect(sprites, model.getQuads(null, face, random));
        }
        random.setSeed(42L);
        collect(sprites, model.getQuads(null, null, random));
        // A custom model may provide its geometry through another API, retaining this sprite.
        if (sprites.isEmpty() && model.getParticleIcon() != null) sprites.add(model.getParticleIcon());
        return List.copyOf(sprites);
    }

    private static void collect(Set<TextureAtlasSprite> sprites, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) if (quad.getSprite() != null) sprites.add(quad.getSprite());
    }

    /** Resolve the optional public API only once, on the first supported item actually rendered. */
    private static final class SodiumBinding {
        private static final MethodHandle MARK_ACTIVE = bind();

        private static MethodHandle bind() {
            if (!ModList.get().isLoaded("sodium")) return null;
            try {
                Class<?> api = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil", true,
                        VibraniumSpriteActivity.class.getClassLoader());
                Object singleton = api.getField("INSTANCE").get(null);
                return MethodHandles.publicLookup().findVirtual(api, "markSpriteActive",
                        MethodType.methodType(void.class, TextureAtlasSprite.class)).bindTo(singleton);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                LogUtils.getLogger().warn("Sodium public sprite activity API is unavailable; vibranium compatibility was not enabled", failure);
                return null;
            }
        }
    }
}
