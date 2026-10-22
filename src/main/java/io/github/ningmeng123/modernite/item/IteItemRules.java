package io.github.ningmeng123.modernite.item;

import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.LoggerFactory;

/** Item configuration hooks and armor effects, separate from visual set qualifications. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class IteItemRules {
    private static final ResourceLocation PROTECTION=ResourceLocation.withDefaultNamespace("protection");
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private IteItemRules() {}

    public static int gemSacrificeExperience(ItemStack stack,int original) {
        if(stack.is(Items.DIAMOND))return IteConfig.integer("diamond_exp");
        if(stack.is(Items.EMERALD))return IteConfig.integer("emerald_exp");
        if(stack.is(Items.QUARTZ))return IteConfig.integer("quartz_exp");
        if(stack.is(Items.LAPIS_LAZULI))return IteConfig.integer("lapis_exp");
        return original;
    }
    public static boolean canSacrificeGem(Player player,ItemStack stack,boolean original) {
        if(!original||!stack.is(Items.QUARTZ))return original;
        int limit=IteConfig.integer("quartz_max_exp_level");
        return limit>0&&player.experienceLevel<limit;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void explainBlockedQuartzSacrifice(PlayerInteractEvent.RightClickItem event) {
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player)||player.isSpectator()
                ||!event.getItemStack().is(Items.QUARTZ)||event.getItemStack().isEmpty())return;
        int limit=IteConfig.integer("quartz_max_exp_level");
        MutableComponent message;
        if(limit<=0||IteConfig.integer("quartz_exp")<=0)message=Component.translatable("message.modern_ite.quartz_sacrifice_disabled");
        else if(player.experienceLevel>=limit)message=Component.translatable("message.modern_ite.quartz_sacrifice_level_limit",limit);
        else return;
        player.displayClientMessage(message.withStyle(ChatFormatting.RED),true);
    }
    public static int foodUseDuration(ItemStack stack,LivingEntity user,int original) {
        if(stack.is(Items.GOLDEN_APPLE))return IteConfig.integer("golden_apple_eat_time");
        if(stack.is(Items.ENCHANTED_GOLDEN_APPLE))return IteConfig.integer("enchanted_golden_apple_eat_time");
        return original;
    }

    public static boolean fullProtectionFive(LivingEntity entity) {
        var protection=entity.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        for(var slot:ARMOR) {
            ItemStack stack=entity.getItemBySlot(slot);
            if(stack.isEmpty()||!(stack.getItem() instanceof ArmorItem armor)||armor.getEquipmentSlot()!=slot)return false;
            if(stack.getEnchantmentLevel(protection)<5&&GearProgression.affixLevel(stack,PROTECTION)<5)return false;
        }
        return true;
    }
    @SubscribeEvent
    public static void protectedSet(LivingDamageEvent.Pre event) {
        if(event.getEntity().level().isClientSide||event.getNewDamage()<=0||event.getSource().is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                ||!fullProtectionFive(event.getEntity()))return;
        event.setNewDamage((float)(event.getNewDamage()*(1D-IteConfig.number("all_protection_v_defence_fraction"))));
    }

    public static void portalUsed(ServerPlayer player,BlockPos entry) {
        if(!IteConfig.bool("log_players_interact_with_portal"))return;
        var message=Component.translatable("message.modern_ite.portal_used",player.getName()).withStyle(ChatFormatting.DARK_RED);
        player.server.getPlayerList().broadcastSystemMessage(message,false);
        LoggerFactory.getLogger(ModernIte.MOD_ID).info("Portal used by {} entry={} destination={} position={}",
                player.getUUID(),entry,player.serverLevel().dimension().location(),player.blockPosition());
    }
}
