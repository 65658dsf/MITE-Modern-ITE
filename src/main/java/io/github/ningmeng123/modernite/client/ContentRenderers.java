package io.github.ningmeng123.modernite.client;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.content.ExtremeContent;
import io.github.ningmeng123.modernite.content.VibraniumChestEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
/** Native chest renderer for the new persisted strongbox. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ContentRenderers {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {event.registerBlockEntityRenderer(ExtremeContent.CHEST_ENTITY.get(),VibraniumChestRenderer::new);}
    @SubscribeEvent public static void items(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if(renderer==null)renderer=new VibraniumChestItemRenderer();return renderer;
            }
        },ExtremeContent.VIBRANIUM_CHEST.get().asItem());
    }
    private static final class VibraniumChestRenderer extends ChestRenderer<VibraniumChestEntity> {
        VibraniumChestRenderer(BlockEntityRendererProvider.Context context){super(context);}
        @Override protected Material getMaterial(VibraniumChestEntity chest,ChestType type) {
            return new Material(Sheets.CHEST_SHEET,ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,"entity/chest/vibranium_single"));
        }
    }
    private static final class VibraniumChestItemRenderer extends BlockEntityWithoutLevelRenderer {
        VibraniumChestItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
        @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
            var entity=new VibraniumChestEntity(BlockPos.ZERO,ExtremeContent.VIBRANIUM_CHEST.get().defaultBlockState());
            Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(entity,pose,buffers,light,overlay);
        }
    }
}
